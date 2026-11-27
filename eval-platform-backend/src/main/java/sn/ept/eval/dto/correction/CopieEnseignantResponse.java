package sn.ept.eval.dto.correction;

import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.model.enums.VerdictCas;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Copie complète vue par son enseignant : chaque question avec la réponse (texte, code, choix cochés), les verdicts des
 * cas de test, la note et l'historique des révisions. {@code notesModifiables} : vrai tant que les résultats ne sont pas
 * publiés et que la copie est en correction ou notée.
 */
public record CopieEnseignantResponse(Long soumissionId, Long evaluationId, String evaluation, String etudiant,
                                      String numEtudiant, StatutSoumission statut, Double noteFinale, double bareme,
                                      boolean resultatsPublies, boolean notesModifiables, LocalDateTime dateRemise,
                                      List<Reponse> reponses) {

    /** Une question de la copie ; {@code reponseId} est nul si l'étudiant n'a rien enregistré pour elle. */
    public record Reponse(Long reponseId, Integer ordre, String type, String enonce, double pointsMax, Double note,
                          boolean notee, String commentaire, String texte, String code, String langage,
                          List<Choix> choix, List<Cas> casDeTests, List<Revision> revisions) {}

    public record Choix(String texte, boolean coche, boolean correct) {}

    public record Cas(String entree, String sortieAttendue, String sortieObtenue, VerdictCas verdict, boolean reussi,
                      boolean masque, String messageErreur) {}

    public record Revision(double ancienneNote, double nouvelleNote, String motif, String auteur, LocalDateTime reviseeLe) {}
}
