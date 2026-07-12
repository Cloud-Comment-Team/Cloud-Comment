package com.cloudcomment.notification.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.notification.application.OwnerNotificationPage;
import com.cloudcomment.notification.application.OwnerNotificationService;
import com.cloudcomment.shared.web.PaginatedResponse;
import com.cloudcomment.shared.web.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
class NotificationController {

    private final OwnerNotificationService service;

    @GetMapping
    PaginatedResponse<NotificationResponse> list(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam(defaultValue = "1") @Min(1) @Max(100_000) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        OwnerNotificationPage notifications = service.list(currentUser, page, pageSize);
        return PaginatedResponse.of(
            notifications.items().stream().map(NotificationResponse::from).toList(),
            notifications.page(),
            notifications.pageSize(),
            notifications.totalItems()
        );
    }

    @GetMapping("/unread-count")
    UnreadNotificationCountResponse unreadCount(@CurrentUser AuthenticatedUser currentUser) {
        return new UnreadNotificationCountResponse(service.unreadCount(currentUser));
    }

    @PatchMapping("/{notificationId}/read")
    NotificationResponse markRead(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID notificationId
    ) {
        return NotificationResponse.from(service.markRead(currentUser, notificationId));
    }

    @PostMapping("/read-all")
    ResponseEntity<Void> markAllRead(@CurrentUser AuthenticatedUser currentUser) {
        service.markAllRead(currentUser);
        return ResponseEntity.noContent().build();
    }
}
