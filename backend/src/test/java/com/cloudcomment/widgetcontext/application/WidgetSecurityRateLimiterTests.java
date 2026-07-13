package com.cloudcomment.widgetcontext.application;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WidgetSecurityRateLimiterTests {

    private static final Instant NOW = Instant.parse("2026-07-13T09:00:00Z");
    private static final UUID SITE_ID = UUID.fromString("7e87f65a-cd40-4a1f-906e-fd44c7f498b1");
    private static final String ORIGIN = "https://customer.example";
    private static final String REMOTE_ADDRESS = "203.0.113.10";

    @Test
    void enforcesEachOperationCapacityAndRecoversUsingClockRefill() {
        MutableClock clock = new MutableClock(NOW);
        WidgetSecurityRateLimiter limiter = new WidgetSecurityRateLimiter(clock);

        consume(240, () -> limiter.checkBootstrap(SITE_ID, ORIGIN, REMOTE_ADDRESS));
        assertRateLimited(() -> limiter.checkBootstrap(SITE_ID, ORIGIN, REMOTE_ADDRESS));
        clock.advance(Duration.ofMillis(250));
        assertThatCode(() -> limiter.checkBootstrap(SITE_ID, ORIGIN, REMOTE_ADDRESS))
            .doesNotThrowAnyException();

        consume(600, () -> limiter.checkExchange(SITE_ID, "https://widget.example", REMOTE_ADDRESS));
        assertRateLimited(() -> limiter.checkExchange(SITE_ID, "https://widget.example", REMOTE_ADDRESS));
        clock.advance(Duration.ofMillis(100));
        assertThatCode(() -> limiter.checkExchange(SITE_ID, "https://widget.example", REMOTE_ADDRESS))
            .doesNotThrowAnyException();

        consume(8, () -> limiter.checkLogin(SITE_ID, ORIGIN, REMOTE_ADDRESS, "owner@example.com"));
        assertRateLimited(() -> limiter.checkLogin(SITE_ID, ORIGIN, REMOTE_ADDRESS, "owner@example.com"));
        clock.advance(Duration.ofMillis(37_500));
        assertThatCode(() -> limiter.checkLogin(SITE_ID, ORIGIN, REMOTE_ADDRESS, "owner@example.com"))
            .doesNotThrowAnyException();

        consume(5, () -> limiter.checkRegister(SITE_ID, ORIGIN, REMOTE_ADDRESS, "new@example.com"));
        assertRateLimited(() -> limiter.checkRegister(SITE_ID, ORIGIN, REMOTE_ADDRESS, "new@example.com"));
        clock.advance(Duration.ofMinutes(12));
        assertThatCode(() -> limiter.checkRegister(SITE_ID, ORIGIN, REMOTE_ADDRESS, "new@example.com"))
            .doesNotThrowAnyException();
    }

    @Test
    void normalizesAuthEmailWhileKeepingSubjectsAndOperationsIsolated() {
        WidgetSecurityRateLimiter limiter = new WidgetSecurityRateLimiter(new MutableClock(NOW));

        consume(8, () -> limiter.checkLogin(
            SITE_ID,
            ORIGIN,
            REMOTE_ADDRESS,
            "  Owner@Example.COM  "
        ));

        assertRateLimited(() -> limiter.checkLogin(
            SITE_ID,
            ORIGIN,
            REMOTE_ADDRESS,
            "owner@example.com"
        ));
        assertRateLimited(() -> limiter.checkLogin(
            SITE_ID,
            ORIGIN,
            REMOTE_ADDRESS,
            "another@example.com"
        ));
        assertThatCode(() -> limiter.checkRegister(
            SITE_ID,
            ORIGIN,
            REMOTE_ADDRESS,
            "owner@example.com"
        )).doesNotThrowAnyException();

        consume(240, () -> limiter.checkBootstrap(
            SITE_ID,
            "HTTPS://CUSTOMER.EXAMPLE:443",
            REMOTE_ADDRESS
        ));
        assertRateLimited(() -> limiter.checkBootstrap(SITE_ID, ORIGIN, REMOTE_ADDRESS));
    }

    @Test
    void globalIpBucketCannotBeBypassedWithRotatingSitesAndOrigins() {
        WidgetSecurityRateLimiter limiter = new WidgetSecurityRateLimiter(new MutableClock(NOW));

        for (int index = 0; index < 240; index++) {
            limiter.checkBootstrap(
                UUID.randomUUID(),
                "https://customer-" + index + ".example",
                REMOTE_ADDRESS
            );
        }

        assertRateLimited(() -> limiter.checkBootstrap(
            UUID.randomUUID(),
            "https://another-customer.example",
            REMOTE_ADDRESS
        ));
    }

    @Test
    void saturationFailsClosedWithoutResettingExistingSecurityState() {
        WidgetSecurityRateLimiter limiter = new WidgetSecurityRateLimiter(new MutableClock(NOW), 8);

        consume(7, () -> limiter.checkLogin(SITE_ID, ORIGIN, REMOTE_ADDRESS, "owner@example.com"));
        for (int index = 0; index < 3; index++) {
            limiter.checkBootstrap(SITE_ID, ORIGIN, "198.51.100." + index);
        }
        assertRateLimited(() -> limiter.checkBootstrap(SITE_ID, ORIGIN, "198.51.100.99"));

        assertThatCode(() -> limiter.checkLogin(SITE_ID, ORIGIN, REMOTE_ADDRESS, "owner@example.com"))
            .doesNotThrowAnyException();
        assertRateLimited(() -> limiter.checkLogin(SITE_ID, ORIGIN, REMOTE_ADDRESS, "owner@example.com"));
    }

    @Test
    void concurrentUniqueKeysNeverGrowBucketStoragePastConfiguredMaximum() throws Exception {
        WidgetSecurityRateLimiter limiter = new WidgetSecurityRateLimiter(new MutableClock(NOW));
        int requestCount = WidgetSecurityRateLimiter.MAX_BUCKETS + 2_000;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>(requestCount);

        try {
            for (int index = 0; index < requestCount; index++) {
                int requestIndex = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        limiter.checkBootstrap(
                            SITE_ID,
                            ORIGIN,
                            "198.51.100." + requestIndex
                        );
                        return true;
                    } catch (ApplicationException exception) {
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.RATE_LIMITED);
                        return false;
                    }
                }));
            }

            start.countDown();
            long accepted = 0;
            for (Future<Boolean> future : futures) {
                accepted += future.get() ? 1 : 0;
            }
            assertThat(accepted).isPositive().isLessThan(requestCount);
        } finally {
            executor.shutdownNow();
        }

        assertThat(limiter.bucketCount())
            .isPositive()
            .isLessThanOrEqualTo(WidgetSecurityRateLimiter.MAX_BUCKETS);
    }

    private void consume(int amount, Runnable request) {
        for (int index = 0; index < amount; index++) {
            request.run();
        }
    }

    private void assertRateLimited(Runnable request) {
        assertThatThrownBy(request::run)
            .isInstanceOfSatisfying(ApplicationException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.RATE_LIMITED)
            );
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now;

        private MutableClock(Instant now) {
            this.now = new AtomicReference<>(now);
        }

        private void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (ZoneOffset.UTC.equals(zone)) {
                return this;
            }
            throw new UnsupportedOperationException("Only UTC is supported in this test clock");
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
