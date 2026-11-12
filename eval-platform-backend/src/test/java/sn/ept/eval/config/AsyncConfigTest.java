package sn.ept.eval.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un ThreadPoolExecutor ne crée de thread au-delà du « core » qu'une fois sa file pleine : avec une file de 1000
 * copies, un « max » plus grand que le « core » ne sert jamais et laisse croire à plus de parallélisme.
 */
class AsyncConfigTest {

    @Test
    @DisplayName("Correction des QCM : taille du pool fixe (core = max), pas de maximum qui ne serait jamais atteint")
    void correctionTaskExecutor_TailleFixe() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().correctionTaskExecutor();
        try {
            assertThat(executor.getMaxPoolSize()).isEqualTo(executor.getCorePoolSize());
        } finally {
            executor.shutdown();
        }
    }
}
