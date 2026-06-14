package tech.waitforu.inspectorweb.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import tech.waitforu.exceptions.BackupLoadException;
import tech.waitforu.exceptions.ExcelExportException;
import tech.waitforu.inspectorweb.exception.ApiException;
import tech.waitforu.inspectorweb.service.InspectionExecutionService;
import tech.waitforu.service.NetworkExcelExportService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {
    private MockMvc mockMvc;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private boolean additive;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        additive = logger.isAdditive();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);

        InspectionExecutionService service = new InspectionExecutionService(
                path -> {
                    throw new AssertionError("binding errors must not call the execution service");
                },
                new NetworkExcelExportService());
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ThrowingController(),
                        new InspectionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        logger.setAdditive(additive);
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void mapsApiExceptionUsingSpecifiedStatusAndMessage() throws Exception {
        mockMvc.perform(get("/throw/api"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("specified API failure"));

        ILoggingEvent warning = eventAt(Level.WARN);
        assertNull(warning.getThrowableProxy());
        assertFalse(hasEventAt(Level.ERROR));
    }

    @Test
    void mapsServerApiExceptionAndLogsItsCauseAtError() throws Exception {
        mockMvc.perform(get("/throw/api-server"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("public server failure"));

        ILoggingEvent error = eventAt(Level.ERROR);
        assertNotNull(error.getThrowableProxy());
        assertNotNull(error.getThrowableProxy().getCause());
        assertEquals("root cause detail", error.getThrowableProxy().getCause().getMessage());
    }

    @Test
    void inheritedSpringMvcServerErrorUsesFixedMessageAndLogsException() throws Exception {
        String internalMessage = "secret serializer implementation detail";
        HttpMessageNotWritableException exception =
                new HttpMessageNotWritableException(internalMessage);

        ResponseEntity<Object> response = new GlobalExceptionHandler().handleException(
                exception,
                new ServletWebRequest(new MockHttpServletRequest()));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse errorResponse = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("服务器内部错误，请查看日志", errorResponse.message());
        assertFalse(errorResponse.message().contains(internalMessage));
        ILoggingEvent error = eventAt(Level.ERROR);
        assertNotNull(error.getThrowableProxy());
        assertEquals(internalMessage, error.getThrowableProxy().getMessage());
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

    @Test
    void missingMultipartFilesRemainsStructuredBadRequest() throws Exception {
        assertStandardClientError(multipart("/api/inspection"), 400);
    }

    @Test
    void jsonContentTypeRemainsStructuredUnsupportedMediaType() throws Exception {
        assertStandardClientError(
                post("/api/inspection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                415);
    }

    @Test
    void unsupportedMethodRemainsStructuredMethodNotAllowed() throws Exception {
        assertStandardClientError(get("/api/inspection"), 405);
    }

    @Test
    void unknownPathRemainsStructuredNotFound() throws Exception {
        assertStandardClientError(get("/api/not-found"), 404);
    }

    private void assertBadRequest(String path, String message) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(message));
    }

    private void assertStandardClientError(RequestBuilder request, int statusCode) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.message").isNotEmpty());

        ILoggingEvent warning = eventAt(Level.WARN);
        assertNull(warning.getThrowableProxy());
        assertFalse(hasEventAt(Level.ERROR));
    }

    private ILoggingEvent eventAt(Level level) {
        ILoggingEvent event = appender.list.stream()
                .filter(candidate -> candidate.getLevel() == level)
                .findFirst()
                .orElse(null);
        assertNotNull(event);
        return event;
    }

    private boolean hasEventAt(Level level) {
        return appender.list.stream().anyMatch(event -> event.getLevel() == level);
    }

    @RestController
    private static class ThrowingController {

        @GetMapping("/throw/api")
        void api() {
            throw new ApiException(HttpStatus.CONFLICT, "specified API failure");
        }

        @GetMapping("/throw/api-server")
        void apiServer() {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "public server failure",
                    new IllegalStateException("root cause detail"));
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
