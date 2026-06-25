package com.cloudcomment.access.persistence;

import com.cloudcomment.access.domain.OwnedResourceType;

import java.util.UUID;

public interface ResourceOwnershipRepository {

    boolean isOwnedBy(UUID ownerId, OwnedResourceType resourceType, UUID resourceId);
}
