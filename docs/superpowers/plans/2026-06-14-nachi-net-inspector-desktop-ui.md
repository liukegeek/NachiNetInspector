# Nachi Net Inspector Desktop UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the copied KRL Parser product with a small Windows/macOS desktop utility that batch-inspects NACHI backup files, renders a topology-first browser UI, and exports complete network evidence to Excel.

**Architecture:** Keep `inspector-core` as a Spring-independent library for backup loading, binary inspection, result models, and Excel export. Create a new synchronous `inspector-web` Spring Boot module for multipart upload isolation, local-only APIs, browser startup, and static frontend delivery; remove copied KRL, Config, server-task, and Docker code after the replacement is working.

**Tech Stack:** Java 21, Maven, Spring Boot 3.4.2, JUnit 5, MockMvc, Mockito, Apache POI, plain HTML/CSS/JavaScript, Cytoscape.js, Dagre, Lucide, GitHub Actions, jpackage

---

## File Structure

### Core module

- Modify: `pom.xml` - rename the root artifact and aggregate only the new product modules.
- Modify: `inspector-core/pom.xml` - add Apache POI and JUnit dependencies.
- Modify: `inspector-core/src/main/java/tech/waitforu/BackFile.java` - expose user-readable backup loading failures.
- Modify: `inspector-core/src/main/java/tech/waitforu/NachiInspector.java` - preserve partial results when one inspection section fails.
- Create: `inspector-core/src/main/java/tech/waitforu/exceptions/ExcelExportException.java` - core Excel failure type.
- Create: `inspector-core/src/main/java/tech/waitforu/service/NetworkExcelExportService.java` - one-Sheet-per-robot evidence workbook.
- Delete: `inspector-core/src/test/java/tech/waitforu/BackfileTest.java` - remove the non-JUnit manual test.
- Create: `inspector-core/src/test/java/tech/waitforu/BackFileTest.java` - formal backup loading tests.
- Create: `inspector-core/src/test/java/tech/waitforu/NachiInspectorTest.java` - success and partial-result tests.
- Create: `inspector-core/src/test/java/tech/waitforu/service/NetworkExcelExportServiceTest.java` - workbook structure tests.

### Web module

- Create: `inspector-web/pom.xml` - Spring Boot application depending on `inspector-core`.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/NachiNetInspectorApplication.java` - desktop launcher and browser opening.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/config/CoreServiceConfiguration.java` - core runner and Excel beans.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/model/InspectionStatus.java` - `SUCCESS`, `PARTIAL`, `FAILED`.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/model/InspectionItem.java` - one uploaded file result.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/model/InspectionBatchResponse.java` - batch items and counts.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/service/BackupInspectionRunner.java` - injectable core inspection boundary.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/service/InspectionExecutionService.java` - synchronous upload, inspection, cleanup, and export flow.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/controller/InspectionController.java` - inspection and Excel endpoints.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/controller/RuntimeController.java` - packaged version endpoint.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/controller/ErrorResponse.java` - structured error body.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/controller/GlobalExceptionHandler.java` - structured API errors.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/exception/ApiException.java` - status-bearing API exception.
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/exception/BadRequestException.java` - client input error.
- Create: `inspector-web/src/main/resources/application.yml` - local-only desktop settings.
- Copy: `krl-web/src/main/resources/static/vendor/{cytoscape.min.js,dagre.min.js,cytoscape-dagre.min.js,lucide.js}` to `inspector-web/src/main/resources/static/vendor/`.
- Create: `inspector-web/src/main/resources/static/index.html` - topology-first UI shell.
- Create: `inspector-web/src/main/resources/static/css/styles.css` - copied visual language adapted to the new UI.
- Create: `inspector-web/src/main/resources/static/js/app.js` - upload, inspection, topology, table, detail, and export behavior.
- Copy: `krl-web/src/main/resources/static/favicon.ico` to `inspector-web/src/main/resources/static/favicon.ico`.
- Create: `inspector-web/src/test/java/tech/waitforu/inspectorweb/NachiNetInspectorApplicationTest.java`.
- Create: `inspector-web/src/test/java/tech/waitforu/inspectorweb/service/InspectionExecutionServiceTest.java`.
- Create: `inspector-web/src/test/java/tech/waitforu/inspectorweb/controller/InspectionControllerTest.java`.
- Create: `inspector-web/src/test/java/tech/waitforu/inspectorweb/controller/RuntimeControllerTest.java`.
- Create: `inspector-web/src/test/java/tech/waitforu/inspectorweb/controller/GlobalExceptionHandlerTest.java`.

### Product cleanup and release

- Delete: `krl-core/`, `krl-web/`, `Dockerfile`, `deploy/`, `package.json`, `package-lock.json`.
- Modify: `.github/workflows/release.yml` - test and package Windows/macOS only.
- Modify: `README.md` - document only Nachi Net Inspector desktop behavior.
- Modify: `.gitignore` - remove stale KRL runtime entry and ignore Nachi runtime output.

---

### Task 1: Establish The New Two-Module Build

**Files:**
- Modify: `pom.xml`
- Modify: `inspector-core/pom.xml`
- Create: `inspector-web/pom.xml`
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/NachiNetInspectorApplication.java`
- Create: `inspector-web/src/main/resources/application.yml`
- Create: `inspector-web/src/test/java/tech/waitforu/inspectorweb/NachiNetInspectorApplicationTest.java`

- [ ] **Step 1: Write the failing web context test**

```java
package tech.waitforu.inspectorweb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NachiNetInspectorApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run the test to verify the new module does not exist yet**

Run: `mvn -B -pl inspector-web -am test`

Expected: FAIL because `inspector-web/pom.xml` and the application class do not exist.

- [ ] **Step 3: Replace the root project coordinates and module list**

Set `pom.xml` to:

```xml
<groupId>tech.waitforu</groupId>
<artifactId>NachiNetInspector</artifactId>
<version>1.0-SNAPSHOT</version>
<packaging>pom</packaging>
<modules>
    <module>inspector-core</module>
    <module>inspector-web</module>
</modules>
```

Keep Java 21, Spring Boot 3.4.2, the Spring Boot BOM, compiler plugin, and Boot repackage plugin. Remove the ANTLR and ini4j version management and all KRL-specific comments.

Update the parent artifact in `inspector-core/pom.xml` to `NachiNetInspector`, then add:

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Create the Spring Boot web module**

Create `inspector-web/pom.xml` with parent `tech.waitforu:NachiNetInspector:1.0-SNAPSHOT`, artifact `inspector-web`, dependencies `spring-boot-starter-web`, `inspector-core`, and test-scoped `spring-boot-starter-test`. Configure `spring-boot-maven-plugin` with the `build-info` goal.

Create the desktop launcher:

```java
package tech.waitforu.inspectorweb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class NachiNetInspectorApplication {
    static final int PORT = 2026;
    static final String DESKTOP_URL = "http://localhost:" + PORT;
    private static final Logger LOGGER = LoggerFactory.getLogger(NachiNetInspectorApplication.class);

    public static void main(String[] args) {
        try {
            SpringApplication.run(NachiNetInspectorApplication.class, args);
            LOGGER.info("Nachi Net Inspector 已启动: {}", DESKTOP_URL);
            openBrowser(DESKTOP_URL);
        } catch (Exception exception) {
            if (isPortInUseException(exception)) {
                LOGGER.info("端口 {} 已被占用，打开已运行实例", PORT);
                openBrowser(DESKTOP_URL);
                return;
            }
            throw exception;
        }
    }

    static boolean isPortInUseException(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof java.net.BindException
                    || current.getClass().getName().contains("PortInUseException")) {
                return true;
            }
        }
        return false;
    }

    static void openBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec("open " + url);
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (IOException exception) {
            LOGGER.warn("自动打开浏览器失败，请手动访问 {}", url, exception);
        }
    }
}
```

Create `application.yml`:

```yaml
server:
  address: 127.0.0.1
  port: 2026

spring:
  application:
    name: inspector-web
  servlet:
    multipart:
      max-file-size: 1000MB
      max-request-size: 1000MB

logging:
  file:
    name: ${user.home}/.NachiNetInspector/logs/inspection.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 7
```

Do not copy the old custom Logback file. Let Spring Boot use `logging.file.name` and the rolling policy from `application.yml`, avoiding a second conflicting log path.

- [ ] **Step 5: Run the new module context test**

Run: `mvn -B -pl inspector-web -am test`

Expected: PASS with reactor modules `NachiNetInspector`, `inspector-core`, and `inspector-web`.

- [ ] **Step 6: Commit the build skeleton**

```bash
git add pom.xml inspector-core/pom.xml inspector-web
git commit -m "build: establish Nachi inspector modules"
```

---

### Task 2: Make Core Inspection Reliable And Testable

**Files:**
- Modify: `inspector-core/src/main/java/tech/waitforu/BackFile.java`
- Modify: `inspector-core/src/main/java/tech/waitforu/NachiInspector.java`
- Delete: `inspector-core/src/test/java/tech/waitforu/BackfileTest.java`
- Create: `inspector-core/src/test/java/tech/waitforu/BackFileTest.java`
- Create: `inspector-core/src/test/java/tech/waitforu/NachiInspectorTest.java`

- [ ] **Step 1: Write failing tests for valid, invalid, and partial backups**

```java
package tech.waitforu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.waitforu.exceptions.BackupLoadException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BackFileTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsAnUnsupportedFileWithReadableError() throws Exception {
        Path file = Files.writeString(tempDir.resolve("not-a-backup.bin"), "plain text");
        BackupLoadException exception = assertThrows(BackupLoadException.class,
                () -> new BackFile(file.toString()));
        assertTrue(exception.getMessage().contains("无法打开备份文件"));
    }
}
```

```java
package tech.waitforu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NachiInspectorTest {
    private static final Path VALID_BACKUP = Path.of("src/test/测试/APR15R1");

    @TempDir
    Path tempDir;

    @Test
    void inspectsRobotAndAttachedDevices() throws Exception {
        try (BackFile backup = new BackFile(VALID_BACKUP.toString())) {
            NachiNetResume result = NachiInspector.inspect(backup);
            assertTrue(result.isSuccess());
            assertEquals("apr15r1", result.robotName());
            assertEquals("190.64.6.89", result.robotSelfNet().deviceIP());
            assertEquals(3, result.subDevicesNet().size());
            assertEquals("0xDE", result.robotSelfNet().ipOffset());
        }
    }

    @Test
    void preservesUsableRobotDataWhenConfigFileIsMissing() throws Exception {
        Path plcEngine = Files.createDirectories(tempDir.resolve("PLCEngine"));
        Files.copy(VALID_BACKUP.resolve("PLCEngine/nwid1.nxd"), plcEngine.resolve("nwid1.nxd"));

        try (BackFile backup = new BackFile(tempDir.toString())) {
            NachiNetResume result = NachiInspector.inspect(backup);
            assertFalse(result.isSuccess());
            assertNotNull(result.robotSelfNet());
            assertEquals("190.64.6.89", result.robotSelfNet().deviceIP());
            assertFalse(result.exceptionMessage().isEmpty());
        }
    }
}
```

- [ ] **Step 2: Run the core tests to verify failure**

Run: `mvn -B -pl inspector-core test`

Expected: FAIL because unsupported backup errors are generic and missing `config1.nxd` aborts inspection.

- [ ] **Step 3: Wrap backup opening and reading errors**

In `BackFile`, make the immutable fields `final`, remove unused string-reading and OS-path helpers, and wrap constructor/read failures:

```java
try {
    if (Files.isDirectory(sourcePath)) {
        fileSystem = sourcePath.getFileSystem();
        rootPath = sourcePath;
        needCloseFileSystem = false;
    } else {
        fileSystem = FileSystems.newFileSystem(sourcePath);
        rootPath = fileSystem.getPath("/");
        needCloseFileSystem = true;
    }
} catch (Exception exception) {
    throw new BackupLoadException("无法打开备份文件: " + sourcePath.getFileName(), exception);
}
```

Add a cause constructor to `BackupLoadException`, and make `readBytes` throw:

```java
throw new BackupLoadException("无法读取备份内文件: " + pathString, exception);
```

- [ ] **Step 4: Preserve section-level partial results**

In each of the three `NachiInspector.inspect` sections, catch `RuntimeException` after the existing specific catches and append a section-specific user message instead of aborting:

```java
} catch (RuntimeException exception) {
    exceptionMessage.add("读取机器人本体网络信息失败：" + readableMessage(exception));
}
```

Use corresponding prefixes for attached-device network information and robot name. Add:

```java
private static String readableMessage(RuntimeException exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
}
```

Remove unused imports and keep `isSuccess = exceptionMessage.isEmpty()`.

- [ ] **Step 5: Run core tests**

Run: `mvn -B -pl inspector-core test`

Expected: PASS; valid backup is successful and missing `config1.nxd` returns partial robot data.

- [ ] **Step 6: Commit the core reliability changes**

```bash
git add inspector-core/src/main inspector-core/src/test
git commit -m "test: harden Nachi backup inspection"
```

---

### Task 3: Add Complete Network Excel Export

**Files:**
- Create: `inspector-core/src/main/java/tech/waitforu/exceptions/ExcelExportException.java`
- Create: `inspector-core/src/main/java/tech/waitforu/service/NetworkExcelExportService.java`
- Create: `inspector-core/src/test/java/tech/waitforu/service/NetworkExcelExportServiceTest.java`

- [ ] **Step 1: Write failing workbook tests**

```java
package tech.waitforu.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import tech.waitforu.DeviceNet;
import tech.waitforu.NachiNetResume;
import tech.waitforu.exceptions.ExcelExportException;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NetworkExcelExportServiceTest {
    private final NetworkExcelExportService service = new NetworkExcelExportService();

    @Test
    void exportsOneEvidenceSheetPerUsableRobot() throws Exception {
        DeviceNet body = new DeviceNet("机器人本体", "10.0.0.1", "255.255.255.0", "10.0.0.254",
                "PLCEngine/nwid1.nxd", "0x17", "0xC8", "", "", "0xDE", "0xE2", "0xE6");
        DeviceNet child = new DeviceNet("timer1", "10.0.0.2", "255.255.255.0", "0.0.0.0",
                "PLCEngine/config1.nxd", "0x17", "0x100", "0x114", "0x116", "0x20E", "0x212", "0x216");
        NachiNetResume result = new NachiNetResume("R1", true, body, List.of(child), List.of());

        byte[] bytes = service.export(List.of(result));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("设备名称", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("网关偏移量", workbook.getSheetAt(0).getRow(0).getCell(11).getStringCellValue());
            assertEquals("机器人本体", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("timer1", workbook.getSheetAt(0).getRow(2).getCell(0).getStringCellValue());
            assertEquals(1, workbook.getSheetAt(0).getPaneInformation().getHorizontalSplitPosition());
        }
    }

    @Test
    void rejectsResultsWithoutUsableDevices() {
        NachiNetResume empty = new NachiNetResume("", false, null, List.of(), List.of("failed"));
        assertThrows(ExcelExportException.class, () -> service.export(List.of(empty)));
    }
}
```

Also add a test with duplicate/invalid robot names such as `R/1` and `R/1`; assert both safe Sheet names are unique and at most 31 characters.

- [ ] **Step 2: Run the Excel tests to verify failure**

Run: `mvn -B -pl inspector-core -Dtest=NetworkExcelExportServiceTest test`

Expected: FAIL because the export service and exception do not exist.

- [ ] **Step 3: Implement the export service**

Create `ExcelExportException` with message and cause constructors. Implement `NetworkExcelExportService` with these fixed headers:

```java
private static final List<String> HEADERS = List.of(
        "设备名称", "IP", "子网掩码", "网关", "来源文件", "记录头",
        "记录起始偏移量", "名称长度偏移量", "名称偏移量",
        "IP偏移量", "掩码偏移量", "网关偏移量"
);
```

The public method must be:

```java
public byte[] export(List<NachiNetResume> results)
```

For each result, collect `robotSelfNet` when non-null followed by `subDevicesNet`. Skip results with no devices. Create a safe unique Sheet name from `robotName`, falling back to `机器人-<index>`. Write one row per device in the exact accessor order:

```java
List.of(
        device.deviceName(), device.deviceIP(), device.deviceMask(), device.deviceGateway(),
        device.sourceFile(), device.recordHeader(), device.recordStartOffset(),
        device.name_length_offset(), device.name_offset(), device.ipOffset(),
        device.maskOffset(), device.gatewayOffset()
)
```

Apply a dark blue header with white bold text, alternating light row fill, borders, `sheet.createFreezePane(0, 1)`, and bounded column widths. Throw `ExcelExportException("没有可导出的网络设备信息")` when no Sheet was created.

- [ ] **Step 4: Run the Excel tests**

Run: `mvn -B -pl inspector-core -Dtest=NetworkExcelExportServiceTest test`

Expected: PASS.

- [ ] **Step 5: Run all core tests**

Run: `mvn -B -pl inspector-core test`

Expected: PASS.

- [ ] **Step 6: Commit Excel export**

```bash
git add inspector-core
git commit -m "feat: export Nachi network evidence to Excel"
```

---

### Task 4: Implement Batch Inspection Models And Service

**Files:**
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/config/CoreServiceConfiguration.java`
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/model/InspectionStatus.java`
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/model/InspectionItem.java`
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/model/InspectionBatchResponse.java`
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/service/BackupInspectionRunner.java`
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/service/InspectionExecutionService.java`
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/exception/ApiException.java`
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/exception/BadRequestException.java`
- Create: `inspector-web/src/test/java/tech/waitforu/inspectorweb/service/InspectionExecutionServiceTest.java`

- [ ] **Step 1: Write failing mixed-batch and cleanup tests**

Use `MockMultipartFile`, a fake `BackupInspectionRunner`, and a mocked `NetworkExcelExportService`:

```java
@Test
void keepsSuccessfulAndFailedItemsInUploadOrder() {
    MockMultipartFile good = new MockMultipartFile("files", "good.any", "application/octet-stream", new byte[]{1});
    MockMultipartFile bad = new MockMultipartFile("files", "bad.txt", "text/plain", new byte[]{2});
    BackupInspectionRunner runner = path -> {
        if (path.getFileName().toString().contains("bad")) {
            throw new IllegalArgumentException("not a backup");
        }
        return successfulResult();
    };
    InspectionExecutionService service = new InspectionExecutionService(runner, mock(NetworkExcelExportService.class));

    InspectionBatchResponse response = service.inspect(List.of(good, bad));

    assertEquals(List.of(InspectionStatus.SUCCESS, InspectionStatus.FAILED),
            response.items().stream().map(InspectionItem::status).toList());
    assertEquals(1, response.successfulCount());
    assertEquals(0, response.partialCount());
    assertEquals(1, response.failedCount());
}
```

Add tests that:

- a result with usable devices plus warnings is `PARTIAL`;
- a result with no usable devices is `FAILED`;
- arbitrary filename extensions are accepted;
- the temporary file observed by the runner no longer exists after `inspect`;
- `exportExcel` passes only usable `SUCCESS` and `PARTIAL` results to the exporter;
- null/empty uploads throw `BadRequestException`;
- no usable export result throws `BadRequestException("没有可导出的网络设备信息")`.

- [ ] **Step 2: Run the service test to verify failure**

Run: `mvn -B -pl inspector-web -am -Dtest=InspectionExecutionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the service and models do not exist.

- [ ] **Step 3: Implement the models**

```java
public enum InspectionStatus {
    SUCCESS, PARTIAL, FAILED
}
```

```java
public record InspectionItem(
        String sourceFileName,
        InspectionStatus status,
        NachiNetResume result,
        String errorMessage
) {
    public boolean hasUsableData() {
        return result != null && (result.robotSelfNet() != null || !result.subDevicesNet().isEmpty());
    }
}
```

```java
public record InspectionBatchResponse(
        List<InspectionItem> items,
        long successfulCount,
        long partialCount,
        long failedCount
) {
    public static InspectionBatchResponse from(List<InspectionItem> items) {
        List<InspectionItem> copied = List.copyOf(items);
        return new InspectionBatchResponse(
                copied,
                copied.stream().filter(item -> item.status() == InspectionStatus.SUCCESS).count(),
                copied.stream().filter(item -> item.status() == InspectionStatus.PARTIAL).count(),
                copied.stream().filter(item -> item.status() == InspectionStatus.FAILED).count()
        );
    }
}
```

- [ ] **Step 4: Implement the injectable core boundary and beans**

```java
@FunctionalInterface
public interface BackupInspectionRunner {
    NachiNetResume inspect(Path backupPath);
}
```

Register:

```java
@Bean
BackupInspectionRunner backupInspectionRunner() {
    return path -> {
        try (BackFile backup = new BackFile(path.toString())) {
            return NachiInspector.inspect(backup);
        } catch (IOException exception) {
            throw new BackupLoadException("关闭备份文件失败", exception);
        }
    };
}

@Bean
NetworkExcelExportService networkExcelExportService() {
    return new NetworkExcelExportService();
}
```

- [ ] **Step 5: Implement synchronous per-file isolation**

`InspectionExecutionService.inspect(List<MultipartFile>)` must:

1. Filter null/empty files and reject an empty list.
2. Create one `nachi-inspection-` temporary directory.
3. For each upload in order, sanitize only `/` and `\`, preserve its extension or lack of extension, and prefix the index to prevent collisions.
4. Copy the upload, invoke `BackupInspectionRunner`, and classify:

```java
private InspectionItem classify(String filename, NachiNetResume result) {
    boolean usable = result != null
            && (result.robotSelfNet() != null || !result.subDevicesNet().isEmpty());
    if (!usable) {
        String message = result == null || result.exceptionMessage().isEmpty()
                ? "未发现可用的网络设备信息"
                : String.join("；", result.exceptionMessage());
        return new InspectionItem(filename, InspectionStatus.FAILED, null, message);
    }
    InspectionStatus status = result.isSuccess() ? InspectionStatus.SUCCESS : InspectionStatus.PARTIAL;
    return new InspectionItem(filename, status, result, null);
}
```

5. Convert an exception for one file into `FAILED` without aborting later files.
6. Recursively delete the temporary directory in `finally`.

`exportExcel(List<MultipartFile>)` must call `inspect`, select `InspectionItem::hasUsableData`, and pass their results to `NetworkExcelExportService.export`.

- [ ] **Step 6: Run the service tests**

Run: `mvn -B -pl inspector-web -am -Dtest=InspectionExecutionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

- [ ] **Step 7: Commit batch inspection**

```bash
git add inspector-web
git commit -m "feat: inspect uploaded backups as an isolated batch"
```

---

### Task 5: Expose The Synchronous Desktop API

**Files:**
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/controller/InspectionController.java`
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/controller/RuntimeController.java`
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/controller/ErrorResponse.java`
- Create: `inspector-web/src/main/java/tech/waitforu/inspectorweb/controller/GlobalExceptionHandler.java`
- Create: `inspector-web/src/test/java/tech/waitforu/inspectorweb/controller/InspectionControllerTest.java`
- Create: `inspector-web/src/test/java/tech/waitforu/inspectorweb/controller/RuntimeControllerTest.java`
- Create: `inspector-web/src/test/java/tech/waitforu/inspectorweb/controller/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write failing controller tests**

For `InspectionControllerTest`, use standalone MockMvc with a mocked `InspectionExecutionService`. Verify:

```java
mockMvc.perform(multipart("/api/inspection")
        .file(new MockMultipartFile("files", "robot.any", "application/octet-stream", new byte[]{1})))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successfulCount").value(1))
        .andExpect(jsonPath("$.items[0].sourceFileName").value("robot.any"));
```

Verify `/api/inspection/excel` returns:

- content type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`;
- UTF-8 attachment filename `Nachi网络信息.xlsx`;
- the mocked workbook bytes.

For `RuntimeControllerTest`, instantiate with version `1.2.3` and expect only:

```json
{"appVersion":"1.2.3"}
```

For `GlobalExceptionHandlerTest`, verify `BadRequestException` maps to 400 and unknown errors map to generic 500 JSON.

- [ ] **Step 2: Run controller tests to verify failure**

Run: `mvn -B -pl inspector-web -am -Dtest='*ControllerTest,*ExceptionHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because controllers do not exist.

- [ ] **Step 3: Implement the inspection controller**

```java
@RestController
@RequestMapping("/api/inspection")
public class InspectionController {
    private final InspectionExecutionService executionService;

    public InspectionController(InspectionExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InspectionBatchResponse inspect(@RequestPart("files") List<MultipartFile> files) {
        return executionService.inspect(files);
    }

    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> exportExcel(@RequestPart("files") List<MultipartFile> files) {
        String filename = URLEncoder.encode("Nachi网络信息.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(executionService.exportExcel(files));
    }
}
```

- [ ] **Step 4: Implement runtime and structured errors**

`RuntimeController` reads optional `BuildProperties` and returns `RuntimeStatusResponse(String appVersion)`.

`GlobalExceptionHandler` maps:

- `ApiException` to its configured status and message;
- `IllegalArgumentException` and `BackupLoadException` to 400;
- `ExcelExportException` to 500;
- all other exceptions to `500 / 服务器内部错误，请查看日志`.

Do not reference KRL exceptions, Config, runtime modes, or tasks.

- [ ] **Step 5: Run web tests**

Run: `mvn -B -pl inspector-web -am test`

Expected: PASS.

- [ ] **Step 6: Commit the desktop API**

```bash
git add inspector-web/src/main/java inspector-web/src/test/java
git commit -m "feat: expose Nachi inspection desktop API"
```

---

### Task 6: Build The Topology-First Frontend

**Files:**
- Create: `inspector-web/src/main/resources/static/index.html`
- Create: `inspector-web/src/main/resources/static/css/styles.css`
- Create: `inspector-web/src/main/resources/static/js/app.js`
- Copy: `krl-web/src/main/resources/static/vendor/cytoscape.min.js`
- Copy: `krl-web/src/main/resources/static/vendor/dagre.min.js`
- Copy: `krl-web/src/main/resources/static/vendor/cytoscape-dagre.min.js`
- Copy: `krl-web/src/main/resources/static/vendor/lucide.js`
- Copy: `krl-web/src/main/resources/static/favicon.ico`

- [ ] **Step 1: Add a failing static-resource smoke assertion**

Extend `NachiNetInspectorApplicationTest` with MockMvc:

```java
@Autowired
MockMvc mockMvc;

@Test
void servesInspectorHomePage() throws Exception {
    mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nachi Net Inspector")))
            .andExpect(content().string(containsString("开始解析")));
}
```

Change the class annotation to `@SpringBootTest` plus `@AutoConfigureMockMvc`.

- [ ] **Step 2: Run the smoke test to verify failure**

Run: `mvn -B -pl inspector-web -am -Dtest=NachiNetInspectorApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because no new static homepage exists.

- [ ] **Step 3: Create the UI shell**

Create semantic elements with these stable IDs:

```html
<input id="fileUpload" type="file" multiple>
<button id="fileUploadButton">上传备份</button>
<button id="startInspectionButton" disabled>开始解析</button>
<button id="exportExcelButton" disabled>导出 Excel</button>
<span id="selectedFileCount">未选择文件</span>
<span id="appVersion"></span>
<section id="emptyState"></section>
<aside id="resultSidebar"></aside>
<section id="resultWorkspace" class="hidden">
  <div id="summaryCards"></div>
  <div id="topology"></div>
  <div id="warningSummary"></div>
  <tbody id="deviceTableBody"></tbody>
</section>
<aside id="deviceDetailPanel" class="hidden"></aside>
<div id="loadingOverlay" class="hidden"></div>
```

Load only `cytoscape.min.js`, `dagre.min.js`, `cytoscape-dagre.min.js`, `lucide.js`, `css/styles.css`, and `js/app.js`. Do not load Tailwind.

- [ ] **Step 4: Implement frontend state and API flow**

Use one state object:

```javascript
const state = {
    files: [],
    batch: null,
    selectedItemIndex: -1,
    selectedDeviceKey: null,
    appVersion: ''
};
```

Implement these exact responsibilities:

```javascript
async function loadRuntimeStatus()
async function startInspection()
async function exportExcel()
function buildFormData()
function selectResult(index)
function selectDevice(deviceKey)
function renderAll()
function renderSidebar()
function renderSummary()
function renderTopology()
function renderDeviceTable()
function renderWarnings()
function renderDeviceDetail()
function updateActionState()
```

`startInspection` posts to `/api/inspection`, stores the JSON batch, selects the first item whose status is not `FAILED`, and renders mixed results without alerts. `exportExcel` posts the same current files to `/api/inspection/excel` and downloads `Nachi网络信息.xlsx`.

Use constants for the core's Chinese JSON keys:

```javascript
const RESULT_KEYS = {
    robotName: '机器人名称',
    success: '成功解析',
    body: '机器人自身网络信息',
    children: '子设备网络信息',
    warnings: '异常信息'
};
```

Use equivalent constants for all `DeviceNet` fields so field access is centralized.

- [ ] **Step 5: Implement topology, table, and detail synchronization**

Build Cytoscape elements from the selected usable item:

- root ID `robot-body`, orange rounded node;
- child IDs `device-<index>`, blue rounded nodes;
- one edge from root to each child;
- Dagre left-to-right layout;
- tapping a node calls `selectDevice(node.id())`;
- clicking a table row calls the same `selectDevice`;
- selected topology node and selected row share an `.is-selected` visual state;
- detail panel shows all twelve exported fields, rendering blank offsets as `--`.

Failed items render their `errorMessage` in the sidebar and cannot render a topology. Partial items render their usable topology plus warning cards.

- [ ] **Step 6: Adapt the copied visual language**

Implement:

- orange gradient brand icon;
- blue upload/start actions and orange Excel action;
- translucent fixed header;
- light dotted topology canvas;
- white rounded panels with slate text and soft shadows;
- desktop left result sidebar;
- responsive stacked layout under `768px`;
- horizontally scrollable device table;
- visible keyboard focus and disabled states;
- no Config, graph-mode switch, node sizing, server badge, or task controls.

- [ ] **Step 7: Run the static-resource and full Maven tests**

Run: `mvn -B test`

Expected: PASS.

- [ ] **Step 8: Commit the frontend**

```bash
git add inspector-web/src/main/resources inspector-web/src/test/java/tech/waitforu/inspectorweb/NachiNetInspectorApplicationTest.java
git commit -m "feat: add topology-first Nachi inspection UI"
```

---

### Task 7: Verify The Complete Local Workflow In Browser

**Files:**
- Modify as required by findings: `inspector-web/src/main/resources/static/index.html`
- Modify as required by findings: `inspector-web/src/main/resources/static/css/styles.css`
- Modify as required by findings: `inspector-web/src/main/resources/static/js/app.js`
- Modify as required by findings: `inspector-web/src/main/java/tech/waitforu/inspectorweb/**`

- [ ] **Step 1: Package and start the application without auto-launch interference**

Run: `mvn -B -pl inspector-web -am clean package`

Expected: PASS and an executable `inspector-web/target/inspector-web-*.jar`.

Run: `java -jar inspector-web/target/inspector-web-1.0-SNAPSHOT.jar`

Expected: application listens on `127.0.0.1:2026`.

- [ ] **Step 2: Open the local application using the Browser plugin**

Open: `http://localhost:2026`

Expected: the initial screen shows product identity, version, upload, start, export, and the three-step empty-state guide.

- [ ] **Step 3: Create uploadable validation fixtures**

Create ZIP archives from existing representative directories without adding them to git:

```bash
cd inspector-core/src/test/测试
(cd APR15R1 && zip -qr /tmp/APR15R1.backup .)
(cd op10R1 && zip -qr /tmp/op10R1.any .)
printf 'not a backup' > /tmp/invalid.txt
```

Expected: two valid arbitrary-extension archives and one invalid file.

- [ ] **Step 4: Verify the mixed batch**

In the Browser plugin:

1. Select `/tmp/APR15R1.backup`, `/tmp/op10R1.any`, and `/tmp/invalid.txt`.
2. Confirm the UI does not parse until "开始解析" is clicked.
3. Click "开始解析".
4. Verify valid robots appear with topology and table data.
5. Verify the invalid file appears as failed without hiding valid robots.
6. Switch robots.
7. Select a topology node and a table row; verify synchronized selection and all evidence fields.
8. Verify partial warnings remain visible when present.
9. Resize to a narrow viewport and verify stacked layout/table scrolling.
10. Select only `/tmp/invalid.txt`, start inspection, and verify the all-failed state keeps export disabled.

- [ ] **Step 5: Verify Excel download**

Click "导出 Excel" to verify the browser download interaction. Independently verify the endpoint workbook bytes with:

```bash
curl -sS \
  -F 'files=@/tmp/APR15R1.backup' \
  -F 'files=@/tmp/op10R1.any' \
  -F 'files=@/tmp/invalid.txt' \
  -o /tmp/Nachi网络信息.xlsx \
  http://localhost:2026/api/inspection/excel
unzip -l /tmp/Nachi网络信息.xlsx | sed -n '1,40p'
```

Expected: a valid XLSX archive with workbook and worksheet entries; the browser downloads successfully while the invalid upload is ignored.

- [ ] **Step 6: Fix any discovered behavior and rerun tests**

Run: `mvn -B test`

Expected: PASS after any browser-driven corrections.

- [ ] **Step 7: Commit browser verification fixes**

```bash
git add inspector-web
git commit -m "fix: polish desktop inspection workflow"
```

---

### Task 8: Remove Copied Product Code And Rewrite Documentation

**Files:**
- Delete: `krl-core/`
- Delete: `krl-web/`
- Delete: `Dockerfile`
- Delete: `deploy/`
- Delete: `package.json`
- Delete: `package-lock.json`
- Modify: `.gitignore`
- Modify: `README.md`

- [ ] **Step 1: Remove copied modules and deployment assets**

```bash
git rm -r krl-core krl-web deploy Dockerfile package.json package-lock.json
```

Expected: only `inspector-core` and `inspector-web` remain as product modules.

- [ ] **Step 2: Rewrite README for the desktop utility**

Document:

- purpose: inspect NACHI robot body and attached-device network information;
- multiple arbitrary-extension file selection;
- explicit upload then start workflow;
- topology, table, parsing evidence, and mixed-result behavior;
- one Excel Sheet per usable robot with all twelve evidence columns;
- source run command `mvn -pl inspector-web -am spring-boot:run`;
- test command `mvn test`;
- Windows/macOS release assets;
- local runtime log path `~/.NachiNetInspector/logs/`.

Do not retain KUKA, KRL, Config, server mode, Docker, authentication, or async-task sections.

- [ ] **Step 3: Update ignore rules**

Replace stale:

```gitignore
KRL_LOG_DIR:/
```

with:

```gitignore
.NachiNetInspector/
build/
```

- [ ] **Step 4: Scan active project files for copied product references**

Run:

```bash
rg -n "KRLParser|krl-core|krl-web|KRL_|/api/config|analysis/tasks|Docker" \
  --glob '!inspector-core/src/test/测试/**' \
  --glob '!.github/workflows/release.yml' \
  --glob '!docs/superpowers/**' .
```

Expected: no matches in active product, build, release, or README files.

- [ ] **Step 5: Run the complete test suite**

Run: `mvn -B clean test`

Expected: PASS with only root, `inspector-core`, and `inspector-web` in the reactor.

- [ ] **Step 6: Commit cleanup and documentation**

```bash
git add -A
git commit -m "chore: remove copied KRL and server assets"
```

---

### Task 9: Update Windows/macOS Release Packaging

**Files:**
- Modify: `.github/workflows/release.yml`

- [ ] **Step 1: Rewrite the release workflow**

Keep tag (`v*`) and manual triggers. Use a Windows/macOS matrix. Resolve tag builds to the tag version without `v`; resolve manual builds to the jpackage-compatible numeric version `0.0.${GITHUB_RUN_NUMBER}`. The build job must:

1. check out source;
2. set up Temurin JDK 21 with Maven cache;
3. resolve the tag or development version;
4. run:

```bash
mvn -B versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false -DprocessAllModules=true
mvn -B clean verify
```

5. locate `inspector-web/target/inspector-web-*.jar`, excluding `.original`;
6. package with:

```bash
jpackage \
  --type app-image \
  --name "NachiNetInspector" \
  --app-version "$VERSION" \
  --input build/input \
  --main-jar app.jar \
  --icon "$ICON_PATH" \
  --vendor "WaitForU" \
  --dest build/dist
```

7. create `NachiNetInspector-<version>-windows.zip` or `NachiNetInspector-<version>-macos.zip`;
8. upload only ZIP artifacts.

The release job must depend only on `build-desktop` and publish only `artifacts/*.zip`. Remove the Docker image job and `.tar.gz` publishing.

- [ ] **Step 2: Validate local packaging inputs**

Run:

```bash
mvn -B clean verify
find inspector-web/target -maxdepth 1 -type f -name 'inspector-web-*.jar' ! -name '*.original'
```

Expected: tests pass and exactly one executable Spring Boot JAR is listed.

- [ ] **Step 3: Inspect the workflow for stale product and Docker references**

Run:

```bash
rg -n "KRLParser|krl-web|build-docker|docker|tar.gz" .github/workflows/release.yml
```

Expected: no matches.

- [ ] **Step 4: Commit release packaging**

```bash
git add .github/workflows/release.yml
git commit -m "ci: package Nachi inspector desktop releases"
```

---

### Task 10: Final Verification

**Files:**
- Modify only files required to fix verification findings.

- [ ] **Step 1: Run the complete clean build**

Run: `mvn -B clean verify`

Expected: BUILD SUCCESS with all core and web tests passing.

- [ ] **Step 2: Run static consistency checks**

Run:

```bash
git diff --check
rg -n "KRLParser|krl-core|krl-web|KRL_|/api/config|analysis/tasks|Docker" \
  --glob '!inspector-core/src/test/测试/**' \
  --glob '!docs/superpowers/**' .
```

Expected: no whitespace errors and no copied product references.

- [ ] **Step 3: Run the packaged application and repeat the critical browser path**

Start the executable JAR, then use the Browser plugin to verify:

- initial empty state;
- mixed valid/invalid batch;
- robot switching;
- topology/table/detail synchronization;
- Excel download;
- narrow-screen layout.

Expected: all acceptance criteria in the approved design document are satisfied.

- [ ] **Step 4: Check repository status**

Run: `git status --short`

Expected: clean worktree.
