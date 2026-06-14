package tech.waitforu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    void malformedFirstDeviceDoesNotPreventLaterDevicesFromBeingParsed() throws IOException {
        Path plcEngine = Files.createDirectories(tempDir.resolve("PLCEngine"));
        Files.copy(
                VALID_BACKUP.resolve(NachiInspector.ROBOT_NET_FILE),
                plcEngine.resolve("nwid1.nxd"));

        byte[] config = Files.readAllBytes(VALID_BACKUP.resolve(NachiInspector.SUB_DEVICE_NET_FILE));
        List<Integer> deviceRecordStarts = findSubArray(config, NachiInspector.CONFIG_DEVICE_RECORD_HEADER);
        int firstNameLengthOffset = deviceRecordStarts.getFirst() + NachiInspector.CONFIG_DEVICE_NAME_LENGTH_REL;
        config[firstNameLengthOffset] = (byte) 0xFF;
        config[firstNameLengthOffset + 1] = (byte) 0xFF;
        Files.write(plcEngine.resolve("config1.nxd"), config);

        try (BackFile backFile = new BackFile(tempDir.toString())) {
            NachiNetResume result = NachiInspector.inspect(backFile);

            assertFalse(result.isSuccess());
            assertTrue(result.exceptionMessage().stream()
                    .anyMatch(message -> message.contains("设备1")));
            assertEquals(deviceRecordStarts.size() - 1, result.subDevicesNet().size());
            assertEquals(
                    deviceRecordStarts.subList(1, deviceRecordStarts.size()).stream()
                            .map(offset -> "0x%02X".formatted(offset))
                            .toList(),
                    result.subDevicesNet().stream()
                            .map(DeviceNet::recordStartOffset)
                            .toList());
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

    private static List<Integer> findSubArray(byte[] source, byte[] target) {
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i <= source.length - target.length; i++) {
            boolean matchesAtIndex = true;
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    matchesAtIndex = false;
                    break;
                }
            }
            if (matchesAtIndex) {
                matches.add(i);
            }
        }
        return matches;
    }
}
