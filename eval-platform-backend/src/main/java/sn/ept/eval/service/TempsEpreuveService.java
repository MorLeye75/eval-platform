package sn.ept.eval.service;

import org.springframework.stereotype.Service;
import sn.ept.eval.model.AmenagementTemps;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.repository.AmenagementTempsRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Règles de temps d'une épreuve : durée effective (aménagement inclus), fin plafonnée par la fermeture
 * de l'évaluation, marge de tolérance et temps restant. Source unique pour la passation, la clôture
 * automatique des copies et, à terme, la supervision enseignant.
 */
@Service
public class TempsEpreuveService {

    /** Délai accordé après la fin théorique (latence réseau, dernier auto-save). */
    public static final int MARGE_TOLERANCE_MINUTES = 2;

    private final AmenagementTempsRepository amenagementTempsRepository;
    private final Clock clock;

    public TempsEpreuveService(AmenagementTempsRepository amenagementTempsRepository, Clock clock) {
        this.amenagementTempsRepository = amenagementTempsRepository;
        this.clock = clock;
    }

    public LocalDateTime maintenant() {
        return LocalDateTime.now(clock);
    }

    /** Durée accordée à l'étudiant : aménagement personnalisé s'il existe, sinon durée standard. */
    public int dureeEffectiveMinutes(Soumission soumission) {
        return amenagementTempsRepository
                .findByEtudiantIdAndEvaluationId(soumission.getEtudiant().getId(), soumission.getEvaluation().getId())
                .map(AmenagementTemps::getDureePersonnalisee)
                .orElse(soumission.getEvaluation().getDureeStandard());
    }

    /** Fin de l'épreuve : début + durée effective, plafonnée par la fermeture de l'évaluation. */
    public LocalDateTime finEpreuve(Soumission soumission) {
        return finEpreuve(soumission, dureeEffectiveMinutes(soumission));
    }

    /** Variante pour un appelant qui connaît déjà la durée effective (évite de relire l'aménagement). */
    public LocalDateTime finEpreuve(Soumission soumission, int dureeEffectiveMinutes) {
        LocalDateTime fin = soumission.getDateDebut().plusMinutes(dureeEffectiveMinutes);
        LocalDateTime fermeture = soumission.getEvaluation().getDateFermeture();
        return (fermeture != null && fermeture.isBefore(fin)) ? fermeture : fin;
    }

    /**
     * Durées effectives d'un lot de copies (id de copie → minutes), avec une seule requête sur les aménagements
     * des évaluations concernées. L'évaluation de chaque copie doit être chargée.
     */
    public Map<Long, Integer> dureesEffectivesMinutes(Collection<Soumission> soumissions) {
        if (soumissions.isEmpty()) return Map.of();
        Set<Long> evaluationIds = soumissions.stream().map(s -> s.getEvaluation().getId()).collect(Collectors.toSet());
        Map<String, Integer> amenagements = new HashMap<>();
        for (AmenagementTemps a : amenagementTempsRepository.findByEvaluationIdIn(evaluationIds)) {
            amenagements.put(cle(a.getEtudiant().getId(), a.getEvaluation().getId()), a.getDureePersonnalisee());
        }
        Map<Long, Integer> durees = new HashMap<>();
        for (Soumission s : soumissions) {
            Integer amenagee = amenagements.get(cle(s.getEtudiant().getId(), s.getEvaluation().getId()));
            durees.put(s.getId(), amenagee != null ? amenagee : s.getEvaluation().getDureeStandard());
        }
        return durees;
    }

    private static String cle(Long etudiantId, Long evaluationId) {
        return etudiantId + ":" + evaluationId;
    }

    /** Vrai si la fin de l'épreuve, marge de tolérance comprise, est dépassée. */
    public boolean estExpiree(Soumission soumission) {
        return estExpiree(soumission, dureeEffectiveMinutes(soumission));
    }

    /** Variante pour un appelant qui connaît déjà la durée effective (évite de relire l'aménagement). */
    public boolean estExpiree(Soumission soumission, int dureeEffectiveMinutes) {
        return maintenant().isAfter(finEpreuve(soumission, dureeEffectiveMinutes).plusMinutes(MARGE_TOLERANCE_MINUTES));
    }

    public long tempsRestantSecondes(Soumission soumission) {
        return tempsRestantSecondes(soumission, dureeEffectiveMinutes(soumission));
    }

    public long tempsRestantSecondes(Soumission soumission, int dureeEffectiveMinutes) {
        return Math.max(0, Duration.between(maintenant(), finEpreuve(soumission, dureeEffectiveMinutes)).getSeconds());
    }
}
