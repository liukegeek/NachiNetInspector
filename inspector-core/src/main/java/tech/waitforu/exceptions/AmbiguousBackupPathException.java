package tech.waitforu.exceptions;

/**
 * ClassName: AmbiguousBackupPathException
 * Package: tech.waitforu.exceptions
 * Description:
 * Author: LiuKe
 * Create: 2026/6/8 23:19
 * Version 1.0
 */
public class AmbiguousBackupPathException extends RuntimeException {
    /**
     * 备份路径不唯一时抛出的异常。
     *
     * @param message 用户可读的异常消息
     */
    public AmbiguousBackupPathException(String message) {
        super(message);
    }
}
