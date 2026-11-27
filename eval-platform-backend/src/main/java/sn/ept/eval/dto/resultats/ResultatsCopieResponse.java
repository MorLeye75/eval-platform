package sn.ept.eval.dto.resultats;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Résultats d'une copie (GET /soumissions/{id}/resultats, DS-13). Hors état PUBLIES, seuls l'état, les
 * identifiants, le titre et la date de remise sont renseignés : aucune note, même partielle.
 *
 * @param moyennePromotion moyenne des copies notées, null en dessous de 5 copies (anonymat)
 * @param ecartMoyenne     note de l'étudiant moins la moyenne, null sans moyenne
 * @param corrigeVisible   bonnes réponses des QCM montrées (après la fermeture de l'évaluation + 2 min)
 */
public record ResultatsCopieResponse(EtatResultats etat, Long soumissionId, Long evaluationId, String titre,
                                     LocalDateTime dateRemise, Double noteFinale, Double totalPoints,
                                     String feedbackGeneral, Double moyennePromotion, Double ecartMoyenne,
                                     boolean corrigeVisible, List<ResultatQuestionDto> questions) {

    public static ResultatsCopieResponse sansNote(EtatResultats etat, Long soumissionId, Long evaluationId,
                                                  String titre, LocalDateTime dateRemise) {
        return new ResultatsCopieResponse(etat, soumissionId, evaluationId, titre, dateRemise,
                null, null, null, null, null, false, List.of());
    }
}
