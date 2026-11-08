package kr.go.edu.boundary.common;

import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadableBody() {
        return ResponseEntity.badRequest().body(Map.of("error", "요청 내용이 비어 있거나 JSON 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, String>> invalidParameter() {
        return ResponseEntity.badRequest().body(Map.of("error", "요청 주소의 ID 또는 숫자 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<Map<String, String>> missingParameter() {
        return ResponseEntity.badRequest().body(Map.of("error", "필수 요청 값 또는 첨부 파일이 누락되었습니다."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> oversizedUpload() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "파일 또는 전체 업로드 용량이 허용 한도를 초과했습니다. 10MB 이하의 파일을 선택해 주세요."));
    }

    // 두 요청이 중복 사전 검사를 동시에 통과해도 DB 유일성 제약 오류를 사용자에게 설명합니다.
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, String>> duplicate() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "이미 등록된 값과 중복됩니다. 새로고침 후 확인해 주세요."));
    }
}

