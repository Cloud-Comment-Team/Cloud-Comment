package com.cloudcomment.automoderation.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.automoderation.application.AutoModerationFeedbackService;
import com.cloudcomment.shared.web.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/moderation/comments/{commentId}/automoderation-feedback")
@RequiredArgsConstructor
class AutoModerationFeedbackController {

    private final AutoModerationFeedbackService service;

    @PutMapping
    AutoModerationFeedbackResponse put(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID commentId,
        @Valid @RequestBody AutoModerationFeedbackRequest request
    ) {
        return AutoModerationFeedbackResponse.from(service.put(currentUser, commentId, request.type()));
    }

    @DeleteMapping
    ResponseEntity<Void> delete(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID commentId
    ) {
        service.delete(currentUser, commentId);
        return ResponseEntity.noContent().build();
    }
}
