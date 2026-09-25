from __future__ import annotations

import unittest
from pathlib import Path

from cim_ple.models import Association, ClassSpec
from cim_ple.relationships import association_mode, relationship_is_bidirectional


def association(
    source: str, target: str, source_cardinality: str = "0..*", source_name: str = "Terminals",
    source_description: str = "The related terminals.",
) -> Association:
    return Association(
        source, target, source_cardinality, "0..1", source_name, target,
        source_description, "The related object.",
    )


def ewb_class(name: str, associations: list[Association]) -> ClassSpec:
    return ClassSpec(name, Path(f"{name}.yaml"), ("ewb", "IEC61970", "Base", name), associations=associations)


class RelationshipDirectionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.requested_association = association("Terminal", "Circuit")
        self.cim_terminal = ClassSpec(
            "Terminal", Path("Terminal.yaml"), ("TC57CIM", "IEC61970", "Base", "Terminal"),
            associations=[self.requested_association],
        )

    @staticmethod
    def resolver(*classes: ClassSpec):
        by_profile_and_name = {(item.relative_path[0], item.name): item for item in classes}
        return lambda name, profile: by_profile_and_name.get((profile, name))

    def test_source_less_ewb_association_is_one_way_even_with_reciprocal_class(self) -> None:
        terminal = ewb_class("Terminal", [association("Terminal", "Circuit", "—", "", "")])
        circuit = ewb_class("Circuit", [association("Circuit", "Terminal")])

        self.assertEqual(association_mode(terminal, self.requested_association, self.resolver(terminal, circuit)), "1 way")

    def test_reciprocal_ewb_association_is_bidirectional(self) -> None:
        terminal = ewb_class("Terminal", [association("Terminal", "Circuit")])
        circuit = ewb_class("Circuit", [association("Circuit", "Terminal")])
        resolver = self.resolver(terminal, circuit)

        self.assertEqual(association_mode(terminal, self.requested_association, resolver), "Bi-directional")
        self.assertTrue(relationship_is_bidirectional(self.cim_terminal, self.requested_association, resolver))

    def test_source_fields_without_a_reciprocal_class_are_one_way(self) -> None:
        terminal = ewb_class("Terminal", [association("Terminal", "Circuit")])

        self.assertEqual(association_mode(terminal, self.requested_association, self.resolver(terminal)), "1 way")

    def test_unrelated_target_association_does_not_count_as_reciprocal(self) -> None:
        terminal = ewb_class("Terminal", [association("Terminal", "Circuit")])
        circuit = ewb_class("Circuit", [association("Circuit", "Other")])

        self.assertEqual(association_mode(terminal, self.requested_association, self.resolver(terminal, circuit)), "1 way")

    def test_absent_ewb_association_has_no_mode(self) -> None:
        terminal = ewb_class("Terminal", [association("Terminal", "Other")])

        self.assertIsNone(association_mode(terminal, self.requested_association, self.resolver(terminal)))


if __name__ == "__main__":
    unittest.main()
