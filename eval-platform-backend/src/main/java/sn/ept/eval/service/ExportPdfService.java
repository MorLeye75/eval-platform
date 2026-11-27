package sn.ept.eval.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ept.eval.dto.evaluation.ResultatsEvaluationResponse;
import sn.ept.eval.dto.evaluation.StatistiquesEvaluationResponse;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * Rapport PDF des résultats d'une évaluation (DS-09, section E) : les indicateurs puis une ligne par étudiant attendu.
 * Il est construit à partir des mêmes données que l'écran et le CSV ({@link StatistiquesEvaluationService},
 * {@link ExportResultatsService}) : mêmes contrôles d'accès, mêmes nombres. Pas d'e-mail dans le document.
 */
@Service
@Transactional(readOnly = true)
public class ExportPdfService {

    private static final Logger log = LoggerFactory.getLogger(ExportPdfService.class);

    private static final Font TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font SOUS_TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font TEXTE = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font ENTETE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font CELLULE = FontFactory.getFont(FontFactory.HELVETICA, 9);

    private final StatistiquesEvaluationService statistiquesService;
    private final ExportResultatsService exportService;

    public ExportPdfService(StatistiquesEvaluationService statistiquesService, ExportResultatsService exportService) {
        this.statistiquesService = statistiquesService;
        this.exportService = exportService;
    }

    public byte[] exporter(Long evaluationId, String email) {
        // Les deux appels vérifient le propriétaire et l'ouverture : 404, 403 ou 409 comme pour le CSV
        StatistiquesEvaluationResponse stats = statistiquesService.statistiques(evaluationId, email);
        ResultatsEvaluationResponse resultats = exportService.lignes(evaluationId, email);

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(document, sortie);
            document.open();
            document.addTitle("Résultats : " + stats.titre());
            document.add(new Paragraph("Résultats : " + stats.titre(), TITRE));
            document.add(new Paragraph("Barème : " + nombre(stats.bareme()) + " points · "
                    + stats.nbAttendus() + " étudiants attendus · " + stats.nbCopies() + " copies · "
                    + stats.nbNotees() + " notées", TEXTE));
            document.add(new Paragraph(" ", TEXTE));

            document.add(new Paragraph("Indicateurs (notes ramenées sur 20)", SOUS_TITRE));
            document.add(new Paragraph("Moyenne : " + sur20(stats.moyenneSur20()) + " · Médiane : " + sur20(stats.medianeSur20())
                    + " · Min : " + sur20(stats.minSur20()) + " · Max : " + sur20(stats.maxSur20())
                    + " · Taux de réussite : " + pourcent(stats.tauxReussite()), TEXTE));
            document.add(new Paragraph(" ", TEXTE));

            document.add(new Paragraph("Notes par étudiant", SOUS_TITRE));
            PdfPTable tableau = new PdfPTable(new float[]{4, 2.2f, 1.6f, 1.6f});
            tableau.setWidthPercentage(100);
            tableau.setSpacingBefore(6);
            for (String titre : new String[]{"Étudiant", "État de la copie", "Note", "Sur 20"}) {
                PdfPCell cellule = new PdfPCell(new Phrase(titre, ENTETE));
                cellule.setBackgroundColor(new java.awt.Color(0xEE, 0xEE, 0xEE));
                tableau.addCell(cellule);
            }
            tableau.setHeaderRows(1);
            for (ResultatsEvaluationResponse.LigneResultat l : resultats.lignes()) {
                tableau.addCell(cellule(l.nom() + " " + l.prenom() + (l.numEtudiant() == null ? "" : " (" + l.numEtudiant() + ")"), Element.ALIGN_LEFT));
                tableau.addCell(cellule(libelleEtat(l.etat()), Element.ALIGN_LEFT));
                tableau.addCell(cellule(l.note() == null ? "—" : nombre(l.note()), Element.ALIGN_RIGHT));
                tableau.addCell(cellule(l.noteSurVingt() == null ? "—" : nombre(l.noteSurVingt()), Element.ALIGN_RIGHT));
            }
            document.add(tableau);
            document.add(new Paragraph(" ", TEXTE));
            document.add(new Paragraph("« — » : pas de note (copie absente ou pas encore notée).", TEXTE));
        } catch (DocumentException e) {
            throw new IllegalStateException("Le PDF n'a pas pu être généré.", e);
        } finally {
            if (document.isOpen()) document.close();
        }
        log.info("[Export] PDF de l'évaluation ID={} généré ({} lignes)", evaluationId, resultats.lignes().size());
        return sortie.toByteArray();
    }

    private static PdfPCell cellule(String texte, int alignement) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte, CELLULE));
        cellule.setHorizontalAlignment(alignement);
        return cellule;
    }

    private static String libelleEtat(String etat) {
        return switch (etat) {
            case "ABSENT" -> "Absent";
            case "EVALUE" -> "Notée";
            case "EN_CORRECTION", "SOUMIS" -> "En attente de note";
            case "EN_COURS" -> "En cours";
            default -> etat;
        };
    }

    private static String nombre(double valeur) {
        return String.format(Locale.FRANCE, "%.2f", valeur).replaceAll(",?0+$", "");
    }

    private static String sur20(Double valeur) {
        return valeur == null ? "—" : nombre(valeur) + "/20";
    }

    private static String pourcent(Double valeur) {
        return valeur == null ? "—" : nombre(valeur) + " %";
    }
}
