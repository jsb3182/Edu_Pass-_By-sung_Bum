package kr.go.edu.boundary.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 이전 주소와 디렉터리 주소를 역할별 화면으로 연결합니다. */
@Controller
public class PageController {
    @GetMapping({"/admin.html", "/master/"})
    public String master() {
        return "redirect:/master/index.html";
    }

    @GetMapping("/login.html")
    public String login() {
        return "redirect:/master/login.html";
    }

    // 기존 서명 링크의 #token은 브라우저가 리다이렉트 대상에 유지합니다.
    @GetMapping({"/sign.html", "/user/"})
    public String user() {
        return "redirect:/user/index.html";
    }
}
