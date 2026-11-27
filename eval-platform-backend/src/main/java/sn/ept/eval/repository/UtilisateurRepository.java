package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;
import sn.ept.eval.model.Utilisateur;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByEmail(String email);
    /** E-mails déjà pris parmi ceux d'un fichier d'import (déjà normalisés, DS-16). */
    @Query("select u.email from Utilisateur u where u.email in :emails")
    java.util.List<String> emailsExistants(@Param("emails") java.util.Collection<String> emails);
    Boolean existsByEmail(String email);

    /**
     * Date de dernière connexion seule, par une requête directe : l'audit du compte (modifié par / le) n'est pas
     * touché. Une sauvegarde de l'entité l'aurait écrasé avec « SYSTEM » (aucun utilisateur authentifié au login).
     */
    @Modifying
    @Transactional
    @Query("update Utilisateur u set u.derniereConnexion = :date where u.id = :id")
    int enregistrerConnexion(@Param("id") Long id, @Param("date") LocalDateTime date);
}
