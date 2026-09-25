"""Targeted EWB YAML updates, independent of the Textual interface."""

from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml
from ruamel.yaml import YAML

from cim_ple.models import Association, Attribute, ClassMarks, ClassSpec


def ewb_spec_path(spec_directory: Path, class_spec: ClassSpec) -> Path:
    return spec_directory / "ewb" / Path(*class_spec.relative_path[1:]).with_suffix(".yaml")


def has_pending_changes(marks: ClassMarks) -> bool:
    return bool(
        marks.changed_attributes
        or marks.changed_associations
        or any(item.get("dirty") == "true" for item in marks.zbex_attributes + marks.zbex_associations)
    )


def attribute_data(attribute: Attribute) -> dict[str, Any]:
    item: dict[str, Any] = {"name": attribute.name, "type": attribute.type}
    if attribute.nullable != "—":
        item["nullable"] = attribute.nullable == "Yes"
    if attribute.description:
        item["description"] = attribute.description
    return item


def association_data(association: Association, direction: str | None = None) -> dict[str, str]:
    fields = {
        "source": association.source,
        "target": association.target,
        "sourceCardinality": association.source_cardinality,
        "targetCardinality": association.target_cardinality,
        "sourceName": association.source_name,
        "targetName": association.target_name,
        "sourceDescription": association.source_description,
        "targetDescription": association.target_description,
    }
    data = {key: value for key, value in fields.items() if value and value != "—"}
    if direction == "1 way":
        for field in ("sourceCardinality", "sourceName", "sourceDescription"):
            data.pop(field, None)
    elif direction == "Bi-directional":
        data.update(
            sourceCardinality=association.source_cardinality,
            sourceName=association.source_name,
            sourceDescription=association.source_description,
        )
    return data


def ewb_spec_data(class_spec: ClassSpec, marks: ClassMarks, existing: Any = None) -> dict[str, Any]:
    """Merge only editor changes into an EWB class mapping."""
    is_new_file = not isinstance(existing, dict)
    data: dict[str, Any] = existing if not is_new_file else {"name": class_spec.name}
    if is_new_file:
        if class_spec.description:
            data["description"] = class_spec.description
        if class_spec.ancestors:
            data["ancestors"] = class_spec.ancestors
        if class_spec.descendants:
            data["descendants"] = class_spec.descendants

    attributes = list(data.get("attributes") or [])
    for attribute in class_spec.attributes:
        if attribute.name not in marks.changed_attributes:
            continue
        attributes = [item for item in attributes if item.get("name") != attribute.name]
        if attribute.name in marks.attributes:
            attributes.append(attribute_data(attribute))
    for item in marks.zbex_attributes:
        if item.get("dirty") == "true":
            attributes = [entry for entry in attributes if entry.get("name") != item["name"]]
            if item.get("marked", "true") == "true":
                attributes.append({key: value for key, value in item.items() if key in {"name", "type", "description"} and value})
    if attributes:
        data["attributes"] = attributes
    else:
        data.pop("attributes", None)

    associations = list(data.get("associations") or [])
    for index, association in enumerate(class_spec.associations):
        if index not in marks.changed_associations:
            continue
        associations = [item for item in associations if item.get("target") != association.target]
        if marks.associations.get(index) in {"1 way", "Bi-directional"}:
            associations.append(association_data(association, marks.associations[index]))
    for item in marks.zbex_associations:
        if item.get("dirty") == "true":
            associations = [entry for entry in associations if entry.get("target") != item["target"]]
            if item.get("marked", "true") == "true":
                associations.append(
                    {
                        "source": class_spec.name,
                        "target": item["target"],
                        "targetCardinality": item.get("cardinality", "0..*"),
                        "targetName": item.get("name", item["target"]),
                        **({"targetDescription": item["description"]} if item.get("description") else {}),
                    }
                )
    if associations:
        data["associations"] = associations
    else:
        data.pop("associations", None)
    return data


def apply_text_changes(source: str, class_spec: ClassSpec, marks: ClassMarks) -> str:
    """Patch only dirty YAML list entries, preserving all other file bytes."""
    text = source
    attributes = {item.name: attribute_data(item) for item in class_spec.attributes}
    for name in marks.changed_attributes:
        text = patch_yaml_item(text, "attributes", "name", name, attributes.get(name), name in marks.attributes)
    for item in marks.zbex_attributes:
        if item.get("dirty") == "true":
            value = {key: value for key, value in item.items() if key in {"name", "type", "description"} and value}
            text = patch_yaml_item(text, "attributes", "name", item["name"], value, item.get("marked") == "true")
    for index in marks.changed_associations:
        association = class_spec.associations[index]
        text = patch_yaml_item(
            text, "associations", "target", association.target,
            association_data(association, marks.associations.get(index)),
            marks.associations.get(index) in {"1 way", "Bi-directional"},
        )
    for item in marks.zbex_associations:
        if item.get("dirty") == "true":
            value = {
                "source": class_spec.name,
                "target": item["target"],
                "targetCardinality": item.get("cardinality", "0..*"),
                "targetName": item.get("name", item["target"]),
                **({"targetDescription": item["description"]} if item.get("description") else {}),
            }
            text = patch_yaml_item(text, "associations", "target", item["target"], value, item.get("marked") == "true")
    return text


def patch_yaml_item(
    source: str, section: str, key: str, value: str, item: dict[str, Any] | None, include: bool
) -> str:
    """Remove or append one top-level YAML-list item without reformatting peers."""
    lines = source.splitlines(keepends=True)
    section_start = next((index for index, line in enumerate(lines) if line == f"{section}:\n"), None)
    if section_start is None:
        if not include or item is None:
            return source
        return source.rstrip("\n") + f"\n{section}:\n" + yaml.safe_dump([item], sort_keys=False, allow_unicode=True)
    section_end = next((index for index in range(section_start + 1, len(lines)) if lines[index] and not lines[index][0].isspace() and not lines[index].startswith("-")), len(lines))
    starts = [index for index in range(section_start + 1, section_end) if lines[index].startswith("- ")]
    match_start = next((start for start in starts if any(line.strip() in {f"{key}: {value}", f"- {key}: {value}"} for line in lines[start:next((item for item in starts if item > start), section_end)])), None)
    if match_start is not None:
        match_end = next((start for start in starts if start > match_start), section_end)
        if not include:
            del lines[match_start:match_end]
        elif section == "associations" and item and "sourceCardinality" not in item and "sourceDescription" not in item:
            lines[match_start:match_end] = [line for line in lines[match_start:match_end] if not line.lstrip().startswith(("sourceCardinality:", "sourceDescription:", "sourceName:"))]
        elif section == "associations" and item:
            for field in ("sourceCardinality", "sourceName", "sourceDescription"):
                if field in item and not any(line.lstrip().startswith(f"{field}:") for line in lines[match_start:match_end]):
                    rendered = yaml.safe_dump({field: item[field]}, sort_keys=False, allow_unicode=True, width=100000)
                    rendered_lines = [f"  {line}" for line in rendered.splitlines(keepends=True)]
                    lines[match_end:match_end] = rendered_lines
                    match_end += len(rendered_lines)
        else:
            return source
        return "".join(lines)
    if include and item is not None:
        lines[section_end:section_end] = yaml.safe_dump([item], sort_keys=False, allow_unicode=True).splitlines(keepends=True)
    return "".join(lines)


def write_ewb_spec(spec_directory: Path, class_spec: ClassSpec, marks: ClassMarks) -> bool:
    """Write dirty selections for one class and return whether its file changed."""
    destination = ewb_spec_path(spec_directory, class_spec)
    if destination.exists() and not has_pending_changes(marks):
        return False
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        original = destination.read_text(encoding="utf-8")
        updated = apply_text_changes(original, class_spec, marks)
        if updated == original:
            return False
        destination.write_text(updated, encoding="utf-8")
        return True
    round_trip_yaml = YAML(typ="rt")
    round_trip_yaml.preserve_quotes = True
    round_trip_yaml.width = 120
    with destination.open("w", encoding="utf-8") as stream:
        round_trip_yaml.dump(ewb_spec_data(class_spec, marks), stream)
    return True
