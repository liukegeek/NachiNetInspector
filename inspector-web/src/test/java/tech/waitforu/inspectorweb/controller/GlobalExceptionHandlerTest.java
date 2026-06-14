package tech.waitforu.inspectorweb.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.waitforu.exceptions.BackupLoadException;
import tech.waitforu.exceptions.ExcelExportException;
import tech.waitforu.inspectorweb.exception.ApiException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsApiExceptionUsingSpecifiedStatusAndMessage() throws Exception {
        mockMvc.perform(get("/throw/api"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("specified API failure"));
    }

    @Test
    void mapsIllegalArgumentExceptionToBadRequest() throws Exception {
        assertBadRequest("/throw/illegal", "invalid argument");
    }

    @Test
    void mapsBackupLoadExceptionToBadRequest() throws Exception {
        assertBadRequest("/throw/backup", "invalid backup");
    }

    @Test
    void mapsExcelExportExceptionToInternalServerError() throws Exception {
        mockMvc.perform(get("/throw/excel"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("export failed"));
    }

    @Test
    void mapsUnknownExceptionWithoutLeakingItsMessage() throws Exception {
        mockMvc.perform(get("/throw/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误，请查看日志"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    private void assertBadRequest(String path, String message) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(message));
    }

    @RestController
    private static class ThrowingController {

        @GetMapping("/throw/api")
        void api() {
            throw new ApiException(HttpStatus.CONFLICT, "specified API failure");
        }

        @GetMapping("/throw/illegal")
        void illegal() {
            throw new IllegalArgumentException("invalid argument");
        }

        @GetMapping("/throw/backup")
        void backup() {
            throw new BackupLoadException("invalid backup");
        }

        @GetMapping("/throw/excel")
        void excel() {
            throw new ExcelExportException("export failed");
        }

        @GetMapping("/throw/unknown")
        void unknown() {
            throw new IllegalStateException("secret database credential");
        }
    }
}
