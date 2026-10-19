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
import sn.ept.eval.model.enums.NiveauAlerte;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.*;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SoumissionService {

    private static final Logger log = LoggerFactory.getLogger(SoumissionService.class);
    public static final int MARGE_TOLERANCE_MINUTES = 2;
    public static final int SEUIL_ALERTE_MODEREE = 3;
    public static final int SEUIL_ALERTE_ELEVEE = 5;
    /** Au-delà de ce délai sans modification, une copie SOUMIS est considérée comme bloquée. */
    public static final int DELAI_CORRECTION_BLOQUEE_MINUTES = 5;

    private final SoumissionRepository soumissionRepository;
    private final EvaluationRepository evaluationRepository;
    private final EtudiantRepository etudiantRepository;
    private final QuestionRepository questionRepository;
    private final ChoixReponseRepository choixReponseRepository;
    private final ReponseEtudiantRepository reponseEtudiantRepository;
    private final AmenagementTempsRepository amenagementTempsRepository;
    private final AlerteAntiTricheRepository alerteAntiTricheRepository;
    private final CorrectionService correctionService;
    private final UtilisateurRepository utilisateurRepository;
    private final Clock clock;

    public SoumissionService(SoumissionRepository soumissionRepository,
                             EvaluationRepository evaluationRepository,
                             EtudiantRepository etudiantRepository,
                             QuestionRepository questionRepository,
                             ChoixReponseRepository choixReponseRepository,
                             ReponseEtudiantRepository reponseEtudiantRepository,
                             AmenagementTempsRepository amenagementTempsRepository,
                             AlerteAntiTricheRepository alerteAntiTricheRepository,
                             CorrectionService correctionService,
                             UtilisateurRepository utilisateurRepository,
                             Clock clock) {
        this.soumissionRepository = soumissionRepository;
        this.evaluationRepository = evaluationRepository;
        this.etudiantRepository = etudiantRepository;
        this.questionRepository = questionRepository;
        this.choixReponseRepository = choixReponseRepository;
        this.reponseEtudiantRepository = reponseEtudiantRepository;
        this.amenagementTempsRepository = amenagementTempsRepository;
        this.alerteAntiTricheRepository = alerteAntiTricheRepository;
        this.correctionService = correctionService;
        this.utilisateurRepository = utilisateurRepository;
        this.clock = clock;
    }

    /**
     * Démarrer ou reprendre une session d'évaluation pour l'étudiant connecté.
     */
    public SoumissionDetailResponse demarrerOuReprendre(Long evaluationId, String userEmail) {
        Etudiant etudiant = getEtudiantConnecte(userEmail);
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Évaluation introuvable (ID: " + evaluationId + ")"));

        LocalDateTime now = LocalDateTime.now(clock);
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

        return buildSoumissionDetailResponse(soumission, etudiant);
    }

    /**
     * Sauvegarde automatique des réponses (conforme au Diagramme de Séquence 2).
     */
    public SauvegardeResponse sauvegarderReponses(Long soumissionId, SauvegardeReponsesRequest request, String userEmail) {
        Etudiant etudiant = getEtudiantConnecte(userEmail);
        Soumission soumission = getSoumissionEtudiant(soumissionId, etudiant.getId());

        LocalDateTime now = LocalDateTime.now(clock);

        // 1. Vérifie statut == EN_COURS
        if (soumission.getStatut() != StatutSoumission.EN_COURS) {
            throw new SoumissionNonModifiableException("Action rejetée : la copie a déjà été soumise ou verrouillée.");
        }

        // 2. Vérifie !isExpiree()
        LocalDateTime expirationAvecMarge = getFinEpreuve(soumission, etudiant).plusMinutes(MARGE_TOLERANCE_MINUTES);

        if (now.isAfter(expirationAvecMarge)) {
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

        return new SauvegardeResponse(LocalDateTime.now(clock), "Sauvegarde effectuée avec succès.", nbSauvegardees);
    }

    /**
     * Soumission finale de la copie (conforme au Diagramme de Séquence 1).
     * Pas de rollback sur TempsEcouleException : le verrouillage de la copie et l'alerte doivent être persistés.
     */
    @Transactional(noRollbackFor = TempsEcouleException.class)
    public SoumissionResponse soumettreCopie(Long soumissionId, String userEmail) {
        Etudiant etudiant = getEtudiantConnecte(userEmail);
        Soumission soumission = getSoumissionEtudiant(soumissionId, etudiant.getId());

        if (soumission.getStatut() != StatutSoumission.EN_COURS) {
            return toSoumissionResponse(soumission, etudiant, "Cette copie a déjà été transmise.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime finEpreuve = getFinEpreuve(soumission, etudiant);
        LocalDateTime limiteMaxAvecMarge = finEpreuve.plusMinutes(MARGE_TOLERANCE_MINUTES);

        // [Cas critique : Temps dépassé au-delà de la marge de tolérance]
        if (now.isAfter(limiteMaxAvecMarge)) {
            log.warn("[Anti-Triche] Dépassement temporel critique pour la soumission ID={}", soumissionId);

            // Le serveur fige la copie à l'état du dernier auto-save
            soumission.setStatut(StatutSoumission.SOUMIS);
            soumission.setDateRemise(finEpreuve);
            soumissionRepository.save(soumission);

            // Détection anti-triche
            AlerteAntiTriche alerte = new AlerteAntiTriche(
                    "Soumission tardive : le temps imparti a été dépassé au-delà de la marge de tolérance.",
                    NiveauAlerte.ELEVE,
                    soumission
            );
            alerte.setDateDetection(now);
            alerteAntiTricheRepository.save(alerte);

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

        return toSoumissionResponse(soumission, etudiant, "Copie soumise avec succès.");
    }

    /**
     * Clôture automatique (tâche planifiée) : les copies EN_COURS dont le temps, marge comprise, est écoulé
     * sont figées à leur dernier auto-save puis corrigées. Cas typique : l'étudiant a fermé son navigateur.
     *
     * @return le nombre de copies clôturées
     */
    public int cloturerCopiesExpirees() {
        LocalDateTime now = LocalDateTime.now(clock);
        int nbCloturees = 0;
        for (Soumission soumission : soumissionRepository.findByStatut(StatutSoumission.EN_COURS)) {
            LocalDateTime finEpreuve = getFinEpreuve(soumission, soumission.getEtudiant());
            if (now.isAfter(finEpreuve.plusMinutes(MARGE_TOLERANCE_MINUTES))) {
                soumission.setStatut(StatutSoumission.SOUMIS);
                soumission.setDateRemise(finEpreuve);
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
        LocalDateTime seuil = LocalDateTime.now(clock).minusMinutes(DELAI_CORRECTION_BLOQUEE_MINUTES);
        List<Soumission> bloquees = soumissionRepository.findByStatutAndDateModificationBefore(StatutSoumission.SOUMIS, seuil);
        bloquees.forEach(s -> {
            log.warn("Relance de la correction de la soumission ID={} (bloquée en SOUMIS)", s.getId());
            declencherCorrectionApresCommit(s.getId());
        });
        return bloquees.size();
    }

    /**
     * Journaliser une sortie d'écran / changement d'onglet (Anti-triche).
     */
    public void enregistrerChangementOnglet(Long soumissionId, String userEmail) {
        Etudiant etudiant = getEtudiantConnecte(userEmail);
        // Verrou pessimiste : des signalements simultanés ne doivent pas perdre d'incrément
        Soumission soumission = soumissionRepository.findByIdForUpdate(soumissionId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Soumission introuvable (ID: " + soumissionId + ")"));
        verifierProprietaire(soumission, etudiant.getId());

        if (soumission.getStatut() != StatutSoumission.EN_COURS) {
            return;
        }

        int nb = soumission.getNbChangementsOnglet() != null ? soumission.getNbChangementsOnglet() + 1 : 1;
        soumission.setNbChangementsOnglet(nb);
        soumissionRepository.save(soumission);

        // Une alerte au franchissement de chaque seuil (et non à chaque événement suivant)
        if (nb == SEUIL_ALERTE_MODEREE || nb == SEUIL_ALERTE_ELEVEE) {
            AlerteAntiTriche alerte = new AlerteAntiTriche(
                    "Suspicion de triche : " + nb + " changements d'onglets ou sorties de plein écran détectés.",
                    nb == SEUIL_ALERTE_ELEVEE ? NiveauAlerte.ELEVE : NiveauAlerte.MODERE,
                    soumission
            );
            alerte.setDateDetection(LocalDateTime.now(clock));
            alerteAntiTricheRepository.save(alerte);
        }
    }

    /**
     * Récupérer le détail complet d'une soumission.
     */
    @Transactional(readOnly = true)
    public SoumissionDetailResponse getDetail(Long soumissionId, String userEmail) {
        Utilisateur utilisateur = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RessourceNonTrouveeException("Utilisateur introuvable pour le compte : " + userEmail));
        Soumission soumission = soumissionRepository.findById(soumissionId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Soumission introuvable (ID: " + soumissionId + ")"));

        boolean autorise = switch (utilisateur.getRole()) {
            case ETUDIANT -> soumission.getEtudiant().getId().equals(utilisateur.getId());
            case ENSEIGNANT -> soumission.getEvaluation().getEnseignant().getId().equals(utilisateur.getId());
            case ADMIN -> true;
        };
        if (!autorise) {
            throw new AccesRefuseException("Accès non autorisé à cette copie.");
        }

        return buildSoumissionDetailResponse(soumission, soumission.getEtudiant());
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

    private int getDureeEffectiveMinutes(Soumission soumission, Etudiant etudiant) {
        Optional<AmenagementTemps> amenagement = amenagementTempsRepository
                .findByEtudiantIdAndEvaluationId(etudiant.getId(), soumission.getEvaluation().getId());
        if (amenagement.isPresent() && amenagement.get().getDureePersonnalisee() != null) {
            return amenagement.get().getDureePersonnalisee();
        }
        return soumission.getEvaluation().getDureeStandard();
    }

    private boolean estDuBonType(ReponseEtudiant reponse, Question question) {
        if (question instanceof QuestionQCM) return reponse instanceof ReponseQCM;
        if (question instanceof QuestionCode) return reponse instanceof ReponseCode;
        return reponse instanceof ReponseOuverte;
    }

    /** Fin de l'épreuve : durée effective (aménagement inclus), plafonnée par la fermeture de l'évaluation. */
    private LocalDateTime getFinEpreuve(Soumission soumission, Etudiant etudiant) {
        LocalDateTime fin = soumission.getDateDebut().plusMinutes(getDureeEffectiveMinutes(soumission, etudiant));
        LocalDateTime fermeture = soumission.getEvaluation().getDateFermeture();
        return (fermeture != null && fermeture.isBefore(fin)) ? fermeture : fin;
    }

    private SoumissionDetailResponse buildSoumissionDetailResponse(Soumission soumission, Etudiant etudiant) {
        Evaluation eval = soumission.getEvaluation();
        int dureeMinutes = getDureeEffectiveMinutes(soumission, etudiant);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiration = getFinEpreuve(soumission, etudiant);
        long tempsRestantSec = Math.max(0, Duration.between(now, expiration).getSeconds());

        SoumissionDetailResponse resp = new SoumissionDetailResponse();
        resp.setId(soumission.getId());
        resp.setEvaluationId(eval.getId());
        resp.setTitreEvaluation(eval.getTitre());
        resp.setDescriptionEvaluation(eval.getDescription());
        resp.setStatut(soumission.getStatut());
        resp.setDateDebut(soumission.getDateDebut());
        resp.setDateRemise(soumission.getDateRemise());
        resp.setDureeMinutes(dureeMinutes);
        resp.setTempsRestantSecondes(tempsRestantSec);
        resp.setNbChangementsOnglet(soumission.getNbChangementsOnglet());

        // Récupérer toutes les questions ordonnées
        List<Question> questions = questionRepository.findByEvaluationIdOrderByOrdreAsc(eval.getId());
        List<ReponseEtudiant> reponses = reponseEtudiantRepository.findBySoumissionId(soumission.getId());

        Map<Long, ReponseEtudiant> reponsesParQuestion = reponses.stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (r1, r2) -> r1));

        List<QuestionExamenDto> questionsDtos = new ArrayList<>();
        for (Question q : questions) {
            QuestionExamenDto qDto = new QuestionExamenDto();
            qDto.setId(q.getId());
            qDto.setEnonce(q.getEnonce());
            qDto.setOrdre(q.getOrdre());
            qDto.setPoints(q.getPoints());
            qDto.setType(q.getType());

            if (q instanceof QuestionQCM qQcm) {
                qDto.setEstChoixMultiple(qQcm.getEstChoixMultiple());
                List<ChoixItemDto> choixDtos = qQcm.getChoix().stream()
                        .map(c -> new ChoixItemDto(c.getId(), c.getTexte()))
                        .collect(Collectors.toList());
                qDto.setChoixPossibles(choixDtos);
            }

            // Réponse actuellement enregistrée
            ReponseEtudiant rep = reponsesParQuestion.get(q.getId());
            if (rep != null) {
                ReponseItemDto repDto = new ReponseItemDto();
                repDto.setQuestionId(q.getId());
                if (rep instanceof ReponseQCM rq) {
                    List<Long> ids = rq.getChoixCoches().stream().map(ChoixReponse::getId).collect(Collectors.toList());
                    repDto.setChoixIds(ids);
                } else if (rep instanceof ReponseCode rc) {
                    repDto.setCodeSoumis(rc.getCodeSoumis());
                } else if (rep instanceof ReponseOuverte ro) {
                    repDto.setTexteSoumis(ro.getTexteSoumis());
                }
                qDto.setReponseActuelle(repDto);
            }

            questionsDtos.add(qDto);
        }

        resp.setQuestions(questionsDtos);
        return resp;
    }

    private SoumissionResponse toSoumissionResponse(Soumission soumission, Etudiant etudiant, String message) {
        SoumissionResponse r = new SoumissionResponse();
        r.setId(soumission.getId());
        r.setEvaluationId(soumission.getEvaluation().getId());
        r.setTitreEvaluation(soumission.getEvaluation().getTitre());
        r.setStatut(soumission.getStatut());
        r.setDateDebut(soumission.getDateDebut());
        r.setDateRemise(soumission.getDateRemise());
        r.setDureeMinutes(getDureeEffectiveMinutes(soumission, etudiant));
        r.setNbChangementsOnglet(soumission.getNbChangementsOnglet());
        r.setMessage(message);
        return r;
    }

    private Etudiant getEtudiantConnecte(String email) {
        return etudiantRepository.findByEmail(email)
                .orElseThrow(() -> new RessourceNonTrouveeException("Profil étudiant introuvable pour le compte : " + email));
    }

    private Soumission getSoumissionEtudiant(Long soumissionId, Long etudiantId) {
        Soumission s = soumissionRepository.findById(soumissionId)
                .orElseThrow(() -> new RessourceNonTrouveeException("Soumission introuvable (ID: " + soumissionId + ")"));
        verifierProprietaire(s, etudiantId);
        return s;
    }

    private void verifierProprietaire(Soumission soumission, Long etudiantId) {
        if (!soumission.getEtudiant().getId().equals(etudiantId)) {
            throw new AccesRefuseException("Accès non autorisé : cette copie appartient à un autre étudiant.");
        }
    }
}
