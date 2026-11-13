from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

from cim_ple.spec_diff import (
    ADDED,
    CHANGED,
    REMOVED,
    GitError,
    diff_class_definition,
    diff_spec_commits,
    resolve_ref,
)


def yaml_data(name: str, attributes: list[dict], associations: list[dict]) -> dict:
    return {"name": name, "attributes": attributes, "associations": associations}


class ClassDiffTests(unittest.TestCase):
    def test_reports_added_changed_and_removed_attributes(self) -> None:
        base = yaml_data("AcLineSegment", [{"name": "oldField", "type": "Float"}, {"name": "goneField", "type": "boolean"}], [])
        head = yaml_data("AcLineSegment", [{"name": "oldField", "type": "String"}, {"name": "newField", "type": "boolean"}], [])

        changes = diff_class_definition(base, head, "AcLineSegment")

        self.assertEqual(
            [(item.kind, item.name, item.detail) for item in changes],
            [("added", "newField", "boolean"), ("changed", "oldField", "Float -> String"), ("removed", "goneField", "boolean")],
        )

    def test_associations_display_name_cardinality_and_target(self) -> None:
        association = {"source": "AcLineSegment", "target": "associatedClass", "targetName": "newAssociation", "targetCardinality": "*..1"}
        changes = diff_class_definition(None, yaml_data("AcLineSegment", [], [association]), "AcLineSegment")

        self.assertEqual([(item.kind, item.name, item.detail) for item in changes], [("added", "newAssociation", "*..1 associatedClass")])

    def test_associations_are_read_from_either_direction(self) -> None:
        association = {"source": "otherAssociatedClass", "target": "AcLineSegment", "sourceName": "anotherNewAssociation", "sourceCardinality": "1..*"}
        changes = diff_class_definition(None, yaml_data("AcLineSegment", [], [association]), "AcLineSegment")

        self.assertEqual([(item.kind, item.name, item.detail) for item in changes], [("added", "anotherNewAssociation", "1..* otherAssociatedClass")])

    def test_missing_revisions_compare_as_empty(self) -> None:
        changes = diff_class_definition(None, yaml_data("New", [{"name": "field", "type": "Float"}], []), "New")

        self.assertEqual([(item.kind, item.name) for item in changes], [(ADDED, "field")])
        self.assertEqual([item.kind for item in diff_class_definition(yaml_data("New", [{"name": "field", "type": "Float"}], []), None, "New")], [REMOVED])

    def test_renamed_association_shows_its_previous_name(self) -> None:
        def association(name: str) -> dict:
            return {"source": "AcLineSegment", "target": "Clamp", "targetName": name, "targetCardinality": "0..*"}

        changes = diff_class_definition(
            yaml_data("AcLineSegment", [], [association("clamp")]),
            yaml_data("AcLineSegment", [], [association("Clamp")]),
            "AcLineSegment",
        )

        self.assertEqual([(item.kind, item.name, item.detail) for item in changes], [(CHANGED, "Clamp", "was clamp: 0..* Clamp")])


class GitSpecDiffTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repository = Path(tempfile.mkdtemp())
        subprocess.run(("git", "init", "-q"), cwd=self.repository, check=True, capture_output=True)
        self.write_spec(
            """name: AcLineSegment
attributes:
- name: r
  type: Float
- name: removedField
  type: Float
associations:
- source: AcLineSegment
  target: UnchangedClass
  targetName: keptAssociation
  targetCardinality: 0..1
"""
        )
        self.commit("first")
        self.write_spec(
            """name: AcLineSegment
attributes:
- name: r
  type: String
- name: newField
  type: boolean
associations:
- source: AcLineSegment
  target: UnchangedClass
  targetName: keptAssociation
  targetCardinality: 0..1
- source: AcLineSegment
  target: associatedClass
  targetName: newAssociation
  targetCardinality: "*..1"
"""
        )
        self.commit("second")

    def write_spec(self, content: str) -> None:
        path = self.repository / "spec" / "ewb" / "AcLineSegment.yaml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def commit(self, message: str) -> None:
        subprocess.run(("git", "add", "."), cwd=self.repository, check=True, capture_output=True)
        subprocess.run(
            ("git", "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-qm", message),
            cwd=self.repository,
            check=True,
            capture_output=True,
        )

    def test_diff_spec_commits_compares_ewb_files_between_commits(self) -> None:
        diff = diff_spec_commits(self.repository, "HEAD~1")

        self.assertEqual([item.name for item in diff.classes], ["AcLineSegment"])
        self.assertEqual(
            [(item.kind, item.name, item.detail) for item in diff.classes[0].changes],
            [
                (ADDED, "newField", "boolean"),
                (CHANGED, "r", "Float -> String"),
                (REMOVED, "removedField", "Float"),
                (ADDED, "newAssociation", "*..1 associatedClass"),
            ],
        )
        self.assertEqual(diff.base, "HEAD~1")
        self.assertRegex(diff.head, r"^[0-9a-f]+$")

    def test_unchanged_classes_are_not_reported(self) -> None:
        path = self.repository / "spec" / "ewb" / "Other.yaml"
        path.write_text("name: Other\n", encoding="utf-8")
        self.commit("add other")

        self.assertTrue(diff_spec_commits(self.repository, "HEAD~1").is_empty)

    def test_unknown_ref_raises_git_error(self) -> None:
        with self.assertRaisesRegex(GitError, "Unknown commit: nope"):
            resolve_ref(self.repository, "nope")


if __name__ == "__main__":
    unittest.main()
