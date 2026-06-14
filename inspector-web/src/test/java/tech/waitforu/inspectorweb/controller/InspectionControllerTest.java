package tech.waitforu.inspectorweb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tech.waitforu.inspectorweb.model.InspectionBatchResponse;
import tech.waitforu.inspectorweb.model.InspectionItem;
import tech.waitforu.inspectorweb.model.InspectionStatus;
import tech.waitforu.inspectorweb.service.InspectionExecutionService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InspectionControllerTest {
    private static final MediaType EXCEL_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void inspectionMultipartReturnsBatchCountsAndItems() throws Exception {
        InspectionExecutionService service = mock(InspectionExecutionService.class);
        InspectionBatchResponse response = new InspectionBatchResponse(
                List.of(new InspectionItem("robot.backup", InspectionStatus.FAILED, null, "bad backup")),
                0,
                0,
                1);
        when(service.inspect(anyList())).thenReturn(response);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new InspectionController(service)).build();

        mockMvc.perform(multipart("/api/inspection")
                        .file(upload("robot.backup", "backup")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successfulCount").value(0))
                .andExpect(jsonPath("$.partialCount").value(0))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.items[0].sourceFileName").value("robot.backup"))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].errorMessage").value("bad backup"));

        verify(service).inspect(anyList());
    }

    @Test
    void excelMultipartReturnsBytesAndUtf8AttachmentHeader() throws Exception {
        InspectionExecutionService service = mock(InspectionExecutionService.class);
        byte[] excel = new byte[]{1, 2, 3, 4};
        when(service.exportExcel(anyList())).thenReturn(excel);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new InspectionController(service)).build();
        String encodedFilename = URLEncoder.encode("Nachi网络信息.xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");

        mockMvc.perform(multipart("/api/inspection/excel")
                        .file(upload("robot.backup", "backup")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXCEL_MEDIA_TYPE))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename))
                .andExpect(content().bytes(excel));

        verify(service).exportExcel(anyList());
    }

    private static MockMultipartFile upload(String filename, String contents) {
        return new MockMultipartFile(
                "files",
                filename,
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                contents.getBytes(StandardCharsets.UTF_8));
    }
}
