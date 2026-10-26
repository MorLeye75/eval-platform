package sn.ept.eval.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.enums.StatutSoumission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SoumissionRepository extends JpaRepository<Soumission, Long> {
    boolean existsByEvaluationId(Long evaluationId);
    boolean existsByEvaluationIdAndEtudiantId(Long evaluationId, Long etudiantId);
    long countByEvaluationId(Long evaluationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Soumission s where s.id = :id")
    Optional<Soumission> findByIdForUpdate(@Param("id") Long id);

    Optional<Soumission> findByEvaluationIdAndEtudiantId(Long evaluationId, Long etudiantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Soumission s where s.evaluation.id = :evaluationId and s.etudiant.id = :etudiantId")
    Optional<Soumission> findByEvaluationIdAndEtudiantIdForUpdate(@Param("evaluationId") Long evaluationId,
                                                                  @Param("etudiantId") Long etudiantId);
    List<Soumission> findByEvaluationId(Long evaluationId);

    List<Soumission> findByStatut(StatutSoumission statut);

    List<Soumission> findByStatutAndDateModificationBefore(StatutSoumission statut, LocalDateTime seuil);
    List<Soumission> findByEtudiantId(Long etudiantId);
}
