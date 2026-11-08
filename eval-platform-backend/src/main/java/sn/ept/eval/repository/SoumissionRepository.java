package sn.ept.eval.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Copies d'un statut avec leur évaluation (durée, fermeture) : les règles de temps s'appliquent sans autre requête. */
    @Query("select s from Soumission s join fetch s.evaluation where s.statut = :statut")
    List<Soumission> findByStatutAvecEvaluation(@Param("statut") StatutSoumission statut);

    /**
     * Copies à corriger (relance planifiée, DS-12) : SOUMIS, sans prise en charge en cours (ou abandonnée avant
     * {@code seuilPriseEnCharge}) et dont la date de prochaine tentative est passée.
     */
    @Query("select s.id from Soumission s where s.statut = sn.ept.eval.model.enums.StatutSoumission.SOUMIS"
            + " and (s.correctionDemarreeLe is null or s.correctionDemarreeLe < :seuilPriseEnCharge)"
            + " and (s.prochaineTentativeLe is null or s.prochaineTentativeLe <= :maintenant)"
            + " order by s.id")
    List<Long> findIdsCorrectionARelancer(@Param("seuilPriseEnCharge") LocalDateTime seuilPriseEnCharge,
                                          @Param("maintenant") LocalDateTime maintenant, Pageable page);

    /** Réserve une copie SOUMIS avant de la remettre en file : pas de nouvelle relance avant {@code date}. */
    @Modifying(flushAutomatically = true)
    @Query("update Soumission s set s.prochaineTentativeLe = :date"
            + " where s.id = :id and s.statut = sn.ept.eval.model.enums.StatutSoumission.SOUMIS")
    int reporterProchaineTentative(@Param("id") Long id, @Param("date") LocalDateTime date);

    /** L'évaluation de la copie contient-elle au moins une question de code (exécutée par Judge0) ? */
    @Query("select case when count(q) > 0 then true else false end from QuestionCode q"
            + " where q.evaluation.id = (select s.evaluation.id from Soumission s where s.id = :id)")
    boolean evaluationContientCode(@Param("id") Long soumissionId);
    List<Soumission> findByEtudiantId(Long etudiantId);
}
