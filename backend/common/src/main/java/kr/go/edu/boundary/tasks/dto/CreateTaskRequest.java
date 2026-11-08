package kr.go.edu.boundary.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 화면에서 보내는 신청 내역 등록 요청입니다. */
public record CreateTaskRequest(
        @NotBlank @Size(max = 120) String applicationNo,
        @NotBlank @Size(max = 200) String applicantName,
        @NotBlank @Size(max = 200) String schoolName,
        @NotBlank @Size(max = 100) String status) { }
