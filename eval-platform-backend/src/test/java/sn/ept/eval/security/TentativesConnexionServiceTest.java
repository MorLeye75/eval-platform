package sn.ept.eval.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sn.ept.eval.exception.TropDeTentativesException;
import sn.ept.eval.security.services.TentativesConnexionService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TentativesConnexionServiceTest {

    /** Horloge que le test fait avancer à la main. */
    private static final class HorlogeManuelle extends Clock {
        private Instant instant = Instant.parse("2026-10-19T10:00:00Z");

        void avancer(Duration duree) { instant = instant.plus(duree); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private static final String CLE = TentativesConnexionService.cle("Etudiant@ept.sn", "10.0.0.1");

    private HorlogeManuelle horloge;
    private TentativesConnexionService service;

    @BeforeEach
    void setUp() {
        horloge = new HorlogeManuelle();
        service = new TentativesConnexionService(horloge, 5, 15, 15);
    }

    private void echouer(int fois) {
        for (int i = 0; i < fois; i++) {
            service.enregistrerEchec(CLE);
        }
    }

    @Test
    @DisplayName("Blocage au 5e échec, avec le délai restant")
    void bloqueApresCinqEchecs() {
        echouer(4);
        assertThatCode(() -> service.verifierNonBloque(CLE)).doesNotThrowAnyException();

        service.enregistrerEchec(CLE);
        assertThatThrownBy(() -> service.verifierNonBloque(CLE))
                .isInstanceOf(TropDeTentativesException.class)
                .extracting(e -> ((TropDeTentativesException) e).getSecondesAvantNouvelEssai())
                .isEqualTo(15 * 60L);
    }

    @Test
    @DisplayName("Le blocage expire après la durée prévue")
    void blocageExpire() {
        echouer(5);
        horloge.avancer(Duration.ofMinutes(15).plusSeconds(1));

        assertThatCode(() -> service.verifierNonBloque(CLE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Les échecs hors de la fenêtre de 15 min ne s'additionnent pas")
    void fenetreGlissante() {
        echouer(4);
        horloge.avancer(Duration.ofMinutes(16));
        echouer(4);

        assertThatCode(() -> service.verifierNonBloque(CLE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Une connexion réussie remet le compteur à zéro")
    void reussiteReinitialise() {
        echouer(4);
        service.reinitialiser(CLE);
        echouer(4);

        assertThatCode(() -> service.verifierNonBloque(CLE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La clé ignore la casse de l'email et distingue les adresses IP")
    void cleNormalisee() {
        echouer(5);

        assertThatThrownBy(() -> service.verifierNonBloque(TentativesConnexionService.cle(" ETUDIANT@ept.sn", "10.0.0.1")))
                .isInstanceOf(TropDeTentativesException.class);
        assertThatCode(() -> service.verifierNonBloque(TentativesConnexionService.cle("etudiant@ept.sn", "10.0.0.2")))
                .doesNotThrowAnyException();
    }
}
