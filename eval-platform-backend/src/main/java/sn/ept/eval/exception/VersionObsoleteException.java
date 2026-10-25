package sn.ept.eval.exception;

/**
 * Sauvegarde saisie sur une version périmée des réponses : la copie a été enregistrée entre-temps
 * depuis un autre onglet ou appareil (contrôle de concurrence optimiste, DS-02).
 */
public class VersionObsoleteException extends RuntimeException {

    private final int versionActuelle;

    public VersionObsoleteException(String message, int versionActuelle) {
        super(message);
        this.versionActuelle = versionActuelle;
    }

    public int getVersionActuelle() {
        return versionActuelle;
    }
}
