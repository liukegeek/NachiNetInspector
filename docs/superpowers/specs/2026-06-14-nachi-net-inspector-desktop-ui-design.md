# Nachi Net Inspector Desktop UI Design

## 1. Goal

Turn the existing `inspector-core` network inspection logic into a small, cross-platform desktop utility. The packaged application starts a local Spring Boot service, automatically opens the user's default browser, accepts multiple NACHI backup files, displays each robot's network topology and detailed parsing evidence, and exports the successful results to Excel.

The utility targets Windows and macOS. It does not provide server deployment, Docker images, asynchronous jobs, persistent task history, user accounts, or editable configuration.

## 2. Scope

### Included

- Select multiple backup files in one operation.
- Accept files without filtering by extension and attempt to parse each independently.
- Require an explicit "Start Inspection" action after file selection.
- Preserve successful results when one or more files fail.
- Display robot-level summaries, network topology, device details, and parsing evidence.
- Export one Excel workbook with one Sheet per successful or partial robot containing usable device data.
- Build Windows and macOS desktop application images through GitHub Actions.

### Excluded

- Backup directory selection.
- Docker and Linux server deployment.
- Server runtime mode and asynchronous task APIs.
- Result persistence between application launches.
- Config file storage, editing, APIs, or environment variables.
- Authentication, authorization, and multi-user behavior.
- Complex mobile-specific interactions.

## 3. Project Structure

The repository will be reduced to two Maven modules:

```text
NachiNetInspector/
├── inspector-core/
│   └── backup loading, network inspection, result models, Excel export
├── inspector-web/
│   └── Spring Boot desktop entry point, HTTP API, temporary uploads, static UI
├── .github/workflows/release.yml
├── icons/
├── pom.xml
└── README.md
```

The root Maven artifact and package-facing product names will use `NachiNetInspector` naming. The copied `krl-core` and `krl-web` modules will be removed after useful desktop startup, styling, and release patterns have been adapted into the new modules.

## 4. Architecture

### 4.1 `inspector-core`

`inspector-core` remains independent of Spring and owns:

- `BackFile`: opens a backup source as an internal file system.
- `NachiInspector`: reads the fixed NACHI files and offsets.
- `NachiNetResume` and `DeviceNet`: represent inspection results.
- A new Excel export service: writes complete network and parsing evidence data.

The core module may refine error boundaries and add tests where required, but the established offset-reading logic remains the source of truth.

### 4.2 `inspector-web`

`inspector-web` is a synchronous local Spring Boot application and owns:

- Multipart upload handling.
- Per-file temporary storage and cleanup.
- Mapping individual file failures into a batch response.
- Serving the static HTML, CSS, JavaScript, and bundled frontend vendor assets.
- Reporting the packaged application version.
- Opening the default browser after startup.

The application binds to `127.0.0.1` only. If its configured port is already occupied by another running instance, the launcher opens that existing instance and exits, preserving the copied project's desktop behavior.

### 4.3 API

The API is intentionally small:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/inspection` | Inspect multiple uploaded files and return a batch result |
| `POST` | `/api/inspection/excel` | Inspect multiple uploaded files and return an Excel workbook |
| `GET` | `/api/runtime/status` | Return the packaged application version |

Both inspection endpoints receive a `files` multipart field. They do not accept configuration text.

The batch JSON response contains one entry for every uploaded file:

```json
{
  "items": [
    {
      "sourceFileName": "APR15R1.backup",
      "status": "SUCCESS",
      "result": {
        "机器人名称": "apr15r1",
        "成功解析": true,
        "机器人自身网络信息": {},
        "子设备网络信息": [],
        "异常信息": []
      },
      "errorMessage": null
    }
  ],
  "successfulCount": 1,
  "partialCount": 0,
  "failedCount": 0
}
```

`status` is `SUCCESS`, `PARTIAL`, or `FAILED`:

- `SUCCESS`: core inspection completed without warnings.
- `PARTIAL`: core inspection returned at least one usable network device and one or more warnings.
- `FAILED`: the backup could not be opened or no usable network device was found.

A failed item has no usable `result` and includes a user-readable `errorMessage`.

## 5. Data Flow

1. The user selects multiple files. The frontend stores the browser `File` objects and displays the selected count.
2. The user clicks "Start Inspection".
3. The frontend sends all selected files to `POST /api/inspection` as one multipart request.
4. The web module processes each upload independently:
   - Copy the upload to an isolated temporary file.
   - Open it through `BackFile`.
   - Inspect it with `NachiInspector`.
   - Convert unexpected loading or parsing failures into a failed batch item.
   - Close the backup file system and delete the temporary file.
5. The frontend stores the batch response and renders successful, partial, and failed items.
6. The user selects a successful or partial robot to view its summary, topology, device table, warnings, and evidence details.
7. When the user clicks "Export Excel", the frontend sends the currently selected files to `POST /api/inspection/excel`.
8. The backend repeats inspection and returns a workbook containing successful and partial robots that have usable device data.

Re-inspecting files for export avoids persistent server state and keeps the tool simple. Excel export is disabled when no uploaded file has produced a successful or partial result containing usable device data.

## 6. User Interface

### 6.1 Visual Language

The UI adapts the copied KRL Parser design language:

- Orange gradient brand icon.
- Blue primary actions and orange Excel export action.
- White translucent header and panels.
- Slate text, light gray borders, rounded cards, and soft shadows.
- Light dotted canvas background.
- Bundled Lucide icons and Cytoscape-based topology rendering.

KRL-specific graph controls, Config editing, runtime mode badges, and task status controls are removed.

### 6.2 Initial State

The initial screen contains:

- Product identity and packaged version.
- "Upload Backups", "Start Inspection", and "Export Excel" actions.
- A concise empty-state guide explaining the three-step workflow.

"Start Inspection" is enabled after at least one file is selected. "Export Excel" is enabled only after an inspection has at least one successful or partial result containing usable device data.

### 6.3 Results State

The results layout is topology-first:

- Left sidebar: every uploaded file, its resolved robot name when available, and success, partial, or failure status.
- Summary cards: robot name, robot IP, sub-device count, and inspection status.
- Main topology: the robot body is an orange root node; attached devices are blue child nodes.
- Device table: body and attached devices with name, IP, subnet mask, and gateway.
- Detail panel: selected device's source file, record header, record start offset, name offsets, IP offset, mask offset, and gateway offset.
- Error summary: failed uploads and partial core inspection warnings remain visible without hiding successful data.

Selecting a topology node or a table row selects the same device and opens its detail panel.

### 6.4 Responsive Behavior

Desktop is the primary layout. On narrow screens:

- The sidebar becomes a horizontal or stacked result selector.
- Summary cards, topology, table, and details stack vertically.
- Tables scroll horizontally when necessary.

No gesture-only behavior or complex mobile control system is introduced.

## 7. Excel Export

The backend returns one `.xlsx` workbook. Each successful robot and each partial robot containing usable device data receives one Sheet.

Each Sheet contains one row for the robot body and one row per attached device. Columns are:

1. Device name
2. IP
3. Subnet mask
4. Gateway
5. Source file
6. Record header
7. Record start offset
8. Name length offset
9. Name offset
10. IP offset
11. Mask offset
12. Gateway offset

The header uses a fixed high-contrast style, rows are readable without manual resizing, and the header row is frozen. Sheet names are sanitized and made unique within Excel limits. Failed uploads do not create Sheets. If no upload contains usable device data, the endpoint returns a user-readable client error instead of an empty workbook.

## 8. Error Handling

Per-file isolation is the main rule:

- Unsupported, corrupt, or non-archive files fail only their own batch item.
- Missing expected NACHI files or invalid record data become a partial result when other usable network data remains; otherwise they become a failed result.
- Unexpected failures become a generic user-readable error while full details are written to the local log.
- Temporary files and opened file systems are cleaned up in success and failure paths.
- If no file contains usable device data, the results page displays the complete failure list and disables export.

The API returns structured JSON errors rather than HTML error pages.

## 9. Testing

### 9.1 Core Tests

- Parse representative valid NACHI backups.
- Verify robot body and attached-device network values.
- Verify source files, record headers, and field offsets.
- Cover missing expected files, corrupt backup sources, and partial inspection results.
- Verify Excel Sheet count, column order, complete evidence fields, unique sanitized Sheet names, and all-failure behavior.

### 9.2 Web Tests

- Context startup.
- Runtime version response.
- Multiple upload success.
- Mixed successful, partial, and failed uploads.
- All uploads failed.
- Excel response headers and workbook response.
- Temporary file cleanup.
- Structured API error mapping.

### 9.3 UI Verification

Use the local browser to verify:

- Selecting multiple arbitrary-extension files.
- Explicit start action.
- Successful, mixed, and all-failed result states.
- Switching robots.
- Topology and table selection synchronization.
- Evidence detail display.
- Excel download.
- Narrow-screen stacking.

## 10. Build And Release

GitHub Actions runs the Maven test suite before packaging. Release builds use JDK 21 and `jpackage` to create self-contained application images:

- `NachiNetInspector-<version>-windows.zip`
- `NachiNetInspector-<version>-macos.zip`

The application image bundles the Spring Boot executable JAR and runtime, so users do not install Java separately. Existing application icons are reused unless product-specific replacements are supplied later.

Docker build jobs, Dockerfiles, Compose files, server deployment documentation, and Linux image assets are removed.

## 11. Documentation And Cleanup

README content is rewritten for Nachi Net Inspector and documents:

- What the utility inspects.
- Supported input behavior.
- Desktop usage flow.
- Excel contents.
- Source build commands.
- Release packaging behavior.

All copied KRL product references, Config instructions, server-mode instructions, and Docker instructions are removed from active project files.

## 12. Acceptance Criteria

- Running the packaged desktop application opens the local UI automatically.
- A user can select multiple files of arbitrary extension and start inspection explicitly.
- One invalid file does not prevent valid files from displaying.
- Each successful or partial robot containing usable device data can be selected and viewed as a topology, table, warnings, and evidence detail panel.
- Excel export produces one complete Sheet per successful or partial robot containing usable device data.
- The repository contains only `inspector-core` and `inspector-web` product modules.
- Config, KRL analysis, server mode, asynchronous task, and Docker code are absent.
- Maven tests pass.
- GitHub Actions can package Windows and macOS application ZIPs.
