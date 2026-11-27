package sn.ept.eval.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import com.jayway.jsonpath.JsonPath;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Promotion;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.PromotionRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie de bout en bout les réponses d'erreur HTTP (format RFC 7807) et les règles d'accès.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecuriteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Sans jeton : 401 au format ProblemDetail")
    void sansJeton_401ProblemDetail() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"));
    }

    @Test
    @DisplayName("Mauvais mot de passe : 401 générique au format ProblemDetail")
    void loginInvalide_401ProblemDetail() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"etudiant@ept.sn\",\"motDePasse\":\"mauvais\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTIFICATION_ECHOUEE"));
    }

    @Test
    @DisplayName("Force brute : après 5 échecs, même le bon mot de passe reçoit 429 avec Retry-After")
    void forceBrute_429() throws Exception {
        // Compte dédié pour ne pas bloquer les comptes de démonstration utilisés par d'autres tests
        String email = "brute-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Test", "Brute", email, passwordEncoder.encode("bonMotDePasse"),
                "ETU-" + UUID.randomUUID(), null));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"motDePasse\":\"mauvais\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"bonMotDePasse\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TROP_DE_TENTATIVES"));
    }

    @Test
    @DisplayName("Connexion : seule la date de dernière connexion change, l'audit (modifié par / le) est conservé")
    void login_NeModifiePasLAudit() throws Exception {
        String email = "audit-" + UUID.randomUUID() + "@ept.sn";
        Long id = utilisateurRepository.save(new Etudiant("Test", "Audit", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), null)).getId();
        // Dernière modification réelle du compte : par un administrateur, à une date connue
        LocalDateTime modifieLe = LocalDateTime.of(2026, 9, 1, 8, 30);
        jdbcTemplate.update("update utilisateurs set modifie_par = 'admin@ept.sn', date_modification = ? where id = ?",
                modifieLe, id);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"motDePasse\"}"))
                .andExpect(status().isOk());

        Map<String, Object> ligne = jdbcTemplate.queryForMap(
                "select modifie_par, date_modification, derniere_connexion from utilisateurs where id = ?", id);
        org.assertj.core.api.Assertions.assertThat(ligne.get("MODIFIE_PAR")).isEqualTo("admin@ept.sn");
        org.assertj.core.api.Assertions.assertThat(((java.sql.Timestamp) ligne.get("DATE_MODIFICATION")).toLocalDateTime())
                .isEqualTo(modifieLe);
        org.assertj.core.api.Assertions.assertThat(ligne.get("DERNIERE_CONNEXION")).isNotNull();
    }

    @Test
    @DisplayName("JSON illisible : reste un 400 (et non un 500 du gestionnaire générique)")
    void jsonIllisible_400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{pas du json"))
                .andExpect(status().isBadRequest())
                // Erreurs gérées par Spring elles-mêmes : même format, avec un code
                .andExpect(jsonPath("$.code").value("REQUETE_INCORRECTE"));
    }

    @Test
    @DisplayName("GET /etudiant/evaluations : 401 sans jeton, 403 pour un enseignant, 200 pour un étudiant")
    void listeEvaluationsEtudiant_ReserveeAuxEtudiants() throws Exception {
        mockMvc.perform(get("/etudiant/evaluations"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/etudiant/evaluations").header("Authorization", "Bearer " + jeton("prof@ept.sn", "prof123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/etudiant/evaluations").header("Authorization", "Bearer " + jeton(nouvelEtudiantDic2(), "motDePasse")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].typesQuestions").isArray());
    }

    @Test
    @DisplayName("Sauvegarde sur une version périmée : 409 VERSION_OBSOLETE avec la version actuelle (RFC 7807)")
    void sauvegardeVersionPerimee_409VersionObsolete() throws Exception {
        String bearer = "Bearer " + jeton(nouvelEtudiantDic2(), "motDePasse");
        Long evalQcmId = evaluationRepository.findAll().stream()
                .filter(e -> e.getTitre().contains("QCM")).findFirst().map(Evaluation::getId).orElseThrow();

        String copie = mockMvc.perform(post("/soumissions/evaluations/" + evalQcmId + "/demarrer").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionReponses").value(0))
                .andReturn().getResponse().getContentAsString();
        Integer soumissionId = JsonPath.read(copie, "$.id");

        mockMvc.perform(put("/soumissions/" + soumissionId + "/sauvegarder").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reponses\":[],\"versionAttendue\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionReponses").value(1));

        // Second onglet resté sur la version 0
        mockMvc.perform(put("/soumissions/" + soumissionId + "/sauvegarder").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reponses\":[],\"versionAttendue\":0}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VERSION_OBSOLETE"))
                .andExpect(jsonPath("$.versionActuelle").value(1));
    }

    @Test
    @DisplayName("Anti-triche : chaque sortie d'onglet est comptée par le serveur, qui renvoie le compteur à jour")
    void changementOnglet_CompteurServeur() throws Exception {
        String bearer = "Bearer " + jeton(nouvelEtudiantDic2(), "motDePasse");
        Long evalQcmId = evaluationRepository.findAll().stream()
                .filter(e -> e.getTitre().contains("QCM")).findFirst().map(Evaluation::getId).orElseThrow();
        String copie = mockMvc.perform(post("/soumissions/evaluations/" + evalQcmId + "/demarrer").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer soumissionId = JsonPath.read(copie, "$.id");

        mockMvc.perform(post("/soumissions/" + soumissionId + "/changement-onglet").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbChangementsOnglet").value(1));
        mockMvc.perform(post("/soumissions/" + soumissionId + "/changement-onglet").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbChangementsOnglet").value(2));

        // Après un rechargement, la copie reprise rend le compteur (le front ne repart pas de zéro)
        mockMvc.perform(post("/soumissions/evaluations/" + evalQcmId + "/demarrer").header("Authorization", bearer))
                .andExpect(jsonPath("$.nbChangementsOnglet").value(2));
    }

    /** Étudiant dédié de la promotion DIC2 (mot de passe "motDePasse") : les comptes de démo restent intacts. */
    private String nouvelEtudiantDic2() {
        String email = "etu-http-" + UUID.randomUUID() + "@ept.sn";
        Promotion dic2 = promotionRepository.findByNom("DIC2 Informatique").orElseThrow();
        utilisateurRepository.save(new Etudiant("Test", "Http", email, passwordEncoder.encode("motDePasse"),
                "ETU-" + UUID.randomUUID(), dic2));
        return email;
    }

    private String jeton(String email, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.token");
    }

    @Test
    @DisplayName("Console H2 non exposée hors profil h2-dev")
    void consoleH2_NonPublique() throws Exception {
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().isUnauthorized());
    }
}
