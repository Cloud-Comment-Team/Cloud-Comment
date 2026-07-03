package com.cloudcomment.privacy.persistence;

import com.cloudcomment.privacy.domain.ConsentSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
class JdbcUserConsentRepository implements UserConsentRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcUserConsentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void save(
        UUID userId,
        String privacyPolicyVersion,
        String termsVersion,
        ConsentSource source,
        Instant acceptedAt
    ) {
        jdbcTemplate.update(
            """
                insert into user_consents (user_id, privacy_policy_version, terms_version, source, accepted_at)
                values (?, ?, ?, ?, ?)
                """,
            userId,
            privacyPolicyVersion,
            termsVersion,
            source.name(),
            OffsetDateTime.ofInstant(acceptedAt, ZoneOffset.UTC)
        );
    }
}
