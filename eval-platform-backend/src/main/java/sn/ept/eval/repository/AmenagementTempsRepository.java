package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.AmenagementTemps;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AmenagementTempsRepository extends JpaRepository<AmenagementTemps, Long> {
    Optional<AmenagementTemps> findByEtudiantIdAndEvaluationId(Long etudiantId, Long evaluationId);
    List<AmenagementTemps> findByEtudiantId(Long etudiantId);
    List<AmenagementTemps> findByEvaluationId(Long evaluationId);

    /** Aménagements de plusieurs évaluations en une requête (clôture des copies en lot). */
    List<AmenagementTemps> findByEvaluationIdIn(Collection<Long> evaluationIds);
}
