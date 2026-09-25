from __future__ import annotations

import subprocess
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from rich.text import Text
from textual.app import App, ComposeResult
from textual.containers import Horizontal, Vertical, VerticalScroll
from textual.suggester import SuggestFromList
from textual.widgets import Footer, Header, Input, Markdown, Static, Tree

from cim_ple.main import EmptyMarksConfirm, FlushConfirm
from cim_ple.ewb_yaml import write_ewb_spec
from cim_ple.relationships import association_mode, relationship_is_bidirectional
from cim_ple.models import Association, Attribute, ClassMarks, ClassSpec, PackageInfo, TreeEntry, clean_description
from cim_ple.spec_model import SpecModel
from cim_ple.UI.mark_editor import MarkEditor


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
        overflow-y: auto;
        scrollbar-size: 1 1;
    }

    #right-sidebar {
        display: none;
        width: 32;
        min-width: 24;
        background: #161b22;
        border-left: solid #30363d;
    }

    #right-sidebar.visible {
        display: block;
    }

    #saved-class-tree {
        height: 1fr;
        background: #161b22;
        color: #c9d1d9;
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
        ("ctrl+u", "vim_page_up", "Page up"),
        ("a", "add_selected_class", "Add class"),
        ("d", "remove_highlighted_class", "Remove class"),
        ("e", "edit_highlighted_class", "Edit fields"),
        ("w", "confirm_flush", "Write specs"),
        ("q", "quit", "Quit"),
    ]

    def __init__(self, spec_directory: Path) -> None:
        super().__init__()
        self.model = SpecModel(spec_directory)
        self._selected_path: tuple[str, ...] | None = None
        self._saved_classes: list[ClassSpec] = []
        self._saved_class_paths: set[tuple[str, ...]] = set()
        self._class_marks: dict[tuple[str, ...], ClassMarks] = {}

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
            with VerticalScroll(id="details"):
                yield Markdown("", id="details-content", open_links=False)
            with Vertical(id="right-sidebar"):
                saved_tree = Tree("SAVED CLASSES", id="saved-class-tree")
                saved_tree.root.expand()
                saved_tree.root.allow_expand = False
                yield saved_tree
        yield Footer()

    def on_mount(self) -> None:
        self.dark = True
        self.title = "CIM Specification Browser"
        self.sub_title = f"{self.model.class_count:,} classes · {self.model.package_count:,} packages"
        self._populate_tree(self.model.tree)
        restored = self._restore_saved_classes_from_git()
        if restored:
            self._render_saved_tree()
            self.query_one("#right-sidebar").add_class("visible")
            self.notify(f"Restored {restored} saved CIM100 class(es) from Git changes")
        self._show_overview()
        if self.model.errors:
            self.notify(
                f"Loaded with {len(self.model.errors)} file warning(s)",
                severity="warning",
                timeout=5,
            )

    def _restore_saved_classes_from_git(self) -> int:
        """Rebuild saved nodes from changed/untracked EWB YAML files in Git."""

        repository = self.model.spec_directory.parent
        commands = (
            ("git", "diff", "--name-only", "HEAD", "--", "spec/ewb"),
            ("git", "ls-files", "--others", "--exclude-standard", "spec/ewb"),
        )
        changed_paths: set[Path] = set()
        try:
            for command in commands:
                result = subprocess.run(command, cwd=repository, text=True, capture_output=True, check=True)
                changed_paths.update(repository / line for line in result.stdout.splitlines() if line.endswith(".yaml"))
        except (OSError, subprocess.CalledProcessError):
            return 0

        restored = 0
        for path in sorted(changed_paths):
            if not path.is_file() or path.name == "__metadata.yaml":
                continue
            try:
                data = yaml.safe_load(path.read_text(encoding="utf-8"))
            except (OSError, yaml.YAMLError):
                continue
            if not isinstance(data, dict) or not isinstance(data.get("name"), str):
                continue
            class_spec = self.model.resolve_reference(data["name"], "TC57CIM")
            if class_spec is not None and class_spec.relative_path[0] == "TC57CIM":
                restored += self._add_saved_class(class_spec)
        return restored

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
        details = self.query_one("#details", VerticalScroll)
        details.scroll_home(animate=False)
        self.query_one("#details-content", Markdown).update(content)

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
                f"{clean_description(attribute.description)} |"
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
                    f"{association.target_cardinality} | {clean_description(association.source_name)} | "
                    f"{clean_description(association.source_description)} | "
                    f"{clean_description(association.target_name)} | "
                    f"{clean_description(association.target_description)} |"
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

    def action_vim_page_up(self) -> None:
        if tree := self._focused_tree():
            tree.action_page_up()

    def action_add_selected_class(self) -> None:
        """Add the selected class to the list in the right sidebar."""

        tree = self.query_one("#tree", Tree)
        entry = tree.cursor_node.data if tree.cursor_node is not None else None
        if not isinstance(entry, TreeEntry) or entry.kind != "class" or not isinstance(entry.item, ClassSpec):
            self.notify("Select a CIM class before adding it", severity="warning")
            return

        class_spec = entry.item
        if class_spec.relative_path[0] != "TC57CIM":
            self.notify("Only CIM100 Data Model classes can be saved", severity="warning")
            return
        if self._add_saved_class(class_spec):
            self._render_saved_tree()
            self.notify(f"Added {class_spec.name}")
        else:
            self.notify(f"{class_spec.name} is already in the list")
        self.query_one("#right-sidebar").add_class("visible")

    def action_remove_highlighted_class(self) -> None:
        saved_tree = self._focused_saved_tree()
        node = saved_tree.cursor_node if saved_tree is not None else None
        if node is None or not isinstance(node.data, ClassSpec):
            self.notify("Focus a saved class before removing it", severity="warning")
            return

        class_spec = node.data
        self._remove_saved_class(class_spec)
        self.notify(f"Removed {class_spec.name}")

    def action_edit_highlighted_class(self) -> None:
        saved_tree = self._focused_saved_tree()
        node = saved_tree.cursor_node if saved_tree is not None else None
        if node is None or not isinstance(node.data, ClassSpec):
            self.notify("Focus a saved class before editing fields", severity="warning")
            return

        class_spec = node.data
        marks = self._class_marks.get(class_spec.relative_path, ClassMarks())
        self.push_screen(
            MarkEditor(class_spec, marks, self.model.class_choices),
            lambda result: self._save_class_marks(class_spec, result),
        )

    def action_confirm_flush(self) -> None:
        if self._focused_saved_tree() is None:
            self.notify("Focus the saved classes tree before writing specs", severity="warning")
            return
        if not self._saved_classes:
            self.notify("There are no saved classes to write", severity="warning")
            return
        self.push_screen(FlushConfirm(len(self._saved_classes)), self._flush_if_confirmed)

    def _flush_if_confirmed(self, confirmed: bool) -> None:
        if not confirmed:
            return
        try:
            written = 0
            for class_spec in self._saved_classes:
                marks = self._class_marks.get(class_spec.relative_path, ClassMarks())
                written += write_ewb_spec(self.model.spec_directory, class_spec, marks)
        except OSError as error:
            self.notify(f"Could not write EWB specs: {error}", severity="error")
            return
        self.notify(f"Wrote {written} EWB specification file(s)")

    def _save_class_marks(self, class_spec: ClassSpec, marks: ClassMarks | None) -> None:
        if marks is None:
            return

        self._class_marks[class_spec.relative_path] = marks
        if (
            not marks.attributes
            and not marks.associations
            and not any(
                item.get("marked", "true") == "true" for item in marks.zbex_attributes + marks.zbex_associations
            )
        ):
            self.push_screen(
                EmptyMarksConfirm(class_spec.name),
                lambda remove: self._handle_empty_marks(class_spec, remove),
            )
            return

        added_classes = False
        for index, association in enumerate(class_spec.associations):
            if marks.associations.get(index) != "Bi-directional":
                continue
            target_class = self.model.resolve_reference(association.target, "TC57CIM")
            if target_class is None or target_class.relative_path[0] != "TC57CIM":
                continue
            if relationship_is_bidirectional(class_spec, association, self.model.resolve_reference):
                continue

            added_classes = self._add_saved_class(target_class) or added_classes
            reciprocal_marks = self._class_marks.setdefault(target_class.relative_path, ClassMarks())
            for reciprocal_index, reciprocal in enumerate(target_class.associations):
                if reciprocal.target == class_spec.name or reciprocal.source == class_spec.name:
                    reciprocal_marks.associations[reciprocal_index] = "Bi-directional"
                    reciprocal_marks.changed_associations.add(reciprocal_index)

        if added_classes:
            self._render_saved_tree()

    def _handle_empty_marks(self, class_spec: ClassSpec, remove: bool) -> None:
        if remove:
            self._remove_saved_class(class_spec)
            self.notify(f"Removed {class_spec.name}")

    def _remove_saved_class(self, class_spec: ClassSpec) -> None:
        self._saved_classes.remove(class_spec)
        self._saved_class_paths.remove(class_spec.relative_path)
        self._class_marks.pop(class_spec.relative_path, None)
        self._render_saved_tree()

    def _add_saved_class(self, class_spec: ClassSpec) -> bool:
        """Add a CIM100 class to the in-memory saved set if it is new."""

        if class_spec.relative_path in self._saved_class_paths:
            return False
        self._saved_classes.append(class_spec)
        self._saved_class_paths.add(class_spec.relative_path)
        self._mark_ewb_implementation(class_spec)
        return True

    def _mark_ewb_implementation(self, class_spec: ClassSpec) -> None:
        """Mark the CIM100 fields implemented by the equivalent EWB class."""

        ewb_class = self.model.resolve_reference(class_spec.name, "ewb")
        if ewb_class is None or ewb_class.relative_path[0] != "ewb":
            return

        marks = self._class_marks.setdefault(class_spec.relative_path, ClassMarks())
        implemented_attributes = {attribute.name for attribute in ewb_class.attributes}
        marks.attributes.update(
            attribute.name for attribute in class_spec.attributes if attribute.name in implemented_attributes
        )
        cim_attribute_names = {attribute.name for attribute in class_spec.attributes}
        for attribute in ewb_class.attributes:
            if attribute.name not in cim_attribute_names and not any(
                item["name"] == attribute.name for item in marks.zbex_attributes
            ):
                marks.zbex_attributes.append(
                    {
                        "kind": "attribute",
                        "name": attribute.name,
                        "type": attribute.type,
                        "description": attribute.description,
                        "marked": "true",
                    }
                )
        for index, association in enumerate(class_spec.associations):
            mode = association_mode(ewb_class, association, self.model.resolve_reference)
            if mode is not None:
                marks.associations[index] = mode
        cim_targets = {association.target for association in class_spec.associations}
        for association in ewb_class.associations:
            if association.target not in cim_targets and not any(
                item["target"] == association.target for item in marks.zbex_associations
            ):
                marks.zbex_associations.append(
                    {
                        "kind": "association",
                        "name": association.target_name or association.target,
                        "target": association.target,
                        "cardinality": association.target_cardinality,
                        "direction": association_mode(ewb_class, association, self.model.resolve_reference) or "1 way",
                        "description": association.target_description,
                        "marked": "true",
                    }
                )

    def _focused_saved_tree(self) -> Tree | None:
        focused = self.screen.focused
        return focused if isinstance(focused, Tree) and focused.id == "saved-class-tree" else None

    def _render_saved_tree(self) -> None:
        """Render saved classes beneath their full CIM profile/package paths."""

        saved_tree = self.query_one("#saved-class-tree", Tree)
        saved_tree.root.remove_children()
        saved_tree.root.expand()
        branches: dict[tuple[str, ...], Any] = {(): saved_tree.root}

        for class_spec in sorted(self._saved_classes, key=lambda item: item.relative_path):
            parent = saved_tree.root
            for depth, component in enumerate(class_spec.relative_path[:-1], 1):
                branch_path = class_spec.relative_path[:depth]
                branch = branches.get(branch_path)
                if branch is None:
                    label = SpecModel.PROFILE_NAMES.get(component, component)
                    branch = parent.add(label)
                    branch.expand()
                    branch.allow_expand = False
                    branches[branch_path] = branch
                parent = branch
            parent.add_leaf(class_spec.name, data=class_spec)
