#  Copyright 2025 Zeppelin Bend Pty Ltd
#
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at https://mozilla.org/MPL/2.0/.
import os.path
from dataclasses import dataclass
import git

from zepben.codegen.generators.base_generator import BaseSpecGenerator, SDKSpecGenerator
from zepben.codegen.generators.kotlin_generator import KotlinGenerator
from zepben.codegen.generators.proto_generator import ProtoSpecGenerator

DEFAULT_GIT_PATH = os.path.expanduser('~/git')

@dataclass
class Paths:
    ewb_grpc: str = DEFAULT_GIT_PATH + '/ewb-grpc'
    ewb_sdk_python: str = DEFAULT_GIT_PATH + '/ewb-sdk-python'
    ewb_sdk_jvm: str = DEFAULT_GIT_PATH + '/ewb-sdk-jvm'

    def base_path(self, generator: BaseSpecGenerator) -> str:
        if isinstance(generator, ProtoSpecGenerator):
            return self.ewb_grpc
        elif isinstance(generator, KotlinGenerator):
            return self.ewb_sdk_jvm
        raise NotImplementedError

    def apply_base(self, generator: BaseSpecGenerator, path: tuple[str, str]) -> tuple[str, str]:
        return f'{self.base_path(generator)}{path[0]}', path[1]


class Dobby:
    """
    Dobby is now a free elf. You thought dropping the cake on old loves head was the worst he could do...
    Think again...
    Usually Dobby is a good boy - but not always.
    Dobby is dumb, and doesnt try and be smart. Dobby doesnt guess. Dobby does exactly what is asked.
    Be careful with EXACTLY WHAT YOU ASK.
    """
    def __init__(self, paths):
        self.paths = paths

    def generate(self, new_yaml_files: list[str]):
        def path_string_to_list(_path):
            return [i for i in _path.split('/') if i]

        for file_path in new_yaml_files:
            proto = ProtoSpecGenerator(file_path)
            kotlin = KotlinGenerator(file_path)
            self.write(proto.generate(), *self.paths.apply_base(proto, (f'/proto/zepben/protobuf/cim/{"/".join(proto.class_path)}', f'{proto.class_spec.name.name}.proto')))
            self.write(kotlin.generate_class(), *self.paths.apply_base(kotlin, kotlin.class_dir))
            self.write(kotlin.generate_table(), *self.paths.apply_base(kotlin, kotlin.table_dir))

            self.add_to_existing(kotlin, kotlin.generate_network_cim_reader(), kotlin.network_cim_reader_dir)
            self.add_to_existing(kotlin, kotlin.generate_network_cim_writer(), kotlin.network_cim_writer_dir)
            self.add_to_existing(kotlin, kotlin.generate_network_database_tables(), kotlin.network_database_tables_dir)
            self.add_to_existing(kotlin, kotlin.generate_network_service_reader(), kotlin.network_service_reader_dir)
            self.add_to_existing(kotlin, kotlin.generate_network_service_writer(), kotlin.network_service_writer_dir)
            self.add_to_existing(kotlin, kotlin.generate_reference_resolver(), kotlin.reference_resolver_dir)
            self.add_to_existing(kotlin, kotlin.generate_resolvers(), kotlin.resolvers_dir)
            self.add_to_existing(kotlin, kotlin.generate_network_service(), kotlin.network_service_dir)
            self.add_to_existing(kotlin, kotlin.generate_network_service_comparator(), kotlin.network_service_comparator_dir)
            self.add_to_existing(kotlin, kotlin.generate_network_service_utils(), kotlin.network_service_utils_dir)
            self.add_to_existing(kotlin, kotlin.generate_network_cim_to_proto(), kotlin.network_cim_to_proto_dir)
            self.add_to_existing(kotlin, kotlin.generate_network_proto_to_cim(), kotlin.network_proto_to_cim_dir)
            self.add_to_existing(kotlin, kotlin.generate_network_pb_extensions(), kotlin.network_pb_extensions_dir)


    def write(self, data: str, file_path, file_name):
        """Will recursively create new dirs, and write new files. WILL OVERWRITE EXISTING FILES"""
        print(f'creating {file_path}/{file_name}')
        os.makedirs(file_path, exist_ok=True)
        with open(f'/{file_path}/{file_name}', 'w') as f:
            f.write(data)
            
    def add_to_existing(self, spec_generator: SDKSpecGenerator, data: str, file_path: str):
        """
        This method should call out to other methods to add functions to existing files.
        - changelog.md
        - pom.xml

        - $var = [main, test]
        - src/$var/kotlin/com/zepben/ewb/cim/<AS PER YAML PATHS>
        - src/$var/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkCimReader.kt
        - src/$var/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkCimWriter.kt
        - src/$var/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkDatabaseTables.kt
        - src/$var/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkServiceReader.kt
        - src/$var/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkServiceWriter.kt
        - src/$var/kotlin/com/zepben/ewb/database/sqlite/cim/tables/TableCimVersion.kt
        - src/$var/kotlin/com/zepben/ewb/database/sqlite/cim/tables/<AS PER YAML PATHS>
        - src/$var/kotlin/com/zepben/ewb/database/sqlite/cim/upgrade/UpgradeRunner.kt
        - src/$var/kotlin/com/zepben/ewb/database/sqlite/cim/upgrade/changesets/ChangeSet<NEXT>.kt
        - src/$var/kotlin/com/zepben/ewb/services/common/ReferenceResolvers.kt
        - src/$var/kotlin/com/zepben/ewb/services/common/Resolvers.kt
        - src/$var/kotlin/com/zepben/ewb/services/network/NetworkService.kt
        - src/$var/kotlin/com/zepben/ewb/services/network/NetworkServiceComparator.kt
        - src/$var/kotlin/com/zepben/ewb/services/network/NetworkServiceUtils.kt
        - src/$var/kotlin/com/zepben/ewb/services/network/translator/NetworkCimToProto.kt
        - src/$var/kotlin/com/zepben/ewb/services/network/translator/NetworkEnumMappers.kt
        - src/$var/kotlin/com/zepben/ewb/services/network/translator/NetworkPbExtensions.kt
        - src/$var/kotlin/com/zepben/ewb/services/network/translator/NetworkProtoToCim.kt
        - src/$var/kotlin/com/zepben/ewb/streaming/get/NetworkConsumerClient.kt
        """
        if not data:
            print(f'empty data: {file_path}')
            return
        try:
            with open(f'{self.paths.base_path(spec_generator)}/{file_path}', 'a') as f:
                f.write(data)
        except FileNotFoundError:
            print(f'File doesnt exist: {file_path}\n  it should... is your repo directory config right?')

    def detect_grpc_yaml_changes(self):
        git_repo = git.Repo(self.paths.ewb_grpc)
        if (active_branch := git_repo.active_branch.name) == 'main':
            print('this tool doesnt work on the main branch')  # TODO: is this really a problem / do we want to allow this?
            return

        return list(filter(
            lambda c: c.startswith('spec/ewb'), git_repo.git.diff(active_branch, 'main', '--name-only').splitlines()
        ))


if __name__ == "__main__":
    writer = Dobby(Paths())
    #writer.generate(['extensions/IEC61968/Common/ContactDetails.yaml'])
    print(writer.detect_grpc_yaml_changes())

