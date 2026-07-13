package com.cloudcomment.widgetcontext.application;

import com.cloudcomment.widgetcontext.persistence.WidgetContextRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class WidgetContextRetentionService {

    private final WidgetContextRepository repository;
    private final Clock clock;
    private final Duration retention;

    public WidgetContextRetentionService(
        WidgetContextRepository repository,
        Clock clock,
        @Value("${cloud-comment.widget-context.retention-hours:24}") long retentionHours
    ) {
        this.repository = repository;
        this.clock = clock;
        this.retention = Duration.ofHours(Math.max(1, retentionHours));
    }

    @Scheduled(cron = "${cloud-comment.widget-context.retention-cleanup-cron:0 */15 * * * *}")
    @Transactional
    public void cleanup() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(retention);
        repository.deleteExpiredBootstrapTickets(now);
        repository.deleteExpiredFrameContexts(cutoff);
    }
}
