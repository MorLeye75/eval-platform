package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.soumission.*;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.exception.SoumissionNonModifiableException;
import sn.ept.eval.exception.TempsEcouleException;
import sn.ept.eval.exception.VersionObsoleteException;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SoumissionService {

    private static final Logger log = LoggerFactory.getLogger(SoumissionService.class);

    private final SoumissionRepository soumissionRepository;
    private final EvaluationRepository evaluationRepository;
    private final EtudiantRepository etudiantRepository;
    private final EnregistrementReponsesService enregistrementReponsesService;
    private final CorrectionDeclencheur correctionDeclencheur;
    private final TempsEpreuveService tempsEpreuveService;
    private final SoumissionMapper soumissionMapper;
    private final SoumissionAccesService soumissionAccesService;
    private final AntiTricheService antiTricheService;
    private final ClotureCopiesService clotureCopiesService;
    private final QuizService quizService;

    public SoumissionService(SoumissionRepository soumissionRepository,
                             EvaluationRepository evaluationRepository,
                             EtudiantRepository etudiantRepository,
                             EnregistrementReponsesService enregistrementReponsesService,
                             CorrectionDeclencheur correctionDeclencheur,
                             TempsEpreuveService tempsEpreuveService,
                             SoumissionMapper soumissionMapper,
                             SoumissionAccesService soumissionAccesService,
                             AntiTricheService antiTricheService,
                             ClotureCopiesService clotureCopiesService,
                             QuizService quizService) {
        this.quizService = quizService;
        this.soumissionRepository = soumissionRepository;
        this.evaluationRepository = evaluationRepository;
        this.etudiantRepository = etudiantRepository;
        this.enregistrementReponsesService = enregistrementReponsesService;
        this.correctionDeclencheur = correctionDeclencheur;
        this.tempsEpreuveService = tempsEpreuveService;
        this.soumissionMapper = soumissionMapper;
        this.soumissionAccesService = soumissionAccesService;
        this.antiTricheService = antiTricheService;
        this.clotureCopiesService = clotureCopiesService;
    }

    /**
     * Démarrer ou reprendre une session d'évaluation pour l'étudiant connecté (Diagramme de Séquence DS-10).
     * Une copie existante est toujours renvoyée selon son état (en cours, expirée puis clôturée, remise),
     * même après la fermeture de l'évaluation ; les conditions d'ouverture ne s'appliquent qu'à la création.
     */
    public SoumissionDetailResponse demarrerOuReprendre(Long evaluationId, String userEmail) {
        Etudiant etudiant = soumissionAccesService.etudiantConnecte(userEmail);
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Évaluation introuvable (ID: " + evaluationId + ")"));

        // L'étudiant doit appartenir à l'une des promotions assignées à l'évaluation
        boolean promotionValide = etudiant.getPromotion() != null && evaluation.getPromotions().stream()
                .anyMatch(p -> p.getId().equals(etudiant.getPromotion().getId()));
        if (!promotionValide) {
            throw new AccesRefuseException("Vous n'êtes pas inscrit dans une promotion assignée à cette évaluation.");
        }

        // Verrou sur l'étudiant : deux démarrages simultanés (double clic, deux onglets) ne doivent pas créer
        // deux copies ; le second attend la fin du premier puis reprend la copie qu'il a créée
        etudiantRepository.findByIdForUpdate(etudiant.getId());

        // Vérifier si une soumission existe déjà (verrouillée : la clôture automatique peut la viser au même moment)
        Optional<Soumission> existanteOpt = soumissionRepository.findByEvaluationIdAndEtudiantIdForUpdate(evaluationId, etudiant.getId());
        Soumission soumission;

        if (existanteOpt.isPresent()) {
            soumission = existanteOpt.get();

            // Copie abandonnée puis rouverte après la marge : clôturée ici plutôt que renvoyée avec un temps nul,
            // sinon le chrono du front la soumettrait et lèverait une fausse alerte de soumission tardive
            if (soumission.getStatut() == StatutSoumission.EN_COURS && tempsEpreuveService.estExpiree(soumission)) {
                clotureCopiesService.cloturerExpiree(soumission);
                log.info("Clôture à la reprise de la soumission ID={} (temps écoulé)", soumission.getId());
            }
            // Copie remise : statut seulement, pas de questions ni de réponses
            if (soumission.getStatut() != StatutSoumission.EN_COURS) {
                return soumissionMapper.versDetailSansQuestions(soumission);
            }
            log.info("Reprise de la soumission ID={} pour l'étudiant {}", soumission.getId(), userEmail);
        } else {
            verifierOuverte(evaluation, tempsEpreuveService.maintenant());
            soumission = new Soumission();
            soumission.setEvaluation(evaluation);
            soumission.setEtudiant(etudiant);
            soumission.setDateDebut(tempsEpreuveService.maintenant());
            soumission.setStatut(StatutSoumission.EN_COURS);
            soumission.setNbChangementsOnglet(0);
            soumission = soumissionRepository.save(soumission);
            log.info("Création d'une nouvelle soumission ID={} pour l'étudiant {}", soumission.getId(), userEmail);
        }

        return soumissionMapper.versDetail(soumission);
    }

    /** Conditions pour créer une copie : évaluation publiée ou en cours, entre ses dates d'ouverture et de fermeture. */
    private void verifierOuverte(Evaluation evaluation, LocalDateTime now) {
        if (now.isBefore(evaluation.getDateOuverture())) {
            throw new IllegalArgumentException("Cette évaluation n'est pas encore ouverte.");
        }
        if (now.isAfter(evaluation.getDateFermeture())) {
            throw new IllegalArgumentException("Cette évaluation est déjà clôturée.");
        }
        if (evaluation.getStatut() != StatutEvaluation.PUBLIEE && evaluation.getStatut() != StatutEvaluation.EN_COURS) {
            throw new IllegalArgumentException("Cette évaluation n'est pas accessible (statut : " + evaluation.getStatut() + ").");
        }
    }

    /**
     * Sauvegarde automatique des réponses (conforme au Diagramme de Séquence 2).
     */
    public SauvegardeResponse sauvegarderReponses(Long soumissionId, SauvegardeReponsesRequest request, String userEmail) {
        Etudiant etudiant = soumissionAccesService.etudiantConnecte(userEmail);
        Soumission soumission = soumissionAccesService.copieVerrouilleeDeLEtudiant(soumissionId, etudiant);

        // 1. Vérifie statut == EN_COURS
        if (soumission.getStatut() != StatutSoumission.EN_COURS) {
            throw new SoumissionNonModifiableException("Action rejetée : la copie a déjà été soumise ou verrouillée.");
        }

        // 2. Vérifie !isExpiree()
        if (tempsEpreuveService.estExpiree(soumission)) {
            log.warn("Tentative de sauvegarde tardive rejetée pour soumission ID={}", soumissionId);
            throw new TempsEcouleException("Action rejetée : temps imparti expiré.");
        }

        // 3. Contrôle de version : refuse d'écraser des réponses enregistrées depuis un autre onglet ou appareil
        Integer versionAttendue = request.getVersionAttendue();
        if (versionAttendue != null && !versionAttendue.equals(soumission.getVersionReponses())) {
            throw new VersionObsoleteException(
                    "Votre copie a été modifiée depuis un autre onglet ou appareil. Rechargez-la avant de continuer.",
                    soumission.getVersionReponses());
        }

        // 4. Quiz chronométré : seule la réponse à la question en cours est enregistrée (ni retour en arrière,
        //    ni réponse en avance, ni réponse hors délai)
        List<ReponseItemDto> reponses = request.getReponses();
        List<QuestionQuiz> questionsQuiz = quizService.questionsQuiz(soumission);
        if (!questionsQuiz.isEmpty()) {
            reponses = quizService.reponsesAutorisees(soumission, questionsQuiz, reponses);
        }

        // 5. Mise à jour ou création des réponses
        int nbSauvegardees = enregistrementReponsesService.enregistrer(soumission, reponses);

        // 6. Nouvelle version des réponses (le verrou sur la copie garantit un incrément sans perte)
        int version = soumission.getVersionReponses() + 1;
        soumission.setVersionReponses(version);

        return new SauvegardeResponse(tempsEpreuveService.maintenant(), "Sauvegarde effectuée avec succès.",
                nbSauvegardees, version);
    }

    /**
     * Soumission finale de la copie (conforme au Diagramme de Séquence 1).
     * Pas de rollback sur TempsEcouleException : le verrouillage de la copie et l'alerte doivent être persistés.
     */
    @Transactional(noRollbackFor = TempsEcouleException.class)
    public SoumissionResponse soumettreCopie(Long soumissionId, String userEmail) {
        Etudiant etudiant = soumissionAccesService.etudiantConnecte(userEmail);
        Soumission soumission = soumissionAccesService.copieVerrouilleeDeLEtudiant(soumissionId, etudiant);

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
            reserverCorrection(soumission, now);
            soumissionRepository.save(soumission);
            antiTricheService.signalerSoumissionTardive(soumission);

            // La copie figée est corrigée comme les autres (sinon elle resterait SOUMIS indéfiniment)
            correctionDeclencheur.apresCommit(soumission.getId());

            throw new TempsEcouleException("Temps dépassé au-delà de la marge de tolérance. Votre copie a été verrouillée à l'état de votre dernière sauvegarde.");
        }

        // [Cas Normal : Temps dans les clous ou dans la marge]
        soumission.setStatut(StatutSoumission.SOUMIS);
        soumission.setDateRemise(now);
        reserverCorrection(soumission, now);
        soumission = soumissionRepository.save(soumission);

        // Déclenche CorrectionService en arrière-plan (async), une fois le statut SOUMIS commité
        correctionDeclencheur.apresCommit(soumission.getId());

        return soumissionMapper.versResponse(soumission, "Copie soumise avec succès.");
    }

    /** La relance planifiée ne reprend la copie que si la correction lancée à la remise ne l'a pas prise en charge à temps (DS-12). */
    private static void reserverCorrection(Soumission soumission, LocalDateTime maintenant) {
        soumission.setProchaineTentativeLe(maintenant.plusMinutes(CorrectionService.DELAI_AVANT_RELANCE_MINUTES));
    }

    /**
     * Quiz chronométré : passe à la question suivante si la copie est encore sur `indexActuel` (un double clic
     * n'avance pas deux fois) et renvoie la copie sur sa nouvelle question.
     */
    public SoumissionDetailResponse passerQuestionSuivante(Long soumissionId, int indexActuel, String userEmail) {
        Etudiant etudiant = soumissionAccesService.etudiantConnecte(userEmail);
        Soumission soumission = soumissionAccesService.copieVerrouilleeDeLEtudiant(soumissionId, etudiant);
        if (soumission.getStatut() != StatutSoumission.EN_COURS) {
            throw new SoumissionNonModifiableException("Action rejetée : la copie a déjà été soumise ou verrouillée.");
        }
        if (tempsEpreuveService.estExpiree(soumission)) {
            throw new TempsEcouleException("Action rejetée : temps imparti expiré.");
        }
        List<QuestionQuiz> questionsQuiz = quizService.questionsQuiz(soumission);
        if (questionsQuiz.isEmpty()) {
            throw new IllegalArgumentException("Cette évaluation n'est pas un quiz chronométré.");
        }
        quizService.passerALaSuivante(soumission, questionsQuiz, indexActuel);
        return soumissionMapper.versDetail(soumission);
    }

    /**
     * Récupérer le détail d'une soumission. L'enseignant de l'évaluation et l'administrateur voient toujours
     * la copie complète ; l'étudiant ne reçoit les énoncés que pendant sa composition (copie EN_COURS dans
     * les temps), comme à la reprise (DS-10) : d'autres étudiants (tiers-temps) peuvent encore composer.
     */
    @Transactional(readOnly = true)
    public SoumissionDetailResponse getDetail(Long soumissionId, String userEmail) {
        Soumission soumission = soumissionAccesService.copieConsultable(soumissionId, userEmail);
        boolean consulteeParSonEtudiant = soumission.getEtudiant().getEmail().equalsIgnoreCase(userEmail);
        boolean enComposition = soumission.getStatut() == StatutSoumission.EN_COURS
                && !tempsEpreuveService.estExpiree(soumission);
        if (consulteeParSonEtudiant && !enComposition) {
            return soumissionMapper.versDetailSansQuestions(soumission);
        }
        return soumissionMapper.versDetail(soumission);
    }
}
