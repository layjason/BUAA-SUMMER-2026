package io.github.layjason.mayoistar.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务配置。
 *
 * <p>类职责：启用 Spring 异步支持并为邮件发送提供专用线程池。
 *
 * <p>不变量：线程池配置不得在运行时动态调整，邮件发送失败不影响调用方。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 邮件发送专用线程池。
     *
     * <p>后置条件：返回一个已配置的 Executor，核心线程 2，最大线程 5，队列容量 50。
     * 队列满时退化为同步执行（CallerRunsPolicy），确保邮件不丢失。
     *
     * @return 邮件发送线程池
     */
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
