# Checklist for model change #

1. Update change log - `docs/release.md`
1. Existing messages updated for new attributes or associations - `proto/zepben/protobuf/cim`
1. New messages added for new classes/enums:
   1. added to model - `proto/zepben/protobuf/cim`
   1. added to producer (all three files) - `proto/zepben/protobuf/*p`
   1. added to consumer `oneof identifiedObject` maps - `proto/zepben/protobuf/*c` e.g. `NetworkIdentifiedObject`
1. Descriptions copied from CIM and added as doc comments to new changes (on messages, property etc)
1. `proto.lock` updated if there were [breaking changes](#breaking-changes)

# Development Setup #

Prerequisites:

1. IntelliJ (other IDE's will work, but not pre-configured)
1. Protobuf support in your IDE
1. Maven

If your protobuf plugin is not configured as below, you will see errors in the IDE. Make sure the proto dir is included.    
 
![](images/plugin-config.png)

To build java:

```
cd java
mvn package
```

To build and package python:

```
cd python
python3 build.py --package
```
 
## Protolock ##

This repository uses protolock to ensure there are no breaking changes between releases.

The proto.lock file can be found at `proto/zepben/proto.lock`. This file will be automatically kept up to date by the build pipeline - PR's **_should not_** update this file. 

For testing, the protolock binary can be retrieved from https://github.com/nilslice/protolock/releases/. Download, extract, and add it to your `PATH`. 

Prior to merging, protolock is run automatically by the build pipeline, and will ensure no breaking changes have been made. You can test this prior to committing by running:

```
cd proto/zepben
protolock status
```

### Breaking changes ###

If you need to merge breaking changes your patch must:

1. Update the major version to the next release for each library

1. Force update the `proto.lock` file prior to committing.

    ```
    cd proto/zepben
    protolock commit --force
    git add proto.lock
    ```
    
1. Commit `proto.lock` changes with version updates.
