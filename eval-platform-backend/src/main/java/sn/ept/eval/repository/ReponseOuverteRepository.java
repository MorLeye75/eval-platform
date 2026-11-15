package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.ReponseOuverte;
import sn.ept.eval.model.enums.StatutProposition;

import java.util.List;
import java.util.Optional;

/** Réponses aux questions ouvertes et leurs propositions IA (DS-08). */
@Repository
public interface ReponseOuverteRepository extends JpaRepository<ReponseOuverte, Long> {

    /** Réponses d'une évaluation à proposer : copie EN_CORRECTION, pas encore notée, sans proposition. */
    @Query("""
            select r.id from ReponseOuverte r
            where r.soumission.evaluation.id = :evaluationId and r.soumission.statut = sn.ept.eval.model.enums.StatutSoumission.EN_CORRECTION
              and r.propositionIA is null and r.valideeParProf = false
            order by r.id""")
    List<Long> idsAProposer(@Param("evaluationId") Long evaluationId);

    @Query("""
            select count(r) from ReponseOuverte r
            where r.soumission.evaluation.enseignant.id = :enseignantId
              and r.soumission.statut = sn.ept.eval.model.enums.StatutSoumission.EN_CORRECTION
              and r.propositionIA is null and r.valideeParProf = false
              and (:evaluationId is null or r.soumission.evaluation.id = :evaluationId)""")
    long compterSansProposition(@Param("enseignantId") Long enseignantId, @Param("evaluationId") Long evaluationId);

    @Query("""
            select r from ReponseOuverte r join fetch r.propositionIA p
            join fetch r.question join fetch r.soumission s join fetch s.evaluation e join fetch s.etudiant
            where e.enseignant.id = :enseignantId
              and (:evaluationId is null or e.id = :evaluationId)
              and (:statut is null or p.statut = :statut)
            order by e.id, r.id""")
    List<ReponseOuverte> propositionsDeLEnseignant(@Param("enseignantId") Long enseignantId,
                                                   @Param("evaluationId") Long evaluationId,
                                                   @Param("statut") StatutProposition statut);

    @Query("select r from ReponseOuverte r where r.propositionIA.id = :propositionId")
    Optional<ReponseOuverte> findByPropositionId(@Param("propositionId") Long propositionId);
}
