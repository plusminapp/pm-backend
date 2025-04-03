//package io.vliet.plusmin.controller
//
//import jakarta.servlet.http.HttpServletRequest
//import org.slf4j.Logger
//import org.slf4j.LoggerFactory
//import org.springframework.http.HttpStatus
//import org.springframework.http.ResponseEntity
//import org.springframework.web.bind.annotation.ExceptionHandler
//import org.springframework.web.bind.annotation.ControllerAdvice
//
//
//@ControllerAdvice
//class RestExceptionHandler {
//
//    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)
//
//    @ExceptionHandler(AccessDeniedException::class)
//    fun handleAccessDeniedException(
//        ex: AccessDeniedException,
//        request: HttpServletRequest
//    ): ResponseEntity<String> {
//        if (request.requestURI.startsWith("/swagger-ui") ||
//            request.requestURI.startsWith("/api/v1/swagger-ui") ||
//            request.requestURI.startsWith("/v1/swagger-ui") ||
//            request.requestURI.startsWith("/api/v1/v3/api-docs")) {
//            return ResponseEntity.ok("Swagger UI access")
//        }
//        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied")
//    }
//
//    @ExceptionHandler(IllegalStateException::class)
//    fun handleIllegalStateException(ex: IllegalStateException): ResponseEntity<String> {
//        val stackTraceElement = ex.stackTrace.firstOrNull { it.className.startsWith("io.vliet") }
//            ?: ex.stackTrace.firstOrNull()
//        val locationInfo = stackTraceElement?.let { " (${it.fileName}:${it.lineNumber})" } ?: ""
//        val errorMessage = "${ex.message}$locationInfo"
//        logger.error(errorMessage)
//        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage)
//    }
//}


//import org.springframework.http.HttpHeaders
//import org.springframework.http.HttpStatus
//import org.springframework.http.ResponseEntity
//import org.springframework.web.bind.annotation.ControllerAdvice
//import org.springframework.web.bind.annotation.ExceptionHandler
//import org.springframework.web.context.request.WebRequest
//import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
//
//@ControllerAdvice
//class RestResponseEntityExceptionHandler : ResponseEntityExceptionHandler() {
//
//    @ExceptionHandler(value = [IllegalArgumentException::class, IllegalStateException::class])
//    protected fun handleConflict(ex: RuntimeException, request: WebRequest): ResponseEntity<Any>? {
//        val bodyOfResponse = "This should be application specific"
//        return handleExceptionInternal(ex, bodyOfResponse, HttpHeaders(), HttpStatus.CONFLICT, request)
//    }
//}


//
//import org.slf4j.Logger
//import org.slf4j.LoggerFactory
//import org.springframework.http.HttpStatus
//import org.springframework.http.ResponseEntity
//import org.springframework.web.bind.annotation.ControllerAdvice
//import org.springframework.web.bind.annotation.ExceptionHandler
//import org.springframework.web.bind.*
//import org.springframework.dao.DataIntegrityViolationException
//import org.springframework.dao.DataRetrievalFailureException
//import org.springframework.http.converter.HttpMessageNotReadableException
//import org.springframework.security.authorization.AuthorizationDeniedException
//import org.springframework.web.servlet.resource.NoResourceFoundException
//
//@ControllerAdvice
//class GlobalExceptionHandler {
//
//    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)
//
//    @ExceptionHandler(DataRetrievalFailureException::class)
//    fun handleResourceNotFound(ex: DataRetrievalFailureException): ResponseEntity<ErrorResponse> {
//        val error = ErrorResponse(
//            errorCode = "NOT_FOUND",
//            errorMessage = ex.message ?: "Resource not found"
//        )
//        return ResponseEntity(error, HttpStatus.NOT_FOUND)
//    }
//
//    @ExceptionHandler(NoResourceFoundException::class)
//    fun handleResourceNotFound(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> {
//        val error = ErrorResponse(
//            errorCode = "NOT_FOUND",
//            errorMessage = ex.message ?: "Resource not found"
//        )
//        return ResponseEntity(error, HttpStatus.NOT_FOUND)
//    }
//
//    @ExceptionHandler(DataIntegrityViolationException::class)
//    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
//        val error = ErrorResponse(
//            errorCode = "BAD_REQUEST",
//            errorMessage = ex.message ?: "Reference already exists"
//        )
//        logger.error(ex.stackTraceToString())
//        return ResponseEntity(error, HttpStatus.BAD_REQUEST)
//    }
//
//    @ExceptionHandler(IllegalArgumentException::class)
//    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
//        val error = ErrorResponse(
//            errorCode = "BAD_REQUEST",
//            errorMessage = ex.message ?: "Illegal argument used"
//        )
//        logger.error(ex.message)
//        return ResponseEntity(error, HttpStatus.BAD_REQUEST)
//    }
//
//    @ExceptionHandler(MethodArgumentNotValidException::class)
//    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
//        val errorMessage = ex.bindingResult.allErrors.joinToString("; ") { it.defaultMessage ?: "Invalid input" }
//        val error = ErrorResponse(
//            errorCode = "BAD_REQUEST",
//            errorMessage = errorMessage
//        )
//        return ResponseEntity(error, HttpStatus.BAD_REQUEST)
//    }
//
//    @ExceptionHandler(HttpMessageNotReadableException::class)
//    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
//        val errorMessage = ex.message ?: "Invalid input"
//        val error = ErrorResponse(
//            errorCode = "BAD_REQUEST",
//            errorMessage = errorMessage
//        )
//        return ResponseEntity(error, HttpStatus.BAD_REQUEST)
//    }
//
//    @ExceptionHandler(AuthorizationDeniedException::class)
//    fun handleAuthorizationDeniedException(ex: AuthorizationDeniedException): ResponseEntity<ErrorResponse> {
//        val errorMessage = ex.message ?: "Access denied"
//        val error = ErrorResponse(
//            errorCode = "UNAUTHORIZED",
//            errorMessage = errorMessage
//        )
//        return ResponseEntity(error, HttpStatus.UNAUTHORIZED)
//    }
//
//    @ExceptionHandler(Exception::class)
//    fun handleGeneralException(ex: Exception): ResponseEntity<ErrorResponse> {
//        val error = ErrorResponse(
//            errorCode = "INTERNAL_SERVER_ERROR",
//            errorMessage = ex.message ?: "An unexpected error occurred"
//        )
//        logger.error(ex.stackTraceToString())
//        return ResponseEntity(error, HttpStatus.INTERNAL_SERVER_ERROR)
//    }
//
//    data class ErrorResponse(
//        val errorCode: String,
//        val errorMessage: String
//    )
//}
