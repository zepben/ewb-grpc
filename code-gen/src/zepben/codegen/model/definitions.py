#  Copyright 2025 Zeppelin Bend Pty Ltd
#
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at https://mozilla.org/MPL/2.0/.
from dataclasses import dataclass
from enum import Enum
from typing import Optional
from unittest import case

from zepben.codegen.generators import InvalidYamlError


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
    INTEGER = "integer"
    STRING = "string"
    BOOLEAN = "boolean"
    DOUBLE = "double"
    FLOAT = "float"
    ENUM = "enum"
    CLASS = "class"

    def to_proto(self, class_name: str = None) -> Optional[str]:
        match self:
            case YamlType.INTEGER: return 'int32'
            case YamlType.STRING: return 'string'
            case YamlType.BOOLEAN: return 'bool'
            case YamlType.DOUBLE: return 'double'
            case YamlType.FLOAT: return 'float'
            case YamlType.ENUM: return 'string'
            case YamlType.CLASS: return class_name

    def to_kt(self, class_name: str = None) -> Optional[str]:
        match self:
            case YamlType.INTEGER: return 'Int'
            case YamlType.STRING: return 'String'
            case YamlType.BOOLEAN: return 'Boolean'
            case YamlType.DOUBLE: return 'Double'
            case YamlType.FLOAT: return 'Float'
            case YamlType.ENUM: return 'string'
            case YamlType.CLASS: return class_name

    def to_sql(self, class_name: str = None) -> Optional[str]:
        match self:
            case YamlType.INTEGER: return 'INTEGER'
            case YamlType.STRING: return 'TEXT'
            case YamlType.BOOLEAN: return 'BOOLEAN'
            case YamlType.DOUBLE: return 'DOUBLE'
            case YamlType.FLOAT: return 'FLOAT'
            case YamlType.ENUM: return 'TEXT'
            case YamlType.CLASS: return class_name

@dataclass()
class MockYamlType:
    _val: str

    def __hash__(self):
        return hash(self._val)

    def __str__(self):
        return self._val

    def to_kt(self):
        return self._val

    def to_proto(self):
        return self._val

    def to_sql(self):
        return 'TODO()'

class DocumentedName:
    def __init__(self, name: str, description: str):
        self.name = name
        self.description = description.replace('\\[', '[').replace('\\]', ']')

    def is_zbex(self):
        return self.description.startswith('[ZBEX]')

    def __str__(self):
        return f'name={self.name},description="{self.description}"'

class DocumentedAttribute(DocumentedName):
    def __init__(self, type: str = None, nullable: bool = True, **kwargs):
        super().__init__(**kwargs)
        self.class_type: Optional[str]
        try:
            self.type = YamlType(type.lower())
            self.class_type = None
        except ValueError:
            self.type = YamlType.CLASS
            self.class_type = type

        self.nullable = nullable


    def __str__(self):
        return f"{super().__str__()},type={self.type},nullable={self.nullable}"

    @property
    def field_type(self):
        match self.type:
            case YamlType.CLASS: return self.class_type
            case _: return self.type.value

    @property
    def is_nullable(self):
        if isinstance(self.type, YamlType):
            if self.type == YamlType.ENUM or self.type == YamlType.CLASS:
                return False    # Enum fields are never nullable
            else:
                return self.name != 'mRID'  # TODO: hack that can be removed once nullable bool added to all attributes in yaml.
        return self.nullable


class DocumentedAssociation(DocumentedAttribute):
    def __init__(self, yaml_spec: dict):
        try:
            super().__init__(
                name=yaml_spec["targetName"],
                description=yaml_spec["targetDescription"],
                type=yaml_spec["target"]
            )
            self.target_class: str = yaml_spec["target"]
            self.cardinality: Cardinality = Cardinality(yaml_spec["targetCardinality"])
        except Exception as e:
            raise InvalidYamlError(f"Could not fully parse yaml_spec. Error was: {e}. yaml_spec was: {yaml_spec}")



@dataclass
class ClassSpec:
    name: DocumentedName
    attributes: list[DocumentedAttribute]
    associations: list[DocumentedAttribute]
    base_class: str

    def __post_init__(self):
        if isinstance(self.base_class, list):
            self.base_class = self.base_class[0]


