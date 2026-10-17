package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.ReponseEtudiant;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReponseEtudiantRepository extends JpaRepository<ReponseEtudiant, Long> {
    List<ReponseEtudiant> findBySoumissionId(Long soumissionId);
    Optional<ReponseEtudiant> findBySoumissionIdAndQuestionId(Long soumissionId, Long questionId);
}
