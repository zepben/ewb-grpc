"""Brief EWB specification diffs between Git commits, independent of the Textual interface."""

from __future__ import annotations

import subprocess
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

from cim_ple.models import as_list, text

SPEC_PATH = "spec/ewb"

ADDED = "added"
REMOVED = "removed"
CHANGED = "changed"


class GitError(RuntimeError):
    """A Git command failed or a revision could not be resolved."""


@dataclass(frozen=True)
class FieldChange:
    kind: str
    name: str
    detail: str


@dataclass
class ClassDiff:
    name: str
    changes: list[FieldChange] = field(default_factory=list)


@dataclass
class SpecDiff:
    base: str
    head: str
    classes: list[ClassDiff] = field(default_factory=list)

    @property
    def is_empty(self) -> bool:
        return not self.classes


def _git(repository: Path, *arguments: str) -> str:
    try:
        result = subprocess.run(("git", *arguments), cwd=repository, text=True, capture_output=True, check=True)
    except (OSError, subprocess.CalledProcessError) as error:
        message = (getattr(error, "stderr", "") or str(error)).strip()
        raise GitError(f"git {arguments[0]} failed: {message}") from error
    return result.stdout


def resolve_ref(repository: Path, ref: str) -> str:
    """Resolve a commit-ish to a short hash, or raise GitError when it does not exist."""

    try:
        return _git(repository, "rev-parse", "--short", "--verify", f"{ref}^{{commit}}").strip()
    except GitError as error:
        raise GitError(f"Unknown commit: {ref}") from error


def _file_at_ref(repository: Path, ref: str, path: str) -> dict[str, Any] | None:
    """Load one YAML specification file as it existed at a commit, or None when absent."""

    try:
        source = _git(repository, "show", f"{ref}:{path}")
    except GitError:
        return None
    if not source.strip():
        return None
    try:
        data = yaml.safe_load(source)
    except yaml.YAMLError:
        return None
    return data if isinstance(data, dict) else None


def _attribute_fields(data: dict[str, Any] | None) -> dict[str, tuple[str, str]]:
    if data is None:
        return {}
    return {
        text(item.get("name")): (text(item.get("name")), text(item.get("type")) or "—")
        for item in as_list(data.get("attributes"))
        if item.get("name")
    }


def _association_fields(data: dict[str, Any] | None, class_name: str) -> dict[str, tuple[str, str]]:
    """Map each association to its short presentation, keyed by the class on the other end."""

    if data is None:
        return {}
    fields: dict[str, tuple[str, str]] = {}
    for item in as_list(data.get("associations")):
        if item.get("source") == class_name:
            other, name, cardinality = text(item.get("target")), text(item.get("targetName")), text(item.get("targetCardinality"))
        elif item.get("target") == class_name:
            other, name, cardinality = text(item.get("source")), text(item.get("sourceName")), text(item.get("sourceCardinality"))
        else:
            continue
        if other:
            fields[other] = (name or other, f"{cardinality or '—'} {other}")
    return fields


def _change_detail(base: tuple[str, str], head: tuple[str, str]) -> str:
    """Describe what actually changed: presentation name, detail, or both."""

    base_name, base_detail = base
    head_name, head_detail = head
    detail = head_detail if base_detail == head_detail else f"{base_detail} -> {head_detail}"
    return f"was {base_name}: {detail}" if base_name != head_name else detail


def _changes_for_fields(
    base: dict[str, tuple[str, str]], head: dict[str, tuple[str, str]]
) -> list[FieldChange]:
    changes = [FieldChange(ADDED, head[name][0], head[name][1]) for name in sorted(head.keys() - base.keys())]
    changes += [
        FieldChange(CHANGED, head[name][0], _change_detail(base[name], head[name]))
        for name in sorted(base.keys() & head.keys())
        if base[name] != head[name]
    ]
    changes += [FieldChange(REMOVED, base[name][0], base[name][1]) for name in sorted(base.keys() - head.keys())]
    return changes


def diff_class_definition(base_data: dict[str, Any] | None, head_data: dict[str, Any] | None, name: str) -> list[FieldChange]:
    """Compare attributes and associations of one class between two specification revisions."""

    changes = _changes_for_fields(_attribute_fields(base_data), _attribute_fields(head_data))
    changes += _changes_for_fields(_association_fields(base_data, name), _association_fields(head_data, name))
    return changes


def changed_spec_files(repository: Path, base: str, head: str) -> list[str]:
    return [
        line
        for line in _git(repository, "diff", "--name-only", base, head, "--", SPEC_PATH).splitlines()
        if line.endswith(".yaml") and Path(line).name != "__metadata.yaml"
    ]


def diff_spec_commits(repository: Path, base: str, head: str = "HEAD") -> SpecDiff:
    """Diff every EWB specification class that changed between two commits."""

    base_ref = resolve_ref(repository, base)
    head_ref = resolve_ref(repository, head)
    classes = []
    for path in changed_spec_files(repository, base_ref, head_ref):
        name = Path(path).stem
        changes = diff_class_definition(_file_at_ref(repository, base_ref, path), _file_at_ref(repository, head_ref, path), name)
        if changes:
            classes.append(ClassDiff(name, changes))
    classes.sort(key=lambda item: item.name.casefold())
    return SpecDiff(base, head_ref, classes)
