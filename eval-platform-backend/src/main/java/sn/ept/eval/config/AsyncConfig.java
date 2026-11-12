package sn.ept.eval.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(Judge0Proprietes.class)
public class AsyncConfig {

    /**
     * Fin d'examen d'une promotion entière : toutes les copies arrivent en même temps. Au-delà de la file,
     * la tâche est refusée sans faire échouer la remise (CorrectionDeclencheur) et reprise par la relance planifiée.
     */
    private static final int CAPACITE_FILE = 1000;

    /** Threads de correction des copies sans code : assez pour des corrections de quelques millisecondes. */
    static final int THREADS_CORRECTION = 4;

    /**
     * Copies sans code (QCM, quiz) : quelques millisecondes chacune, elles n'attendent jamais derrière Judge0.
     * Taille fixe : un ThreadPoolExecutor ne dépasse son « core » qu'une fois sa file pleine (1000 copies),
     * un « max » plus grand ne servirait jamais.
     */
    @Bean(name = "correctionTaskExecutor")
    public Executor correctionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(THREADS_CORRECTION);
        executor.setMaxPoolSize(THREADS_CORRECTION);
        executor.setQueueCapacity(CAPACITE_FILE);
        executor.setThreadNamePrefix("CorrectionAsync-");
        executor.initialize();
        return executor;
    }

    /**
     * Copies avec du code (DS-12) : au plus autant de corrections simultanées que de workers Judge0. Les autres
     * attendent ici, avant toute prise en charge : un Judge0 chargé n'est pas pris pour une panne, et l'attente
     * ne consomme pas la durée maximale des exécutions.
     */
    @Bean(name = "correctionJudge0Executor")
    public Executor correctionJudge0Executor(Judge0Proprietes judge0) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(judge0.concurrence());
        executor.setMaxPoolSize(judge0.concurrence());
        executor.setQueueCapacity(CAPACITE_FILE);
        executor.setThreadNamePrefix("CorrectionJudge0-");
        executor.initialize();
        return executor;
    }
}
