package tech.waitforu.inspectorweb.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tech.waitforu.exceptions.BackupLoadException;
import tech.waitforu.exceptions.ExcelExportException;
import tech.waitforu.inspectorweb.exception.ApiException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_SERVER_ERROR = "服务器内部错误，请查看日志";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        HttpStatus status = exception.getStatus();
        LOGGER.warn("API request failed with status {}: {}", status.value(), exception.getMessage());
        return response(status, exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, BackupLoadException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException exception) {
        LOGGER.warn("Bad request: {}", exception.getMessage());
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ExcelExportException.class)
    public ResponseEntity<ErrorResponse> handleExcelExportException(ExcelExportException exception) {
        LOGGER.error("Excel export failed", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        LOGGER.error("Unexpected API error", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
    }

    private static ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), message));
    }
}
