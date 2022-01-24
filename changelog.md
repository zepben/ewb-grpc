### v0.19.0

##### Breaking Changes

* None.

##### New Features

* Added the following CIM classes/enums:
    * `TransformerConstructionKind`
    * `TransformerFunctionKind`
    * `StreetDetail`

##### Enhancements

* Added fields to `PowerTransformer` to define `constructionKind` and `function`.
* Added fields to `StreetAddress` to define `poBox` and `streetDetail`.
* Added the following fields to `EnergySource`:
    * `isExternalGrid`
    * `r_min`
    * `rn_min`
    * `r0_min`
    * `x_min`
    * `xn_min`
    * `x0_min`
    * `r_max`
    * `rn_max`
    * `r0_max`
    * `x_max`
    * `xn_max`
    * `x0_max`
* Update grpcio to 1.43.0 in setup.py
* Update grpcio-tool to 1.43.0 in setup.py

##### Fixes

* None.

##### Notes

* None.
