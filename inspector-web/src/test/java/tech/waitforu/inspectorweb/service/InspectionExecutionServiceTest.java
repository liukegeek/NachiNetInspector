package tech.waitforu.inspectorweb.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tech.waitforu.DeviceNet;
import tech.waitforu.NachiNetResume;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionExecutionServiceTest {

    @Test
    void mixedGoodAndBadFilesPreserveUploadOrderStatusesAndCounts() {
        BackupInspectionRunner runner = path -> switch (read(path)) {
            case "good" -> result("good", true, device("body"), List.of(), List.of());
            case "partial" -> result("partial", false, null, List.of(device("child")), List.of("body missing"));
            default -> throw new IllegalArgumentException("not a backup");
        };
        InspectionExecutionService service = service(runner);

        InspectionBatchResponse response = service.inspect(List.of(
                upload("good.zip", "good"),
                upload("broken.bin", "bad"),
                upload("partial.backup", "partial")));

        assertEquals(List.of("good.zip", "broken.bin", "partial.backup"),
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
    void usableResultWithWarningsIsPartialEvenWhenCoreMarksItSuccessful() {
        NachiNetResume warned = result("warned", true, device("body"), List.of(), List.of("child warning"));
        InspectionExecutionService service = service(path -> warned);

        InspectionItem item = service.inspect(List.of(upload("warned.zip", "warned"))).items().getFirst();

        assertEquals(InspectionStatus.PARTIAL, item.status());
        assertEquals(warned, item.result());
        assertEquals("child warning", item.errorMessage());
    }

    @Test
    void noUsableDevicesFailsWithReadableJoinedWarnings() {
        InspectionExecutionService service = service(path ->
                result("empty", false, null, null, List.of("robot network missing", "device network missing")));

        InspectionItem item = service.inspect(List.of(upload("empty.zip", "empty"))).items().getFirst();

        assertEquals(InspectionStatus.FAILED, item.status());
        assertNull(item.result());
        assertTrue(item.errorMessage().contains("robot network missing"));
        assertTrue(item.errorMessage().contains("device network missing"));
    }

    @Test
    void arbitraryExtensionsAndFilesWithoutExtensionsAreAccepted() {
        List<Path> observedPaths = new ArrayList<>();
        InspectionExecutionService service = service(path -> {
            observedPaths.add(path);
            return result(path.getFileName().toString(), true, device("body"), List.of(), List.of());
        });

        InspectionBatchResponse response = service.inspect(List.of(
                upload("backup.strange-extension", "one"),
                upload("backup", "two")));

        assertEquals(List.of(InspectionStatus.SUCCESS, InspectionStatus.SUCCESS),
                response.items().stream().map(InspectionItem::status).toList());
        assertTrue(observedPaths.get(0).getFileName().toString().endsWith(".strange-extension"));
        assertTrue(observedPaths.get(1).getFileName().toString().endsWith("backup"));
    }

    @Test
    void removesObservedTemporaryFilesAndDirectoryAfterInspectionIncludingRunnerException() {
        List<Path> observedPaths = new ArrayList<>();
        InspectionExecutionService service = service(path -> {
            observedPaths.add(path);
            if (read(path).equals("bad")) {
                throw new IllegalStateException("inspection failed");
            }
            return result("good", true, device("body"), List.of(), List.of());
        });

        service.inspect(List.of(upload("good.zip", "good"), upload("bad.zip", "bad")));

        assertEquals(2, observedPaths.size());
        assertEquals(observedPaths.get(0).getParent(), observedPaths.get(1).getParent());
        assertTrue(observedPaths.get(0).getParent().getFileName().toString().startsWith("nachi-inspection-"));
        assertFalse(Files.exists(observedPaths.get(0)));
        assertFalse(Files.exists(observedPaths.get(1)));
        assertFalse(Files.exists(observedPaths.get(0).getParent()));
    }

    @Test
    void duplicateUploadFilenamesDoNotCollide() {
        List<Path> observedPaths = new ArrayList<>();
        List<String> observedContents = new ArrayList<>();
        InspectionExecutionService service = service(path -> {
            observedPaths.add(path);
            observedContents.add(read(path));
            return result("good", true, device("body"), List.of(), List.of());
        });

        service.inspect(List.of(upload("same.zip", "first"), upload("same.zip", "second")));

        assertNotEquals(observedPaths.get(0).getFileName(), observedPaths.get(1).getFileName());
        assertEquals(List.of("first", "second"), observedContents);
    }

    @Test
    void sanitizesOnlySlashAndBackslashInUploadedFilename() {
        List<String> observedNames = new ArrayList<>();
        InspectionExecutionService service = service(path -> {
            observedNames.add(path.getFileName().toString());
            return result("good", true, device("body"), List.of(), List.of());
        });

        service.inspect(List.of(upload("../folder\\backup.$odd", "data")));

        assertEquals(1, observedNames.size());
        assertFalse(observedNames.getFirst().contains("/"));
        assertFalse(observedNames.getFirst().contains("\\"));
        assertTrue(observedNames.getFirst().endsWith(".._folder_backup.$odd"));
    }

    @Test
    void exportPassesOnlyUsableSuccessAndPartialResultsToExporter() {
        NachiNetResume success = result("success", true, device("body"), List.of(), List.of());
        NachiNetResume failed = result("failed", false, null, null, List.of("missing"));
        NachiNetResume partial = result("partial", false, null, List.of(device("child")), List.of("body missing"));
        RecordingExporter exporter = new RecordingExporter(new byte[]{1, 2, 3});
        InspectionExecutionService service = new InspectionExecutionService(
                path -> switch (read(path)) {
                    case "success" -> success;
                    case "partial" -> partial;
                    default -> failed;
                },
                exporter);

        byte[] exported = service.exportExcel(List.of(
                upload("success.zip", "success"),
                upload("failed.zip", "failed"),
                upload("partial.zip", "partial")));

        assertArrayEquals(new byte[]{1, 2, 3}, exported);
        assertEquals(List.of(success, partial), exporter.observedResults);
    }

    @Test
    void nullEmptyOrOnlyEmptyUploadsThrowBadRequestException() {
        InspectionExecutionService service = service(path ->
                result("unused", true, device("body"), List.of(), List.of()));

        assertEquals("请上传备份文件",
                assertThrows(BadRequestException.class, () -> service.inspect(null)).getMessage());
        assertEquals("请上传备份文件",
                assertThrows(BadRequestException.class, () -> service.inspect(List.of())).getMessage());
        assertEquals("请上传备份文件",
                assertThrows(BadRequestException.class, () -> service.inspect(Arrays.asList(
                        null,
                        new MockMultipartFile("files", "empty.zip", "application/zip", new byte[0]))))
                        .getMessage());
    }

    @Test
    void exportWithoutUsableResultsThrowsExactMessage() {
        InspectionExecutionService service = service(path ->
                result("empty", false, null, null, List.of("missing")));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.exportExcel(List.of(upload("empty.zip", "empty"))));

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
