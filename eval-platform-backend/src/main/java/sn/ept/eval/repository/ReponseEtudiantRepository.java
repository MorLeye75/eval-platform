package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.ReponseEtudiant;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReponseEtudiantRepository extends JpaRepository<ReponseEtudiant, Long> {
    List<ReponseEtudiant> findBySoumissionId(Long soumissionId);
    Optional<ReponseEtudiant> findBySoumissionIdAndQuestionId(Long soumissionId, Long questionId);

    /** Points obtenus par question sur les copies notées d'une évaluation (DS-09). */
    interface TotalQuestion {
        Long getQuestionId();
        Double getTotal();
    }

    @Query("select r.question.id as questionId, sum(r.noteObtenue) as total from ReponseEtudiant r where r.soumission.evaluation.id = :evaluationId and r.soumission.statut = sn.ept.eval.model.enums.StatutSoumission.EVALUE group by r.question.id")
    List<TotalQuestion> totauxParQuestion(@Param("evaluationId") Long evaluationId);

    /** Nombre de réponses données par copie (supervision, DS-17). */
    interface NbParCopie {
        Long getSoumissionId();
        Long getNb();
    }

    /** Une réponse vide enregistrée par la sauvegarde automatique ne compte pas comme donnée. */
    @Query("select r.soumission.id as soumissionId, count(r) as nb from ReponseOuverte r where r.soumission.evaluation.id = :evaluationId and r.texteSoumis is not null and trim(r.texteSoumis) <> '' group by r.soumission.id")
    List<NbParCopie> reponsesOuvertesDonnees(@Param("evaluationId") Long evaluationId);

    @Query("select r.soumission.id as soumissionId, count(r) as nb from ReponseCode r where r.soumission.evaluation.id = :evaluationId and r.codeSoumis is not null and trim(r.codeSoumis) <> '' group by r.soumission.id")
    List<NbParCopie> reponsesCodeDonnees(@Param("evaluationId") Long evaluationId);

    @Query("select r.soumission.id as soumissionId, count(r) as nb from ReponseQCM r where r.soumission.evaluation.id = :evaluationId and size(r.choixCoches) > 0 group by r.soumission.id")
    List<NbParCopie> reponsesQcmDonnees(@Param("evaluationId") Long evaluationId);
}
