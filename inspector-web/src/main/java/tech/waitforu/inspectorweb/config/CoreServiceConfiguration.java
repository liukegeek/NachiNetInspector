package tech.waitforu.inspectorweb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.waitforu.BackFile;
import tech.waitforu.NachiInspector;
import tech.waitforu.exceptions.BackupLoadException;
import tech.waitforu.inspectorweb.service.BackupInspectionRunner;
import tech.waitforu.service.NetworkExcelExportService;

import java.io.IOException;

@Configuration
public class CoreServiceConfiguration {

    @Bean
    public BackupInspectionRunner backupInspectionRunner() {
        return backupPath -> {
            try (BackFile backup = new BackFile(backupPath.toString())) {
                return NachiInspector.inspect(backup);
            } catch (IOException exception) {
                throw new BackupLoadException("关闭备份文件失败", exception);
            }
        };
    }

    @Bean
    public NetworkExcelExportService networkExcelExportService() {
        return new NetworkExcelExportService();
    }
}
