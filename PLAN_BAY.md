# Plan: Implement Bay in the protobuf CIM model

Status: draft for review
Date: 2026-06-23

## Goal
Add the CIM Bay model to this repository in a way that is consistent with the existing proto conventions, spec YAMLs, and service unions.

## Scope
Bay is not a single-file change. The class depends on related containment and reverse associations, so the implementation should include:
- `Bay` message proto
- `VoltageLevel` message proto, because Bay is associated to VoltageLevel and the reverse association must be represented
- `BreakerConfiguration` and `BusbarConfiguration` enum protos
- updates to parent/related protos for reverse references
- EWB spec YAML updates
- network service union updates
- changelog and generated artifact review

## Source of truth to follow
- TC57 CIM spec:
  - `spec/TC57CIM/IEC61970/Base/Core/Bay.yaml`
- Existing EWB spec files for related classes:
  - `spec/ewb/IEC61970/Base/Core/Substation.yaml`
  - `spec/ewb/IEC61970/Base/Core/BaseVoltage.yaml`
  - `spec/ewb/IEC61970/InfIEC61970/Feeder/Circuit.yaml`
- Existing proto patterns:
  - `proto/zepben/protobuf/cim/iec61970/base/core/Substation.proto`
  - `proto/zepben/protobuf/cim/iec61970/infiec61970/feeder/Circuit.proto`
  - `proto/zepben/protobuf/nc/nc-data.proto`

## Association matrix to implement
1. Bay -> Substation
   - forward field: `substationMRID`
   - reverse field: `Substation.bayMRIDs`
2. Bay -> VoltageLevel
   - forward field: `voltageLevelMRID`
   - reverse field: `VoltageLevel.bayMRIDs`
3. Bay -> Circuit
   - forward field: `circuitMRID`
   - reverse field: `Circuit.endBayMRID`
4. VoltageLevel -> Substation
   - forward field: `substationMRID`
   - reverse field: `Substation.voltageLevelMRIDs`
5. VoltageLevel -> BaseVoltage
   - forward field: `baseVoltageMRID`
   - reverse field: `BaseVoltage.voltageLevelMRIDs`

## Planned file changes

### New proto files
Create these under `proto/zepben/protobuf/cim/iec61970/base/core/`:
- `BreakerConfiguration.proto`
- `BusbarConfiguration.proto`
- `VoltageLevel.proto`
- `Bay.proto`

### Update existing proto files
- `proto/zepben/protobuf/cim/iec61970/base/core/Substation.proto`
  - add reverse Bay collection
  - add reverse VoltageLevel collection
- `proto/zepben/protobuf/cim/iec61970/base/core/BaseVoltage.proto`
  - add reverse VoltageLevel collection
- `proto/zepben/protobuf/cim/iec61970/infiec61970/feeder/Circuit.proto`
  - add reverse Bay association
- `proto/zepben/protobuf/nc/nc-data.proto`
  - add `VoltageLevel` and `Bay` to `NetworkIdentifiable`

### New or updated EWB spec YAML
Create/update matching spec files under `spec/ewb/IEC61970/`:
- `spec/ewb/IEC61970/Base/Core/Bay.yaml`
- `spec/ewb/IEC61970/Base/Core/VoltageLevel.yaml`
- `spec/ewb/IEC61970/Base/Core/Substation.yaml`
- `spec/ewb/IEC61970/Base/Core/BaseVoltage.yaml`
- `spec/ewb/IEC61970/InfIEC61970/Feeder/Circuit.yaml`

### Derived / repository-level updates
- `changelog.md`
- `rdfs/cim_profile_schema.ttl` regeneration or review
- `proto/zepben/proto.lock` only if the change proves breaking

## Implementation order
1. Confirm the Bay/VoltageLevel field layout from the CIM spec and matching sibling proto patterns.
2. Create the two enum protos first so Bay can import them explicitly.
3. Create `VoltageLevel.proto`.
4. Create `Bay.proto`.
5. Patch `Substation.proto`, `BaseVoltage.proto`, and `Circuit.proto` with reverse associations.
6. Add `VoltageLevel` and `Bay` to `nc-data.proto`.
7. Add and update EWB spec YAMLs so the model metadata matches the protos.
8. Update changelog and regenerate derived artifacts.
9. Run the build and verify the new types compile and import cleanly.

## Verification checklist
- Protos compile successfully via the Python build pipeline
- Generated Python stubs include `Bay`, `VoltageLevel`, `BreakerConfiguration`, and `BusbarConfiguration`
- `nc-data.proto` includes both new types in the union with safe field numbers
- Reverse association fields exist on `Substation`, `BaseVoltage`, and `Circuit`
- Spec YAMLs match the proto associations and ancestor structure
- No unwanted renumbering of existing proto fields or union entries

## Notes / pitfalls to avoid
- `google.protobuf.NullValue` imports must be explicit in any proto that uses nullable scalar fields
- Same-package enum protos still need explicit imports in the message proto
- Bay and VoltageLevel are associative containment relationships, not inheritance relationships
- Do not edit generated `_pb2.py` files manually

## Done means
The repo has Bay modeled end-to-end in proto and spec form, the network union can carry the new types, and the build verifies the result.
