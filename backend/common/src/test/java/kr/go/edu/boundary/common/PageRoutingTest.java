package kr.go.edu.boundary.common;

import kr.go.edu.boundary.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PageController.class)
@Import(SecurityConfig.class)
class PageRoutingTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;

    @BeforeEach
    void activeAccount() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), anyString())).thenReturn(1L);
    }

    @Test
    void visitorAndLoginResourcesArePublic() throws Exception {
        mvc.perform(get("/user/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/user/js/sign.js")));
        mvc.perform(get("/master/login.html")).andExpect(status().isOk());
        mvc.perform(get("/master/js/login.js")).andExpect(status().isOk());
        mvc.perform(get("/shared/assets/css/app.css")).andExpect(status().isOk());
    }

    @Test
    void masterRequiresLoginAndAllowsOperator() throws Exception {
        mvc.perform(get("/master/index.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/master/login.html"));
        mvc.perform(get("/master/index.html").with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").with(user("operator").roles("OPERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledAccountCannotOpenMaster() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Long.class), anyString())).thenReturn(0L);
        mvc.perform(get("/master/index.html").with(user("disabled").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyLinksRedirectToRolePages() throws Exception {
        mvc.perform(get("/sign.html")).andExpect(redirectedUrl("/user/index.html"));
        mvc.perform(get("/login.html")).andExpect(redirectedUrl("/master/login.html"));
        mvc.perform(get("/admin.html").with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl("/master/index.html"));
    }
}
