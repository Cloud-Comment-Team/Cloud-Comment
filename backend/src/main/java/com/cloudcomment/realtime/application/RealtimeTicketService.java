package com.cloudcomment.realtime.application;

import com.cloudcomment.auth.application.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class RealtimeTicketService {

    private static final Duration TICKET_TTL = Duration.ofSeconds(60);
    private static final int TICKET_BYTES = 32;

    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<String, TicketEntry> tickets = new ConcurrentHashMap<>();

    public RealtimeTicket issue(AuthenticatedUser user) {
        Instant now = clock.instant();
        removeExpired(now);

        byte[] randomBytes = new byte[TICKET_BYTES];
        secureRandom.nextBytes(randomBytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant expiresAt = now.plus(TICKET_TTL);
        tickets.put(value, new TicketEntry(user, expiresAt));
        return new RealtimeTicket(value, expiresAt);
    }

    public Optional<AuthenticatedUser> consume(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        TicketEntry entry = tickets.remove(value);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.user());
    }

    private void removeExpired(Instant now) {
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record TicketEntry(AuthenticatedUser user, Instant expiresAt) {
    }
}
