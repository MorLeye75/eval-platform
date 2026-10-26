package sn.ept.eval.service;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.evaluation.AmenagementResponse;
import sn.ept.eval.dto.evaluation.CasDeTestDTO;
import sn.ept.eval.dto.evaluation.ChoixDTO;
import sn.ept.eval.dto.evaluation.CreateEvaluationRequest;
import sn.ept.eval.dto.evaluation.EvaluationResponse;
import sn.ept.eval.dto.evaluation.QuestionRequestDTO;
import sn.ept.eval.dto.evaluation.QuestionResponseDTO;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.exception.EvaluationNonModifiableException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.model.CasDeTest;
import sn.ept.eval.model.ChoixReponse;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.Question;
import sn.ept.eval.model.QuestionCode;
import sn.ept.eval.model.QuestionOuverte;
import sn.ept.eval.model.QuestionQCM;
import sn.ept.eval.model.QuestionQuiz;
import sn.ept.eval.model.Utilisateur;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.repository.AmenagementTempsRepository;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cycle de vie d'une évaluation côté enseignant : création, modification, suppression (brouillon),
 * publication et dépublication (DS-03, DS-04). Le passage automatique des statuts est dans
 * {@link EvaluationStatutService} (DS-05), le tiers-temps dans {@link AmenagementTempsService} (DS-06).
 */
@Service
@Transactional
public class EvaluationService {

    private final EvaluationRepository evaluationRepository;
    private final PromotionRepository promotionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final SoumissionRepository soumissionRepository;
    private final AmenagementTempsRepository amenagementTempsRepository;
    private final Clock clock;

    public EvaluationService(EvaluationRepository evaluationRepository,
                             PromotionRepository promotionRepository,
                             UtilisateurRepository utilisateurRepository,
                             SoumissionRepository soumissionRepository,
                             AmenagementTempsRepository amenagementTempsRepository,
                             Clock clock) {
        this.evaluationRepository = evaluationRepository;
        this.promotionRepository = promotionRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.soumissionRepository = soumissionRepository;
        this.amenagementTempsRepository = amenagementTempsRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- DS-03

    /** Crée un brouillon avec ses questions. L'enseignant vient du JWT, jamais du corps de la requête. */
    public EvaluationResponse creer(CreateEvaluationRequest request, String email) {
        Enseignant enseignant = enseignantConnecte(email);
        verifierRegles(request);
        List<Promotion> promotions = promotionsExistantes(request.getPromotionIds());

        Evaluation evaluation = new Evaluation();
        evaluation.setEnseignant(enseignant);
        evaluation.setStatut(StatutEvaluation.BROUILLON);
        remplir(evaluation, request, promotions);
        return versResponse(evaluationRepository.save(evaluation), true);
    }

    @Transactional(readOnly = true)
    public EvaluationResponse details(Long id, String email) {
        Enseignant enseignant = enseignantConnecte(email);
        return versResponse(evaluationDeLEnseignant(id, enseignant), true);
    }

    @Transactional(readOnly = true)
    public List<EvaluationResponse> mesEvaluations(String email) {
        Enseignant enseignant = enseignantConnecte(email);
        return evaluationRepository.findByEnseignantIdOrderByDateOuvertureDesc(enseignant.getId()).stream()
                .map(e -> versResponse(e, false))
                .toList();
    }

    /** Modifie un brouillon : les questions sont remplacées en bloc (orphanRemoval). */
    public EvaluationResponse modifier(Long id, CreateEvaluationRequest request, String email) {
        Enseignant enseignant = enseignantConnecte(email);
        Evaluation evaluation = evaluationDeLEnseignant(id, enseignant);
        exigerBrouillon(evaluation, "Seul un brouillon peut être modifié. Repassez l'évaluation en brouillon pour la modifier.");

        verifierRegles(request);
        List<Promotion> promotions = promotionsExistantes(request.getPromotionIds());

        evaluation.getQuestions().clear();
        remplir(evaluation, request, promotions);
        // saveAndFlush : un conflit de version (@Version) est détecté ici, donc traduit en 409 CONFLIT_CONCURRENT
        return versResponse(evaluationRepository.saveAndFlush(evaluation), true);
    }

    public void supprimer(Long id, String email) {
        Enseignant enseignant = enseignantConnecte(email);
        Evaluation evaluation = evaluationDeLEnseignant(id, enseignant);
        exigerBrouillon(evaluation, "Seul un brouillon peut être supprimé.");
        evaluationRepository.delete(evaluation);
    }

    // ---------------------------------------------------------------- DS-04

    /** BROUILLON → PUBLIEE. Le passage à EN_COURS est fait par la tâche planifiée (DS-05). */
    public EvaluationResponse publier(Long id, String email) {
        Enseignant enseignant = enseignantConnecte(email);
        Evaluation evaluation = evaluationDeLEnseignant(id, enseignant);
        exigerBrouillon(evaluation, "Cette évaluation est déjà publiée.");

        if (evaluation.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("Ajoutez au moins une question avant de publier l'évaluation.");
        }
        if (!evaluation.getDateFermeture().isAfter(maintenant())) {
            throw new IllegalArgumentException("La date de fermeture est déjà passée : modifiez les dates avant de publier.");
        }

        evaluation.setStatut(StatutEvaluation.PUBLIEE);
        return versResponse(evaluationRepository.saveAndFlush(evaluation), false);
    }

    /**
     * PUBLIEE → BROUILLON, pour corriger une erreur. Impossible dès qu'une copie existe : l'évaluation
     * est alors verrouillée en écriture le temps du contrôle.
     */
    public EvaluationResponse depublier(Long id, String email) {
        Enseignant enseignant = enseignantConnecte(email);
        Evaluation evaluation = evaluationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RessourceNonTrouveeException("Évaluation introuvable (ID: " + id + ")"));
        verifierProprietaire(evaluation, enseignant);

        if (evaluation.getStatut() != StatutEvaluation.PUBLIEE) {
            throw new EvaluationNonModifiableException("Seule une évaluation publiée, pas encore ouverte, peut repasser en brouillon.");
        }
        if (soumissionRepository.existsByEvaluationId(evaluation.getId())) {
            throw new EvaluationNonModifiableException("Des étudiants ont déjà commencé cette évaluation : la dépublication est impossible.");
        }

        evaluation.setStatut(StatutEvaluation.BROUILLON);
        return versResponse(evaluationRepository.saveAndFlush(evaluation), false);
    }

    // ---------------------------------------------------------------- accès

    /** Évaluation appartenant à l'enseignant : 404 si elle n'existe pas, 403 si elle appartient à un autre. */
    Evaluation evaluationDeLEnseignant(Long id, Enseignant enseignant) {
        Evaluation evaluation = evaluationRepository.findById(id)
                .orElseThrow(() -> new RessourceNonTrouveeException("Évaluation introuvable (ID: " + id + ")"));
        verifierProprietaire(evaluation, enseignant);
        return evaluation;
    }

    Enseignant enseignantConnecte(String email) {
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RessourceNonTrouveeException("Profil enseignant introuvable pour le compte : " + email));
        if (!(Hibernate.unproxy(utilisateur) instanceof Enseignant enseignant)) {
            throw new RessourceNonTrouveeException("Profil enseignant introuvable pour le compte : " + email);
        }
        return enseignant;
    }

    private void verifierProprietaire(Evaluation evaluation, Enseignant enseignant) {
        if (!evaluation.getEnseignant().getId().equals(enseignant.getId())) {
            throw new AccesRefuseException("Accès non autorisé : cette évaluation appartient à un autre enseignant.");
        }
    }

    private void exigerBrouillon(Evaluation evaluation, String message) {
        if (evaluation.getStatut() != StatutEvaluation.BROUILLON) {
            throw new EvaluationNonModifiableException(message);
        }
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(clock);
    }

    // ---------------------------------------------------------------- validation

    /** Règles métier du DS-03 : dates, puis règles propres à chaque type de question. Rien n'est écrit si l'une échoue. */
    void verifierRegles(CreateEvaluationRequest request) {
        if (!request.getDateFermeture().isAfter(request.getDateOuverture())) {
            throw new IllegalArgumentException("La date de fermeture doit être postérieure à la date d'ouverture.");
        }
        List<QuestionRequestDTO> questions = request.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            String erreur = erreurQuestion(questions.get(i));
            if (erreur != null) {
                throw new IllegalArgumentException("Question " + (i + 1) + " : " + erreur);
            }
        }
    }

    /** Retourne le message de la première règle violée par la question, ou null si elle est valide. */
    private String erreurQuestion(QuestionRequestDTO q) {
        switch (q.getType()) {
            case QCM -> {
                return erreurChoix(q);
            }
            case QUIZ_CHRONOMETREE -> {
                if (q.getDureeQuestion() == null || q.getDureeQuestion() <= 0) {
                    return "la durée par question d'un quiz doit être supérieure à 0.";
                }
                return erreurChoix(q);
            }
            case CODE -> {
                if (q.getCasTests() == null || q.getCasTests().isEmpty()) {
                    return "une question de code doit avoir au moins un cas de test.";
                }
                return null;
            }
            case OUVERTE -> {
                return null;
            }
            default -> {
                return "type de question non géré.";
            }
        }
    }

    private String erreurChoix(QuestionRequestDTO q) {
        List<ChoixDTO> choix = q.getChoix();
        if (choix == null || choix.size() < 2) {
            return "il faut au moins 2 choix.";
        }
        long corrects = choix.stream().filter(c -> Boolean.TRUE.equals(c.getEstCorrect())).count();
        if (corrects == 0) {
            return "au moins un choix doit être correct.";
        }
        if (!Boolean.TRUE.equals(q.getEstChoixMultiple()) && corrects > 1) {
            return "un seul choix peut être correct quand la question n'est pas à choix multiples.";
        }
        return null;
    }

    private List<Promotion> promotionsExistantes(List<Long> ids) {
        Set<Long> distincts = new HashSet<>(ids);
        List<Promotion> promotions = promotionRepository.findAllById(distincts);
        if (promotions.size() != distincts.size()) {
            Set<Long> trouvees = new HashSet<>();
            promotions.forEach(p -> trouvees.add(p.getId()));
            distincts.removeAll(trouvees);
            throw new RessourceNonTrouveeException("Promotion introuvable (ID: " + distincts.iterator().next() + ")");
        }
        return new ArrayList<>(promotions);
    }

    // ---------------------------------------------------------------- construction

    private void remplir(Evaluation evaluation, CreateEvaluationRequest request, List<Promotion> promotions) {
        evaluation.setTitre(request.getTitre().trim());
        evaluation.setDescription(request.getDescription());
        evaluation.setDureeStandard(request.getDureeStandard());
        evaluation.setDateOuverture(request.getDateOuverture());
        evaluation.setDateFermeture(request.getDateFermeture());
        evaluation.setPromotions(promotions);

        List<QuestionRequestDTO> questions = request.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            evaluation.addQuestion(construireQuestion(questions.get(i), i + 1));
        }
    }

    private Question construireQuestion(QuestionRequestDTO q, int ordre) {
        boolean multiple = Boolean.TRUE.equals(q.getEstChoixMultiple());
        switch (q.getType()) {
            case QCM -> {
                QuestionQCM question = new QuestionQCM(q.getEnonce(), ordre, q.getPoints(), multiple);
                q.getChoix().forEach(c -> question.addChoix(new ChoixReponse(c.getTexte(), c.getEstCorrect())));
                return question;
            }
            case QUIZ_CHRONOMETREE -> {
                QuestionQuiz question = new QuestionQuiz(q.getEnonce(), ordre, q.getPoints(), multiple, q.getDureeQuestion());
                q.getChoix().forEach(c -> question.addChoix(new ChoixReponse(c.getTexte(), c.getEstCorrect())));
                return question;
            }
            case CODE -> {
                QuestionCode question = new QuestionCode(q.getEnonce(), ordre, q.getPoints());
                q.getCasTests().forEach(c -> question.addCasDeTest(
                        new CasDeTest(c.getEntree(), c.getSortieAttendue(), Boolean.TRUE.equals(c.getEstMasque()))));
                return question;
            }
            default -> {
                return new QuestionOuverte(q.getEnonce(), ordre, q.getPoints());
            }
        }
    }

    // ---------------------------------------------------------------- réponse

    private EvaluationResponse versResponse(Evaluation evaluation, boolean avecQuestions) {
        EvaluationResponse r = new EvaluationResponse();
        r.setId(evaluation.getId());
        r.setTitre(evaluation.getTitre());
        r.setDescription(evaluation.getDescription());
        r.setDureeStandard(evaluation.getDureeStandard());
        r.setDateOuverture(evaluation.getDateOuverture());
        r.setDateFermeture(evaluation.getDateFermeture());
        r.setStatut(evaluation.getStatut());
        r.setResultatsPublies(evaluation.getResultatsPublies());
        r.setVersion(evaluation.getVersion());
        r.setPromotionIds(evaluation.getPromotions().stream().map(Promotion::getId).toList());
        r.setNbQuestions(evaluation.getQuestions().size());
        if (avecQuestions) {
            r.setQuestions(evaluation.getQuestions().stream().map(this::versQuestionResponse).toList());
            if (evaluation.getId() != null) {
                r.setAmenagements(amenagementTempsRepository.findByEvaluationId(evaluation.getId()).stream()
                        .map(a -> new AmenagementResponse(a.getEtudiant().getId(), a.getDureePersonnalisee()))
                        .toList());
            }
        }
        return r;
    }

    private QuestionResponseDTO versQuestionResponse(Question question) {
        Object reelle = Hibernate.unproxy(question);
        QuestionResponseDTO dto = new QuestionResponseDTO();
        dto.setId(question.getId());
        dto.setType(question.getType());
        dto.setEnonce(question.getEnonce());
        dto.setOrdre(question.getOrdre());
        dto.setPoints(question.getPoints());
        if (reelle instanceof QuestionQCM qcm) {
            dto.setEstChoixMultiple(qcm.getEstChoixMultiple());
            dto.setChoix(qcm.getChoix().stream().map(c -> new ChoixDTO(c.getTexte(), c.getEstCorrect())).toList());
        }
        if (reelle instanceof QuestionQuiz quiz) {
            dto.setDureeQuestion(quiz.getDureeQuestion());
        }
        if (reelle instanceof QuestionCode code) {
            dto.setCasTests(code.getCasTests().stream()
                    .map(c -> new CasDeTestDTO(c.getEntree(), c.getSortieAttendue(), c.getEstMasque()))
                    .toList());
        }
        return dto;
    }
}
