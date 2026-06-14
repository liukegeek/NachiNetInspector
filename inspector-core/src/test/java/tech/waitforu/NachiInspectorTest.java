package tech.waitforu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NachiInspectorTest {

    private static final Path VALID_BACKUP = Path.of("src/test/测试/APR15R1");

    @TempDir
    Path tempDir;

    @Test
    void validDirectoryInspectsSuccessfully() throws IOException {
        try (BackFile backFile = new BackFile(VALID_BACKUP.toString())) {
            NachiNetResume result = NachiInspector.inspect(backFile);

            assertTrue(result.isSuccess());
            assertEquals("apr15r1", result.robotName());
            assertNotNull(result.robotSelfNet());
            assertEquals("190.64.6.89", result.robotSelfNet().deviceIP());
            assertEquals("0xDE", result.robotSelfNet().ipOffset());
            assertEquals(3, result.subDevicesNet().size());
        }
    }

    @Test
    void missingConfigPreservesUsableRobotBodyData() throws IOException {
        Path plcEngine = Files.createDirectories(tempDir.resolve("PLCEngine"));
        Files.copy(
                VALID_BACKUP.resolve(NachiInspector.ROBOT_NET_FILE),
                plcEngine.resolve("nwid1.nxd"));

        try (BackFile backFile = new BackFile(tempDir.toString())) {
            NachiNetResume result = NachiInspector.inspect(backFile);

            assertFalse(result.isSuccess());
            assertNotNull(result.robotSelfNet());
            assertEquals("190.64.6.89", result.robotSelfNet().deviceIP());
            assertFalse(result.exceptionMessage().isEmpty());
        }
    }

    @Test
    void unexpectedRuntimeFailuresProduceSectionSpecificReadableWarnings() throws IOException {
        try (BackFile backFile = new BackFile(VALID_BACKUP.toString()) {
            @Override
            public byte[] readBytes(String pathString) {
                throw new IllegalStateException();
            }
        }) {
            NachiNetResume result = NachiInspector.inspect(backFile);

            assertFalse(result.isSuccess());
            assertEquals(3, result.exceptionMessage().size());
            assertTrue(result.exceptionMessage().stream()
                    .anyMatch(message -> message.contains("机器人本体网络信息")
                            && message.contains("IllegalStateException")));
            assertTrue(result.exceptionMessage().stream()
                    .anyMatch(message -> message.contains("子设备网络信息")
                            && message.contains("IllegalStateException")));
            assertTrue(result.exceptionMessage().stream()
                    .anyMatch(message -> message.contains("机器人本体名称")
                            && message.contains("IllegalStateException")));
        }
    }
}
