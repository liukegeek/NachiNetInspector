package tech.waitforu;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * ClassName: BackFile
 * Package: tech.waitforu
 * Description: 通过指定路径，来加载机器人备份文件(自动识别绝对路径、相对路径以及zip压缩包，转换成一个独立的内部文件夹，并输出流)
 * Author: LiuKe
 * Create: 2026/6/7 23:15
 * Version 1.0
 */
public class BackFile implements AutoCloseable {
    private Path sourcePath; // 备份文件的原始路径
    private FileSystem fileSystem; // 备份文件获取的文件系统对象
    private Path rootPath; // 进行解析时的根路径。
    private boolean needCloseFileSystem;


    public BackFile(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("输入的文件路径不能为空或null");
        }
        this.sourcePath = Paths.get(sourcePath).toAbsolutePath().normalize();
        if (!Files.exists(this.sourcePath)) {
            throw new IllegalArgumentException("输入的文件路径不存在");
        }

        try {
            if (Files.isDirectory(this.sourcePath)) {
                // 如果是目录,则直接获取当前文件的文件系统对象。
                fileSystem = this.sourcePath.getFileSystem();
                rootPath = this.sourcePath;
                needCloseFileSystem = false;
            } else {
                // 如果是文件,则直接创建文件系统
                fileSystem = FileSystems.newFileSystem(this.sourcePath);
                rootPath = fileSystem.getPath("/");
                needCloseFileSystem = true;
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 解析备份文件中指定路径，将其转换为相对于备份目录的路径。
     *
     * @param pathString 要解析的路径字符串
     * @return 解析后的路径
     * @throws InvalidPathException 如果路径字符串无效,则抛出 InvalidPathException
     */
    private Path resolvePath(String pathString) {
        Path input = fileSystem.getPath(pathString);

        // 如果输入的是绝对路径，则去掉其前面的"/"或者“\\”，将路径改写成一个相对路径的形式。
        if (input.isAbsolute()) {
            input = input.getRoot().relativize(input);
        }

        // 通过resolve，获得pathString相对于rootPath的路径。
        Path resolved = rootPath.resolve(input).normalize();

        // 禁止使用 ../../ 跳出备份目录
        if (!resolved.startsWith(rootPath)) {
            throw new InvalidPathException(pathString, "路径超出备份根目录");
        }

        return resolved;
    }

    /**
     * 获取备份文件的原始路径。
     *
     * @return 备份文件的原始路径
     */
    public Path getSourcePath() {
        return sourcePath;
    }

    public String getSourcePathAsString() {
        return sourcePath.toString();
    }

    /**
     * 获取备份文件中指定路径，相对于当前操作系统中的路径表示。
     *
     * @param pathString 备份文件所构成的文件系统中，要获取真实路径的文件路径字符串
     * @return 相对于当前操作系统中的路径表示
     * @throws InvalidPathException 如果路径字符串无效,则抛出 InvalidPathException
     */
    public String getPathInOs(String pathString) throws InvalidPathException {
        Path path = fileSystem.getPath(pathString);
        return Paths.get(sourcePath.toString(), path.toAbsolutePath().toString()).toString();
    }

    /**
     * 打开指定路径的文件字节数组。
     *
     * @param pathString 要打开的文件路径字符串
     * @return 文件字节数组
     */
    public byte[] readBytes(String pathString) throws InvalidPathException {
        Path path = resolvePath(pathString);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 读取指定路径的文件内容，转换为字符串。
     *
     * @param pathString 要读取的文件路径字符串
     * @return 文件内容的字符串表示
     * @throws RuntimeException 如果读取文件时发生错误
     */
    public String readStringByUTF8(String pathString) throws InvalidPathException {
        Path path = resolvePath(pathString);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 关闭备份文件流。
     *
     * @throws IOException 如果关闭文件系统时发生错误,则抛出 IOException
     */
    @Override
    public void close() throws IOException {
        if (needCloseFileSystem && fileSystem != null) {
            fileSystem.close();
        }
    }
}
