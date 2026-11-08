package kr.go.edu.boundary.common;

import kr.go.edu.boundary.tasks.TaskController;
import kr.go.edu.boundary.tasks.TaskService;
import kr.go.edu.boundary.tasks.ExcelImportController;
import kr.go.edu.boundary.tasks.ExcelImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({TaskController.class, ExcelImportController.class})
@AutoConfigureMockMvc(addFilters = false)
class ApiErrorsTest {
    @Autowired MockMvc mvc;
    @MockitoBean TaskService tasks;
    @MockitoBean ExcelImportService imports;

    @Test
    void malformedJsonReturnsUsefulErrorWithoutCallingService() throws Exception {
        mvc.perform(post("/api/admin/tasks").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("JSON")));
        verifyNoInteractions(tasks);
    }

    @Test
    void invalidIdAndPageReturnJson() throws Exception {
        mvc.perform(get("/api/admin/tasks/not-a-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
        mvc.perform(get("/api/admin/tasks?page=abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
        verifyNoInteractions(tasks);
    }

    @Test
    void missingFileReturnsUsefulError() throws Exception {
        mvc.perform(multipart("/api/admin/imports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("첨부 파일")));
        verifyNoInteractions(imports);
    }

    @Test
    void oversizedFileReturns413() throws Exception {
        when(imports.importFile(any(), any())).thenThrow(new MaxUploadSizeExceededException(10_000_000));
        mvc.perform(multipart("/api/admin/imports").file(new MockMultipartFile("file", "test.xlsx", null, new byte[]{1})))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value(containsString("10MB")));
    }

    @Test
    void duplicateDatabaseValueDoesNotExposeSql() throws Exception {
        when(tasks.list(anyString(), anyInt())).thenThrow(new DuplicateKeyException("INSERT INTO users secret-value"));
        mvc.perform(get("/api/admin/tasks"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("중복")))
                .andExpect(content().string(not(containsString("secret-value"))))
                .andExpect(content().string(not(containsString("INSERT"))));
    }
}
