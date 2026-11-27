package sn.ept.eval.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.admin.VueEnsembleAdminResponses.Alerte;
import sn.ept.eval.dto.admin.VueEnsembleAdminResponses.EvaluationEnCours;
import sn.ept.eval.dto.admin.VueEnsembleAdminResponses.ParRole;
import sn.ept.eval.dto.admin.VueEnsembleAdminResponses.StatPromotion;
import sn.ept.eval.dto.admin.VueEnsembleAdminResponses.Statistiques;
import sn.ept.eval.dto.admin.VueEnsembleAdminResponses.Supervision;
import sn.ept.eval.dto.admin.VueEnsembleAdminResponses.TableauDeBord;
import sn.ept.eval.model.AlerteAntiTriche;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.AdministrateurRepository;
import sn.ept.eval.repository.AlerteAntiTricheRepository;
import sn.ept.eval.repository.EnseignantRepository;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.SoumissionRepository.NbParEvaluation;
import sn.ept.eval.repository.SoumissionRepository.NoteCopie;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vue d'ensemble de l'administrateur (DS-18) : trois lectures seules, sans verrou, calculées à la demande.
 * Aucune table de statistiques : les chiffres sont ceux des écrans de l'enseignant (DS-09, DS-17).
 */
@Service
@Transactional(readOnly = true)
public class AdminVueEnsembleService {

    private final EtudiantRepository etudiantRepository;
    private final EnseignantRepository enseignantRepository;
    private final AdministrateurRepository administrateurRepository;
    private final EvaluationRepository evaluationRepository;
    private final SoumissionRepository soumissionRepository;
    private final AlerteAntiTricheRepository alerteRepository;
    private final PromotionRepository promotionRepository;
    private final Clock clock;
    private final String fournisseurIA;

    public AdminVueEnsembleService(EtudiantRepository etudiantRepository, EnseignantRepository enseignantRepository,
                                   AdministrateurRepository administrateurRepository, EvaluationRepository evaluationRepository,
                                   SoumissionRepository soumissionRepository, AlerteAntiTricheRepository alerteRepository,
                                   PromotionRepository promotionRepository, Clock clock,
                                   @Value("${correction.ia.fournisseur:simule}") String fournisseurIA) {
        this.etudiantRepository = etudiantRepository;
        this.enseignantRepository = enseignantRepository;
        this.administrateurRepository = administrateurRepository;
        this.evaluationRepository = evaluationRepository;
        this.soumissionRepository = soumissionRepository;
        this.alerteRepository = alerteRepository;
        this.promotionRepository = promotionRepository;
        this.clock = clock;
        this.fournisseurIA = fournisseurIA;
    }

    public TableauDeBord tableauDeBord() {
        LocalDateTime depuis = LocalDateTime.now(clock).minusDays(7);
        long etudiants = etudiantRepository.countByActifTrue();
        long enseignants = enseignantRepository.countByActifTrue();
        long admins = administrateurRepository.countByActifTrue();
        return new TableauDeBord(etudiants + enseignants + admins, new ParRole(etudiants, enseignants, admins),
                evaluationRepository.countByStatut(StatutEvaluation.EN_COURS),
                evaluationRepository.countByStatut(StatutEvaluation.PUBLIEE),
                soumissionRepository.countByDateDebutGreaterThanEqual(depuis),
                alerteRepository.countByDateDetectionGreaterThanEqual(depuis),
                // Seulement ce que le serveur sait : les lectures ci-dessus ont réussi, et le connecteur IA est configuré.
                List.of(new sn.ept.eval.dto.admin.VueEnsembleAdminResponses.Service("Base de données", "OPERATIONNEL", null),
                        new sn.ept.eval.dto.admin.VueEnsembleAdminResponses.Service("Correction IA", "CONFIGURE",
                                "fournisseur : " + fournisseurIA)));
    }

    public Supervision supervision() {
        List<Evaluation> enCours = evaluationRepository.findByStatut(StatutEvaluation.EN_COURS).stream()
                .sorted(Comparator.comparing(Evaluation::getDateFermeture)).toList();
        List<Long> ids = enCours.stream().map(Evaluation::getId).toList();
        Map<Long, Long> commences = ids.isEmpty() ? Map.of() : parEvaluation(soumissionRepository.copiesParEvaluation(ids));
        Map<Long, Long> remises = ids.isEmpty() ? Map.of() : parEvaluation(soumissionRepository.copiesRemisesParEvaluation(ids));

        List<EvaluationEnCours> evaluations = enCours.stream()
                .map(e -> new EvaluationEnCours(e.getId(), e.getTitre(),
                        e.getEnseignant().getPrenom() + " " + e.getEnseignant().getNom(),
                        etudiantRepository.findByPromotionIdIn(e.getPromotions().stream().map(Promotion::getId).toList()).size(),
                        commences.getOrDefault(e.getId(), 0L), remises.getOrDefault(e.getId(), 0L),
                        e.getDateOuverture(), e.getDateFermeture()))
                .toList();

        List<Alerte> alertes = alerteRepository.findTop20ByOrderByDateDetectionDesc().stream().map(this::versAlerte).toList();
        return new Supervision(evaluations, alertes);
    }

    public Statistiques statistiques() {
        List<NoteCopie> notees = soumissionRepository.notesDesCopiesNotees();
        long nbCopies = soumissionRepository.count();
        long enCours = soumissionRepository.countByStatut(StatutSoumission.EN_COURS);

        Map<Long, List<Double>> parPromotion = new HashMap<>();
        List<Double> toutes = new ArrayList<>();
        for (NoteCopie n : notees) {
            if (n.getBareme() == null || n.getBareme() <= 0) continue;   // évaluation sans barème : pas de note sur 20 possible
            double sur20 = StatistiquesEvaluationService.sur20(n.getNote(), n.getBareme());
            toutes.add(sur20);
            parPromotion.computeIfAbsent(n.getPromotionId(), k -> new ArrayList<>()).add(sur20);
        }

        List<StatPromotion> promotions = promotionRepository.findAll().stream()
                .sorted(Comparator.comparing((Promotion p) -> p.getNom().toLowerCase(Locale.ROOT)).thenComparing(Promotion::getAnnee))
                .map(p -> {
                    List<Double> notes = parPromotion.getOrDefault(p.getId(), List.of());
                    return new StatPromotion(p.getId(), p.getNom(), p.getAnnee(), etudiantRepository.countByPromotionId(p.getId()),
                            evaluationRepository.countByPromotionsId(p.getId()), notes.size(), moyenne(notes), tauxReussite(notes));
                }).toList();

        return new Statistiques(evaluationRepository.count(), nbCopies, toutes.size(),
                nbCopies == 0 ? null : StatistiquesEvaluationService.arrondi(100.0 * (nbCopies - enCours) / nbCopies),
                moyenne(toutes), tauxReussite(toutes), promotions);
    }

    private static Double moyenne(List<Double> notes) {
        return notes.isEmpty() ? null : StatistiquesEvaluationService.arrondi(StatistiquesEvaluationService.moyenne(notes));
    }

    private static Double tauxReussite(List<Double> notes) {
        if (notes.isEmpty()) return null;
        return StatistiquesEvaluationService.arrondi(
                100.0 * notes.stream().filter(n -> n >= StatistiquesEvaluationService.SEUIL_REUSSITE_SUR_20).count() / notes.size());
    }

    private static Map<Long, Long> parEvaluation(List<NbParEvaluation> lignes) {
        return lignes.stream().collect(Collectors.toMap(NbParEvaluation::getEvaluationId, NbParEvaluation::getNb, Long::sum));
    }

    private Alerte versAlerte(AlerteAntiTriche a) {
        var copie = a.getSoumission();
        return new Alerte(a.getId(), copie.getEvaluation().getId(), copie.getEvaluation().getTitre(),
                copie.getEtudiant().getPrenom() + " " + copie.getEtudiant().getNom(), a.getDescription(), a.getNiveau(),
                a.getDateDetection());
    }
}
