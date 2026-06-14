package tech.waitforu.exceptions;

/**
 * ClassName: BackupSourceClosedException
 * Package: tech.waitforu.exceptions
 * Description:
 * Author: LiuKe
 * Create: 2026/6/8 23:19
 * Version 1.0
 */
public class BackupSourceClosedException extends RuntimeException {
    /**
     * 备份源已关闭时抛出的异常。
     *
     * @param message 用户可读的异常消息
     */
    public BackupSourceClosedException(String message) {
        super(message);
    }
}
