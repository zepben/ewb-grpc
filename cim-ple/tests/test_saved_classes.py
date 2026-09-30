from __future__ import annotations

import unittest
from pathlib import Path

from cim_ple.UI.cim_browser import CimBrowser
from cim_ple.models import ClassMarks, ClassSpec


def class_spec(name: str, profile: str, ancestors: list[str] | None = None) -> ClassSpec:
    return ClassSpec(name, Path(f"{name}.yaml"), (profile, "IEC61970", "Base", name), ancestors=ancestors or [])


class ModelStub:
    def __init__(self, classes: list[ClassSpec]) -> None:
        self.classes = {(item.relative_path[0], item.name): item for item in classes}

    def resolve_reference(self, name: str, profile: str) -> ClassSpec | None:
        return self.classes.get((profile, name))


class SavedClassAncestorTests(unittest.TestCase):
    def test_adds_missing_ancestors_recursively_and_skips_implemented_ancestors(self) -> None:
        root = class_spec("IdentifiedObject", "TC57CIM")
        middle = class_spec("PowerSystemResource", "TC57CIM", ["IdentifiedObject"])
        implemented = class_spec("EquipmentContainer", "TC57CIM")
        implemented_ewb = class_spec("EquipmentContainer", "ewb")
        child = class_spec("Bay", "TC57CIM", ["PowerSystemResource", "EquipmentContainer"])
        browser = CimBrowser.__new__(CimBrowser)
        browser.model = ModelStub([root, middle, implemented, implemented_ewb, child])
        browser._saved_classes = []
        browser._saved_class_paths = set()
        browser._class_marks = {}

        self.assertTrue(browser._add_saved_class(child))

        self.assertEqual([item.name for item in browser._saved_classes], ["IdentifiedObject", "PowerSystemResource", "Bay"])

    def test_stops_recursive_ancestor_cycles(self) -> None:
        first = class_spec("First", "TC57CIM", ["Second"])
        second = class_spec("Second", "TC57CIM", ["First"])
        browser = CimBrowser.__new__(CimBrowser)
        browser.model = ModelStub([first, second])
        browser._saved_classes = []
        browser._saved_class_paths = set()
        browser._class_marks = {}

        self.assertTrue(browser._add_saved_class(first))

        self.assertEqual([item.name for item in browser._saved_classes], ["Second", "First"])

    def test_marked_zbex_association_adds_its_cim_target_and_missing_ancestors(self) -> None:
        root = class_spec("IdentifiedObject", "TC57CIM")
        target = class_spec("ExtensionTarget", "TC57CIM", ["IdentifiedObject"])
        source = class_spec("ExtensionSource", "TC57CIM")
        browser = CimBrowser.__new__(CimBrowser)
        browser.model = ModelStub([root, target, source])
        browser._saved_classes = [source]
        browser._saved_class_paths = {source.relative_path}
        browser._class_marks = {}
        browser._render_saved_tree = lambda: None
        marks = ClassMarks(
            zbex_associations=[{"target": "ExtensionTarget", "marked": "true", "dirty": "true"}]
        )

        browser._save_class_marks(source, marks)

        self.assertEqual(
            [item.name for item in browser._saved_classes],
            ["ExtensionSource", "IdentifiedObject", "ExtensionTarget"],
        )


if __name__ == "__main__":
    unittest.main()
