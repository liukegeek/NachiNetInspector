package tech.waitforu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.waitforu.exceptions.BackupLoadException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackFileTest {

    @TempDir
    Path tempDir;

    @Test
    void unsupportedPlainFileThrowsBackupLoadException() throws IOException {
        Path plainFile = Files.writeString(tempDir.resolve("backup.txt"), "not a supported backup");

        BackupLoadException exception = assertThrows(
                BackupLoadException.class,
                () -> new BackFile(plainFile.toString()));

        assertTrue(exception.getMessage().contains("无法打开备份文件"));
        assertNotNull(exception.getCause());
    }

    @Test
    void readBytesWrapsProviderReadFailure() {
        try (BackFile backFile = new BackFile(tempDir.toString())) {
            BackupLoadException exception = assertThrows(
                    BackupLoadException.class,
                    () -> backFile.readBytes("missing.nxd"));

            assertTrue(exception.getMessage().contains("无法读取备份内文件: missing.nxd"));
            assertNotNull(exception.getCause());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
