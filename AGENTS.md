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
- Field numbers are sequential, starting from 1
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

## ZBEX Extensions

Zepben-specific extensions to the standard CIM model are marked with `[ZBEX]` in comments:

```protobuf
/**
 * [ZBEX] The LV feeders that are normally energized by this feeder.
 */
repeated string normalEnergizedLvFeederMRIDs = 6;
```

New ZBEX classes go in `cim/extensions/` (not in `iec61970/base/` or `iec61968/`).

## Adding New CIM Classes

1. **Determine the parent class** in the CIM hierarchy (use the table above).
2. **Create the proto file** in the appropriate directory under `cim/iec61970/base/` or `cim/iec61968/` (or `cim/extensions/` for ZBEX).
3. **First field** must be the embedded parent class with abbreviation `1`.
4. **Reference fields** use `string <Type>MRID` or `repeated string <Type>MRIDs` pattern.
5. **Optional fields** use the `oneof` + `NullValue` pattern.
6. **Use short aliases** for same-package imports (e.g., `IdentifiedObject io = 1;` when `IdentifiedObject.proto` is imported).
7. **Use full paths** for cross-package imports (e.g., `cim.iec61970.base.core.EquipmentContainer ec = 1;`).
8. **Add Javadoc comments** to the message and all fields.

## Adding to Service Identifiable Unions

When a new CIM class is added that should be queryable via a service:

1. Add the import to the service's `*-data.proto` file.
2. Add the type to the `oneof identifiable` in the `*Identifiable` message with the next available field number.
3. Use the same import style as existing entries (check how similar types are imported).

## Reference Files

When creating new files, use these as templates:

- **Core CIM class:** `proto/zepben/protobuf/cim/iec61970/base/core/Substation.proto` (embedded parent, mRID references, Javadoc)
- **ZBEX extension:** `proto/zepben/protobuf/cim/extensions/iec61970/base/feeder/LvFeeder.proto` (cross-package reference with `cim.` prefix)
- **Service proto:** `proto/zepben/protobuf/nc/nc.proto` (service definition, imports)
- **Data proto:** `proto/zepben/protobuf/nc/nc-data.proto` (Identifiable union with oneof)
- **Request proto:** `proto/zepben/protobuf/nc/nc-requests.proto` (messageId + mrids pattern)
- **Response proto:** `proto/zepben/protobuf/nc/nc-responses.proto` (messageId + identifiables pattern)
