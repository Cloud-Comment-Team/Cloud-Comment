package com.cloudcomment.site.persistence;

import com.cloudcomment.site.application.SitePage;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.Site;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JdbcSiteRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SiteRepository siteRepository;

    @Test
    void createListUpdateAndReplaceOriginsStayOwnerScoped() {
        UUID ownerId = insertUser("owner");
        UUID otherUserId = insertUser("other");
        Site createdSite = siteRepository.create(
            ownerId,
            "Example site",
            "example.com",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ModerationMode.PRE_MODERATION,
            List.of("https://example.com", "https://admin.example.com")
        );

        assertThat(createdSite.id()).isNotNull();
        assertThat(createdSite.ownerId()).isEqualTo(ownerId);
        assertThat(createdSite.domain()).isEqualTo("example.com");
        assertThat(createdSite.allowedOrigins()).containsExactly("https://admin.example.com", "https://example.com");
        assertThat(siteRepository.existsByOwnerIdAndDomain(ownerId, "example.com")).isTrue();
        assertThat(siteRepository.existsByOwnerIdAndDomain(otherUserId, "example.com")).isFalse();

        SitePage ownerSites = siteRepository.findByOwnerId(ownerId, 1, 20);
        assertThat(ownerSites.items()).extracting(Site::id).containsExactly(createdSite.id());
        assertThat(ownerSites.totalItems()).isEqualTo(1);
        assertThat(siteRepository.findByOwnerId(otherUserId, 1, 20).items()).isEmpty();

        Site updatedSite = siteRepository.update(
            createdSite.id(),
            new SiteUpdate("Updated site", "updated.example.com", ModerationMode.POST_MODERATION, false)
        ).orElseThrow();
        assertThat(updatedSite.name()).isEqualTo("Updated site");
        assertThat(updatedSite.domain()).isEqualTo("updated.example.com");
        assertThat(updatedSite.moderationMode()).isEqualTo(ModerationMode.POST_MODERATION);
        assertThat(updatedSite.active()).isFalse();

        Site originsReplaced = siteRepository.replaceAllowedOrigins(
            createdSite.id(),
            List.of("https://widget.example.com")
        ).orElseThrow();
        assertThat(originsReplaced.allowedOrigins()).containsExactly("https://widget.example.com");
        assertThat(siteRepository.existsByOwnerIdAndDomainExcludingSite(ownerId, "updated.example.com", createdSite.id()))
            .isFalse();
    }

    @Test
    void replaceAllowedOriginsReturnsEmptyForMissingSite() {
        assertThat(siteRepository.replaceAllowedOrigins(
            UUID.randomUUID(),
            List.of("https://missing.example.com")
        )).isEmpty();
    }

    private UUID insertUser(String label) {
        return jdbcTemplate.queryForObject(
            """
                insert into app_users (email, password_hash)
                values (?, ?)
                returning id
                """,
            UUID.class,
            label + "-" + UUID.randomUUID() + "@example.com",
            "hashed-password"
        );
    }
}
