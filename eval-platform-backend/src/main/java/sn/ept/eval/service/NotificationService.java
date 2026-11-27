package sn.ept.eval.service;

import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Soumission;

/**
 * Notifications internes de la plateforme (DS-15). Les trois événements que le module enseignant déclenche ;
 * la création des notifications elle-même (destinataires, contenu, stockage) est le travail du module de Mor.
 *
 * <p>Les implémentations doivent être appelées <b>dans la transaction de l'événement</b> : si l'événement est
 * annulé, ses notifications le sont aussi. Tant qu'aucune implémentation n'est enregistrée, la plateforme utilise
 * {@link NotificationParDefautConfig.NotificationServiceParDefaut}, qui ne fait rien.
 */
public interface NotificationService {

    /** Une évaluation vient de passer à EN_COURS : prévenir les étudiants de ses promotions (DS-05). */
    void evaluationOuverte(Evaluation evaluation);

    /** Les résultats d'une évaluation viennent d'être publiés : une notification par copie déjà notée (DS-07). */
    void resultatsPublies(Evaluation evaluation);

    /** Une copie notée après la publication des résultats l'est maintenant : prévenir son étudiant (DS-08). */
    void resultatPublie(Soumission copie);
}
