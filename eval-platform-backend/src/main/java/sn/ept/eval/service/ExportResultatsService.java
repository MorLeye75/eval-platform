package sn.ept.eval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.evaluation.ResultatsEvaluationResponse;
import sn.ept.eval.model.Enseignant;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Question;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.model.enums.StatutSoumission;
import sn.ept.eval.repository.EtudiantRepository;
import sn.ept.eval.repository.SoumissionRepository;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Export CSV des résultats d'une évaluation (DS-09) : une ligne par étudiant attendu, y compris les absents. Le fichier
 * contient des données personnelles : réservé au propriétaire de l'évaluation.
 */
@Service
@Transactional(readOnly = true)
public class ExportResultatsService {

    private static final Logger log = LoggerFactory.getLogger(ExportResultatsService.class);
    static final String ENTETE = "numEtudiant;nom;prenom;email;promotion;etatCopie;note;bareme;noteSur20;dateRemise";
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Pattern FORMULE = Pattern.compile("^[=+\\-@\\t\\r].*", Pattern.DOTALL);

    private final EvaluationService evaluationService;
    private final EtudiantRepository etudiantRepository;
    private final SoumissionRepository soumissionRepository;

    public ExportResultatsService(EvaluationService evaluationService, EtudiantRepository etudiantRepository,
                                  SoumissionRepository soumissionRepository) {
        this.evaluationService = evaluationService;
        this.etudiantRepository = etudiantRepository;
        this.soumissionRepository = soumissionRepository;
    }

    /** Une ligne d'étudiant attendu avec sa copie : source commune de l'écran et du CSV. */
    private record Ligne(Etudiant etudiant, Soumission copie) {}

    private record Donnees(Enseignant enseignant, double bareme, List<Ligne> lignes) {}

    /** Notes par étudiant pour l'écran de l'enseignant (DS-09, section C) : mêmes données et même ordre que le CSV. */
    public ResultatsEvaluationResponse lignes(Long evaluationId, String email) {
        Donnees d = charger(evaluationId, email);
        return new ResultatsEvaluationResponse(evaluationId, d.bareme(), d.lignes().stream().map(l -> {
            Soumission copie = l.copie();
            boolean notee = estNotee(copie);
            return new ResultatsEvaluationResponse.LigneResultat(l.etudiant().getId(), l.etudiant().getNumEtudiant(),
                    l.etudiant().getNom(), l.etudiant().getPrenom(), etat(copie),
                    notee ? copie.getNoteFinale() : null,
                    notee && d.bareme() > 0 ? StatistiquesEvaluationService.arrondi(
                            StatistiquesEvaluationService.sur20(copie.getNoteFinale(), d.bareme())) : null,
                    copie == null ? null : copie.getDateRemise());
        }).toList());
    }

    private Donnees charger(Long evaluationId, String email) {
        Enseignant enseignant = evaluationService.enseignantConnecte(email);
        Evaluation evaluation = evaluationService.evaluationDeLEnseignant(evaluationId, enseignant);
        StatistiquesEvaluationService.exigerOuverte(evaluation);

        double bareme = evaluation.getQuestions().stream().mapToDouble(Question::getPoints).sum();
        List<Etudiant> etudiants = etudiantRepository.findByPromotionIdIn(
                evaluation.getPromotions().stream().map(p -> p.getId()).toList());
        Map<Long, Soumission> copies = new HashMap<>();
        for (Soumission s : soumissionRepository.findByEvaluationId(evaluationId)) copies.put(s.getEtudiant().getId(), s);
        List<Ligne> lignes = etudiants.stream()
                .sorted(Comparator.comparing((Etudiant e) -> e.getNom().toLowerCase(Locale.ROOT))
                        .thenComparing(e -> e.getPrenom().toLowerCase(Locale.ROOT)))
                .map(e -> new Ligne(e, copies.get(e.getId()))).toList();
        return new Donnees(enseignant, bareme, lignes);
    }

    private static String etat(Soumission copie) {
        return copie == null ? "ABSENT" : copie.getStatut().name();
    }

    private static boolean estNotee(Soumission copie) {
        return copie != null && copie.getStatut() == StatutSoumission.EVALUE && copie.getNoteFinale() != null;
    }

    /** Octets du fichier : BOM UTF-8 (Excel) puis le CSV. */
    public byte[] exporter(Long evaluationId, String email) {
        Donnees d = charger(evaluationId, email);
        Enseignant enseignant = d.enseignant();
        double bareme = d.bareme();
        StringBuilder csv = new StringBuilder(ENTETE).append('\n');
        d.lignes().forEach(l -> csv.append(ligne(l.etudiant(), l.copie(), bareme)).append('\n'));
        List<Etudiant> etudiants = d.lignes().stream().map(Ligne::etudiant).toList();

        log.info("[Export] Évaluation ID={} exportée par l'enseignant ID={} ({} lignes)", evaluationId, enseignant.getId(), etudiants.size());
        byte[] contenu = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] fichier = new byte[BOM.length + contenu.length];
        System.arraycopy(BOM, 0, fichier, 0, BOM.length);
        System.arraycopy(contenu, 0, fichier, BOM.length, contenu.length);
        return fichier;
    }

    private static String ligne(Etudiant e, Soumission copie, double bareme) {
        String etat = etat(copie);
        boolean notee = estNotee(copie);
        String note = notee ? nombre(copie.getNoteFinale()) : "";
        String sur20 = notee && bareme > 0 ? nombre(StatistiquesEvaluationService.arrondi(
                StatistiquesEvaluationService.sur20(copie.getNoteFinale(), bareme))) : "";
        String remise = copie == null || copie.getDateRemise() == null ? "" : copie.getDateRemise().toString();
        return String.join(";", cellule(e.getNumEtudiant()), cellule(e.getNom()), cellule(e.getPrenom()), cellule(e.getEmail()),
                cellule(e.getPromotion() == null ? null : e.getPromotion().getNom()), etat, note,
                notee ? nombre(bareme) : "", sur20, remise);
    }

    /** Décimale avec une virgule : l'Excel français lit « 12,5 » comme un nombre. */
    private static String nombre(double valeur) {
        return String.valueOf(valeur).replace('.', ',');
    }

    /**
     * Cellule CSV sûre : une valeur qui commence par = + - @ ou une tabulation serait exécutée comme une formule par un
     * tableur ; elle est préfixée d'une apostrophe. Guillemets doublés, champ entre guillemets s'il contient ; " ou un saut de ligne.
     */
    static String cellule(String valeur) {
        if (valeur == null) return "";
        String sure = FORMULE.matcher(valeur).matches() ? "'" + valeur : valeur;
        return sure.contains(";") || sure.contains("\"") || sure.contains("\n") || sure.contains("\r")
                ? "\"" + sure.replace("\"", "\"\"") + "\"" : sure;
    }
}
