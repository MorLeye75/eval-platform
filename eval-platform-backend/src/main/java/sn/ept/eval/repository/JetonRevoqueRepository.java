package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.JetonRevoque;

import java.time.LocalDateTime;

@Repository
public interface JetonRevoqueRepository extends JpaRepository<JetonRevoque, String> {

    /** Purge des jetons expirés, en une requête (pas de chargement ligne par ligne). */
    @Modifying
    @Query("delete from JetonRevoque j where j.expireLe < :maintenant")
    int supprimerExpires(@Param("maintenant") LocalDateTime maintenant);
}
