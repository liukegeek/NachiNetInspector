package tech.waitforu.inspectorweb.model;

import java.util.List;
import java.util.Objects;

public record InspectionBatchResponse(
        List<InspectionItem> items,
        long successfulCount,
        long partialCount,
        long failedCount
) {
    public InspectionBatchResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static InspectionBatchResponse from(List<InspectionItem> items) {
        List<InspectionItem> safeItems = items == null
                ? List.of()
                : items.stream().filter(Objects::nonNull).toList();
        return new InspectionBatchResponse(
                safeItems,
                count(safeItems, InspectionStatus.SUCCESS),
                count(safeItems, InspectionStatus.PARTIAL),
                count(safeItems, InspectionStatus.FAILED));
    }

    private static long count(List<InspectionItem> items, InspectionStatus status) {
        return items.stream().filter(item -> item.status() == status).count();
    }
}
