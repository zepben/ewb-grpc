from __future__ import annotations

from textual.widgets import Button, Checkbox, Static


from textual.app import ComposeResult
from textual.containers import Horizontal, Vertical, VerticalScroll
from textual.screen import ModalScreen

from cim_ple.models import ClassMarks, ClassSpec
from cim_ple.UI.add_zbex_field import AddZbexField


class MarkEditor(ModalScreen[ClassMarks | None]):
    """Modal editor for selecting attributes and association directions."""

    BINDINGS = [
        ("escape", "cancel", "Cancel"),
        ("q", "cancel", "Cancel"),
        ("ctrl+enter", "save", "Save"),
    ]

    CSS = """
    MarkEditor {
        align: center middle;
        background: #000000aa;
    }

    #mark-dialog {
        width: 90%;
        height: 85%;
        max-width: 120;
        background: #161b22;
        border: round #58a6ff;
        padding: 1 2;
        overflow: hidden;
    }

    #mark-fields {
        height: 1fr;
        min-height: 0;
        margin: 1 0;
        overflow-y: auto;
    }

    #attribute-fields, #association-fields {
        height: auto;
    }

    .mark-heading {
        color: #58a6ff;
        text-style: bold;
        margin-top: 1;
    }

    .association-row {
        height: auto;
        margin: 0 0 1 0;
    }

    .association-name {
        color: #c9d1d9;
    }

    .association-mode {
        margin-top: 1;
    }

    #mark-actions {
        height: auto;
        align-horizontal: right;
    }
    """

    MODES = ("Unmarked", "1 way", "Bi-directional")

    def __init__(self, class_spec: ClassSpec, marks: ClassMarks, class_choices: dict[str, ClassSpec]) -> None:
        super().__init__()
        self.class_spec = class_spec
        self.marks = marks.copy()
        self.class_choices = class_choices

    def compose(self) -> ComposeResult:
        with Vertical(id="mark-dialog"):
            yield Static(f"MARK FIELDS · {self.class_spec.name}")
            with VerticalScroll(id="mark-fields"):
                yield Static("ATTRIBUTES", classes="mark-heading")
                with Vertical(id="attribute-fields"):
                    for index, attribute in enumerate(self.class_spec.attributes):
                        yield Checkbox(
                            f"{attribute.name}  ({attribute.type})",
                            value=attribute.name in self.marks.attributes,
                            id=f"attribute-{index}",
                        )
                    for index, field in enumerate(self.marks.zbex_attributes):
                        yield Checkbox(
                            f"ZBEX · {field['name']}  ({field['type']})",
                            value=field.get("marked", "true") == "true",
                            id=f"zbex-attribute-{index}",
                        )
                    if not self.class_spec.attributes and not self.marks.zbex_attributes:
                        yield Static("No attributes")
                yield Button("Add ZBEX attribute", id="add-zbex-attribute")

                yield Static("ASSOCIATIONS", classes="mark-heading")
                with Vertical(id="association-fields"):
                    for index, association in enumerate(self.class_spec.associations):
                        mode = self.marks.associations.get(index, "Unmarked")
                        with Vertical(classes="association-row"):
                            yield Static(
                                f"{association.source} → {association.target}",
                                classes="association-name",
                            )
                            yield Button(mode, id=f"association-{index}", classes="association-mode")
                    for index, field in enumerate(self.marks.zbex_associations):
                        with Vertical(classes="association-row"):
                            yield Static(
                                f"ZBEX · {field['name']} → {field['target']} ({field.get('cardinality', '—')})",
                                classes="association-name",
                            )
                            yield Button(
                                field.get("direction", "1 way")
                                if field.get("marked", "true") == "true"
                                else "Unmarked",
                                id=f"zbex-association-{index}",
                                classes="association-mode",
                            )
                    if not self.class_spec.associations and not self.marks.zbex_associations:
                        yield Static("No associations")
                yield Button("Add ZBEX association", id="add-zbex-association")
            with Horizontal(id="mark-actions"):
                yield Button("Cancel", id="cancel-marks")
                yield Button("Save", id="save-marks", variant="primary")

    def on_checkbox_changed(self, event: Checkbox.Changed) -> None:
        checkbox_id = event.checkbox.id or ""
        if checkbox_id.startswith("zbex-attribute-"):
            field = self.marks.zbex_attributes[int(checkbox_id.removeprefix("zbex-attribute-"))]
            field["marked"] = "true" if event.value else "false"
            field["dirty"] = "true"
        else:
            index = int(checkbox_id.removeprefix("attribute-"))
            attribute = self.class_spec.attributes[index]
            self.marks.changed_attributes.add(attribute.name)
            if event.value:
                self.marks.attributes.add(attribute.name)
            else:
                self.marks.attributes.discard(attribute.name)

    def on_button_pressed(self, event: Button.Pressed) -> None:
        button_id = event.button.id or ""
        if button_id == "cancel-marks":
            self.dismiss(None)
        elif button_id == "save-marks":
            self.dismiss(self.marks)
        elif button_id == "add-zbex-attribute":
            self.app.push_screen(AddZbexField("attribute", self.class_choices), self._add_zbex_field)
        elif button_id == "add-zbex-association":
            self.app.push_screen(AddZbexField("association", self.class_choices), self._add_zbex_field)
        elif button_id.startswith("zbex-association-"):
            field = self.marks.zbex_associations[int(button_id.removeprefix("zbex-association-"))]
            current = field.get("direction", "1 way") if field.get("marked", "true") == "true" else "Unmarked"
            next_mode = self.MODES[(self.MODES.index(current) + 1) % len(self.MODES)]
            field["marked"] = "false" if next_mode == "Unmarked" else "true"
            field["dirty"] = "true"
            if next_mode != "Unmarked":
                field["direction"] = next_mode
            event.button.label = next_mode
        elif button_id.startswith("association-"):
            index = int(button_id.removeprefix("association-"))
            current = self.marks.associations.get(index, "Unmarked")
            next_mode = self.MODES[(self.MODES.index(current) + 1) % len(self.MODES)]
            if next_mode == "Unmarked":
                self.marks.associations.pop(index, None)
            else:
                self.marks.associations[index] = next_mode
            self.marks.changed_associations.add(index)
            event.button.label = next_mode

    def action_cancel(self) -> None:
        self.dismiss(None)

    def action_save(self) -> None:
        self.dismiss(self.marks)

    def _add_zbex_field(self, field: dict[str, str] | None) -> None:
        if field is None:
            return
        collection = self.marks.zbex_attributes if field["kind"] == "attribute" else self.marks.zbex_associations
        field["marked"] = "true"
        field["dirty"] = "true"
        collection.append(field)
        field_id = f"zbex-{field['kind']}-{len(collection) - 1}"
        if field["kind"] == "attribute":
            self.query_one("#attribute-fields", Vertical).mount(
                Checkbox(f"ZBEX · {field['name']}  ({field['type']})", value=True, id=field_id)
            )
        else:
            self.query_one("#association-fields", Vertical).mount(
                Vertical(
                    Static(
                        f"ZBEX · {field['name']} → {field['target']} ({field.get('cardinality', '—')})",
                        classes="association-name",
                    ),
                    Button(field.get("direction", "1 way"), id=field_id, classes="association-mode"),
                    classes="association-row",
                )
            )
