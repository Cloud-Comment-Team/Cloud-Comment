package com.cloudcomment.realtime.api;

import com.cloudcomment.realtime.application.RealtimeTicket;

import java.time.Instant;

record RealtimeTicketResponse(
    String ticket,
    Instant expiresAt
) {

    static RealtimeTicketResponse from(RealtimeTicket ticket) {
        return new RealtimeTicketResponse(ticket.value(), ticket.expiresAt());
    }
}
