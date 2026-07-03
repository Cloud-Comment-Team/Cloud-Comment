package com.cloudcomment.privacy.api;

import com.cloudcomment.privacy.application.ConsentRequirements;
import com.cloudcomment.privacy.application.ConsentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class PrivacyControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsentService consentService;

    @Test
    void consentRequirementsArePublic() throws Exception {
        when(consentService.currentRequirements()).thenReturn(new ConsentRequirements(
            "2026-07-01",
            "2026-07-01",
            "/legal/privacy-policy.html",
            "/legal/terms.html",
            "/docs/personal-data-notice.md",
            "/docs/personal-data-notice.md#data-export"
        ));

        mockMvc.perform(get("/api/privacy/consent-requirements"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.privacyPolicyVersion", is("2026-07-01")))
            .andExpect(jsonPath("$.termsVersion", is("2026-07-01")))
            .andExpect(jsonPath("$.privacyPolicyUrl", is("/legal/privacy-policy.html")));
    }
}
