package sn.ept.eval.service;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.resultats.*;
import sn.ept.eval.model.*;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.QuestionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.SoumissionRepository.StatistiquesNotes;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consultation des résultats par l'étudiant (DS-13) : détail d'une copie et tableau de bord. Lecture seule.
 *
 * Confidentialité :
 * - aucune note avant la publication par l'enseignant, même automatique ;
 * - bonnes réponses des QCM seulement après la fermeture + 2 min (plus personne ne compose) ;
 * - cas de test masqués : verdict et temps seulement ;
 * - proposition de l'IA jamais renvoyée ;
 * - moyenne de la promotion seulement à partir de 5 copies notées.
 */
@Service
@Transactional(readOnly = true)
public class ResultatsService {

    /** En dessous, quelques étudiants qui comparent leurs notes retrouveraient celle d'un autre. */
    static final int COPIES_MIN_MOYENNE = 5;

    private final SoumissionAccesService soumissionAccesService;
    private final SoumissionRepository soumissionRepository;
    private final QuestionRepository questionRepository;
    private final TempsEpreuveService tempsEpreuveService;

    public ResultatsService(SoumissionAccesService soumissionAccesService, SoumissionRepository soumissionRepository,
                            QuestionRepository questionRepository, TempsEpreuveService tempsEpreuveService) {
        this.soumissionAccesService = soumissionAccesService;
        this.soumissionRepository = soumissionRepository;
        this.questionRepository = questionRepository;
        this.tempsEpreuveService = tempsEpreuveService;
    }

    // ---------------------------------------------------------------------------------------------------
    // Détail d'une copie
    // ---------------------------------------------------------------------------------------------------

    public ResultatsCopieResponse resultatsCopie(Long soumissionId, String email) {
        Etudiant etudiant = soumissionAccesService.etudiantConnecte(email);
        Soumission copie = soumissionRepository.findById(soumissionId)
                .orElseThrow(() -> SoumissionAccesService.soumissionIntrouvable(soumissionId));
        soumissionAccesService.verifierProprietaire(copie, etudiant);
        Evaluation evaluation = copie.getEvaluation();

        if (copie.getStatut() == StatutSoumission.EN_COURS) {
            return ResultatsCopieResponse.sansNote(EtatResultats.NON_REMISE, copie.getId(), evaluation.getId(),
                    evaluation.getTitre(), null);
        }
        if (!estPubliee(copie)) {
            return ResultatsCopieResponse.sansNote(EtatResultats.EN_ATTENTE, copie.getId(), evaluation.getId(),
                    evaluation.getTitre(), copie.getDateRemise());
        }

        List<Question> questions = questionRepository.findByEvaluationIdOrderByOrdreAsc(evaluation.getId());
        boolean corrigeVisible = corrigeVisible(evaluation);
        Map<Long, ReponseEtudiant> reponses = copie.getReponses().stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), Function.identity(), (a, b) -> a));

        Double moyenne = moyenneAnonyme(statistiques(List.of(evaluation.getId())).get(evaluation.getId()));
        Double note = copie.getNoteFinale();
        Double ecart = (moyenne == null || note == null) ? null : CorrectionService.arrondi(note - moyenne);
        double totalPoints = questions.stream().mapToDouble(q -> q.getPoints() == null ? 0 : q.getPoints()).sum();

        return new ResultatsCopieResponse(EtatResultats.PUBLIES, copie.getId(), evaluation.getId(),
                evaluation.getTitre(), copie.getDateRemise(), note, totalPoints, copie.getFeedbackGeneral(),
                moyenne, ecart, corrigeVisible,
                questions.stream().map(q -> resultatQuestion(q, reponses.get(q.getId()), corrigeVisible)).toList());
    }

    private ResultatQuestionDto resultatQuestion(Question q, ReponseEtudiant r, boolean corrigeVisible) {
        Object question = Hibernate.unproxy(q);
        Object reponse = r == null ? null : Hibernate.unproxy(r);
        List<ChoixResultatDto> choix = List.of();
        List<CasTestResultatDto> casTests = List.of();
        String texte = null;
        String code = null;
        String langage = null;

        if (question instanceof QuestionQCM qcm) {
            Set<Long> coches = reponse instanceof ReponseQCM rq
                    ? rq.getChoixCoches().stream().map(ChoixReponse::getId).collect(Collectors.toSet())
                    : Set.of();
            choix = qcm.getChoix().stream()
                    .sorted(Comparator.comparing(ChoixReponse::getId))
                    .map(c -> new ChoixResultatDto(c.getId(), c.getTexte(), coches.contains(c.getId()),
                            corrigeVisible ? c.getEstCorrect() : null))
                    .toList();
        } else if (reponse instanceof ReponseOuverte ro) {
            texte = ro.getTexteSoumis(); // la proposition de l'IA n'est jamais renvoyée
        } else if (reponse instanceof ReponseCode rc) {
            code = rc.getCodeSoumis();
            langage = rc.getLangage();
            casTests = rc.getResultatsCasTests().stream()
                    .sorted(Comparator.comparing(rt -> rt.getCasDeTest().getId()))
                    .map(ResultatsService::resultatCas)
                    .toList();
        }

        ReponseEtudiant re = (ReponseEtudiant) reponse;
        // QCM avant le corrigé : « 2/2 points » à côté des choix cochés dirait lesquels sont justes, à un camarade
        // qui compose encore (tiers-temps). Seule la note totale est donnée ; les points reviennent avec le corrigé.
        boolean pointsMasques = question instanceof QuestionQCM && !corrigeVisible;
        Double note = (re == null || pointsMasques) ? null : re.getNoteObtenue();
        return new ResultatQuestionDto(q.getId(), q.getOrdre(), q.getType(), q.getEnonce(), q.getPoints(),
                note, re == null ? null : re.getCommentaireProf(),
                choix, texte, code, langage, casTests);
    }

    /**
     * Cas visible : déjà montré pendant l'épreuve (entrée, sortie attendue), donc toujours affiché.
     * Cas masqué : verdict et temps seulement (réutilisable, et la sortie d'erreur peut recopier l'entrée).
     */
    private static CasTestResultatDto resultatCas(ResultatCasTest r) {
        CasDeTest cas = r.getCasDeTest();
        boolean reussi = Boolean.TRUE.equals(r.getEstValide());
        if (Boolean.TRUE.equals(cas.getEstMasque())) {
            return new CasTestResultatDto(true, reussi, r.getVerdict(), r.getTempsExecution(), null, null, null, null);
        }
        return new CasTestResultatDto(false, reussi, r.getVerdict(), r.getTempsExecution(), cas.getEntree(),
                cas.getSortieAttendue(), r.getSortieObtenue(), r.getMessageErreur());
    }

    /** Plus personne ne compose après la fermeture + la marge : la fin de chaque épreuve y est plafonnée. */
    private boolean corrigeVisible(Evaluation evaluation) {
        return tempsEpreuveService.maintenant().isAfter(
                evaluation.getDateFermeture().plusMinutes(TempsEpreuveService.MARGE_TOLERANCE_MINUTES));
    }

    // ---------------------------------------------------------------------------------------------------
    // Tableau de bord
    // ---------------------------------------------------------------------------------------------------

    public PerformancesResponse performances(String email) {
        Etudiant etudiant = soumissionAccesService.etudiantConnecte(email);
        List<Soumission> copies = soumissionRepository.findCopiesPubliees(etudiant.getId());
        if (copies.isEmpty()) {
            return new PerformancesResponse(List.of(), null, null, null, 0);
        }

        Set<Long> evaluationIds = copies.stream().map(s -> s.getEvaluation().getId()).collect(Collectors.toSet());
        Map<Long, Double> baremes = questionRepository.totalPointsParEvaluation(evaluationIds).stream()
                .filter(t -> t.getTotal() != null)
                .collect(Collectors.toMap(QuestionRepository.TotalPoints::getEvaluationId,
                        QuestionRepository.TotalPoints::getTotal));
        Map<Long, StatistiquesNotes> stats = statistiques(evaluationIds);

        List<PointPerformanceDto> historique = new ArrayList<>();
        for (Soumission copie : copies) {
            Long evaluationId = copie.getEvaluation().getId();
            Double bareme = baremes.get(evaluationId);
            if (bareme == null || bareme <= 0 || copie.getNoteFinale() == null) continue; // pas de division par zéro
            Double moyenne = moyenneAnonyme(stats.get(evaluationId));
            historique.add(new PointPerformanceDto(copie.getId(), evaluationId, copie.getEvaluation().getTitre(),
                    copie.getDateRemise(), surVingt(copie.getNoteFinale(), bareme),
                    moyenne == null ? null : surVingt(moyenne, bareme)));
        }
        if (historique.isEmpty()) {
            return new PerformancesResponse(List.of(), null, null, null, 0);
        }

        List<PointPerformanceDto> comparables = historique.stream()
                .filter(p -> p.moyennePromotionSur20() != null).toList();
        Double moyennePromotion = comparables.isEmpty() ? null
                : moyenne(comparables.stream().map(PointPerformanceDto::moyennePromotionSur20).toList());
        Double ecart = comparables.isEmpty() ? null : CorrectionService.arrondi(
                moyenne(comparables.stream().map(PointPerformanceDto::noteSur20).toList()) - moyennePromotion);

        return new PerformancesResponse(List.copyOf(historique),
                moyenne(historique.stream().map(PointPerformanceDto::noteSur20).toList()),
                moyennePromotion, ecart, historique.size());
    }

    // ---------------------------------------------------------------------------------------------------

    private static boolean estPubliee(Soumission copie) {
        return copie.getStatut() == StatutSoumission.EVALUE
                && Boolean.TRUE.equals(copie.getEvaluation().getResultatsPublies());
    }

    private Map<Long, StatistiquesNotes> statistiques(Collection<Long> evaluationIds) {
        return soumissionRepository.statistiquesNotes(evaluationIds).stream()
                .collect(Collectors.toMap(StatistiquesNotes::getEvaluationId, Function.identity()));
    }

    /** Moyenne de la promotion, seulement à partir de {@value #COPIES_MIN_MOYENNE} copies notées. */
    static Double moyenneAnonyme(StatistiquesNotes stats) {
        if (stats == null || stats.getNbCopies() == null || stats.getNbCopies() < COPIES_MIN_MOYENNE
                || stats.getMoyenne() == null) {
            return null;
        }
        return CorrectionService.arrondi(stats.getMoyenne());
    }

    private static double surVingt(double note, double bareme) {
        return CorrectionService.arrondi(note / bareme * 20);
    }

    private static Double moyenne(List<Double> valeurs) {
        return CorrectionService.arrondi(valeurs.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
    }
}
