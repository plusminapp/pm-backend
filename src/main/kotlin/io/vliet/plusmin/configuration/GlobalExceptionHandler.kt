package io.vliet.plusmin.configuration

import io.vliet.plusmin.domain.ErrorResponse
import jakarta.validation.ConstraintViolationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.LocalDateTime

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger: Logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(io.vliet.plusmin.domain.PlusMinException::class)
    fun handlePlusMinException(
        ex: io.vliet.plusmin.domain.PlusMinException,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("PlusMin exception: ${ex.errorCode} - ${ex.message}")

        return ResponseEntity
            .status(ex.httpStatus)
            .body(
                ErrorResponse(
                    ex.errorCode,
                    ex.message,
                    ex.parameters,
                    request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(
        ex: IllegalStateException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val stackTraceElement = ex.stackTrace.firstOrNull { it.className.startsWith("io.vliet") }
            ?: ex.stackTrace.firstOrNull()
        val locationInfo = stackTraceElement?.let { " (${it.fileName}:${it.lineNumber})" } ?: ""
        val errorMessage = "${ex.message}$locationInfo"

        logger.error("IllegalStateException: $errorMessage", ex)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("BUSINESS_RULE_VIOLATION", errorMessage))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val stackTraceElement = ex.stackTrace.firstOrNull { it.className.startsWith("io.vliet") }
            ?: ex.stackTrace.firstOrNull()
        val locationInfo = stackTraceElement?.let { " (${it.fileName}:${it.lineNumber})" } ?: ""
        val errorMessage = "${ex.message}$locationInfo"
        val parts = ex.message?.split(".")
        val parameters = parts?.size?.let { if (it >= 2) parts.takeLast(2) else emptyList() } ?: emptyList()

        logger.error("IllegalArgumentException: $errorMessage, illegalArgument: $parameters")

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    "ILLEGAL_ARGUMENT", errorMessage, parameters = parameters, path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ValidationErrorResponse> {
        val errors = mutableMapOf<String, String>()
        ex.bindingResult.allErrors.forEach { error ->
            val fieldName = (error as FieldError).field
            val errorMessage = error.getDefaultMessage() ?: "Invalid value"
            errors[fieldName] = errorMessage
        }

        logger.warn("Validation failed: $errors")

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ValidationErrorResponse("VALIDATION_FAILED", "Validation errors occurred", errors))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(
        ex: ConstraintViolationException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val message = ex.constraintViolations.joinToString(", ") { it.message }
        logger.warn("Constraint violation: $message")

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("CONSTRAINT_VIOLATION", message))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(
        ex: MethodArgumentTypeMismatchException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val message = "Parameter '${ex.name}' should be of type ${ex.requiredType?.simpleName}"
        logger.warn("Type mismatch: $message")

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("TYPE_MISMATCH", message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(
        ex: HttpMessageNotReadableException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Malformed JSON request: ${ex.message}")

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("MALFORMED_JSON", "Request body contains invalid JSON"))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(
        ex: AccessDeniedException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Access denied: ${ex.message}")

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(
                ErrorResponse(
                    "ACCESS_DENIED",
                    ex.message ?: "Access is denied",
                    path = request.userPrincipal?.name
                )
            )
    }

    @ExceptionHandler(io.vliet.plusmin.domain.ResourceNotFoundException::class)
    fun handleResourceNotFoundException(
        ex: io.vliet.plusmin.domain.ResourceNotFoundException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Resource not found: ${ex.resourceType} with id ${ex.resourceId}")

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.code, ex.message, path = request.getDescription(false)))
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(
        ex: RuntimeException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected runtime exception: ${ex.message}", ex)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected exception: ${ex.message}", ex)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"))
    }

    data class ValidationErrorResponse(
        val errorCode: String,
        val errorMessage: String,
        val fieldErrors: Map<String, String>,
        val timestamp: LocalDateTime = LocalDateTime.now()
    )
}