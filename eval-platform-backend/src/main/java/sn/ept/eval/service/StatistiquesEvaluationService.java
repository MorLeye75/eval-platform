package sn.ept.eval.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.evaluation.StatistiquesEvaluationResponse;
import sn.ept.eval.dto.evaluation.StatistiquesEvaluationResponse.QuestionStat;
import sn.ept.eval.dto.evaluation.StatistiquesEvaluationResponse.Tranche;
import sn.ept.eval.exception.EvaluationNonModifiableException;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Question;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.ReponseEtudiantRepository;
import sn.ept.eval.repository.SoumissionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Statistiques d'une évaluation pour son enseignant (DS-09). Calculées à la demande sur les copies notées (EVALUE) et
 * ramenées sur 20, donc toujours à jour de la dernière note validée (DS-08) et comparables d'une évaluation à l'autre.
 */
@Service
@Transactional(readOnly = true)
public class StatistiquesEvaluationService {

    public static final double SEUIL_REUSSITE_SUR_20 = 10.0;
    static final int NB_TRANCHES = 10;

    private final EvaluationService evaluationService;
    private final SoumissionRepository soumissionRepository;
    private final ReponseEtudiantRepository reponseRepository;
    private final EtudiantRepository etudiantRepository;

    public StatistiquesEvaluationService(EvaluationService evaluationService, SoumissionRepository soumissionRepository,
                                         ReponseEtudiantRepository reponseRepository, EtudiantRepository etudiantRepository) {
        this.evaluationService = evaluationService;
        this.soumissionRepository = soumissionRepository;
        this.reponseRepository = reponseRepository;
        this.etudiantRepository = etudiantRepository;
    }

    public StatistiquesEvaluationResponse statistiques(Long evaluationId, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        Evaluation evaluation = evaluationService.evaluationDeLEnseignant(evaluationId, enseignant);
        exigerOuverte(evaluation);

        List<Question> questions = evaluation.getQuestions().stream()
                .sorted(Comparator.comparing(Question::getOrdre)).toList();
        double bareme = questions.stream().mapToDouble(Question::getPoints).sum();

        List<Double> notesSur20 = soumissionRepository.notesFinales(evaluationId).stream()
                .map(n -> sur20(n, bareme)).toList();
        long nbNotees = notesSur20.size();
        long nbCopies = soumissionRepository.countByEvaluationId(evaluationId);
        long nbEnCorrection = soumissionRepository.countByEvaluationIdAndStatut(evaluationId, StatutSoumission.SOUMIS)
                + soumissionRepository.countByEvaluationIdAndStatut(evaluationId, StatutSoumission.EN_CORRECTION);
        long nbAttendus = etudiantRepository.findByPromotionIdIn(
                evaluation.getPromotions().stream().map(p -> p.getId()).toList()).size();

        Map<Long, Double> totaux = reponseRepository.totauxParQuestion(evaluationId).stream()
                .collect(Collectors.toMap(ReponseEtudiantRepository.TotalQuestion::getQuestionId,
                        t -> t.getTotal() == null ? 0.0 : t.getTotal()));
        List<QuestionStat> parQuestion = new ArrayList<>();
        for (Question q : questions) {
            Double moyennePoints = null;
            Double taux = null;
            if (nbNotees > 0) {
                double total = totaux.getOrDefault(q.getId(), 0.0);
                moyennePoints = arrondi(total / nbNotees);
                taux = q.getPoints() > 0 ? arrondi(100.0 * total / (nbNotees * q.getPoints())) : null;
            }
            parQuestion.add(new QuestionStat(q.getId(), q.getOrdre(), q.getEnonce(), q.getPoints(), moyennePoints, taux));
        }

        return new StatistiquesEvaluationResponse(evaluationId, evaluation.getTitre(), evaluation.getStatut(), bareme,
                nbAttendus, nbCopies, nbNotees, nbEnCorrection,
                nbNotees == 0 ? null : arrondi(moyenne(notesSur20)),
                nbNotees == 0 ? null : arrondi(mediane(notesSur20)),
                nbNotees == 0 ? null : arrondi(notesSur20.stream().min(Double::compare).orElseThrow()),
                nbNotees == 0 ? null : arrondi(notesSur20.stream().max(Double::compare).orElseThrow()),
                nbNotees < 2 ? null : arrondi(ecartType(notesSur20)),
                nbNotees == 0 ? null : arrondi(100.0 * notesSur20.stream().filter(n -> n >= SEUIL_REUSSITE_SUR_20).count() / nbNotees),
                distribution(notesSur20), parQuestion);
    }

    /** Avant l'ouverture, aucune copie n'est possible : il n'y a rien à compter ni à exporter. */
    static void exigerOuverte(Evaluation evaluation) {
        if (evaluation.getStatut() == StatutEvaluation.BROUILLON || evaluation.getStatut() == StatutEvaluation.PUBLIEE) {
            throw new EvaluationNonModifiableException(
                    "Aucune copie n'est possible avant l'ouverture de l'évaluation : il n'y a pas encore de résultats.");
        }
    }

    // ------------------------------------------------------------------ calculs (statiques : testés sans base)

    static double sur20(double note, double bareme) {
        return bareme <= 0 ? 0.0 : note / bareme * 20.0;
    }

    static double moyenne(List<Double> valeurs) {
        return valeurs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    static double mediane(List<Double> valeurs) {
        List<Double> triees = valeurs.stream().sorted().toList();
        int n = triees.size();
        return n % 2 == 1 ? triees.get(n / 2) : (triees.get(n / 2 - 1) + triees.get(n / 2)) / 2.0;
    }

    /** Écart-type de la population (la promotion entière est l'effectif, pas un échantillon). */
    static double ecartType(List<Double> valeurs) {
        double m = moyenne(valeurs);
        return Math.sqrt(valeurs.stream().mapToDouble(v -> (v - m) * (v - m)).average().orElse(0.0));
    }

    /** 10 tranches de 2 points sur 20 ; 20 appartient à la dernière. */
    static List<Tranche> distribution(List<Double> notesSur20) {
        long[] nb = new long[NB_TRANCHES];
        for (double n : notesSur20) {
            int i = (int) Math.floor(n / (20.0 / NB_TRANCHES));
            nb[Math.max(0, Math.min(NB_TRANCHES - 1, i))]++;
        }
        List<Tranche> tranches = new ArrayList<>();
        for (int i = 0; i < NB_TRANCHES; i++) tranches.add(new Tranche(i * 2, i * 2 + 2, nb[i]));
        return tranches;
    }

    static double arrondi(double valeur) {
        return BigDecimal.valueOf(valeur).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
