package sn.ept.eval.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.evaluation.SupervisionResponse;
import sn.ept.eval.dto.evaluation.SupervisionResponse.AlerteSupervision;
import sn.ept.eval.dto.evaluation.SupervisionResponse.EtatCopie;
import sn.ept.eval.dto.evaluation.SupervisionResponse.EtudiantSupervise;
import sn.ept.eval.exception.EvaluationNonModifiableException;
import sn.ept.eval.model.AlerteAntiTriche;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.enums.StatutEvaluation;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.AlerteAntiTricheRepository;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.ReponseEtudiantRepository;
import sn.ept.eval.repository.ReponseEtudiantRepository.NbParCopie;
import sn.ept.eval.repository.SoumissionRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Supervision d'une évaluation par son enseignant (DS-17) : lecture seule, sans verrou (superviser ne ralentit jamais
 * les étudiants qui composent). Le « temps réel » est un sondage du frontend ; chaque appel est une lecture complète.
 */
@Service
@Transactional(readOnly = true)
public class SupervisionService {

    private final EvaluationService evaluationService;
    private final EtudiantRepository etudiantRepository;
    private final SoumissionRepository soumissionRepository;
    private final ReponseEtudiantRepository reponseRepository;
    private final AlerteAntiTricheRepository alerteRepository;

    public SupervisionService(EvaluationService evaluationService, EtudiantRepository etudiantRepository,
                              SoumissionRepository soumissionRepository, ReponseEtudiantRepository reponseRepository,
                              AlerteAntiTricheRepository alerteRepository) {
        this.evaluationService = evaluationService;
        this.etudiantRepository = etudiantRepository;
        this.soumissionRepository = soumissionRepository;
        this.reponseRepository = reponseRepository;
        this.alerteRepository = alerteRepository;
    }

    public SupervisionResponse supervision(Long evaluationId, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        Evaluation evaluation = evaluationService.evaluationDeLEnseignant(evaluationId, enseignant);
        if (evaluation.getStatut() == StatutEvaluation.BROUILLON || evaluation.getStatut() == StatutEvaluation.PUBLIEE) {
            throw new EvaluationNonModifiableException("L'évaluation n'est pas encore ouverte : il n'y a rien à superviser.");
        }

        // Étudiants attendus, plus les propriétaires de copies qui n'en feraient plus partie (changement de promotion)
        Map<Long, Etudiant> etudiants = new LinkedHashMap<>();
        etudiantRepository.findByPromotionIdIn(evaluation.getPromotions().stream().map(p -> p.getId()).toList())
                .forEach(e -> etudiants.put(e.getId(), e));
        Map<Long, Soumission> copies = new HashMap<>();
        for (Soumission s : soumissionRepository.findByEvaluationId(evaluationId)) {
            copies.put(s.getEtudiant().getId(), s);
            etudiants.putIfAbsent(s.getEtudiant().getId(), s.getEtudiant());
        }

        // Réponses réellement données par copie : trois requêtes groupées, jamais une par copie
        Map<Long, Integer> reponses = new HashMap<>();
        for (List<NbParCopie> lot : List.of(reponseRepository.reponsesOuvertesDonnees(evaluationId),
                reponseRepository.reponsesCodeDonnees(evaluationId), reponseRepository.reponsesQcmDonnees(evaluationId))) {
            lot.forEach(n -> reponses.merge(n.getSoumissionId(), n.getNb().intValue(), Integer::sum));
        }

        List<AlerteAntiTriche> alertes = alerteRepository.findBySoumissionEvaluationId(evaluationId);
        Map<Long, Long> alertesParCopie = alertes.stream()
                .collect(Collectors.groupingBy(a -> a.getSoumission().getId(), Collectors.counting()));

        int nbQuestions = evaluation.getQuestions().size();
        List<EtudiantSupervise> lignes = etudiants.values().stream()
                .sorted(Comparator.comparing((Etudiant e) -> e.getNom().toLowerCase(Locale.ROOT))
                        .thenComparing(e -> e.getPrenom().toLowerCase(Locale.ROOT)))
                .map(e -> {
                    Soumission copie = copies.get(e.getId());
                    return new EtudiantSupervise(e.getId(), e.getNom(), e.getPrenom(), etat(copie),
                            copie == null ? 0 : Math.min(nbQuestions, reponses.getOrDefault(copie.getId(), 0)),
                            copie == null || copie.getNbChangementsOnglet() == null ? 0 : copie.getNbChangementsOnglet(),
                            copie == null ? 0 : alertesParCopie.getOrDefault(copie.getId(), 0L).intValue());
                })
                .toList();

        List<AlerteSupervision> listeAlertes = alertes.stream()
                .sorted(Comparator.comparing(AlerteAntiTriche::getDateDetection).reversed())
                .map(a -> {
                    Etudiant e = a.getSoumission().getEtudiant();
                    return new AlerteSupervision(a.getId(), e.getId(), e.getPrenom() + " " + e.getNom(), a.getDescription(),
                            a.getNiveau(), a.getDateDetection());
                })
                .toList();

        int nbCommences = (int) lignes.stream().filter(l -> l.etat() != EtatCopie.NON_COMMENCE).count();
        int nbSoumis = (int) lignes.stream().filter(l -> l.etat() == EtatCopie.SOUMIS).count();
        return new SupervisionResponse(evaluationId, evaluation.getTitre(), evaluation.getStatut(), evaluation.getDateOuverture(),
                evaluation.getDateFermeture(), nbQuestions, lignes.size(), nbCommences, nbSoumis, listeAlertes.size(),
                lignes, listeAlertes);
    }

    private static EtatCopie etat(Soumission copie) {
        if (copie == null) return EtatCopie.NON_COMMENCE;
        return copie.getStatut() == StatutSoumission.EN_COURS ? EtatCopie.EN_COURS : EtatCopie.SOUMIS;
    }
}
