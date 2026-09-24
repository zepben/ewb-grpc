#!/usr/bin/env python3
"""Interactive terminal browser for the CIM YAML specification."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import yaml
from rich.text import Text
from textual.app import App, ComposeResult
from textual.containers import Horizontal, Vertical
from textual.suggester import SuggestFromList
from textual.widgets import Footer, Header, Input, Markdown, Static, Tree


def _text(value: Any) -> str:
    """Convert YAML values to readable text without displaying ``None``."""

    if value is None:
        return ""
    if isinstance(value, (list, tuple)):
        return ", ".join(_text(item) for item in value)
    return str(value)


def _clean_description(value: Any) -> str:
    """Make a description suitable for one Markdown table cell."""

    description = re.sub(r"\s+", " ", _text(value)).strip()
    return description.replace("|", r"\|") or "—"


def _as_list(value: Any) -> list[dict[str, Any]]:
    if not value:
        return []
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict)]


@dataclass(frozen=True)
class Attribute:
    name: str
    type: str
    nullable: str
    description: str


@dataclass(frozen=True)
class Association:
    source: str
    target: str
    source_cardinality: str
    target_cardinality: str
    source_name: str
    target_name: str
    source_description: str
    target_description: str


@dataclass
class ClassSpec:
    name: str
    path: Path
    relative_path: tuple[str, ...]
    description: str = ""
    attributes: list[Attribute] = field(default_factory=list)
    ancestors: list[str] = field(default_factory=list)
    descendants: list[str] = field(default_factory=list)
    associations: list[Association] = field(default_factory=list)


@dataclass
class PackageInfo:
    name: str
    path: tuple[str, ...]
    description: str = ""
    classes: list[ClassSpec] = field(default_factory=list)


@dataclass
class TreeEntry:
    label: str
    path: tuple[str, ...]
    kind: str
    item: ClassSpec | PackageInfo | None = None
    children: list["TreeEntry"] = field(default_factory=list)


class SpecModel:
    """Load class and package information from the repository's YAML files."""

    PROFILE_NAMES = {
        "ewb": "EWB Profile",
        "TC57CIM": "CIM100 Data Model",
    }

    def __init__(self, spec_directory: Path) -> None:
        self.spec_directory = spec_directory
        self.classes: dict[tuple[str, ...], ClassSpec] = {}
        self.packages: dict[tuple[str, ...], PackageInfo] = {}
        self.errors: list[str] = []
        self._load()
        self.tree = self._build_tree()
        self.class_choices = self._build_class_choices()

    @property
    def class_count(self) -> int:
        return len(self.classes)

    @property
    def package_count(self) -> int:
        return len(self.packages)

    def _load_yaml(self, path: Path) -> dict[str, Any] | None:
        try:
            value = yaml.safe_load(path.read_text(encoding="utf-8"))
        except (OSError, yaml.YAMLError) as error:
            self.errors.append(f"{path}: {error}")
            return None
        return value if isinstance(value, dict) else None

    def _load(self) -> None:
        if not self.spec_directory.is_dir():
            self.errors.append(f"Spec directory does not exist: {self.spec_directory}")
            return

        metadata: dict[tuple[str, ...], dict[str, Any]] = {}
        for path in sorted(self.spec_directory.rglob("__metadata.yaml")):
            data = self._load_yaml(path)
            if data is not None:
                metadata[tuple(path.parent.relative_to(self.spec_directory).parts)] = data

        for path in sorted(self.spec_directory.rglob("*.yaml")):
            if path.name == "__metadata.yaml":
                continue
            data = self._load_yaml(path)
            if not data or not data.get("name"):
                continue

            relative = path.relative_to(self.spec_directory)
            parts = relative.parts
            class_path = tuple(parts[:-1] + (path.stem,))
            self.classes[class_path] = self._class_from_yaml(path, class_path, data)

        # Build package records from metadata and from directories that contain
        # classes. Metadata is optional, so the browser still works on partial specs.
        for class_spec in self.classes.values():
            for depth in range(1, len(class_spec.relative_path)):
                package_path = class_spec.relative_path[:depth]
                package = self.packages.setdefault(
                    package_path,
                    PackageInfo(name=package_path[-1], path=package_path),
                )
                if class_spec not in package.classes:
                    package.classes.append(class_spec)

        for package_path, data in metadata.items():
            if not package_path:
                continue
            package = self.packages.setdefault(
                package_path,
                PackageInfo(name=package_path[-1], path=package_path),
            )
            package.description = _text(data.get("description"))
            package.name = _text(data.get("name")) or package.name

    def _class_from_yaml(
        self, path: Path, class_path: tuple[str, ...], data: dict[str, Any]
    ) -> ClassSpec:
        attributes = [
            Attribute(
                name=_text(item.get("name")),
                type=_text(item.get("type")) or "—",
                nullable="Yes" if item.get("nullable") is True else "No" if item.get("nullable") is False else "—",
                description=_text(item.get("description")),
            )
            for item in _as_list(data.get("attributes"))
            if item.get("name")
        ]
        attributes.sort(key=lambda item: item.name.casefold())
        associations = [
            Association(
                source=_text(item.get("source")),
                target=_text(item.get("target")),
                source_cardinality=_text(item.get("sourceCardinality")) or "—",
                target_cardinality=_text(item.get("targetCardinality")) or "—",
                source_name=_text(item.get("sourceName")),
                target_name=_text(item.get("targetName")),
                source_description=_text(item.get("sourceDescription")),
                target_description=_text(item.get("targetDescription")),
            )
            for item in _as_list(data.get("associations"))
            if item.get("target")
        ]
        return ClassSpec(
            name=_text(data.get("name")),
            path=path,
            relative_path=class_path,
            description=_text(data.get("description")),
            attributes=attributes,
            ancestors=sorted((_text(item) for item in (data.get("ancestors") or [])), key=str.casefold),
            descendants=sorted(
                (_text(item) for item in (data.get("descendants") or data.get("descendents") or [])),
                key=str.casefold,
            ),
            associations=associations,
        )

    def _build_tree(self) -> TreeEntry:
        root = TreeEntry("CIM Specifications", (), "root")
        for class_path, class_spec in sorted(self.classes.items()):
            current = root
            for depth, component in enumerate(class_path[:-1], 1):
                component_path = class_path[:depth]
                child = next((item for item in current.children if item.path == component_path), None)
                if child is None:
                    label = self.PROFILE_NAMES.get(component, component)
                    package = self.packages.get(component_path)
                    child = TreeEntry(label, component_path, "package", package)
                    current.children.append(child)
                current = child
            current.children.append(
                TreeEntry(class_spec.name, class_path, "class", class_spec)
            )
        self._sort_tree(root)
        root.children.sort(key=lambda item: {"ewb": 0, "TC57CIM": 1}.get(item.path[0], 2))
        for profile in root.children:
            if profile.kind == "package":
                overview = TreeEntry(
                    "Overview",
                    profile.path + ("__overview__",),
                    "overview",
                    profile.item,
                )
                profile.children.insert(0, overview)
        return root

    def _sort_tree(self, entry: TreeEntry) -> None:
        entry.children.sort(key=lambda item: (item.kind == "class", item.label.lower()))
        for child in entry.children:
            self._sort_tree(child)

    def filtered_tree(self, query: str) -> TreeEntry:
        query = query.casefold().strip()
        if not query:
            return self.tree

        def keep(entry: TreeEntry) -> TreeEntry | None:
            matching_children = [child for child in entry.children if keep(child)]
            own_match = query in entry.label.casefold()
            if entry.kind == "class" and isinstance(entry.item, ClassSpec):
                own_match = own_match or query in entry.item.description.casefold()
            if own_match or matching_children:
                return TreeEntry(entry.label, entry.path, entry.kind, entry.item, matching_children)
            return None

        return keep(self.tree) or TreeEntry(self.tree.label, (), "root")

    def _build_class_choices(self) -> dict[str, ClassSpec]:
        """Create unambiguous, type-ahead labels for every known class."""

        choices: dict[str, ClassSpec] = {}
        profile_order = {"ewb": 0, "TC57CIM": 1}
        for class_spec in sorted(
            self.classes.values(),
            key=lambda item: (profile_order.get(item.relative_path[0], 2), item.name.casefold(), item.relative_path),
        ):
            profile = self.PROFILE_NAMES.get(class_spec.relative_path[0], class_spec.relative_path[0])
            location = " / ".join((profile, *class_spec.relative_path[1:-1]))
            choice = f"{class_spec.name} — {location}"
            # A few YAML files define different classes with the same display
            # name in one package. Keep every class selectable in that case.
            if choice in choices:
                choice = f"{choice} [{class_spec.relative_path[-1]}]"
            suffix = 2
            while choice in choices:
                choice = f"{class_spec.name} — {location} [{class_spec.relative_path[-1]} #{suffix}]"
                suffix += 1
            choices[choice] = class_spec
        return choices

    def find_class(self, query: str) -> ClassSpec | None:
        """Resolve an autocomplete choice or an exact class name."""

        if choice := self.class_choices.get(query):
            return choice
        exact_matches = [
            item for item in self.class_choices.values() if item.name.casefold() == query.casefold()
        ]
        return exact_matches[0] if exact_matches else None

    def resolve_reference(self, name: str, profile: str) -> ClassSpec | None:
        """Resolve a class name, preferring the profile currently being viewed."""

        matches = [item for item in self.classes.values() if item.name == name]
        if not matches:
            return None
        return next((item for item in matches if item.relative_path[0] == profile), matches[0])


class CimBrowser(App[None]):
    """Textual application presenting the CIM model as a navigable reference."""

    CSS = """
    Screen {
        background: #0d1117;
        color: #c9d1d9;
    }

    Header {
        background: #161b22;
        color: #e6edf3;
        border-bottom: solid #30363d;
    }

    #body {
        height: 1fr;
    }

    #sidebar {
        width: 34;
        min-width: 26;
        border-right: solid #30363d;
        background: #161b22;
    }

    #sidebar-title {
        height: 2;
        padding: 1 2 0 2;
        color: #58a6ff;
        text-style: bold;
    }

    #search {
        margin: 0 1 1 1;
        background: #0d1117;
        color: #c9d1d9;
        border: round #30363d;
    }

    #tree {
        height: 1fr;
        padding: 0 1;
        background: #161b22;
        color: #c9d1d9;
    }

    #details {
        width: 1fr;
        height: 1fr;
        padding: 1 4;
        background: #0d1117;
        color: #c9d1d9;
        scrollbar-size: 1 1;
    }

    Markdown {
        width: 100%;
        background: #0d1117;
        color: #c9d1d9;
    }

    Footer {
        background: #161b22;
        color: #8b949e;
        border-top: solid #30363d;
    }
    """

    BINDINGS = [
        ("ctrl+l", "focus_search", "Search"),
        ("/", "focus_search", "Search"),
        ("escape", "clear_search", "Clear search"),
        ("j", "vim_down", "Down"),
        ("k", "vim_up", "Up"),
        ("h", "vim_left", "Collapse"),
        ("l", "vim_right", "Expand"),
        ("g", "vim_top", "Top"),
        ("shift+g", "vim_bottom", "Bottom"),
        ("ctrl+d", "vim_page_down", "Page down"),
        ("ctrl+u", "vim_page_up", "Page up"),
        ("q", "quit", "Quit"),
    ]

    def __init__(self, spec_directory: Path) -> None:
        super().__init__()
        self.model = SpecModel(spec_directory)
        self._selected_path: tuple[str, ...] | None = None

    def compose(self) -> ComposeResult:
        yield Header(show_clock=False)
        with Horizontal(id="body"):
            with Vertical(id="sidebar"):
                yield Static("CIM DATA MODEL", id="sidebar-title")
                yield Input(
                    placeholder="Search classes...",
                    suggester=SuggestFromList(self.model.class_choices, case_sensitive=False),
                    id="search",
                )
                tree = Tree("CIM Specifications", id="tree", data=None)
                tree.show_root = False
                yield tree
            yield Markdown("", id="details", open_links=False)
        yield Footer()

    def on_mount(self) -> None:
        self.dark = True
        self.title = "CIM Specification Browser"
        self.sub_title = f"{self.model.class_count:,} classes · {self.model.package_count:,} packages"
        self._populate_tree(self.model.tree)
        self._show_overview()
        if self.model.errors:
            self.notify(
                f"Loaded with {len(self.model.errors)} file warning(s)",
                severity="warning",
                timeout=5,
            )

    def _populate_tree(self, entry: TreeEntry) -> None:
        tree = self.query_one("#tree", Tree)
        tree.clear()
        root = tree.root
        for child in entry.children:
            self._add_tree_node(root, child)
        root.expand()
        # Expand the two profile roots, but leave the package lists compact.
        for node in root.children:
            if node.data and node.data.kind == "package":
                node.expand()

    def _add_tree_node(self, parent: Any, entry: TreeEntry) -> Any:
        if entry.kind == "class":
            node = parent.add_leaf(Text(entry.label, style="#c9d1d9"), data=entry)
        else:
            node = parent.add(entry.label, data=entry)
            for child in entry.children:
                self._add_tree_node(node, child)
        return node

    def on_input_submitted(self, event: Input.Submitted) -> None:
        if event.input.id != "search":
            return
        class_spec = self.model.find_class(event.value)
        if class_spec is None:
            self.notify("Choose a class from the autocomplete suggestion", severity="warning")
            return
        self._select_class_in_tree(class_spec)
        event.input.value = ""

    def on_tree_node_selected(self, event: Tree.NodeSelected[Any]) -> None:
        entry = event.node.data
        if not isinstance(entry, TreeEntry):
            return
        self._selected_path = entry.path
        if entry.kind == "class" and isinstance(entry.item, ClassSpec):
            self._show_class(entry.item)
        elif entry.kind == "overview" and isinstance(entry.item, PackageInfo):
            self._show_profile_overview(entry.item)
        elif entry.kind == "package" and isinstance(entry.item, PackageInfo):
            self._show_package(entry.item)
        else:
            self._show_overview()

    def on_markdown_link_clicked(self, event: Markdown.LinkClicked) -> None:
        """Open a local CIM class reference without leaving the application."""

        parsed = urlparse(event.href)
        if parsed.scheme != "cim":
            return
        class_path = tuple(part for part in (parsed.netloc + parsed.path).split("/") if part)
        if class_spec := self.model.classes.get(class_path):
            self._select_class_in_tree(class_spec)

    def _update_details(self, content: str) -> None:
        self.query_one("#details", Markdown).update(content)

    def _select_class_in_tree(self, class_spec: ClassSpec) -> None:
        """Expand the class path in the normal tree, then select and reveal it."""

        self._populate_tree(self.model.tree)
        tree = self.query_one("#tree", Tree)
        node = tree.root
        for component in class_spec.relative_path:
            next_node = next(
                (child for child in node.children if child.data and child.data.path[-1] == component),
                None,
            )
            if next_node is None:
                self.notify(f"Could not locate {class_spec.name} in the tree", severity="error")
                return
            node.expand()
            node = next_node
        # Expanding nodes changes their line numbers on the next layout pass.
        # Select afterwards so the cursor lands on (and scrolls to) the class.
        self.call_after_refresh(self._finish_tree_selection, tree, node)

    @staticmethod
    def _finish_tree_selection(tree: Tree, node: Any) -> None:
        # Force line generation after expanding the ancestor packages.
        _ = tree._tree_lines
        tree.focus()
        tree.select_node(node)

    def _show_overview(self) -> None:
        profiles = "  \n".join(
            f"- **{label}** — {sum(1 for item in self.model.classes if item and item[0] == key):,} classes"
            for key, label in self.model.PROFILE_NAMES.items()
            if any(item and item[0] == key for item in self.model.classes)
        )
        self._update_details(
            "# CIM Data Model\n\n"
            "Browse the YAML-backed Common Information Model specification. "
            "Select a package or class from the tree to inspect its definition.\n\n"
            "## Profiles\n\n"
            f"{profiles}\n\n"
            "## Contents\n\n"
            f"- **{self.model.class_count:,}** classes\n"
            f"- **{self.model.package_count:,}** packages\n"
            "- Attributes, inheritance, descendants, and associations\n\n"
            "Use **Search classes...** or press **Ctrl+L** to find a class quickly."
        )

    def _show_profile_overview(self, profile: PackageInfo) -> None:
        profile_key = profile.path[0] if profile.path else ""
        label = SpecModel.PROFILE_NAMES.get(profile_key, profile.name)
        classes = sum(1 for path in self.model.classes if path and path[0] == profile_key)
        packages = sum(1 for path in self.model.packages if path and path[0] == profile_key)
        self._update_details(
            f"# {label}\n\n"
            f"Browse the **{label}** contained in the local CIM YAML specification.\n\n"
            "## Contents\n\n"
            f"- **{classes:,}** classes\n"
            f"- **{packages:,}** packages\n\n"
            "Select a package or class from the tree to inspect its definition."
        )

    def _show_package(self, package: PackageInfo) -> None:
        direct_classes = [item for item in package.classes if len(item.relative_path) == len(package.path) + 1]
        child_packages = sorted(
            (
                info
                for path, info in self.model.packages.items()
                if len(path) == len(package.path) + 1 and path[: len(package.path)] == package.path
            ),
            key=lambda item: item.name.casefold(),
        )
        classes = "\n".join(f"- `{item.name}`" for item in sorted(direct_classes, key=lambda item: item.name.lower()))
        children = "\n".join(f"- **{item.name}**" for item in child_packages)
        sections = [f"# {package.name}", package.description or "Package in the CIM specification."]
        if child_packages:
            sections += ["## Subpackages", children]
        sections += ["## Classes", classes or "No classes directly in this package."]
        self._update_details("\n\n".join(sections))

    def _show_class(self, item: ClassSpec) -> None:
        sections = [
            f"# {item.name}",
            "## Class Description",
            item.description or "No class description provided.",
        ]

        sections += ["## Attributes"]
        if item.attributes:
            # Keep the same three-column presentation as the generated web docs.
            rows = ["| Name | Type | Description |", "| --- | --- | --- |"]
            rows += [
                f"| `{attribute.name}` | {self._class_reference(attribute.type, item)} | "
                f"{_clean_description(attribute.description)} |"
                for attribute in item.attributes
            ]
            sections.append("\n".join(rows))
        else:
            sections.append("None")

        sections += ["## Relationships", "### Ancestors"]
        sections.append(
            "\n".join(f"- {self._class_reference(ancestor, item)}" for ancestor in item.ancestors)
            if item.ancestors
            else "No ancestor classes"
        )
        sections += ["### Descendants"]
        sections.append(
            "\n".join(f"- {self._class_reference(descendant, item)}" for descendant in item.descendants)
            if item.descendants
            else "No descendent classes"
        )

        sections += ["## Associations"]
        if item.associations:
            rows = [
                (
                    "| Source Class | Source Cardinality | Target | Target Cardinality | Source Name | "
                    "Source Assoc. Description | Target Name | Target Assoc. Description |"
                ),
                "| --- | --- | --- | --- | --- | --- | --- | --- |",
            ]
            for association in item.associations:
                rows.append(
                    f"| {self._class_reference(association.source, item)} | {association.source_cardinality} | "
                    f"{self._class_reference(association.target, item)} | "
                    f"{association.target_cardinality} | {_clean_description(association.source_name)} | "
                    f"{_clean_description(association.source_description)} | "
                    f"{_clean_description(association.target_name)} | "
                    f"{_clean_description(association.target_description)} |"
                )
            sections.append("\n".join(rows))
        else:
            sections.append("None")
        self._update_details("\n\n".join(sections))

    def _class_reference(self, name: str, context: ClassSpec) -> str:
        """Render a class name as an internal link when the spec defines it."""

        class_spec = self.model.resolve_reference(name, context.relative_path[0])
        if class_spec is None:
            return f"`{name}`"
        return f"[{name}](cim://{'/'.join(class_spec.relative_path)})"

    def action_focus_search(self) -> None:
        self.query_one("#search", Input).focus()

    def action_clear_search(self) -> None:
        search = self.query_one("#search", Input)
        if search.value:
            search.value = ""
        else:
            self.screen.focused = None

    def _focused_tree(self) -> Tree | None:
        """Return the navigation tree only when it owns keyboard focus."""

        focused = self.screen.focused
        return focused if isinstance(focused, Tree) else None

    def action_vim_down(self) -> None:
        if tree := self._focused_tree():
            tree.action_cursor_down()

    def action_vim_up(self) -> None:
        if tree := self._focused_tree():
            tree.action_cursor_up()

    def action_vim_left(self) -> None:
        if not (tree := self._focused_tree()) or not (node := tree.cursor_node):
            return
        if node.is_expanded:
            node.collapse()
        else:
            tree.action_cursor_parent()

    def action_vim_right(self) -> None:
        if not (tree := self._focused_tree()) or not (node := tree.cursor_node):
            return
        if node.allow_expand and not node.is_expanded:
            node.expand()
        elif node.children:
            tree.move_cursor(node.children[0])

    def action_vim_top(self) -> None:
        if tree := self._focused_tree():
            tree.action_scroll_home()

    def action_vim_bottom(self) -> None:
        if tree := self._focused_tree():
            tree.action_scroll_end()

    def action_vim_page_down(self) -> None:
        if tree := self._focused_tree():
            tree.action_page_down()

    def action_vim_page_up(self) -> None:
        if tree := self._focused_tree():
            tree.action_page_up()


def main() -> None:
    parser = argparse.ArgumentParser(description="Browse a CIM specification in the terminal")
    parser.add_argument(
        "--spec",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "spec",
        help="directory containing the CIM YAML files (default: ../spec)",
    )
    args = parser.parse_args()
    CimBrowser(args.spec.expanduser().resolve()).run()


if __name__ == "__main__":
    main()
