package com.cloudcomment.privacy.api;

import com.cloudcomment.privacy.application.ConsentRequirements;
import com.cloudcomment.privacy.application.ConsentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class PrivacyControllerTests {

    private static final String EXTERNAL_ORIGIN = "https://card.ifbest.org";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsentService consentService;

    @Test
    void consentRequirementsArePublic() throws Exception {
        when(consentService.currentRequirements()).thenReturn(new ConsentRequirements(
            "2026-07-12",
            "2026-07-01",
            "/legal/privacy-policy.html",
            "/legal/terms.html",
            "/legal/personal-data-notice.html",
            "/legal/personal-data-notice.html#data-export"
        ));

        mockMvc.perform(get("/api/privacy/consent-requirements")
                .header(HttpHeaders.ORIGIN, EXTERNAL_ORIGIN))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
            .andExpect(jsonPath("$.privacyPolicyVersion", is("2026-07-12")))
            .andExpect(jsonPath("$.termsVersion", is("2026-07-01")))
            .andExpect(jsonPath("$.privacyPolicyUrl", is("/legal/privacy-policy.html")))
            .andExpect(jsonPath("$.personalDataNoticeUrl", is("/legal/personal-data-notice.html")))
            .andExpect(jsonPath("$.dataExportInfoUrl", is("/legal/personal-data-notice.html#data-export")));
    }

    @Test
    void consentRequirementsAllowCrossOriginPreflightWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/api/privacy/consent-requirements")
                .header(HttpHeaders.ORIGIN, EXTERNAL_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Accept"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET"));
    }
}
