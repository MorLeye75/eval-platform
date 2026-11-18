package sn.ept.eval.service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Administration des comptes (DS-16) de bout en bout sur H2. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-comptes;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class AdminComptesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    // ---------------------------------------------------------------- création

    @Test
    @DisplayName("Créer un étudiant : 201, mot de passe provisoire de 12 caractères une seule fois, connexion possible, jamais de haché")
    void creerEtudiant() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        String email = unEmail();
        String corps = mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("Sy", "Mariama", email, "ETUDIANT", promotionId(), unNumero(), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.utilisateur.role").value("ETUDIANT"))
                .andExpect(jsonPath("$.utilisateur.actif").value(true))
                .andExpect(jsonPath("$.utilisateur.promotion").value("DIC2 Informatique"))
                .andExpect(jsonPath("$.utilisateur.motDePasse").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String provisoire = JsonPath.read(corps, "$.motDePasseProvisoire");
        assertThat(provisoire).hasSize(12);

        mockMvc.perform(post("/auth/login").contentType(JSON).content(login(email, provisoire))).andExpect(status().isOk());

        String liste = mockMvc.perform(get("/admin/utilisateurs?q=" + email).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andReturn().getResponse().getContentAsString();
        assertThat(liste).doesNotContain("motDePasse").doesNotContain("$2a$");
        assertThat(jdbcTemplate.queryForObject("select mot_de_passe from utilisateurs where email = ?", String.class, email))
                .startsWith("$2").isNotEqualTo(provisoire);
    }

    @Test
    @DisplayName("Créer un enseignant et un administrateur ; l'e-mail est normalisé ; doublons 409 ; données invalides 400 ; promotion inconnue 404")
    void creerEtRefuser() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        String email = unEmail();

        mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("Fall", "Awa", "  " + email.toUpperCase() + " ", "ENSEIGNANT", null, null, "MAT-" + UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.utilisateur.email").value(email));
        mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("Ba", "Cheikh", unEmail(), "ADMIN", null, null, null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.utilisateur.role").value("ADMIN"));

        // même e-mail, autre casse : refusé
        mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("Fall", "Awa", email.toUpperCase(), "ENSEIGNANT", null, null, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPTE_OPERATION_REFUSEE"));

        String numero = unNumero();
        mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("A", "B", unEmail(), "ETUDIANT", promotionId(), numero, null))).andExpect(status().isCreated());
        mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("C", "D", unEmail(), "ETUDIANT", promotionId(), numero, null)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("A", "B", unEmail(), "ETUDIANT", null, unNumero(), null))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("A", "B", unEmail(), "ETUDIANT", promotionId(), "  ", null))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("A", "B", "pas-un-email", "ENSEIGNANT", null, null, null))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("", "B", unEmail(), "ENSEIGNANT", null, null, null))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("A", "B", unEmail(), "ETUDIANT", 999999L, unNumero(), null))).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- modification, désactivation, mot de passe

    @Test
    @DisplayName("Modifier : nom et e-mail changent, le rôle jamais ; e-mail d'un autre compte refusé ; compte inconnu 404")
    void modifier() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        long id = creerEtudiant(admin, unEmail());
        String nouvelEmail = unEmail();

        mockMvc.perform(put("/admin/utilisateurs/" + id).header("Authorization", admin).contentType(JSON)
                        .content(modification("Diop", "Moussa", nouvelEmail, promotionId(), unNumero(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Diop")).andExpect(jsonPath("$.email").value(nouvelEmail))
                .andExpect(jsonPath("$.role").value("ETUDIANT"));
        mockMvc.perform(put("/admin/utilisateurs/" + id).header("Authorization", admin).contentType(JSON)
                        .content(modification("Diop", "Moussa", "admin@ept.sn", promotionId(), unNumero(), null)))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/admin/utilisateurs/999999").header("Authorization", admin).contentType(JSON)
                        .content(modification("X", "Y", unEmail(), promotionId(), unNumero(), null)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Désactiver : connexion refusée ET jeton déjà émis refusé à la requête suivante ; réactiver rétablit l'accès ; son propre compte : 409")
    void desactiverReactiver() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        String email = unEmail();
        long id = creerEtudiant(admin, email);
        String provisoire = reinitialiser(admin, id);
        String jetonEtudiant = bearer(email, provisoire);
        mockMvc.perform(get("/etudiant/evaluations").header("Authorization", jetonEtudiant)).andExpect(status().isOk());

        mockMvc.perform(post("/admin/utilisateurs/" + id + "/desactiver").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        mockMvc.perform(get("/etudiant/evaluations").header("Authorization", jetonEtudiant)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType(JSON).content(login(email, provisoire))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/utilisateurs?actif=false&q=" + email).header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/admin/utilisateurs/" + id + "/activer").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true));
        mockMvc.perform(post("/auth/login").contentType(JSON).content(login(email, provisoire))).andExpect(status().isOk());

        long idAdmin = jdbcTemplate.queryForObject("select id from utilisateurs where email = 'admin@ept.sn'", Long.class);
        mockMvc.perform(post("/admin/utilisateurs/" + idAdmin + "/desactiver").header("Authorization", admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPTE_OPERATION_REFUSEE"));
        mockMvc.perform(post("/admin/utilisateurs/999999/desactiver").header("Authorization", admin)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Réinitialiser le mot de passe : l'ancien ne marche plus, le nouveau marche, le haché change")
    void reinitialiserMotDePasse() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        String email = unEmail();
        long id = creerEtudiant(admin, email);
        String ancien = jdbcTemplate.queryForObject("select mot_de_passe from utilisateurs where id = ?", String.class, id);

        String nouveau = reinitialiser(admin, id);

        assertThat(nouveau).hasSize(12);
        assertThat(jdbcTemplate.queryForObject("select mot_de_passe from utilisateurs where id = ?", String.class, id)).isNotEqualTo(ancien);
        mockMvc.perform(post("/auth/login").contentType(JSON).content(login(email, nouveau))).andExpect(status().isOk());
        mockMvc.perform(post("/admin/utilisateurs/999999/reinitialiser-mot-de-passe").header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- promotions

    @Test
    @DisplayName("Créer une promotion : 201 ; même nom et même année : 409 ; champ vide : 400")
    void promotions() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        String nom = "DIC3 " + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post("/admin/promotions").header("Authorization", admin).contentType(JSON)
                        .content("{\"nom\":\"" + nom + "\",\"annee\":\"2026-2027\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nbEtudiants").value(0));
        mockMvc.perform(post("/admin/promotions").header("Authorization", admin).contentType(JSON)
                        .content("{\"nom\":\"" + nom.toUpperCase() + "\",\"annee\":\"2026-2027\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/admin/promotions").header("Authorization", admin).contentType(JSON)
                        .content("{\"nom\":\"\",\"annee\":\"2026\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Renommer une promotion : 200 ; doublon avec une autre : 409 ; inconnue : 404 ; champ vide : 400")
    void renommerPromotion() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        String suffixe = UUID.randomUUID().toString().substring(0, 6);
        long id = creerPromotion(admin, "Renom A " + suffixe, "2030");
        creerPromotion(admin, "Renom B " + suffixe, "2030");

        mockMvc.perform(put("/admin/promotions/" + id).header("Authorization", admin).contentType(JSON)
                        .content("{\"nom\":\"Renom A2 " + suffixe + "\",\"annee\":\"2031\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Renom A2 " + suffixe))
                .andExpect(jsonPath("$.annee").value("2031")).andExpect(jsonPath("$.nbEtudiants").value(0));
        // sans changement : pas de conflit avec soi-même
        mockMvc.perform(put("/admin/promotions/" + id).header("Authorization", admin).contentType(JSON)
                        .content("{\"nom\":\"renom a2 " + suffixe + "\",\"annee\":\"2031\"}")).andExpect(status().isOk());
        mockMvc.perform(put("/admin/promotions/" + id).header("Authorization", admin).contentType(JSON)
                        .content("{\"nom\":\"Renom B " + suffixe + "\",\"annee\":\"2030\"}")).andExpect(status().isConflict());
        mockMvc.perform(put("/admin/promotions/999999").header("Authorization", admin).contentType(JSON)
                        .content("{\"nom\":\"X\",\"annee\":\"1\"}")).andExpect(status().isNotFound());
        mockMvc.perform(put("/admin/promotions/" + id).header("Authorization", admin).contentType(JSON)
                        .content("{\"nom\":\" \",\"annee\":\"1\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Supprimer une promotion : vide 204 puis 404 ; avec des étudiants 409 et elle reste ; accès réservé à l'admin")
    void supprimerPromotion() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        long vide = creerPromotion(admin, "Vide " + UUID.randomUUID().toString().substring(0, 6), "2030");
        mockMvc.perform(delete("/admin/promotions/" + vide).header("Authorization", bearer("prof@ept.sn", "prof123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/promotions/" + vide).header("Authorization", admin)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/admin/promotions/" + vide).header("Authorization", admin)).andExpect(status().isNotFound());

        Long occupee = promotionId();
        mockMvc.perform(delete("/admin/promotions/" + occupee).header("Authorization", admin)).andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("select count(*) from promotions where id = ?", Integer.class, occupee)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- import CSV

    @Test
    @DisplayName("Import valide : 200, comptes créés, mots de passe provisoires valides, séparateur « , », BOM et guillemets acceptés")
    void importValide() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        String e1 = unEmail(), e2 = unEmail();
        String csv = "﻿nom;prenom;email;numEtudiant;promotion\n"
                + "Diop;Moussa;" + e1 + ";" + unNumero() + ";DIC2 Informatique\n"
                + "\"Ndiaye, Sy\";Awa;" + e2.toUpperCase() + ";" + unNumero() + ";dic2 informatique\n";

        String corps = importer(admin, csv).andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value(true)).andExpect(jsonPath("$.nbCrees").value(2))
                .andExpect(jsonPath("$.erreurs").isEmpty()).andReturn().getResponse().getContentAsString();

        String provisoire = JsonPath.read(corps, "$.comptes[0].motDePasseProvisoire");
        mockMvc.perform(post("/auth/login").contentType(JSON).content(login(e1, provisoire))).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select nom from utilisateurs where email = ?", String.class, e2)).isEqualTo("Ndiaye, Sy");

        String virgule = "nom,prenom,email,numEtudiant,promotion\nBa,Cheikh," + unEmail() + "," + unNumero() + ",DIC2 Informatique\n";
        importer(admin, virgule).andExpect(status().isOk()).andExpect(jsonPath("$.nbCrees").value(1));
    }

    @Test
    @DisplayName("Import avec erreurs : 422, toutes les erreurs listées avec leur ligne, AUCUN compte créé (tout ou rien)")
    void importToutOuRien() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        int avant = jdbcTemplate.queryForObject("select count(*) from utilisateurs", Integer.class);
        String bon = unEmail(), doublon = unEmail();
        String csv = "nom;prenom;email;numEtudiant;promotion\n"
                + "Diop;Moussa;" + bon + ";" + unNumero() + ";DIC2 Informatique\n"       // ligne 2 : valide
                + "Fall;Awa;pas-un-email;" + unNumero() + ";DIC2 Informatique\n"          // ligne 3
                + "Sy;Ali;" + doublon + ";N-1;DIC2 Informatique\n"                        // ligne 4
                + "Sy;Ali;" + doublon + ";N-2;DIC2 Informatique\n"                        // ligne 5 : e-mail en double
                + "Ba;Omar;" + unEmail() + ";" + unNumero() + ";Promotion fantôme\n"      // ligne 6
                + "Ka;Mame;admin@ept.sn;" + unNumero() + ";DIC2 Informatique\n"           // ligne 7 : e-mail déjà en base
                + "Ndoye;Pape;" + unEmail() + ";;DIC2 Informatique\n";                    // ligne 8 : champ manquant

        String corps = importer(admin, csv).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.importe").value(false)).andExpect(jsonPath("$.nbCrees").value(0))
                .andExpect(jsonPath("$.comptes").isEmpty()).andReturn().getResponse().getContentAsString();

        assertThat((java.util.List<Integer>) JsonPath.read(corps, "$.erreurs[*].ligne")).containsExactly(3, 5, 6, 7, 8);
        assertThat(jdbcTemplate.queryForObject("select count(*) from utilisateurs", Integer.class)).isEqualTo(avant);
        assertThat(jdbcTemplate.queryForObject("select count(*) from utilisateurs where email = ?", Integer.class, bon)).isZero();
    }

    @Test
    @DisplayName("Import : fichier vide, en-tête incorrecte, trop de lignes -> 400")
    void importInvalide() throws Exception {
        String admin = bearer("admin@ept.sn", "admin123");
        importer(admin, "").andExpect(status().isBadRequest());
        importer(admin, "nom;prenom\nA;B\n").andExpect(status().isBadRequest());
        importer(admin, "nom;prenom;email;numEtudiant;promotion\n").andExpect(status().isBadRequest());
        StringBuilder trop = new StringBuilder("nom;prenom;email;numEtudiant;promotion\n");
        for (int i = 0; i <= ImportEtudiantsService.LIGNES_MAX; i++) trop.append("A;B;e").append(i).append("@x.sn;N").append(i).append(";P\n");
        importer(admin, trop.toString()).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- accès

    @Test
    @DisplayName("Accès : enseignant 403, étudiant 403, sans jeton 401, sur toutes les routes d'administration")
    void acces() throws Exception {
        String prof = bearer("prof@ept.sn", "prof123");
        String etudiant = bearer("etudiant@ept.sn", "etudiant123");
        for (String jeton : new String[]{prof, etudiant}) {
            mockMvc.perform(get("/admin/utilisateurs").header("Authorization", jeton)).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/utilisateurs").header("Authorization", jeton).contentType(JSON)
                    .content(creation("A", "B", unEmail(), "ADMIN", null, null, null))).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/utilisateurs/1/desactiver").header("Authorization", jeton)).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/utilisateurs/1/reinitialiser-mot-de-passe").header("Authorization", jeton)).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/promotions").header("Authorization", jeton).contentType(JSON)
                    .content("{\"nom\":\"X\",\"annee\":\"1\"}")).andExpect(status().isForbidden());
            importer(jeton, "nom;prenom;email;numEtudiant;promotion\n").andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/admin/utilisateurs")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- outils

    private ResultActions importer(String jeton, String csv) throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("fichier", "etudiants.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/admin/etudiants/import").file(fichier).header("Authorization", jeton));
    }

    private long creerEtudiant(String admin, String email) throws Exception {
        String corps = mockMvc.perform(post("/admin/utilisateurs").header("Authorization", admin).contentType(JSON)
                        .content(creation("Sy", "Mariama", email, "ETUDIANT", promotionId(), unNumero(), null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.utilisateur.id")).longValue();
    }

    private long creerPromotion(String admin, String nom, String annee) throws Exception {
        String corps = mockMvc.perform(post("/admin/promotions").header("Authorization", admin).contentType(JSON)
                        .content("{\"nom\":\"" + nom + "\",\"annee\":\"" + annee + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.id")).longValue();
    }

    private String reinitialiser(String admin, long id) throws Exception {
        String corps = mockMvc.perform(post("/admin/utilisateurs/" + id + "/reinitialiser-mot-de-passe").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corps, "$.motDePasseProvisoire");
    }

    private Long promotionId() {
        return jdbcTemplate.queryForObject("select id from promotions where nom = 'DIC2 Informatique'", Long.class);
    }

    private static String unEmail() { return "admin-test-" + UUID.randomUUID() + "@ept.sn"; }

    private static String unNumero() { return "NUM-" + UUID.randomUUID(); }

    private static String creation(String nom, String prenom, String email, String role, Long promotionId, String numEtudiant, String matricule) {
        return "{\"nom\":" + q(nom) + ",\"prenom\":" + q(prenom) + ",\"email\":" + q(email) + ",\"role\":" + q(role)
                + ",\"promotionId\":" + promotionId + ",\"numEtudiant\":" + q(numEtudiant) + ",\"matricule\":" + q(matricule) + "}";
    }

    private static String modification(String nom, String prenom, String email, Long promotionId, String numEtudiant, String matricule) {
        return "{\"nom\":" + q(nom) + ",\"prenom\":" + q(prenom) + ",\"email\":" + q(email)
                + ",\"promotionId\":" + promotionId + ",\"numEtudiant\":" + q(numEtudiant) + ",\"matricule\":" + q(matricule) + "}";
    }

    private static String q(String valeur) { return valeur == null ? "null" : "\"" + valeur.replace("\"", "\\\"") + "\""; }

    private static String login(String email, String motDePasse) {
        return "{\"email\":" + q(email) + ",\"motDePasse\":" + q(motDePasse) + "}";
    }

    private String bearer(String email, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(JSON).content(login(email, motDePasse)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.token");
    }
}
