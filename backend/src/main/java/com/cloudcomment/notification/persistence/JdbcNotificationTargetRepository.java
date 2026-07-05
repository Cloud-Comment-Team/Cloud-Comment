package com.cloudcomment.notification.persistence;

import com.cloudcomment.notification.domain.NotificationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcNotificationTargetRepository implements NotificationTargetRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<NotificationTarget> findCommentTarget(UUID siteId, UUID pageId) {
        List<NotificationTarget> targets = jdbcTemplate.query(
            """
                select s.owner_id, s.name, p.url
                from sites s
                join pages p on p.site_id = s.id
                where s.id = ?
                  and p.id = ?
                """,
            (resultSet, rowNumber) -> new NotificationTarget(
                resultSet.getObject("owner_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("url")
            ),
            siteId,
            pageId
        );
        return targets.stream().findFirst();
    }
}
