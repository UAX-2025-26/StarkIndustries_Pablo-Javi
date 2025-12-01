package com.starkindustries.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// Configura los ejecutores (pools de hilos) usados por @Async
@Configuration
public class AsyncConfiguration implements AsyncConfigurer {

    // Pool principal para procesar eventos de sensores
    @Bean(name = "sensorExecutor")
    public ThreadPoolTaskExecutor sensorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Número de hilos que se mantienen siempre activos
        executor.setCorePoolSize(20);
        // Máximo de hilos que puede crecer el pool bajo carga
        executor.setMaxPoolSize(50);
        // Cola de tareas pendientes cuando todos los hilos están ocupados
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("SensorProcessor-");
        // Esperar a que terminen las tareas al apagar la aplicación
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    // Pool específico para tareas de alertas (separado del de sensores)
    @Bean(name = "alertExecutor")
    public ThreadPoolTaskExecutor alertExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("AlertProcessor-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    // Pool para tareas de notificaciones (emails, WebSocket, etc.)
    @Bean(name = "notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Notification-");
        executor.initialize();
        return executor;
    }

    // Executor por defecto que usará @Async cuando no se especifique un bean concreto
    @Override
    public Executor getAsyncExecutor() {
        return sensorExecutor();
    }
}
