package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.AetherisOrchestratorApplication;
import io.aetheris.orchestrator.api.OwnerlessTaskMissionHttpFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S0-E5: enumerates real production Spring MVC business-handler mappings
 * from the ACTUAL AetherisOrchestratorApplication, with disposable H2 only.
 *
 * REVIEW MATRIX ONLY: labels are conservative prerequisites, not granted
 * rights. Every current route remains DENY_404 under the REAL S0-E4 filter.
 * This test never registers a route, server, security bypass or unlock.
 */
@SpringBootTest(
    classes = AetherisOrchestratorApplication.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:s0_e5_policy_inventory;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.main.web-application-type=servlet",
        "spring.task.scheduling.enabled=false",
        "aetheris.dispatcher.enabled=false",
        "aetheris.syntra.owner-runtime.enabled=false",
        "aetheris.cloud-provider.enabled=false",
        "aetheris.execution.workspace-write-enabled=false",
        "aetheris.execution.allowed-commands=",
        "aetheris.execution.github.allowed-repositories=",
        "aetheris.host.simulation-only=true"
    })
class RuntimeControllerPolicyInventoryTest {
    private static final String DENIED = "DENY_404";
    private static final String HEADER =
        "method\tpattern\tcontroller\tjava_method\trisk_group\tcurrent_boundary\trelease_prerequisites\n";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlers;

    @Autowired
    org.springframework.context.ApplicationContext application;

    record Entry(String method, String pattern, String controller, String handler,
                 String category, String releaseRequirements) {
        String serialize() {
            return String.join("\t", clean(method), clean(pattern), clean(controller),
                clean(handler), clean(category), DENIED, clean(releaseRequirements)) + "\n";
        }
    }

    static String clean(String value) {
        // No spreadsheet-formula injection, tab, CR or line break in evidence.
        String oneLine = value.replace('\t',' ').replace('\r',' ').replace('\n',' ');
        return oneLine.matches("^[=+@-].*") ? "'" + oneLine : oneLine;
    }

    static boolean contained(String path) {
        return RuntimeUncertifiedApiHttpFilter.quarantinedPath(path);
    }

    static String classify(String path) {
        if (!contained(path)) return "UNCLASSIFIED_STOP";
        if (path.equals("/api/orchestrator/tasks") || path.startsWith("/api/orchestrator/tasks/")
                || path.equals("/api/orchestrator/missions")
                || path.startsWith("/api/orchestrator/missions/")) {
            return "TASK_MISSION_OWNER_BOUNDARY";
        }
        if (path.equals("/api/orchestrator/live")
                || path.startsWith("/api/orchestrator/live/")
                || path.contains("/events")) {
            return "LIVE_EVENT_AND_REPLAY";
        }
        if (path.contains("/connectors") || path.contains("/mcp")
                || path.contains("/oauth") || path.contains("/webhook")) {
            return "EXTERNAL_CREDENTIAL_AND_CONNECTOR";
        }
        if (path.contains("/tools") || path.contains("/execution")
                || path.contains("/models") || path.contains("/hosts")
                || path.contains("/stage") || path.contains("/trading")) {
            return "EXECUTION_AND_CONTROL";
        }
        if (path.equals("/actuator") || path.startsWith("/actuator/")) {
            return "OPERATOR_MONITORING";
        }
        return "OTHER_UNCERTIFIED_API";
    }

    static String releaseRequirements(String category) {
        return switch (category) {
            case "TASK_MISSION_OWNER_BOUNDARY" ->
                "signed Identity and session, tenant/project membership, durable owner FK, two-user negatives, scoped SSE";
            case "LIVE_EVENT_AND_REPLAY" ->
                "signed Identity and session, event owner/tenant scope, bounded replay, multi-instance subscriber isolation";
            case "EXTERNAL_CREDENTIAL_AND_CONNECTOR" ->
                "signed caller and workload, provider credential ACL, capability check, owner approval, audit and rollback";
            case "EXECUTION_AND_CONTROL" ->
                "signed caller and workload, least privilege, owner approval, quota policy, action audit and cancellation";
            case "OPERATOR_MONITORING" ->
                "private health design, operator trust, no public metrics, independent deployment probe";
            case "OTHER_UNCERTIFIED_API" ->
                "named owner and reviewer, signed caller, scope/role policy, data authorization, negative access tests";
            default -> throw new IllegalArgumentException("Unreviewed route classification");
        };
    }

    private List<Entry> snapshot() {
        var out = new ArrayList<Entry>();
        for (Map.Entry<RequestMappingInfo,
                org.springframework.web.method.HandlerMethod> item
                : handlers.getHandlerMethods().entrySet()) {
            String declaringClass = item.getValue().getBeanType().getName();
            if (declaringClass.startsWith(
                    "org.springframework.boot.autoconfigure.web.servlet.error.")) {
                continue; // internal framework exception mapping; audit separately
            }
            Set<String> patterns;
            if (item.getKey().getPathPatternsCondition() != null) {
                patterns = item.getKey().getPathPatternsCondition().getPatternValues();
            } else {
                assertNotNull(item.getKey().getPatternsCondition());
                patterns = item.getKey().getPatternsCondition().getPatterns();
            }
            Set<String> methods = new TreeSet<>();
            for (RequestMethod method : item.getKey().getMethodsCondition().getMethods()) {
                methods.add(method.name());
            }
            if (methods.isEmpty()) methods.add("ANY");
            for (String pattern : patterns) {
                String category = classify(pattern);
                for (String method : methods) {
                    out.add(new Entry(method, pattern,
                        declaringClass, item.getValue().getMethod().getName(),
                        category,
                        "UNCLASSIFIED_STOP".equals(category) ? "" : releaseRequirements(category)));
                }
            }
        }
        out.sort(Comparator.comparing(Entry::pattern).thenComparing(Entry::method)
            .thenComparing(Entry::controller).thenComparing(Entry::handler));
        return out;
    }

    @Test
    void everyMappedBusinessHandlerMustBeInTheDenyFirstMatrix() throws Exception {
        var inventory = snapshot();
        assertTrue(inventory.size() >= 70,
            "Expected actual comprehensive Runtime MVC mapping, not minimal test context");
        var unclassified = inventory.stream()
            .filter(x -> "UNCLASSIFIED_STOP".equals(x.category())).toList();
        assertTrue(unclassified.isEmpty(),
            "Uncovered mapped business routes: " + unclassified);
        for (Entry entry : inventory) {
            assertTrue(contained(entry.pattern()), entry.pattern());
            assertFalse(entry.releaseRequirements().isBlank(), entry.pattern());
            assertFalse(entry.category().isBlank());
        }
        StringBuilder output = new StringBuilder(HEADER);
        for (Entry entry : inventory) output.append(entry.serialize());
        Path directory = Path.of("target", "s0-e5-route-audit");
        Files.createDirectories(directory);
        Path artifact = directory.resolve("runtime-handler-policy-matrix.tsv");
        Files.writeString(artifact, output.toString(), StandardCharsets.UTF_8);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
            output.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("AETHERIS_S0_E5_HANDLER_COUNT=" + inventory.size());
        System.out.println("AETHERIS_S0_E5_MATRIX_SHA256=" + HexFormat.of().formatHex(digest));
        System.out.println("AETHERIS_S0_E5_ALL_ACTUAL_MVC_ROUTES_DENY_404_WITH_OWNER_PREREQUISITES_PASS");
    }

    @Test
    void representativeHighRiskRoutesRequireDistinctIndependentSignOffs() {
        var inventory = snapshot();
        var categories = new LinkedHashSet<String>();
        for (Entry entry : inventory) categories.add(entry.category());
        assertTrue(categories.contains("TASK_MISSION_OWNER_BOUNDARY"));
        assertTrue(categories.contains("LIVE_EVENT_AND_REPLAY"));
        assertTrue(categories.contains("EXTERNAL_CREDENTIAL_AND_CONNECTOR"));
        assertTrue(categories.contains("EXECUTION_AND_CONTROL"));
        assertTrue(categories.contains("OTHER_UNCERTIFIED_API"));

        assertEquals("TASK_MISSION_OWNER_BOUNDARY",
            classify("/api/orchestrator/tasks/{id}/events/history"));
        assertEquals("LIVE_EVENT_AND_REPLAY",
            classify("/api/orchestrator/live/events"));
        assertEquals("EXTERNAL_CREDENTIAL_AND_CONNECTOR",
            classify("/api/orchestrator/connectors/connections/{id}"));
        assertEquals("EXECUTION_AND_CONTROL",
            classify("/api/orchestrator/models/execute"));
        assertEquals("OPERATOR_MONITORING",classify("/actuator/health"));
        assertEquals("UNCLASSIFIED_STOP",classify("/unreviewed-business-root"));
        assertEquals("UNCLASSIFIED_STOP",classify("/apiary/undetected"));
    }

    @Test
    void normalProductionFiltersRemainUnconditionalWithoutTestOnlyGateway() {
        var task = application.getBean(OwnerlessTaskMissionHttpFilter.class);
        var general = application.getBean(RuntimeUncertifiedApiHttpFilter.class);
        assertNotNull(task);
        assertNotNull(general);
        assertEquals(Ordered.HIGHEST_PRECEDENCE,
            task.getClass().getAnnotation(
                org.springframework.core.annotation.Order.class).value());
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 1,
            general.getClass().getAnnotation(
                org.springframework.core.annotation.Order.class).value());
        assertFalse(application.containsBean("onlyTestSyntheticGate"));
        assertFalse(application.containsBean("testOnlyPinnedBearerSecond"));
    }
}
