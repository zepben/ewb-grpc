# AGENTS.md - EWB gRPC / CIM Proto Repository

## Repository Purpose

This repository contains the gRPC protocol definitions and CIM (Common Information Model) protobuf schema for the Energy Workbench (EWB) ecosystem. The proto files define:

1. **CIM data model** - IEC 61970 (transmission) and IEC 61968 (distribution) classes, plus Zepben-specific extensions
2. **gRPC services** - Consumer-facing services for network model (NC), diagram (DC), and customer (CC) data

## Directory Structure

```
proto/zepben/protobuf/
├── cim/                          # Core CIM model (188 proto files)
│   ├── iec61970/base/            # IEC 61970-301 transmission model
│   │   ├── core/                 # Base core classes (IdentifiedObject, Equipment, etc.)
│   │   ├── wires/                # Electrical equipment (Breaker, AcLineSegment, PowerTransformer, etc.)
│   │   ├── auxiliaryequipment/   # CTs, PTs, fault indicators
│   │   ├── generation/production/ # Battery, PV, wind units
│   │   ├── meas/                 # Measurements (Analog, Discrete, Control, Accumulator)
│   │   ├── scada/                # Remote control/source
│   │   ├── protection/           # Relays
│   │   ├── equivalents/          # Equivalent branch
│   │   └── diagramlayout/        # Diagram objects
│   ├── iec61968/                 # IEC 61968 distribution model
│   │   ├── common/               # Organisation, Location
│   │   ├── customers/            # Customer, CustomerAgreement, Tariff, PricingStructure
│   │   ├── assetinfo/            # CableInfo, PowerTransformerInfo, etc.
│   │   ├── assets/               # AssetOwner, Streetlight, Pole
│   │   ├── metering/             # Meter, UsagePoint
│   │   ├── operations/           # OperationalRestriction
│   │   └── infiec61968/          # Infrastructure classes
│   └── extensions/               # Zepben-specific extensions (ZBEX)
│       ├── iec61968/
│       └── iec61970/base/        # LvFeeder, LvSubstation, Loop, etc.
├── nc/                           # Network Consumer service
│   ├── nc.proto                  # Service definition (NetworkConsumer)
│   ├── nc-data.proto             # Data types (NetworkIdentifiable union)
│   ├── nc-requests.proto         # Request messages
│   └── nc-responses.proto        # Response messages
├── dc/                           # Diagram Consumer service
│   ├── dc.proto                  # Service definition (DiagramConsumer)
│   ├── dc-data.proto             # Data types (DiagramIdentifiable union)
│   ├── dc-requests.proto         # Request messages
│   └── dc-responses.proto        # Response messages
├── cc/                           # Customer Consumer service
│   ├── cc.proto                  # Service definition (CustomerConsumer)
│   ├── cc-data.proto             # Data types (CustomerIdentifiable union)
│   ├── cc-requests.proto         # Request messages
│   └── cc-responses.proto        # Response messages
└── [other services]              # ec/, hc/, mc/, mp/, ns/, wc/, network/, metadata/, etc.
```

## Key Files

- `spec/ewb/` - CIM spec metadata (IEC61970, IEC61968, extensions directories with `__metadata.yaml`)
- `README.md` - Repository overview
- `changelog.md` - Change history
- `proto/zepben/proto.lock` - Protolock compatibility baseline; only update this for breaking changes
- `rdfs/cim_profile_schema.ttl` - Generated RDFS schema that should be regenerated for data model changes

## Data Model Change Checklist

For actual data model changes, treat this as the minimum repository checklist:

1. Update `changelog.md`.
2. Update existing or new proto messages under `proto/zepben/protobuf/cim/`.
3. If a new class or enum is queryable or streamed by a service, update the relevant consumer or producer proto files as well.
4. Add or update the corresponding spec YAML under `spec/ewb/`.
5. Regenerate `rdfs/cim_profile_schema.ttl`.
6. Review `proto/zepben/proto.lock`:
   - normal non-breaking changes should generally leave it alone;
   - breaking changes must force-update it before commit.

`AGENTS.md` is a conventions guide, but `README.md` is the better source of truth for the broader model-change workflow.

## File Conventions

### Header (every proto file)

Every file starts with the MPL 2.0 license header:

```
/*
 * Copyright 2020 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
```

Copyright year may vary per file (check when adding new files; use the current year).

### Proto3 Boilerplate

Every file includes:

```protobuf
syntax = "proto3";
option java_multiple_files = true;
option java_package = "com.zepben.protobuf.<package>";
package zepben.protobuf.<package>;
```

The `<package>` mirrors the directory path relative to `proto/zepben/protobuf/`. For example:
- `proto/zepben/protobuf/cim/iec61970/base/core/Substation.proto` => `package zepben.protobuf.cim.iec61970.base.core`
- `proto/zepben/protobuf/nc/nc.proto` => `package zepben.protobuf.nc`

### Imports

Imports use paths relative to the `proto/` root:

```protobuf
import "zepben/protobuf/cim/iec61970/base/core/IdentifiedObject.proto";
import "google/protobuf/struct.proto";
import "google/protobuf/empty.proto";
import "google/protobuf/any.proto";
import "google/protobuf/timestamp.proto";
```

Standard google imports (`struct.proto`, `empty.proto`, `any.proto`, `timestamp.proto`) are used for optional fields, RPC stubs, and union catch-alls.

## CIM Data Model Patterns

### Inheritance via Embedded Fields

Proto3 does not support class inheritance. Instead, the CIM hierarchy is flattened using embedded message fields with **single-letter abbreviations**:

| Abbreviation | Stands For |
|---|---|
| `io` | IdentifiedObject |
| `psr` | PowerSystemResource |
| `cnc` | ConnectivityNodeContainer |
| `ec` | EquipmentContainer |
| `eq` | Equipment |
| `ce` | ConductingEquipment |
| `cd` | Conductor |
| `sw` | Switch / ProtectedSwitch |
| `ad` | AcDcTerminal |
| `or` | OrganisationRole |

**Inheritance chain (top to bottom):**

```
IdentifiedObject (io)
  └── PowerSystemResource (psr)
        ├── ConnectivityNodeContainer (cnc)
        │     └── EquipmentContainer (ec)
        │           ├── Substation
        │           ├── Feeder
        │           └── ...
        └── Equipment (eq)
              └── ConductingEquipment (ce)
                    ├── Switch (ce)
                    │     └── ProtectedSwitch (sw)
                    │           └── Breaker
                    └── ...
```

**Rule:** The first field in every message is always the embedded parent class. Field number is always `1`.

### Reference by mRID (Not Object Embedding)

Objects reference each other via string mRID, never by embedding full objects:

- Single reference: `string <lowercase>TypeMRID = N;`
- Multi reference: `repeated string <lowercase>TypeMRIDs = N;`

Examples:
```protobuf
string subGeographicalRegionMRID = 2;
repeated string normalEnergizedFeederMRIDs = 3;
repeated string powerTransformerEndMRIDs = 2;
string conductingEquipmentMRID = 2;
```

### Bidirectional Associations and Reverse References

The proto model usually represents associations by mRID fields on one or both related classes. When making a data model change, think about whether the relationship is logically bidirectional and whether the reverse side must also be documented in the spec.

For example, `Equipment` may point at an `EquipmentContainer`, while the corresponding container conceptually owns or exposes collections of `Equipment`. In SDKs this often appears as a forward property such as `equipment.equipmentContainer` plus a reverse collection on the container.

Proto files do not embed live object graphs, so you still model these relationships with mRID fields rather than object embedding. However, when updating `spec/ewb/**/*.yaml`, associations should be checked from both directions:

- fill in the `target*` fields for the forward association;
- if the target class also has a reverse association, fill in the matching `source*` fields;
- if you add one side in the spec, verify whether the opposite class also needs a corresponding association entry.

This bidirectional association maintenance is especially important for `Equipment` / `EquipmentContainer` style relationships and other parent/child model links.

### Optional Fields (Null Pattern)

Optional scalar fields use the `oneof` + `NullValue` pattern from `google.protobuf.struct.proto`:

```protobuf
oneof fieldName {
    google.protobuf.NullValue fieldNameNull = N;
    double fieldNameSet = N+1;
}
```

This produces a nullable field in the generated code. The `Null` variant uses field number N, the `Set` variant uses N+1.

Common types used this way: `double`, `int32`, `bool`, `string`.

### Javadoc Comments

All messages and fields use Javadoc-style `/** ... */` comments. Field descriptions should explain the semantic meaning, not just repeat the field name.

## Service Proto Patterns

### File Layout

Each service has 4 files:

1. **`<domain>.proto`** - gRPC service definition with `service <Domain>Consumer`
2. **`<domain>-data.proto`** - Data types, most importantly the `*Identifiable` union message
3. **`<domain>-requests.proto`** - Request message definitions
4. **`<domain>-responses.proto`** - Response message definitions

### Service Definition

```protobuf
service NetworkConsumer {
    rpc getIdentifiables (stream GetIdentifiablesRequest) returns (stream GetIdentifiablesResponse);
    rpc getNetworkHierarchy (GetNetworkHierarchyRequest) returns (GetNetworkHierarchyResponse);
    rpc checkConnection(connection.CheckConnectionRequest) returns (google.protobuf.Empty);
    rpc getMetadata(metadata.GetMetadataRequest) returns (metadata.GetMetadataResponse);
}
```

- Service name: `<Domain>Consumer` (e.g., `NetworkConsumer`, `DiagramConsumer`, `CustomerConsumer`)
- RPCs use bidirectional streaming (`stream`) for bulk data transfer
- `checkConnection` and `getMetadata` are standard stubs
- Imports: `*-requests.proto`, `*-responses.proto`, `connection-requests.proto`, `metadata-requests.proto`, `metadata-responses.proto`, `google/protobuf/empty.proto`

### Identifiable Union Type

Each service has a `*Identifiable` message with a `oneof identifiable` containing all leaf types from the CIM model that the service manages:

```protobuf
message NetworkIdentifiable {
    oneof identifiable {
        zepben.protobuf.cim.iec61970.base.core.Substation substation = 16;
        zepben.protobuf.cim.iec61970.base.core.Feeder feeder = 12;
        // ... more types ...
        google.protobuf.Any other = 999;
    }
}
```

- Use **full package paths** for cross-package imports
- Use **short aliases** (e.g., `cim.iec61970.base.core.Substation`) when the package is imported
- Preserve existing field numbers. Many unions start at 1, but gaps may already exist; use the next safe available number rather than renumbering existing entries.
- `google.protobuf.Any other = 999;` is always the last entry as a catch-all

### Request Message Pattern

```protobuf
message GetIdentifiablesRequest {
    int64 messageId = 1;        // An identifier for this message. Currently unused.
    repeated string mrids = 2;  // The list of mRIDs to retrieve.
}
```

- `messageId = 1` is always present (currently unused)
- `mrids = 2` (repeated string) for list-based queries
- Additional query parameters follow (e.g., `includeSubstations`, `networkState`)

### Response Message Pattern

```protobuf
message GetIdentifiablesResponse {
    int64 messageId = 1;
    repeated <Service>Identifiable identifiables = 2;
}
```

- `messageId = 1`
- `identifiables = 2` (repeated `*Identifiable`)

## Import Style Conventions

Import style depends on the scope of the reference:

- **Same-package imports**: Use short aliases. Import the file, then reference the message with a package prefix:
  ```protobuf
  import "zepben/protobuf/cim/iec61970/base/wires/ProtectedSwitch.proto";
  message Breaker {
      wires.ProtectedSwitch sw = 1;  // short alias
  }
  ```
- **Cross-package imports (ZBEX extensions)**: Use the `cim.` prefix to reference standard CIM types from extensions:
  ```protobuf
  import "zepben/protobuf/cim/iec61970/base/core/EquipmentContainer.proto";
  message LvFeeder {
      cim.iec61970.base.core.EquipmentContainer ec = 1;
  }
  ```
- **Service data files** (`*-data.proto`): Use full package paths for every CIM type in the `*Identifiable` union:
  ```protobuf
  zepben.protobuf.cim.iec61970.base.core.Substation substation = 16;
  ```

## Non-CIM Service Directories

Not every directory under `proto/zepben/protobuf/` references CIM types. Several are self-contained:

| Directory | Files | Purpose |
|-----------|-------|---------|
| `metadata/` | 3 | DataSource, ServiceInfo — no CIM references |
| `ns/` | 3 | Network state update/query — references `connection/`, `*-requests/`, `*-responses/` |
| `mp/` | 3 | Measurement Producer — references `mc/` types |
| `mc/` | 3 | Measurement Consumer |
| `hc/` | 3 | Hydro-specific (Job, LogMessage, Syf) |
| `mapbox/` | 1 | Vector tile format |
| `ec/` | 1 | EnergyConsumer (empty stub, no RPCs) |
| `wc/` | 1 | WeatherConsumer (empty stub, no RPCs) |

## ZBEX Extensions

Zepben-specific extensions to the standard CIM model are marked with `[ZBEX]` in comments:

```protobuf
/**
 * [ZBEX] The LV feeders that are normally energized by this feeder.
 */
repeated string normalEnergizedLvFeederMRIDs = 6;
```

New ZBEX classes go in `cim/extensions/` (not in `iec61970/base/` or `iec61968/`), mirroring the CIM hierarchy. They use the `cim.` prefix to cross-reference standard CIM parent classes.

Note: Standard CIM files may import ZBEX extensions or IEC 61968 infrastructure types. For example, `PowerTransformer.proto` (IEC 61970) imports `VectorGroup` (ZBEX) and `TransformerConstructionKind`/`TransformerFunctionKind` (IEC 61968 infrastructure), showing cross-profile mixing is intentional.

## Adding New CIM Classes

1. **Determine the parent class** in the CIM hierarchy (use the table above).
2. **Create the proto file** in the appropriate directory under `cim/iec61970/base/` or `cim/iec61968/` (or `cim/extensions/` for ZBEX).
3. **First field** must be the embedded parent class with abbreviation `1`.
4. **Reference fields** use `string <Type>MRID` or `repeated string <Type>MRIDs` pattern.
5. **Optional fields** use the `oneof` + `NullValue` pattern.
6. **Use short aliases** for same-package imports (e.g., `IdentifiedObject io = 1;` when `IdentifiedObject.proto` is imported).
7. **Use full paths** for cross-package imports (e.g., `cim.iec61970.base.core.EquipmentContainer ec = 1;`).
8. **Add Javadoc comments** to the message and all fields.
9. **Check bidirectional associations** in the spec YAML, not just the forward proto field. If the target class exposes the reverse side, update that association metadata as well.
10. **Update the model spec** under `spec/ewb/` for the class, attributes, ancestors/descendants, and associations.
11. **Regenerate or review derived artifacts** (`rdfs/cim_profile_schema.ttl`, and `proto.lock` for breaking changes).

## Adding to Service Identifiable Unions

When a new CIM class is added that should be queryable via a service:

1. Add the import to the service's `*-data.proto` file.
2. Add the type to the `oneof identifiable` in the `*Identifiable` message with the next safe available field number.
3. Use the same import style as existing entries (check how similar types are imported).
4. Do not renumber existing entries just to make the union look sequential.

## Reference Files

When creating new files, use these as templates:

- **Core CIM class:** `proto/zepben/protobuf/cim/iec61970/base/core/Substation.proto` (embedded parent, mRID references, Javadoc)
- **ZBEX extension:** `proto/zepben/protobuf/cim/extensions/iec61970/base/feeder/LvFeeder.proto` (cross-package reference with `cim.` prefix)
- **Service proto:** `proto/zepben/protobuf/nc/nc.proto` (service definition, imports)
- **Data proto:** `proto/zepben/protobuf/nc/nc-data.proto` (Identifiable union with oneof)
- **Request proto:** `proto/zepben/protobuf/nc/nc-requests.proto` (messageId + mrids pattern)
- **Response proto:** `proto/zepben/protobuf/nc/nc-responses.proto` (messageId + identifiables pattern)

## Python Build Process

The Python library lives in `python/` and is built with `python3.13`.

### Prerequisites

- Python 3.13 (system or venv)
- Virtualenv: `python3.13 -m venv .venv`
- Activate: `source .venv/bin/activate`
- Install deps: `pip install -r python/requirements.txt`

### Build Steps

1. **Clean generated code:** `python python/build.py --clean`
2. **Compile protos to Python:** `python python/build.py`
3. **Install the package:** `cd python && pip install -e .`
4. **Build distribution:** `python python/build.py --package`

The build script uses `grpc_tools.protoc` to compile all `.proto` files under `proto/zepben/protobuf/` into Python gRPC stubs in `src/`. It also creates `__init__.py` files in every subdirectory.

### Notes

- The current build script still imports `pkg_resources`; do not assume it has already been migrated to `importlib.resources.files()`.
- The `--pyi_out` flag generates `.pyi` stub files alongside the `.py` files.
- Generated files are `*_pb2.py` and `*_pb2_grpc.py`. They are auto-generated and should not be edited manually.
