package com.itlk.myclaudecode.config;

import com.itlk.myclaudecode.agent.entity.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e, HttpServletResponse response) {
        log.error("RuntimeException: {}", e.getMessage(), e);

        // SSE endpoint: response already committed or Content-Type is text/event-stream
        // cannot write JSON body in this case
        if (response.isCommitted()) {
            log.warn("Response already committed, cannot write error body");
            return null;
        }
        String contentType = response.getContentType();
        if (contentType != null && contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            log.warn("SSE endpoint error, skipping JSON error body");
            return null;
        }

        return Result.error(e.getMessage());
    }
}
