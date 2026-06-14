package tech.waitforu.inspectorweb.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.waitforu.inspectorweb.model.InspectionBatchResponse;
import tech.waitforu.inspectorweb.service.InspectionExecutionService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/inspection")
public class InspectionController {
    private static final String EXCEL_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String EXCEL_FILENAME = "Nachi网络信息.xlsx";

    private final InspectionExecutionService executionService;

    public InspectionController(InspectionExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InspectionBatchResponse inspect(@RequestParam("files") List<MultipartFile> files) {
        return executionService.inspect(files);
    }

    @PostMapping(
            path = "/excel",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = EXCEL_MEDIA_TYPE)
    public ResponseEntity<byte[]> exportExcel(@RequestParam("files") List<MultipartFile> files) {
        String encodedFilename = URLEncoder.encode(EXCEL_FILENAME, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EXCEL_MEDIA_TYPE))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .body(executionService.exportExcel(files));
    }
}
