package kr.go.edu.boundary.tasks;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/imports")
public class ExcelImportController {
    private final ExcelImportService imports;

    public ExcelImportController(ExcelImportService imports) {
        this.imports = imports;
    }

    @PostMapping
    public ExcelImportService.ImportResult upload(@RequestParam MultipartFile file, Authentication authentication) {
        return imports.importFile(file, authentication);
    }
}
