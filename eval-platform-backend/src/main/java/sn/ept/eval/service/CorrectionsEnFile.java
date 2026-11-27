package sn.ept.eval.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copies dont la correction attend dans une file d'exécution ou est en cours (DS-12) : la relance planifiée
 * ne remet pas en file une copie qui y est déjà. Ajoutée par CorrectionDeclencheur, retirée à la fin de la
 * correction ou aussitôt si l'exécuteur refuse la tâche. En mémoire : vidée par un redémarrage, ce qui est
 * voulu (les tâches en file sont alors perdues et doivent être relancées).
 */
@Component
public class CorrectionsEnFile {

    private final Set<Long> ids = ConcurrentHashMap.newKeySet();

    /** @return false si la copie est déjà en file */
    public boolean ajouter(Long soumissionId) {
        return ids.add(soumissionId);
    }

    public void retirer(Long soumissionId) {
        ids.remove(soumissionId);
    }

    public boolean contient(Long soumissionId) {
        return ids.contains(soumissionId);
    }
}
