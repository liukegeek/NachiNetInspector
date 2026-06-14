package tech.waitforu.inspectorweb.service;

import tech.waitforu.NachiNetResume;

import java.nio.file.Path;

@FunctionalInterface
public interface BackupInspectionRunner {
    NachiNetResume inspect(Path backupPath);
}
