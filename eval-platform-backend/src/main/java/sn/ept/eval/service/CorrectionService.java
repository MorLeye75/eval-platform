package sn.ept.eval.service;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.config.Judge0Proprietes;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.service.judge0.Judge0Client;
import sn.ept.eval.service.judge0.Judge0Client.CasAExecuter;
import sn.ept.eval.service.judge0.Judge0Client.ResultatCas;
import sn.ept.eval.service.judge0.Judge0IndisponibleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Correction automatique d'une copie remise (DS-01, DS-12) : QCM notés « bonne réponse exacte ou zéro »,
 * code exécuté par Judge0 et noté au prorata des cas de test réussis.
 *
 * Trois phases, pour ne garder ni verrou ni transaction pendant les appels à Judge0 :
 * 1. sous verrou : prise en charge (correction_demarree_le, le « jeton ») et copie des données à exécuter ;
 * 2. hors transaction : exécutions sur Judge0, 10 min au plus ;
 * 3. sous verrou : écriture des notes, seulement si la copie est toujours SOUMIS et le jeton inchangé.
 *
 * Judge0 indisponible : nouvelle tentative 1, 2, 4 puis 8 min plus tard ; à la 5e, la copie passe en
 * correction manuelle (QCM notés, code sans note). Une erreur imprévue compte de la même façon.
 */
@Service
public class CorrectionService {

    private static final Logger log = LoggerFactory.getLogger(CorrectionService.class);

    /** Au-delà, une prise en charge est considérée comme abandonnée (serveur arrêté pendant la correction). */
    public static final int PRISE_EN_CHARGE_MINUTES = 15;
    public static final int TENTATIVES_MAX = 5;
    /**
     * Remise, clôture et relance réservent la copie ce temps-là : la relance planifiée ne la remet pas en file
     * tant que la correction lancée peut encore la prendre en charge.
     */
    public static final int DELAI_AVANT_RELANCE_MINUTES = 5;

    private final SoumissionRepository soumissionRepository;
    private final Judge0Client judge0;
    private final CorrectionsEnFile enFile;
    private final Clock clock;
    private final Duration dureeMaxExecution;
    /** Une transaction courte par phase : toute erreur l'annule entièrement (pas de notes partielles). */
    private final TransactionTemplate transaction;
    private final NotificationService notificationService;

    public CorrectionService(SoumissionRepository soumissionRepository, Judge0Client judge0, CorrectionsEnFile enFile,
                             Clock clock, Judge0Proprietes judge0Proprietes,
                             PlatformTransactionManager transactionManager, NotificationService notificationService) {
        this.soumissionRepository = soumissionRepository;
        this.notificationService = notificationService;
        this.judge0 = judge0;
        this.enFile = enFile;
        this.clock = clock;
        this.dureeMaxExecution = judge0Proprietes.dureeMaxExecution();
        this.transaction = new TransactionTemplate(transactionManager);
        // Nouvelle transaction même si l'appel arrive pendant la fin d'une autre (afterCommit d'une remise)
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Copie d'une évaluation sans code (QCM, quiz) : exécuteur rapide. */
    @Async("correctionTaskExecutor")
    public CompletableFuture<Void> corrigerSoumissionAsync(Long soumissionId) {
        return corrigerPuisLiberer(soumissionId);
    }

    /** Copie d'une évaluation avec du code : exécuteur limité au nombre de workers Judge0. */
    @Async("correctionJudge0Executor")
    public CompletableFuture<Void> corrigerSoumissionAvecCodeAsync(Long soumissionId) {
        return corrigerPuisLiberer(soumissionId);
    }

    private CompletableFuture<Void> corrigerPuisLiberer(Long soumissionId) {
        try {
            corriger(soumissionId);
        } finally {
            enFile.retirer(soumissionId);
        }
        return CompletableFuture.completedFuture(null);
    }

    /** Correction complète, dans le thread appelant. N'échoue jamais : les erreurs sont enregistrées sur la copie. */
    void corriger(Long soumissionId) {
        log.info("[Correction] Démarrage pour la soumission ID={}", soumissionId);
        LocalDateTime jeton = null; // reste null si l'erreur survient avant la prise en charge
        try {
            PriseEnCharge prise = transaction.execute(tx -> prendreEnCharge(soumissionId));
            if (prise == null) return;
            jeton = prise.jeton();

            Execution execution = executer(soumissionId, prise.questions(), clock.instant().plus(dureeMaxExecution));

            LocalDateTime jetonPhase1 = jeton;
            transaction.executeWithoutResult(tx -> enregistrer(soumissionId, jetonPhase1, execution));
        } catch (RuntimeException e) {
            enregistrerErreur(soumissionId, jeton, e);
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // Phase 1 — prise en charge sous verrou
    // ---------------------------------------------------------------------------------------------------

    private PriseEnCharge prendreEnCharge(Long soumissionId) {
        // Verrou : remise, clôture automatique et relance simultanées sont sérialisées
        Soumission soumission = soumissionRepository.findByIdForUpdate(soumissionId).orElse(null);
        if (soumission == null) {
            log.warn("[Correction] Soumission ID={} introuvable", soumissionId);
            return null;
        }
        // Idempotence : une copie déjà corrigée (ou rouverte) n'est pas recorrigée, la note de l'enseignant est conservée
        if (soumission.getStatut() != StatutSoumission.SOUMIS) {
            log.info("[Correction] Soumission ID={} ignorée (statut {})", soumissionId, soumission.getStatut());
            return null;
        }
        LocalDateTime maintenant = maintenant();
        LocalDateTime demarree = soumission.getCorrectionDemarreeLe();
        if (demarree != null && demarree.isAfter(maintenant.minusMinutes(PRISE_EN_CHARGE_MINUTES))) {
            log.info("[Correction] Soumission ID={} déjà en cours de correction depuis {}", soumissionId, demarree);
            return null;
        }
        soumission.setCorrectionDemarreeLe(maintenant);

        Map<Long, ReponseCode> reponsesParQuestion = reponsesCodeParQuestion(soumission);
        List<QuestionACorriger> questions = new ArrayList<>();
        for (Question q : soumission.getEvaluation().getQuestions()) {
            if (!(Hibernate.unproxy(q) instanceof QuestionCode question)) continue;
            ReponseCode reponse = reponsesParQuestion.get(question.getId());
            List<CasAExecuter> cas = question.getCasTests().stream()
                    .sorted(Comparator.comparing(CasDeTest::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(c -> new CasAExecuter(c.getId(), c.getEntree(), c.getSortieAttendue()))
                    .toList();
            questions.add(new QuestionACorriger(question.getId(),
                    reponse == null ? null : reponse.getCodeSoumis(),
                    reponse == null ? null : reponse.getLangage(), cas));
        }
        soumissionRepository.save(soumission);
        // Copiées dans des records : la phase 2 ne touche aucune entité (pas de chargement LAZY hors transaction)
        return new PriseEnCharge(maintenant, List.copyOf(questions));
    }

    // ---------------------------------------------------------------------------------------------------
    // Phase 2 — exécutions sur Judge0, hors transaction
    // ---------------------------------------------------------------------------------------------------

    private Execution executer(Long soumissionId, List<QuestionACorriger> questions, Instant echeance) {
        Map<Long, NoteCode> notes = new HashMap<>();
        for (QuestionACorriger q : questions) {
            if (q.code() == null || q.code().isBlank()) {
                notes.put(q.questionId(), NoteCode.zero());
            } else if (q.cas().isEmpty()) {
                notes.put(q.questionId(), NoteCode.manuelle()); // l'enseignant n'a prévu aucun cas de test
            } else if (!judge0.langageConnu(q.langage())) {
                log.warn("[Correction] Soumission ID={}, question ID={} : langage « {} » non exécutable, correction manuelle",
                        soumissionId, q.questionId(), q.langage());
                notes.put(q.questionId(), NoteCode.manuelle());
            } else {
                try {
                    notes.put(q.questionId(), NoteCode.executee(judge0.executer(q.code(), q.langage(), q.cas(), echeance)));
                } catch (Judge0IndisponibleException e) {
                    // Panne de Judge0, pas une erreur de l'étudiant : toute la copie sera recorrigée plus tard
                    log.warn("[Correction] Soumission ID={} : Judge0 indisponible ({})", soumissionId, e.getMessage());
                    return Execution.JUDGE0_INDISPONIBLE;
                }
            }
        }
        return new Execution(false, notes);
    }

    // ---------------------------------------------------------------------------------------------------
    // Phase 3 — écriture sous verrou
    // ---------------------------------------------------------------------------------------------------

    private void enregistrer(Long soumissionId, LocalDateTime jeton, Execution execution) {
        Soumission soumission = soumissionRepository.findByIdForUpdate(soumissionId).orElse(null);
        if (soumission == null || soumission.getStatut() != StatutSoumission.SOUMIS) {
            log.info("[Correction] Soumission ID={} modifiée pendant les exécutions : résultats abandonnés", soumissionId);
            return;
        }
        if (!Objects.equals(soumission.getCorrectionDemarreeLe(), jeton)) {
            log.info("[Correction] Soumission ID={} reprise par une autre correction : rien n'est écrit", soumissionId);
            return;
        }
        LocalDateTime maintenant = maintenant();
        soumission.setCorrectionDemarreeLe(null);

        if (execution.judge0Indisponible()) {
            int tentatives = soumission.getTentativesCorrection() + 1;
            soumission.setTentativesCorrection(tentatives);
            if (tentatives < TENTATIVES_MAX) {
                soumission.setProchaineTentativeLe(maintenant.plus(delaiAvantTentative(tentatives)));
                log.warn("[Correction] Soumission ID={} : tentative {}/{} échouée (Judge0), nouvel essai à {}",
                        soumissionId, tentatives, TENTATIVES_MAX, soumission.getProchaineTentativeLe());
            } else {
                // Repli prévu par le cahier des charges : l'enseignant corrige le code à la main
                noterQcm(soumission);
                soumission.setStatut(StatutSoumission.EN_CORRECTION);
                log.error("[Correction] Soumission ID={} : Judge0 indisponible après {} tentatives, correction manuelle",
                        soumissionId, tentatives);
            }
            soumissionRepository.save(soumission);
            return;
        }

        noterQcm(soumission);
        boolean toutEstNote = noterCode(soumission, execution.notes());

        if (toutEstNote) {
            double note = soumission.getReponses().stream()
                    .map(ReponseEtudiant::getNoteObtenue)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            soumission.setNoteFinale(arrondi(note));
            soumission.setStatut(StatutSoumission.EVALUE);
            // Résultats déjà publiés (copie remise ou corrigée tard) : l'étudiant est prévenu, dans cette
            // transaction (DS-15 : la notification suit l'événement s'il est annulé)
            if (Boolean.TRUE.equals(soumission.getEvaluation().getResultatsPublies())) {
                notificationService.resultatPublie(soumission);
            }
        } else {
            // Questions ouvertes, code sans cas de test : le reste est corrigé par l'enseignant
            soumission.setStatut(StatutSoumission.EN_CORRECTION);
        }
        soumissionRepository.save(soumission);
        log.info("[Correction] Terminée pour la soumission ID={}, statut={}", soumissionId, soumission.getStatut());
    }

    private void noterQcm(Soumission soumission) {
        for (ReponseEtudiant reponse : soumission.getReponses()) {
            // La question est un proxy LAZY de Question (classe abstraite) : il faut le déballer avant le cast
            Question question = (Question) Hibernate.unproxy(reponse.getQuestion());
            if (question instanceof QuestionQCM questionQcm && Hibernate.unproxy(reponse) instanceof ReponseQCM reponseQcm) {
                corrigerQuestionQcm(reponseQcm, questionQcm);
            }
        }
    }

    /**
     * Note les questions de code d'après les exécutions.
     *
     * @return true si toutes les questions de l'évaluation sont notées automatiquement
     */
    private boolean noterCode(Soumission soumission, Map<Long, NoteCode> notes) {
        Map<Long, ReponseCode> reponses = reponsesCodeParQuestion(soumission);
        boolean toutEstNote = true;
        for (Question q : soumission.getEvaluation().getQuestions()) {
            Object question = Hibernate.unproxy(q);
            if (question instanceof QuestionQCM) continue; // QCM et quiz chronométrés
            if (!(question instanceof QuestionCode questionCode)) {
                toutEstNote = false; // question ouverte
                continue;
            }
            NoteCode note = notes.get(questionCode.getId());
            ReponseCode reponse = reponses.get(questionCode.getId());
            if (note == null || note.mode() == ModeNote.MANUELLE) {
                toutEstNote = false;
            } else if (note.mode() == ModeNote.ZERO) {
                if (reponse != null) {
                    reponse.getResultatsCasTests().clear(); // résultats d'une correction précédente
                    reponse.setNoteObtenue(0.0);
                    reponse.setValideeParProf(true);
                    reponse.setCommentaireProf("Correction automatique : aucun code soumis.");
                }
            } else {
                if (reponse == null) throw new IllegalStateException("Réponse de code introuvable pour la question " + questionCode.getId());
                appliquerExecutions(reponse, questionCode, note.cas());
            }
        }
        return toutEstNote;
    }

    private void appliquerExecutions(ReponseCode reponse, QuestionCode question, List<ResultatCas> resultats) {
        Map<Long, CasDeTest> casParId = question.getCasTests().stream()
                .collect(Collectors.toMap(CasDeTest::getId, Function.identity()));
        reponse.getResultatsCasTests().clear(); // résultats d'une correction précédente
        long reussis = 0;
        for (ResultatCas r : resultats) {
            CasDeTest cas = casParId.get(r.casId());
            if (cas == null) throw new IllegalStateException("Cas de test ID=" + r.casId() + " supprimé pendant la correction");
            ResultatCasTest resultat = new ResultatCasTest();
            resultat.setCasDeTest(cas);
            resultat.setEstValide(r.reussi());
            resultat.setSortieObtenue(r.sortie());
            resultat.setTempsExecution(r.tempsSecondes());
            resultat.setMessageErreur(r.message());
            resultat.setVerdict(r.verdict());
            reponse.addResultat(resultat);
            if (r.reussi()) reussis++;
        }
        double points = question.getPoints() == null ? 0.0 : question.getPoints();
        reponse.setNoteObtenue(resultats.isEmpty() ? 0.0 : arrondi(points * reussis / resultats.size()));
        reponse.setValideeParProf(true);
        reponse.setCommentaireProf("Correction automatique : " + reussis + "/" + resultats.size() + " cas de test réussis.");
    }

    private void corrigerQuestionQcm(ReponseQCM reponseQCM, QuestionQCM questionQcm) {
        Set<Long> idsCorrects = questionQcm.getChoix().stream()
                .filter(ChoixReponse::getEstCorrect)
                .map(ChoixReponse::getId)
                .collect(Collectors.toSet());

        Set<Long> idsCoches = reponseQCM.getChoixCoches().stream()
                .map(ChoixReponse::getId)
                .collect(Collectors.toSet());

        reponseQCM.setNoteObtenue(idsCorrects.equals(idsCoches) ? questionQcm.getPoints() : 0.0);
        reponseQCM.setValideeParProf(true);
        reponseQCM.setCommentaireProf("Correction automatique QCM.");
    }

    // ---------------------------------------------------------------------------------------------------
    // Erreur imprévue
    // ---------------------------------------------------------------------------------------------------

    /**
     * La transaction en cours est déjà annulée. Une nouvelle compte l'échec, à condition que la copie soit encore
     * SOUMIS et qu'aucune autre correction ne l'ait reprise (jeton inchangé, ou prise en charge abandonnée).
     * Une erreur avant la prise en charge (jeton null) compte aussi : pas de reprise sans fin.
     */
    private void enregistrerErreur(Long soumissionId, LocalDateTime jeton, RuntimeException erreur) {
        try {
            transaction.executeWithoutResult(tx -> {
                Soumission soumission = soumissionRepository.findByIdForUpdate(soumissionId).orElse(null);
                if (soumission == null || soumission.getStatut() != StatutSoumission.SOUMIS) return;
                LocalDateTime maintenant = maintenant();
                LocalDateTime demarree = soumission.getCorrectionDemarreeLe();
                boolean priseEnChargeLibre = Objects.equals(demarree, jeton)
                        || (demarree != null && demarree.isBefore(maintenant.minusMinutes(PRISE_EN_CHARGE_MINUTES)));
                if (!priseEnChargeLibre) {
                    log.warn("[Correction] Soumission ID={} : erreur ignorée, une autre correction est en cours", soumissionId, erreur);
                    return;
                }
                int tentatives = soumission.getTentativesCorrection() + 1;
                soumission.setTentativesCorrection(tentatives);
                soumission.setCorrectionDemarreeLe(null);
                if (tentatives < TENTATIVES_MAX) {
                    soumission.setProchaineTentativeLe(maintenant.plus(delaiAvantTentative(tentatives)));
                    log.warn("[Correction] Soumission ID={} : tentative {}/{} en erreur (rien n'est enregistré), nouvel essai à {}",
                            soumissionId, tentatives, TENTATIVES_MAX, soumission.getProchaineTentativeLe(), erreur);
                } else {
                    // 5 échecs révèlent un bug, pas une panne : les QCM restent sans note (transaction annulée)
                    soumission.setStatut(StatutSoumission.EN_CORRECTION);
                    log.error("[Correction] Soumission ID={} : {} tentatives en erreur, correction manuelle",
                            soumissionId, tentatives, erreur);
                }
                soumissionRepository.save(soumission);
            });
        } catch (RuntimeException e) {
            if (e != erreur) e.addSuppressed(erreur); // la même panne peut ressurgir telle quelle
            log.error("[Correction] Soumission ID={} : échec non enregistré, reprise par la relance planifiée", soumissionId, e);
        }
    }

    // ---------------------------------------------------------------------------------------------------

    /** Délai avant la tentative suivante : 1, 2, 4 puis 8 min. */
    static Duration delaiAvantTentative(int tentativesEchouees) {
        return Duration.ofMinutes(1L << (tentativesEchouees - 1));
    }

    static double arrondi(double note) {
        return BigDecimal.valueOf(note).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Précision de la base (timestamp(6)) : le jeton relu doit être égal au jeton écrit. */
    private LocalDateTime maintenant() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static Map<Long, ReponseCode> reponsesCodeParQuestion(Soumission soumission) {
        Map<Long, ReponseCode> reponses = new HashMap<>();
        for (ReponseEtudiant r : soumission.getReponses()) {
            if (Hibernate.unproxy(r) instanceof ReponseCode reponseCode) {
                reponses.put(reponseCode.getQuestion().getId(), reponseCode);
            }
        }
        return reponses;
    }

    private record QuestionACorriger(Long questionId, String code, String langage, List<CasAExecuter> cas) {}

    private record PriseEnCharge(LocalDateTime jeton, List<QuestionACorriger> questions) {}

    private enum ModeNote { ZERO, MANUELLE, EXECUTEE }

    private record NoteCode(ModeNote mode, List<ResultatCas> cas) {
        static NoteCode zero() { return new NoteCode(ModeNote.ZERO, List.of()); }
        static NoteCode manuelle() { return new NoteCode(ModeNote.MANUELLE, List.of()); }
        static NoteCode executee(List<ResultatCas> cas) { return new NoteCode(ModeNote.EXECUTEE, cas); }
    }

    private record Execution(boolean judge0Indisponible, Map<Long, NoteCode> notes) {
        static final Execution JUDGE0_INDISPONIBLE = new Execution(true, Map.of());
    }
}
