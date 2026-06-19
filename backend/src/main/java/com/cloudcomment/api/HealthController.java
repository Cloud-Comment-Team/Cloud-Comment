package com.cloudcomment.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
