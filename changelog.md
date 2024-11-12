# Zepben Protobuf and GRPC definitions
## [0.33.0] - UNRELEASED
### Breaking Changes
* Removed the `ProcessingPaused` current state status response due to pause/resume support being dropped.

### New Features
* None.

### Enhancements
* State update batches that are skipped/ignored because they have already been processed in the backlog processing can now return `BatchNotProcessed`.
* Added `StateEventFailure.message` to provide a descriptive message of the failure.
* Added new event failure type `StateEventUnsupportedMrid`, which can be used to indicate a valid element was found, but the operation isn't supported by the
  server. e.g. As of writing, you can't operate switches at the EHV level.

### Fixes
* None.

### Notes
* None.

## [0.32.0] - 2024-12-02
### Breaking Changes
* Renamed `from` and `to` fields in the `GetCurrentStatesRequest` message to `fromTimestamp` and `toTimestamp` to ensure compatibility with Python's reserved
  keywords.

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

### Enhancements
* Added new attributes to `RegulatingControl`
  * `ctPrimary`: Current rating of the CT, expressed in terms of the current (in Amperes) that flows in the Primary where the 'Primary' is the conductor being monitored. 
    It ensures proper operation of the regulating equipment by providing the necessary current references for control actions. An important side effect of this 
    current value is that it also defines the current value at which the full LDC R and X voltages are applied by the controller, where enabled.
  * `minTargetDeadband`: This is the minimum allowable range for discrete control in regulating devices, used to prevent frequent control actions and promote 
    operational stability. This attribute sets a baseline range within which no adjustments are made, applicable across various devices like voltage regulators, 
    shunt compensators, or battery units.
* Added repeated attribute of `batteryControls` to `BatteryUnit`.
* Added repeated attribute of `endDeviceFunctions` to `EndDevice`.

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
