# Zepben Protobuf and GRPC definitions
## [0.24.0] - UNRELEASED
### Breaking Changes
* None.

### New Features
* Added new RPCs:
  * `getDiagramObjects`: Get the DiagramObjects for a given mRID.
  * `getCustomersForContainer`: Get the customers for a given EquipmentContainer. 

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
  * `PotentialTransformer`: Instrument transformer (also known as Voltage Transformer) used to measure electrical qualities of the circuit that is being protected and/or monitored.
  * `CurrentTransformerInfo`: Properties of current transformer assets. Extends AssetInfo.
  * `PotentialTransformerInfo`: Properties of potential transformer assets. Extends AssetInfo.
  * `Ratio`: A fraction specified explicitly with a numerator and denominator, which can be used to calculate the quotient.
  * `RecloseSequence`: A reclose sequence (open and close) is defined for each possible reclosure of a breaker.
  * `ProtectionEquipment`: An electrical device designed to respond to input conditions in a prescribed manner and after specified conditions are met to cause contact operation
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
  * `Switch.ratedCurrent`: The maximum continuous current carrying capacity in amps governed by the device material and construction. The attribute shall be a positive value.
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
