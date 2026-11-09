package sn.ept.eval;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration V4 sur un vrai PostgreSQL contenant des doublons dans les tables de liaison (base migrée en V3,
 * données insérées, puis V4 appliquée) : une seule ligne par couple, clés primaires et index posés.
 * Ignoré si Docker n'est pas disponible (exécuté en CI).
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationV4LiaisonsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private Flyway flyway(String cible) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(cible)
                .load();
    }

    private Connection connexion() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static List<String> lignes(Statement st, String requete) throws SQLException {
        List<String> lignes = new ArrayList<>();
        try (ResultSet rs = st.executeQuery(requete)) {
            while (rs.next()) lignes.add(rs.getString(1) + "->" + rs.getString(2));
        }
        return lignes;
    }

    @Test
    @DisplayName("V4 dédoublonne les tables de liaison et pose clés primaires et index")
    void v4_ClesEtIndex() throws SQLException {
        flyway("3").migrate();

        try (Connection c = connexion(); Statement st = c.createStatement()) {
            st.execute("insert into utilisateurs (id, actif, date_creation, email, mot_de_passe, nom, prenom, role) values "
                    + "(1, true, now(), 'e1@ept.sn', 'x', 'A', 'A', 'ETUDIANT'), (2, true, now(), 'p@ept.sn', 'x', 'P', 'P', 'ENSEIGNANT')");
            st.execute("insert into etudiants (id) values (1)");
            st.execute("insert into enseignants (id) values (2)");
            st.execute("insert into promotions (id, annee, nom) values (20, '2025/2026', 'DIC2'), (21, '2025/2026', 'DIC1')");
            st.execute("insert into evaluations (id, duree_standard, resultats_publies, date_creation, date_ouverture, "
                    + "date_fermeture, enseignant_id, statut, titre) "
                    + "values (10, 30, false, now(), now(), now() + interval '1 day', 2, 'PUBLIEE', 'Examen')");
            // Doublon (10, 20) et une seconde promotion
            st.execute("insert into evaluation_promotions (evaluation_id, promotion_id) values (10, 20), (10, 20), (10, 21)");

            st.execute("insert into questions (id, ordre, points, evaluation_id, enonce, type) values (100, 1, 2, 10, 'QCM', 'QCM')");
            st.execute("insert into questions_qcm (id, est_choix_multiple) values (100, true)");
            st.execute("insert into choix_reponses (id, question_id, est_correct, texte) values "
                    + "(1000, 100, true, 'Pile'), (1001, 100, true, 'File')");
            st.execute("insert into soumissions (id, nb_changements_onglet, date_creation, date_debut, etudiant_id, "
                    + "evaluation_id, statut) values (50, 0, now(), now(), 1, 10, 'EN_COURS')");
            st.execute("insert into reponses_etudiant (id, soumission_id, question_id, validee_par_prof, date_creation) "
                    + "values (500, 50, 100, false, now())");
            st.execute("insert into reponses_qcm (id) values (500)");
            // Doublon (500, 1000) et un second choix
            st.execute("insert into reponse_qcm_choix (reponse_qcm_id, choix_id) values (500, 1000), (500, 1000), (500, 1001)");
        }

        flyway("latest").migrate();

        try (Connection c = connexion(); Statement st = c.createStatement()) {
            assertThat(lignes(st, "select evaluation_id, promotion_id from evaluation_promotions order by 2"))
                    .containsExactly("10->20", "10->21");
            assertThat(lignes(st, "select reponse_qcm_id, choix_id from reponse_qcm_choix order by 2"))
                    .containsExactly("500->1000", "500->1001");

            assertThat(lignes(st, "select tablename, indexname from pg_indexes "
                    + "where tablename in ('evaluation_promotions', 'reponse_qcm_choix') order by 2"))
                    .containsExactly(
                            "evaluation_promotions->idx_evaluation_promotions_promotion",
                            "reponse_qcm_choix->idx_reponse_qcm_choix_choix",
                            "evaluation_promotions->pk_evaluation_promotions",
                            "reponse_qcm_choix->pk_reponse_qcm_choix");

            assertThatThrownBy(() -> st.execute("insert into evaluation_promotions values (10, 20)"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("pk_evaluation_promotions");
            assertThatThrownBy(() -> st.execute("insert into reponse_qcm_choix (reponse_qcm_id, choix_id) values (500, 1000)"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("pk_reponse_qcm_choix");
        }
    }
}
