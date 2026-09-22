from __future__ import annotations

from dataclasses import dataclass
import re


@dataclass(frozen=True)
class CompiledRule:
    rule_id: str
    effect: str
    target: str
    raw_text: str
    requires_preview: bool = True


@dataclass(frozen=True)
class PolicyCompileResult:
    rule: CompiledRule | None
    conflicts: tuple[str, ...]
    warnings: tuple[str, ...]


class PolicyCompiler:
    """Small deterministic compiler for explicit owner-rule phrases.

    It intentionally does not pretend to understand arbitrary natural language.
    Unsupported phrasing is rejected for owner review.
    """

    _patterns = (
        (re.compile(r"^never\s+(.+)$", re.I), "block"),
        (re.compile(r"^always\s+(.+)$", re.I), "require"),
        (re.compile(r"^require approval (?:for|before)\s+(.+)$", re.I), "approval"),
    )

    def compile(self, text: str, existing: tuple[CompiledRule, ...] = ()) -> PolicyCompileResult:
        normalized = " ".join(text.strip().split())
        if not normalized:
            return PolicyCompileResult(None, (), ("empty-rule",))

        effect = None
        target = None
        for pattern, candidate_effect in self._patterns:
            match = pattern.match(normalized)
            if match:
                effect = candidate_effect
                target = match.group(1).strip().casefold()
                break

        if not effect or not target:
            return PolicyCompileResult(
                None,
                (),
                ("unsupported-phrasing-requires-owner-normalization",),
            )

        rule_id = re.sub(r"[^a-z0-9]+", "-", f"{effect}-{target}").strip("-")[:80]
        conflicts = []
        for rule in existing:
            same_target = rule.target == target
            incompatible = {rule.effect, effect} == {"block", "require"}
            if same_target and incompatible:
                conflicts.append(rule.rule_id)

        return PolicyCompileResult(
            CompiledRule(rule_id, effect, target, normalized),
            tuple(sorted(conflicts)),
            (),
        )
