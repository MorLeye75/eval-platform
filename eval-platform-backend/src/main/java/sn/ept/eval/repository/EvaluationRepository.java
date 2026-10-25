package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;

import java.util.Collection;
import java.util.List;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    List<Evaluation> findByEnseignantId(Long enseignantId);
    List<Evaluation> findByStatut(StatutEvaluation statut);
    List<Evaluation> findByPromotionsId(Long promotionId);
    List<Evaluation> findByPromotionsIdAndStatutInOrderByDateOuvertureDesc(Long promotionId, Collection<StatutEvaluation> statuts);
}
