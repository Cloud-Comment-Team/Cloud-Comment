package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.application.OwnerAnalyticsService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.shared.web.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
class OwnerAnalyticsController {

    private final OwnerAnalyticsService ownerAnalyticsService;

    @GetMapping("/owner")
    OwnerAnalyticsResponse getOwnerAnalytics(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam(defaultValue = "30d") @Pattern(regexp = "7d|30d|90d|all") String range,
        @RequestParam(required = false) UUID siteId,
        @RequestParam(defaultValue = "UTC") @NotBlank @Size(max = 64) String timeZone
    ) {
        return OwnerAnalyticsResponse.from(
            ownerAnalyticsService.getOwnerAnalytics(currentUser, range, siteId, timeZone)
        );
    }
}
