package com.cloudcomment.shared.web.error;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.shared.web.security.PublicApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class ApiErrorHandlingTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownRouteReturnsUnifiedNotFoundError() throws Exception {
        mockMvc.perform(get("/api/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")))
            .andExpect(jsonPath("$.error.status", is(404)))
            .andExpect(jsonPath("$.error.path", is("/api/missing")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void malformedJsonReturnsUnifiedBadRequestError() throws Exception {
        mockMvc.perform(post("/api/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("MALFORMED_JSON")))
            .andExpect(jsonPath("$.error.message", is("Malformed request body")))
            .andExpect(jsonPath("$.error.status", is(400)))
            .andExpect(jsonPath("$.error.path", is("/api/test/validated")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void validationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.message", is("Request validation failed")))
            .andExpect(jsonPath("$.error.status", is(400)))
            .andExpect(jsonPath("$.error.path", is("/api/test/validated")))
            .andExpect(jsonPath("$.error.fields", not(empty())))
            .andExpect(jsonPath("$.error.fields[0].field", is("name")))
            .andExpect(jsonPath("$.error.fields[0].message", is("must not be blank")))
            .andExpect(jsonPath("$.error.fields[0].code", is("NotBlank")));
    }

    @Test
    void businessExceptionReturnsConfiguredError() throws Exception {
        mockMvc.perform(get("/api/test/business-error"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("BUSINESS_ERROR")))
            .andExpect(jsonPath("$.error.message", is("Business rule failed")))
            .andExpect(jsonPath("$.error.status", is(409)))
            .andExpect(jsonPath("$.error.path", is("/api/test/business-error")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void unexpectedExceptionReturnsSanitizedError() throws Exception {
        mockMvc.perform(get("/api/test/unexpected-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error.code", is("INTERNAL_ERROR")))
            .andExpect(jsonPath("$.error.message", is("Unexpected server error")))
            .andExpect(jsonPath("$.error.status", is(500)))
            .andExpect(jsonPath("$.error.path", is("/api/test/unexpected-error")))
            .andExpect(jsonPath("$.error.fields", empty()))
            .andExpect(jsonPath("$.error.exception").doesNotExist());
    }

    @Test
    void unsupportedMethodReturnsUnifiedError() throws Exception {
        mockMvc.perform(patch("/api/test/validated"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.error.code", is("METHOD_NOT_ALLOWED")))
            .andExpect(jsonPath("$.error.status", is(405)))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void typeMismatchReturnsUnifiedError() throws Exception {
        mockMvc.perform(get("/api/test/typed").param("page", "not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("TYPE_MISMATCH")))
            .andExpect(jsonPath("$.error.message", is("Request parameter type mismatch")))
            .andExpect(jsonPath("$.error.status", is(400)))
            .andExpect(jsonPath("$.error.path", is("/api/test/typed")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void methodValidationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(get("/api/test/validated-param").param("page", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.message", is("Request validation failed")))
            .andExpect(jsonPath("$.error.status", is(400)))
            .andExpect(jsonPath("$.error.path", is("/api/test/validated-param")))
            .andExpect(jsonPath("$.error.fields", not(empty())))
            .andExpect(jsonPath("$.error.fields[0].field", is("page")))
            .andExpect(jsonPath("$.error.fields[0].code", is("Min")));
    }

    @TestConfiguration
    static class TestControllerConfiguration {

        @Bean
        TestApiController testApiController() {
            return new TestApiController();
        }
    }

    @RestController
    @PublicApi
    @RequestMapping("/api/test")
    static class TestApiController {

        @PostMapping("/validated")
        TestRequest validated(@Valid @RequestBody TestRequest request) {
            return request;
        }

        @GetMapping("/business-error")
        void businessError() {
            throw new ApplicationException(ApiErrorCode.BUSINESS_ERROR, "Business rule failed");
        }

        @GetMapping("/unexpected-error")
        void unexpectedError() {
            throw new IllegalStateException("Sensitive internal message");
        }

        @GetMapping("/typed")
        int typed(@RequestParam int page) {
            return page;
        }

        @GetMapping("/validated-param")
        int validatedParam(@RequestParam("page") @Min(1) int page) {
            return page;
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
