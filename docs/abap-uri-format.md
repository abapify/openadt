# ABAP URI Format

When using MCP tools like `abap_run_unit_tests`, URIs must follow the VS Code ABAP virtual filesystem format.

## Format

```text
abap://project/repotree-v1/{destination}/{path-to-file}.{extension}
```

Where:

- `project` — workspace folder name (fixed; identifies the project context)
- `repotree-v1` — ABAP repository version root (fixed)
- `{destination}` — SAP system destination ID (e.g., `S0D_200_PPLENKOV_EN`)
- `{path-to-file}` — ABAP object path in the repository
- `{extension}` — file type extension:
  - `.clas.abap` — ABAP class
  - `.intf.abap` — ABAP interface
  - `.prog.abap` — ABAP program
  - `.dtel.abap` — Data element
  - `.fugr.abap` — Function group
  - (other standard ABAP object extensions)

## Examples

**Class:**

```text
abap://project/repotree-v1/S0D_200_PPLENKOV_EN/System%20Library/ZCA_OTEL/Source%20Code%20Library/Classes/ZCL_OTEL/zcl_otel.clas.abap
```

**Interface:**

```text
abap://project/repotree-v1/S0D_200_PPLENKOV_EN/System%20Library/ZCA_OTEL/Source%20Code%20Library/Interfaces/ZIF_OTEL/zif_otel.intf.abap
```

## URL Encoding

Spaces in paths must be URL-encoded as `%20`:

- `System Library` → `System%20Library`
- `Source Code Library` → `Source%20Code%20Library`

## Virtual File System

The file does **not** need to be open in VS Code editor. SAP ADT extension (`sapse.adt-vscode`) provides a virtual filesystem that dynamically loads ABAP objects from the backend SAP system.

## Path Components

The path reflects the ABAP repository hierarchy as exposed by SAP ADT backend:

- Repository root categories (e.g., `System Library`, `Source Code Library`) — determined by SAP system configuration
- Package name (e.g., `ZCA_OTEL`)
- Object type folder (e.g., `Classes`, `Interfaces`, `Programs`)
- Object name folder
- Filename with extension (e.g., `zcl_otel.clas.abap`)

**How to find the correct path:**

1. Open the ABAP object in VS Code explorer (via SAP ADT extension)
2. Note the full path shown in the breadcrumb or file explorer
3. Copy that path as-is, URL-encoding spaces as `%20`

**Note:** The exact hierarchy structure is determined by your SAP system configuration and repository metadata. If a URI fails, verify the exact path from VS Code ABAP explorer.

## Used By

- `abap_run_unit_tests` — run ABAP Unit Tests for specified objects
- Other SAP ADT MCP tools that accept URIs
