package sn.ept.eval.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    List<Evaluation> findByEnseignantId(Long enseignantId);
    List<Evaluation> findByStatut(StatutEvaluation statut);
    List<Evaluation> findByPromotionsId(Long promotionId);
    List<Evaluation> findByPromotionsIdAndStatutInOrderByDateOuvertureDesc(Long promotionId, Collection<StatutEvaluation> statuts);

    /** Évaluations d'un enseignant, les plus récentes d'abord (DS-03). */
    List<Evaluation> findByEnseignantIdOrderByDateOuvertureDesc(Long enseignantId);

    /** Verrou pessimiste (DS-04) : la dépublication ne croise pas un autre changement de statut. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Evaluation e where e.id = :id")
    Optional<Evaluation> findByIdForUpdate(@Param("id") Long id);

    /** Évaluations d'un statut dont la date d'ouverture est atteinte (DS-05). */
    List<Evaluation> findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation statut, LocalDateTime date);

    /** Évaluations d'un statut dont la date de fermeture est atteinte (DS-05). */
    List<Evaluation> findByStatutAndDateFermetureLessThanEqual(StatutEvaluation statut, LocalDateTime date);
}
