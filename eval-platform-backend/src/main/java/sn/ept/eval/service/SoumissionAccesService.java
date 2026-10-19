package sn.ept.eval.service;

import org.springframework.stereotype.Service;
import sn.ept.eval.exception.AccesRefuseException;
import sn.ept.eval.exception.RessourceNonTrouveeException;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.Utilisateur;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.SoumissionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

/**
 * Contrôle d'accès aux copies : identification de l'étudiant connecté et règles de propriété
 * (protection IDOR). Partagé par la passation et l'anti-triche.
 */
@Service
public class SoumissionAccesService {

    private final EtudiantRepository etudiantRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final SoumissionRepository soumissionRepository;

    public SoumissionAccesService(EtudiantRepository etudiantRepository,
                                  UtilisateurRepository utilisateurRepository,
                                  SoumissionRepository soumissionRepository) {
        this.etudiantRepository = etudiantRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.soumissionRepository = soumissionRepository;
    }

    public Etudiant etudiantConnecte(String email) {
        return etudiantRepository.findByEmail(email)
                .orElseThrow(() -> new RessourceNonTrouveeException("Profil étudiant introuvable pour le compte : " + email));
    }

    /** Copie appartenant à l'étudiant, sinon 404 (inexistante) ou 403 (copie d'un autre étudiant). */
    public Soumission copieDeLEtudiant(Long soumissionId, Etudiant etudiant) {
        Soumission soumission = soumissionRepository.findById(soumissionId)
                .orElseThrow(() -> soumissionIntrouvable(soumissionId));
        verifierProprietaire(soumission, etudiant);
        return soumission;
    }

    public void verifierProprietaire(Soumission soumission, Etudiant etudiant) {
        if (!soumission.getEtudiant().getId().equals(etudiant.getId())) {
            throw new AccesRefuseException("Accès non autorisé : cette copie appartient à un autre étudiant.");
        }
    }

    /**
     * Copie consultable selon le rôle : l'étudiant voit la sienne, l'enseignant celles de ses évaluations,
     * l'administrateur toutes.
     */
    public Soumission copieConsultable(Long soumissionId, String email) {
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RessourceNonTrouveeException("Utilisateur introuvable pour le compte : " + email));
        Soumission soumission = soumissionRepository.findById(soumissionId)
                .orElseThrow(() -> soumissionIntrouvable(soumissionId));

        boolean autorise = switch (utilisateur.getRole()) {
            case ETUDIANT -> soumission.getEtudiant().getId().equals(utilisateur.getId());
            case ENSEIGNANT -> soumission.getEvaluation().getEnseignant().getId().equals(utilisateur.getId());
            case ADMIN -> true;
        };
        if (!autorise) {
            throw new AccesRefuseException("Accès non autorisé à cette copie.");
        }
        return soumission;
    }

    public static RessourceNonTrouveeException soumissionIntrouvable(Long soumissionId) {
        return new RessourceNonTrouveeException("Soumission introuvable (ID: " + soumissionId + ")");
    }
}
