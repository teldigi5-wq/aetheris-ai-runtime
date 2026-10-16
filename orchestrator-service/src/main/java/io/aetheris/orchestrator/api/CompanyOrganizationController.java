package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.organization.CompanyOrganizationDirectory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public configuration metadata only, mirroring the existing GET agent
 * catalog. No owner ID, task history, secrets, live action or delegation
 * endpoint is introduced. Does not bypass the owner-sensitive API quarantine.
 */
@RestController
public final class CompanyOrganizationController {
    private final CompanyOrganizationDirectory directory;

    public CompanyOrganizationController(CompanyOrganizationDirectory directory) {
        this.directory = directory;
    }

    @GetMapping("/api/orchestrator/company/organization")
    public ResponseEntity<CompanyOrganizationDirectory.OrganizationSnapshot> organization() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(directory.current());
    }
}
