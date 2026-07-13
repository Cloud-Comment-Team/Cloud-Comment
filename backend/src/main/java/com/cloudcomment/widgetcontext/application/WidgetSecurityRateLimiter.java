package com.cloudcomment.widgetcontext.application;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.SiteInputRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class WidgetSecurityRateLimiter {

    static final int MAX_BUCKETS = 10_000;
    private static final long CLEANUP_INTERVAL = 256;
    private static final Duration IDLE_TTL = Duration.ofHours(2);

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();
    private final Object bucketCreationLock = new Object();
    private final Clock clock;
    private final int maximumBuckets;

    @Autowired
    public WidgetSecurityRateLimiter(Clock clock) {
        this(clock, MAX_BUCKETS);
    }

    WidgetSecurityRateLimiter(Clock clock, int maximumBuckets) {
        this.clock = clock;
        if (maximumBuckets < 2) {
            throw new IllegalArgumentException("Rate-limit bucket capacity must be at least two");
        }
        this.maximumBuckets = maximumBuckets;
    }

    public void checkBootstrap(UUID siteId, String origin, String remoteAddress) {
        check(Operation.BOOTSTRAP, siteId, origin, remoteAddress, "");
    }

    public void checkExchange(UUID siteId, String frameOrigin, String remoteAddress) {
        check(Operation.EXCHANGE, siteId, frameOrigin, remoteAddress, "");
    }

    public void checkLogin(UUID siteId, String origin, String remoteAddress, String email) {
        check(Operation.LOGIN, siteId, origin, remoteAddress, normalizeEmail(email));
    }

    public void checkRegister(UUID siteId, String origin, String remoteAddress, String email) {
        check(Operation.REGISTER, siteId, origin, remoteAddress, normalizeEmail(email));
    }

    int bucketCount() {
        return buckets.size();
    }

    private void check(
        Operation operation,
        UUID siteId,
        String origin,
        String remoteAddress,
        String subject
    ) {
        Instant now = clock.instant();
        if (requests.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanup(now);
        }
        consumeBucket(hashGlobalKey(operation, remoteAddress), operation, now);
        consumeBucket(hashScopedKey(operation, siteId, origin, remoteAddress, subject), operation, now);
    }

    private void consumeBucket(String key, Operation operation, Instant now) {
        TokenBucket bucket = buckets.get(key);
        if (bucket == null) {
            synchronized (bucketCreationLock) {
                bucket = buckets.get(key);
                if (bucket == null) {
                    requireCapacity(now);
                    bucket = new TokenBucket(operation.capacity, now);
                    buckets.put(key, bucket);
                }
            }
        }
        if (!bucket.tryConsume(operation, now)) {
            throw new ApplicationException(ApiErrorCode.RATE_LIMITED, "Too many requests");
        }
    }

    private void cleanup(Instant now) {
        Instant threshold = now.minus(IDLE_TTL);
        buckets.entrySet().removeIf(entry -> entry.getValue().lastAccess().isBefore(threshold));
    }

    private void requireCapacity(Instant now) {
        if (buckets.size() < maximumBuckets) {
            return;
        }
        cleanup(now);
        if (buckets.size() < maximumBuckets) {
            return;
        }
        throw rateLimited();
    }

    private String hashGlobalKey(Operation operation, String remoteAddress) {
        return digest("GLOBAL\n" + operation.name() + "\n" + safe(remoteAddress));
    }

    private String hashScopedKey(
        Operation operation,
        UUID siteId,
        String origin,
        String remoteAddress,
        String subject
    ) {
        String material = "SCOPED\n" + operation.name() + "\n" + siteId + "\n"
            + normalizeOrigin(origin) + "\n" + safe(remoteAddress) + "\n" + safe(subject);
        return digest(material);
    }

    private String digest(String material) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOrigin(String origin) {
        if ("null".equals(origin)) {
            return "null";
        }
        return SiteInputRules.normalizeOrigin(origin).orElse("invalid");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private ApplicationException rateLimited() {
        return new ApplicationException(ApiErrorCode.RATE_LIMITED, "Too many requests");
    }

    private enum Operation {
        BOOTSTRAP(240, 240.0 / 60.0),
        EXCHANGE(600, 600.0 / 60.0),
        LOGIN(8, 8.0 / 300.0),
        REGISTER(5, 5.0 / 3600.0);

        private final int capacity;
        private final double tokensPerSecond;

        Operation(int capacity, double tokensPerSecond) {
            this.capacity = capacity;
            this.tokensPerSecond = tokensPerSecond;
        }
    }

    private static final class TokenBucket {

        private double tokens;
        private Instant lastRefill;
        private volatile Instant lastAccess;

        private TokenBucket(double tokens, Instant now) {
            this.tokens = tokens;
            this.lastRefill = now;
            this.lastAccess = now;
        }

        private synchronized boolean tryConsume(Operation operation, Instant now) {
            double elapsedSeconds = Math.max(0, Duration.between(lastRefill, now).toMillis() / 1000.0);
            tokens = Math.min(operation.capacity, tokens + elapsedSeconds * operation.tokensPerSecond);
            lastRefill = now;
            lastAccess = now;
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }

        private Instant lastAccess() {
            return lastAccess;
        }
    }
}
