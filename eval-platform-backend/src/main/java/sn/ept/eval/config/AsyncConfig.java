package sn.ept.eval.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "correctionTaskExecutor")
    public Executor correctionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        // Fin d'examen d'une promotion entière : toutes les copies arrivent en même temps. Au-delà, la tâche
        // est refusée sans faire échouer la remise (CorrectionDeclencheur) et reprise par la relance planifiée.
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("CorrectionAsync-");
        executor.initialize();
        return executor;
    }
}
