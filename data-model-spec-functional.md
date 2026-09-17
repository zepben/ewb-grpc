# Context

CDPSM CIM everywhere


# TODO:

1. Add TapSchedule
2. Figure out what classes we should actually implement for timeseries data - cover SCADA (measurement points), load. Taps should be handled directly in model (profile only via TapSchedule, not stored in timeseries database).

# Decisions

1. We do absolutes for everything (that goes through EWB), and user provides absolutes via SDK for all measurement values.
   a. Fallback if they need is reference timestamp/point on sincal side.


# Questions

1. What voltage schedule/profile...?
2. Nothing covering current?
3. ExternalNetworkInjection doesn't have enough values... can we just exclude and use EnergySource only?


# Functional Package Scope

This scope contains four components:

1. Ingestion of metering timeseries data via SDK
2. Validation and transfer of network model databases into EWB via SDK.
3. CDPSM XML export from SDK.
4. Data model changes to support CDPSM Functional package.
5. Zone sub modelling (the surprise)

# Ingestion of Metering timeseries data

Metering classes will be added to the SDK which link UsagePoints/Meters to MeterReadings, which support timeseries data.
Helper functions will be added to the SDK to allow a user to supply a meter ID, NMI, or other ID and a set of Readings to associate with that meter/NMI/UsagePoint.
As an example a function signature could look like:

    createReadingsFor(id: String, readings: List[Reading]): MeterReadings

Where MeterReadings is a data class containing the UsagePoint and corresponding MeterReading + Readings.
Another helper function will be added to support serialising and sending these readings to EWB, at which point they will be ingested into
the EWB timeseries database. This will be intended as a batch process that gets run daily to load in the last days readings for all

    sendReadings(meterReadings: List[MeterReadings])

Once readings have been sent and processed, they will be accessible through the EWB load APIs for querying.

## Data model change spec for meter data ingestion

### BaseReading
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [BaseReading](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61968/Metering/BaseReading/) |
| **Package:**      | iec61968.metering |
| **Parent Class:** | [MeasurementValue](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Meas/MeasurementValue) |
| **Description:**  | Common underlying class for Reading and IntervalReading. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

#### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| value | Double? | Value of this reading. | False |
| reportedDateTime | DateTime | Sequence number or time stamp for this reading value. | False |

#### Relationships
None

### Reading
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [Reading](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61968/Metering/Reading/) |
| **Package:**      | iec61968.metering |
| **Parent Class:** | [BaseReading](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61968/Metering/BaseReading) |
| **Description:**  | Specific data captured of a continuous stream of data sampled at specified intervals. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

#### Attributes
None

#### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| MeterReadings | MeterReading | 0..* | All meter readings (sets of values) containing this reading value. | Direct |

### MeterReading
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [MeterReading](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61968/Metering/MeterReading/) |
| **Package:**      | iec61968.metering |
| **Parent Class:** | [IdentifiedObject](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/IdentifiedObject) |
| **Description:**  | Set of values obtained from the meter. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

#### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| valuesInterval | DateTimeInterval? | Date and time interval of the data items contained within this meter reading. | False |

#### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| Readings | Reading | 0..* | All reading values contained within this meter reading. | Direct |

### UsagePoint
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [UsagePoint](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61968/Metering/UsagePoint/) |
| **Package:**      | iec61968.metering |
| **Parent Class:** | [IdentifiedObject](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/IdentifiedObject) |
| **Description:**  | Logical or physical point in the network to which consumption or production is attributed. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

#### Attributes
None

#### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| MeterReadings | MeterReading | 0..* | All meter readings obtained from this usage point. | Direct |

### Meter
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [Meter](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61968/Metering/Meter/) |
| **Package:**      | iec61968.metering |
| **Parent Class:** | [EndDevice](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61968/Metering/EndDevice) |
| **Description:**  | Physical object that measures, computes, and records the flow of electricity, gas, water, or other commodities, and communicates that data to outside systems. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

#### Attributes
None

#### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| MeterReadings | MeterReading | 0..* | All meter readings provided by this meter. | Direct |

# Validation and transfer of network data

Functionality will be add to the SDK so a user building a network model can validate their populated `Services` classes
to ensure EWB will start successfully. This will entail validation similar to EWB startup (loading all relationships, tracing feeders, setting phases), plus
other basic validation checks to ensure the network meets EWB minimum standards.

Helper functionality will also be added so that a valid network model can be transferred to the hosted environment.
This will be in the form of a new helper function similar to the below:

    fun uploadEwbServices(services: Services, ewbConnectionInfo: EwbConnectionInfo, triggerApply: Boolean)

Once uploaded the model will appear in the admin UI and be ready for selection, and if triggerApply is true, EWB will be restarted immediately with the
uploaded services.


# CDPSM XML Export

To meet the requirement of having a CDPSM CIM XML available for use with other tools, we will create a separate tool for exporting
CDPSM XML from the server. A user will be able to run the tool pointing at a EWB server, and receive XML files broken down per Feeder

# Functional package

Below contains the data model change specification for the CDPSM Functional package.

## GeneratingUnit
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [GeneratingUnit](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Generation/Production/GeneratingUnit/) |
| **Package:**      | iec61970.base.generation.production |
| **Parent Class:** | [Equipment](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/Equipment) |
| **Description:**  | A single or set of synchronous machines for converting mechanical power into alternating-current power. For example, individual machines within a set may be defined for scheduling purposes while a single control signal is derived for the set. In this case there would be a GeneratingUnit for each member of the set and an additional GeneratingUnit corresponding to the set. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| totalEfficiency | Double | The efficiency of the unit in converting the fuel into electrical energy. [percent (0.0,1.0)] | False |
| initialP | Double | Default Initial active power which is used to store a powerflow result for the initial active power for this unit in this network configuration. [watts] | False |
| ratedNetMaxP | Double | The net rated maximum capacity determined by subtracting the auxiliary power used to operate the internal plant machinery from the rated gross maximum capacity. | False |
| baseP | Double | For dispatchable units, this value represents the economic active power basepoint; for units that are not dispatchable, this value represents the fixed generation value. The value must be between the operating low and high limits. [watts] | False |
| governorSCD | Double | Governor Speed Changer Droop. This is the change in generator power output divided by the change in frequency normalized by the nominal power of the generator and the nominal frequency and expressed in percent and negated. A positive value of speed change droop provides additional generator output upon a drop in frequency. [percent (0.0,1.0)] | False |
| maxOperatingP | Double | This is the maximum operating active power limit the dispatcher can enter for this unit. [watts] | False |
| minOperatingP | Double | This is the minimum operating active power limit the dispatcher can enter for this unit. [watts] | False |
| nominalP | Double | The nominal power of the generating unit. Used to give precise meaning to percentage based attributes such as the governor speed change droop (governorSCD attribute). The attribute shall be a positive value equal or less than RotatingMachine.ratedS. [watts] | False |
| normalPF | Double | Generating unit economic participation factor. | False |
| ratedGrossMaxP | Double | The unit's gross rated maximum capacity (book value). [watts] | False |
| ratedGrossMinP | Double | The gross rated minimum generation level which the unit can safely operate at while delivering power to the transmission grid. [watts] | False |
| shortPF | Double | Generating unit short term economic participation factor. | False |

### Relationships
None

## HydroGeneratingUnit
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [HydroGeneratingUnit](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Generation/Production/HydroGeneratingUnit/) |
| **Package:**      | iec61970.base.generation.production |
| **Parent Class:** | [GeneratingUnit](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Generation/Production/GeneratingUnit) |
| **Description:**  | A generating unit whose prime mover is a hydraulic turbine (e.g., Francis, Pelton, Kaplan). |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| hydroPowerPlant | HydroPowerPlant | 0..1 | The hydro generating unit belongs to a hydro power plant. | Direct |

## HydroPowerPlant
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [HydroPowerPlant](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Generation/Production/HydroPowerPlant/) |
| **Package:**      | iec61970.base.generation.production |
| **Parent Class:** | [PowerSystemResource](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/PowerSystemResource) |
| **Description:**  | A hydro power station which can generate or pump. When generating, the generator turbines receive water from an upper reservoir. When pumping, the pumps receive their water from a lower reservoir. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| hydroPlantStorageType | HydroPlantStorageKind | The type of hydro power plant water storage. | False |

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| hydroPumps | HydroPump | 0..* | The hydro pump may be a member of a pumped storage plant or a pump for distributing water. | Direct |

## HydroPump
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [HydroPump](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Generation/Production/HydroPump/) |
| **Package:**      | iec61970.base.generation.production |
| **Parent Class:** | [Equipment](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/Equipment) |
| **Description:**  | A synchronous motor-driven pump, typically associated with a pumped storage plant. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| hydroPowerPlant | HydroPowerPlant | 0..1 | The hydro pump may be a member of a pumped storage plant or a pump for distributing water. | Direct |
| rotatingMachine | RotatingMachine | 1..1 | The synchronous machine drives the turbine which moves the water from a low elevation to a higher elevation. The direction of machine rotation for pumping may or may not be the same as for generating. | Direct |

## SolarGeneratingUnit
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [SolarGeneratingUnit](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Generation/Production/SolarGeneratingUnit/) |
| **Package:**      | iec61970.base.generation.production |
| **Parent Class:** | [GeneratingUnit](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Generation/Production/GeneratingUnit) |
| **Description:**  | A solar thermal generating unit. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
None

## StationSupply
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [StationSupply](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/StationSupply/) |
| **Package:**      | iec61970.base.wires |
| **Parent Class:** | [EnergyConsumer](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/EnergyConsumer) |
| **Description:**  | Station supply with load derived from the station output. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
None

## ThermalGeneratingUnit
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [ThermalGeneratingUnit](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Generation/Production/ThermalGeneratingUnit/) |
| **Package:**      | iec61970.base.generation.production |
| **Parent Class:** | [GeneratingUnit](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Generation/Production/GeneratingUnit) |
| **Description:**  | A generating unit whose prime mover could be a steam turbine, combustion turbine, or diesel engine. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
None

## WindGeneratingUnit
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [WindGeneratingUnit](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Generation/Production/WindGeneratingUnit/) |
| **Package:**      | iec61970.base.generation.production |
| **Parent Class:** | [GeneratingUnit](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Generation/Production/GeneratingUnit) |
| **Description:**  | A wind driven generating unit. May be used to represent a single turbine or an aggregation. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
None

# Equivalents

## EquivalentInjection
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [EquivalentInjection](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Equivalents/EquivalentInjection/) |
| **Package:**      | iec61970.base.equivalents |
| **Parent Class:** | [EquivalentEquipment](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Equivalents/EquivalentEquipment) |
| **Description:**  | This class represents equivalent injections (generation or load). Voltage regulation is allowed only at the point of connection. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| p | Double | Equivalent active power injection. Load sign convention is used, i.e. positive sign means flow out from a node. Starting value for steady state solution. [watts] | False |
| q | Double | Equivalent reactive power injection. Load sign convention is used, i.e. positive sign means flow out from a node. Starting value for steady state solution. [vars] | False |
| minP | Double | Minimum active power of the injection. [watts] | False |
| maxP | Double | Maximum active power of the injection. [watts] | False |
| minQ | Double | Minimum reactive power of the injection. [vars] | False |
| maxQ | Double | Maximum reactive power of the injection. [vars] | False |
| regulationCapability | Boolean | Specifies whether or not the EquivalentInjection has the capability to regulate the voltage. | False |
| r | Double | Positive sequence resistance. Used to represent extended-equivalent conductive capability. [ohms] | False |
| x | Double | Positive sequence reactance. Used to represent extended-equivalent inductive capability. [ohms] | False |
| r0 | Double | Zero sequence resistance. Used to represent extended-equivalent conductive capability. [ohms] | False |
| x0 | Double | Zero sequence reactance. Used to represent extended-equivalent inductive capability. [ohms] | False |
| r2 | Double | Negative sequence resistance. Used to represent extended-equivalent conductive capability. [ohms] | False |
| x2 | Double | Negative sequence reactance. Used to represent extended-equivalent inductive capability. [ohms] | False |

### Relationships
None

## ConformLoad
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [ConformLoad](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Loads/ConformLoad/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [EnergyConsumer](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/EnergyConsumer) |
| **Description:**  | ConformLoad represent loads that follow a daily load change pattern where the pattern can be used to scale the load with a system load. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| LoadGroup | ConformLoadGroup | 0..1 | Group of this ConformLoad. | Direct |

## ConformLoadGroup
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [ConformLoadGroup](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Loads/ConformLoadGroup/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [LoadGroup](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Loads/LoadGroup) |
| **Description:**  | A group of loads conforming to an allocation pattern. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| ConformLoadSchedules | ConformLoadSchedule | 0..* | The ConformLoadSchedules in the ConformLoadGroup. | Direct |
| EnergyConsumers | ConformLoad | 0..* | Conform loads assigned to this ConformLoadGroup. | Direct |

## NonConformLoad
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [NonConformLoad](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Loads/NonConformLoad/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [EnergyConsumer](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/EnergyConsumer) |
| **Description:**  | NonConformLoad represent loads that do not follow a daily load change pattern and changes are not correlated with the daily load change pattern. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| LoadGroup | NonConformLoadGroup | 0..1 | Group of this ConformLoad. | Direct |

## NonConformLoadGroup
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [NonConformLoadGroup](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Loads/NonConformLoadGroup/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [LoadGroup](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Loads/LoadGroup) |
| **Description:**  | Loads that do not follow a daily and seasonal load variation pattern. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| EnergyConsumers | NonConformLoad | 0..* | Group of this ConformLoad. | Direct |
| NonConformLoadSchedules | NonConformLoadSchedule | 0..* | The NonConformLoadSchedules in the NonConformLoadGroup. | Direct |

## NonConformLoadSchedule
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [NonConformLoadSchedule](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Loads/NonConformLoadSchedule/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [SeasonDayTypeSchedule](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/SeasonDayTypeSchedule) |
| **Description:**  | An active power (Y1-axis) and reactive power (Y2-axis) schedule (curves) versus time (X-axis) for non-conforming loads, e.g. large industrial load or power station service (where modeled). |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| NonConformLoadGroup | NonConformLoadGroup | 1..1 | The NonConformLoadGroup where the NonConformLoadSchedule belongs. | Direct |

## BasicIntervalSchedule
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [BasicIntervalSchedule](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/BasicIntervalSchedule/) |
| **Package:**      | iec61970.base.core |
| **Parent Class:** | [IdentifiedObject](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/IdentifiedObject) |
| **Description:**  | Schedule of values at points in time. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| startTime | DateTime | The time for the first time point. | False |
| value1Unit | UnitSymbol | Value1 units of measure. | False |
| value2Unit | UnitSymbol | Value2 units of measure. | False |

### Relationships
None

## ConformLoadSchedule
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [ConformLoadSchedule](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Loads/ConformLoadSchedule/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [SeasonDayTypeSchedule](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/SeasonDayTypeSchedule) |
| **Description:**  | A curve of load versus time (X-axis) showing the active power values (Y1-axis) and reactive power (Y2-axis) for each unit of the period covered. This curve represents a typical pattern of load over the time period for a given day type and season. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| ConformLoadGroup | ConformLoadGroup | 1..1 | The ConformLoadGroup where the ConformLoadSchedule belongs. | Direct |

## DayType
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [DayType](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/DayType/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [IdentifiedObject](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/IdentifiedObject) |
| **Description:**  | Group of similar days. For example it could be used to represent weekdays, weekend, or holidays. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
None

## RegularIntervalSchedule
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [RegularIntervalSchedule](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/RegularIntervalSchedule/) |
| **Package:**      | iec61970.base.core |
| **Parent Class:** | [BasicIntervalSchedule](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/BasicIntervalSchedule) |
| **Description:**  | The schedule has time points where the time between them is constant. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| endTime | DateTime | The time for the last time point. | False |
| timeStep | Seconds | The time between each pair of subsequent regular time points in sequence order. | False |

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| TimePoints | RegularTimePoint | 1..* | The regular time points in this schedule. | Direct |

## RegularTimePoint
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [RegularTimePoint](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/RegularTimePoint/) |
| **Package:**      | iec61970.base.core |
| **Parent Class:** | [CoreValue](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/CoreValue) |
| **Description:**  | Time point for a schedule where the time between the consecutive points is constant. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| sequenceNumber | Integer | The position of the regular time point in the sequence. Note that time points don't have to be sequential, i.e. time points may be omitted. The actual time for a RegularTimePoint is computed by multiplying the associated regular interval schedule's time step with the regular time point sequence number and adding the associated schedules start time. | False |
| value1 | Double | The first value at the time. The meaning of the value is defined by the derived type of the associated schedule. | False |
| value2 | Double | The second value at the time. The meaning of the value is defined by the derived type of the associated schedule. | False |

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| IntervalSchedule | RegularIntervalSchedule | 1..1 | Regular interval schedule containing this time point. | Direct |

## RegulationSchedule
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [RegulationSchedule](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/RegulationSchedule/) |
| **Package:**      | iec61970.base.wires |
| **Parent Class:** | [SeasonDayTypeSchedule](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/SeasonDayTypeSchedule) |
| **Description:**  | A pre-established pattern over time for a controlled variable, e.g. busbar voltage. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| RegulatingControl | RegulatingControl | 1..1 | Regulating controls that have this schedule. | Direct |

## Season
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [Season](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/Season/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [IdentifiedObject](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/IdentifiedObject) |
| **Description:**  | A specified time period of the year. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| endDate | MonthDay | Date season ends. | False |
| startDate | MonthDay | Date season starts. | False |

### Relationships
None

## SeasonDayTypeSchedule
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [SeasonDayTypeSchedule](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/SeasonDayTypeSchedule/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [RegularIntervalSchedule](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/RegularIntervalSchedule) |
| **Description:**  | A time schedule covering a 24 hour period, with curve data for a specific type of season and day. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| DayType | DayType | 1..1 | DayType for the Schedule. | Direct |
| Season | Season | 1..1 | Season for the Schedule. | Direct |

## EnergyArea
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [EnergyArea](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/EnergyArea/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [IdentifiedObject](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/IdentifiedObject) |
| **Description:**  | Describes an area having energy production or consumption. Specializations are intended to support the load allocation function as typically required in energy management systems or planning studies to allocate hypothesized load levels to individual load points for power flow analysis. Often the energy area can be linked to both measured and forecast load levels. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
None

## LoadArea
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [LoadArea](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/LoadArea/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [EnergyArea](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/EnergyArea) |
| **Description:**  | The class is the root or first level in a hierarchical structure for grouping of loads for the purpose of load flow load scaling. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| SubLoadAreas | SubLoadArea | 0..* | The SubLoadAreas in the LoadArea. | Direct |

## LoadGroup
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [LoadGroup](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/LoadGroup/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [IdentifiedObject](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/IdentifiedObject) |
| **Description:**  | The class is the third level in a hierarchical structure for grouping of loads for the purpose of load flow load scaling. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| SubLoadArea | SubLoadArea | 1..1 | The SubLoadArea where the Loadgroup belongs. | Direct |

## SubLoadArea
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [SubLoadArea](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/SubLoadArea/) |
| **Package:**      | iec61970.base.loadmodel |
| **Parent Class:** | [EnergyArea](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/LoadModel/EnergyArea) |
| **Description:**  | The class is the second level in a hierarchical structure for grouping of loads for the purpose of load flow load scaling. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| LoadArea | LoadArea | 1..1 | The LoadArea where the SubLoadArea belongs. | Direct |
| LoadGroups | LoadGroup | 0..* | The LoadGroups in the SubLoadArea. | Direct |

## ExternalNetworkInjection
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [ExternalNetworkInjection](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/ExternalNetworkInjection/) |
| **Package:**      | iec61970.base.wires |
| **Parent Class:** | [RegulatingCondEq](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/RegulatingCondEq) |
| **Description:**  | This class represents external network and it is used for IEC 60909 calculations. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| ikSecond | Double | Initial symmetrical short-circuit current (=Ik'') in A (three-phase fault). Used for short circuit data exchange according to IEC 60909. [amperes] | False |
| maxInitialSymShStCurrent | Double | Maximum initial symmetrical short-circuit current (Ik'' max) in A (three-phase fault). Used for short circuit data exchange according to IEC 60909. [amperes] | False |
| minInitialSymShStCurrent | Double | Minimum initial symmetrical short-circuit current (Ik'' min) in A (three-phase fault). Used for short circuit data exchange according to IEC 60909. [amperes] | False |
| maxR0ToX0Ratio | Double | Maximum ratio of zero sequence resistance of an external network to zero sequence reactance (R(0)/X(0) max). Used for short circuit data exchange according to IEC 60909. | False |
| maxR1ToX1Ratio | Double | Maximum ratio of positive sequence resistance of an external network to positive sequence reactance (R(1)/X(1) max). Used for short circuit data exchange according to IEC 60909. | False |
| maxZ0ToZ1Ratio | Double | Maximum ratio of zero sequence impedance to positive sequence impedance (Z(0)/Z(1) max). Used for short circuit data exchange according to IEC 60909. | False |
| minR0ToX0Ratio | Double | Minimum ratio of zero sequence resistance of an external network to zero sequence reactance (R(0)/X(0) min). Used for short circuit data exchange according to IEC 60909. | False |
| minR1ToX1Ratio | Double | Minimum ratio of positive sequence resistance of an external network to positive sequence reactance (R(1)/X(1) min). Used for short circuit data exchange according to IEC 60909. | False |
| minZ0ToZ1Ratio | Double | Minimum ratio of zero sequence impedance to positive sequence impedance (Z(0)/Z(1) min). Used for short circuit data exchange according to IEC 60909. | False |
| voltageFactor | Double | Voltage factor in pu, which was used to calculate short-circuit current Ik'' and power Sk''. Used for short circuit data exchange according to IEC 60909. [dimensionless] | False |
| referencePriority | Integer | Priority of unit for use in powerflow calculations scope. | False |
| pMin | Double | Minimum active power of the injection. [watts] | False |
| pMax | Double | Maximum active power of the injection. [watts] | False |
| qMin | Double | Minimum reactive power of the injection. [vars] | False |
| qMax | Double | Maximum reactive power of the injection. [vars] | False |

### Relationships
None

## Bay
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [Bay](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/Bay/) |
| **Package:**      | iec61970.base.core |
| **Parent Class:** | [EquipmentContainer](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/EquipmentContainer) |
| **Description:**  | A collection of power system resources (within a given substation) including conducting equipment, protection relays, measurements, and telemetry. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| breakerConfiguration | BreakerConfiguration | Breaker configuration. | False |

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| Substation | Substation | 0..1 | Substation containing the bay. | Direct |
| VoltageLevel | VoltageLevel | 0..1 | The voltage level containing this bay. | Direct |

## VoltageLevel
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [VoltageLevel](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/VoltageLevel/) |
| **Package:**      | iec61970.base.core |
| **Parent Class:** | [EquipmentContainer](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/EquipmentContainer) |
| **Description:**  | A collection of equipment at one common system voltage forming a switchgear. The equipment typically consist of breakers, busbars, instrumentation, control, regulation and protection devices as well as assemblies of all these. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| BaseVoltage | BaseVoltage | 1..1 | The base voltage used for all equipment within the voltage level. | Direct |
| Substation | Substation | 1..1 | The substation of the voltage level. | Direct |
| Bays | Bay | 0..* | The bays within this voltage level. | Direct |

## PSRType
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [PSRType](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/PSRType/) |
| **Package:**      | iec61970.base.core |
| **Parent Class:** | [IdentifiedObject](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/IdentifiedObject) |
| **Description:**  | Classifying instances of the same class, e.g. overhead and underground ACLineSegments. This classification mechanism is intended to provide flexibility outside the scope of this standard, i.e. provide customisation that is non standard. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
None

## PowerSystemResource
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [PowerSystemResource](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/PowerSystemResource/) |
| **Package:**      | iec61970.base.core |
| **Parent Class:** | [IdentifiedObject](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Core/IdentifiedObject) |
| **Description:**  | A power system resource can be an item of equipment such as a Switch, an EquipmentContainer containing many individual items of equipment such as a Substation, or an organisational entity such as Company or SubControlArea. This provides for the nesting of collections of PowerSystemResources within other PowerSystemResources. For example, a Switch could be a member of a Substation and a Substation could be a member of a division of a Company. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| PSRType | PSRType | 0..1 | Custom classification for this power system resource. | Direct |

## NonlinearShuntCompensator
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [NonlinearShuntCompensator](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/NonlinearShuntCompensator/) |
| **Package:**      | iec61970.base.wires |
| **Parent Class:** | [ShuntCompensator](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/ShuntCompensator) |
| **Description:**  | A non linear shunt compensator has bank or section admittance values that differs. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
None

## ShuntCompensatorControl
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [ShuntCompensatorControl](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61968/InfIEC61968/ShuntCompensatorControl/) |
| **Package:**      | iec61968.infiec61968 |
| **Parent Class:** | [RegulatingControl](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/RegulatingControl) |
| **Description:**  | Control parameters for a shunt compensator. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
| **Attribute** | **Type** | **Description** | **ZBEX** |
|---------------|----------|-----------------|----------|
| cellSize | Double | The size of the individual units that make up the bank. [vars] | False |

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| ShuntCompensatorInfo | ShuntCompensatorInfo | 0..1 | Shunt compensator asset info driven by this control. | Direct |

## ShuntCompensatorInfo
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [ShuntCompensatorInfo](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61968/AssetInfo/ShuntCompensatorInfo/) |
| **Package:**      | iec61968.assetinfo |
| **Parent Class:** | [AssetInfo](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61968/AssetInfo/AssetInfo) |
| **Description:**  | Properties of shunt compensator assets. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
| **Name** | **Target Class** | **Multiplier** | **Description** | **Implementation Type** |
|----------|------------------|----------------|-----------------|-------------------------|
| ShuntCompensatorControl | ShuntCompensatorControl | 0..1 | Control parameters associated with this shunt compensator info. | Direct |

## Sectionaliser
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [Sectionaliser](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/Sectionaliser/) |
| **Package:**      | iec61970.base.wires |
| **Parent Class:** | [Switch](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/Switch) |
| **Description:**  | Automatic switch that will lock open to isolate a faulted section. It may, or may not, have load breaking capability. Its primary purpose is to provide fault sectionalising at locations where the fault current is either too high, or too low, for proper coordination of fuses. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
None

## SurgeArrester
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [SurgeArrester](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/SurgeArrester/) |
| **Package:**      | iec61970.base.wires |
| **Parent Class:** | [AuxiliaryEquipment](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/AuxiliaryEquipment) |
| **Description:**  | Shunt device, installed on the network, usually in the proximity of electrical equipment in order to protect the said equipment against transient voltage transients caused by lightning or switching activity. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
None

## WaveTrap
|                   |                             |
|-------------------|-----------------------------|
| **Class Name:**   | [WaveTrap](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/WaveTrap/) |
| **Package:**      | iec61970.base.wires |
| **Parent Class:** | [AuxiliaryEquipment](https://zepben.github.io/evolve/docs/cim/cim100/TC57CIM/IEC61970/Base/Wires/AuxiliaryEquipment) |
| **Description:**  | Line traps are devices that impede high frequency power line carrier signals yet present a negligible impedance at the main power frequency. |
| **Service:**      | NetworkService |
| **ZBEX:**         | False |

### Attributes
None

### Relationships
None
