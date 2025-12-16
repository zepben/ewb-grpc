# Dobby

As part of any data model change, you likely have to touch the following files in the Jvm sdk
... and then again in the python sdk
... and write .proto files
... and write .yaml spec files for the website.

    changelog.md
    pom.xml

    src/main/kotlin/com/zepben/ewb/cim/<AS PER YAML PATHS>
    src/main/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkCimReader.kt
    src/main/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkCimWriter.kt
    src/main/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkDatabaseTables.kt
    src/main/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkServiceReader.kt
    src/main/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkServiceWriter.kt
    src/main/kotlin/com/zepben/ewb/database/sqlite/cim/tables/TableCimVersion.kt
    src/main/kotlin/com/zepben/ewb/database/sqlite/cim/tables/<AS PER YAML PATHS>
    src/main/kotlin/com/zepben/ewb/database/sqlite/cim/upgrade/UpgradeRunner.kt
    src/main/kotlin/com/zepben/ewb/database/sqlite/cim/upgrade/changesets/ChangeSet<NEXT>.kt
    src/main/kotlin/com/zepben/ewb/services/common/ReferenceResolvers.kt
    src/main/kotlin/com/zepben/ewb/services/common/Resolvers.kt
    src/main/kotlin/com/zepben/ewb/services/network/NetworkService.kt
    src/main/kotlin/com/zepben/ewb/services/network/NetworkServiceComparator.kt
    src/main/kotlin/com/zepben/ewb/services/network/NetworkServiceUtils.kt
    src/main/kotlin/com/zepben/ewb/services/network/translator/NetworkCimToProto.kt
    src/main/kotlin/com/zepben/ewb/services/network/translator/NetworkEnumMappers.kt
    src/main/kotlin/com/zepben/ewb/services/network/translator/NetworkPbExtensions.kt
    src/main/kotlin/com/zepben/ewb/services/network/translator/NetworkProtoToCim.kt
    src/main/kotlin/com/zepben/ewb/streaming/get/NetworkConsumerClient.kt

    src/test/kotlin/com/zepben/ewb/cim/<AS PER YAML PATHS>
    src/test/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkCimReader.kt
    src/test/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkCimWriter.kt
    src/test/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkDatabaseTables.kt
    src/test/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkServiceReader.kt
    src/test/kotlin/com/zepben/ewb/database/sqlite/cim/network/NetworkServiceWriter.kt
    src/test/kotlin/com/zepben/ewb/database/sqlite/cim/tables/TableCimVersion.kt
    src/test/kotlin/com/zepben/ewb/database/sqlite/cim/tables/<AS PER YAML PATHS>
    src/test/kotlin/com/zepben/ewb/database/sqlite/cim/upgrade/UpgradeRunner.kt
    src/test/kotlin/com/zepben/ewb/database/sqlite/cim/upgrade/changesets/ChangeSet<NEXT>.kt
    src/test/kotlin/com/zepben/ewb/services/common/ReferenceResolvers.kt
    src/test/kotlin/com/zepben/ewb/services/common/Resolvers.kt
    src/test/kotlin/com/zepben/ewb/services/network/NetworkService.kt
    src/test/kotlin/com/zepben/ewb/services/network/NetworkServiceComparator.kt
    src/test/kotlin/com/zepben/ewb/services/network/NetworkServiceUtils.kt
    src/test/kotlin/com/zepben/ewb/services/network/translator/NetworkCimToProto.kt
    src/test/kotlin/com/zepben/ewb/services/network/translator/NetworkEnumMappers.kt
    src/test/kotlin/com/zepben/ewb/services/network/translator/NetworkPbExtensions.kt
    src/test/kotlin/com/zepben/ewb/services/network/translator/NetworkProtoToCim.kt
    src/test/kotlin/com/zepben/ewb/streaming/get/NetworkConsumerClient.kt

All of these files (actually pretty much none right now) need to have the same docs copied everywhere.
... The same types copied everywhere
... in their own language's syntax.

Dobby aims to help with this task, by populating stubs in all required files for each SDK and the proto files, 
all auto-generated based off the yaml spec files. At the moment it doesn't aim to completely automate this, but
instead just do some of the work with an intent to reduce the mental load of a data model change.

## Setup

Set up a virtualenv, and then in this directory run:

    pip install -e .

## Usage

Update the yaml spec files in the `spec/ewb` directory as required, and then in `dobby.py` modify the main
function to pass in the files and paths to your SDK + git directories as required. CLI arguments and 
git diff file detection coming soon..

