#!/usr/bin/env python3
"""Verify rendered Docker Compose ports: pinned source only, not deployed state."""
import argparse
import json
from pathlib import Path

EXPECTED = {
    "user-service": (8081, 8081),
    "identity-service": (8082, 8082),
    "audit-service": (8083, 8083),
    "gateway": (8080, 8080),
}
DATA_STORES = ("postgres", "redis", "rabbitmq")
RUNTIME = (8090, 8090)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_model(model, *, runtime_required):
    require(isinstance(model, dict) and isinstance(model.get("services"), dict),
            "invalid rendered Compose services")
    services = model["services"]
    admitted = {}
    for name, service in services.items():
        require(isinstance(service, dict), "malformed Compose service: " + name)
        require(service.get("network_mode") != "host",
                "host network bypass: " + name)
        require(service.get("privileged") is not True,
                "privileged container in source: " + name)
        ports = service.get("ports") or []
        require(isinstance(ports, list), "malformed ports: " + name)
        parsed = []
        for entry in ports:
            require(isinstance(entry, dict), "noncanonical rendered port: " + name)
            require(entry.get("host_ip") == "127.0.0.1",
                    "unsafe or unbound host published port: " + name)
            require(entry.get("protocol", "tcp") == "tcp",
                    "unexpected published protocol: " + name)
            published, target = str(entry.get("published")), entry.get("target")
            require(published.isdecimal() and isinstance(target, int)
                    and 1 <= int(published) <= 65535 and 1 <= target <= 65535,
                    "unsupported published port mapping: " + name)
            parsed.append((int(published), target))
        admitted[name] = parsed
    for name, required in EXPECTED.items():
        require(required in admitted.get(name, []),
                "required local-only service route not bound: " + name)
    for name in DATA_STORES:
        require(not admitted.get(name), "data store has published host port: " + name)
    if runtime_required:
        require(RUNTIME in admitted.get("orchestrator-service", []),
                "orchestrator port missing in full/merged model")
    else:
        require(not admitted.get("orchestrator-service"),
                "core-only model unexpectedly publishes Runtime")
    return sum(len(x) for x in admitted.values())


def negative_trials(model):
    def fail(mutator, runtime_required):
        changed = json.loads(json.dumps(model))
        mutator(changed)
        try:
            validate_model(changed, runtime_required=runtime_required)
        except ValueError:
            return
        raise AssertionError("rendered source mutation unexpectedly passed")
    def pub(m, service):
        return m["services"][service]["ports"][0]
    fail(lambda m: pub(m, "user-service").update(host_ip="0.0.0.0"), True)
    fail(lambda m: pub(m, "user-service").pop("host_ip", None), True)
    fail(lambda m: pub(m, "gateway").update(protocol="udp"), True)
    fail(lambda m: m["services"]["gateway"].update(network_mode="host"), True)
    fail(lambda m: m["services"]["user-service"].update(privileged=True), True)
    fail(lambda m: m["services"]["postgres"].update(ports=[
        {"host_ip": "127.0.0.1", "published": "5432", "target": 5432}
    ]), True)
    fail(lambda m: m["services"]["orchestrator-service"].pop("ports", None), True)
    fail(lambda m: m["services"]["audit-service"]["ports"].append(
        {"published": "9000", "target": 9000, "protocol": "tcp"}), True)
    return 8


def main():
    parser = argparse.ArgumentParser()
    for name in ("full", "core", "external", "merged"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    out = {}
    for name in ("full", "core", "external", "merged"):
        model = json.loads(getattr(args, name).read_text(encoding="utf-8"))
        out[name] = validate_model(model, runtime_required=name != "core")
        if name == "full":
            out["rendered_negative_trials"] = negative_trials(model)
    print(json.dumps({
        "status": "RENDERED_SOURCE_ONLY_PASS",
        "rendered_compose_models": 4,
        "negative_tests": out["rendered_negative_trials"],
        "deployment_verified": False,
        "resource_fencing_verified": False,
        "release": "STAGE45_NO_GO",
        "port_counts": out,
    }, sort_keys=True))


if __name__ == "__main__":
    main()
