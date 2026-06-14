package tech.waitforu.exceptions;

/**
 * ClassName: InspectorException
 * Package: tech.waitforu.exceptions
 * Description:
 * Author: LiuKe
 * Create: 2026/6/13 23:07
 * Version 1.0
 */
public class InspectorException extends RuntimeException {
    /**
     * 从BackFile文件中解析网络信息时发生的异常。
     * @param message 异常消息
     */
    public InspectorException(String message) {
        super(message);
    }
}
