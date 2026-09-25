#!/usr/bin/env python3
"""Interactive terminal browser for the CIM YAML specification."""

from __future__ import annotations

import argparse
from pathlib import Path
from textual.app import ComposeResult
from textual.containers import Horizontal, Vertical
from textual.screen import ModalScreen
from textual.widgets import Button, Static

class EmptyMarksConfirm(ModalScreen[bool]):
    """Ask whether a saved class with no selected fields should be removed."""

    BINDINGS = [("escape", "keep", "Keep"), ("q", "keep", "Keep")]

    CSS = """
    EmptyMarksConfirm {
        align: center middle;
        background: #000000aa;
    }

    #empty-marks-dialog {
        width: 64;
        height: auto;
        background: #161b22;
        border: round #58a6ff;
        padding: 1 2;
    }

    #empty-marks-actions {
        height: auto;
        margin-top: 1;
        align-horizontal: right;
    }
    """

    def __init__(self, class_name: str) -> None:
        super().__init__()
        self.class_name = class_name

    def compose(self) -> ComposeResult:
        with Vertical(id="empty-marks-dialog"):
            yield Static(f"{self.class_name} has no marked fields. Remove it from saved classes?")
            with Horizontal(id="empty-marks-actions"):
                yield Button("Keep", id="keep-empty-marks")
                yield Button("Remove", id="remove-empty-marks", variant="error")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        self.dismiss(event.button.id == "remove-empty-marks")

    def action_keep(self) -> None:
        self.dismiss(False)


class FlushConfirm(ModalScreen[bool]):
    """Require explicit confirmation before changing specification files."""

    BINDINGS = [("escape", "cancel", "Cancel"), ("q", "cancel", "Cancel")]
    CSS = EmptyMarksConfirm.CSS

    def __init__(self, count: int) -> None:
        super().__init__()
        self.count = count

    def compose(self) -> ComposeResult:
        with Vertical(id="empty-marks-dialog"):
            yield Static(
                f"Write {self.count} saved CIM100 class specification(s) to spec/ewb?\n\n"
                "Existing matching files will be replaced."
            )
            with Horizontal(id="empty-marks-actions"):
                yield Button("Cancel", id="cancel-flush")
                yield Button("Write files", id="confirm-flush", variant="error")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        self.dismiss(event.button.id == "confirm-flush")

    def action_cancel(self) -> None:
        self.dismiss(False)


def default_spec_directory() -> Path:
    """Locate the repository's spec directory for source and installed runs."""

    candidates = (Path.cwd(), *Path(__file__).resolve().parents)
    for directory in candidates:
        spec_directory = directory / "spec"
        if spec_directory.is_dir():
            return spec_directory
    return Path.cwd() / "spec"


def main() -> None:
    from cim_ple.UI.cim_browser import CimBrowser

    parser = argparse.ArgumentParser(description="Browse a CIM specification in the terminal")
    parser.add_argument(
        "--spec",
        type=Path,
        default=default_spec_directory(),
        help="directory containing the CIM YAML files (default: discovered ./spec)",
    )
    args = parser.parse_args()
    CimBrowser(args.spec.expanduser().resolve()).run()


if __name__ == "__main__":
    main()
