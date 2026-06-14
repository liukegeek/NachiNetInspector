package tech.waitforu.exceptions;

/**
 * ClassName: BackupLoadException
 * Package: tech.waitforu.exceptions
 * Description:
 * Author: LiuKe
 * Create: 2026/6/8 23:18
 * Version 1.0
 */
public class BackupLoadException extends RuntimeException {
    /*
     * 加载备份文件时出错。
     *
     * @param message 用户可读的异常消息
     */
    public BackupLoadException(String message) {
        super(message);
    }

    public BackupLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
