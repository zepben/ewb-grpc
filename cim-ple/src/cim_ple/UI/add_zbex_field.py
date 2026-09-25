from __future__ import annotations

from textual.app import ComposeResult
from textual.containers import Horizontal, Vertical
from textual.screen import ModalScreen
from textual.suggester import SuggestFromList
from textual.widgets import Button, Input, Static

from cim_ple.models import ClassSpec


class AddZbexField(ModalScreen[dict[str, str] | None]):
    """Form for adding an in-memory ZBEX extension field."""

    BINDINGS = [("escape", "cancel", "Cancel"), ("q", "cancel", "Cancel")]

    CSS = """
    AddZbexField { align: center middle; background: #000000aa; }
    #zbex-form { width: 72; height: auto; background: #161b22; border: round #58a6ff; padding: 1 2; }
    #zbex-form Input { margin-top: 1; }
    #zbex-form-actions { height: auto; margin-top: 1; align-horizontal: right; }
    """

    def __init__(self, kind: str, choices: dict[str, ClassSpec]) -> None:
        super().__init__()
        self.kind = kind
        self.choices = choices

    def compose(self) -> ComposeResult:
        reference_label = "Type class" if self.kind == "attribute" else "Target class"
        with Vertical(id="zbex-form"):
            yield Static(f"ADD ZBEX {self.kind.upper()}")
            yield Input(placeholder="Name", id="zbex-name")
            yield Input(
                placeholder=reference_label,
                suggester=SuggestFromList(self.choices, case_sensitive=False),
                id="zbex-reference",
            )
            if self.kind == "association":
                yield Input(value="0..*", placeholder="Cardinality", id="zbex-cardinality")
                yield Input(value="1 way", placeholder="Direction", id="zbex-direction")
            yield Input(placeholder="Description", id="zbex-description")
            with Horizontal(id="zbex-form-actions"):
                yield Button("Cancel", id="cancel-zbex")
                yield Button("Add", id="save-zbex", variant="primary")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id == "cancel-zbex":
            self.dismiss(None)
            return
        name = self.query_one("#zbex-name", Input).value.strip()
        reference = self.query_one("#zbex-reference", Input).value.strip()
        class_spec = self.choices.get(reference)
        if not name or class_spec is None:
            self.notify("Provide a name and choose a class from autocomplete", severity="warning")
            return
        field = {
            "kind": self.kind,
            "name": name,
            "description": self.query_one("#zbex-description", Input).value.strip(),
        }
        if self.kind == "attribute":
            field["type"] = class_spec.name
        else:
            field["target"] = class_spec.name
            field["cardinality"] = self.query_one("#zbex-cardinality", Input).value.strip()
            field["direction"] = self.query_one("#zbex-direction", Input).value.strip()
        self.dismiss(field)

    def action_cancel(self) -> None:
        self.dismiss(None)
