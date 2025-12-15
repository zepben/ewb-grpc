#  Copyright 2025 Zeppelin Bend Pty Ltd
#
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at https://mozilla.org/MPL/2.0/.
from dataclasses import dataclass
from enum import Enum
from unittest import case


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
    BOOLEAN = 'Boolean'
    DOUBLE = 'Double'

    def to_proto(self):
        match self:
            case YamlType.INTEGER: return "int32"
            case YamlType.STRING: return "string"
            case YamlType.BOOLEAN: return "bool"
            case YamlType.DOUBLE: return "double"

    def to_kt(self):
        match self:
            case YamlType.INTEGER: return 'Int'
            case YamlType.STRING: return 'String'
            case YamlType.BOOLEAN: return 'Boolean'
            case YamlType.DOUBLE: return 'Double'

    def to_sql(self):
        match self:
            case YamlType.INTEGER: return 'INTEGER'
            case YamlType.STRING: return 'STRING'
            case YamlType.BOOLEAN: return 'BOOLEAN'
            case YamlType.DOUBLE: return 'DOUBLE'

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
        self.description = description.replace('\[', '[').replace('\]', ']')

    def is_zbex(self):
        return self.description.startswith('[ZBEX]')

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
    base_class: str

    def __post_init__(self):
        if isinstance(self.base_class, list):
            self.base_class = self.base_class[0]


