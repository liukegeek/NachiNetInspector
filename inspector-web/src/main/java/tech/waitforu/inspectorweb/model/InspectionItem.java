package tech.waitforu.inspectorweb.model;

import tech.waitforu.NachiNetResume;

public record InspectionItem(
        String sourceFileName,
        InspectionStatus status,
        NachiNetResume result,
        String errorMessage
) {
    public boolean hasUsableData() {
        if (result == null) {
            return false;
        }
        if (result.robotSelfNet() != null) {
            return true;
        }
        return result.subDevicesNet() != null
                && result.subDevicesNet().stream().anyMatch(device -> device != null);
    }
}
