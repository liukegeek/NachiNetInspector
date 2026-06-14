package tech.waitforu;

import tech.waitforu.exceptions.InspectorException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * ClassName: NachiInspector
 * Package: tech.waitforu
 * Description:
 * Author: LiuKe
 * Create: 2026/6/13 18:14
 * Version 1.0
 */
public class NachiInspector {


    // 机器人本体 网络信息 所存储的文件路径
    public static final String ROBOT_NET_FILE = "PLCEngine/nwid1.nxd";
    // 机器人子设备网络信息 所存储的文件路径
    public static final String SUB_DEVICE_NET_FILE = "PLCEngine/config1.nxd";

    // NWID文件中 网络信息 特征记录头 的值
    public static final byte[] NWID_RECORD_HEADER = new byte[]{
            (byte) 0x17, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x78, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };
    // NWID文件中 IP信息 相对记录头起点 的偏移量
    public static final int NWID_IP_REL = 0x16;
    // NWID文件中 子网掩码信息 相对记录头起点 的偏移量
    public static final int NWID_MASK_REL = 0x1A;
    // NWID文件中 网关信息 相对记录头起点 的偏移量
    public static final int NWID_GATEWAY_REL = 0x1E;


    // CONFIG文件中 网络信息 特征记录头 的值
    public static final byte[] CONFIG_DEVICE_RECORD_HEADER = new byte[]{
            (byte) 0x17, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x50, (byte) 0x01, (byte) 0x00, (byte) 0x00
    };
    // CONFIG文件中 设备名称长度信息 相对记录头起点 的偏移量
    public static final int CONFIG_DEVICE_NAME_LENGTH_REL = 0x14;
    // CONFIG文件中 设备名称信息 相对记录头起点 的偏移量
    public static final int CONFIG_DEVICE_NAME_REL = 0x16;
    // CONFIG文件中 IP信息 相对记录头起点 的偏移量
    public static final int CONFIG_DEVICE_IP_REL = 0x10E;
    // CONFIG文件中 子网掩码信息 相对记录头起点 的偏移量
    public static final int CONFIG_DEVICE_MASK_REL = 0x112;
    // CONFIG文件中 网关信息 相对记录头起点 的偏移量
    public static final int CONFIG_DEVICE_GATEWAY_REL = 0x116;


    public static final byte[] CONFIG_ROBOT_SELF_RECORD_HEADER = new byte[]{
            (byte) 0x17, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x30, (byte) 0x02, (byte) 0x00, (byte) 0x00
    };
    // CONFIG文件中 机器人本体的名称信息，也保存在这里。其偏移量对应以下变量。
    public static final int CONFIG_ROBOT_SELF_NAME_LENGTH_REL = 0x102;
    public static final int CONFIG_ROBOT_SELF_NAME_REL = 0x104;


    // NWID文件中 网络信息 最小记录大小
    public static final int MIN_NWID_RECORD_SIZE = NWID_GATEWAY_REL + 4;
    // CONFIG文件中 网络信息 最小记录大小
    public static final int MIN_DEVICE_RECORD_SIZE = CONFIG_DEVICE_GATEWAY_REL + 4;


    public static NachiNetResume inspect(BackFile nachiBackFile) {
        String robotSelfName = "";
        boolean isSuccess = false;
        DeviceNet robotSelfNet = null;
        List<DeviceNet> subDevicesNet = new ArrayList<>();
        List<String> exceptionMessage = new ArrayList<>();


        // 解析机器人本体网络信息
        try {
            //读取机器人本体网络信息文件
            byte[] robotNetFile = nachiBackFile.readBytes(ROBOT_NET_FILE);

            //查找机器人本体网络信息记录表示的 位置索引
            List<Integer> robotNetMatchIndex = findSubArray(robotNetFile, NWID_RECORD_HEADER);

            // 机器人本体网络信息记录头必须唯一，不能存在多个，否则无法解析网络信息
            if (robotNetMatchIndex.size() != 1) {
                throw new InspectorException("机器人本体中，网络信息记录头不唯一，或不存在，故而无法解析");
            }
            //获取机器人本体网络信息记录头起始位置
            int robotNetRecordStart = robotNetMatchIndex.getFirst();
            if (robotNetRecordStart + MIN_NWID_RECORD_SIZE > robotNetFile.length) {
                throw new InspectorException("机器人本体中，网络信息记录头后，后续数据长度不足，故而无法解析");
            }

            //借助ByteBuffer，来完成LittleEndian的相关数据读取。
            ByteBuffer robotNetByte = ByteBuffer.wrap(robotNetFile).order(ByteOrder.LITTLE_ENDIAN);
            //解析机器人本体IP
            int robotNetIPOffset = robotNetRecordStart + NWID_IP_REL;
            int robotNetIPInt = robotNetByte.position(robotNetIPOffset).getInt();
            String robotNetIP = intToIpv4(robotNetIPInt);

            //解析机器人本地子网掩码
            int robotNetMaskIPOffset = robotNetRecordStart + NWID_MASK_REL;
            int robotNetMaskIPInt = robotNetByte.position(robotNetMaskIPOffset).getInt();
            String robotNetMaskIP = intToIpv4(robotNetMaskIPInt);

            //解析机器人本地网关
            int robotNetGatewayOffset = robotNetRecordStart + NWID_GATEWAY_REL;
            int robotNetGatewayIPInt = robotNetByte.position(robotNetGatewayOffset).getInt();
            String robotNetGatewayIP = intToIpv4(robotNetGatewayIPInt);


            robotSelfNet = new DeviceNet(
                    "机器人本体",
                    robotNetIP,
                    robotNetMaskIP,
                    robotNetGatewayIP,
                    ROBOT_NET_FILE,
                    toHexStr(NWID_RECORD_HEADER),
                    toHexStr(robotNetRecordStart),
                    "",
                    "",
                    toHexStr(robotNetIPOffset),
                    toHexStr(robotNetMaskIPOffset),
                    toHexStr(robotNetGatewayOffset));

        } catch (InspectorException | InvalidParameterException e) {
            exceptionMessage.add(e.getMessage());
        } catch (InvalidPathException e) {
            String message = "路径错误：" + e.getMessage();
            exceptionMessage.add(message);
        }

        // 解析子设备网络信息
        try {
            byte[] subNetFile = nachiBackFile.readBytes(SUB_DEVICE_NET_FILE);

            //查找子设备网络信息记录表示的 位置索引
            List<Integer> subNetMatchIndex = findSubArray(subNetFile, CONFIG_DEVICE_RECORD_HEADER);

            //借助ByteBuffer，来完成LittleEndian的相关数据读取。
            ByteBuffer subNetByte = ByteBuffer.wrap(subNetFile).order(ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < subNetMatchIndex.size(); i++) {
                int subNetRecordStart = subNetMatchIndex.get(i);
                if (subNetRecordStart + MIN_DEVICE_RECORD_SIZE > subNetFile.length) {
                    exceptionMessage.add("机器人下该设备" + (i + 1) + "中，网络信息记录头的，后续数据长度不足，故而无法解析该设备的网络信息");
                    continue;
                }

                // 解析子设备名称长度
                int subNetNameLengthOffset = subNetRecordStart + CONFIG_DEVICE_NAME_LENGTH_REL;
                int subNetNameLength = subNetByte.position(subNetNameLengthOffset).getShort();

                // 解析子设备名称
                int subNetNameOffset = subNetRecordStart + CONFIG_DEVICE_NAME_REL;
                byte[] subNetNameBytes = new byte[subNetNameLength];
                subNetByte.position(subNetNameOffset).get(subNetNameBytes);
                String subNetName = new String(subNetNameBytes, StandardCharsets.UTF_8);

                // 解析子设备IP
                int subNetIPOffset = subNetRecordStart + CONFIG_DEVICE_IP_REL;
                int subNetIPInt = subNetByte.position(subNetIPOffset).getInt();
                String subNetIP = intToIpv4(subNetIPInt);

                // 解析子设备子网掩码
                int subNetMaskIPOffset = subNetRecordStart + CONFIG_DEVICE_MASK_REL;
                int subNetMaskIPInt = subNetByte.position(subNetMaskIPOffset).getInt();
                String subNetMaskIP = intToIpv4(subNetMaskIPInt);

                // 解析子设备网关
                int subNetGatewayOffset = subNetRecordStart + CONFIG_DEVICE_GATEWAY_REL;
                int subNetGatewayIPInt = subNetByte.position(subNetGatewayOffset).getInt();
                String subNetGatewayIP = intToIpv4(subNetGatewayIPInt);

                subDevicesNet.add(new DeviceNet(
                        subNetName,
                        subNetIP,
                        subNetMaskIP,
                        subNetGatewayIP,
                        SUB_DEVICE_NET_FILE,
                        toHexStr(CONFIG_DEVICE_RECORD_HEADER),
                        toHexStr(subNetRecordStart),
                        toHexStr(subNetNameLengthOffset),
                        toHexStr(subNetNameOffset),
                        toHexStr(subNetIPOffset),
                        toHexStr(subNetMaskIPOffset),
                        toHexStr(subNetGatewayOffset)
                ));
            }

        } catch (InspectorException | InvalidParameterException e) {
            exceptionMessage.add(e.getMessage());
        } catch (InvalidPathException e) {
            String message = "路径错误：" + e.getMessage();
            exceptionMessage.add(message);
        }


        /* 解析机器人本体名称。
         机器人本体的名称字段没有同网络信息一样，存储在"NXID"文件当中，而是跟下挂设备一起存储在Config文件里。
         故而，本体名称需要单独解析。
         */
        try {
            byte[] configFile = nachiBackFile.readBytes(SUB_DEVICE_NET_FILE);
            //查找机器人本体名字信息记录表示的 位置索引
            List<Integer> robotSelfNameMatchIndex = findSubArray(configFile, CONFIG_ROBOT_SELF_RECORD_HEADER);

            // 机器人本体网络信息记录头必须唯一，不能存在多个，否则无法解析网络信息
            if (robotSelfNameMatchIndex.size() != 1) {
                throw new InspectorException("机器人本体中，名字信息记录头不唯一，或不存在，故而无法解析");
            }
            //获取机器人本体名字信息记录头起始位置
            int robotSelfNameRecordStart = robotSelfNameMatchIndex.getFirst();
            if (robotSelfNameRecordStart + CONFIG_ROBOT_SELF_NAME_REL > configFile.length) {
                throw new InspectorException("机器人本体中，名字信息记录头后，后续数据长度不足，故而无法解析");
            }

            ByteBuffer configFileBytes = ByteBuffer.wrap(configFile).order(ByteOrder.LITTLE_ENDIAN);

            // 解析机器人本体名称长度
            int robotSelfNameLengthOffset = robotSelfNameRecordStart + CONFIG_ROBOT_SELF_NAME_LENGTH_REL;
            int robotSelfNameLength = configFileBytes.position(robotSelfNameLengthOffset).getShort();
            // 解析机器人本体名称
            int robotSelfNameOffset = robotSelfNameRecordStart + CONFIG_ROBOT_SELF_NAME_REL;
            byte[] robotSelfNameBytes = new byte[robotSelfNameLength];
            configFileBytes.position(robotSelfNameOffset).get(robotSelfNameBytes);

            // 机器人本体的名称。
            robotSelfName = new String(robotSelfNameBytes, StandardCharsets.UTF_8);
        } catch (InspectorException | InvalidParameterException e) {
            exceptionMessage.add(e.getMessage());
        } catch (InvalidPathException e) {
            String message = "路径错误：" + e.getMessage();
            exceptionMessage.add(message);
        }

        // 如果没有异常，说明解析机器人成功，否则失败。
        isSuccess = exceptionMessage.isEmpty();


        //解析完成，返回解析结果
        return new NachiNetResume(
                robotSelfName,
                isSuccess,
                robotSelfNet,
                subDevicesNet,
                exceptionMessage);
    }

    private static List<Integer> findSubArray(byte[] source, byte[] target) {
        List<Integer> matchList = new ArrayList<>();

        if (target.length == 0 || source.length < target.length) {
            return matchList;
        }

        for (int i = 0; i < source.length - target.length + 1; i++) {
            boolean matchFlag = true;
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    matchFlag = false;
                    break;
                }
            }
            if (matchFlag) {
                matchList.add(i);
            }
        }
        return matchList;
    }

    private static String intToIpv4(int ipInt) throws InspectorException {
        try {
            byte[] ipBytes = new byte[4];
            ipBytes[0] = (byte) ((ipInt >> 24) & 0xFF);
            ipBytes[1] = (byte) ((ipInt >> 16) & 0xFF);
            ipBytes[2] = (byte) ((ipInt >> 8) & 0xFF);
            ipBytes[3] = (byte) ((ipInt) & 0xFF);

            return InetAddress.getByAddress(ipBytes).getHostAddress();
        } catch (UnknownHostException e) {
            throw new InspectorException("将整数转换为IP地址时出现错误，无法成功解析为IP地址");
        }
    }

    /**
     * 将整数转换为十六进制字符串。
     *
     * @param value 待转换的整数
     * @return 十六进制字符串，前缀为"0x"
     */
    private static String toHexStr(int value) {
        return "0x%02X".formatted(value);
    }

    private static String toHexStr(byte[] bytes) {
        List<String> hexList = new ArrayList<>();
        for (byte b : bytes) {
            hexList.add("0x%02X".formatted(b & 0xFF));
        }
        return String.join(" ", hexList);
    }

}
