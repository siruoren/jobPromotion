package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PromotionThreadPool {

    private static final Logger LOGGER = Logger.getLogger(PromotionThreadPool.class.getName());

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 10;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final int QUEUE_CAPACITY = 100;

    private static volatile PromotionThreadPool instance;

    private final ExecutorService executorService;

    private PromotionThreadPool() {
        this.executorService = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                new PromotionThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public static PromotionThreadPool getInstance() {
        if (instance == null) {
            synchronized (PromotionThreadPool.class) {
                if (instance == null) {
                    instance = new PromotionThreadPool();
                }
            }
        }
        return instance;
    }

    public Future<?> submitWithAuth(@NonNull Runnable task) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return executorService.submit(() -> {
            Authentication previousAuth = SecurityContextHolder.getContext().getAuthentication();
            try {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                ACL.impersonate2(authentication);
                task.run();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error executing task with auth context", e);
            } finally {
                SecurityContextHolder.getContext().setAuthentication(previousAuth);
            }
        });
    }

    public <T> Future<T> submitWithAuth(@NonNull Callable<T> task) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return executorService.submit(() -> {
            Authentication previousAuth = SecurityContextHolder.getContext().getAuthentication();
            try {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                ACL.impersonate2(authentication);
                return task.call();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error executing callable task with auth context", e);
                throw e;
            } finally {
                SecurityContextHolder.getContext().setAuthentication(previousAuth);
            }
        });
    }

    public int getActiveCount() {
        if (executorService instanceof ThreadPoolExecutor tpe) {
            return tpe.getActiveCount();
        }
        return 0;
    }

    public int getQueueSize() {
        if (executorService instanceof ThreadPoolExecutor tpe) {
            return tpe.getQueue().size();
        }
        return 0;
    }

    private static class PromotionThreadFactory implements ThreadFactory {
        private int counter = 0;

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread thread = new Thread(r, "job-promotion-worker-" + (counter++));
            thread.setDaemon(true);
            return thread;
        }
    }
}
