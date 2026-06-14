package tech.waitforu;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ClassName: DeviceNet
 * Package: tech.waitforu
 * Description:
 * Author: LiuKe
 * Create: 2026/6/13 18:43
 * Version 1.0
 */
public record DeviceNet(
        @JsonProperty("名称")
        String deviceName,
        @JsonProperty("IP")
        String deviceIP,
        @JsonProperty("掩码")
        String deviceMask,
        @JsonProperty("网关")
        String deviceGateway,
        @JsonProperty("来源文件")
        String sourceFile,
        @JsonProperty("记录头")
        String recordHeader,
        @JsonProperty("记录起始偏移量")
        String recordStartOffset,
        @JsonProperty("名称长度偏移量")
        String name_length_offset,
        @JsonProperty("名称偏移量")
        String name_offset,
        @JsonProperty("IP偏移量")
        String ipOffset,
        @JsonProperty("掩码偏移量")
        String maskOffset,
        @JsonProperty("网关偏移量")
        String gatewayOffset

) {
}
