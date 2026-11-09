package sn.ept.eval.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    List<Evaluation> findByEnseignantId(Long enseignantId);
    List<Evaluation> findByStatut(StatutEvaluation statut);
    boolean existsByTitre(String titre);
    List<Evaluation> findByPromotionsId(Long promotionId);
    List<Evaluation> findByPromotionsIdAndStatutInOrderByDateOuvertureDesc(Long promotionId, Collection<StatutEvaluation> statuts);

    /**
     * Verrou partagé (FOR SHARE) : une dépublication ou une modification en cours (FOR UPDATE) est attendue, puis
     * l'évaluation est relue telle qu'elle a été validée. Compatible entre lecteurs : les démarrages simultanés des
     * étudiants ne se bloquent pas entre eux (DS-10, point ouvert du DS-04).
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from Evaluation e where e.id = :id")
    Optional<Evaluation> findByIdForShare(@Param("id") Long id);
}
