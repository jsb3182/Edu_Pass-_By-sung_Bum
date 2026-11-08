package kr.go.edu.boundary.tasks.dto;

import java.util.List;
import kr.go.edu.boundary.tasks.TaskRecord;

/** 검색 결과와 페이지 정보를 함께 반환합니다. page는 0부터 시작합니다. */
public record TaskPage(List<TaskRecord> items, long total, int page, int size) { }
