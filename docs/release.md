#### Release History

| Version | Released |
| --- | --- |
| [0.12.0](#v0120) | `TBD` |
| [0.11.0](#v0110) | `01 February 2021` |
| [0.10.0](#v0100)| `13 January 2021` |
| [0.9.0](#v090)| `17 December 2020` |
| [0.8.0](#v080) | `10 November 2020` |
| [0.7.0](#v070) | `02 October 2020` |

---

NOTE: This library is not yet stable, and breaking changes should be expected until
a 1.0.0 release.

---

### v0.12.0

##### Breaking Changes
* `Equipment` mRIDs are no longer serialised along with `EquipmentContainer`, `Feeder`, and `OperationalRestriction`.
* `Terminal` mRIDs are no longer serialised along with `ConnectivityNode`
* `GetIdentifiedObjectsResponse` now only contains a `NetworkIdentifiedObject`. `IdentifiedObjectGroup` has been removed and `IdentifiedObject`s will always be sent by themselves.

##### New Features
* New network RPCs `getEquipmentForContainer`, `getCurrentEquipmentForFeeder`, `getEquipmentForRestriction` which allow requesting for all the equipment or current equipment for a given `EquipmentContainer`, `Feeder`, or `OperationalRestriction` respectively.
* New network RPC `getTerminalsForNode` for fetching all `Terminal`s for a given `ConnectivityNode`.

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
