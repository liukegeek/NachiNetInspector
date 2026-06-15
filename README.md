# Nachi Net Inspector

Nachi Net Inspector 是一个用于检查那智机器人备份目录中网络设备信息的桌面小工具。程序读取备份中的固定格式文件，解析机器人本体及子设备的名称、IP、掩码、网关和字段偏移证据，并通过网页界面展示结果。

## 功能

- 选择一个或多个机器人备份目录，重复选择会继续累加目录
- 批量解析目录，并分别展示成功、部分成功或失败结果
- 使用网络拓扑图直观展示机器人本体与子设备关系
- 使用表格和详情面板展示网络字段及 12 项解析证据
- 导出 Excel 文件，每个可用机器人对应一个 Sheet
- 启动后自动打开本地网页，仅监听 `127.0.0.1:2026`

## 使用流程

1. 点击“选择备份”，选择一个机器人备份目录；需要批量处理时可重复选择。
2. 点击“开始解析”。
3. 在左侧切换机器人，在拓扑图或设备表格中选择设备查看完整字段。
4. 点击“导出 Excel”保存本次已选择目录的可用解析结果。

## 从源码运行

环境要求：

- JDK 21
- Maven 3.9+

启动应用：

```bash
mvn -pl inspector-web -am spring-boot:run
```

运行测试：

```bash
mvn test
```

构建可执行 JAR：

```bash
mvn clean package
java -jar inspector-web/target/inspector-web-1.0-SNAPSHOT.jar
```

## 桌面发布

GitHub Actions 会在 Windows 和 macOS 上使用 `jpackage` 构建自带 Java 运行时的桌面应用压缩包。推送 `v*` 标签时，构建结果会自动附加到 GitHub Release。

应用运行日志保存在：

```text
~/.NachiNetInspector/logs/
```

## 项目结构

```text
NachiNetInspector/
├── inspector-core/    # 备份目录读取、网络信息解析、Excel 导出
├── inspector-web/     # 桌面启动入口、本地 API、网页界面
├── icons/             # Windows/macOS 打包图标
└── .github/workflows/ # 跨平台发布流程
```
