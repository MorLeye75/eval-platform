package sn.ept.eval.service;

import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.dto.correction.CorrectionsIAResponse;
import sn.ept.eval.dto.correction.NoteRequest;
import sn.ept.eval.dto.correction.NoteResponse;
import sn.ept.eval.dto.correction.PropositionIAResponse;
import sn.ept.eval.exception.CorrectionNonModifiableException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutProposition;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.ReponseEtudiantRepository;
import sn.ept.eval.repository.ReponseOuverteRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.service.ia.CorrecteurIA;
import sn.ept.eval.service.ia.CorrecteurIA.CorrecteurIAIndisponibleException;
import sn.ept.eval.service.ia.CorrecteurIA.Proposition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Correction assistée par IA et validation par l'enseignant (DS-08). L'IA propose ; seule la décision de l'enseignant
 * écrit la note d'une réponse. Une copie passe EVALUE quand toutes ses réponses sont notées.
 */
@Service
public class CorrectionIAService {

    private static final Logger log = LoggerFactory.getLogger(CorrectionIAService.class);

    private final EvaluationService evaluationService;
    private final ReponseOuverteRepository reponseOuverteRepository;
    private final ReponseEtudiantRepository reponseEtudiantRepository;
    private final SoumissionRepository soumissionRepository;
    private final NotificationService notificationService;
    private final CorrecteurIA correcteur;
    private final EntityManager entityManager;
    /** Une transaction courte par phase : aucune n'est ouverte pendant l'appel au modèle. */
    private final TransactionTemplate transaction;

    public CorrectionIAService(EvaluationService evaluationService, ReponseOuverteRepository reponseOuverteRepository,
                               ReponseEtudiantRepository reponseEtudiantRepository, SoumissionRepository soumissionRepository,
                               NotificationService notificationService, CorrecteurIA correcteur,
                               EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.evaluationService = evaluationService;
        this.reponseOuverteRepository = reponseOuverteRepository;
        this.reponseEtudiantRepository = reponseEtudiantRepository;
        this.soumissionRepository = soumissionRepository;
        this.notificationService = notificationService;
        this.correcteur = correcteur;
        this.entityManager = entityManager;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ B — une proposition (tâche asynchrone)

    private record AProposer(String enonce, double bareme, String texte) {}

    /** Propose une note pour une réponse. N'échoue jamais : une indisponibilité du modèle laisse la réponse à noter à la main. */
    public void proposer(Long reponseId) {
        try {
            AProposer donnees = transaction.execute(tx -> lire(reponseId));
            if (donnees == null) return;
            Proposition proposition;
            if (donnees.texte() == null || donnees.texte().isBlank()) {
                proposition = new Proposition(0.0, "Aucune réponse.");
            } else {
                proposition = correcteur.proposer(donnees.enonce(), donnees.bareme(), donnees.texte());
            }
            double note = CorrecteurIA.borner(proposition.note(), donnees.bareme());
            String justification = note == proposition.note() ? proposition.justification()
                    : "Note corrigée : le modèle proposait " + proposition.note() + ". " + proposition.justification();
            transaction.executeWithoutResult(tx -> ecrire(reponseId, note, justification));
        } catch (CorrecteurIAIndisponibleException e) {
            log.warn("[CorrectionIA] Réponse ID={} : modèle indisponible ({})", reponseId, e.getMessage());
        } catch (RuntimeException e) {
            log.error("[CorrectionIA] Réponse ID={} : erreur imprévue", reponseId, e);
        }
    }

    private AProposer lire(Long reponseId) {
        ReponseOuverte r = reponseOuverteRepository.findById(reponseId).orElse(null);
        if (r == null || !proposable(r)) return null;
        Question question = (Question) Hibernate.unproxy(r.getQuestion());
        return new AProposer(question.getEnonce(), question.getPoints(), r.getTexteSoumis());
    }

    private void ecrire(Long reponseId, double note, String justification) {
        ReponseOuverte r = reponseOuverteRepository.findById(reponseId).orElse(null);
        // Apparue entre-temps, ou copie plus en correction : rien n'est écrit (idempotence)
        if (r == null || !proposable(r)) return;
        r.setPropositionIA(new PropositionIA(note, justification, StatutProposition.EN_ATTENTE));
        reponseOuverteRepository.save(r);
    }

    private boolean proposable(ReponseOuverte r) {
        return r.getPropositionIA() == null && !Boolean.TRUE.equals(r.getValideeParProf())
                && r.getSoumission().getStatut() == StatutSoumission.EN_CORRECTION;
    }

    // ------------------------------------------------------------------ C — consulter

    @Transactional(readOnly = true)
    public CorrectionsIAResponse lister(String email, Long evaluationId, StatutProposition statut) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        List<PropositionIAResponse> propositions = reponseOuverteRepository
                .propositionsDeLEnseignant(enseignant.getId(), evaluationId, statut).stream()
                .map(this::versResponse).toList();
        long enCours = reponseOuverteRepository.compterSansProposition(enseignant.getId(), evaluationId);
        return new CorrectionsIAResponse(propositions, enCours);
    }

    private PropositionIAResponse versResponse(ReponseOuverte r) {
        PropositionIA p = r.getPropositionIA();
        Soumission copie = r.getSoumission();
        Question question = (Question) Hibernate.unproxy(r.getQuestion());
        boolean decidee = p.getStatut() != StatutProposition.EN_ATTENTE;
        return new PropositionIAResponse(p.getId(), r.getId(), copie.getEvaluation().getId(), copie.getEvaluation().getTitre(),
                copie.getEtudiant().getPrenom() + " " + copie.getEtudiant().getNom(), question.getEnonce(),
                r.getTexteSoumis(), question.getPoints(), p.getNoteProposee(), p.getJustification(), p.getStatut(),
                decidee ? r.getNoteObtenue() : null);
    }

    // ------------------------------------------------------------------ D — valider ou rejeter

    @Transactional
    public NoteResponse valider(Long propositionId, NoteRequest requete, String email) {
        return decider(propositionId, requete, email, false);
    }

    @Transactional
    public NoteResponse rejeter(Long propositionId, NoteRequest requete, String email) {
        return decider(propositionId, requete, email, true);
    }

    private NoteResponse decider(Long propositionId, NoteRequest requete, String email, boolean rejet) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        ReponseOuverte reponse = reponseOuverteRepository.findByPropositionId(propositionId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Proposition introuvable (ID: " + propositionId + ")"));
        evaluationService.verifierProprietaire(reponse.getSoumission().getEvaluation(), enseignant);
        double bareme = bareme(reponse);
        Double demandee = requete == null ? null : requete.note();
        if (rejet && demandee == null) {
            throw new IllegalArgumentException("La note de l'enseignant est obligatoire pour rejeter une proposition.");
        }
        double note = demandee != null ? verifierNote(demandee, bareme) : reponse.getPropositionIA().getNoteProposee();

        Soumission copie = verrouillerCopie(reponse);
        PropositionIA proposition = reponse.getPropositionIA();
        if (proposition.getStatut() != StatutProposition.EN_ATTENTE) {
            throw new CorrectionNonModifiableException("Cette proposition a déjà été traitée.");
        }
        proposition.setStatut(rejet ? StatutProposition.REJETEE : StatutProposition.VALIDEE);
        return poserNote(copie, reponse, note, requete == null ? null : requete.commentaire());
    }

    // ------------------------------------------------------------------ E — note manuelle

    /** Note une réponse sans proposition (modèle indisponible, code sans cas de test) ; une proposition en attente est rejetée. */
    @Transactional
    public NoteResponse noterManuellement(Long reponseId, NoteRequest requete, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        ReponseEtudiant reponse = reponseEtudiantRepository.findById(reponseId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Réponse introuvable (ID: " + reponseId + ")"));
        evaluationService.verifierProprietaire(reponse.getSoumission().getEvaluation(), enseignant);
        if (requete == null || requete.note() == null) {
            throw new IllegalArgumentException("La note est obligatoire.");
        }
        double note = verifierNote(requete.note(), bareme(reponse));

        Soumission copie = verrouillerCopie(reponse);
        if (Boolean.TRUE.equals(reponse.getValideeParProf())) {
            throw new CorrectionNonModifiableException("Cette réponse est déjà notée.");
        }
        if (Hibernate.unproxy(reponse) instanceof ReponseOuverte ouverte && ouverte.getPropositionIA() != null
                && ouverte.getPropositionIA().getStatut() == StatutProposition.EN_ATTENTE) {
            ouverte.getPropositionIA().setStatut(StatutProposition.REJETEE);
        }
        return poserNote(copie, reponse, note, requete.commentaire());
    }

    // ------------------------------------------------------------------ communs

    private double bareme(ReponseEtudiant reponse) {
        return ((Question) Hibernate.unproxy(reponse.getQuestion())).getPoints();
    }

    private double verifierNote(double note, double bareme) {
        if (Double.isNaN(note) || note < 0 || note > bareme) {
            throw new IllegalArgumentException("La note doit être comprise entre 0 et " + bareme + ".");
        }
        return arrondi(note);
    }

    /** Verrou de la copie (même verrou que DS-12) puis relecture : deux validations simultanées sont sérialisées. */
    private Soumission verrouillerCopie(ReponseEtudiant reponse) {
        Soumission copie = soumissionRepository.findByIdForUpdate(reponse.getSoumission().getId()).orElseThrow();
        entityManager.refresh(reponse);
        if (copie.getStatut() != StatutSoumission.EN_CORRECTION) {
            throw new CorrectionNonModifiableException("Cette copie n'est plus en cours de correction.");
        }
        return copie;
    }

    private NoteResponse poserNote(Soumission copie, ReponseEtudiant reponse, double note, String commentaire) {
        reponse.setNoteObtenue(note);
        reponse.setValideeParProf(true);
        reponse.setCommentaireProf(commentaire);
        reponseEtudiantRepository.save(reponse);

        boolean toutEstNote = copie.getReponses().stream().allMatch(r -> Boolean.TRUE.equals(r.getValideeParProf()));
        Double noteFinale = null;
        if (toutEstNote) {
            noteFinale = arrondi(copie.getReponses().stream().map(ReponseEtudiant::getNoteObtenue)
                    .filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum());
            copie.setNoteFinale(noteFinale);
            copie.setStatut(StatutSoumission.EVALUE);
            soumissionRepository.save(copie);
            // Résultats déjà publiés : prévenir l'étudiant dans la même transaction (sans effet sinon, jamais en double)
            notificationService.resultatPublie(copie);
        }
        return new NoteResponse(reponse.getId(), note, toutEstNote, noteFinale);
    }

    private static double arrondi(double note) {
        return BigDecimal.valueOf(note).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
