package sn.ept.eval.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Égalité des entités placées dans des Set (Evaluation.promotions, ReponseQCM.choixCoches). */
class EgaliteEntitesTest {

    private static ChoixReponse choix(Long id) {
        ChoixReponse c = new ChoixReponse("Pile", true);
        c.setId(id);
        return c;
    }

    @Test
    @DisplayName("Deux instances de la même ligne (même identifiant) sont égales : pas de doublon dans un Set")
    void memeIdentifiant_Egales() {
        Set<ChoixReponse> coches = new HashSet<>(Set.of(choix(5L)));

        assertThat(choix(5L)).isEqualTo(choix(5L));
        assertThat(coches.contains(choix(5L))).isTrue();
        assertThat(coches.add(choix(5L))).isFalse();
    }

    @Test
    @DisplayName("Identifiants différents, ou entités pas encore enregistrées : distinctes")
    void identifiantsDifferentsOuAbsents_Distinctes() {
        assertThat(choix(5L)).isNotEqualTo(choix(6L));
        ChoixReponse nouveau = choix(null);
        assertThat(nouveau).isNotEqualTo(choix(null)).isEqualTo(nouveau);
    }

    @Test
    @DisplayName("Promotion : même règle")
    void promotion() {
        Promotion a = new Promotion("DIC2 Informatique", "2025/2026");
        Promotion b = new Promotion("DIC2 Informatique", "2025/2026");
        a.setId(1L);
        b.setId(1L);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(new Promotion("DIC2", "2025/2026")).isNotEqualTo(new Promotion("DIC2", "2025/2026"));
    }
}
