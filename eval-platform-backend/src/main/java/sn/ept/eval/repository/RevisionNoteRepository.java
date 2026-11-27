package sn.ept.eval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.ept.eval.model.RevisionNote;

import java.util.Collection;
import java.util.List;

public interface RevisionNoteRepository extends JpaRepository<RevisionNote, Long> {

    /** Historique des réponses d'une copie, du plus ancien au plus récent, auteur chargé. */
    @Query("""
            select r from RevisionNote r join fetch r.auteur
            where r.reponse.id in :reponseIds
            order by r.reviseeLe, r.id""")
    List<RevisionNote> historique(@Param("reponseIds") Collection<Long> reponseIds);
}
