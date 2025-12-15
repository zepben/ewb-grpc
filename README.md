# Checklist for model change

1. Update change log - `changelog.md`
2. Existing messages updated for new attributes or associations - `proto/zepben/protobuf/cim`
3. New messages added for new classes/enums:
   1. added to model - `proto/zepben/protobuf/cim`
   2. added to producer (all three files) - `proto/zepben/protobuf/*p`
   3. added to consumer `oneof identifiedObject` maps - `proto/zepben/protobuf/*c` e.g. `NetworkIdentifiedObject`
4. Descriptions copied from CIM and added as doc comments to new changes (on messages, property etc.)
5. `proto.lock` updated if there were breaking changes.
6. Document changes in the [model specification](#model-specification)
7. [Update RDFS](#compiling-an-RDF-schema)

## Development Setup

Prerequisites:

1. IntelliJ (other IDE's will work, but not pre-configured)
2. Protobuf support in your IDE
3. Maven

If your protobuf plugin is not configured as below, you will see errors in the IDE. Make sure the proto dir is included.

![](images/plugin-config.png)

## Protolock ##

This repository uses protolock to ensure there are no breaking changes between releases.

The protolock file can be found at `proto/zepben/proto.lock`. This file will be automatically kept up to date by the build pipeline - PR's **_should not_** update this file.

For testing, the protolock binary can be retrieved from https://github.com/nilslice/protolock/releases/. Download, extract, and add it to your `PATH`.

Prior to merging, protolock is run automatically by the build pipeline, and will ensure no breaking changes have been made. You can test this prior to committing by running:

```sh
cd proto/zepben
protolock status
```

## Breaking changes ##

If you need to merge breaking changes your patch must:

1. Update the major version to the next release for each library

2. Force update the `proto.lock` file prior to committing.

    ```sh
    cd proto/zepben
    protolock commit --force
    git add proto.lock
    ```

3. Commit `proto.lock` changes with version updates.

## Building/Installing debug versions

Once you have installed the local package, update you project to reference the local version of testing.

### Java

```sh
cd java
-- edit pom.xml and change the version to <VERSION>-LOCAL# --
mvn clean install
```

### Python

```sh
cd python
-- edit setup.py and change the version to <VERSION>.local# --
python3 build.py --package
```

### C

There are 2 ways to build packages locally:
 * To build the libraries using a container, run: 

```sh
cd c
make container-lib
```
  This will download the container `zepben/grpc-c-builder` and use it to build the libraries.

 * Alternatively, to build libraries directly, install `protoc`, `grpc` and `make` packages, and run: 

```sh
cd c
make
```

You can now copy the output files from the `c/out/` directory as needed to your C project.

## Model Specification

The model specification is found in the [spec](./spec/) folder. This folder contains YAML files that describe both the CIM profile and our Zepben profile. The YAML profile spec forms a neutral machine-readable format that can be used to generate other formats such as documentation.

### The Structure

```text
spec/
├── ewb/
│   ├── extensions/
│   │   ├── IEC61968/
│   │   │   ├── .metadata.yaml
│   │   │   └── AssetInfo/
│   │   │       ├── RelayInfo.yaml
│   │   │       └── .metadata.yaml
│   │   └── .metadata.yaml
│   └── IEC61968/
│       ├── .metadata.yaml
│       └── AssetInfo/
│           ├── .metadata.yaml
│           └── CableInfo.yaml
└── TC57CIM/
    └── IEC61968/
        ├── .metadata.yaml
        └── AssetInfo/
            ├── .metadata.yaml
            └── BusbarSectionInfo.yaml
```

Above is a simplified diagram of the structure that is present in the `spec` folder. The folders form the following structure

- The top level folders in the `spec` folder represent profiles, we have two. The CIM standard and our own Zepben profile.
- Inside a profile folder, there are folders that represent packages in that profile. Packages can contain other packages. Each package at minimum will contain a single file called `.metadata.yaml` which names the package and provides a description of what's in the package.
- Packages can also contain other yaml files such as `RelayInfo.yaml` in the example. These files represent classes that are inside a package.
- Classes contain the following attributes
  - `name` - the name of the class
  - `description` - a description of the class
  - `ancestors` - a list containing the class the current class inherits from
  - `descendants` - a list containing all direct inheritors of the current class
  - `attributes` - a list of objects containing:
    - `name` - the name of the attribute
    - `type` - the type of the attribute, this is normally another class in the profile
    - `description` - a description of the attribute
  - `associations` - a list of object containing:
    - `source` - the name of the current class
    - `target` - the other class this class is associated to
    - `targetCardinality` - the cardinality against the target class, can be (0..1, 1, 0..\*, 1..\*).
    - `targetName` - the name of the association on the current class that can fetch the target class
    - `targetDescription` - the description of the association
    - `source*` - same as target, but the relationship from the target class back to the current class. This may not always exist.

### Rules

Using the above is not always easy, so here are a set of rules when trying to describe a class in the spec format. Keep in mind this is for the Zepben profile only. The CIM profile is purely generated:

1. The location of the class file must match the package hierarchy in code
2. `name` and `description` are always present
3. Is it a class or an enum? - if it's an enum, fill in `name` and `description`. All the enum values becomes `attributes` with no type
4. Does the class inherit from another class? - Add as an `ancestor` in this class, and as a `descendant` on the other class
5. If the fields don't end with `mrids` in proto - these are most likely `attributes`, document them as so.
6. For the other fields that end with `mrids` in proto this implies an association. In the Kotlin SDK, most fields on class that reference another model also imply an association.
    1. The `source` and `target` fields should be the names of the classes that form the association
    2. Starting for the rest of the target fields first
       1. The cardinality is `0..*` if the field is a `Collection`, if it's a single object that's nullable, then its `0..1`
       2. The `targetName` is the name of the field in the Kotlin SDK
       3. The `targetDescription` is the comment associated with the field in the SDK
    3. For the `source*` fields, navigate to the `target` class
       1. If there isn't a field that refer the `source` class, then don't fill in these fields
       2. If there is, then fill in those fields using the same instructions as the `target*` fields but from the view of the `target` class
       3. If you do fill in the `source*` fields, then there should be an association on the `target` class YAML as well

### Other Notes

- When it comes to adding new classes, if the class already exists in the CIM profile spec, copy it into the Zepben spec at the same package path and then modify it
- If the class does not exist in CIM whether in the CIM published spec or the informal spec (`infiec61968`), then it should go into the `extensions` folder
- Everything the extensions folder should follow the class hierarchy identical to where that extension would go in CIM.

### Building

To build the website output, run the following commands from a terminal.

```commandline
cd profile-manager
gradlew run --args "generate ../spec site"
```

:::tip

If you run the `gradlew` command via the IDE, make sure you change the working directory in your run configuration to include `profile-manager`

:::

:::info

If you wish to keep any previous outputs, specify `--no-delete-output` to prevent the deletion prior to creating the new profile output. 

:::

You will either be presented with errors in your spec that need to be fixed, or the output will be created in `profile-manager/site`.

### Viewing Output

To make use of the output, you will need to clone the [data model site](https://github.com/zepben/evolve-datamodel-site), which contains the docusaurus configuration required to render the output.

1. Delete the folders in `<datamodel-repo>/docs`. __*DO NOT DELETE THE `*.md` FILES*__
2. Copy the output folders from `site` into `<datamodel-repo>/docs`
3. Follow the instructions in `<datamodel-repo>/README.md` to locally host the site.

### Deploying

After viewing your output, follow the instructions in `<datamodel-repo>/README.md` to locally build the site, then copy this into the [docs website repo](https://github.com/zepben/zepben.github.io)

# Compiling an RDF schema

The script `generate_cim_profile_schema.py` can be used to create a Turtle RDFS schema for the current EWB profile. It should be run for every data model change
and the `rdfs/cim_profile_schema.ttl` should be updated.

To run, using your python virtual environment for the proto files:

    # Ensure you've got all dependencies (pyyaml)
    cd python
    pip install -e . 

    # Run
    cd ..
    python python/generate_cim_profile_schema.py -o rdfs/cim_profile_schema.ttl spec/ewb