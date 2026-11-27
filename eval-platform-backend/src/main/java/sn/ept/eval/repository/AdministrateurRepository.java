package sn.ept.eval.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Administrateur;

import java.util.List;

@Repository
public interface AdministrateurRepository extends JpaRepository<Administrateur, Long> {
    long countByActifTrue();

    /**
     * Administrateurs actifs, verrouillés (FOR UPDATE) jusqu'à la fin de la transaction : deux désactivations
     * simultanées des deux derniers administrateurs sont sérialisées et la seconde est refusée (DS-16).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Administrateur a where a.actif = true order by a.id")
    List<Administrateur> findActifsForUpdate();
}
