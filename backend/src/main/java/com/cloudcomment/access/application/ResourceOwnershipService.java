package com.cloudcomment.access.application;

import com.cloudcomment.access.domain.OwnedResourceType;
import com.cloudcomment.access.persistence.ResourceOwnershipRepository;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ResourceOwnershipService {

    private final ResourceOwnershipRepository resourceOwnershipRepository;

    public ResourceOwnershipService(ResourceOwnershipRepository resourceOwnershipRepository) {
        this.resourceOwnershipRepository = resourceOwnershipRepository;
    }

    public void assertSiteOwnedBy(AuthenticatedUser currentUser, UUID siteId) {
        assertOwnedBy(currentUser, OwnedResourceType.SITE, siteId);
    }

    public void assertSiteAllowedOriginOwnedBy(AuthenticatedUser currentUser, UUID originId) {
        assertOwnedBy(currentUser, OwnedResourceType.SITE_ALLOWED_ORIGIN, originId);
    }

    public void assertPageOwnedBy(AuthenticatedUser currentUser, UUID pageId) {
        assertOwnedBy(currentUser, OwnedResourceType.PAGE, pageId);
    }

    public void assertCommentOwnedBy(AuthenticatedUser currentUser, UUID commentId) {
        assertOwnedBy(currentUser, OwnedResourceType.COMMENT, commentId);
    }

    public void assertModerationActionOwnedBy(AuthenticatedUser currentUser, UUID moderationActionId) {
        assertOwnedBy(currentUser, OwnedResourceType.MODERATION_ACTION, moderationActionId);
    }

    private void assertOwnedBy(
        AuthenticatedUser currentUser,
        OwnedResourceType resourceType,
        UUID resourceId
    ) {
        if (!resourceOwnershipRepository.isOwnedBy(currentUser.id(), resourceType, resourceId)) {
            throw notFound();
        }
    }

    private ApplicationException notFound() {
        return new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
    }
}
