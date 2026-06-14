package tech.waitforu;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * ClassName: NachiNetResume
 * Package: tech.waitforu
 * Description:
 * Author: LiuKe
 * Create: 2026/6/13 22:36
 * Version 1.0
 */
public record NachiNetResume(
        @JsonProperty("机器人名称")
        String robotName,
        @JsonProperty("成功解析")
        boolean isSuccess,
        @JsonProperty("机器人自身网络信息")
        DeviceNet robotSelfNet,
        @JsonProperty("子设备网络信息")
        List<DeviceNet> subDevicesNet,
        @JsonProperty("异常信息")
        List<String> exceptionMessage
) {
}
