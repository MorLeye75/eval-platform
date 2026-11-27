package sn.ept.eval.config;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ept.eval.model.ReponseCode;
import sn.ept.eval.model.ResultatCasTest;
import sn.ept.eval.model.enums.VerdictCas;
import sn.ept.eval.repository.EvaluationRepository;
import sn.ept.eval.repository.ReponseEtudiantRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Jeu de données de démonstration (profils dev et h2-dev) : il doit permettre de montrer tout le parcours de
 * correction et de publication, sans attendre de date de fermeture, et ne jamais se dupliquer au redémarrage.
 */
@SpringBootTest(properties = {
        "app.demo.jeu-complet=true",
        "spring.datasource.url=jdbc:h2:mem:donnees-demo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@AutoConfigureMockMvc
class DonneesDemoIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private DonneesDemoInitializer initializer;
    @Autowired private ReponseEtudiantRepository reponseEtudiantRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Évaluations de démonstration : terminées et publiées (avec notes, statistiques, historique), terminée à publier, brouillon et à venir")
    void evaluationsDeDemonstration() throws Exception {
        String prof = bearer("prof@ept.sn", "prof123");
        Map<String, Map<String, Object>> parTitre = evaluations(prof);

        assertThat(parTitre.get(DonneesDemoInitializer.TITRE_RESEAUX)).containsEntry("statut", "TERMINEE").containsEntry("resultatsPublies", true);
        assertThat(parTitre.get(DonneesDemoInitializer.TITRE_PARTIEL)).containsEntry("statut", "TERMINEE").containsEntry("resultatsPublies", true);
        assertThat(parTitre.get(DonneesDemoInitializer.TITRE_CONTROLE)).containsEntry("statut", "TERMINEE");
        assertThat(parTitre.get(DonneesDemoInitializer.TITRE_BROUILLON)).containsEntry("statut", "BROUILLON");
        assertThat(parTitre.get(DonneesDemoInitializer.TITRE_A_VENIR)).containsEntry("statut", "PUBLIEE");

        // Partiel : copies notées, statistiques calculées, une note revue avant publication (historique)
        Number partiel = (Number) parTitre.get(DonneesDemoInitializer.TITRE_PARTIEL).get("id");
        mockMvc.perform(get("/enseignant/evaluations/" + partiel + "/statistiques").header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbNotees").value(12))
                .andExpect(jsonPath("$.moyenneSur20").isNumber());
        Integer revisions = jdbcTemplate.queryForObject("select count(*) from revisions_note", Integer.class);
        assertThat(revisions).isGreaterThanOrEqualTo(1);

        // Un deuxième enseignant possède sa propre évaluation (DIC3) : le premier n'y a pas accès
        mockMvc.perform(get("/enseignant/evaluations/" + idDe(bearer("prof2@ept.sn", "prof123"), DonneesDemoInitializer.TITRE_DIC3)
                        + "/statistiques").header("Authorization", prof))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Étudiant de démonstration : ses résultats publiés et ses notifications")
    void espaceEtudiant() throws Exception {
        String aminou = bearer("etudiant@ept.sn", "etudiant123");
        mockMvc.perform(get("/etudiant/performances").header("Authorization", aminou))
                .andExpect(status().isOk());
        String notifications = mockMvc.perform(get("/notifications").header("Authorization", aminou))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(notifications, "$.notifications[*].type");
        assertThat(types).filteredOn("RESULTAT_PUBLIE"::equals).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Parcours de démonstration : le contrôle continu attend (non publié, invisible pour Aminou) ; noter le code, valider les propositions IA, publier -> Aminou est notifié")
    void parcoursDeCorrectionEtPublication() throws Exception {
        String prof = bearer("prof@ept.sn", "prof123");
        Map<String, Object> controleAvant = evaluations(prof).get(DonneesDemoInitializer.TITRE_CONTROLE);
        assertThat(controleAvant).containsEntry("resultatsPublies", false);
        Number controle = (Number) controleAvant.get("id");
        String liste = mockMvc.perform(get("/etudiant/evaluations").header("Authorization", bearer("etudiant@ept.sn", "etudiant123")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Object> notesControle = JsonPath.read(liste, "$[?(@.titre == '" + DonneesDemoInitializer.TITRE_CONTROLE + "')].noteFinale");
        assertThat(notesControle).containsOnlyNulls();

        // La copie d'Aminou attend : code non noté (repli manuel), question ouverte avec une proposition de l'IA
        String lignes = mockMvc.perform(get("/enseignant/evaluations/" + controle + "/resultats").header("Authorization", prof))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> copieAminou = JsonPath.read(lignes, "$.lignes[?(@.prenom == 'Aminou')].soumissionId");
        List<String> etats = JsonPath.read(lignes, "$.lignes[*].etat");
        assertThat(etats).contains("ABSENT", "EVALUE", "EN_CORRECTION");

        String copie = mockMvc.perform(get("/enseignant/soumissions/" + copieAminou.get(0) + "/copie").header("Authorization", prof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_CORRECTION"))
                .andExpect(jsonPath("$.reponses[?(@.type == 'CODE')].notee").value(false))
                .andReturn().getResponse().getContentAsString();
        List<Integer> codeId = JsonPath.read(copie, "$.reponses[?(@.type == 'CODE')].reponseId");
        mockMvc.perform(put("/enseignant/reponses/" + codeId.get(0) + "/note").header("Authorization", prof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":8}"))
                .andExpect(status().isOk());

        String propositions = mockMvc.perform(get("/enseignant/corrections-ia?evaluationId=" + controle + "&statut=EN_ATTENTE")
                        .header("Authorization", prof))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(propositions, "$.propositions[*].id");
        assertThat(ids).isNotEmpty();
        for (Integer id : ids) {
            mockMvc.perform(put("/enseignant/corrections-ia/" + id + "/valider").header("Authorization", prof))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/enseignant/soumissions/" + copieAminou.get(0) + "/copie").header("Authorization", prof))
                .andExpect(jsonPath("$.statut").value("EVALUE"));

        // Fermé depuis plus que la marge : publiable tout de suite
        mockMvc.perform(post("/enseignant/evaluations/" + controle + "/resultats/publier").header("Authorization", prof))
                .andExpect(status().isOk());
        String notifications = mockMvc.perform(get("/notifications").header("Authorization", bearer("etudiant@ept.sn", "etudiant123")))
                .andReturn().getResponse().getContentAsString();
        List<Object> pourLeControle = JsonPath.read(notifications, "$.notifications[?(@.message =~ /.*Contrôle continu.*/)]");
        assertThat(pourLeControle).isNotEmpty();
    }

    @Test
    @DisplayName("Code des copies : chaque verdict et chaque note correspondent à ce que le code affiché produit vraiment")
    void verdictsCoherentsAvecLeCode() {
        Integer verifiees = transactionTemplate.execute(tx -> {
            int n = 0;
            for (var brute : reponseEtudiantRepository.findAll()) {
                if (!(org.hibernate.Hibernate.unproxy(brute) instanceof ReponseCode rc) || rc.getResultatsCasTests().isEmpty()) continue;
                ProgrammesDemo programme = ProgrammesDemo.de(rc.getCodeSoumis())
                        .orElseThrow(() -> new AssertionError("code inconnu : " + rc.getCodeSoumis()));
                int reussis = 0;
                for (ResultatCasTest rt : rc.getResultatsCasTests()) {
                    String attendu = rt.getCasDeTest().getSortieAttendue();
                    String obtenu = programme.executer(rt.getCasDeTest().getEntree()).orElse("");
                    assertThat(rt.getSortieObtenue()).as("sortie de %s pour %s", programme, rt.getCasDeTest().getEntree()).isEqualTo(obtenu);
                    assertThat(rt.getEstValide()).isEqualTo(!programme.erreurDeSyntaxe() && obtenu.equals(attendu));
                    assertThat(rt.getVerdict()).isEqualTo(programme.erreurDeSyntaxe() ? VerdictCas.ERREUR_EXECUTION
                            : rt.getEstValide() ? VerdictCas.REUSSI : VerdictCas.MAUVAISE_REPONSE);
                    if (rt.getEstValide()) reussis++;
                }
                double attendue = rc.getQuestion().getPoints() * reussis / rc.getResultatsCasTests().size();
                Double revisee = jdbcTemplate.query("select ancienne_note from revisions_note where reponse_id = ?",
                        rs -> rs.next() ? rs.getDouble(1) : null, rc.getId());
                // Une note revue garde dans l'historique la note de la correction automatique
                assertThat(revisee != null ? revisee : rc.getNoteObtenue()).as("note de %s", programme).isEqualTo(attendue);
                n++;
            }
            return n;
        });
        assertThat(verifiees).isGreaterThanOrEqualTo(20);
    }

    @Test
    @DisplayName("Programmes de démonstration : sorties réelles (voyelles sans minuscules ni y : 1 cas sur 4 ; somme avec isdigit : le cas négatif échoue)")
    void sortiesDesProgrammes() {
        assertThat(List.of("Bonjour", "PYTHON", "xyz", "rythme").stream()
                .map(e -> ProgrammesDemo.VOYELLES_DEBUTANT.executer(e).orElseThrow()).toList()).containsExactly("3", "0", "0", "1");
        assertThat(List.of("Bonjour", "PYTHON", "xyz", "rythme").stream()
                .map(e -> ProgrammesDemo.VOYELLES_JUSTE.executer(e).orElseThrow()).toList()).containsExactly("3", "2", "1", "2");
        assertThat(ProgrammesDemo.SOMME_SANS_NEGATIFS.executer("4\n-1\n1\n5\n5")).contains("11");
        assertThat(ProgrammesDemo.SOMME_AVEC_N.executer("3\n1\n2\n3")).contains("9");
        assertThat(ProgrammesDemo.SOMME_JUSTE.executer("0")).contains("0");
        assertThat(ProgrammesDemo.VOYELLES_ERREUR_SYNTAXE.executer("Bonjour")).isEmpty();
    }

    @Test
    @DisplayName("Redémarrage : le jeu de données n'est pas recréé (aucun doublon)")
    void pasDeDoublonAuRedemarrage() throws Exception {
        long avant = evaluationRepository.count();
        initializer.run();
        assertThat(evaluationRepository.count()).isEqualTo(avant);
    }

    // ------------------------------------------------------------------

    private Map<String, Map<String, Object>> evaluations(String jeton) throws Exception {
        String json = mockMvc.perform(get("/enseignant/evaluations").header("Authorization", jeton))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> liste = JsonPath.read(json, "$");
        return liste.stream().collect(java.util.stream.Collectors.toMap(e -> (String) e.get("titre"), e -> e, (a, b) -> a));
    }

    private Number idDe(String jeton, String titre) throws Exception {
        return (Number) evaluations(jeton).get(titre).get("id");
    }

    private String bearer(String email, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.token");
    }
}
