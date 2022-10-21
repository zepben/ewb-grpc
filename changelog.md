# Zepben Protobuf and GRPC definitions
## [0.22.0] - UNRELEASED
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
