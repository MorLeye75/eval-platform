package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.ChoixReponse;

import java.util.List;

@Repository
public interface ChoixReponseRepository extends JpaRepository<ChoixReponse, Long> {
    List<ChoixReponse> findByQuestionId(Long questionId);
}
