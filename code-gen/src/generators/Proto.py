#  Copyright 2025 Zeppelin Bend Pty Ltd
#
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at https://mozilla.org/MPL/2.0/.
import os
from abc import ABC, abstractmethod
from dataclasses import dataclass
from enum import Enum
from unittest import case

import yaml

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

    def get(self, item: str):
        return '.'.join([a.lower() for a in self.class_path_map.get(item)] + [item])


class Cardinality(Enum):
    NULLABLE = "0..1"
    NULLABLE_LIST = "0..*"
    NOT_NULLABLE = "1"
    NOT_NULLABLE_LIST = "1..*"

    def is_list(self):
        return self in (Cardinality.NOT_NULLABLE_LIST, Cardinality.NULLABLE_LIST)

class ReferenceType(Enum):
    MRID = 0
    DIRECT = 1

class YamlType(Enum):
    INTEGER = "Integer"
    STRING = "String"

    def to_proto(self):
        match self:
            case YamlType.INTEGER:
                return "int32"
            case YamlType.STRING:
                return "string"

class DocumentedName:
    def __init__(self, name: str, description: str):
        self.name = name
        self.description = description

class DocumentedAttribute(DocumentedName):
    def __init__(self, type: str, **kwargs):
        super().__init__(**kwargs)
        try:
            self.type = YamlType(type)
        except ValueError:
            self.type = type

    @property
    def nullable(self):
        if isinstance(self.type, YamlType):
            return self.name != 'mRID'
        return False


class DocumentedAssociation(DocumentedAttribute):
    def __init__(self, yaml_spec: dict):
        super().__init__(
            name=yaml_spec["targetName"],
            description=yaml_spec["targetDescription"],
            type=yaml_spec["target"]
        )
        self.cardinality: Cardinality = Cardinality(yaml_spec["targetCardinality"])



@dataclass
class ClassSpec:
    name: DocumentedName
    attributes: list[DocumentedAttribute]
    associations: list[DocumentedAttribute]


class BaseSpecGenerator(ABC):
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
            ]
        )


        self.init()


    @abstractmethod
    def init(self): ...

class ProtoSpecGenerator(BaseSpecGenerator):
    _template = """
/*
 * Copyright 2025 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

syntax = "proto3";
option java_multiple_files = true;
option java_package = "com.zepben.protobuf.cim.{class_path_periods}";
package zepben.protobuf.cim.{class_path_periods};

import "zepben/protobuf/cim/iec61970/infiec61970/part303/genericdataset/ChangeSet.proto";
import "zepben/protobuf/cim/iec61970/base/core/IdentifiedObject.proto";

{class_description}
message {class_name} {{

{base_class}{attributes}{associations}
}}
/* TODO: CHECK GENERATION, ps: ner ner Kurt it wasnt a waste of time... */
    """
    def init(self):
        self._index = 0

    @property
    def current_index(self):
        self._index += 1
        return self._index

    def multiline_comment(self, comment: str) -> str:
        return "/**\n" + '\n'.join(f" * {c}" for c in comment.splitlines()) + "\n */"

    def indent(self, block: str, indent_amount: int = 4) -> str:
        return "\n".join(f"{' '*indent_amount}{l}" for l in block.splitlines())

    def generate_attribute(self, attribute: DocumentedAttribute) -> str:
        comment = self.multiline_comment(attribute.description.replace('   ', '\n')) + '\n'

        if attribute.nullable:
            return self.indent(
                comment + f'oneof {attribute.name} ' + '{\n'
                + f'  google.protobuf.NullValue {attribute.name}Null = {self.current_index}\n'
                + f'  {attribute.type.to_proto()} {attribute.name}Set = {self.current_index}\n'
                + '}'
            )
        else:
            return self.indent(comment +
                               f'{attribute.type.to_proto()} {attribute.name} = {self.current_index}')

    def generate_association(self, association: DocumentedAssociation):
        return self.indent(
            self.multiline_comment(association.description.replace('   ', '\n')) + '\n'
            + ('repeated ' if association.cardinality.is_list() else '')
            + f'{self.spec_tree_parser.get(association.type)} {association.name} = {self.current_index};')

    def generate_proto(self):
        return self._template.format(
            class_path_periods='.'.join(self.class_path).lower(),
            class_name=self.class_spec.name.name,
            class_description=self.multiline_comment(self.class_spec.name.description),
            base_class="",
            attributes=("\n\n".join(self.generate_attribute(a) for a in self.class_spec.attributes or []) + '\n\n') or "",
            associations=("\n\n".join(self.generate_association(a) for a in self.class_spec.associations or []) + '\n') or "",
        )


if __name__ == "__main__":
    #spec_obj = ProtoSpecGenerator("IEC61970/InfIEC61970/Part303/GenericDataSet/ChangeSetMember.yaml")
    spec_obj = ProtoSpecGenerator("IEC61970/Base/Core/IdentifiedObject.yaml")
    print(spec_obj.generate_proto())
    print(SpecTreeParser().class_path_map)
