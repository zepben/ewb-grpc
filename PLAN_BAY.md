# Plan: Implement Bay in the EWB gRPC CIM Proto Model

## Background

Bay is a core CIM class (IEC 61970-301) representing a collection of power system resources within a substation — conducting equipment, protection relays, measurements, and telemetry. It sits in the containment hierarchy:

```
Substation → VoltageLevel → Bay → SwitchingEquipment (Breaker, Disconnector, etc.)
```

The naming convention referenced in Measurement.proto: `Substation-VoltageLevel-Bay-Switch-Measurement`.

## Current State

**Already exists:**
- `Substation.proto` — has `bayMRIDs`? No, it has `circuitMRIDs` and `loopMRIDs` but no bay field
- `Feeder.proto` — reference for pattern
- `VoltageLevel.yaml` (TC57CIM spec) — defines VoltageLevel with Bay association
- `Bay.yaml` (TC57CIM spec) — defines Bay with attributes and associations
- `BreakerConfiguration.yaml` / `BusbarConfiguration.yaml` — enum definitions
- `Circuit.proto` — has `endBayMRID`? Needs check
- `nc-data.proto` — NetworkIdentifiable union (no Bay yet)

**Does NOT exist:**
- `Bay.proto` — the main proto file
- `VoltageLevel.proto` — VoltageLevel has no proto yet (prerequisite for Bay)
- `BreakerConfiguration.proto` — enum proto
- `BusbarConfiguration.proto` — enum proto
- `spec/ewb/IEC61970/Base/Core/Bay.yaml` — EWB spec for Bay
- `spec/ewb/IEC61970/Base/Core/VoltageLevel.yaml` — EWB spec for VoltageLevel

## Scope

This is a **data model** addition. Bay is queryable via the network service, so it needs to be in the NetworkIdentifiable union.

## Steps

### Step 1: Create BreakerConfiguration enum proto

**File:** `proto/zepben/protobuf/cim/iec61970/base/core/BreakerConfiguration.proto`

An enum with 4 values: `singleBreaker`, `breakerAndAHalf`, `doubleBreaker`, `noBreaker`.

### Step 2: Create BusbarConfiguration enum proto

**File:** `proto/zepben/protobuf/cim/iec61970/base/core/BusbarConfiguration.proto`

An enum with 4 values: `singleBus`, `doubleBus`, `mainWithTransferBus`, `ringBus`.

### Step 3: Create VoltageLevel proto

**File:** `proto/zepben/protobuf/cim/iec61970/base/core/VoltageLevel.proto`

VoltageLevel is a **prerequisite** for Bay.proto — Bay references it. VoltageLevel does not currently exist as a proto file anywhere in the repo.

**Inheritance chain:** IdentifiedObject → PowerSystemResource → ConnectivityNodeContainer → EquipmentContainer → VoltageLevel

**Proto structure:**
```protobuf
syntax = "proto3";
option java_multiple_files = true;
option java_package = "com.zepben.protobuf.cim.iec61970.base.core";
package zepben.protobuf.cim.iec61970.base.core;

import "zepben/protobuf/cim/iec61970/base/core/EquipmentContainer.proto";
import "zepben/protobuf/cim/iec61970/base/core/BaseVoltage.proto";

/**
 * A collection of equipment at one common system voltage forming a switchgear.
 * The equipment typically consists of breakers, busbars, instrumentation,
 * control, regulation and protection devices as well as assemblies of all these.
 */
message VoltageLevel {

    /**
     * EquipmentContainer fields for this VoltageLevel.
     */
    EquipmentContainer ec = 1;

    /**
     * The bus bar's high voltage limit. The limit applies to all equipment
     * and nodes contained in a given VoltageLevel. It is not required that
     * it is exchanged in pair with lowVoltageLimit. It is preferable to use
     * operational VoltageLimit, which prevails, if present.
     */
    string highVoltageLimit = 2;

    /**
     * The bus bar's low voltage limit. The limit applies to all equipment
     * and nodes contained in a given VoltageLevel. It is not required that
     * it is exchanged in pair with highVoltageLimit. It is preferable to use
     * operational VoltageLimit, which prevails, if present.
     */
    string lowVoltageLimit = 3;

    /**
     * The base voltage used for all equipment within the voltage level.
     */
    string baseVoltageMRID = 4;

    /**
     * The bays within this voltage level.
     */
    repeated string bayMRIDs = 5;

    /**
     * The substation of the voltage level.
     */
    string substationMRID = 6;
}
```

**Field numbering rationale:**
- Field 1: embedded parent `EquipmentContainer` (always field 1)
- Fields 2-3: `highVoltageLimit` / `lowVoltageLimit` — scalar string per spec (not nullable in TC57CIM spec, so no oneof/NullValue needed)
- Fields 4-6: mRID references — `baseVoltageMRID`, `bayMRIDs`, `substationMRID`
- Gap left at 7+ for future attributes if spec adds more

**Imports:**
- `EquipmentContainer.proto` — parent class
- `BaseVoltage.proto` — referenced via mRID (not embedded, but import for reference)

**Notes:**
- VoltageLevel is a leaf in the sense that no other existing class extends it in the proto model (the TC57CIM spec lists it as an ancestor of nothing in the descendants list, though `Bay` extends `EquipmentContainer` not `VoltageLevel` — Bay is a sibling of VoltageLevel, not a child. The containment relationship is via the `voltageLevelMRID` field on Bay, not inheritance.)
- Wait — re-check: The TC57CIM spec shows Bay's ancestor is `EquipmentContainer`, not `VoltageLevel`. So Bay and VoltageLevel are siblings (both descend from EquipmentContainer). The containment is logical/associative, not via proto inheritance. This means Bay.proto imports VoltageLevel.proto for the mRID reference, not the other way around.

### Step 4: Create Bay proto

**File:** `proto/zepben/protobuf/cim/iec61970/base/core/Bay.proto`

- Parent: `EquipmentContainer` (embedded as `ec = 1`)
- Fields from spec:
  - `substationMRID` (string) — the containing substation
  - `voltageLevelMRID` (string) — the containing voltage level
  - `circuitMRID` (string) — the Circuit (from Circuit.yaml: `EndBay`)
  - `bayEnergyMeasFlag` (bool, nullable) — presence of energy measurements
  - `bayPowerMeasFlag` (bool, nullable) — presence of active/reactive power measurements
  - `breakerConfiguration` (BreakerConfiguration enum) — switching arrangement
  - `busbarConfiguration` (BusbarConfiguration enum) — busbar layout
- Use Javadoc comments for all fields
- Import `EquipmentContainer.proto`, `BreakerConfiguration.proto`, `BusbarConfiguration.proto`

### Step 5: Update Substation.proto

Substation needs **two** new reverse-reference fields:

**a) Bay reverse reference:**
```protobuf
repeated string bayMRIDs = 7;
```
This is the reverse side of the Bay→Substation association (Bay contains `substationMRID`, Substation should contain `bayMRIDs`).

**b) VoltageLevel reverse reference:**
```protobuf
repeated string voltageLevelMRIDs = 8;
```
From Substation.yaml spec: `source: Substation → target: VoltageLevel` (sourceName: Substation, targetName: VoltageLevels). This is the reverse of VoltageLevel→Substation (VoltageLevel contains `substationMRID`, Substation should contain `voltageLevelMRIDs`).

### Step 5.5: Update BaseVoltage.proto

Add a reverse reference for VoltageLevel:
```protobuf
repeated string voltageLevelMRIDs = 3;
```
From BaseVoltage.yaml spec: `source: BaseVoltage → target: VoltageLevel` (sourceName: BaseVoltage, targetName: VoltageLevel). This is the reverse of VoltageLevel→BaseVoltage (VoltageLevel contains `baseVoltageMRID`, BaseVoltage should contain `voltageLevelMRIDs`).

### Step 6: Update Circuit.proto

Check if Circuit already has a `bayMRID` field. If not, add:
```protobuf
string endBayMRID = N;
```
This is the reverse side of the Bay→Circuit association.

### Step 7: Add Bay to nc-data.proto

- Add import for Bay: `import "zepben/protobuf/cim/iec61970/base/core/Bay.proto";`
- Add import for VoltageLevel: `import "zepben/protobuf/cim/iec61970/base/core/VoltageLevel.proto";`
- Add both types to the `NetworkIdentifiable` oneof union:
  - `VoltageLevel voltageLevel = 92;`
  - `Bay bay = 93;`
  (Use next available field numbers after 91)

### Step 8: Update EWB spec YAML files

**Create:** `spec/ewb/IEC61970/Base/Core/Bay.yaml`
- Follow the EWB spec format (like Feeder.yaml, ConnectivityNode.yaml)
- Include: name, description, attributes, ancestors, associations (Substation, VoltageLevel, Circuit)

**Create:** `spec/ewb/IEC61970/Base/Core/VoltageLevel.yaml`
- Follow the EWB spec format
- Include: name, description, attributes, ancestors, associations (Bay, BaseVoltage, Substation)

### Step 9: Update existing EWB spec YAML files

**Update:** `spec/ewb/IEC61970/Base/Core/Substation.yaml`
- Add Bay association (reverse of Bay→Substation)

**Update:** `spec/ewb/IEC61970/Base/Meas/Measurement.yaml`
- No changes needed — Bay is already referenced in the description text

### Step 10: Regenerate derived artifacts

- Regenerate `rdfs/cim_profile_schema.ttl` (per AGENTS.md checklist)
- Review `proto.lock` — this is a non-breaking change, so leave it alone unless there's a reason to force-update

### Step 11: Build and verify

- Run `python python/build.py --clean && python python/build.py` to regenerate Python stubs
- Install with `cd python && pip install -e .`
- Verify no compilation errors

## Risks & Considerations

1. **VoltageLevel.proto is also missing** — VoltageLevel is a prerequisite for Bay but doesn't exist yet. Both should be done together.

2. **BreakerConfiguration and BusbarConfiguration are enums** — Need to decide if these are standalone proto enums or if they should be integrated into Bay.proto. Standalone is cleaner and matches the pattern of other CIM enums.

3. **Circuit.proto already exists** — Need to check if it has a `endBayMRID` field. The Circuit.yaml spec shows a `EndBay` association, so the reverse reference may already exist.

4. **Service impact** — Adding Bay and VoltageLevel to nc-data.proto is a non-breaking change (only adds new union variants). Existing consumers won't be affected.

5. **Field numbers** — VoltageLevel and Bay should use field numbers starting at 2 (1 is taken by the embedded parent). Substation's new field should be 7 (6 is the highest used).

## Execution Order

1-2 (enums) → 3 (VoltageLevel) → 4 (Bay) → 5 (Substation update) → 6 (Circuit check/update) → 7 (nc-data update) → 8 (spec YAMLs) → 9 (spec updates) → 10 (regenerate) → 11 (build verify)
