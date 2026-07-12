package com.cloudcomment.automoderation.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.automoderation.application.AutoModerationPolicyService;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyVersion;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.shared.web.security.CurrentUser;
import com.cloudcomment.site.application.SiteService;
import com.cloudcomment.site.domain.ModerationMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/sites/{siteId}/automoderation")
@RequiredArgsConstructor
class AutoModerationPolicyController {

    private final AutoModerationPolicyService service;
    private final SiteService siteService;

    @GetMapping("/policies")
    AutoModerationPoliciesResponse list(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId
    ) {
        return AutoModerationPoliciesResponse.from(service.list(currentUser, siteId));
    }

    @PostMapping("/policies")
    ResponseEntity<AutoModerationPolicyResponse> createDraft(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        @Valid @RequestBody(required = false) CreateAutoModerationPolicyRequest request
    ) {
        CreateAutoModerationPolicyRequest body = request != null
            ? request
            : new CreateAutoModerationPolicyRequest(null, null, null);
        AutoModerationPolicyVersion policy = service.createDraft(
            currentUser, siteId, body.preset(), body.enabled(), body.executionMode()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AutoModerationPolicyResponse.from(policy, null));
    }

    @PatchMapping("/policies/{policyId}")
    AutoModerationPolicyResponse updateDraft(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        @PathVariable UUID policyId,
        @Valid @RequestBody UpdateAutoModerationPolicyRequest request
    ) {
        AutoModerationPolicyVersion policy = service.updateDraft(
            currentUser,
            siteId,
            policyId,
            request.expectedRevision(),
            request.enabled(),
            request.preset(),
            request.executionMode(),
            request.reviewThreshold(),
            request.spamThreshold(),
            request.cleanAction(),
            request.linkAction(),
            request.maxLinks(),
            request.blockedWords()
        );
        return AutoModerationPolicyResponse.from(policy, null);
    }

    @DeleteMapping("/policies/{policyId}")
    ResponseEntity<Void> deleteDraft(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        @PathVariable UUID policyId,
        @RequestParam @Min(1) int expectedRevision
    ) {
        service.deleteDraft(currentUser, siteId, policyId, expectedRevision);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/policies/{policyId}/simulate")
    AutoModerationSimulationResponse simulate(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        @PathVariable UUID policyId,
        @Valid @RequestBody SimulateAutoModerationPolicyRequest request
    ) {
        ModerationMode moderationMode = siteService.getSite(currentUser, siteId).moderationMode();
        CommentStatus baseline = moderationMode == ModerationMode.PRE_MODERATION
            ? CommentStatus.PENDING
            : CommentStatus.APPROVED;
        return AutoModerationSimulationResponse.from(service.simulate(
            currentUser, siteId, policyId, request.content(), baseline
        ));
    }

    @PostMapping("/policies/{policyId}/publish")
    AutoModerationPolicyResponse publish(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        @PathVariable UUID policyId,
        @Valid @RequestBody PublishAutoModerationPolicyRequest request
    ) {
        AutoModerationPolicyVersion policy = service.publish(
            currentUser,
            siteId,
            policyId,
            request.expectedRevision(),
            request.expectedActiveVersionId()
        );
        return AutoModerationPolicyResponse.from(policy, policy.id());
    }

    @PostMapping("/versions/{policyId}/rollback")
    AutoModerationPolicyResponse rollback(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        @PathVariable UUID policyId,
        @Valid @RequestBody RollbackAutoModerationPolicyRequest request
    ) {
        AutoModerationPolicyVersion policy = service.rollback(
            currentUser, siteId, policyId, request.expectedActiveVersionId()
        );
        return AutoModerationPolicyResponse.from(policy, policy.id());
    }
}
