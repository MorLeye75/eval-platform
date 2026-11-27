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
 * Migration V3 sur un vrai PostgreSQL contenant des réponses en double (base migrée en V2, données
 * insérées, puis V3 appliquée) : seule la réponse la plus ancienne de chaque (copie, question) est gardée,
 * ses lignes dépendantes sont conservées, celles des doublons supprimées, et la contrainte est posée.
 * Ignoré si Docker n'est pas disponible (exécuté en CI).
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationV3DoublonsIntegrationTest {

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

    private static List<Long> ids(Statement st, String requete) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (ResultSet rs = st.executeQuery(requete)) {
            while (rs.next()) ids.add(rs.getLong(1));
        }
        return ids;
    }

    private static List<String> paires(Statement st, String requete) throws SQLException {
        List<String> paires = new ArrayList<>();
        try (ResultSet rs = st.executeQuery(requete)) {
            while (rs.next()) paires.add(rs.getLong(1) + "->" + rs.getLong(2));
        }
        return paires;
    }

    /** Réponse de base (table mère de l'héritage JOINED). */
    private static String reponse(long id, long soumissionId, long questionId) {
        return "insert into reponses_etudiant (id, soumission_id, question_id, validee_par_prof, date_creation) "
                + "values (" + id + ", " + soumissionId + ", " + questionId + ", false, now())";
    }

    @Test
    @DisplayName("V3 supprime les réponses en double et leurs dépendances, garde la plus ancienne et pose la contrainte")
    void v3_SupprimeLesDoublons() throws SQLException {
        flyway("2").migrate();

        try (Connection c = connexion(); Statement st = c.createStatement()) {
            // Deux étudiants, un enseignant, une évaluation à 4 questions (QCM, code, 2 ouvertes)
            st.execute("insert into utilisateurs (id, actif, date_creation, email, mot_de_passe, nom, prenom, role) values "
                    + "(1, true, now(), 'e1@ept.sn', 'x', 'A', 'A', 'ETUDIANT'),"
                    + "(2, true, now(), 'p@ept.sn', 'x', 'P', 'P', 'ENSEIGNANT'),"
                    + "(3, true, now(), 'e2@ept.sn', 'x', 'B', 'B', 'ETUDIANT')");
            st.execute("insert into etudiants (id) values (1), (3)");
            st.execute("insert into enseignants (id) values (2)");
            st.execute("insert into evaluations (id, duree_standard, resultats_publies, date_creation, date_ouverture, "
                    + "date_fermeture, enseignant_id, statut, titre) "
                    + "values (10, 30, false, now(), now(), now() + interval '1 day', 2, 'PUBLIEE', 'Examen')");
            st.execute("insert into questions (id, ordre, points, evaluation_id, enonce, type) values "
                    + "(100, 1, 2, 10, 'QCM', 'QCM'), (101, 2, 2, 10, 'Code', 'CODE'),"
                    + "(102, 3, 2, 10, 'Ouverte', 'OUVERTE'), (103, 4, 2, 10, 'Ouverte 2', 'OUVERTE')");
            st.execute("insert into questions_qcm (id, est_choix_multiple) values (100, false)");
            st.execute("insert into questions_code (id) values (101)");
            st.execute("insert into questions_ouvertes (id) values (102), (103)");
            st.execute("insert into choix_reponses (id, question_id, est_correct, texte) values "
                    + "(1000, 100, true, 'Pile'), (1001, 100, false, 'File')");
            st.execute("insert into cas_de_tests (id, question_id, est_masque, sortie_attendue) values (2000, 101, false, '42')");
            st.execute("insert into soumissions (id, nb_changements_onglet, date_creation, date_debut, etudiant_id, "
                    + "evaluation_id, statut) values (50, 0, now(), now(), 1, 10, 'EVALUE'), (51, 0, now(), now(), 3, 10, 'EVALUE')");

            // Copie 50 : QCM en double, code en double, ouverte en triple, ouverte sans doublon
            st.execute(reponse(500, 50, 100));
            st.execute(reponse(501, 50, 100));
            st.execute("insert into reponses_qcm (id) values (500), (501)");
            st.execute("insert into reponse_qcm_choix (reponse_qcm_id, choix_id) values (500, 1000), (501, 1001)");

            st.execute(reponse(510, 50, 101));
            st.execute(reponse(511, 50, 101));
            st.execute("insert into reponses_code (id, code_soumis) values (510, 'garde'), (511, 'doublon')");
            st.execute("insert into resultats_cas_tests (id, est_valide, temps_execution, cas_de_test_id, reponse_code_id) "
                    + "values (3000, true, 0.1, 2000, 510), (3001, false, 0.1, 2000, 511)");

            st.execute("insert into propositions_ia (id, note_proposee, statut) values (4000, 2, 'VALIDEE'), (4001, 1, 'EN_ATTENTE')");
            st.execute(reponse(520, 50, 102));
            st.execute(reponse(521, 50, 102));
            st.execute(reponse(522, 50, 102));
            st.execute("insert into reponses_ouvertes (id, texte_soumis, proposition_ia_id) values "
                    + "(520, 'garde', 4000), (521, 'doublon', 4001), (522, 'doublon', null)");

            st.execute(reponse(530, 50, 103));
            st.execute("insert into reponses_ouvertes (id, texte_soumis) values (530, 'seule')");

            // Copie 51 : même question QCM que la copie 50, mais ce n'est pas un doublon (autre copie)
            st.execute(reponse(600, 51, 100));
            st.execute("insert into reponses_qcm (id) values (600)");
            st.execute("insert into reponse_qcm_choix (reponse_qcm_id, choix_id) values (600, 1000)");
        }

        flyway("latest").migrate();

        try (Connection c = connexion(); Statement st = c.createStatement()) {
            assertThat(ids(st, "select id from reponses_etudiant order by id")).containsExactly(500L, 510L, 520L, 530L, 600L);
            assertThat(ids(st, "select id from reponses_qcm order by id")).containsExactly(500L, 600L);
            assertThat(ids(st, "select id from reponses_code order by id")).containsExactly(510L);
            assertThat(ids(st, "select id from reponses_ouvertes order by id")).containsExactly(520L, 530L);
            assertThat(paires(st, "select reponse_qcm_id, choix_id from reponse_qcm_choix order by reponse_qcm_id"))
                    .containsExactly("500->1000", "600->1000");
            assertThat(ids(st, "select id from resultats_cas_tests order by id")).containsExactly(3000L);
            assertThat(ids(st, "select id from propositions_ia order by id")).containsExactly(4000L);

            // La contrainte refuse désormais un doublon
            assertThatThrownBy(() -> st.execute(reponse(700, 50, 100)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_reponses_soumission_question");
        }
    }
}
