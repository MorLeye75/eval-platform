package sn.ept.eval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sn.ept.eval.dto.evaluation.StatistiquesEvaluationResponse.Tranche;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Calculs de DS-09 et protection des cellules du CSV, sans base. */
class StatistiquesCalculTest {

    @Test
    @DisplayName("Moyenne, médiane (nombre impair et pair), écart-type de la population")
    void indicateurs() {
        assertThat(StatistiquesEvaluationService.moyenne(List.of(6.0, 14.0))).isEqualTo(10.0);
        assertThat(StatistiquesEvaluationService.mediane(List.of(5.0, 1.0, 9.0))).isEqualTo(5.0);
        assertThat(StatistiquesEvaluationService.mediane(List.of(4.0, 1.0, 9.0, 2.0))).isEqualTo(3.0);
        // écarts de ±4 autour de 10 : variance 16, écart-type 4 (population, pas échantillon : 5,66)
        assertThat(StatistiquesEvaluationService.ecartType(List.of(6.0, 14.0))).isEqualTo(4.0);
        assertThat(StatistiquesEvaluationService.ecartType(List.of(12.0, 12.0, 12.0))).isZero();
    }

    @Test
    @DisplayName("Note ramenée sur 20 : barème de 40 points, barème nul sans division par zéro")
    void surVingt() {
        assertThat(StatistiquesEvaluationService.sur20(30, 40)).isEqualTo(15.0);
        assertThat(StatistiquesEvaluationService.sur20(5, 0)).isZero();
        assertThat(StatistiquesEvaluationService.arrondi(13.3333)).isEqualTo(13.33);
    }

    @Test
    @DisplayName("Distribution : 10 tranches de 2 points, 0 dans la première, 20 dans la dernière, 10 dans la tranche 10-12")
    void distribution() {
        List<Tranche> tranches = StatistiquesEvaluationService.distribution(List.of(0.0, 1.99, 2.0, 10.0, 19.99, 20.0, 20.0));
        assertThat(tranches).hasSize(10);
        assertThat(tranches.get(0)).isEqualTo(new Tranche(0, 2, 2));
        assertThat(tranches.get(1)).isEqualTo(new Tranche(2, 4, 1));
        assertThat(tranches.get(5)).isEqualTo(new Tranche(10, 12, 1));
        assertThat(tranches.get(9)).isEqualTo(new Tranche(18, 20, 3));
        assertThat(tranches.stream().mapToLong(Tranche::nb).sum()).isEqualTo(7);
    }

    @Test
    @DisplayName("Aucune note : distribution vide mais complète (10 tranches à 0)")
    void distributionVide() {
        assertThat(StatistiquesEvaluationService.distribution(List.of())).hasSize(10).allMatch(t -> t.nb() == 0);
    }

    @Test
    @DisplayName("Cellule CSV : formules neutralisées (= + - @ tabulation), guillemets doublés, séparateur et saut de ligne protégés")
    void celluleCsv() {
        assertThat(ExportResultatsService.cellule(null)).isEmpty();
        assertThat(ExportResultatsService.cellule("Diop")).isEqualTo("Diop");
        assertThat(ExportResultatsService.cellule("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(ExportResultatsService.cellule("+33")).isEqualTo("'+33");
        assertThat(ExportResultatsService.cellule("-1")).isEqualTo("'-1");
        assertThat(ExportResultatsService.cellule("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(ExportResultatsService.cellule("\tx")).isEqualTo("'\tx");
        assertThat(ExportResultatsService.cellule("Ndiaye; Sy")).isEqualTo("\"Ndiaye; Sy\"");
        assertThat(ExportResultatsService.cellule("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(ExportResultatsService.cellule("O'Neil")).isEqualTo("O'Neil");
    }
}
