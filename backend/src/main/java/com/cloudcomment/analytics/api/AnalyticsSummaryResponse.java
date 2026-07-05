package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.domain.AnalyticsSummary;

record AnalyticsSummaryResponse(
    long sites,
    long pages,
    long comments,
    long replies,
    long reactions,
    long pending,
    long approved,
    long rejected,
    long hidden,
    long spam
) {

    static AnalyticsSummaryResponse from(AnalyticsSummary summary) {
        return new AnalyticsSummaryResponse(
            summary.sites(),
            summary.pages(),
            summary.comments(),
            summary.replies(),
            summary.reactions(),
            summary.pending(),
            summary.approved(),
            summary.rejected(),
            summary.hidden(),
            summary.spam()
        );
    }
}
