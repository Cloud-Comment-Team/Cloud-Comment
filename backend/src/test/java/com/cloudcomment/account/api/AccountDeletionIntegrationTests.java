package com.cloudcomment.account.api;

import com.cloudcomment.shared.mail.LoggingMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.cloudcomment.privacy.application.ConsentTestSupport.registerRequestJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AccountDeletionIntegrationTests {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("И вставьте одноразовый код:\\s*(\\S+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LoggingMailSender loggingMailSender;

    @Test
    void accountDeletionFlowRevokesSessionsAndBlocksAuth() throws Exception {
        String email = "delete-" + UUID.randomUUID() + "@example.com";
        String password = "strong-password";

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(email, password)))
            .andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String sessionToken = extractJsonField(loginResponse, "token");
        String userId = extractJsonField(loginResponse, "user", "id");

        mockMvc.perform(post("/api/account/deletion-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("PENDING")));

        mockMvc.perform(get("/api/account/deletion-requests/current")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PENDING")));

        String confirmationToken = extractConfirmationToken(loggingMailSender.lastSentMessage().textBody());

        mockMvc.perform(post("/api/account/deletion-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isCreated());

        String rotatedToken = extractConfirmationToken(loggingMailSender.lastSentMessage().textBody());
        assertThat(rotatedToken).isNotEqualTo(confirmationToken);

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "%s"
                    }
                    """.formatted(rotatedToken)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));

        Boolean deleted = jdbcTemplate.queryForObject(
            "select deleted_at is not null from app_users where id = ?",
            Boolean.class,
            UUID.fromString(userId)
        );
        assertThat(deleted).isTrue();

        Integer activeSessions = jdbcTemplate.queryForObject(
            """
                select count(*)
                from auth_sessions
                where user_id = ?
                  and revoked_at is null
                """,
            Integer.class,
            UUID.fromString(userId)
        );
        assertThat(activeSessions).isZero();
    }

    @Test
    void personalDataExportReturnsOwnSnapshotAndWritesAuditEvent() throws Exception {
        String email = "export-" + UUID.randomUUID() + "@example.com";
        String password = "strong-password";

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(email, password)))
            .andExpect(status().isCreated());

        String loginResponse = login(email, password);
        String sessionToken = extractJsonField(loginResponse, "token");
        UUID userId = UUID.fromString(extractJsonField(loginResponse, "user", "id"));

        UUID siteId = insertSite(userId, "export-" + UUID.randomUUID() + ".example.com");
        UUID pageId = insertPage(siteId, "https://export.example.com/page");
        UUID commentId = insertComment(pageId, userId, email, "Personal data comment", "APPROVED");
        insertReaction(commentId, userId, "LOVE");

        String response = mockMvc.perform(get("/api/account/personal-data")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.id", is(userId.toString())))
            .andExpect(jsonPath("$.account.email", is(email)))
            .andExpect(jsonPath("$.sessions.active", is(1)))
            .andExpect(jsonPath("$.resources.ownedSites", is(1)))
            .andExpect(jsonPath("$.resources.ownedPages", is(1)))
            .andExpect(jsonPath("$.resources.ownedComments", is(1)))
            .andExpect(jsonPath("$.resources.authoredComments", is(1)))
            .andExpect(jsonPath("$.resources.commentReactions", is(1)))
            .andExpect(jsonPath("$.consents[0].privacyPolicyVersion", is("2026-07-01")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(response).doesNotContain("password").doesNotContain("token");
        assertThat(countPrivacyEvents(userId, "PERSONAL_DATA_EXPORTED")).isOne();
    }

    @Test
    void accountDeletionAnonymizesRelatedPersonalDataAndWritesAuditEvents() throws Exception {
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
        String deletedEmail = "commenter-" + UUID.randomUUID() + "@example.com";
        String password = "strong-password";

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(ownerEmail, password)))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(deletedEmail, password)))
            .andExpect(status().isCreated());

        UUID ownerId = UUID.fromString(extractJsonField(login(ownerEmail, password), "user", "id"));
        String deletedLoginResponse = login(deletedEmail, password);
        String deletedSessionToken = extractJsonField(deletedLoginResponse, "token");
        UUID deletedUserId = UUID.fromString(extractJsonField(deletedLoginResponse, "user", "id"));

        UUID ownedSiteId = insertSite(deletedUserId, "owned-" + UUID.randomUUID() + ".example.com");
        UUID ownerSiteId = insertSite(ownerId, "foreign-" + UUID.randomUUID() + ".example.com");
        UUID ownerPageId = insertPage(ownerSiteId, "https://foreign.example.com/page");
        UUID foreignCommentId = insertComment(ownerPageId, deletedUserId, deletedEmail, "Remove my data", "APPROVED");
        insertReaction(foreignCommentId, deletedUserId, "WOW");
        UUID moderationActionId = insertModerationAction(foreignCommentId, deletedUserId);

        mockMvc.perform(post("/api/account/deletion-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + deletedSessionToken))
            .andExpect(status().isCreated());
        String confirmationToken = extractConfirmationToken(loggingMailSender.lastSentMessage().textBody());

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "%s"
                    }
                    """.formatted(confirmationToken)))
            .andExpect(status().isNoContent());

        assertThat(countRows("select count(*) from sites where id = ?", ownedSiteId)).isZero();
        assertThat(countRows("select count(*) from sites where id = ?", ownerSiteId)).isOne();

        jdbcTemplate.queryForObject(
            """
                select author_user_id is null,
                       author_email is null,
                       author_name,
                       body
                from comments
                where id = ?
                """,
            (resultSet, rowNumber) -> {
                assertThat(resultSet.getBoolean(1)).isTrue();
                assertThat(resultSet.getBoolean(2)).isTrue();
                assertThat(resultSet.getString(3)).isEqualTo("Deleted user");
                assertThat(resultSet.getString(4)).isEqualTo("Comment deleted by user");
                return true;
            },
            foreignCommentId
        );

        Boolean moderatorCleared = jdbcTemplate.queryForObject(
            "select moderator_id is null from moderation_actions where id = ?",
            Boolean.class,
            moderationActionId
        );
        assertThat(moderatorCleared).isTrue();
        assertThat(countRows("select count(*) from comment_reactions where user_id = ?", deletedUserId)).isZero();
        assertThat(countPrivacyEvents(deletedUserId, "ACCOUNT_DELETION_CONFIRMED")).isOne();
        assertThat(countPrivacyEvents(deletedUserId, "ACCOUNT_DELETED")).isOne();
    }

    @Test
    void createDeletionRequestCancelsExpiredPendingRequestBeforeCreatingNewOne() throws Exception {
        String email = "refresh-expired-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(email, "strong-password")))
            .andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "strong-password"
                    }
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String sessionToken = extractJsonField(loginResponse, "token");
        UUID userId = UUID.fromString(extractJsonField(loginResponse, "user", "id"));

        mockMvc.perform(post("/api/account/deletion-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isCreated());
        String expiredToken = extractConfirmationToken(loggingMailSender.lastSentMessage().textBody());

        jdbcTemplate.update(
            """
                update account_deletion_requests
                set expires_at = now() - interval '1 minute'
                where user_id = ?
                """,
            userId
        );

        mockMvc.perform(post("/api/account/deletion-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("PENDING")));

        String newToken = extractConfirmationToken(loggingMailSender.lastSentMessage().textBody());
        assertThat(newToken).isNotEqualTo(expiredToken);

        Integer cancelledRequests = jdbcTemplate.queryForObject(
            """
                select count(*)
                from account_deletion_requests
                where user_id = ?
                  and cancelled_at is not null
                """,
            Integer.class,
            userId
        );
        assertThat(cancelledRequests).isOne();

        Integer activeRequests = jdbcTemplate.queryForObject(
            """
                select count(*)
                from account_deletion_requests
                where user_id = ?
                  and confirmed_at is null
                  and cancelled_at is null
                  and expires_at > now()
                """,
            Integer.class,
            userId
        );
        assertThat(activeRequests).isOne();
    }

    @Test
    void confirmRejectsExpiredAndReusedTokens() throws Exception {
        String email = "expired-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(email, "strong-password")))
            .andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "strong-password"
                    }
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String sessionToken = extractJsonField(loginResponse, "token");

        mockMvc.perform(post("/api/account/deletion-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isCreated());

        String token = extractConfirmationToken(loggingMailSender.lastSentMessage().textBody());
        jdbcTemplate.update(
            """
                update account_deletion_requests
                set expires_at = now() - interval '1 minute'
                where token_hash is not null
                """
        );

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "%s"
                    }
                    """.formatted(token)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("BUSINESS_ERROR")));

        jdbcTemplate.update(
            """
                update account_deletion_requests
                set expires_at = now() + interval '1 hour',
                    confirmed_at = null
                where token_hash is not null
                """
        );

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "%s"
                    }
                    """.formatted(token)))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "%s"
                    }
                    """.formatted(token)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("BUSINESS_ERROR")));

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "unknown-token-value"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    private String extractConfirmationToken(String body) {
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private UUID insertSite(UUID ownerId, String domain) {
        return jdbcTemplate.queryForObject(
            """
                insert into sites (owner_id, name, domain, public_key, moderation_mode, is_active)
                values (?, ?, ?, ?, 'PRE_MODERATION', true)
                returning id
                """,
            UUID.class,
            ownerId,
            "Privacy Test Site",
            domain,
            publicKey()
        );
    }

    private UUID insertPage(UUID siteId, String pageUrl) {
        return jdbcTemplate.queryForObject(
            """
                insert into pages (site_id, url)
                values (?, ?)
                returning id
                """,
            UUID.class,
            siteId,
            pageUrl
        );
    }

    private UUID insertComment(UUID pageId, UUID authorUserId, String authorEmail, String body, String status) {
        return jdbcTemplate.queryForObject(
            """
                insert into comments (page_id, author_user_id, author_email, author_name, body, status)
                values (?, ?, ?, ?, ?, ?)
                returning id
                """,
            UUID.class,
            pageId,
            authorUserId,
            authorEmail,
            authorEmail,
            body,
            status
        );
    }

    private UUID insertModerationAction(UUID commentId, UUID moderatorId) {
        return jdbcTemplate.queryForObject(
            """
                insert into moderation_actions (comment_id, moderator_id, action, from_status, to_status)
                values (?, ?, 'APPROVE', 'PENDING', 'APPROVED')
                returning id
                """,
            UUID.class,
            commentId,
            moderatorId
        );
    }

    private void insertReaction(UUID commentId, UUID userId, String reactionType) {
        jdbcTemplate.update(
            """
                insert into comment_reactions (comment_id, user_id, reaction_type)
                values (?, ?, ?)
                """,
            commentId,
            userId,
            reactionType
        );
    }

    private int countRows(String sql, UUID id) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count == null ? 0 : count;
    }

    private int countPrivacyEvents(UUID userId, String eventType) {
        return jdbcTemplate.queryForObject(
            "select count(*) from privacy_events where user_id = ? and event_type = ?",
            Integer.class,
            userId,
            eventType
        );
    }

    private String publicKey() {
        return (UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "")).substring(0, 64);
    }

    private String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String extractJsonField(String json, String objectName, String field) {
        Pattern pattern = Pattern.compile("\"" + objectName + "\":\\{\"id\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
