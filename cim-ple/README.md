# CIM-ple

A terminal browser for the CIM YAML specification in this repository. It uses
[Textual](https://textual.textualize.io/) for the interface and mirrors the
specification's package/class hierarchy in a collapsible sidebar.

## Run

From the repository root:

```shell
python -m pip install -e cim-ple
cim-ple
```

Or run it without installing the project:

```shell
python cim-ple/main.py
```

Use `--spec PATH` to browse another directory containing the YAML files. The
sidebar search autocompletes known classes; select a suggestion to expand the
tree to that class. Press `q` to quit.
