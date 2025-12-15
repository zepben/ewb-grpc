#  Copyright 2025 Zeppelin Bend Pty Ltd
#
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at https://mozilla.org/MPL/2.0/.

#!/usr/bin/env python3

"""
Generate an RDFS schema (Turtle) from a folder of CIM profile YAML files.

See README.md for usage details.
Requires pyyaml 6
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, List

import yaml

PREFIXES = """@prefix cim: <http://iec.ch/TC57/CIM100#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix profile: <http://example.com/cim/profile#> ."""

CARDINALITY_PROPERTIES = """profile:sourceCardinality a rdf:Property ;
    rdfs:comment "Cardinality at the source end of an association." .

profile:targetCardinality a rdf:Property ;
    rdfs:comment "Cardinality at the target end of an association." ."""

XSD_TYPE_MAP = {
    "string": "xsd:string",
    "boolean": "xsd:boolean",
    "bool": "xsd:boolean",
    "integer": "xsd:int",
    "int": "xsd:int",
    "long": "xsd:long",
    "float": "xsd:double",
    "double": "xsd:double",
    "number": "xsd:double",
}

CIM_PRIMITIVES = {
    "activepower",
    "reactivepower",
    "apparentpower",
    "current",
    "voltage",
    "frequency",
    "pu",
    "angledegrees",
    "resistance",
    "susceptance",
    "conductance",
}


@dataclass
class CIMAttribute:
    name: str
    type: str
    description: str


@dataclass
class CIMAssociation:
    source: str
    target: str
    source_cardinality: str | None
    target_cardinality: str | None
    source_name: str | None
    target_name: str | None
    source_description: str | None
    target_description: str | None


@dataclass
class CIMClass:
    name: str
    description: str
    ancestors: List[str]
    attributes: List[CIMAttribute]
    associations: List[CIMAssociation]
    source_path: Path


def sanitize_local_name(name: str) -> str:
    """Return a safe local name for use in Turtle IRIs."""
    sanitized = re.sub(r"[^A-Za-z0-9_]", "", name or "")
    if not sanitized:
        sanitized = "item"
    if sanitized[0].isdigit():
        sanitized = f"_{sanitized}"
    return sanitized


def sanitize_property_name(name: str) -> str:
    """Return a safe property local name preserving dot notation (e.g., Class.attr)."""
    sanitized = re.sub(r"[^A-Za-z0-9_.]", "", name or "")
    sanitized = sanitized.strip(".")
    if not sanitized:
        sanitized = "prop"
    if sanitized[0].isdigit():
        sanitized = f"_{sanitized}"
    return sanitized


def escape_literal(text: str | None) -> str:
    """Escape a string for safe inclusion in Turtle string literals."""
    if text is None:
        return ""
    escaped = text.replace("\\", "\\\\").replace('"', '\\"')
    return escaped.replace("\n", "\\n")


def iter_yaml_files(folder: Path) -> Iterable[Path]:
    """Yield YAML files recursively (both .yaml and .yml), sorted for stability."""
    return sorted(list(folder.rglob("*.yaml")) + list(folder.rglob("*.yml")))


def ensure_list(value: Any, key: str, path: Path) -> List[Any]:
    if value is None:
        return []
    if isinstance(value, list):
        return value
    raise ValueError(f"Expected '{key}' to be a list in {path}")


def get_list_field(
    data: Dict[str, Any], key: str, path: Path, required: bool = False
) -> List[Any]:
    if key not in data:
        if required:
            raise ValueError(f"Missing required key '{key}' in {path}")
        print(f"Note: '{key}' missing in {path}; defaulting to empty list.", file=sys.stderr)
        return []
    return ensure_list(data.get(key), key, path)


def load_yaml_file(path: Path) -> Dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as handle:
            data = yaml.safe_load(handle)
    except yaml.YAMLError as exc:  # pragma: no cover - handled in CLI flow
        raise ValueError(f"Failed to parse YAML file {path}: {exc}") from exc
    if not isinstance(data, dict):
        raise ValueError(f"Top-level YAML content must be a mapping in {path}")
    return data


def parse_class(data: Dict[str, Any], path: Path) -> CIMClass:
    name = data.get("name")
    description = data.get("description")
    if not name:
        raise ValueError(f"Missing required key 'name' in {path}")
    if description is None:
        raise ValueError(f"Missing required key 'description' in {path} for class {name}")

    ancestors = get_list_field(data, "ancestors", path)
    attributes_raw = get_list_field(data, "attributes", path)
    associations_raw = get_list_field(data, "associations", path)

    attributes: List[CIMAttribute] = []
    for idx, attr in enumerate(attributes_raw):
        if not isinstance(attr, dict):
            raise ValueError(f"Attribute #{idx + 1} in {path} is not a mapping")
        attr_name = attr.get("name")
        attr_type = attr.get("type")
        if not attr_name:
            raise ValueError(f"Attribute #{idx + 1} in {path} must have 'name'")
        if not attr_type:
            print(
                f"Note: attribute '{attr_name}' in {path} missing 'type'; defaulting to String.",
                file=sys.stderr,
            )
            attr_type = "String"
        attributes.append(
            CIMAttribute(
                name=str(attr_name),
                type=str(attr_type),
                description=str(attr.get("description", "")),
            )
        )

    associations: List[CIMAssociation] = []
    for idx, assoc in enumerate(associations_raw):
        if not isinstance(assoc, dict):
            raise ValueError(f"Association #{idx + 1} in {path} is not a mapping")
        source = assoc.get("source")
        target = assoc.get("target")
        if not source or not target:
            raise ValueError(
                f"Association #{idx + 1} in {path} must include 'source' and 'target'"
            )
        associations.append(
            CIMAssociation(
                source=str(source),
                target=str(target),
                source_cardinality=_maybe_str(assoc.get("sourceCardinality")),
                target_cardinality=_maybe_str(assoc.get("targetCardinality")),
                source_name=_maybe_str(assoc.get("sourceName")),
                target_name=_maybe_str(assoc.get("targetName")),
                source_description=_maybe_str(assoc.get("sourceDescription")),
                target_description=_maybe_str(assoc.get("targetDescription")),
            )
        )

    return CIMClass(
        name=str(name),
        description=str(description),
        ancestors=[str(ancestor) for ancestor in ancestors],
        attributes=attributes,
        associations=associations,
        source_path=path,
    )


def _maybe_str(value: Any) -> str | None:
    return None if value is None else str(value)


def load_classes(folder: Path) -> Dict[str, CIMClass]:
    if not folder.exists() or not folder.is_dir():
        raise FileNotFoundError(f"Folder does not exist or is not a directory: {folder}")

    yaml_files = list(iter_yaml_files(folder))
    if not yaml_files:
        raise FileNotFoundError(f"No YAML files found in {folder}")

    classes: Dict[str, CIMClass] = {}
    for file_path in yaml_files:
        if file_path.name.startswith("__"):
            print(f"Note: skipping metadata file {file_path}", file=sys.stderr)
            continue
        data = load_yaml_file(file_path)
        cim_class = parse_class(data, file_path)
        if cim_class.name in classes:
            existing = classes[cim_class.name].source_path
            raise ValueError(
                f"Duplicate class name '{cim_class.name}' in {file_path} "
                f"(already defined in {existing})"
            )
        classes[cim_class.name] = cim_class

    return classes


def datatype_range(datatype: str) -> str:
    if not datatype:
        return "rdfs:Literal"
    normalized = datatype.strip()
    lowered = normalized.lower()
    if lowered in XSD_TYPE_MAP:
        return XSD_TYPE_MAP[lowered]
    if lowered in CIM_PRIMITIVES:
        return f"cim:{sanitize_local_name(normalized)}"
    return f"cim:{sanitize_local_name(normalized)}"


def build_class_blocks(classes: Dict[str, CIMClass]) -> List[str]:
    lines: List[str] = []
    for idx, class_name in enumerate(sorted(classes.keys(), key=sanitize_local_name)):
        cim_class = classes[class_name]
        class_id = sanitize_local_name(cim_class.name)
        block: List[str] = [f"cim:{class_id} a rdfs:Class ;", f'    rdfs:label "{escape_literal(cim_class.name)}" ;']
        comment_line = f'    rdfs:comment "{escape_literal(cim_class.description)}"'
        if cim_class.ancestors:
            comment_line += " ;"
        else:
            comment_line += " ."
        block.append(comment_line)
        for anc_idx, ancestor in enumerate(cim_class.ancestors):
            ancestor_id = sanitize_local_name(ancestor)
            terminator = " ." if anc_idx == len(cim_class.ancestors) - 1 else " ;"
            block.append(f"    rdfs:subClassOf cim:{ancestor_id}{terminator}")
        if idx > 0:
            lines.append("")
        lines.extend(block)
    return lines


def build_property_blocks(classes: Dict[str, CIMClass]) -> List[List[str]]:
    blocks: List[tuple[str, List[str]]] = []

    for class_name in sorted(classes.keys(), key=sanitize_local_name):
        cim_class = classes[class_name]
        domain_id = sanitize_local_name(cim_class.name)

        for attr in cim_class.attributes:
            prop_local = sanitize_property_name(f"{cim_class.name}.{attr.name}")
            prop_iri = f"cim:{prop_local}"
            block = [
                f"{prop_iri} a rdf:Property ;",
                f'    rdfs:label "{escape_literal(attr.name)}" ;',
                f'    rdfs:comment "{escape_literal(attr.description)}" ;',
                f"    rdfs:domain cim:{domain_id} ;",
                f"    rdfs:range {datatype_range(attr.type)} .",
            ]
            blocks.append((prop_iri, block))

        for assoc in cim_class.associations:
            prop_local = sanitize_property_name(f"{assoc.source}.{assoc.target}")
            prop_iri = f"cim:{prop_local}"
            label = f"{assoc.source}.{assoc.target}"
            desc_parts = [part for part in [assoc.source_description, assoc.target_description] if part]
            comment = " ".join(desc_parts) if desc_parts else label
            range_id = sanitize_local_name(assoc.target)
            block = [
                f"{prop_iri} a rdf:Property ;",
                f'    rdfs:label "{escape_literal(label)}" ;',
                f'    rdfs:comment "{escape_literal(comment)}" ;',
                f"    rdfs:domain cim:{sanitize_local_name(assoc.source)} ;",
                f"    rdfs:range cim:{range_id} ;",
            ]
            if assoc.source_cardinality is not None:
                block.append(
                    f'    profile:sourceCardinality "{escape_literal(assoc.source_cardinality)}" ;'
                )
            if assoc.target_cardinality is not None:
                block.append(
                    f'    profile:targetCardinality "{escape_literal(assoc.target_cardinality)}" .'
                )
            else:
                # Replace trailing semicolon with period if we didn't add target cardinality.
                block[-1] = block[-1].rstrip(" ;") + " ."
            blocks.append((prop_iri, block))

    blocks.sort(key=lambda pair: pair[0])
    return [block for _, block in blocks]


def generate_turtle(classes: Dict[str, CIMClass]) -> str:
    lines: List[str] = [PREFIXES, "", CARDINALITY_PROPERTIES, ""]
    lines.extend(build_class_blocks(classes))
    property_blocks = build_property_blocks(classes)
    if property_blocks:
        lines.append("")
        for idx, block in enumerate(property_blocks):
            if idx > 0:
                lines.append("")
            lines.extend(block)
    return "\n".join(lines).strip() + "\n"


def parse_args(argv: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate an RDFS (Turtle) schema from CIM profile YAML files."
    )
    parser.add_argument(
        "folder",
        type=Path,
        help="Path to a folder containing CIM profile YAML files.",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=Path.cwd() / "cim_profile_schema.ttl",
        help="Output TTL file path. Defaults to ./cim_profile_schema.ttl",
    )
    return parser.parse_args(argv)


def main(argv: List[str] | None = None) -> None:
    args = parse_args(argv or sys.argv[1:])
    try:
        classes = load_classes(args.folder)
        ttl_content = generate_turtle(classes)
        args.output.write_text(ttl_content, encoding="utf-8")
        print(f"Wrote schema for {len(classes)} classes to {args.output.resolve()}")
    except Exception as exc:  # pragma: no cover - CLI convenience
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":  # pragma: no cover
    main()
