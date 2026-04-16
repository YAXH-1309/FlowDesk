package com.flowdesk.auth.controller;

import com.flowdesk.auth.rbac.RequiresRole;
import com.flowdesk.auth.rbac.Role;
import com.flowdesk.core.featureflag.FeatureFlagEntry;
import com.flowdesk.core.featureflag.FeatureFlagService;
import com.flowdesk.core.retention.GdprDeletionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final FeatureFlagService featureFlagService;
    private final GdprDeletionService gdprDeletionService;

    public AdminController(FeatureFlagService featureFlagService,
                           GdprDeletionService gdprDeletionService) {
        this.featureFlagService = featureFlagService;
        this.gdprDeletionService = gdprDeletionService;
    }

    @GetMapping("/feature-flags")
    @RequiresRole(Role.ADMIN)
    public List<FeatureFlagEntry> listFlags() {
        return featureFlagService.getAll();
    }

    @GetMapping("/feature-flags/{key}")
    @RequiresRole(Role.ADMIN)
    public ResponseEntity<FeatureFlagEntry> getFlag(@PathVariable String key) {
        return featureFlagService.getAll().stream()
                .filter(f -> f.getFlagKey().equals(key))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/feature-flags/{key}")
    @RequiresRole(Role.ADMIN)
    public FeatureFlagEntry upsertFlag(@PathVariable String key,
                                       @RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        int rollout = body.containsKey("rolloutPercentage")
                ? ((Number) body.get("rolloutPercentage")).intValue() : 0;
        UUID tenantId = body.containsKey("tenantId")
                ? UUID.fromString((String) body.get("tenantId")) : null;
        UUID userId = body.containsKey("userId")
                ? UUID.fromString((String) body.get("userId")) : null;
        return featureFlagService.upsert(key, enabled, tenantId, userId, rollout);
    }

    @PostMapping("/gdpr/delete-request")
    @RequiresRole(Role.ADMIN)
    public ResponseEntity<Void> gdprDelete(@RequestBody Map<String, String> body) {
        String subjectEmail = body.get("subjectEmail");
        gdprDeletionService.processDeleteRequest(subjectEmail);
        return ResponseEntity.accepted().build();
    }
}
