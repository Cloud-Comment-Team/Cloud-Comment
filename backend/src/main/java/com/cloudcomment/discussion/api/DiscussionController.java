package com.cloudcomment.discussion.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.discussion.application.DiscussionFilters;
import com.cloudcomment.discussion.application.DiscussionPage;
import com.cloudcomment.discussion.application.DiscussionService;
import com.cloudcomment.discussion.domain.DiscussionFilter;
import com.cloudcomment.shared.web.PaginatedResponse;
import com.cloudcomment.shared.web.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/discussions")
@RequiredArgsConstructor
class DiscussionController {

    private final DiscussionService discussionService;

    @GetMapping
    PaginatedResponse<DiscussionSummaryResponse> list(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam(required = false) UUID siteId,
        @RequestParam(defaultValue = "ALL") DiscussionFilter view,
        @RequestParam(required = false) @Size(max = 120) String search,
        @RequestParam(defaultValue = "1") @Min(1) @Max(100_000) int page,
        @RequestParam(defaultValue = "30") @Min(1) @Max(100) int pageSize
    ) {
        DiscussionPage discussions = discussionService.list(
            currentUser,
            new DiscussionFilters(siteId, view, search),
            page,
            pageSize
        );
        return PaginatedResponse.of(
            discussions.items().stream().map(DiscussionSummaryResponse::from).toList(),
            discussions.page(),
            discussions.pageSize(),
            discussions.totalItems()
        );
    }

    @GetMapping("/{rootCommentId}")
    DiscussionThreadResponse get(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID rootCommentId
    ) {
        return DiscussionThreadResponse.from(discussionService.get(currentUser, rootCommentId));
    }
}
