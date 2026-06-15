package tech.waitforu.inspectorweb.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tech.waitforu.DeviceNet;
import tech.waitforu.NachiNetResume;
import tech.waitforu.exceptions.BackupLoadException;
import tech.waitforu.inspectorweb.exception.BadRequestException;
import tech.waitforu.inspectorweb.model.InspectionBatchResponse;
import tech.waitforu.inspectorweb.model.InspectionItem;
import tech.waitforu.inspectorweb.model.InspectionStatus;
import tech.waitforu.service.NetworkExcelExportService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionExecutionServiceTest {

    @Test
    void rebuildsBackupDirectoriesAndInspectsEachTopLevelDirectoryOnceInFirstSeenOrder() {
        List<String> observedDirectories = new ArrayList<>();
        List<String> observedFiles = new ArrayList<>();
        InspectionExecutionService service = service(path -> {
            assertTrue(Files.isDirectory(path));
            observedDirectories.add(path.getFileName().toString());
            try (var files = Files.walk(path)) {
                observedFiles.addAll(files
                        .filter(Files::isRegularFile)
                        .map(file -> path.getFileName() + "/" + path.relativize(file).toString().replace('\\', '/')
                                + "=" + read(file))
                        .toList());
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            return result(path.getFileName().toString(), true, device("body"), List.of(), List.of());
        });

        InspectionBatchResponse response = service.inspect(List.of(
                upload("APR15R1/PLCEngine/nwid1.nxd", "network"),
                upload("APR15R1/RESUME.bak", ""),
                upload("SECOND\\config\\robot.dat", "second"),
                upload("APR15R1/config/late.dat", "late")));

        observedFiles.sort(String::compareTo);
        assertEquals(List.of("APR15R1", "SECOND"), observedDirectories);
        assertEquals(List.of(
                "APR15R1/PLCEngine/nwid1.nxd=network",
                "APR15R1/RESUME.bak=",
                "APR15R1/config/late.dat=late",
                "SECOND/config/robot.dat=second"), observedFiles);
        assertEquals(List.of("APR15R1", "SECOND"),
                response.items().stream().map(InspectionItem::sourceFileName).toList());
    }

    @Test
    void blocksEscapingPathsWithoutPreventingOtherBackupDirectories() {
        List<String> observedDirectories = new ArrayList<>();
        InspectionExecutionService service = service(path -> {
            observedDirectories.add(path.getFileName().toString());
            return result("good", true, device("body"), List.of(), List.of());
        });

        InspectionBatchResponse response = service.inspect(List.of(
                upload("BAD/../../escaped.txt", "escape"),
                upload("/absolute/backup.dat", "absolute"),
                upload("GOOD/config/robot.dat", "good")));

        assertEquals(List.of("GOOD"), observedDirectories);
        assertEquals(List.of(InspectionStatus.FAILED, InspectionStatus.FAILED, InspectionStatus.SUCCESS),
                response.items().stream().map(InspectionItem::status).toList());
        assertFalse(Files.exists(Path.of("escaped.txt")));
    }

    @Test
    void mixedGoodAndBadDirectoriesPreserveUploadOrderStatusesAndCounts() {
        BackupInspectionRunner runner = path -> switch (readMarker(path)) {
            case "good" -> result("good", true, device("body"), List.of(), List.of());
            case "partial" -> result("partial", false, null, List.of(device("child")), List.of("body missing"));
            default -> throw new IllegalArgumentException("not a backup");
        };
        InspectionExecutionService service = service(runner);

        InspectionBatchResponse response = service.inspect(List.of(
                directoryUpload("good", "good"),
                directoryUpload("broken", "bad"),
                directoryUpload("partial", "partial")));

        assertEquals(List.of("good", "broken", "partial"),
                response.items().stream().map(InspectionItem::sourceFileName).toList());
        assertEquals(List.of(InspectionStatus.SUCCESS, InspectionStatus.FAILED, InspectionStatus.PARTIAL),
                response.items().stream().map(InspectionItem::status).toList());
        assertEquals(1, response.successfulCount());
        assertEquals(1, response.partialCount());
        assertEquals(1, response.failedCount());
        assertNull(response.items().get(1).result());
        assertEquals("not a backup", response.items().get(1).errorMessage());
    }

    @Test
    void unexpectedRunnerExceptionUsesControlledMessageAndLogsDisplayFilename() {
        Logger logger = (Logger) LoggerFactory.getLogger(InspectionExecutionService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        boolean additive = logger.isAdditive();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
        try {
            InspectionExecutionService service = service(path -> {
                throw new RuntimeException("internal database password=secret");
            });

            InspectionItem item = service.inspect(List.of(directoryUpload("robot-display", "bad")))
                    .items().getFirst();

            assertEquals(InspectionStatus.FAILED, item.status());
            assertEquals("解析备份目录失败，请查看日志", item.errorMessage());
            assertFalse(item.errorMessage().contains("internal database password=secret"));
            assertTrue(appender.list.stream().anyMatch(event ->
                    event.getLevel() == Level.ERROR
                            && event.getFormattedMessage().contains("robot-display")
                            && event.getThrowableProxy() != null));
        } finally {
            logger.setAdditive(additive);
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void expectedBackupAndInputErrorsRemainUserReadable() {
        InspectionExecutionService service = service(path -> {
            if (readMarker(path).equals("load")) {
                throw new BackupLoadException("无法打开备份文件");
            }
            throw new IllegalArgumentException("不支持的备份格式");
        });

        InspectionBatchResponse response = service.inspect(List.of(
                directoryUpload("load", "load"),
                directoryUpload("invalid", "invalid")));

        assertEquals(
                List.of("无法打开备份文件", "不支持的备份格式"),
                response.items().stream().map(InspectionItem::errorMessage).toList());
    }

    @Test
    void usableResultWithWarningsIsPartialEvenWhenCoreMarksItSuccessful() {
        NachiNetResume warned = result("warned", true, device("body"), List.of(), List.of("child warning"));
        InspectionExecutionService service = service(path -> warned);

        InspectionItem item = service.inspect(List.of(directoryUpload("warned", "warned"))).items().getFirst();

        assertEquals(InspectionStatus.PARTIAL, item.status());
        assertEquals(warned, item.result());
        assertEquals("child warning", item.errorMessage());
    }

    @Test
    void noUsableDevicesFailsWithReadableJoinedWarnings() {
        InspectionExecutionService service = service(path ->
                result("empty", false, null, null, List.of("robot network missing", "device network missing")));

        InspectionItem item = service.inspect(List.of(directoryUpload("empty", "empty"))).items().getFirst();

        assertEquals(InspectionStatus.FAILED, item.status());
        assertNull(item.result());
        assertTrue(item.errorMessage().contains("robot network missing"));
        assertTrue(item.errorMessage().contains("device network missing"));
    }

    @Test
    void noUsableDevicesWithoutWarningsUsesExactFallbackMessage() {
        InspectionExecutionService service = service(path ->
                result("empty", false, null, null, List.of()));

        InspectionItem item = service.inspect(List.of(directoryUpload("empty", "empty"))).items().getFirst();

        assertEquals(InspectionStatus.FAILED, item.status());
        assertNull(item.result());
        assertEquals("未发现可用的网络设备信息", item.errorMessage());
    }

    @Test
    void arbitraryExtensionsAndFilesWithoutExtensionsAreRebuiltInsideDirectory() {
        List<Path> observedPaths = new ArrayList<>();
        List<String> observedContents = new ArrayList<>();
        InspectionExecutionService service = service(path -> {
            observedPaths.add(path);
            observedContents.add(read(path.resolve("backup.strange-extension")));
            observedContents.add(read(path.resolve("backup")));
            return result(path.getFileName().toString(), true, device("body"), List.of(), List.of());
        });

        InspectionBatchResponse response = service.inspect(List.of(
                upload("backup/backup.strange-extension", "one"),
                upload("backup/backup", "two")));

        assertEquals(List.of(InspectionStatus.SUCCESS),
                response.items().stream().map(InspectionItem::status).toList());
        assertEquals(List.of("one", "two"), observedContents);
    }

    @Test
    void removesObservedTemporaryFilesAndDirectoryAfterInspectionIncludingRunnerException() {
        List<Path> observedPaths = new ArrayList<>();
        InspectionExecutionService service = service(path -> {
            observedPaths.add(path);
            if (readMarker(path).equals("bad")) {
                throw new IllegalArgumentException("inspection failed");
            }
            return result("good", true, device("body"), List.of(), List.of());
        });

        service.inspect(List.of(directoryUpload("good", "good"), directoryUpload("bad", "bad")));

        assertEquals(2, observedPaths.size());
        assertEquals(observedPaths.get(0).getParent(), observedPaths.get(1).getParent());
        assertTrue(observedPaths.get(0).getParent().getFileName().toString().startsWith("nachi-inspection-"));
        assertFalse(Files.exists(observedPaths.get(0)));
        assertFalse(Files.exists(observedPaths.get(1)));
        assertFalse(Files.exists(observedPaths.get(0).getParent()));
    }

    @Test
    void filesFromTheSameTopLevelDirectoryAreInspectedTogether() {
        List<Path> observedPaths = new ArrayList<>();
        List<String> observedContents = new ArrayList<>();
        InspectionExecutionService service = service(path -> {
            observedPaths.add(path);
            observedContents.add(read(path.resolve("first.dat")));
            observedContents.add(read(path.resolve("second.dat")));
            return result("good", true, device("body"), List.of(), List.of());
        });

        service.inspect(List.of(
                upload("same/first.dat", "first"),
                upload("same/second.dat", "second")));

        assertEquals(1, observedPaths.size());
        assertEquals(List.of("first", "second"), observedContents);
    }

    @Test
    void exportPassesOnlyUsableSuccessAndPartialResultsToExporter() {
        NachiNetResume success = result("success", true, device("body"), List.of(), List.of());
        NachiNetResume failed = result("failed", false, null, null, List.of("missing"));
        NachiNetResume partial = result("partial", false, null, List.of(device("child")), List.of("body missing"));
        RecordingExporter exporter = new RecordingExporter(new byte[]{1, 2, 3});
        InspectionExecutionService service = new InspectionExecutionService(
                path -> switch (readMarker(path)) {
                    case "success" -> success;
                    case "partial" -> partial;
                    default -> failed;
                },
                exporter);

        byte[] exported = service.exportExcel(List.of(
                directoryUpload("success", "success"),
                directoryUpload("failed", "failed"),
                directoryUpload("partial", "partial")));

        assertArrayEquals(new byte[]{1, 2, 3}, exported);
        assertEquals(List.of(success, partial), exporter.observedResults);
    }

    @Test
    void nullEmptyOrOnlyNullUploadsThrowBadRequestExceptionButEmptyFilesAreKept() {
        InspectionExecutionService service = service(path ->
                result("unused", true, device("body"), List.of(), List.of()));

        assertEquals("请选择备份目录",
                assertThrows(BadRequestException.class, () -> service.inspect(null)).getMessage());
        assertEquals("请选择备份目录",
                assertThrows(BadRequestException.class, () -> service.inspect(List.of())).getMessage());
        assertEquals("请选择备份目录",
                assertThrows(BadRequestException.class, () -> service.inspect(Arrays.asList(null, null))).getMessage());
        assertEquals(InspectionStatus.SUCCESS, service.inspect(List.of(
                new MockMultipartFile("files", "empty/zero.dat", "application/octet-stream", new byte[0])))
                .items().getFirst().status());
    }

    @Test
    void exportWithoutUsableResultsThrowsExactMessage() {
        InspectionExecutionService service = service(path ->
                result("empty", false, null, null, List.of("missing")));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.exportExcel(List.of(directoryUpload("empty", "empty"))));

        assertEquals("没有可导出的网络设备信息", exception.getMessage());
    }

    @Test
    void batchResponseFromIsImmutableAndNullSafe() {
        InspectionBatchResponse empty = InspectionBatchResponse.from(null);
        InspectionItem success = new InspectionItem(
                "success.zip",
                InspectionStatus.SUCCESS,
                result("success", true, device("body"), null, null),
                null);

        InspectionBatchResponse response = InspectionBatchResponse.from(Arrays.asList(null, success));

        assertEquals(List.of(), empty.items());
        assertEquals(0, empty.successfulCount());
        assertEquals(List.of(success), response.items());
        assertEquals(1, response.successfulCount());
        assertThrows(UnsupportedOperationException.class, () -> response.items().add(success));
    }

    private static InspectionExecutionService service(BackupInspectionRunner runner) {
        return new InspectionExecutionService(runner, new RecordingExporter(new byte[]{9}));
    }

    private static MockMultipartFile upload(String originalFilename, String contents) {
        return new MockMultipartFile(
                "files",
                originalFilename,
                "application/octet-stream",
                contents.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile directoryUpload(String directoryName, String contents) {
        return upload(directoryName + "/marker.txt", contents);
    }

    private static String readMarker(Path directory) {
        return read(directory.resolve("marker.txt"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static NachiNetResume result(
            String robotName,
            boolean success,
            DeviceNet body,
            List<DeviceNet> children,
            List<String> warnings
    ) {
        return new NachiNetResume(robotName, success, body, children, warnings);
    }

    private static DeviceNet device(String name) {
        return new DeviceNet(
                name,
                "10.0.0.1",
                "255.255.255.0",
                "10.0.0.254",
                "config.nxd",
                "ABCD",
                "0x01",
                "0x02",
                "0x03",
                "0x04",
                "0x05",
                "0x06");
    }

    private static final class RecordingExporter extends NetworkExcelExportService {
        private final byte[] exportedBytes;
        private List<NachiNetResume> observedResults = List.of();

        private RecordingExporter(byte[] exportedBytes) {
            this.exportedBytes = exportedBytes;
        }

        @Override
        public byte[] export(List<NachiNetResume> results) {
            observedResults = List.copyOf(results);
            return exportedBytes;
        }
    }
}
