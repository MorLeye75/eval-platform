package sn.ept.eval.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.enums.StatutEvaluation;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    List<Evaluation> findByEnseignantId(Long enseignantId);
    List<Evaluation> findByStatut(StatutEvaluation statut);
    boolean existsByTitre(String titre);
    List<Evaluation> findByPromotionsId(Long promotionId);
    List<Evaluation> findByPromotionsIdAndStatutInOrderByDateOuvertureDesc(Long promotionId, Collection<StatutEvaluation> statuts);

    /** Évaluations d'un enseignant, les plus récentes d'abord (DS-03). */
    List<Evaluation> findByEnseignantIdOrderByDateOuvertureDesc(Long enseignantId);

    /** Verrou pessimiste (DS-04) : la dépublication ne croise pas un autre changement de statut. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Evaluation e where e.id = :id")
    Optional<Evaluation> findByIdForUpdate(@Param("id") Long id);

    /**
     * Changement de statut **conditionnel** et atomique (DS-05) : la ligne n'est modifiée que si l'évaluation a
     * encore le statut attendu. Deux passages concurrents de la tâche planifiée ne peuvent donc pas tous deux
     * « réussir » la même transition, ce qui évite les notifications en double. `versioned` incrémente la
     * version (@Version) comme une mise à jour normale.
     *
     * @return 1 si la transition vient d'avoir lieu, 0 sinon
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update versioned Evaluation e set e.statut = :nouveau where e.id = :id and e.statut = :attendu")
    int changerStatutSi(@Param("id") Long id, @Param("attendu") StatutEvaluation attendu,
                        @Param("nouveau") StatutEvaluation nouveau);

    /**
     * Publication des résultats (DS-07), conditionnelle et atomique : seule la première requête passe
     * `resultatsPublies` à vrai et reçoit 1, une double publication ne notifie pas deux fois.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update versioned Evaluation e set e.resultatsPublies = true where e.id = :id and e.resultatsPublies = false")
    int publierResultatsSiNonPublies(@Param("id") Long id);

    /** Évaluations d'un statut dont la date d'ouverture est atteinte (DS-05). */
    List<Evaluation> findByStatutAndDateOuvertureLessThanEqual(StatutEvaluation statut, LocalDateTime date);

    /** Évaluations d'un statut dont la date de fermeture est atteinte (DS-05). */
    List<Evaluation> findByStatutAndDateFermetureLessThanEqual(StatutEvaluation statut, LocalDateTime date);
    /**
     * Verrou partagé (FOR SHARE) : une dépublication ou une modification en cours (FOR UPDATE) est attendue, puis
     * l'évaluation est relue telle qu'elle a été validée. Compatible entre lecteurs : les démarrages simultanés des
     * étudiants ne se bloquent pas entre eux (DS-10, point ouvert du DS-04).
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from Evaluation e where e.id = :id")
    Optional<Evaluation> findByIdForShare(@Param("id") Long id);
}
