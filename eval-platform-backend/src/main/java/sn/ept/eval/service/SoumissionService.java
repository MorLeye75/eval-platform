package sn.ept.eval.service;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sn.ept.eval.dto.soumission.*;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.exception.SoumissionNonModifiableException;
import sn.ept.eval.exception.TempsEcouleException;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SoumissionService {

    private static final Logger log = LoggerFactory.getLogger(SoumissionService.class);
    /** Au-delà de ce délai sans modification, une copie SOUMIS est considérée comme bloquée. */
    public static final int DELAI_CORRECTION_BLOQUEE_MINUTES = 5;

    private final SoumissionRepository soumissionRepository;
    private final EvaluationRepository evaluationRepository;
    private final EtudiantRepository etudiantRepository;
    private final QuestionRepository questionRepository;
    private final ChoixReponseRepository choixReponseRepository;
    private final ReponseEtudiantRepository reponseEtudiantRepository;
    private final CorrectionService correctionService;
    private final TempsEpreuveService tempsEpreuveService;
    private final SoumissionMapper soumissionMapper;
    private final SoumissionAccesService soumissionAccesService;
    private final AntiTricheService antiTricheService;

    public SoumissionService(SoumissionRepository soumissionRepository,
                             EvaluationRepository evaluationRepository,
                             EtudiantRepository etudiantRepository,
                             QuestionRepository questionRepository,
                             ChoixReponseRepository choixReponseRepository,
                             ReponseEtudiantRepository reponseEtudiantRepository,
                             CorrectionService correctionService,
                             TempsEpreuveService tempsEpreuveService,
                             SoumissionMapper soumissionMapper,
                             SoumissionAccesService soumissionAccesService,
                             AntiTricheService antiTricheService) {
        this.soumissionRepository = soumissionRepository;
        this.evaluationRepository = evaluationRepository;
        this.etudiantRepository = etudiantRepository;
        this.questionRepository = questionRepository;
        this.choixReponseRepository = choixReponseRepository;
        this.reponseEtudiantRepository = reponseEtudiantRepository;
        this.correctionService = correctionService;
        this.tempsEpreuveService = tempsEpreuveService;
        this.soumissionMapper = soumissionMapper;
        this.soumissionAccesService = soumissionAccesService;
        this.antiTricheService = antiTricheService;
    }

    /**
     * Démarrer ou reprendre une session d'évaluation pour l'étudiant connecté.
     */
    public SoumissionDetailResponse demarrerOuReprendre(Long evaluationId, String userEmail) {
        Etudiant etudiant = soumissionAccesService.etudiantConnecte(userEmail);
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Évaluation introuvable (ID: " + evaluationId + ")"));

        LocalDateTime now = tempsEpreuveService.maintenant();
        if (now.isBefore(evaluation.getDateOuverture())) {
            throw new IllegalArgumentException("Cette évaluation n'est pas encore ouverte.");
        }
        if (now.isAfter(evaluation.getDateFermeture())) {
            throw new IllegalArgumentException("Cette évaluation est déjà clôturée.");
        }
        if (evaluation.getStatut() != StatutEvaluation.PUBLIEE && evaluation.getStatut() != StatutEvaluation.EN_COURS) {
            throw new IllegalArgumentException("Cette évaluation n'est pas accessible (statut : " + evaluation.getStatut() + ").");
        }

        // L'étudiant doit appartenir à l'une des promotions assignées à l'évaluation
        boolean promotionValide = etudiant.getPromotion() != null && evaluation.getPromotions().stream()
                .anyMatch(p -> p.getId().equals(etudiant.getPromotion().getId()));
        if (!promotionValide) {
            throw new AccesRefuseException("Vous n'êtes pas inscrit dans une promotion assignée à cette évaluation.");
        }

        // Verrou sur l'étudiant : deux démarrages simultanés (double clic, deux onglets) ne doivent pas créer
        // deux copies ; le second attend la fin du premier puis reprend la copie qu'il a créée
        etudiantRepository.findByIdForUpdate(etudiant.getId());

        // Vérifier si une soumission existe déjà
        Optional<Soumission> existanteOpt = soumissionRepository.findByEvaluationIdAndEtudiantId(evaluationId, etudiant.getId());
        Soumission soumission;

        if (existanteOpt.isPresent()) {
            soumission = existanteOpt.get();
            log.info("Reprise de la soumission ID={} pour l'étudiant {}", soumission.getId(), userEmail);
        } else {
            soumission = new Soumission();
            soumission.setEvaluation(evaluation);
            soumission.setEtudiant(etudiant);
            soumission.setDateDebut(now);
            soumission.setStatut(StatutSoumission.EN_COURS);
            soumission.setNbChangementsOnglet(0);
            soumission = soumissionRepository.save(soumission);
            log.info("Création d'une nouvelle soumission ID={} pour l'étudiant {}", soumission.getId(), userEmail);
        }

        return soumissionMapper.versDetail(soumission);
    }

    /**
     * Sauvegarde automatique des réponses (conforme au Diagramme de Séquence 2).
     */
    public SauvegardeResponse sauvegarderReponses(Long soumissionId, SauvegardeReponsesRequest request, String userEmail) {
        Etudiant etudiant = soumissionAccesService.etudiantConnecte(userEmail);
        Soumission soumission = soumissionAccesService.copieDeLEtudiant(soumissionId, etudiant);

        // 1. Vérifie statut == EN_COURS
        if (soumission.getStatut() != StatutSoumission.EN_COURS) {
            throw new SoumissionNonModifiableException("Action rejetée : la copie a déjà été soumise ou verrouillée.");
        }

        // 2. Vérifie !isExpiree()
        if (tempsEpreuveService.estExpiree(soumission)) {
            log.warn("Tentative de sauvegarde tardive rejetée pour soumission ID={}", soumissionId);
            throw new TempsEcouleException("Action rejetée : temps imparti expiré.");
        }

        // 3. Mise à jour ou création des réponses
        // Questions de l'évaluation et réponses existantes chargées en une requête chacune (pas de N+1)
        Map<Long, Question> questionsEvaluation = questionRepository
                .findByEvaluationIdOrderByOrdreAsc(soumission.getEvaluation().getId()).stream()
                .collect(Collectors.toMap(Question::getId, q -> (Question) Hibernate.unproxy(q)));
        Map<Long, ReponseEtudiant> reponsesExistantes = reponseEtudiantRepository.findBySoumissionId(soumission.getId()).stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (r1, r2) -> r1));

        int nbSauvegardees = 0;
        if (request.getReponses() != null) {
            for (ReponseItemDto item : request.getReponses()) {
                if (item.getQuestionId() == null) continue;

                Question question = questionsEvaluation.get(item.getQuestionId());
                if (question == null) {
                    // La question doit appartenir à l'évaluation de cette copie ; un id inexistant est ignoré
                    if (questionRepository.existsById(item.getQuestionId())) {
                        throw new IllegalArgumentException("La question " + item.getQuestionId() + " n'appartient pas à cette évaluation.");
                    }
                    continue;
                }

                ReponseEtudiant reponse = reponsesExistantes.get(question.getId());

                // Une réponse existante d'un autre type (ex. question modifiée) est remplacée, pas dupliquée
                if (reponse != null && !estDuBonType(reponse, question)) {
                    soumission.getReponses().remove(reponse);
                    reponseEtudiantRepository.delete(reponse);
                    reponseEtudiantRepository.flush();
                    reponse = null;
                }

                // Dispatch sur la classe : QuestionQuiz hérite de QuestionQCM (même stockage par choix cochés)
                if (question instanceof QuestionQCM questionQcm) {
                    ReponseQCM rq = (reponse instanceof ReponseQCM) ? (ReponseQCM) reponse : new ReponseQCM();
                    rq.setSoumission(soumission);
                    rq.setQuestion(question);
                    if (item.getChoixIds() != null && !item.getChoixIds().isEmpty()) {
                        rq.setChoixCoches(getChoixValides(questionQcm, item.getChoixIds()));
                    } else {
                        rq.setChoixCoches(new ArrayList<>());
                    }
                    reponseEtudiantRepository.save(rq);
                    nbSauvegardees++;
                } else if (question instanceof QuestionCode) {
                    ReponseCode rc = (reponse instanceof ReponseCode) ? (ReponseCode) reponse : new ReponseCode();
                    rc.setSoumission(soumission);
                    rc.setQuestion(question);
                    rc.setCodeSoumis(item.getCodeSoumis());
                    reponseEtudiantRepository.save(rc);
                    nbSauvegardees++;
                } else {
                    ReponseOuverte ro = (reponse instanceof ReponseOuverte) ? (ReponseOuverte) reponse : new ReponseOuverte();
                    ro.setSoumission(soumission);
                    ro.setQuestion(question);
                    ro.setTexteSoumis(item.getTexteSoumis());
                    reponseEtudiantRepository.save(ro);
                    nbSauvegardees++;
                }
            }
        }

        return new SauvegardeResponse(tempsEpreuveService.maintenant(), "Sauvegarde effectuée avec succès.", nbSauvegardees);
    }

    /**
     * Soumission finale de la copie (conforme au Diagramme de Séquence 1).
     * Pas de rollback sur TempsEcouleException : le verrouillage de la copie et l'alerte doivent être persistés.
     */
    @Transactional(noRollbackFor = TempsEcouleException.class)
    public SoumissionResponse soumettreCopie(Long soumissionId, String userEmail) {
        Etudiant etudiant = soumissionAccesService.etudiantConnecte(userEmail);
        Soumission soumission = soumissionAccesService.copieDeLEtudiant(soumissionId, etudiant);

        if (soumission.getStatut() != StatutSoumission.EN_COURS) {
            return soumissionMapper.versResponse(soumission, "Cette copie a déjà été transmise.");
        }

        LocalDateTime now = tempsEpreuveService.maintenant();
        LocalDateTime finEpreuve = tempsEpreuveService.finEpreuve(soumission);

        // [Cas critique : Temps dépassé au-delà de la marge de tolérance]
        if (tempsEpreuveService.estExpiree(soumission)) {
            // Le serveur fige la copie à l'état du dernier auto-save
            soumission.setStatut(StatutSoumission.SOUMIS);
            soumission.setDateRemise(finEpreuve);
            soumissionRepository.save(soumission);
            antiTricheService.signalerSoumissionTardive(soumission);

            // La copie figée est corrigée comme les autres (sinon elle resterait SOUMIS indéfiniment)
            declencherCorrectionApresCommit(soumission.getId());

            throw new TempsEcouleException("Temps dépassé au-delà de la marge de tolérance. Votre copie a été verrouillée à l'état de votre dernière sauvegarde.");
        }

        // [Cas Normal : Temps dans les clous ou dans la marge]
        soumission.setStatut(StatutSoumission.SOUMIS);
        soumission.setDateRemise(now);
        soumission = soumissionRepository.save(soumission);

        // Déclenche CorrectionService en arrière-plan (async), une fois le statut SOUMIS commité
        declencherCorrectionApresCommit(soumission.getId());

        return soumissionMapper.versResponse(soumission, "Copie soumise avec succès.");
    }

    /**
     * Clôture automatique (tâche planifiée) : les copies EN_COURS dont le temps, marge comprise, est écoulé
     * sont figées à leur dernier auto-save puis corrigées. Cas typique : l'étudiant a fermé son navigateur.
     *
     * @return le nombre de copies clôturées
     */
    public int cloturerCopiesExpirees() {
        int nbCloturees = 0;
        for (Soumission soumission : soumissionRepository.findByStatut(StatutSoumission.EN_COURS)) {
            if (tempsEpreuveService.estExpiree(soumission)) {
                soumission.setStatut(StatutSoumission.SOUMIS);
                soumission.setDateRemise(tempsEpreuveService.finEpreuve(soumission));
                declencherCorrectionApresCommit(soumission.getId());
                log.info("Clôture automatique de la soumission ID={} (temps écoulé)", soumission.getId());
                nbCloturees++;
            }
        }
        return nbCloturees;
    }

    /**
     * Reprise (tâche planifiée) des copies restées SOUMIS : la correction asynchrone a échoué
     * ou le serveur a redémarré entre la soumission et la correction.
     *
     * @return le nombre de corrections relancées
     */
    public int relancerCorrectionsBloquees() {
        LocalDateTime seuil = tempsEpreuveService.maintenant().minusMinutes(DELAI_CORRECTION_BLOQUEE_MINUTES);
        List<Soumission> bloquees = soumissionRepository.findByStatutAndDateModificationBefore(StatutSoumission.SOUMIS, seuil);
        bloquees.forEach(s -> {
            log.warn("Relance de la correction de la soumission ID={} (bloquée en SOUMIS)", s.getId());
            declencherCorrectionApresCommit(s.getId());
        });
        return bloquees.size();
    }

    /**
     * Récupérer le détail complet d'une soumission.
     */
    @Transactional(readOnly = true)
    public SoumissionDetailResponse getDetail(Long soumissionId, String userEmail) {
        return soumissionMapper.versDetail(soumissionAccesService.copieConsultable(soumissionId, userEmail));
    }

    private void declencherCorrectionApresCommit(Long soumissionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    correctionService.corrigerSoumissionAsync(soumissionId);
                }
            });
        } else {
            correctionService.corrigerSoumissionAsync(soumissionId);
        }
    }

    private List<ChoixReponse> getChoixValides(QuestionQCM question, List<Long> choixIds) {
        Set<Long> idsDemandes = new HashSet<>(choixIds);
        List<ChoixReponse> choix = choixReponseRepository.findAllById(idsDemandes);
        boolean tousValides = choix.size() == idsDemandes.size()
                && choix.stream().allMatch(c -> c.getQuestion().getId().equals(question.getId()));
        if (!tousValides) {
            throw new IllegalArgumentException("Choix invalides pour la question " + question.getId() + ".");
        }
        if (!Boolean.TRUE.equals(question.getEstChoixMultiple()) && choix.size() > 1) {
            throw new IllegalArgumentException("La question " + question.getId() + " n'accepte qu'un seul choix.");
        }
        return choix;
    }

    private boolean estDuBonType(ReponseEtudiant reponse, Question question) {
        if (question instanceof QuestionQCM) return reponse instanceof ReponseQCM;
        if (question instanceof QuestionCode) return reponse instanceof ReponseCode;
        return reponse instanceof ReponseOuverte;
    }
}
