#!/usr/bin/env python3
"""Aetheris E3.9G governed Agent Skills catalog — discovery only, never execution.

Third-party files are inert *data*, not system/developer/owner instructions.
CLI commands: validate | list | match <query> | show <name>.
No shell, interpreter execution, network, installers, model calls or writes.
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

SKILLS_ROOT = Path(__file__).resolve().parents[2] / "skills"
REGISTRY = SKILLS_ROOT / "registry.json"
ALLOWED_TOP = frozenset({"native", "third-party"})
SKILL_ID = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\Z", re.ASCII)
SHA1_HEX = re.compile(r"[0-9a-f]{40}\Z", re.ASCII)
SHA256_HEX = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
ALLOWED_TRUST = frozenset(("third-party-pinned-reference", "first-party-advisory"))
REGISTRY_FIELDS = frozenset(("schemaVersion","mode","skills","upstreamReview"))
COMMON_FIELDS = frozenset(("name","path","trust","tags","capabilities","autoActivate"))
THIRD_PARTY_FIELDS = COMMON_FIELDS | frozenset(("origin","originCommit","upstreamBlobSha1","license"))
SOURCE_REPO = "https://github.com/emilkowalski/skills"
SOURCE_COMMIT = "2d228630ba1446365f614a108cb8252f1feda202"


def no_duplicate_keys(items):
    result = {}
    for key, value in items:
        if key in result:
            raise ValueError("E39G_DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def read_checked(root: Path, relative: str, limit: int = 48_000) -> bytes:
    if not isinstance(relative, str) or not relative or "\\" in relative:
        raise ValueError("E39G_INVALID_RELATIVE_PATH")
    p = Path(relative)
    if (p.is_absolute() or any(part in (".", "..") for part in p.parts)
            or not p.parts or p.parts[0] not in ALLOWED_TOP):
        raise ValueError("E39G_TRAVERSAL_OR_SCOPE_DENIED")
    cur = root
    if cur.is_symlink():
        raise ValueError("E39G_SYMLINK_SKILLS_ROOT")
    for part in p.parts:
        cur = cur / part
        if cur.is_symlink():
            raise ValueError("E39G_SYMLINK_SKILL_DENIED")
    if (cur.resolve().is_relative_to(root.resolve()) is False
            or not cur.is_file() or cur.stat().st_size > limit
            or cur.stat().st_size < 15):
        raise ValueError("E39G_INVALID_FILE_OR_SIZE")
    return cur.read_bytes()


def frontmatter(content: bytes, expected_name: str) -> str:
    try:
        text = content.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ValueError("E39G_NON_UTF8_SKILL") from error
    # Do not parse YAML objects/tags or instantiate arbitrary loader classes.
    lines = text.splitlines()
    if not lines or lines[0] != "---":
        raise ValueError("E39G_SKILL_FRONTMATTER_REQUIRED")
    try:
        closing = lines.index("---", 1)
    except ValueError as error:
        raise ValueError("E39G_FRONTMATTER_NOT_CLOSED") from error
    if closing > 25 or closing < 3:
        raise ValueError("E39G_FRONTMATTER_SIZE_INVALID")
    metadata = {}
    for line in lines[1:closing]:
        if not line.strip() or line.startswith(" "):
            continue  # Ignore optional upstream nested metadata; never execute YAML.
        if ":" not in line:
            raise ValueError("E39G_MALFORMED_METADATA")
        key, value = line.split(":", 1)
        if key in metadata:
            raise ValueError("E39G_DUPLICATE_FRONTMATTER_FIELD")
        metadata[key] = value.strip().strip('"').strip("'")
    if metadata.get("name") != expected_name:
        raise ValueError("E39G_FRONTMATTER_NAME_MISMATCH")
    description = metadata.get("description")
    if not description or len(description) > 1300:
        raise ValueError("E39G_MISSING_SKILL_DESCRIPTION")
    if len(lines) <= closing + 2:
        raise ValueError("E39G_EMPTY_SKILL_BODY")
    return description


def git_blob_sha(raw: bytes) -> str:
    # GitHub Windows runners may checkout text with CRLF. Compare against the
    # exact pinned Git blob LF representation; do not ignore any other edits.
    if b"\r" in raw.replace(b"\r\n", b""):
        raise ValueError("E39G_UNEXPECTED_CARRIAGE_RETURN")
    canonical = raw.replace(b"\r\n", b"\n")
    return hashlib.sha1(b"blob " + str(len(canonical)).encode() + b"\0" + canonical).hexdigest()


def load_registry(root: Path = SKILLS_ROOT) -> list[dict]:
    reg = root / "registry.json"
    if reg.is_symlink() or not reg.is_file() or reg.stat().st_size > 50_000:
        raise ValueError("E39G_MISSING_REGISTRY_OR_SYMLINK")
    data = json.loads(reg.read_text("utf-8"), object_pairs_hook=no_duplicate_keys)
    if (type(data) is not dict or set(data) != REGISTRY_FIELDS
            or data["schemaVersion"] != 1
            or data["mode"] != "READ_ONLY_SKILL_REFERENCES_NO_AGENT_TOOLS"
            or type(data["skills"]) is not list or not data["skills"]
            or len(data["skills"]) > 32
            or type(data["upstreamReview"]) is not dict):
        raise ValueError("E39G_REGISTRY_UNSAFE_SCHEMA")
    provenance = data["upstreamReview"]
    if (provenance.get("repository") != SOURCE_REPO
            or provenance.get("commit") != SOURCE_COMMIT
            or provenance.get("license") != "MIT"
            or provenance.get("licensePath") != "third-party/emilkowalski/LICENSE"):
        raise ValueError("E39G_UNPINNED_UPSTREAM_PROVENANCE")
    license_bytes = read_checked(root, provenance["licensePath"], 4000)
    if b"MIT License" not in license_bytes or b"Emil Kowalski" not in license_bytes:
        raise ValueError("E39G_MISSING_UPSTREAM_LICENSE")
    seen = set()
    paths = set()
    result = []
    for item in data["skills"]:
        if not isinstance(item, dict):
            raise ValueError("E39G_NONOBJECT_SKILL")
        trust = item.get("trust")
        if trust not in ALLOWED_TRUST:
            raise ValueError("E39G_UNSUPPORTED_TRUST_TIER")
        required = THIRD_PARTY_FIELDS if trust == "third-party-pinned-reference" else COMMON_FIELDS
        if set(item) != required:
            raise ValueError("E39G_EXTRANEOUS_FIELDS_OR_UNDECLARED_CAPABILITY")
        name, path = item["name"], item["path"]
        if (type(name) is not str or SKILL_ID.fullmatch(name) is None
                or len(name) > 64 or name in seen):
            raise ValueError("E39G_INVALID_OR_DUPLICATE_SKILL_NAME")
        if (type(path) is not str or path in paths
                or not path.endswith("/" + name + "/SKILL.md")):
            raise ValueError("E39G_INVALID_SKILL_PATH")
        expected_prefix = ("third-party/emilkowalski/" if
                           trust == "third-party-pinned-reference" else "native/")
        if not path.startswith(expected_prefix):
            raise ValueError("E39G_SKILL_TRUST_PATH_MISMATCH")
        if (item["autoActivate"] is not False
                or type(item["capabilities"]) is not list or item["capabilities"]):
            raise ValueError("E39G_CAPABILITY_ESCALATION_DENIED")
        tags = item["tags"]
        if (not isinstance(tags, list) or not tags or len(tags) > 12
                or any(type(tag) is not str or SKILL_ID.fullmatch(tag) is None
                       for tag in tags)):
            raise ValueError("E39G_SKILL_TAGS_INVALID")
        raw = read_checked(root, path)
        description = frontmatter(raw, name)
        if trust == "third-party-pinned-reference":
            if (item["origin"] != SOURCE_REPO
                    or item["originCommit"] != SOURCE_COMMIT
                    or item["license"] != "MIT"
                    or type(item["upstreamBlobSha1"]) is not str
                    or SHA1_HEX.fullmatch(item["upstreamBlobSha1"]) is None
                    or git_blob_sha(raw) != item["upstreamBlobSha1"]):
                raise ValueError("E39G_THIRD_PARTY_CONTENT_DRIFT_OR_UNPINNED")
        result.append({"name":name,"description":description,"tags":tags,
                       "trust":trust,"path":path, "sha256":hashlib.sha256(raw).hexdigest(),
                       "autoActivate":False,"capabilities":[]})
        seen.add(name)
        paths.add(path)
    # Reject unexpected additional skill payloads; only reviewed skill files count.
    for candidate in root.rglob("SKILL.md"):
        if candidate.is_symlink() or candidate.relative_to(root).as_posix() not in paths:
            raise ValueError("E39G_UNREVIEWED_SKILL_PAYLOAD")
    return result


def find(skills: list[dict], query: str) -> list[dict]:
    if type(query) is not str or not query or len(query) > 160:
        raise ValueError("E39G_SEARCH_QUERY_INVALID")
    words = re.findall(r"[a-z0-9]+", query.lower())
    return sorted((s for s in skills if
                  any(word in s["name"].replace("-"," ") or
                      any(word in tag for tag in s["tags"]) for word in words)),
                  key=lambda s:s["name"])


def main(args: list[str]) -> None:
    if args == ["validate"]:
        skills = load_registry()
        print(json.dumps({"status":"VALIDATED_READ_ONLY_REFERENCE_ONLY",
                          "skills":len(skills),
                          "thirdParty":sum(s["trust"]=="third-party-pinned-reference" for s in skills),
                          "native":sum(s["trust"]=="first-party-advisory" for s in skills),
                          "agentActivation":False,"toolAuthorityGranted":False}))
    elif args == ["list"]:
        print(json.dumps(load_registry(), indent=2, sort_keys=True))
    elif len(args) == 2 and args[0] == "match":
        print(json.dumps(find(load_registry(), args[1]), indent=2, sort_keys=True))
    elif len(args) == 2 and args[0] == "show":
        index = {s["name"]:s for s in load_registry()}
        if args[1] not in index:
            raise ValueError("E39G_UNKNOWN_SKILL")
        item = index[args[1]]
        print("E39G_UNTRUSTED_REFERENCE_NOT_AUTHORITY; NO_TOOLS_OR_AUTOMATIC_EXECUTION")
        print(read_checked(SKILLS_ROOT,item["path"]).decode("utf-8"))
    else:
        raise ValueError("E39G_ALLOWED_COMMANDS_VALIDATE_LIST_MATCH_SHOW_ONLY")


if __name__ == "__main__":
    main(sys.argv[1:])
