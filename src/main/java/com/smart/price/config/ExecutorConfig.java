package com.smart.price.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pools próprios e limitados, no lugar do ForkJoinPool.commonPool, para que chamadas lentas
 * a APIs externas não disputem threads entre si nem com o resto da JVM.
 */
@Configuration
public class ExecutorConfig {

    @Bean(name = "mercadoLivreExecutor")
    public Executor mercadoLivreExecutor(@Value("${mercadolivre.api.threads:4}") int threads) {
        return criar("ml-", threads, 50);
    }

    @Bean(name = "notificacaoExecutor")
    public Executor notificacaoExecutor() {
        return criar("notificador-", 2, 10);
    }

    private ThreadPoolTaskExecutor criar(String prefixo, int threads, int fila) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefixo);
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(fila);
        // Fila cheia: quem chamou executa a tarefa, em vez de descartar
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
