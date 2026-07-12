package com.cloudcomment.realtime.application;

import com.cloudcomment.auth.application.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeTicketServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Test
    void issuedTicketCanOnlyBeConsumedOnce() {
        RealtimeTicketService service = new RealtimeTicketService(Clock.fixed(NOW, ZoneOffset.UTC));
        AuthenticatedUser user = user();

        RealtimeTicket ticket = service.issue(user);

        assertThat(ticket.value()).isNotBlank();
        assertThat(ticket.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(service.consume(ticket.value())).contains(user);
        assertThat(service.consume(ticket.value())).isEmpty();
    }

    @Test
    void expiredTicketIsRejectedAndConsumed() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(61));
        RealtimeTicketService service = new RealtimeTicketService(clock);

        RealtimeTicket ticket = service.issue(user());

        assertThat(service.consume(ticket.value())).isEmpty();
        assertThat(service.consume(ticket.value())).isEmpty();
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(
            UUID.randomUUID(),
            "owner@example.com",
            Set.of("OWNER"),
            NOW,
            NOW
        );
    }
}
