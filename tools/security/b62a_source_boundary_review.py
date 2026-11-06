#!/usr/bin/env python3
"""S-B62-A: pinned SOURCE evidence only, never production security acceptance."""
import argparse
import json
from pathlib import Path
import re

HOST_PORTS = ("127.0.0.1:8081:8081", "127.0.0.1:8082:8082",
              "127.0.0.1:8083:8083", "127.0.0.1:8080:8080")
FILES = ("docker-compose.yml", "docker-compose.core.yml",
         "docker-compose.integration-external.yml", "docker-compose.override.yml")


def require(value, reason):
    if not value:
        raise ValueError(reason)


def read(root, name):
    path = root / name
    require(path.is_file(), "missing pinned source: " + name)
    return path.read_text(encoding="utf-8")


def published(text):
    require(re.search(r"(?m)^services:\s*$", text), "missing services")
    ports, active = [], False
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if re.fullmatch(r"    ports:", raw):
            active = True
            continue
        if not active:
            continue
        if raw.startswith("      - "):
            value = line[2:].strip().strip('"').strip("'")
            require(bool(re.fullmatch(r"[0-9.]+:[0-9]+:[0-9]+(?:/tcp)?", value)),
                    "unsupported or unbounded published port")
            ports.append(value)
        else:
            active = False
    return ports


def check_compose(name, source):
    ports = published(source)
    if name == "docker-compose.override.yml":
        require(not ports, "override unexpectedly publishes ports")
        return ports
    for expected in HOST_PORTS:
        require(expected in ports, "missing loopback service port: " + expected)
    if name != "docker-compose.core.yml":
        require("127.0.0.1:8090:8090" in ports, "Runtime is not host-loopback bound")
    require(bool(ports) and all(x.startswith("127.0.0.1:") for x in ports),
            "unbounded / unreviewed host network exposure")
    return ports


def check_filter(text):
    for snippet in ("@Component", "@Order(Ordered.HIGHEST_PRECEDENCE + 1)",
                    'segment(path, "/api")', 'segment(path, "/actuator")',
                    "request.getServletPath()", "request.getPathInfo()",
                    "quarantinedPath(relative)",
                    "response.sendError(HttpServletResponse.SC_NOT_FOUND)"):
        require(snippet in text, "Runtime quarantine source guard missing: " + snippet)


def check_defaults(text):
    for key, val in (
        ("aetheris.syntra.owner-runtime.enabled", "false"),
        ("aetheris.execution.workspace-write-enabled", "false"),
        ("aetheris.cloud-provider.enabled", "false"),
        ("aetheris.mcp.allowed-remote-hosts", ""),
    ):
        lines = [s for s in text.splitlines() if s.startswith(key + "=")]
        require(len(lines) == 1 and lines[0].endswith(":" + val + "}"),
                "unsafe or unknown Runtime default: " + key)


def check_helm(safety, policy):
    for token in ("allowInsecureLocalDefaults",
                  "secrets.redisPassword requires 32+",
                  "unsafe Helm image reference:",
                  "unsafe Helm default: replace secrets.jwtSecret",
                  "unsafe Helm default: replace secrets.postgresPassword"):
        require(token in safety, "Helm safety template guard missing: " + token)
    require('policyTypes: ["Ingress"]' in policy, "Helm ingress policy missing")
    require("if not .Values.global.allowInsecureLocalDefaults" in policy,
            "Helm nonlocal guard missing")
    for service in ("user-service", "identity-service", "audit-service"):
        pattern = r'\(dict "name" "' + re.escape(service) + r'" "port" \d+ "clients" \(list "gateway"\)\)'
        require(bool(re.search(pattern, policy)),
                "Helm backend is not gateway-only in source: " + service)


def negative_trials(compose, runtime_filter, defaults, safety, network):
    def rejects(call):
        try:
            call()
        except ValueError:
            return
        raise AssertionError("a deliberately unsafe source change was accepted")
    public = compose.replace('"127.0.0.1:8081:8081"',
                             '"0.0.0.0:8081:8081"', 1)
    require(public != compose, "missing real Compose mutation target")
    rejects(lambda: check_compose("docker-compose.yml", public))
    implicit = compose.replace('"127.0.0.1:8081:8081"', '"8081:8081"', 1)
    rejects(lambda: check_compose("docker-compose.yml", implicit))
    long_form = compose.replace('      - "127.0.0.1:8081:8081"',
                                "      - target: 8081", 1)
    rejects(lambda: check_compose("docker-compose.yml", long_form))
    denial = runtime_filter.replace(
        "response.sendError(HttpServletResponse.SC_NOT_FOUND)", "return", 1)
    rejects(lambda: check_filter(denial))
    path_info = runtime_filter.replace("request.getPathInfo()", "null", 1)
    rejects(lambda: check_filter(path_info))
    writable = defaults.replace("AETHERIS_WORKSPACE_WRITE_ENABLED:false",
                                "AETHERIS_WORKSPACE_WRITE_ENABLED:true", 1)
    rejects(lambda: check_defaults(writable))
    wildcard = network.replace('"clients" (list "gateway")',
                               '"clients" (list "gateway" "any-pod")', 1)
    rejects(lambda: check_helm(safety, wildcard))
    no_guard = safety.replace("unsafe Helm image reference:", "allow image", 1)
    rejects(lambda: check_helm(no_guard, network))
    return 8


def inventory(runtime, platform):
    checks = {}
    for name in FILES:
        checks[name] = len(check_compose(name, read(platform, name)))
    filt = read(runtime, "orchestrator-service/src/main/java/io/aetheris/"
                         "orchestrator/security/RuntimeUncertifiedApiHttpFilter.java")
    conf = read(runtime, "orchestrator-service/src/main/resources/application.properties")
    safety = read(platform, "deploy/helm/aetheris/templates/00-safety-gate.yaml")
    network = read(platform, "deploy/helm/aetheris/templates/10-ingress-network-policies.yaml")
    check_filter(filt)
    check_defaults(conf)
    check_helm(safety, network)
    checks["source_negative_trials"] = negative_trials(
        read(platform, "docker-compose.yml"), filt, conf, safety, network)
    return {"status": "SOURCE_ONLY_PASS", "deployment_verified": False,
            "fencing_verified": False, "api_authorization_certified": False,
            "release": "STAGE45_NO_GO", "checks": checks,
            "missing_evidence": ["real_host_and_pod_reachability",
                                 "real_operator_owned_old_primary_fencing",
                                 "independent_epoch_authority",
                                 "authenticated_workload_and_session_retirement",
                                 "tenant_resource_authorization",
                                 "hardware_acceptance_and_independent_review"]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime", required=True, type=Path)
    parser.add_argument("--platform", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(inventory(args.runtime, args.platform), sort_keys=True, indent=2))


if __name__ == "__main__":
    main()
