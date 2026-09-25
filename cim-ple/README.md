# CIM-ple

An interactive terminal browser for the CIM YAML specification.

## Install and run

From the repository root:

```shell
python -m pip install -e cim-ple
cim-ple
```

The tool automatically finds the repository `spec` directory. Use another
specification directory with:

```shell
cim-ple --spec /path/to/spec
```

## Navigation

- `/` or `Ctrl+L` — search classes; autocomplete selects and reveals a class.
- `j` / `k` — move down / up in a tree.
- `h` / `l` — collapse / expand a tree branch.
- `g` / `G` — go to the first / last tree entry.
- `Ctrl+U` — page up.
- `q` — quit.

Class references in the details panel are clickable and open the referenced
class.

## Saved CIM100 classes

Select a class from **CIM100 Data Model** and press `a` to add it to the saved
classes tree. The saved tree appears in the right sidebar.

- `e` — edit the highlighted saved class.
- `d` — remove the highlighted saved class.
- `w` — write saved class changes to `spec/ewb` after confirmation.

The editor lets you mark attributes and associations. Associations cycle
between **Unmarked**, **1 way**, and **Bi-directional**. It also supports
adding and marking ZBEX attributes and associations.

Within the editor:

- `Ctrl+Enter` — save changes.
- `q` or `Esc` — close without saving.
