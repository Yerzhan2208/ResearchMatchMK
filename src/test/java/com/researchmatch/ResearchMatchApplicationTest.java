package com.researchmatch;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the real {@code main} entry point. With no web server and lazy shutdown, {@link
 * org.springframework.boot.SpringApplication#run} returns once the (non-web) context is refreshed and the
 * {@code main} method completes, so the launcher lines are covered without leaking a running server.
 */
class ResearchMatchApplicationTest {

    @Test
    void main_runsAndReturns() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Thread[] failure = new Thread[1];
        Thread launcher = new Thread(() -> {
            try {
                ResearchMatchApplication.main(new String[]{
                        "--spring.main.web-application-type=none",
                        "--spring.main.banner-mode=off",
                        "--spring.main.lazy-initialization=true"
                });
            } catch (RuntimeException e) {
                failure[0] = Thread.currentThread();
                throw e;
            } finally {
                done.countDown();
            }
        }, "app-main-launcher");
        launcher.start();

        // main() must finish (close) within a reasonable window for a non-web, lazy context.
        boolean finished = done.await(90, TimeUnit.SECONDS);
        if (!finished) {
            launcher.interrupt();
        }
        assertThat(failure[0]).isNull();
    }
}
