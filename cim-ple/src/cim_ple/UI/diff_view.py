from __future__ import annotations

from rich.text import Text
from textual.app import ComposeResult
from textual.containers import Vertical, VerticalScroll
from textual.screen import ModalScreen
from textual.widgets import Input, Static

from cim_ple.spec_diff import ADDED, CHANGED, REMOVED, SpecDiff

CHANGE_STYLES = {ADDED: "#3fb950", CHANGED: "#d29922", REMOVED: "#f85149"}
CHANGE_PREFIXES = {ADDED: "+", CHANGED: "~", REMOVED: "-"}


class CommitPrompt(ModalScreen[str | None]):
    """Ask which commit, branch, or tag to compare the current specification with."""

    BINDINGS = [("escape", "cancel", "Cancel")]

    CSS = """
    CommitPrompt {
        align: center middle;
        background: #000000aa;
    }

    #commit-form {
        width: 68;
        height: auto;
        background: #161b22;
        border: round #58a6ff;
        padding: 1 2;
    }

    #commit-form Input {
        margin-top: 1;
        background: #0d1117;
        color: #c9d1d9;
        border: round #30363d;
    }
    """

    def __init__(self, default_ref: str = "main") -> None:
        super().__init__()
        self.default_ref = default_ref

    def compose(self) -> ComposeResult:
        with Vertical(id="commit-form"):
            yield Static(f"DIFF SPEC · CURRENT COMMIT <- ANOTHER COMMIT (default: {self.default_ref})")
            yield Input(placeholder=f"commit, branch or tag (default: {self.default_ref})", id="commit-ref")

    def on_mount(self) -> None:
        self.query_one("#commit-ref", Input).focus()

    def on_input_submitted(self, event: Input.Submitted) -> None:
        self.dismiss(event.value.strip() or self.default_ref)

    def action_cancel(self) -> None:
        self.dismiss(None)


class DiffOverlay(ModalScreen[None]):
    """Modal panel briefly presenting which class fields changed between two commits."""

    BINDINGS = [("escape", "close", "Close"), ("q", "close", "Close")]

    CSS = """
    DiffOverlay {
        align: center middle;
        background: #000000aa;
    }

    #diff-dialog {
        width: 90%;
        height: 85%;
        max-width: 110;
        background: #161b22;
        border: round #58a6ff;
        padding: 1 2;
        overflow: hidden;
    }

    #diff-heading {
        color: #58a6ff;
        text-style: bold;
    }

    #diff-body {
        height: 1fr;
        min-height: 0;
        margin-top: 1;
        overflow-y: auto;
    }

    #diff-hint {
        height: auto;
        margin-top: 1;
        color: #8b949e;
    }
    """

    def __init__(self, diff: SpecDiff) -> None:
        super().__init__()
        self.diff = diff

    def compose(self) -> ComposeResult:
        with Vertical(id="diff-dialog"):
            yield Static(
                Text.assemble(
                    (f"{self.diff.base} <- ", "bold #e6edf3"),
                    (self.diff.head, "bold #58a6ff"),
                ),
                id="diff-heading",
            )
            with VerticalScroll(id="diff-body"):
                for class_diff in self.diff.classes:
                    yield Static(Text(class_diff.name, style="bold #c9d1d9"))
                    for change in class_diff.changes:
                        line = Text()
                        line.append(f"    {CHANGE_PREFIXES[change.kind]} ", style=CHANGE_STYLES[change.kind])
                        line.append(change.name, style="#c9d1d9")
                        line.append(f": {change.detail}", style=CHANGE_STYLES[change.kind])
                        yield Static(line)
            yield Static("q or Esc to close", id="diff-hint")

    def action_close(self) -> None:
        self.dismiss(None)
