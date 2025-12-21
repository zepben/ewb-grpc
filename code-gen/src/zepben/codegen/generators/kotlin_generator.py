#  Copyright 2025 Zeppelin Bend Pty Ltd
#
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at https://mozilla.org/MPL/2.0/.
from typing import Tuple

from zepben.codegen.generators.base_generator import SDKSpecGenerator
from zepben.codegen.generators.comment_generator import CommentGenerator
from zepben.codegen.model.definitions import DocumentedAttribute, DocumentedAssociation, YamlType
from zepben.utils import camel2snake, removeindent


class KotlinGenerator(SDKSpecGenerator):
    _template = """
package {package}
{imports}
{class_def}
"""
    document_single_vars = False


    @staticmethod
    def multiline_comment(comment):
        return CommentGenerator.kt(comment)

    def init(self):
        pass

    def generate_class_doc(self) -> str:
        if self.document_single_vars:
            return self.multiline_comment(self.class_spec.name.description)
        else:
            attributes = ('\n\n' + '\n'.join(f'@property {self.lowercase_first(a.name)} {a.description}' for a in self.class_spec.attributes)) if self.class_spec.attributes else ''
            associations = ('\n' + '\n'.join(f'@property {self.lowercase_first(a.name)} {a.description}' for a in self.class_spec.associations) + '\n') if self.class_spec.associations else ''
            return self.multiline_comment(
                f'{self.class_spec.name.description}{attributes}{associations}'
            )

    def generate_class_def(self) -> str:
        private_vars = []

        attributes = "\n\n".join(self.generate_attribute(a, private_vars) for a in self.class_spec.attributes or []) or ""
        associations = "\n\n".join(self.generate_association(a, private_vars) for a in self.class_spec.associations or []) or ""

        body = ''
        if attributes:
            body += ("\n" + attributes + "\n")

        if associations:
            body += ("\n" + associations + "\n")

        base_class_def = f' : {self.class_spec.base_class}(mRID)' if self.class_spec.base_class else ''
        return (self.generate_class_doc() + '\n'
                + ('@ZBEX\n' if self.class_spec.name.is_zbex() else '')
                + f'class {self.class_spec.name.name}(mRID: String){base_class_def} {{\n'
                + self.indent('\n'.join(private_vars)) + '\n'
                + f'{body}\n'
                + '}')

    def generate_attribute(self, attribute: DocumentedAttribute, private_vars: list[str]) -> str:
        to_kt = lambda s: s.to_kt(attribute.field_type) if isinstance(s, YamlType) else s
        if self.document_single_vars:
            comment = self.multiline_comment(attribute.description.replace('   ', '\n')) + '\n'
        else:
            comment = ''

        return self.indent(
            comment
            + ('@ZBEX\n' if attribute.is_zbex() else '')
            + f'var {attribute.name}: {to_kt(attribute.field_type)}'
            + ('? = null' if attribute.is_nullable else ' = TODO()')
        )


    def generate_association(self, association: DocumentedAssociation, private_vars: list[str]) -> str:
        if self.document_single_vars:
            comment = self.multiline_comment(association.description.replace('   ', '\n')) + '\n'
        else:
            comment = ''

        var_name = self.lowercase_first(association.name)

        if association.cardinality.is_list():
            private_vars.append(f"private var _{var_name}: MutableList<{association.field_type}>? = null")
            var_def = f"val {var_name}: Collection<{association.field_type}> get() = _{var_name}.asUnmodifiable()"

        else:
            var_def = f"var {var_name}: {association.field_type}? = null"

        return self.indent(comment
                           + ('@ZBEX\n' if association.is_zbex() else '')
                           + var_def)

    def generate_imports(self) -> str:
        imports = set()
        for association in self.class_spec.associations:
            if association.cardinality.is_list():
                imports.add('import com.zepben.ewb.services.common.extensions.asUnmodifiable')
        if imports:
            return '\n' + '\n'.join(list(imports)) + '\n'
        return ''

    @property
    def class_dir(self):
        return f'/src/main/kotlin/com/zepben/ewb/cim/{"/".join(self.package_dir_lowered)}', f'{self.class_name}.kt'

    def generate_class(self):
        return self.template.format(
            package='com.zepben.ewb.cim.' + '.'.join(self.package_dir_lowered),
            imports=self.generate_imports(),
            class_def=self.generate_class_def(),
        )

    @property
    def table_dir(self) -> Tuple[str, str]:
        return f'/src/main/kotlin/com/zepben/ewb/database/sqlite/cim/tables/{"/".join(self.package_dir_lowered)}', f'Table{self.class_name if not self.class_name.endswith('s') else f"{self.class_name}s"}.kt'

    def generate_table(self) -> str:
        imports = (
            "import com.zepben.ewb.database.sql.Column\n"
            "import com.zepben.ewb.database.sql.Column.Nullable\n"
            "import com.zepben.ewb.database.sql.Column.Type\n"
        )
        return self.template.format(
            package='com.zepben.ewb.database.sqlite.cim.tables.' + '.'.join(self.package_dir_lowered) + '\n',
            imports=imports,
            class_def=self.generate_table_def(),
        )

    def generate_table_def(self):
        to_sql = lambda s, n: s.to_sql(n) if isinstance(s, YamlType) else s
        def generate_columns(attribute: DocumentedAttribute) -> str:
            snake_name = camel2snake(attribute.name)
            return f'val {snake_name.upper()}: Column = Column(++queryIndex, "{snake_name}", Type.{to_sql(attribute.field_type, attribute.class_type)}, Nullable.{"NULL" if attribute.is_nullable else "NOT_NULL"})'
        attributes = "\n".join(generate_columns(a) for a in self.class_spec.attributes or []) or ""

        body = ''
        if attributes:
            body += ("\n" + attributes + "\n")

        if self.class_spec.associations:
            body += '\n'
        for association in self.class_spec.associations:
            body += f'// TODO: Table{self.class_spec.name.name}{association.target_class} relation table\n'

        base_class_def = f' : Table{self.class_spec.base_class}' if self.class_spec.base_class else ''
        return (self.generate_class_doc() + '\n'
                + '@Suppress("PropertyName")\n'
                + f'class Table{self.class_spec.name.name}{base_class_def} {{\n'
                + f'{self.indent(body)}\n'
                + '}')

    network_cim_reader_dir = '/src/main/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkCimReader.kt'
    def generate_network_cim_reader(self):
        class_name = self.class_spec.name.name
        return removeindent(f"""
            /**
             * Create a [{class_name}] and populate its fields from [Table{class_name}].
             *
             * @param service The [NetworkService] used to store any items read from the database.
             * @param table The database table to read the [{class_name}] fields from.
             * @param resultSet The record in the database table containing the fields for this [{class_name}].
             * @param setIdentifier A callback to register the mRID of this [{class_name}] for logging purposes.
             *
             * @return true if the [{class_name}] was successfully read from the database and added to the service.
             * @throws SQLException For any errors encountered reading from the database.
             */
            @Throws(SQLException::class)
            fun read(service: NetworkService, table: Table{class_name}, resultSet: ResultSet, setIdentifier: (String) -> String): Boolean {{
                TODO()
            }}"""
        )

    network_cim_writer_dir = '/src/main/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkCimWriter.kt'
    def generate_network_cim_writer(self):
        class_name = self.class_spec.name.name
        return removeindent(f"""
            /**
             * Write the [{class_name}] fields to [Table{class_name}].
             *
             * @param {self.lowercase_first(class_name)} The [{class_name}] instance to write to the database.
             *
             * @return true if the [{class_name}] was successfully written to the database, otherwise false.
             * @throws SQLException For any errors encountered writing to the database.
             */
            @Throws(SQLException::class)
            fun write({self.lowercase_first(class_name)}: {class_name}): Boolean {{
                val table = databaseTables.getTable<Table{class_name}>()
                val insert = databaseTables.getInsert<Table{class_name}>()
                
                TODO()
            }}
            """
        )

    network_database_tables_dir = '/src/main/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkDatabaseTables.kt'
    def generate_network_database_tables(self):
        return f'Table{self.class_spec.name.name}(), // FIXME: move'

    network_service_reader_dir = '/src/main/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkServiceReader.kt'
    def generate_network_service_reader(self):
        return f'readEach<Table{self.class_spec.name.name}>(service, reader::read) and // FIXME: move'

    network_service_writer_dir = '/src/main/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkServiceWriter.kt'
    def generate_network_service_writer(self):
        return f'writeEach<{self.class_spec.name.name}>(writer::write) and // FIXME: move'

    reference_resolver_dir = '/src/main/kotlin/com/zepben/ewb/services/common/ReferenceResolvers.kt'
    def generate_reference_resolver(self):
        # TODO: Reverse Reference Resolver (other class should sort this out tho...)
        result = []
        for association in self.class_spec.associations:
            _from = self.class_spec.name.name
            _to = association.target_class
            result.append(removeindent(f"""
                internal object {_from}To{_to}Resolver : ReferenceResolver<{_from}, {_to}> by KReferenceResolver( // FIXME: move
                    {_from}::class, {_to}::class, {_from}::add{_to}
                )""")
            )
        return '\n'.join(result)

    resolvers_dir = '/src/main/kotlin/com/zepben/ewb/services/common/Resolvers.kt'
    def generate_resolvers(self):
        # TODO: Reverse Reference Resolver (other class should sort this out tho...)
        result = []
        for association in self.class_spec.associations:
            _from = self.class_spec.name.name
            _to = association.target_class
            result.append(removeindent(f"""
                @JvmStatic
                fun {self.lowercase_first(_to)}({self.lowercase_first(_from)}: {_from}): BoundReferenceResolver<{_from}, {_to}> =
                    BoundReferenceResolver({self.lowercase_first(_from)}, {_from}To{_to}Resolver, {_to}To{_from}Resolver) // FIXME: move
                    """)
            )

    network_service_dir = '/src/main/kotlin/com/zepben/ewb/services/network/NetworkService.kt'
    def generate_network_service(self):
        # TODO: de-dupe
        class_name = self.class_spec.name.name
        return removeindent(f"""
            /**
             * Add the [{class_name}] to this service.
             *
             * @param {self.lowercase_first(class_name)} The [{class_name}] to add.
             * @return `true` if the item was added to the service, otherwise false.
             */
            fun add({self.lowercase_first(class_name)}: {class_name}): Boolean = super.add({self.lowercase_first(class_name)}) // FIXME: move

            /**
             * Remove the [{class_name}] from this service.
             *
             * @param {self.lowercase_first(class_name)} The [{class_name}] to remove.
             * @return `true` if the item was removed from the service, otherwise false.
             */
            fun remove({self.lowercase_first(class_name)}: {class_name}): Boolean = super.remove({self.lowercase_first(class_name)}) // FIXME: move
        """)

    network_service_comparator_dir = '/src/main/kotlin/com/zepben/ewb/services/network/NetworkServiceComparator.kt'
    def generate_network_service_comparator(self):
        class_name = self.class_spec.name.name

        body = ''
        if base := self.class_spec.base_class:
            body += f'compare{self.class_spec.base_class}()\n'

        if self.class_spec.attributes:
            compare_values = 'compareValues(\n'
            for attr in self.class_spec.attributes:
                compare_values += f'            {class_name}::{self.lowercase_first(attr.name)},\n'
            body += f'{compare_values}        )\n'

        return removeindent(f"""
            private fun compare{class_name}(source: {class_name}, target: {class_name}): ObjectDifference<{class_name}> =
                ObjectDifference(source, target).apply {{
                    {body}
                    TODO()
                }}  
        """)

    network_service_utils_dir = '/src/main/kotlin/com/zepben/ewb/services/network/NetworkServiceUtils.kt'
    def generate_network_service_utils(self):
        """ TODO: """

    network_cim_to_proto_dir = '/src/main/kotlin/com/zepben/ewb/services/network/translator/NetworkCimToProto.kt'
    def generate_network_cim_to_proto(self):
        class_name = self.class_spec.name.name

        new_builder = f'is{class_name} = {{ {self.lowercase_first(class_name)} = it.toPb() }}, // FIXME: move.'

        to_pb = removeindent(f"""
            /**
             * Convert the [{class_name}] into its protobuf counterpart.
             *
             * @param cim The [{class_name}] to convert.
             * @param pb The protobuf builder to populate.
             * @return [pb] for fluent use.
             */
            fun toPb(cim: {class_name}, pb: PB{class_name}.Builder): PB{class_name}.Builder =
                pb.apply {{
                    TODO()
                }}

            /**
             * An extension for converting any [{class_name}] into its protobuf counterpart.
             */
            fun {class_name}.toPb(): PB{class_name} = toPb(this, PB{class_name}.newBuilder()).build() // FIXME: move.
        """)

        java_friendly_class = removeindent(f"""
            /**
             * Convert the [{class_name}] into its protobuf counterpart.
             *
             * @param cim The [{class_name}] to convert.
             * @return [pb] for fluent use.
             */
            fun toPb(cim: {class_name}): PB{class_name} = cim.toPb()
            TODO("Move function")
        """)
        return f'{new_builder}\n\n{to_pb}\n\n{java_friendly_class}'

    network_pb_extensions_dir = '/src/main/kotlin/com/zepben/ewb/services/network/translator/NetworkPbExtensions.kt'
    def generate_network_pb_extensions(self):
        """TODO: stub the file?"""

    network_proto_to_cim_dir = '/src/main/kotlin/com/zepben/ewb/services/network/translator/NetworkProtoToCim.kt'
    def generate_network_proto_to_cim(self):
        class_name = self.class_spec.name.name

        add_from_pb_result = f"{class_name.upper()} -> getOrAddFromPb(pb.{self.lowercase_first(class_name)}.mRID()) {{ addFromPb(pb.{self.lowercase_first(class_name)}) }} // FIXME: move"
        to_cim = removeindent(f"""
            /**
             * Convert the protobuf [PB{class_name}] into its CIM counterpart.
             *
             * @param pb The protobuf [PB{class_name}] to convert.
             * @param networkService The [NetworkService] the converted CIM object will be added too.
             * @return The converted [pb] as a CIM [{class_name}].
             */
            fun toCim(pb: PB{class_name}, networkService: NetworkService): {class_name} =
                {class_name}(pb.mRID()).apply {{
                    TODO()
                }}

            /**
             * An extension to add a converted copy of the protobuf [PB{class_name}] to the [NetworkService].
             */
            fun NetworkService.addFromPb(pb: PB{class_name}): {class_name}? = tryAddOrNull(toCim(pb, this))
        """)
        java_friendly_class = removeindent(f"""
            /**
             * Add a converted copy of the protobuf [PB{class_name}] to the [NetworkService].
             *
             * @param pb The [PB{class_name}] to convert.
             * @return The converted [{class_name}]
             */
            fun addFromPb(pb: PB{class_name}): {class_name}? = networkService.addFromPb(pb) // FIXME: move.
        """)

        return f'{add_from_pb_result}\n\n{to_cim}\n\n{java_friendly_class}'

# TODO: TableCimVersion, changelog.md, pom.xml, UpgradeRunner, ChangeSet<NEXT>, NetworkEnumMappers, NetworkConsumerClient
if __name__ == '__main__':
    spec_obj = KotlinGenerator("IEC61970/Base/Core/ConductingEquipment.yaml")
    print(spec_obj.generate_class())
    print(spec_obj.generate_table())
    print(spec_obj.generate_network_cim_writer())
    print(spec_obj.generate_network_cim_reader())
    print(spec_obj.generate_network_service_reader())
    print(spec_obj.generate_network_service_writer())
    print(spec_obj.generate_reference_resolver())
    print(spec_obj.generate_resolvers())
    print(spec_obj.generate_network_service())
    print(spec_obj.generate_network_service_comparator())
    print('\n'.join(spec_obj.generate_network_cim_to_proto()))
    print('\n'.join(spec_obj.generate_network_proto_to_cim()))
