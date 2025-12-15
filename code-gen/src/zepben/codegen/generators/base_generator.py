#  Copyright 2025 Zeppelin Bend Pty Ltd
#
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at https://mozilla.org/MPL/2.0/.
import os
from abc import ABC, abstractmethod, abstractproperty

import yaml

from zepben.codegen.model.definitions import ClassSpec, DocumentedName, DocumentedAttribute, DocumentedAssociation

GRPC_ROOT = "/home/max/git/ewb-grpc"


class SpecTreeParser():
    root = f"{GRPC_ROOT}/spec/ewb/"
    class_path_map = {}
    def __init__(self):
        if not self.class_path_map:
            self._generate_map()

    @classmethod
    def _generate_map(cls):
        for class_path, class_name in cls.walk_directory(cls.root):
            if exist := cls.class_path_map.get(class_name):
                print(exist, (class_path, class_name))
                raise Exception(f"Duplicate class path: {class_path}")
            cls.class_path_map[class_name] = class_path

    @classmethod
    def walk_directory(cls, directory: str):
        for result in os.walk(directory):
            for file in result[2]:
                if not file.startswith('__') and file.endswith(".yaml"):
                    yield result[0].replace(directory, '').split('/'), file.replace('.yaml', '')

    def get(self, item: str, separator: str = '.'):
        return separator.join([a.lower() for a in self.class_path_map.get(item)] + [item])


class BaseSpecGenerator(ABC):
    _license = (
        "Copyright 2025 Zeppelin Bend Pty Ltd\n\n"
        "This Source Code Form is subject to the terms of the Mozilla Public\n"
        "License, v. 2.0. If a copy of the MPL was not distributed with this\n"
        "file, You can obtain one at https://mozilla.org/MPL/2.0/."
    )
    _footnote = "/* TODO: CHECK GENERATION, ps: ner ner Kurt it wasnt a waste of time... */"
    _template = None

    @property
    def template(self):
        if self._template is None:
            raise NotImplementedError
        return self.multiline_comment(self._license) + '\n' + self._template + self._footnote

    @staticmethod
    @abstractmethod
    def multiline_comment(comment: str) -> str: ...

    spec_tree_parser = SpecTreeParser()
    def __init__(self, class_path: str):
        self._index = 0
        with open(f"{GRPC_ROOT}/spec/ewb/{class_path}") as f:
            yaml_spec = yaml.safe_load(f)

        self.class_path = class_path.split("/")[:-1]
        self.class_spec = ClassSpec(
            name=DocumentedName(
                name=yaml_spec["name"],
                description=yaml_spec["description"],
            ),
            attributes=[
                DocumentedAttribute(**a) for a in yaml_spec.get("attributes") or []
            ],
            associations=[
                DocumentedAssociation(a) for a in yaml_spec.get("associations") or []
            ],
            base_class=yaml_spec.get("ancestors")
        )

        self.init()

    @staticmethod
    def indent(block: str, indent_amount: int = 4) -> str:
        return "\n".join(f"{' '*indent_amount}{l}" for l in block.splitlines())

    @abstractmethod
    def init(self): ...

    def lowercase_first(self, s: str | None) -> str:
        return s[:1].lower() + s[1:] if s else ''

class SDKSpecGenerator(BaseSpecGenerator):
    def init(self):
        super().init()

    @property
    @abstractmethod
    def class_dir(self) -> str: ...

    @abstractmethod
    def generate_class(self) -> str: ...

    @property
    @abstractmethod
    def table_dir(self) -> str: ...

    @abstractmethod
    def generate_table(self) -> set[str]: ...

    @property
    @abstractmethod
    def network_cim_reader_dir(self) -> str: ...

    @abstractmethod
    def generate_network_cim_reader(self) -> str: ...

    @property
    @abstractmethod
    def network_cim_writer_dir(self) -> str: ...

    @abstractmethod
    def generate_network_cim_writer(self) -> str: ...

    @property
    @abstractmethod
    def network_database_tables_dir(self) -> str: ...

    @abstractmethod
    def generate_network_database_tables(self) -> str: ...

    @property
    @abstractmethod
    def network_service_reader_dir(self) -> str: ...

    @abstractmethod
    def generate_network_service_reader(self) -> str: ...

    @property
    @abstractmethod
    def network_service_writer_dir(self) -> str: ...

    @abstractmethod
    def generate_network_service_writer(self) -> str: ...

    @property
    @abstractmethod
    def reference_resolver_dir(self) -> str: ...

    @abstractmethod
    def generate_reference_resolver(self) -> str: ...

    @property
    @abstractmethod
    def resolvers_dir(self) -> str: ...

    @abstractmethod
    def generate_resolvers(self) -> str: ...

    @property
    @abstractmethod
    def network_service_dir(self) -> str: ...

    @abstractmethod
    def generate_network_service(self) -> str: ...

    @property
    @abstractmethod
    def network_service_comparator_dir(self) -> str: ...

    @abstractmethod
    def generate_network_service_comparator(self) -> str: ...

    @property
    @abstractmethod
    def network_service_utils_dir(self) -> str: ...

    @abstractmethod
    def generate_network_service_utils(self) -> str: ...

    @property
    @abstractmethod
    def network_cim_to_proto_dir(self) -> str: ...

    @abstractmethod
    def generate_network_cim_to_proto(self) -> str: ...

    @property
    @abstractmethod
    def network_pb_extensions_dir(self) -> str: ...

    @abstractmethod
    def generate_network_pb_extensions(self) -> str: ...

    @property
    @abstractmethod
    def network_proto_to_cim_dir(self) -> str: ...

    @abstractmethod
    def generate_network_proto_to_cim(self) -> str: ...
