package com.cloudcomment.realtime.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.realtime.application.RealtimeTicketService;
import com.cloudcomment.shared.web.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
class RealtimeTicketController {

    private final RealtimeTicketService ticketService;

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    RealtimeTicketResponse issueTicket(@CurrentUser AuthenticatedUser currentUser) {
        return RealtimeTicketResponse.from(ticketService.issue(currentUser));
    }
}
