from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


def text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, (list, tuple)):
        return ", ".join(text(item) for item in value)
    return str(value)


def as_list(value: Any) -> list[dict[str, Any]]:
    return [item for item in value or [] if isinstance(item, dict)] if isinstance(value, list) else []


def clean_description(value: Any) -> str:
    return re.sub(r"\s+", " ", text(value)).strip().replace("|", r"\|") or "—"


@dataclass(frozen=True)
class Attribute:
    name: str
    type: str
    nullable: str
    description: str


@dataclass(frozen=True)
class Association:
    source: str; target: str; source_cardinality: str; target_cardinality: str
    source_name: str; target_name: str; source_description: str; target_description: str


@dataclass
class ClassSpec:
    name: str; path: Path; relative_path: tuple[str, ...]; description: str = ""
    attributes: list[Attribute] = field(default_factory=list)
    ancestors: list[str] = field(default_factory=list)
    descendants: list[str] = field(default_factory=list)
    associations: list[Association] = field(default_factory=list)


@dataclass
class PackageInfo:
    name: str; path: tuple[str, ...]; description: str = ""
    classes: list[ClassSpec] = field(default_factory=list)


@dataclass
class TreeEntry:
    label: str; path: tuple[str, ...]; kind: str
    item: ClassSpec | PackageInfo | None = None
    children: list["TreeEntry"] = field(default_factory=list)


@dataclass
class ClassMarks:
    attributes: set[str] = field(default_factory=set)
    associations: dict[int, str] = field(default_factory=dict)
    zbex_attributes: list[dict[str, str]] = field(default_factory=list)
    zbex_associations: list[dict[str, str]] = field(default_factory=list)
    changed_attributes: set[str] = field(default_factory=set)
    changed_associations: set[int] = field(default_factory=set)

    def copy(self) -> "ClassMarks":
        return ClassMarks(set(self.attributes), dict(self.associations), [dict(x) for x in self.zbex_attributes], [dict(x) for x in self.zbex_associations], set(self.changed_attributes), set(self.changed_associations))
