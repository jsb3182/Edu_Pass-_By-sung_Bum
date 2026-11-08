package kr.go.edu.boundary.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** version은 화면에서 마지막으로 조회한 내역의 버전입니다. */
public record ChangeTaskStatusRequest(
        @NotBlank @Size(max = 100) String status, long version) { }
