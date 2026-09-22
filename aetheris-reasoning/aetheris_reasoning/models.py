from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class RiskLevel(str, Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


class ActionRoute(str, Enum):
    DIRECT = "direct"
    LOCAL_MODEL = "local_model"
    SPECIALIST_AGENT = "specialist_agent"
    RESEARCH = "research"
    SIMULATION = "simulation"
    COUNCIL = "council"
    OWNER_APPROVAL = "owner_approval"


class SourceKind(str, Enum):
    HUMAN = "human"
    OFFICIAL = "official"
    TOOL = "tool"
    FILE = "file"
    MEMORY = "memory"
    WEB = "web"
    MODEL_INFERENCE = "model_inference"


@dataclass(frozen=True)
class Evidence:
    source_id: str
    source_kind: SourceKind
    claim: str
    confidence: float
    verified: bool = False

    def __post_init__(self) -> None:
        if not 0.0 <= self.confidence <= 1.0:
            raise ValueError("evidence confidence must be between 0 and 1")


@dataclass(frozen=True)
class TaskContext:
    task_id: str
    description: str
    risk: RiskLevel = RiskLevel.LOW
    uncertainty: float = 0.0
    has_side_effects: bool = False
    reversible: bool = True
    private_data: bool = False
    requires_fresh_information: bool = False
    complexity: int = 1
    evidence: tuple[Evidence, ...] = ()

    def __post_init__(self) -> None:
        if not 0.0 <= self.uncertainty <= 1.0:
            raise ValueError("uncertainty must be between 0 and 1")
        if not 1 <= self.complexity <= 10:
            raise ValueError("complexity must be between 1 and 10")


@dataclass(frozen=True)
class Claim:
    subject: str
    predicate: str
    value: str
    source_id: str
    confidence: float = 1.0
    valid_from: str | None = None
    valid_until: str | None = None

    def __post_init__(self) -> None:
        if not 0.0 <= self.confidence <= 1.0:
            raise ValueError("claim confidence must be between 0 and 1")


@dataclass
class DecisionRecord:
    decision_id: str
    task_id: str
    decision: str
    route: ActionRoute
    confidence: float
    evidence: list[dict[str, Any]] = field(default_factory=list)
    alternatives: list[str] = field(default_factory=list)
    approval: str | None = None
    outcome: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)
