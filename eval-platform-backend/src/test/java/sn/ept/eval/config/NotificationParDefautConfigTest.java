package sn.ept.eval.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import sn.ept.eval.config.NotificationParDefautConfig.NotificationServiceParDefaut;
import sn.ept.eval.model.Evaluation;
import sn.ept.eval.model.Soumission;
import sn.ept.eval.service.NotificationService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le bean par défaut ne doit jamais l'emporter sur l'implémentation du module de notifications (DS-15), quel que soit
 * l'ordre d'enregistrement des beans : c'est ce que {@code @ConditionalOnMissingBean} ne garantit pas hors
 * auto-configuration, et ce que {@code @Fallback} garantit.
 */
class NotificationParDefautConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    /** Une implémentation réelle, comme le serait celle du module de notifications. */
    @Service
    static class NotificationServiceReel implements NotificationService {
        @Override public void evaluationOuverte(Evaluation evaluation) {}
        @Override public void resultatsPublies(Evaluation evaluation) {}
        @Override public void resultatPublie(Soumission copie) {}
    }

    /** Une classe qui, comme EvaluationStatutService, reçoit le NotificationService par son constructeur. */
    @Component
    static class Consommateur {
        final NotificationService notifications;

        Consommateur(NotificationService notifications) {
            this.notifications = notifications;
        }
    }

    @Configuration
    static class ImplementationDeclareeParBean {
        @Bean
        NotificationService notificationsDuModuleDeMor() {
            return new NotificationServiceReel();
        }
    }

    @Test
    @DisplayName("Sans autre implémentation : le bean par défaut est injecté, rien ne casse")
    void sansAutreImplementation_LeDefautEstUtilise() {
        runner.withUserConfiguration(NotificationParDefautConfig.class, Consommateur.class).run(contexte -> {
            assertThat(contexte).hasNotFailed();
            assertThat(contexte.getBean(Consommateur.class).notifications).isInstanceOf(NotificationServiceParDefaut.class);
        });
    }

    @Test
    @DisplayName("Une implémentation déclarée AVANT la configuration par défaut est celle qui est injectée")
    void implementationEnregistreeAvant_EstPreferee() {
        runner.withUserConfiguration(NotificationServiceReel.class, NotificationParDefautConfig.class, Consommateur.class)
                .run(this::verifierQueLImplementationReelleEstInjectee);
    }

    @Test
    @DisplayName("Une implémentation déclarée APRÈS la configuration par défaut est celle qui est injectée (ordre inverse)")
    void implementationEnregistreeApres_EstPreferee() {
        runner.withUserConfiguration(NotificationParDefautConfig.class, NotificationServiceReel.class, Consommateur.class)
                .run(this::verifierQueLImplementationReelleEstInjectee);
    }

    @Test
    @DisplayName("Une implémentation déclarée par une méthode @Bean est préférée elle aussi, dans les deux ordres")
    void implementationDeclareeParBean_EstPreferee() {
        runner.withUserConfiguration(ImplementationDeclareeParBean.class, NotificationParDefautConfig.class, Consommateur.class)
                .run(this::verifierQueLImplementationReelleEstInjectee);
        runner.withUserConfiguration(NotificationParDefautConfig.class, ImplementationDeclareeParBean.class, Consommateur.class)
                .run(this::verifierQueLImplementationReelleEstInjectee);
    }

    @Test
    @DisplayName("Le bean par défaut reste déclaré mais n'est jamais le candidat retenu : aucune ambiguïté (pas de NoUniqueBeanDefinitionException)")
    void pasDAmbiguite() {
        runner.withUserConfiguration(NotificationParDefautConfig.class, NotificationServiceReel.class).run(contexte -> {
            assertThat(contexte).hasNotFailed();
            assertThat(contexte.getBeansOfType(NotificationService.class)).hasSize(2);
            assertThat(contexte.getBean(NotificationService.class)).isInstanceOf(NotificationServiceReel.class);
        });
    }

    @Test
    @DisplayName("Les trois événements du bean par défaut ne lèvent rien : un événement n'échoue pas faute de notifications")
    void leDefautNeFaitRienSansEchouer() {
        NotificationService defaut = new NotificationServiceParDefaut();
        Evaluation evaluation = new Evaluation();
        evaluation.setId(1L);
        Soumission copie = new Soumission();
        copie.setId(2L);

        defaut.evaluationOuverte(evaluation);
        defaut.resultatsPublies(evaluation);
        defaut.resultatPublie(copie);
    }

    private void verifierQueLImplementationReelleEstInjectee(org.springframework.boot.test.context.assertj.AssertableApplicationContext contexte) {
        assertThat(contexte).hasNotFailed();
        assertThat(contexte.getBean(Consommateur.class).notifications).isInstanceOf(NotificationServiceReel.class);
    }
}
