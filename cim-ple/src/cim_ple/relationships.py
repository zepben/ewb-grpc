"""EWB association direction classification."""

from __future__ import annotations

from collections.abc import Callable

from cim_ple.models import Association, ClassSpec

ResolveReference = Callable[[str, str], ClassSpec | None]


def association_mode(
    ewb_class: ClassSpec, association: Association, resolve_reference: ResolveReference
) -> str | None:
    """Return how an association is implemented by an EWB class, if present."""
    matching = [item for item in ewb_class.associations if item.target == association.target]
    if not matching:
        return None
    if any(_has_no_source_fields(item) for item in matching):
        return "1 way"
    return "Bi-directional" if has_reciprocal_association(ewb_class, association.target, resolve_reference) else "1 way"


def relationship_is_bidirectional(
    class_spec: ClassSpec, association: Association, resolve_reference: ResolveReference
) -> bool:
    ewb_class = resolve_reference(class_spec.name, "ewb")
    return (
        ewb_class is not None
        and ewb_class.relative_path[0] == "ewb"
        and association_mode(ewb_class, association, resolve_reference) == "Bi-directional"
    )


def has_reciprocal_association(
    ewb_class: ClassSpec, target_name: str, resolve_reference: ResolveReference
) -> bool:
    target_ewb_class = resolve_reference(target_name, "ewb")
    if target_ewb_class is None or target_ewb_class.relative_path[0] != "ewb":
        return False
    return any(
        association.target == ewb_class.name or association.source == ewb_class.name
        for association in target_ewb_class.associations
    )


def _has_no_source_fields(association: Association) -> bool:
    return all(value in {"", "—"} for value in (
        association.source_cardinality,
        association.source_name,
        association.source_description,
    ))
