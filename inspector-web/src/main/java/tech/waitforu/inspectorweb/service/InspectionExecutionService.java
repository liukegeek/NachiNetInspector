package tech.waitforu.inspectorweb.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.waitforu.NachiNetResume;
import tech.waitforu.inspectorweb.exception.ApiException;
import tech.waitforu.inspectorweb.exception.BadRequestException;
import tech.waitforu.inspectorweb.model.InspectionBatchResponse;
import tech.waitforu.inspectorweb.model.InspectionItem;
import tech.waitforu.inspectorweb.model.InspectionStatus;
import tech.waitforu.service.NetworkExcelExportService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class InspectionExecutionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(InspectionExecutionService.class);
    private static final String UPLOAD_REQUIRED = "请上传备份文件";
    private static final String NO_EXPORTABLE_DATA = "没有可导出的网络设备信息";
    private static final String NO_USABLE_DATA = "未发现可用的网络设备信息";

    private final BackupInspectionRunner runner;
    private final NetworkExcelExportService exporter;

    public InspectionExecutionService(BackupInspectionRunner runner, NetworkExcelExportService exporter) {
        this.runner = runner;
        this.exporter = exporter;
    }

    public InspectionBatchResponse inspect(List<MultipartFile> uploads) {
        List<MultipartFile> usableUploads = usableUploads(uploads);
        Path temporaryDirectory = createTemporaryDirectory();
        List<InspectionItem> items = new ArrayList<>();

        try {
            for (int index = 0; index < usableUploads.size(); index++) {
                MultipartFile upload = usableUploads.get(index);
                String sourceFileName = sourceFileName(upload, index);
                try {
                    Path backupPath = temporaryDirectory.resolve(index + "-" + sanitize(sourceFileName));
                    copy(upload, backupPath);
                    items.add(classify(sourceFileName, runner.inspect(backupPath)));
                } catch (Exception exception) {
                    items.add(new InspectionItem(
                            sourceFileName,
                            InspectionStatus.FAILED,
                            null,
                            readableMessage(exception)));
                }
            }
            return InspectionBatchResponse.from(items);
        } finally {
            deleteRecursively(temporaryDirectory);
        }
    }

    public byte[] exportExcel(List<MultipartFile> uploads) {
        List<NachiNetResume> results = inspect(uploads).items().stream()
                .filter(item -> item.status() == InspectionStatus.SUCCESS
                        || item.status() == InspectionStatus.PARTIAL)
                .filter(InspectionItem::hasUsableData)
                .map(InspectionItem::result)
                .toList();
        if (results.isEmpty()) {
            throw new BadRequestException(NO_EXPORTABLE_DATA);
        }
        return exporter.export(results);
    }

    private static List<MultipartFile> usableUploads(List<MultipartFile> uploads) {
        if (uploads == null) {
            throw new BadRequestException(UPLOAD_REQUIRED);
        }
        List<MultipartFile> usableUploads = uploads.stream()
                .filter(upload -> upload != null && !upload.isEmpty())
                .toList();
        if (usableUploads.isEmpty()) {
            throw new BadRequestException(UPLOAD_REQUIRED);
        }
        return usableUploads;
    }

    private static Path createTemporaryDirectory() {
        try {
            return Files.createTempDirectory("nachi-inspection-");
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "创建临时检查目录失败", exception);
        }
    }

    private static String sourceFileName(MultipartFile upload, int index) {
        String originalFilename = upload.getOriginalFilename();
        return originalFilename == null || originalFilename.isBlank()
                ? "backup-" + index
                : originalFilename;
    }

    private static String sanitize(String filename) {
        return filename.replace('/', '_').replace('\\', '_');
    }

    private static void copy(MultipartFile upload, Path destination) throws IOException {
        try (InputStream input = upload.getInputStream()) {
            Files.copy(input, destination);
        }
    }

    private static InspectionItem classify(String sourceFileName, NachiNetResume result) {
        InspectionItem candidate = new InspectionItem(sourceFileName, null, result, null);
        String warnings = joinedWarnings(result);
        if (!candidate.hasUsableData()) {
            return new InspectionItem(
                    sourceFileName,
                    InspectionStatus.FAILED,
                    null,
                    warnings.isBlank() ? NO_USABLE_DATA : warnings);
        }
        if (result.isSuccess() && warnings.isBlank()) {
            return new InspectionItem(sourceFileName, InspectionStatus.SUCCESS, result, null);
        }
        return new InspectionItem(
                sourceFileName,
                InspectionStatus.PARTIAL,
                result,
                warnings.isBlank() ? "检查未完全成功" : warnings);
    }

    private static String joinedWarnings(NachiNetResume result) {
        if (result == null || result.exceptionMessage() == null) {
            return "";
        }
        return String.join(
                "；",
                result.exceptionMessage().stream()
                        .filter(message -> message != null && !message.isBlank())
                        .toList());
    }

    private static String readableMessage(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return exception.getClass().getSimpleName();
    }

    private static void deleteRecursively(Path directory) {
        List<Path> pathsToDelete;
        try (var paths = Files.walk(directory)) {
            pathsToDelete = paths.sorted(Comparator.reverseOrder()).toList();
        } catch (IOException exception) {
            LOGGER.warn("Unable to inspect temporary directory {} for cleanup", directory, exception);
            return;
        }

        for (Path path : pathsToDelete) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                LOGGER.warn("Unable to remove temporary inspection path {}", path, exception);
            }
        }
    }
}
