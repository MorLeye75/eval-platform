package sn.ept.eval.service;

import org.springframework.stereotype.Service;
import sn.ept.eval.model.AmenagementTemps;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.repository.AmenagementTempsRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

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

    /** Vrai si la fin de l'épreuve, marge de tolérance comprise, est dépassée. */
    public boolean estExpiree(Soumission soumission) {
        return maintenant().isAfter(finEpreuve(soumission).plusMinutes(MARGE_TOLERANCE_MINUTES));
    }

    public long tempsRestantSecondes(Soumission soumission) {
        return tempsRestantSecondes(soumission, dureeEffectiveMinutes(soumission));
    }

    public long tempsRestantSecondes(Soumission soumission, int dureeEffectiveMinutes) {
        return Math.max(0, Duration.between(maintenant(), finEpreuve(soumission, dureeEffectiveMinutes)).getSeconds());
    }
}
