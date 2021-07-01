#### Release History

| Version | Released |
| --- | --- |
| [0.15.0](#v0150) | `TBD` |
| [0.14.0](#v0140) | `27 April 2021` |
| [0.13.0](#v0130) | `06 April 2021` |
| [0.12.0](#v0120) | `04 March 2021` |
| [0.11.0](#v0110) | `01 February 2021` |
| [0.10.0](#v0100)| `13 January 2021` |
| [0.9.0](#v090)| `17 December 2020` |
| [0.8.0](#v080) | `10 November 2020` |
| [0.7.0](#v070) | `02 October 2020` |

---

NOTE: This library is not yet stable, and breaking changes should be expected until
a 1.0.0 release.

---

### v0.15.0

##### Breaking Changes
* All `NetworkConsumer` calls that return `NetworkIdentifiedObject` instances
  have been updated to allow repeated values.
* Renamed the following:
    * `getEquipmentForContainer` to `getEquipmentForContainers`
    * `GetEquipmentForContainerRequest` to `GetEquipmentForContainersRequest`
    * `GetEquipmentForContainerResponse` to `GetEquipmentForContainersResponse`
* All services `getIdentifiedObjects` and `NetworkConsumer.getEquipmentForContainers`
  have been updated to take a stream of requests.
* `GetEquipmentForContainersRequest` has been changed to have a repeated mRID field.
* Changed `IdentifiedObject.diagramObjectStyle` to be a string and removed `DiagramObjectStyle` enum.
* Removed unused field `Name.identifiedObjectMRID`.

##### New Features
* Added the following protos:
  * EquivalentBranch
  * EquivalentEquipment
  * NoLoadTest
  * OpenCircuitTest
  * ShortCircuitTest
  * TransformerTest
    
##### Enhancements
* None.

##### Fixes
* None.

##### Notes
* None.

---

### v0.14.0

##### Breaking Changes
* `getNetworkHierarchy` now returns the actual containers rather than a simplified version.

##### New Features
* None.

##### Enhancements
* Circuits and loops have been added to the `getNetworkHierarchy` response.

##### Fixes
* None.

##### Notes
* None.

---

### v0.13.0

##### Breaking Changes
* `TransformerStarImpedance.TransformerEndInfoMRID` has been renamed to `TransformerStarImpedance.transformerEndInfoMRID`.
* `TransformerEnd.transformerStarImpedanceMRID` has been renamed to `TransformerEnd.starImpedanceMRID`.

##### New Features
* Added SwitchStateService and rpc to set current switch state.
* Added dual links for transformer tank and end info.

##### Enhancements
* None.

##### Fixes
* None.

##### Notes
* None.

---

### v0.12.0

##### Breaking Changes
* `Equipment` mRIDs are no longer serialised along with `EquipmentContainer`, `Feeder`, and `OperationalRestriction`.
* `Terminal` mRIDs are no longer serialised along with `ConnectivityNode`
* `GetIdentifiedObjectsResponse` now only contains a `NetworkIdentifiedObject`. `IdentifiedObjectGroup` has been removed and `IdentifiedObject`s will always be sent by themselves.

##### New Features
* New network RPCs `getEquipmentForContainer`, `getCurrentEquipmentForFeeder`, `getEquipmentForRestriction` which allow requesting for all the equipment or current equipment for a given `EquipmentContainer`, `Feeder`, or `OperationalRestriction` respectively.
* New network RPC `getTerminalsForNode` for fetching all `Terminal`s for a given `ConnectivityNode`.
* Added the following protos:
  * LoadBreakSwitch
  * Name
  * NameType
  * TransformerEndInfo
  * TransformerTankInfo
  * TransformerStarImpedance

##### Enhancements
* None.

##### Fixes
* None.

##### Notes
* None.

---

### v0.11.0

##### Breaking Changes
* None.

##### New Features
* Added the following protos:
  * BatteryStateKind
  * BatteryUnit
  * BusbarSection
  * PhotoVoltaicUnit
  * PowerElectronicsConnection
  * PowerElectronicsConnectionPhase
  * PowerElectronicsUnit
  * PowerElectronicsWindUnit

##### Enhancements
* None.

##### Fixes
* None.

##### Notes
* None.

---

### v0.10.0

##### Breaking Changes
* None.

##### New Features
* None.

##### Enhancements
* Created PowerTransformerInfo proto.

##### Fixes
* None.

##### Notes
* None.

---

### v0.9.0

##### Breaking Changes
* None.

##### New Features
* None.

##### Enhancements
* Updated PowerTransformer proto to include transformerUtilisation property.

##### Fixes
* None.

##### Notes
* None.

---

### v0.8.0

##### Breaking Changes
* Updated the customer and diagram consumer services to use getIdentifiedObjects.

##### New Features
* None.

##### Enhancements
* None.

##### Fixes
* None.

##### Notes
* None.

---

### v0.7.0

Initial github release of the evolve protobuf and gRPC definitions.
