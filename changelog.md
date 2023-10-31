# Zepben Protobuf and GRPC definitions
## [0.26.0] - UNRELEASED
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


## [0.25.0] - UNRELEASED

### Breaking Changes

* Renamed hosting capacity `EnergyMeterReport` to `OpenDssReport` with added support for diagnostic reports.
* A bunch of breaking changes to 0.24.0 hosting capacity spec additions. These were not in use so nobody should be effected.

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
    * `CurrentTransformer`: Instrument transformer used to measure electrical qualities of the circuit that is being protected and/or monitored.
    * `PotentialTransformer`: Instrument transformer (also known as Voltage Transformer) used to measure electrical qualities of the circuit that is being
      protected and/or monitored.
    * `CurrentTransformerInfo`: Properties of current transformer assets. Extends AssetInfo.
    * `PotentialTransformerInfo`: Properties of potential transformer assets. Extends AssetInfo.
    * `Ratio`: A fraction specified explicitly with a numerator and denominator, which can be used to calculate the quotient.
    * `RecloseSequence`: A reclose sequence (open and close) is defined for each possible reclosure of a breaker.
    * `ProtectionEquipment`: An electrical device designed to respond to input conditions in a prescribed manner and after specified conditions are met to cause
      contact operation
      or similar abrupt change in associated electric control circuits, or simply to display the detected condition. Protection equipment is associated with
      conducting equipment and usually operate circuit breakers.
    * `CurrentRelay`: A device that checks current flow values in any direction or designated direction.
    * `SwitchInfo`: Switch datasheet information.
    * `CurrentRelayInfo`: Current Relay Datasheet Information.
* Added new enums:
    * `PotentialTransformerKind`: The construction kind of the potential transformer.
    * `ProtectionKind`: The kind of protection being provided by this protection equipment.
* Added new fields:
    * `ProtectedSwitch.breakingCapacity`: The maximum fault current in amps a breaking device can break safely under prescribed conditions of use.
    * `Switch.ratedCurrent`: The maximum continuous current carrying capacity in amps governed by the device material and construction. The attribute shall be a
      positive value.
    * `Breaker.inTransitTime`: The transition time from open to close in seconds.

## [0.22.0] - 2022-10-21

### Breaking Changes

* Renamed `Equipment.currentFeederMRIDs` to `Equipment.currentContainerMRIDs`.

### New Features

* Added `LvFeeder`, an `EquipmentContainer` containing only LV `Equipment`.
* Added the following fields to `GetEquipmentForContainersRequest`:
    * `includeEnergizingContainers`: Specifies whether to include equipment from containers energizing the ones listed in
      `mridList`. This is of the enum type `IncludedEnergizingContainers`, which has three possible values:
        * `EXCLUDE_ENERGIZING_CONTAINERS`: No additional effect (default).
        * `INCLUDE_ENERGIZING_FEEDERS`: Include HV/MV feeders that power LV feeders listed in `mridList`.
        * `INCLUDE_ENERGIZING_SUBSTATIONS`: In addition to `INCLUDE_ENERGIZING_FEEDERS`, include substations that
          energize a HV/MV feeder listed in `mridList` or included via `INCLUDE_ENERGIZING_FEEDERS`.
    * `includeEnergizedContainers`: Specifies whether to include equipment from containers energized by the ones listed in
      `mridList`. This is of the enum type `IncludedEnergizedContainers`, which has three possible values:
        * `EXCLUDE_ENERGIZED_CONTAINERS`: No additional effect (default).
        * `INCLUDE_ENERGIZED_FEEDERS`: Include HV/MV feeders powered by substations listed in `mridList`.
        * `INCLUDE_ENERGIZED_LV_FEEDERS`: In addition to `INCLUDE_ENERGIZED_FEEDERS`, include LV feeders that
          are energizes by a HV/MV feeder listed in `mridList` or included via `INCLUDE_ENERGIZED_FEEDERS`.
