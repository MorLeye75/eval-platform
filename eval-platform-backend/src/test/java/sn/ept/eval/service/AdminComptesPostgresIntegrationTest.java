package sn.ept.eval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sn.ept.eval.exception.OperationCompteRefuseeException;
import sn.ept.eval.model.Administrateur;
import sn.ept.eval.repository.AdministrateurRepository;
import sn.ept.eval.repository.UtilisateurRepository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fou « dernier administrateur actif » (DS-16) sur un vrai PostgreSQL : le verrou FOR UPDATE sur les
 * administrateurs actifs (héritage JOINED) et la sérialisation de deux désactivations simultanées.
 * H2 ne reproduit pas ces verrous. Ignoré sans Docker.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@Testcontainers(disabledWithoutDocker = true)
class AdminComptesPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private AdminComptesService service;
    @Autowired private AdministrateurRepository administrateurRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    @MockitoBean private NotificationService notificationService;

    private Administrateur nouvelAdmin() {
        return utilisateurRepository.save(new Administrateur("Admin", "Test", "admin-" + UUID.randomUUID() + "@ept.sn", "x"));
    }

    private long actifs() {
        return administrateurRepository.findAll().stream().filter(a -> Boolean.TRUE.equals(a.getActif())).count();
    }

    @Test
    @DisplayName("La requête FOR UPDATE sur les administrateurs actifs s'exécute sur PostgreSQL ; le dernier actif ne peut pas être désactivé")
    void dernierAdministrateur() {
        Administrateur a = nouvelAdmin();
        Administrateur b = nouvelAdmin();
        // Désactive tous les autres administrateurs éventuels pour partir de deux actifs exactement
        administrateurRepository.findAll().stream().filter(x -> !x.getId().equals(a.getId()) && !x.getId().equals(b.getId()))
                .forEach(x -> { x.setActif(false); administrateurRepository.save(x); });

        assertThat(service.changerEtat(b.getId(), false, a.getEmail()).actif()).isFalse();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.changerEtat(a.getId(), false, "autre@ept.sn"))
                .isInstanceOf(OperationCompteRefuseeException.class);
        assertThat(actifs()).isEqualTo(1);
    }

    @Test
    @DisplayName("Deux désactivations simultanées des deux seuls administrateurs : une seule réussit, il en reste un actif")
    void desactivationsSimultanees() throws Exception {
        Administrateur a = nouvelAdmin();
        Administrateur b = nouvelAdmin();
        administrateurRepository.findAll().stream().filter(x -> !x.getId().equals(a.getId()) && !x.getId().equals(b.getId()))
                .forEach(x -> { x.setActif(false); administrateurRepository.save(x); });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        AtomicInteger reussites = new AtomicInteger();
        AtomicInteger refus = new AtomicInteger();
        try {
            // Chacun est désactivé par un compte tiers : seul le décompte des administrateurs actifs peut l'empêcher
            CompletableFuture<Void> f1 = CompletableFuture.runAsync(() -> essayer(a.getId(), depart, reussites, refus), pool);
            CompletableFuture<Void> f2 = CompletableFuture.runAsync(() -> essayer(b.getId(), depart, reussites, refus), pool);
            depart.countDown();
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(reussites.get()).isEqualTo(1);
        assertThat(refus.get()).isEqualTo(1);
        assertThat(actifs()).isEqualTo(1);
    }

    private void essayer(Long id, CountDownLatch depart, AtomicInteger reussites, AtomicInteger refus) {
        try {
            depart.await();
            service.changerEtat(id, false, "tiers@ept.sn");
            reussites.incrementAndGet();
        } catch (OperationCompteRefuseeException e) {
            refus.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
