package com.cloudcomment.widgetcontext.application;

import com.cloudcomment.widgetcontext.persistence.WidgetContextRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WidgetContextRetentionServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-13T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void cleanupDeletesExpiredTicketsImmediatelyAndUsesConfiguredContextCutoff() {
        WidgetContextRepository repository = mock(WidgetContextRepository.class);
        WidgetContextRetentionService service = new WidgetContextRetentionService(repository, CLOCK, 24);

        service.cleanup();

        Instant cutoff = Instant.parse("2026-07-12T09:00:00Z");
        InOrder deletionOrder = inOrder(repository);
        deletionOrder.verify(repository).deleteExpiredBootstrapTickets(NOW);
        deletionOrder.verify(repository).deleteExpiredFrameContexts(cutoff);
        deletionOrder.verifyNoMoreInteractions();
    }

    @Test
    void cleanupClampsNonPositiveRetentionToOneHourAndInvokesBothDeletes() {
        WidgetContextRepository repository = mock(WidgetContextRepository.class);
        WidgetContextRetentionService service = new WidgetContextRetentionService(repository, CLOCK, 0);

        service.cleanup();

        Instant cutoff = Instant.parse("2026-07-13T08:00:00Z");
        verify(repository).deleteExpiredBootstrapTickets(NOW);
        verify(repository).deleteExpiredFrameContexts(cutoff);
    }
}
