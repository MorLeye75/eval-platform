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

import static org.assertj.core.api.Assertions.assertThat;
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

    private static final String EMAIL = "Etudiant@ept.sn";
    private static final String IP = "10.0.0.1";

    private HorlogeManuelle horloge;
    private TentativesConnexionService service;

    @BeforeEach
    void setUp() {
        horloge = new HorlogeManuelle();
        // 5 échecs par couple, fenêtre et blocage de 15 min, 1000 entrées au plus
        service = new TentativesConnexionService(horloge, 5, 15, 15, 1000);
    }

    private void echouer(int fois) {
        for (int i = 0; i < fois; i++) {
            service.enregistrerEchec(EMAIL, IP);
        }
    }

    @Test
    @DisplayName("Blocage au 5e échec, avec le délai restant")
    void bloqueApresCinqEchecs() {
        echouer(4);
        assertThatCode(() -> service.verifierNonBloque(EMAIL, IP)).doesNotThrowAnyException();

        service.enregistrerEchec(EMAIL, IP);
        assertThatThrownBy(() -> service.verifierNonBloque(EMAIL, IP))
                .isInstanceOf(TropDeTentativesException.class)
                .extracting(e -> ((TropDeTentativesException) e).getSecondesAvantNouvelEssai())
                .isEqualTo(15 * 60L);
    }

    @Test
    @DisplayName("Le blocage expire après la durée prévue")
    void blocageExpire() {
        echouer(5);
        horloge.avancer(Duration.ofMinutes(15).plusSeconds(1));

        assertThatCode(() -> service.verifierNonBloque(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Les échecs hors de la fenêtre de 15 min ne s'additionnent pas")
    void fenetreGlissante() {
        echouer(4);
        horloge.avancer(Duration.ofMinutes(16));
        echouer(4);

        assertThatCode(() -> service.verifierNonBloque(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Une connexion réussie remet le compteur du couple à zéro")
    void reussiteReinitialise() {
        echouer(4);
        service.reinitialiser(EMAIL, IP);
        echouer(4);

        assertThatCode(() -> service.verifierNonBloque(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Le couple ignore la casse de l'email et distingue les adresses IP")
    void cleNormalisee() {
        echouer(5);

        assertThatThrownBy(() -> service.verifierNonBloque(" ETUDIANT@ept.sn", IP))
                .isInstanceOf(TropDeTentativesException.class);
        assertThatCode(() -> service.verifierNonBloque("etudiant@ept.sn", "10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Campus derrière un NAT : beaucoup d'échecs depuis une même IP, sur des comptes différents, ne bloquent pas les autres comptes")
    void memeIpComptesDifferents_PasDeBlocageCollectif() {
        for (int i = 0; i < 200; i++) {
            service.enregistrerEchec("compte" + i + "@ept.sn", IP); // un seul essai par compte
        }

        assertThatCode(() -> service.verifierNonBloque("autre@ept.sn", IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Mémoire bornée : des milliers d'emails inventés ne font pas grossir les compteurs sans limite")
    void memoireBornee() {
        TentativesConnexionService borne = new TentativesConnexionService(horloge, 5, 15, 15, 50);
        for (int i = 0; i < 5; i++) borne.enregistrerEchec(EMAIL, IP); // couple bloqué, à conserver

        for (int i = 0; i < 5_000; i++) {
            borne.enregistrerEchec("invente" + i + "@ept.sn", "10.1." + (i / 250) + "." + (i % 250));
        }

        assertThat(borne.nombreDeCompteurs()).isLessThanOrEqualTo(50);
        // Un blocage en cours n'est pas évincé au profit des nouvelles entrées
        assertThatThrownBy(() -> borne.verifierNonBloque(EMAIL, IP)).isInstanceOf(TropDeTentativesException.class);
    }
}
