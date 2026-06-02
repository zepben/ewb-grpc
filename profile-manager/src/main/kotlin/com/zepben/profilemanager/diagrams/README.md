# Diagram Exporter PoC

Proof of concept Python script to export diagrams from an EA CIM database. Should be converted into a function within `profile-manager` itself.

> [!NOTE]
> `implementation("org.jetbrains.lets-plot:lets-plot-kotlin:4.14.0")` was left lying around in the Gradle config during development (before the PoC was left as Python), so might be
worth checking it out when implementing the real thing in Kotlin?

## Initial Setup

Copy `config.sample.py` to `config.py` and adjust its values for your setup.

## Running the exporter

Just run the `export-diagrams` script, which will use your config to determine what to export:

```bash
python export-diagrams.py
```

You can obviously chain this with removing the previous output if you are browsing through a subset of packages:

```bash
rm -rf exported_diagrams/ && python export-diagrams.py
```

### Debugging

If there are issues you want to look at in a particular digram with the debugger, you can set the debug filter to simplify the process. Search for `# DEBUG WHITELIST FILTER` and
check the examples below.
