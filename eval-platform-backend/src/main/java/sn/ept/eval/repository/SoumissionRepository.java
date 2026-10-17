package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Soumission;

import java.util.List;
import java.util.Optional;

@Repository
public interface SoumissionRepository extends JpaRepository<Soumission, Long> {
    Optional<Soumission> findByEvaluationIdAndEtudiantId(Long evaluationId, Long etudiantId);
    List<Soumission> findByEvaluationId(Long evaluationId);
    List<Soumission> findByEtudiantId(Long etudiantId);
}
