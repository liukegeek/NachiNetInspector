package tech.waitforu.inspectorweb.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.waitforu.NachiNetResume;
import tech.waitforu.exceptions.BackupLoadException;
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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InspectionExecutionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(InspectionExecutionService.class);
    private static final String UPLOAD_REQUIRED = "请选择备份目录";
    private static final String NO_EXPORTABLE_DATA = "没有可导出的网络设备信息";
    private static final String NO_USABLE_DATA = "未发现可用的网络设备信息";
    private static final String INSPECTION_FAILED = "解析备份目录失败，请查看日志";
    private static final String INVALID_DIRECTORY_PATH = "备份目录路径无效";

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
            for (BackupDirectory backupDirectory : groupByBackupDirectory(usableUploads).values()) {
                String sourceFileName = backupDirectory.sourceFileName;
                try {
                    if (backupDirectory.error != null) {
                        throw backupDirectory.error;
                    }
                    Path backupPath = rebuild(temporaryDirectory, backupDirectory);
                    items.add(classify(sourceFileName, runner.inspect(backupPath)));
                } catch (BackupLoadException | IllegalArgumentException exception) {
                    items.add(new InspectionItem(
                            sourceFileName,
                            InspectionStatus.FAILED,
                            null,
                            readableMessage(exception)));
                } catch (Exception exception) {
                    LOGGER.error("Unexpected error inspecting uploaded backup {}", sourceFileName, exception);
                    items.add(new InspectionItem(
                            sourceFileName,
                            InspectionStatus.FAILED,
                            null,
                            INSPECTION_FAILED));
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
                .filter(upload -> upload != null)
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

    private static Map<String, BackupDirectory> groupByBackupDirectory(List<MultipartFile> uploads) {
        Map<String, BackupDirectory> directories = new LinkedHashMap<>();
        for (int index = 0; index < uploads.size(); index++) {
            MultipartFile upload = uploads.get(index);
            try {
                UploadPath uploadPath = parseUploadPath(upload);
                directories.computeIfAbsent(uploadPath.topLevelDirectory, BackupDirectory::new)
                        .uploads.add(new DirectoryFile(upload, uploadPath.relativePath));
            } catch (IllegalArgumentException exception) {
                String key = "invalid-" + index;
                String originalFilename = upload.getOriginalFilename();
                String sourceFileName = originalFilename == null || originalFilename.isBlank()
                        ? key
                        : originalFilename;
                directories.put(key, new BackupDirectory(sourceFileName, exception));
            }
        }
        return directories;
    }

    private static UploadPath parseUploadPath(MultipartFile upload) {
        String originalFilename = upload.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException(INVALID_DIRECTORY_PATH);
        }
        String normalized = originalFilename.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException(INVALID_DIRECTORY_PATH);
        }
        String[] segments = normalized.split("/", -1);
        if (segments.length < 2) {
            throw new IllegalArgumentException(INVALID_DIRECTORY_PATH);
        }
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(INVALID_DIRECTORY_PATH);
            }
        }
        return new UploadPath(segments[0], Path.of("", segments).subpath(1, segments.length));
    }

    private static Path rebuild(Path temporaryDirectory, BackupDirectory backupDirectory) throws IOException {
        Path backupPath = temporaryDirectory.resolve(backupDirectory.sourceFileName).normalize();
        if (!backupPath.startsWith(temporaryDirectory)) {
            throw new IllegalArgumentException(INVALID_DIRECTORY_PATH);
        }
        Files.createDirectories(backupPath);
        for (DirectoryFile directoryFile : backupDirectory.uploads) {
            Path destination = backupPath.resolve(directoryFile.relativePath).normalize();
            if (!destination.startsWith(backupPath)) {
                throw new IllegalArgumentException(INVALID_DIRECTORY_PATH);
            }
            Files.createDirectories(destination.getParent());
            try (InputStream input = directoryFile.upload.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return backupPath;
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

    private record UploadPath(String topLevelDirectory, Path relativePath) {
    }

    private record DirectoryFile(MultipartFile upload, Path relativePath) {
    }

    private static final class BackupDirectory {
        private final String sourceFileName;
        private final List<DirectoryFile> uploads = new ArrayList<>();
        private final IllegalArgumentException error;

        private BackupDirectory(String sourceFileName) {
            this(sourceFileName, null);
        }

        private BackupDirectory(String sourceFileName, IllegalArgumentException error) {
            this.sourceFileName = sourceFileName;
            this.error = error;
        }
    }
}
