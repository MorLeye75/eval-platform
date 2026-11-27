package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Promotion;

import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    /** Plusieurs promotions peuvent porter le même nom, d'années différentes (import CSV, DS-16). */
    java.util.List<Promotion> findAllByNomIgnoreCase(String nom);
    boolean existsByNomIgnoreCaseAndAnnee(String nom, String annee);
    /** Renommer une promotion : le doublon ne compte pas contre elle-même (DS-16). */
    boolean existsByNomIgnoreCaseAndAnneeAndIdNot(String nom, String annee, Long id);
    Optional<Promotion> findByNom(String nom);
}
