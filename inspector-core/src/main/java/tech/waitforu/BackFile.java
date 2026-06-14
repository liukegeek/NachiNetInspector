package tech.waitforu;

import java.nio.file.*;
import java.util.Collections;

/**
 * ClassName: BackFileLoad
 * Package: tech.waitforu
 * Description: 通过指定路径，来加载机器人备份文件(自动识别绝对路径、相对路径以及zip压缩包，转换成一个独立的内部文件夹，并输出流)
 * Author: LiuKe
 * Create: 2026/6/7 23:15
 * Version 1.0
 */
public class BackFileLoad {
    private Path sourcePath;

    public BackFileLoad(String sourcePath) {
        if(sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath cannot be blank or null");
        }
        Path filePath = Paths.get(sourcePath);
        if(!Files.exists(filePath)) {
            throw new IllegalArgumentException("sourcePath does not exist");
        }

        this.sourcePath = filePath.toAbsolutePath();

        try(FileSystem fs = FileSystems.newFileSystem(filePath, Collections.emptyMap())) {

        }catch(Exception e) {
            throw new RuntimeException(e);
        }

    }
}
