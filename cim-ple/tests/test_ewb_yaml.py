from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import yaml

from cim_ple.ewb_yaml import write_ewb_spec
from cim_ple.models import Association, Attribute, ClassMarks, ClassSpec


class WriteEwbSpecTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.spec_directory = Path(self.temporary_directory.name)
        self.class_spec = ClassSpec(
            name="Terminal",
            path=Path("Terminal.yaml"),
            relative_path=("TC57CIM", "IEC61970", "Base", "Core", "Terminal"),
            attributes=[Attribute("phases", "PhaseCode", "No", "The terminal phases.")],
            associations=[
                Association(
                    "Terminal", "ConnectivityNode", "0..*", "0..1", "Terminals", "ConnectivityNode",
                    "The connected terminals.", "The connected node.",
                )
            ],
        )
        self.destination = self.spec_directory / "ewb" / "IEC61970" / "Base" / "Core" / "Terminal.yaml"
        self.destination.parent.mkdir(parents=True)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def write_existing_association(self, source_fields: str) -> None:
        self.destination.write_text(
            """# Preserve this comment exactly.
name: Terminal
attributes:
- name: oldField
  type: String
associations:
- source: Terminal
  target: ConnectivityNode
""" + source_fields + """  targetCardinality: 0..1
  targetName: ConnectivityNode
  targetDescription: The connected node.
""",
            encoding="utf-8",
        )

    def test_writes_one_way_association_without_source_fields(self) -> None:
        self.write_existing_association(
            "  sourceCardinality: 0..*\n  sourceName: Terminals\n  sourceDescription: The connected terminals.\n"
        )
        marks = ClassMarks(associations={0: "1 way"}, changed_associations={0})

        self.assertTrue(write_ewb_spec(self.spec_directory, self.class_spec, marks))

        contents = self.destination.read_text(encoding="utf-8")
        association = yaml.safe_load(contents)["associations"][0]
        self.assertIn("# Preserve this comment exactly.\n", contents)
        self.assertNotIn("sourceCardinality", association)
        self.assertNotIn("sourceName", association)
        self.assertNotIn("sourceDescription", association)
        self.assertEqual(association["target"], "ConnectivityNode")

    def test_writes_bidirectional_association_with_source_fields(self) -> None:
        self.write_existing_association("")
        marks = ClassMarks(associations={0: "Bi-directional"}, changed_associations={0})

        self.assertTrue(write_ewb_spec(self.spec_directory, self.class_spec, marks))

        association = yaml.safe_load(self.destination.read_text(encoding="utf-8"))["associations"][0]
        self.assertEqual(association["sourceCardinality"], "0..*")
        self.assertEqual(association["sourceName"], "Terminals")
        self.assertEqual(association["sourceDescription"], "The connected terminals.")

    def test_writes_changed_attribute_without_reformatting_existing_content(self) -> None:
        self.destination.write_text("# Preserve this comment exactly.\nname: Terminal\n", encoding="utf-8")
        marks = ClassMarks(attributes={"phases"}, changed_attributes={"phases"})

        self.assertTrue(write_ewb_spec(self.spec_directory, self.class_spec, marks))

        contents = self.destination.read_text(encoding="utf-8")
        self.assertTrue(contents.startswith("# Preserve this comment exactly.\nname: Terminal\n"))
        self.assertEqual(yaml.safe_load(contents)["attributes"], [{"name": "phases", "type": "PhaseCode", "nullable": False, "description": "The terminal phases."}])

    def test_creates_new_file_with_selected_fields_and_class_context(self) -> None:
        self.class_spec.description = "An electrical connection point."
        self.class_spec.ancestors = ["ACDCTerminal"]
        marks = ClassMarks(
            attributes={"phases"},
            associations={0: "1 way"},
            changed_attributes={"phases"},
            changed_associations={0},
        )

        self.assertTrue(write_ewb_spec(self.spec_directory, self.class_spec, marks))

        data = yaml.safe_load(self.destination.read_text(encoding="utf-8"))
        self.assertEqual(data["name"], "Terminal")
        self.assertEqual(data["description"], "An electrical connection point.")
        self.assertEqual(data["ancestors"], ["ACDCTerminal"])
        self.assertEqual(data["attributes"][0]["name"], "phases")
        self.assertNotIn("sourceCardinality", data["associations"][0])

    def test_removes_unmarked_existing_attribute_and_association(self) -> None:
        self.destination.write_text(
            """name: Terminal
attributes:
- name: phases
  type: PhaseCode
- name: keepMe
  type: String
associations:
- source: Terminal
  target: ConnectivityNode
  targetCardinality: 0..1
- source: Terminal
  target: KeepTarget
  targetCardinality: 0..*
""",
            encoding="utf-8",
        )
        marks = ClassMarks(changed_attributes={"phases"}, changed_associations={0})

        self.assertTrue(write_ewb_spec(self.spec_directory, self.class_spec, marks))

        data = yaml.safe_load(self.destination.read_text(encoding="utf-8"))
        self.assertEqual(data["attributes"], [{"name": "keepMe", "type": "String"}])
        self.assertEqual(data["associations"], [{"source": "Terminal", "target": "KeepTarget", "targetCardinality": "0..*"}])

    def test_writes_and_removes_dirty_zbex_fields(self) -> None:
        self.destination.write_text(
            """name: Terminal
attributes:
- name: obsoleteExtension
  type: String
associations:
- source: Terminal
  target: ObsoleteTarget
  targetCardinality: 0..*
""",
            encoding="utf-8",
        )
        marks = ClassMarks(
            zbex_attributes=[
                {"name": "newExtension", "type": "Integer", "description": "A new field.", "dirty": "true", "marked": "true"},
                {"name": "obsoleteExtension", "dirty": "true", "marked": "false"},
            ],
            zbex_associations=[
                {"target": "NewTarget", "name": "New terminals", "cardinality": "1..*", "dirty": "true", "marked": "true"},
                {"target": "ObsoleteTarget", "dirty": "true", "marked": "false"},
            ],
        )

        self.assertTrue(write_ewb_spec(self.spec_directory, self.class_spec, marks))

        data = yaml.safe_load(self.destination.read_text(encoding="utf-8"))
        self.assertEqual(data["attributes"], [{"name": "newExtension", "type": "Integer", "description": "A new field."}])
        self.assertEqual(
            data["associations"],
            [{"source": "Terminal", "target": "NewTarget", "targetCardinality": "1..*", "targetName": "New terminals"}],
        )

    def test_does_not_write_existing_file_without_pending_changes(self) -> None:
        original = "# This must not change.\nname: Terminal\n"
        self.destination.write_text(original, encoding="utf-8")

        self.assertFalse(write_ewb_spec(self.spec_directory, self.class_spec, ClassMarks()))

        self.assertEqual(self.destination.read_text(encoding="utf-8"), original)


if __name__ == "__main__":
    unittest.main()
