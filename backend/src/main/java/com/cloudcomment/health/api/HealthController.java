package com.cloudcomment.health.api;

import com.cloudcomment.shared.web.security.PublicApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PublicApi
@RestController
@RequestMapping("/api/health")
class HealthController {

    @GetMapping
    HealthResponse health() {
        return new HealthResponse("UP", "cloud-comment");
    }

    record HealthResponse(String status, String application) {
    }
}
