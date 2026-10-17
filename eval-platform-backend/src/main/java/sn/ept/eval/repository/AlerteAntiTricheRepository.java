package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.AlerteAntiTriche;

import java.util.List;

@Repository
public interface AlerteAntiTricheRepository extends JpaRepository<AlerteAntiTriche, Long> {
    List<AlerteAntiTriche> findBySoumissionEvaluationId(Long evaluationId);
    List<AlerteAntiTriche> findBySoumissionId(Long soumissionId);
}
