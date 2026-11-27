package sn.ept.eval.service;

import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.correction.CopieEnseignantResponse;
import sn.ept.eval.dto.correction.RevisionNoteRequest;
import sn.ept.eval.dto.correction.RevisionNoteResponse;
import sn.ept.eval.exception.CorrectionNonModifiableException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.ReponseEtudiantRepository;
import sn.ept.eval.repository.RevisionNoteRepository;
import sn.ept.eval.repository.SoumissionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Copie vue par son enseignant et révision d'une note avant la publication des résultats.
 *
 * <p>Une note déjà posée (QCM, Judge0, IA validée, note manuelle) peut être revue tant que les résultats ne sont pas
 * publiés : le motif est obligatoire et chaque révision est tracée (ancienne note, nouvelle note, motif, auteur, date).
 * Après publication, plus aucune note ne change. La publication (manuelle ou programmée) bascule l'évaluation par une
 * mise à jour conditionnelle : la révision lit l'évaluation sous verrou partagé, donc les deux se sérialisent — une
 * révision commencée avant la publication est publiée avec elle, une révision arrivée après est refusée.
 */
@Service
public class CopieEnseignantService {

    private static final Set<StatutSoumission> STATUTS_NOTES = Set.of(StatutSoumission.EN_CORRECTION, StatutSoumission.EVALUE);
    private static final String PREFIXE_COMMENTAIRE = "Note revue par l'enseignant : ";

    private final EvaluationService evaluationService;
    private final EvaluationRepository evaluationRepository;
    private final SoumissionRepository soumissionRepository;
    private final ReponseEtudiantRepository reponseEtudiantRepository;
    private final RevisionNoteRepository revisionNoteRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public CopieEnseignantService(EvaluationService evaluationService, EvaluationRepository evaluationRepository,
                                  SoumissionRepository soumissionRepository, ReponseEtudiantRepository reponseEtudiantRepository,
                                  RevisionNoteRepository revisionNoteRepository, EntityManager entityManager, Clock clock) {
        this.evaluationService = evaluationService;
        this.evaluationRepository = evaluationRepository;
        this.soumissionRepository = soumissionRepository;
        this.reponseEtudiantRepository = reponseEtudiantRepository;
        this.revisionNoteRepository = revisionNoteRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ consulter

    @Transactional(readOnly = true)
    public CopieEnseignantResponse copie(Long soumissionId, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        Soumission copie = soumissionRepository.findById(soumissionId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Copie introuvable (ID: " + soumissionId + ")"));
        Evaluation evaluation = copie.getEvaluation();
        evaluationService.verifierProprietaire(evaluation, enseignant);

        Map<Long, ReponseEtudiant> parQuestion = copie.getReponses().stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), Function.identity(), (a, b) -> a));
        Map<Long, List<RevisionNote>> revisions = parQuestion.isEmpty() ? Map.of()
                : revisionNoteRepository.historique(parQuestion.values().stream().map(ReponseEtudiant::getId).toList())
                .stream().collect(Collectors.groupingBy(r -> r.getReponse().getId()));

        List<CopieEnseignantResponse.Reponse> reponses = evaluation.getQuestions().stream()
                .map(q -> (Question) Hibernate.unproxy(q))
                .sorted(Comparator.comparing(Question::getOrdre, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(q -> reponse(q, parQuestion.get(q.getId()), revisions))
                .toList();
        double bareme = evaluation.getQuestions().stream().mapToDouble(q -> q.getPoints() == null ? 0 : q.getPoints()).sum();
        boolean publies = Boolean.TRUE.equals(evaluation.getResultatsPublies());
        Etudiant etudiant = copie.getEtudiant();
        return new CopieEnseignantResponse(copie.getId(), evaluation.getId(), evaluation.getTitre(),
                etudiant.getPrenom() + " " + etudiant.getNom(), etudiant.getNumEtudiant(), copie.getStatut(),
                copie.getStatut() == StatutSoumission.EVALUE ? copie.getNoteFinale() : null, arrondi(bareme), publies,
                !publies && STATUTS_NOTES.contains(copie.getStatut()), copie.getDateRemise(), reponses);
    }

    private CopieEnseignantResponse.Reponse reponse(Question question, ReponseEtudiant brute,
                                                    Map<Long, List<RevisionNote>> revisions) {
        double points = question.getPoints() == null ? 0 : question.getPoints();
        String type = question.getType() == null ? null : question.getType().name();
        if (brute == null) {
            return new CopieEnseignantResponse.Reponse(null, question.getOrdre(), type, question.getEnonce(), points,
                    null, false, null, null, null, null, List.of(), List.of(), List.of());
        }
        Object r = Hibernate.unproxy(brute);
        boolean notee = Boolean.TRUE.equals(brute.getValideeParProf());
        String texte = r instanceof ReponseOuverte o ? o.getTexteSoumis() : null;
        String code = r instanceof ReponseCode c ? c.getCodeSoumis() : null;
        String langage = r instanceof ReponseCode c ? c.getLangage() : null;

        List<CopieEnseignantResponse.Choix> choix = List.of();
        if (question instanceof QuestionQCM qcm && r instanceof ReponseQCM rq) {
            Set<Long> coches = rq.getChoixCoches().stream().map(ChoixReponse::getId).collect(Collectors.toSet());
            choix = qcm.getChoix().stream().map(ch -> new CopieEnseignantResponse.Choix(ch.getTexte(),
                    coches.contains(ch.getId()), Boolean.TRUE.equals(ch.getEstCorrect()))).toList();
        }
        List<CopieEnseignantResponse.Cas> cas = List.of();
        if (r instanceof ReponseCode c) {
            cas = c.getResultatsCasTests().stream()
                    .sorted(Comparator.comparing(rc -> rc.getCasDeTest().getId()))
                    .map(rc -> new CopieEnseignantResponse.Cas(rc.getCasDeTest().getEntree(),
                            rc.getCasDeTest().getSortieAttendue(), rc.getSortieObtenue(), rc.getVerdict(),
                            Boolean.TRUE.equals(rc.getEstValide()), Boolean.TRUE.equals(rc.getCasDeTest().getEstMasque()),
                            rc.getMessageErreur()))
                    .toList();
        }
        List<CopieEnseignantResponse.Revision> historique = revisions.getOrDefault(brute.getId(), List.of()).stream()
                .map(rv -> new CopieEnseignantResponse.Revision(rv.getAncienneNote(), rv.getNouvelleNote(), rv.getMotif(),
                        rv.getAuteur().getPrenom() + " " + rv.getAuteur().getNom(), rv.getReviseeLe()))
                .toList();
        return new CopieEnseignantResponse.Reponse(brute.getId(), question.getOrdre(), type, question.getEnonce(), points,
                notee ? brute.getNoteObtenue() : null, notee, brute.getCommentaireProf(), texte, code, langage, choix, cas,
                historique);
    }

    // ------------------------------------------------------------------ réviser

    @Transactional
    public RevisionNoteResponse reviser(Long reponseId, RevisionNoteRequest requete, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        ReponseEtudiant reponse = reponseEtudiantRepository.findById(reponseId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Réponse introuvable (ID: " + reponseId + ")"));
        Long evaluationId = reponse.getSoumission().getEvaluation().getId();
        evaluationService.verifierProprietaire(reponse.getSoumission().getEvaluation(), enseignant);

        String motif = requete == null || requete.motif() == null ? "" : requete.motif().trim();
        if (motif.isEmpty()) {
            throw new IllegalArgumentException("Le motif de la révision est obligatoire.");
        }
        if (requete.note() == null) {
            throw new IllegalArgumentException("La nouvelle note est obligatoire.");
        }
        double points = ((Question) Hibernate.unproxy(reponse.getQuestion())).getPoints();
        double note = requete.note();
        if (Double.isNaN(note) || note < 0 || note > points) {
            throw new IllegalArgumentException("La note doit être comprise entre 0 et " + points + ".");
        }
        note = arrondi(note);

        // Verrou partagé sur l'évaluation d'abord (même ordre que le démarrage d'une copie), puis la copie : la
        // publication, qui modifie l'évaluation, attend la fin de cette révision ou la précède entièrement.
        Evaluation evaluation = evaluationRepository.findByIdForShare(evaluationId).orElseThrow();
        entityManager.refresh(evaluation);
        if (Boolean.TRUE.equals(evaluation.getResultatsPublies())) {
            throw new CorrectionNonModifiableException("Les résultats sont publiés : les notes de cette évaluation ne sont plus modifiables.");
        }
        Soumission copie = soumissionRepository.findByIdForUpdate(reponse.getSoumission().getId()).orElseThrow();
        entityManager.refresh(copie);
        entityManager.refresh(reponse);
        if (!STATUTS_NOTES.contains(copie.getStatut())) {
            throw new CorrectionNonModifiableException("Cette copie n'est pas encore corrigée.");
        }
        if (!Boolean.TRUE.equals(reponse.getValideeParProf()) || reponse.getNoteObtenue() == null) {
            throw new CorrectionNonModifiableException("Cette réponse n'est pas encore notée : notez-la d'abord.");
        }
        double ancienne = reponse.getNoteObtenue();
        if (Objects.equals(ancienne, note)) {
            throw new IllegalArgumentException("La nouvelle note est identique à la note actuelle (" + ancienne + ").");
        }

        revisionNoteRepository.save(new RevisionNote(reponse, enseignant, ancienne, note, motif, LocalDateTime.now(clock)));
        reponse.setNoteObtenue(note);
        reponse.setCommentaireProf(PREFIXE_COMMENTAIRE + motif);
        reponseEtudiantRepository.save(reponse);

        Double noteFinale = null;
        if (copie.getStatut() == StatutSoumission.EVALUE) {
            // Même calcul que la correction : jamais sous 0 (barème négatif des QCM)
            noteFinale = CorrectionService.noteFinale(copie.getReponses());
            copie.setNoteFinale(noteFinale);
            soumissionRepository.save(copie);
        }
        return new RevisionNoteResponse(reponse.getId(), ancienne, note, noteFinale);
    }

    private static double arrondi(double valeur) {
        return BigDecimal.valueOf(valeur).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
