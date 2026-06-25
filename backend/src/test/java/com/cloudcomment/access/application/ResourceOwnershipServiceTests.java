package com.cloudcomment.access.application;

import com.cloudcomment.access.domain.OwnedResourceType;
import com.cloudcomment.access.persistence.ResourceOwnershipRepository;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceOwnershipServiceTests {

    private final ResourceOwnershipRepository resourceOwnershipRepository = mock(ResourceOwnershipRepository.class);
    private final ResourceOwnershipService resourceOwnershipService = new ResourceOwnershipService(resourceOwnershipRepository);

    @Test
    void ownedSiteDoesNotThrowAndChecksCurrentUserId() {
        UUID resourceId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        when(resourceOwnershipRepository.isOwnedBy(currentUser.id(), OwnedResourceType.SITE, resourceId))
            .thenReturn(true);

        assertThatCode(() -> resourceOwnershipService.assertSiteOwnedBy(currentUser, resourceId))
            .doesNotThrowAnyException();

        verify(resourceOwnershipRepository).isOwnedBy(currentUser.id(), OwnedResourceType.SITE, resourceId);
    }

    @Test
    void ownedSiteAllowedOriginChecksCurrentUserIdAndResourceType() {
        UUID resourceId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        when(resourceOwnershipRepository.isOwnedBy(currentUser.id(), OwnedResourceType.SITE_ALLOWED_ORIGIN, resourceId))
            .thenReturn(true);

        resourceOwnershipService.assertSiteAllowedOriginOwnedBy(currentUser, resourceId);

        verify(resourceOwnershipRepository).isOwnedBy(
            currentUser.id(),
            OwnedResourceType.SITE_ALLOWED_ORIGIN,
            resourceId
        );
    }

    @Test
    void ownedPageChecksCurrentUserIdAndResourceType() {
        UUID resourceId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        when(resourceOwnershipRepository.isOwnedBy(currentUser.id(), OwnedResourceType.PAGE, resourceId))
            .thenReturn(true);

        resourceOwnershipService.assertPageOwnedBy(currentUser, resourceId);

        verify(resourceOwnershipRepository).isOwnedBy(currentUser.id(), OwnedResourceType.PAGE, resourceId);
    }

    @Test
    void ownedCommentChecksCurrentUserIdAndResourceType() {
        UUID resourceId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        when(resourceOwnershipRepository.isOwnedBy(currentUser.id(), OwnedResourceType.COMMENT, resourceId))
            .thenReturn(true);

        resourceOwnershipService.assertCommentOwnedBy(currentUser, resourceId);

        verify(resourceOwnershipRepository).isOwnedBy(currentUser.id(), OwnedResourceType.COMMENT, resourceId);
    }

    @Test
    void ownedModerationActionChecksCurrentUserIdAndResourceType() {
        UUID resourceId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        when(resourceOwnershipRepository.isOwnedBy(currentUser.id(), OwnedResourceType.MODERATION_ACTION, resourceId))
            .thenReturn(true);

        resourceOwnershipService.assertModerationActionOwnedBy(currentUser, resourceId);

        verify(resourceOwnershipRepository).isOwnedBy(
            currentUser.id(),
            OwnedResourceType.MODERATION_ACTION,
            resourceId
        );
    }

    @Test
    void foreignOrMissingResourceThrowsNotFound() {
        UUID resourceId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();

        assertThatThrownBy(() -> resourceOwnershipService.assertSiteOwnedBy(currentUser, resourceId))
            .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                assertThat(exception.code()).isEqualTo(ApiErrorCode.NOT_FOUND);
                assertThat(exception).hasMessage("Resource not found");
            });

        verify(resourceOwnershipRepository).isOwnedBy(currentUser.id(), OwnedResourceType.SITE, resourceId);
    }

    private AuthenticatedUser currentUser() {
        UUID userId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-06-25T00:00:00Z");
        return new AuthenticatedUser(userId, "owner@example.com", Set.of("OWNER"), timestamp, timestamp);
    }
}
