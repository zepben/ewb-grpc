#  Copyright 2025 Zeppelin Bend Pty Ltd
#
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at https://mozilla.org/MPL/2.0/.
from zepben.codegen.generators import InvalidYamlError
from zepben.codegen.generators.base_generator import BaseSpecGenerator
from zepben.codegen.generators.comment_generator import CommentGenerator
from zepben.codegen.model.definitions import DocumentedAttribute, DocumentedAssociation, YamlType


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

{imports}

{class_description}
message {class_name} {{
{body}
}}
/* TODO: CHECK GENERATION, ps: ner ner Kurt it wasnt a waste of time... */
    """
    @staticmethod
    def multiline_comment(comment):
        return CommentGenerator.proto(comment)

    def init(self):
        self._index = 0

    @property
    def current_index(self):
        self._index += 1
        return self._index

    def generate_base_class(self):
        if not self.class_spec.base_class:
            return
        return self.indent(
            self.multiline_comment(f"The {self.class_spec.base_class} fields for this {self.class_spec.name.name}.") + '\n'
            + f'{self.spec_tree_parser.get(self.class_spec.base_class)} changeMe = {self.current_index};\n'
        )

    def generate_attribute(self, attribute: DocumentedAttribute) -> str:
        to_proto = lambda s: s.to_proto(attribute.field_type) if isinstance(s, YamlType) else s
        comment = self.multiline_comment(attribute.description.replace('   ', '\n')) + '\n'

        try:
            if attribute.is_nullable:
                    return self.indent(
                        comment + f'oneof {attribute.name} ' + '{\n'
                        + f'  google.protobuf.NullValue {attribute.name}Null = {self.current_index};\n'
                        + f'  {attribute.type.to_proto(attribute.field_type)} {self.lowercase_first(attribute.name)}Set = {self.current_index};\n'
                        + '}'
                    )
            else:
                return self.indent(comment +
                                   f'{to_proto(attribute.type)} {self.lowercase_first(attribute.name)} = {self.current_index}')
        except Exception as e:
            raise InvalidYamlError(f"Could not generate attribute for {attribute}. Error was {e}", e)

    def generate_association(self, association: DocumentedAssociation):
        return self.indent(
            self.multiline_comment(association.description.replace('   ', '\n')) + '\n'
            + ('repeated ' if association.cardinality.is_list() else '')
            + f'{self.spec_tree_parser.get(association.target_class)} {self.lowercase_first(association.name)} = {self.current_index};')

    def generate_imports(self) -> set[str]:
        imports = set()
        for attribute in self.class_spec.attributes:
            if not isinstance(attribute.type, YamlType) and attribute.type != YamlType.ENUM:
                if (_type := self.spec_tree_parser.get(attribute.type, separator='/')) is not None:
                    imports.add(f'zepben/protobuf/cim/{_type}.proto')

            if attribute.is_nullable:
                imports.add('google/protobuf/struct.proto')
        for association in self.class_spec.associations:
            if (_type := self.spec_tree_parser.get(association.target_class, separator='/')) is not None:
                imports.add(f'zepben/protobuf/cim/{_type}.proto')
        return imports

    def generate(self) -> str:
        """
        returns a string-form protobuf definition
        """
        base_class = self.generate_base_class() or ""
        attributes = "\n\n".join(self.generate_attribute(a) for a in self.class_spec.attributes or []) or ""
        associations = "\n\n".join(self.generate_association(a) for a in self.class_spec.associations or []) or ""

        body = ''
        if base_class:
            body += ("\n" + base_class + "\n")

        if attributes:
            body += ("\n" + attributes + "\n")

        if associations:
            body += ("\n" + associations + "\n")

        return self._template.format(
            class_path_periods='.'.join(self.package_dir_lowered),
            class_name=self.class_spec.name.name,
            class_description=self.multiline_comment(self.class_spec.name.description),
            imports='\n'.join(f'import "{i}";' for i in self.generate_imports()),
            body=body
        )


if __name__ == "__main__":
    print("#####")
    #spec_obj = ProtoSpecGenerator("IEC61970/InfIEC61970/Part303/GenericDataSet/ChangeSetMember.yaml")
    #print(spec_obj.generate_proto())
    print("#####")
    spec_obj = ProtoSpecGenerator("IEC61970/Base/Core/IdentifiedObject.yaml")
    print(spec_obj.generate())
    print("#####")
    spec_obj = ProtoSpecGenerator("IEC61970/Base/Core/ConductingEquipment.yaml")
    print(spec_obj.generate())
