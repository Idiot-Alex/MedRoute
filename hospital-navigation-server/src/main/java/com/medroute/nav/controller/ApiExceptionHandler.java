package com.medroute.nav.controller;

import com.medroute.nav.navigation.algorithm.RouteUnreachableException;
import com.medroute.nav.navigation.service.DraftChangedException;
import com.medroute.nav.navigation.service.DraftValidationFailedException;
import com.medroute.nav.navigation.service.ForbiddenRouteModeException;
import com.medroute.nav.navigation.service.NavigationResourceNotFoundException;
import com.medroute.nav.navigation.service.OperationConflictException;
import com.medroute.nav.navigation.service.PoiNotInReleaseException;
import com.medroute.nav.navigation.service.ReleaseImmutableException;
import com.medroute.nav.navigation.service.ReleaseMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(
        ApiExceptionHandler.class
    );

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
        Exception error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.BAD_REQUEST,
            "INVALID_ARGUMENT",
            readableMessage(error),
            request,
            List.of()
        );
    }

    @ExceptionHandler(NavigationResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
        NavigationResourceNotFoundException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            error.getMessage(),
            request,
            List.of()
        );
    }

    @ExceptionHandler(ForbiddenRouteModeException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(
        ForbiddenRouteModeException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "当前身份无权使用员工路线。",
            request,
            List.of()
        );
    }

    @ExceptionHandler(PoiNotInReleaseException.class)
    public ResponseEntity<ApiErrorResponse> handlePoiNotInRelease(
        PoiNotInReleaseException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "POI_NOT_IN_RELEASE",
            "POI 不属于当前发布版本。",
            request,
            List.of(
                new ErrorDetail(
                    error.fieldName(),
                    "POI " + error.poiId() + " is not in the active release"
                )
            )
        );
    }

    @ExceptionHandler(RouteUnreachableException.class)
    public ResponseEntity<ApiErrorResponse> handleRouteUnreachable(
        RouteUnreachableException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "ROUTE_UNREACHABLE",
            "当前模式和设施状态下没有可用路线。",
            request,
            List.of()
        );
    }

    @ExceptionHandler(ReleaseMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleReleaseMismatch(
        ReleaseMismatchException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.CONFLICT,
            "RELEASE_MISMATCH",
            "楼栋地图已更新，请刷新后重试。",
            request,
            List.of(
                new ErrorDetail(
                    "expectedReleaseId",
                    "expected " + error.expectedReleaseId()
                        + " but active release is " + error.activeReleaseId()
                )
            )
        );
    }

    @ExceptionHandler(DraftChangedException.class)
    public ResponseEntity<ApiErrorResponse> handleDraftChanged(
        DraftChangedException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.CONFLICT,
            "DRAFT_CHANGED",
            "草稿已被其他操作修改，请刷新后重试。",
            request,
            List.of(
                new ErrorDetail(
                    "If-Match",
                    "expected revision " + error.expectedRevision()
                        + " but current revision is " + error.actualRevision()
                )
            )
        );
    }

    @ExceptionHandler(ReleaseImmutableException.class)
    public ResponseEntity<ApiErrorResponse> handleReleaseImmutable(
        ReleaseImmutableException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.CONFLICT,
            "RELEASE_IMMUTABLE",
            "已发布或已退役版本不可修改。",
            request,
            List.of(
                new ErrorDetail(
                    "releaseId",
                    "release " + error.releaseId() + " is immutable"
                )
            )
        );
    }

    @ExceptionHandler(DraftValidationFailedException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailed(
        DraftValidationFailedException error,
        HttpServletRequest request
    ) {
        List<ErrorDetail> details = error.validation().errors().stream()
            .map(issue -> new ErrorDetail(issue.elementType(), issue.message()))
            .toList();
        return response(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "VALIDATION_FAILED",
            "草稿未通过发布校验。",
            request,
            details
        );
    }

    @ExceptionHandler({
        OperationConflictException.class,
        DataIntegrityViolationException.class
    })
    public ResponseEntity<ApiErrorResponse> handleOperationConflict(
        Exception error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.CONFLICT,
            "OPERATION_CONFLICT",
            "当前数据状态无法完成该操作。",
            request,
            List.of()
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
        HttpRequestMethodNotSupportedException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.METHOD_NOT_ALLOWED,
            "METHOD_NOT_ALLOWED",
            "请求方法不受支持。",
            request,
            List.of()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
        HttpMediaTypeNotSupportedException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_MEDIA_TYPE",
            "请求媒体类型不受支持。",
            request,
            List.of()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(
        HttpMediaTypeNotAcceptableException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.NOT_ACCEPTABLE,
            "NOT_ACCEPTABLE",
            "无法生成客户端要求的响应媒体类型。",
            request,
            List.of()
        );
    }

    @ExceptionHandler({
        NoHandlerFoundException.class,
        NoResourceFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleUnknownPath(
        Exception error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            "请求的资源不存在。",
            request,
            List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
        Exception error,
        HttpServletRequest request
    ) {
        LOGGER.error(
            "Unhandled API error for {}",
            request.getRequestURI(),
            error
        );
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "服务暂时不可用，请稍后重试。",
            request,
            List.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request,
        List<ErrorDetail> details
    ) {
        Object requestAttribute = request.getAttribute(
            RequestIdFilter.REQUEST_ATTRIBUTE
        );
        String requestId = requestAttribute instanceof String value ? value : null;
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader(RequestIdFilter.HEADER_NAME);
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = "req-" + UUID.randomUUID();
        }
        ApiErrorResponse body = new ApiErrorResponse(
            new ApiError(
                code,
                message,
                requestId,
                Instant.now(),
                request.getRequestURI(),
                details
            )
        );
        return ResponseEntity.status(status).body(body);
    }

    private String readableMessage(Exception error) {
        if (error instanceof HttpMessageNotReadableException) {
            return "请求 JSON、UUID 或枚举值无效。";
        }
        return error.getMessage() == null ? "请求参数无效。" : error.getMessage();
    }

    public record ApiErrorResponse(ApiError error) {
    }

    public record ApiError(
        String code,
        String message,
        String requestId,
        Instant timestamp,
        String path,
        List<ErrorDetail> details
    ) {
    }

    public record ErrorDetail(String field, String reason) {
    }
}
