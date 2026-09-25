from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from cim_ple.spec_model import SpecModel


class LoadYamlTests(unittest.TestCase):
    def setUp(self) -> None:
        # Avoid loading a complete spec tree: these tests exercise only _load_yaml.
        self.model = SpecModel.__new__(SpecModel)
        self.model.errors = []
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary_directory.name)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def write_yaml(self, name: str, contents: str) -> Path:
        path = self.directory / name
        path.write_text(contents, encoding="utf-8")
        return path

    def test_returns_mapping_from_valid_yaml(self) -> None:
        path = self.write_yaml("Terminal.yaml", "name: Terminal\nattributes:\n  - name: phases\n")

        result = self.model._load_yaml(path)

        self.assertEqual(result, {"name": "Terminal", "attributes": [{"name": "phases"}]})
        self.assertEqual(self.model.errors, [])

    def test_rejects_valid_yaml_with_non_mapping_root(self) -> None:
        path = self.write_yaml("classes.yaml", "- Terminal\n- Circuit\n")

        self.assertIsNone(self.model._load_yaml(path))
        self.assertEqual(self.model.errors, [])

    def test_records_error_for_malformed_yaml(self) -> None:
        path = self.write_yaml("broken.yaml", "name: Terminal\n  description: bad indentation\n")

        self.assertIsNone(self.model._load_yaml(path))
        self.assertEqual(len(self.model.errors), 1)
        self.assertIn(str(path), self.model.errors[0])


class LoadClassYamlTests(unittest.TestCase):
    def test_loads_class_attributes_and_association_shapes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            spec_directory = Path(temporary_directory)
            class_path = spec_directory / "TC57CIM" / "IEC61970" / "Base" / "Core" / "Terminal.yaml"
            class_path.parent.mkdir(parents=True)
            class_path.write_text(
                """\
name: Terminal
description: An electrical connection point.
ancestors:
  - ACDCTerminal
descendants:
  - DCTerminal
attributes:
  - name: phases
    type: PhaseCode
    nullable: false
    description: The terminal phases.
associations:
  - source: Terminal
    target: ConnectivityNode
    targetCardinality: 0..1
    targetName: ConnectivityNode
    targetDescription: The connected node.
  - source: Terminal
    target: ConductingEquipment
    sourceCardinality: 0..*
    sourceName: Terminals
    sourceDescription: The equipment terminals.
    targetCardinality: 0..1
    targetName: ConductingEquipment
    targetDescription: The terminal equipment.
""",
                encoding="utf-8",
            )

            model = SpecModel(spec_directory)

        terminal = model.classes[("TC57CIM", "IEC61970", "Base", "Core", "Terminal")]
        self.assertEqual(terminal.name, "Terminal")
        self.assertEqual(terminal.relative_path, ("TC57CIM", "IEC61970", "Base", "Core", "Terminal"))
        self.assertEqual(terminal.ancestors, ["ACDCTerminal"])
        self.assertEqual(terminal.descendants, ["DCTerminal"])
        self.assertEqual(terminal.attributes[0].name, "phases")
        self.assertEqual(terminal.attributes[0].nullable, "No")

        one_way, bidirectional = terminal.associations
        self.assertEqual(one_way.target, "ConnectivityNode")
        self.assertEqual(one_way.source_cardinality, "—")
        self.assertEqual(one_way.source_name, "")
        self.assertEqual(one_way.source_description, "")
        self.assertEqual(one_way.target_cardinality, "0..1")
        self.assertEqual(bidirectional.target, "ConductingEquipment")
        self.assertEqual(bidirectional.source_cardinality, "0..*")
        self.assertEqual(bidirectional.source_name, "Terminals")
        self.assertEqual(bidirectional.source_description, "The equipment terminals.")


class SpecModelBehaviorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.spec_directory = Path(self.temporary_directory.name)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def write_yaml(self, relative_path: str, contents: str) -> None:
        path = self.spec_directory / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding="utf-8")

    def test_loads_metadata_skips_unnamed_classes_and_supports_descendents(self) -> None:
        self.write_yaml(
            "TC57CIM/IEC61970/Base/__metadata.yaml",
            "name: Base package\ndescription: Foundational CIM types.\n",
        )
        self.write_yaml(
            "TC57CIM/IEC61970/Base/Core/Terminal.yaml",
            "name: Terminal\ndescendents:\n  - DCTerminal\n",
        )
        self.write_yaml("TC57CIM/IEC61970/Base/Core/ignored.yaml", "description: No class name.\n")

        model = SpecModel(self.spec_directory)

        self.assertEqual(model.class_count, 1)
        terminal = model.classes[("TC57CIM", "IEC61970", "Base", "Core", "Terminal")]
        self.assertEqual(terminal.descendants, ["DCTerminal"])
        package = model.packages[("TC57CIM", "IEC61970", "Base")]
        self.assertEqual(package.name, "Base package")
        self.assertEqual(package.description, "Foundational CIM types.")

    def test_orders_profiles_and_resolves_references_in_requested_profile(self) -> None:
        self.write_yaml("ewb/IEC61970/Base/Core/Terminal.yaml", "name: Terminal\n")
        self.write_yaml("TC57CIM/IEC61970/Base/Core/Terminal.yaml", "name: Terminal\n")
        self.write_yaml("TC57CIM/IEC61970/Base/Core/ACDCTerminal.yaml", "name: ACDCTerminal\n")

        model = SpecModel(self.spec_directory)

        self.assertEqual([entry.label for entry in model.tree.children], ["EWB Profile", "CIM100 Data Model"])
        self.assertEqual(model.resolve_reference("Terminal", "ewb").relative_path[0], "ewb")
        self.assertEqual(model.resolve_reference("Terminal", "TC57CIM").relative_path[0], "TC57CIM")
        self.assertIsNone(model.resolve_reference("Unknown", "TC57CIM"))
        self.assertEqual(model.find_class("terminal").name, "Terminal")
        self.assertEqual(len(model.class_choices), 3)


if __name__ == "__main__":
    unittest.main()
