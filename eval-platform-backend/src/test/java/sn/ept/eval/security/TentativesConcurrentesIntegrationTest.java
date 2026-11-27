package sn.ept.eval.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.repository.UtilisateurRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Force brute en parallèle : le seuil de 5 échecs doit tenir même quand les essais arrivent tous en même temps.
 * Sans réservation atomique, chaque requête vérifiait le blocage avant que les autres aient enregistré leur échec,
 * et un attaquant obtenait autant d'essais que de requêtes simultanées. Base H2 propre à cette classe.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tentatives-concurrentes;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class TentativesConcurrentesIntegrationTest {

    private static final int REQUETES = 40;
    private static final int SEUIL = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("40 essais simultanés sur un même compte : 5 au plus sont réellement tentés, les autres reçoivent 429")
    void essaisSimultanes_SeuilRespecte() throws Exception {
        String email = "etu-brute-" + UUID.randomUUID() + "@ept.sn";
        utilisateurRepository.save(new Etudiant("Brute", "Force", email, passwordEncoder.encode("leBonMotDePasse"),
                "ETU-" + UUID.randomUUID(), null));
        String corps = "{\"email\":\"" + email + "\",\"motDePasse\":\"mauvais\"}";

        ExecutorService pool = Executors.newFixedThreadPool(REQUETES);
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<Integer>> reponses = new ArrayList<>();
        for (int i = 0; i < REQUETES; i++) {
            reponses.add(pool.submit(() -> {
                depart.await();
                return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(corps))
                        .andReturn().getResponse().getStatus();
            }));
        }
        depart.countDown();
        int tentes = 0;
        int bloques = 0;
        for (Future<Integer> r : reponses) {
            int statut = r.get(60, TimeUnit.SECONDS);
            if (statut == 401) tentes++;
            if (statut == 429) bloques++;
        }
        pool.shutdown();

        assertThat(tentes).isLessThanOrEqualTo(SEUIL);
        assertThat(tentes + bloques).isEqualTo(REQUETES);
    }
}
