package sn.ept.eval.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Etudiant;

import java.util.List;
import java.util.Optional;

@Repository
public interface EtudiantRepository extends JpaRepository<Etudiant, Long> {
    Optional<Etudiant> findByNumEtudiant(String numEtudiant);
    Optional<Etudiant> findByEmail(String email);

    /** Effectif d'une promotion (liste des promotions de l'assistant de création, DS-03). */
    long countByPromotionId(Long promotionId);

    /** Étudiants de plusieurs promotions, en une requête (notification « évaluation ouverte », DS-15). */
    List<Etudiant> findByPromotionIdIn(java.util.Collection<Long> promotionIds);

    /** Verrou pessimiste sur l'étudiant : sérialise ses démarrages d'épreuve concurrents. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Etudiant e where e.id = :id")
    Optional<Etudiant> findByIdForUpdate(@Param("id") Long id);
}
