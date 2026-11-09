package sn.ept.eval.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import sn.ept.eval.model.Etudiant;
import sn.ept.eval.repository.UtilisateurRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les dates d'audit (création, modification) viennent de l'horloge de l'application, comme les règles de temps
 * des épreuves : la relance des corrections compare date_modification à un seuil calculé avec cette horloge.
 * Base H2 propre à cette classe, horloge figée.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Import(AuditHorlogeIntegrationTest.HorlogeFigee.class)
class AuditHorlogeIntegrationTest {

    static final LocalDateTime INSTANT = LocalDateTime.of(2030, 1, 15, 8, 0);

    @TestConfiguration
    static class HorlogeFigee {
        @Bean
        @Primary
        Clock horlogeFigee() {
            ZoneId zone = ZoneId.systemDefault();
            return Clock.fixed(INSTANT.atZone(zone).toInstant(), zone);
        }
    }

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Test
    @DisplayName("Un compte créé est daté par l'horloge de l'application, pas par l'heure système")
    void dateCreation_HorlogeDeLApplication() {
        String email = "horloge-" + UUID.randomUUID() + "@ept.sn";

        Etudiant cree = utilisateurRepository.save(new Etudiant("Test", "Horloge", email, "x", "ETU-" + UUID.randomUUID(), null));

        assertThat(cree.getDateCreation()).isEqualTo(INSTANT);
        assertThat(cree.getDateModification()).isEqualTo(INSTANT);
    }
}
