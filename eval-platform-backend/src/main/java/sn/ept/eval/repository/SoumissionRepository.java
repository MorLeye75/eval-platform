package sn.ept.eval.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Soumission;

import java.util.List;
import java.util.Optional;

@Repository
public interface SoumissionRepository extends JpaRepository<Soumission, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Soumission s where s.id = :id")
    Optional<Soumission> findByIdForUpdate(@Param("id") Long id);

    Optional<Soumission> findByEvaluationIdAndEtudiantId(Long evaluationId, Long etudiantId);
    List<Soumission> findByEvaluationId(Long evaluationId);
    List<Soumission> findByEtudiantId(Long etudiantId);
}
