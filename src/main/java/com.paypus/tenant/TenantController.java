package com.paypus.tenant;

import com.paypus.common.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class TenantController {
    private final TenantRepository tenantRepository;
    public TenantController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping("/v1/me")
    public ResponseEntity<TenantResponse> me(){
        UUID tenantId = TenantContext.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Authenticated tenant not found: " + tenantId));

        TenantResponse response = new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }
}
