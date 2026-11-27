package sn.ept.eval.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.ept.eval.model.JetonReinitialisation;

import java.time.LocalDateTime;
import java.util.Optional;

public interface JetonReinitialisationRepository extends JpaRepository<JetonReinitialisation, Long> {

    /** Verrouillé : deux utilisations simultanées du même lien sont sérialisées, la seconde le trouve utilisé. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JetonReinitialisation j join fetch j.utilisateur where j.empreinte = :empreinte")
    Optional<JetonReinitialisation> findByEmpreinteForUpdate(@Param("empreinte") String empreinte);

    @Query("select count(j) from JetonReinitialisation j where j.utilisateur.id = :utilisateurId and j.creeLe > :depuis")
    long compterDemandesDepuis(@Param("utilisateurId") Long utilisateurId, @Param("depuis") LocalDateTime depuis);

    /** Une nouvelle demande remplace les liens encore valables du compte. */
    @Modifying(flushAutomatically = true)
    @Query("update JetonReinitialisation j set j.utiliseLe = :maintenant"
            + " where j.utilisateur.id = :utilisateurId and j.utiliseLe is null")
    int invaliderLesLiensDe(@Param("utilisateurId") Long utilisateurId, @Param("maintenant") LocalDateTime maintenant);

    @Modifying
    @Query("delete from JetonReinitialisation j where j.expireLe < :avant")
    int supprimerExpires(@Param("avant") LocalDateTime avant);
}
