package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Question;

import java.util.Collection;
import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByEvaluationIdOrderByOrdreAsc(Long evaluationId);

    /** Total des points d'une évaluation (barème). */
    interface TotalPoints {
        Long getEvaluationId();
        Double getTotal();
    }

    /** Barème de plusieurs évaluations en une requête (tableau de bord étudiant, DS-13). */
    @Query("select q.evaluation.id as evaluationId, sum(q.points) as total from Question q"
            + " where q.evaluation.id in :evaluationIds group by q.evaluation.id")
    List<TotalPoints> totalPointsParEvaluation(@Param("evaluationIds") Collection<Long> evaluationIds);
}
