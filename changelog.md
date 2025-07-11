# Zepben Protobuf and GRPC definitions
## [1.0.0] - UNRELEASED
### Breaking Changes
* Renamed the JVM package to:
  ```xml
  <dependency>
      <groupId>com.zepben</groupId>
      <artifactId>protobuf</artifactId>
  </dependency>
  ```
* Relocated the following classes into the Zepben extensions area, marking them as `[ZBEX]`:
  * `DistanceRelay`: `cim.iec61970.base.protection` -> `cim.extensions.iec61970.base.protection`.
  * `EvChargingUnit`: `cim.iec61970.infiec61970.wires.generation.production` -> `cim.extensions.iec61970.base.generation.production`.
  * `Loop`: `cim.iec61970.infiec61970.feeder` -> `cim.extensions.iec61970.base.feeder`.
  * `LvFeeder`: `cim.iec61970.infiec61970.feeder` -> `cim.extensions.iec61970.base.feeder`.
  * `PowerDirectionKind`: `cim.iec61970.infiec61970.protection` -> `cim.extensions.iec61970.base.protection`.
  * `ProtectionKind`: `cim.iec61970.infiec61970.protection` -> `cim.extensions.iec61970.base.protection`.
  * `ProtectionRelayFunction`: `cim.iec61970.base.protection` -> `cim.extensions.iec61970.base.protection`.
  * `ProtectionRelayScheme`: `cim.iec61970.base.protection` -> `cim.extensions.iec61970.base.protection`.
  * `ProtectionRelaySystem`: `cim.iec61970.base.protection` -> `cim.extensions.iec61970.base.protection`.
  * `RelayInfo`: `cim.iec61968.infiec61968.infassetinfo` -> `cim.extensions.iec61968.assetinfo`.
  * `RelaySetting`: `cim.iec61970.base.protection` -> `cim.extensions.iec61970.base.protection`.
  * `Site`: `cim.iec61970.base.core` -> `cim.extensions.iec61970.base.core`.
  * `TransformerCoolingType`: `cim.iec61970.base.wires` -> `cim.extensions.iec61970.base.wires`.
  * `TransformerEndRatedS`: `cim.iec61970.base.wires` -> `cim.extensions.iec61970.base.wires`.
  * `VectorGroup`: `cim.iec61970.base.wires` -> `cim.extensions.iec61970.base.wires`.
  * `VoltageRelay`: `cim.iec61970.base.protection` -> `cim.extensions.iec61970.base.protection`.
  * `WindingConnection`: `cim.iec61970.base.wires.winding` -> `cim.iec61970.base.wires`.
* Updated the values of the following enums to conform to Protobuf standard naming:
  * `BatteryControlMode`
  * `BatteryStateKind`
  * `CustomerKind`
  * `DiagramStyle`
  * `EndDeviceFunctionKind`
  * `FeederDirection`
  * `IncludedEnergizedContainers`
  * `IncludedEnergizingContainers`
  * `LogLevel`
  * `LogSource`
  * `NetworkState`
  * `OrientationKind`
  * `PhaseCode`
  * `PhaseShuntConnectionKind`
  * `PotentialTransformerKind`
  * `PowerDirectionKind`
  * `ProtectionKind`
  * `RegulatingControlModeKind`
  * `SinglePhaseKind`
  * `StreetlightLampKind`
  * `SVCControlMode`
  * `SwitchAction`
  * `SynchronousMachineKind`
  * `TransformerConstructionKind`
  * `TransformerCoolingType`
  * `TransformerFunctionKind`
  * `UnitSymbol`
  * `VectorGroup`
  * `WindingConnection`
  * `WireMaterialKind`
* Renamed the following enum values (in addition to the gRPC scoping changes):
  * `IncludedEnergizingContainers.EXCLUDED` -> `IncludedEnergizingContainers.NONE`
  * `IncludedEnergizedContainers.EXCLUDED` -> `IncludedEnergizedContainers.NONE`
* Relocated the following classes that were in the wrong packages:
  * `Pole`: `cim.iec61968.assets` -> `cim.iec61968.infiec61968.infassets`.
  * `StreetlightLampKind`: `cim.iec61968.assets` -> `cim.iec61968.infiec61968.infassets`.
  * All classes in the incorrectly located `cim.iec61970.base.wires.generation.production` -> `cim.iec61970.base.generation.production`.
* Renumbered the protobuf fields for:
  * `AcLineSegment`
  * `Control`
  * `Diagram`
  * `TransformerEnd`

### New Features
* None.

### Enhancements
* Updated profile manager processing to:
  * Handle lists of types in specs.
  * Split class descriptions with `<br/>`.
  * Use correct case for `Descendants` section.

### Fixes
* Fixed typos in some class docstrings and specs.
* Added missing descriptions in some class docstrings and specs.
* Added missing ancestor/descendant links in some specs.
* Marked some extensions properties and classes with `[ZBEX]` that were missing them (might still be more). In addition to the ones moved into the extensions
  package:
  * `PhaseCode.Y`
  * `PhaseCode.YN`
  * `PowerElectronicsConnection.inverterStandard`
  * `PowerElectronicsConnection.sustainOpOvervoltLimit`
  * `PowerElectronicsConnection.stopAtOverFreq`
  * `PowerElectronicsConnection.stopAtUnderFreq`
  * `PowerElectronicsConnection.invVoltWattRespMode`
  * `PowerElectronicsConnection.invWattRespV1`
  * `PowerElectronicsConnection.invWattRespV2`
  * `PowerElectronicsConnection.invWattRespV3`
  * `PowerElectronicsConnection.invWattRespV4`
  * `PowerElectronicsConnection.invWattRespPAtV1`
  * `PowerElectronicsConnection.invWattRespPAtV2`
  * `PowerElectronicsConnection.invWattRespPAtV3`
  * `PowerElectronicsConnection.invWattRespPAtV4`
  * `PowerElectronicsConnection.invVoltVarRespMode`
  * `PowerElectronicsConnection.invVarRespV1`
  * `PowerElectronicsConnection.invVarRespV2`
  * `PowerElectronicsConnection.invVarRespV3`
  * `PowerElectronicsConnection.invVarRespV4`
  * `PowerElectronicsConnection.invVarRespQAtV1`
  * `PowerElectronicsConnection.invVarRespQAtV2`
  * `PowerElectronicsConnection.invVarRespQAtV3`
  * `PowerElectronicsConnection.invVarRespQAtV4`
  * `PowerElectronicsConnection.invReactivePowerMode`
  * `PowerElectronicsConnection.invFixReactivePower`
  * `PowerTransformerEnd.ratings`
  * `RegulatingControl.ratedCurrent`
  * `Sensor.relayFunctions`
  * `UsagePoint.approvedInverterCapacity`

### Notes
* Added a note to the protection extension objects that were modelled off a CIM working groups discussions.

## [0.37.0] - 2025-05-07
### Breaking Changes
* Downgrade python grpcio/protobuf versions (grpcio to `1.61.3`, protobuf to `4.25.7`).

### New Features
* None.

### Enhancements
* None.

### Fixes
* None.

### Notes
* None.

## [0.36.0] - 2025-04-28
### Breaking Changes
* Super pom updated to 0.38.0 which introduces:
  * Latest versions of protobuf (4.30.2 for java and 5.26.2 for python)
  * Latest versions of gRPC (1.71.0)

### New Features
* None.

### Enhancements
* None.

### Fixes
* None.

### Notes
* None.

## [0.35.0] - 2025-04-24
### Breaking Changes
* None.

### New Features
* Added relationships between `Assets` and `PowerSystemResources`.

### Enhancements
* None.

### Fixes
* From 0.34.1:
  * Added `checkConnection` methods to all services. This will return an empty response for SDK connection tests.
  * Changed AddJumperEvent to not use reserved words.

### Notes
* None.

## [0.34.1] - 2025-01-23
### Fixes
* Added `checkConnection` methods to all services. This will return an empty response for SDK connection tests.
* Changed AddJumperEvent to not use reserved words.

## [0.34.0] - 2025-01-21
### Breaking Changes
* None.

### New Features
* Added New Classes
  * `Clamp`: A Clamp is a galvanic connection at a line segment where other equipment is connected. A Clamp does not cut the line segment. A Clamp is
    ConductingEquipment and has one Terminal with an associated ConnectivityNode. Any other ConductingEquipment can be connected to the Clamp ConnectivityNode.
  * `Cut`: A cut separates a line segment into two parts. The cut appears as a switch inserted between these two parts and connects them together. As the cut is
    normally open there is no galvanic connection between the two line segment parts. But it is possible to close the cut to get galvanic connection. The cut
    terminals are oriented towards the line segment terminals with the same sequence number. Hence the cut terminal with sequence number equal to 1 is oriented
    to the line segment's terminal with sequence number equal to 1. The cut terminals also act as connection points for jumpers and other equipment, e.g. a
    mobile generator. To enable this, connectivity nodes are placed at the cut terminals. Once the connectivity nodes are in place any conducting equipment can
    be connected at them.
* Adds FeederDirection.CONNECTOR for single-terminal busbar support

### Enhancements
* None.

### Fixes
* None.

### Notes
* None.

## [0.33.0] - 2025-01-05
### Breaking Changes
* Removed the `ProcessingPaused` current state status response due to pause/resume support being dropped.
* Corrected `AcLineSegment.perLengthSequenceImpedanceMRID` to `AcLineSegment.perLengthImpedanceMRID`.
* Removed `getCurrentEquipmentForFeeder` as its functionality is now incorporated in `getEquipmentForContainers`.

### New Features
* Added New Classes
  * `StaticVarCompensator`: A facility for providing variable and controllable shunt reactive power. The SVC typically consists of a stepdown transformer,
    filter, thyristor-controlled reactor, and thyristor-switched capacitor arms. The SVC may operate in fixed MVar output mode or in voltage control mode.
    When in voltage control mode, the output of the SVC will be proportional to the deviation of voltage at the controlled bus from the voltage setpoint.
    The SVC characteristic slope defines the proportion. If the voltage at the controlled bus is equal to the voltage setpoint, the SVC MVar output is zero.
  * `SVCControlMode`: Static VAr Compensator control mode.
  * `EndDeviceFunction`: Function performed by an end device such as a meter, communication equipment, controllers, etc.
  * `PanDemandResponseFunction`: PAN function that an end device supports, distinguished by 'kind'.
  * `ControlledAppliance`: Appliance controlled with a PAN device control.
  * `EndDeviceFunctionKind`: Kind of end device function.
  * `AssetFunction`: Function performed by an asset.
  * `BatteryControl`: Describes behaviour specific to controlling batteries.
  * `PerLengthPhaseImpedance`: Impedance and admittance parameters per unit length for n-wire unbalanced lines, in matrix form.
  * `PhaseImpedanceData`: Impedance and conductance matrix element values. The diagonal elements are described by the elements having the same toPhase and
    fromPhase value an the off diagonal elements have different toPhase and fromPhase values.

### Enhancements
* State update batches that are skipped/ignored because they have already been processed in the backlog processing can now return `BatchNotProcessed`.
* Added `StateEventFailure.message` to provide a descriptive message of the failure.
* Added new event failure type `StateEventUnsupportedMrid`, which can be used to indicate a valid element was found, but the operation isn't supported by the
  server. e.g. As of writing, you can't operate switches at the EHV level.
* Added new attributes to `RegulatingControl`
  * `ctPrimary`: Current rating of the CT, expressed in terms of the current (in Amperes) that flows in the Primary where the 'Primary' is the conductor being
    monitored. It ensures proper operation of the regulating equipment by providing the necessary current references for control actions. An important side
    effect of this current value is that it also defines the current value at which the full LDC R and X voltages are applied by the controller, where enabled.
  * `minTargetDeadband`: This is the minimum allowable range for discrete control in regulating devices, used to prevent frequent control actions and promote
    operational stability. This attribute sets a baseline range within which no adjustments are made, applicable across various devices like voltage regulators,
    shunt compensators, or battery units.
* Added repeated attribute of `batteryControls` to `BatteryUnit`.
* Added repeated attribute of `endDeviceFunctions` to `EndDevice`.
* Added the energized relationship for the current state of network between `Feeder` and `LvFeeder`.
* Updated `getEquipmentForContainers` to allow requesting normal, current or all equipments.

### Fixes
* None.

### Notes
* None.

## [0.32.0] - 2024-12-02
### Breaking Changes
* Renamed `from` and `to` fields in the `GetCurrentStatesRequest` message to `fromTimestamp` and `toTimestamp` to ensure compatibility with Python's reserved
  keywords.

### New Features
* None.

### Enhancements
* None.

### Fixes
* None.

### Notes
* None.

## [0.31.0] - 2024-10-18
### Breaking Changes
* Updated the hosting capacity 'Job' and 'Syf' protos to support the configuration for the generator, executor, and result processor modules be passed as base64
  encoded json objects.
* `Switch.ratedCurrent` has been converted to a `double` (used to be an `integer`). Type safe languages will need to be updated to support floating point
  arithmatic/syntax.
* Refactored switch state events from `com.zepben.protobuf.nm` to `com.zepben.protobuf.ns`.

### New Features
* Added New Classes
  * `RotatingMachine`: A rotating machine which may be used as a generator or motor.
  * `SynchronousMachine`: An electromechanical device that operates with shaft rotating synchronously with the network. It is a single machine operating
    either as a generator or synchronous condenser or pump.
  * `SynchronousMachineKind`: Synchronous machine type.
  * `Curve`: A multipurpose curve or functional relationship between an independent variable (X-axis) and dependent (Y-axis) variables.
  * `CurveData`: Multi-purpose data points for defining a curve. The use of this generic class is discouraged if a more specific class can be used to specify
    the X and Y axis values along with their specific data types.
  * `ReactiveCapabilityCurve`: Reactive power rating envelope versus the synchronous machine's active power, in both the generating and motoring modes.
    For each active power value there is a corresponding high and low reactive power limit value. Typically there will be a separate curve for each coolant
    condition, such as hydrogen pressure. The Y1 axis values represent reactive minimum and the Y2 axis values represent reactive maximum.
  * `EarthFaultCompensator`: A conducting equipment used to represent a connection to ground which is typically used to compensate earth faults. An earth fault
    compensator device modelled with a single terminal implies a second terminal solidly connected to ground. If two terminals are modelled, the ground is not
    assumed and normal connection rules apply.
  * `GroundingImpedance`: A fixed impedance device used for grounding.
  * `PetersenCoil`: A variable impedance device normally used to offset line charging during single line faults in an ungrounded section of network.
  * Added new 'failure' type to OpendssReport.
* Added `OpenDssReportBatch`
* Added `interventionConfig` to both `Job` and `Syf`.

### Enhancements
* Add `phaseCode` to `UsagePoint` to record the phase data of the Usage Point.
* Add docker-based C lib bulder;
* Add `make container-lib` option to build Hosting Capacity C libraries in a container

### Fixes
* Update java to superpom 0.36.4 (which brings dokka 1.9.20).

### Notes
* None.

## [0.30.0] - 2024-05-29
### Breaking Changes
* None.

### New Features
* None.

### Enhancements
* Added `specialNeed` to `Customer` to capture any special needs of the customer, e.g. life support.

### Fixes
* None.

### Notes
* None.


## [0.29.0] - 2024-05-15

### Breaking Changes

* None.

### New Features

* None.

### Enhancements

* Added `designTemperature` and `designRating` to `Conductor` to capture limitations in the conductor based on the
  network design and physical surrounds of the conductor.

### Fixes

* None.

### Notes

* None.

## [0.28.0] - 2024-05-14

### Breaking Changes

* This is the last release using evolve-grpc, future releases will be made as ewb-grpc. This will impact the JVM and C#
  packages, but will not impact the python package which will continue to be released as `zepben.protobuf`

### New Features

* None.

### Enhancements

* None.

### Fixes

* None.

### Notes

* None.

## [0.27.0] - 20240408

### Breaking Changes

* Removed `ProtectionEquipment`.
  * Change of inheritance: `CurrentRelay` &rarr; `ProtectionEquipment`.
    becomes `CurrentRelay` &rarr; `ProtectionRelayFunction`.
  * Removed symmetric relation `ProtectionEquipment` &harr; `ProtectedSwitch`.
* Renamed `CurrentRelayInfo` to `RelayInfo`.
* Reworked values for enumerable type `ProtectionKind`.
* `LoadShape`: added reactive power values to LoadShape.

### New Features

* Added new messages and fields to support advanced modelling of protection relays:
  * `SeriesCompensator`: A series capacitor or reactor or an AC transmission line without charging susceptance.
  * `Ground`: A point where the system is grounded used for connecting conducting equipment to ground.
  * `GroundDisconnector`: A manually operated or motor operated mechanical switching device used for isolating a
    circuit
    or equipment from ground.
  * `ProtectionRelayScheme`: A scheme that a group of relay functions implement. For example, typically schemes are
    primary and secondary, or main and failsafe.
  * `ProtectionRelayFunction`: A function that a relay implements to protect equipment.
  * `ProtectionRelaySystem`: A relay system for controlling `ProtectedSwitch`es.
  * `RelaySetting`: The threshold settings for a given relay.
  * `VoltageRelay`: A device that detects when the voltage in an AC circuit reaches a preset voltage.
  * `DistanceRelay`: A protective device used in power systems that measures the impedance of a transmission line to
    determine the distance to a fault, and initiates circuit breaker tripping to isolate the faulty
    section and safeguard the power system.
  * `RelayInfo.recloseFast`: True if recloseDelays are associated with a fast Curve, False otherwise.
  * `RegulatingControl.ratedCurrent`: The rated current of associated CT in amps for a RegulatingControl.

### Enhancements

* Added MeasurementZoneInfo to the Syf proto message.
* Bumped protobuf and gRPC versions:
  * JVM (Maven): `com.google.protobuf:*:3.24.2`, `io.grpc:*:1.59.1`
  * Python (setup.py): `protobuf==4.24.2`, `grpcio==1.59.3`, `grpcio-tools==1.59.3`
  * C# (NuGet): `Google.Protobuf` version `3.24.2`, `Grpc.*` version `2.46.6`

### Fixes

* None.

### Notes

* None.

## [0.26.0] - 20231103

### Breaking Changes

* None.

### New Features

* Support passing configuration for results in Syf and Job messages.
* Support passing arbitrary one-off configuration for hosting capacity processors in Job and Syf messages.
* Added load data date time information to ModelConfig and Syf messages.
* Added load data timezone information to ModelConfig and Syf messages.
* Added calibration flag to ModelConfig message.
* Added new RPCs:
  * `getMetadata`: Get the metadata related to the service. Added to `CustomerConsumer`, `DiagramConsumer`,
    and `NetworkConsumer`.
* Added new message:
  * `GetMetadataRequest`
  * `GetMetadataResponse`
  * `DataSource`
* Added `MaybeModel` to support signalling the end of a sequence of `Model` messages.

### Enhancements

* None.

### Fixes

* None.

### Notes

* None.

## [0.25.0] - 2024-05-15

### Breaking Changes

* Renamed hosting capacity `EnergyMeterReport` to `OpenDssReport` with added support for diagnostic reports.
* A bunch of breaking changes to 0.24.0 hosting capacity spec additions. These were not in use so nobody should be
  effected.

### New Features

* `ratedS` has been deprecated from PowerTransformerEnd. Use `ratings` instead.
* Added configs to `Job` message for CIM to OpenDSS translation and OpenDSS solve parameters.
* Added the following metadata fields to the hosting capacity `Job` message:
  * `normVMinPu`
  * `normVMaxPu`
  * `resultsDetailLevel`
* Added `LogMessage` to the hosting capacity module, which is used to carry logs between HC processes.

### Enhancements

* None.

### Fixes

* None.

### Notes

* None.

## [0.24.0] - 2023-04-30

### Breaking Changes

* Removed `RecloseSequences` message.

### New Features

* Added new RPCs:
  * `getDiagramObjects`: Get the DiagramObjects for a given mRID.
  * `getCustomersForContainer`: Get the customers for a given EquipmentContainer.
* Added the following messages for hosting capacity:
  * `Syf` and `Job` messages for hosting capacity control.
  * `Model`, `LoadShape` and `EnergyMeter` report messages for streaming results from OpenDSS.
* Added `Job` message for hosting capacity, which describes a hosting capacity run for a single feeder.
* Added CIM enums:
  * `PowerDirectionKind`
  * `RegulatingControlModeKind`
* Added CIM message:
  * `RegulatingControl`
  * `TapChangerControl`
  * `EvChargingUnit`
* Added new fields to:
  * `Equipment`
  * `PowerElectronicsConnection`
  * `ProtectionEquipment`
  * `TapChanger`
  * `UsagePoint`

### Enhancements

* None.

### Fixes

* None.

### Notes

* None.

## [0.23.0] - 2023-02-07

### Breaking Changes

* None

### New Features

* Added new messages:
  * `Sensor`: AuxiliaryEquipment that transform a measured quantity into signals.
  * `CurrentTransformer`: Instrument transformer used to measure electrical qualities of the circuit that is being
    protected and/or monitored.
  * `PotentialTransformer`: Instrument transformer (also known as Voltage Transformer) used to measure electrical
    qualities of the circuit that is being
    protected and/or monitored.
  * `CurrentTransformerInfo`: Properties of current transformer assets. Extends AssetInfo.
  * `PotentialTransformerInfo`: Properties of potential transformer assets. Extends AssetInfo.
  * `Ratio`: A fraction specified explicitly with a numerator and denominator, which can be used to calculate the
    quotient.
  * `RecloseSequence`: A reclose sequence (open and close) is defined for each possible reclosure of a breaker.
  * `ProtectionEquipment`: An electrical device designed to respond to input conditions in a prescribed manner and
    after specified conditions are met to cause
    contact operation
    or similar abrupt change in associated electric control circuits, or simply to display the detected condition.
    Protection equipment is associated with
    conducting equipment and usually operate circuit breakers.
  * `CurrentRelay`: A device that checks current flow values in any direction or designated direction.
  * `SwitchInfo`: Switch datasheet information.
  * `CurrentRelayInfo`: Current Relay Datasheet Information.
* Added new enums:
  * `PotentialTransformerKind`: The construction kind of the potential transformer.
  * `ProtectionKind`: The kind of protection being provided by this protection equipment.
* Added new fields:
  * `ProtectedSwitch.breakingCapacity`: The maximum fault current in amps a breaking device can break safely under
    prescribed conditions of use.
  * `Switch.ratedCurrent`: The maximum continuous current carrying capacity in amps governed by the device material
    and construction. The attribute shall be a
    positive value.
  * `Breaker.inTransitTime`: The transition time from open to close in seconds.

## [0.22.0] - 2022-10-21

### Breaking Changes

* Renamed `Equipment.currentFeederMRIDs` to `Equipment.currentContainerMRIDs`.

### New Features

* Added `LvFeeder`, an `EquipmentContainer` containing only LV `Equipment`.
* Added the following fields to `GetEquipmentForContainersRequest`:
  * `includeEnergizingContainers`: Specifies whether to include equipment from containers energizing the ones listed
    in
    `mridList`. This is of the enum type `IncludedEnergizingContainers`, which has three possible values:
    * `EXCLUDE_ENERGIZING_CONTAINERS`: No additional effect (default).
    * `INCLUDE_ENERGIZING_FEEDERS`: Include HV/MV feeders that power LV feeders listed in `mridList`.
    * `INCLUDE_ENERGIZING_SUBSTATIONS`: In addition to `INCLUDE_ENERGIZING_FEEDERS`, include substations that
      energize a HV/MV feeder listed in `mridList` or included via `INCLUDE_ENERGIZING_FEEDERS`.
  * `includeEnergizedContainers`: Specifies whether to include equipment from containers energized by the ones listed
    in
    `mridList`. This is of the enum type `IncludedEnergizedContainers`, which has three possible values:
    * `EXCLUDE_ENERGIZED_CONTAINERS`: No additional effect (default).
    * `INCLUDE_ENERGIZED_FEEDERS`: Include HV/MV feeders powered by substations listed in `mridList`.
    * `INCLUDE_ENERGIZED_LV_FEEDERS`: In addition to `INCLUDE_ENERGIZED_FEEDERS`, include LV feeders that
      are energizes by a HV/MV feeder listed in `mridList` or included via `INCLUDE_ENERGIZED_FEEDERS`.
