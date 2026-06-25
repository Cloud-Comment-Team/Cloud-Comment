package com.cloudcomment.access.persistence;

import com.cloudcomment.access.domain.OwnedResourceType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class JdbcResourceOwnershipRepository implements ResourceOwnershipRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcResourceOwnershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isOwnedBy(UUID ownerId, OwnedResourceType resourceType, UUID resourceId) {
        Boolean exists = jdbcTemplate.queryForObject(
            ownershipQuery(resourceType),
            Boolean.class,
            ownerId,
            resourceId
        );
        return Boolean.TRUE.equals(exists);
    }

    private String ownershipQuery(OwnedResourceType resourceType) {
        return switch (resourceType) {
            case SITE -> """
                select exists(
                    select 1
                    from sites s
                    where s.owner_id = ?
                      and s.id = ?
                )
                """;
            case SITE_ALLOWED_ORIGIN -> """
                select exists(
                    select 1
                    from site_allowed_origins o
                    join sites s on s.id = o.site_id
                    where s.owner_id = ?
                      and o.id = ?
                )
                """;
            case PAGE -> """
                select exists(
                    select 1
                    from pages p
                    join sites s on s.id = p.site_id
                    where s.owner_id = ?
                      and p.id = ?
                )
                """;
            case COMMENT -> """
                select exists(
                    select 1
                    from comments c
                    join pages p on p.id = c.page_id
                    join sites s on s.id = p.site_id
                    where s.owner_id = ?
                      and c.id = ?
                )
                """;
            case MODERATION_ACTION -> """
                select exists(
                    select 1
                    from moderation_actions a
                    join comments c on c.id = a.comment_id
                    join pages p on p.id = c.page_id
                    join sites s on s.id = p.site_id
                    where s.owner_id = ?
                      and a.id = ?
                )
                """;
        };
    }
}
