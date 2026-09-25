from pathlib import Path
from typing import Any

import yaml

from cim_ple.models import Association, Attribute, ClassSpec, PackageInfo, TreeEntry, as_list, text


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
            package.description = text(data.get("description"))
            package.name = text(data.get("name")) or package.name

    def _class_from_yaml(self, path: Path, class_path: tuple[str, ...], data: dict[str, Any]) -> ClassSpec:
        attributes = [
            Attribute(
                name=text(item.get("name")),
                type=text(item.get("type")) or "—",
                nullable="Yes" if item.get("nullable") is True else "No" if item.get("nullable") is False else "—",
                description=text(item.get("description")),
            )
            for item in as_list(data.get("attributes"))
            if item.get("name")
        ]
        attributes.sort(key=lambda item: item.name.casefold())
        associations = [
            Association(
                source=text(item.get("source")), target=text(item.get("target")),
                source_cardinality=text(item.get("sourceCardinality")) or "—", target_cardinality=text(item.get("targetCardinality")) or "—",
                source_name=text(item.get("sourceName")), target_name=text(item.get("targetName")),
                source_description=text(item.get("sourceDescription")), target_description=text(item.get("targetDescription")),
            )
            for item in as_list(data.get("associations"))
            if item.get("target")
        ]
        return ClassSpec(
            name=text(data.get("name")),
            path=path,
            relative_path=class_path,
            description=text(data.get("description")),
            attributes=attributes,
            ancestors=sorted((text(item) for item in (data.get("ancestors") or [])), key=str.casefold),
            descendants=sorted(
                (text(item) for item in (data.get("descendants") or data.get("descendents") or [])),
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
            current.children.append(TreeEntry(class_spec.name, class_path, "class", class_spec))
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
        profile_order = {"TC57CIM": 0, "ewb": 1}
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
        exact_matches = [item for item in self.class_choices.values() if item.name.casefold() == query.casefold()]
        return exact_matches[0] if exact_matches else None

    def resolve_reference(self, name: str, profile: str) -> ClassSpec | None:
        """Resolve a class name, preferring the profile currently being viewed."""

        matches = [item for item in self.classes.values() if item.name == name]
        if not matches:
            return None
        return next((item for item in matches if item.relative_path[0] == profile), matches[0])
