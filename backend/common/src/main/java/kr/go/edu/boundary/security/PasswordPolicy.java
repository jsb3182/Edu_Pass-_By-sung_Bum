package kr.go.edu.boundary.security;

import java.nio.charset.StandardCharsets;

/** BCrypt가 처리할 수 있는 UTF-8 바이트 길이까지 검사합니다. */
public final class PasswordPolicy {
    private PasswordPolicy() { }

    public static void validate(String password) {
        if (password == null || password.length() < 16
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("비밀번호는 16자 이상, UTF-8 기준 72바이트 이하로 입력하세요. "
                    + "영문·숫자는 최대 72자, 한글만 사용하면 최대 24자입니다.");
        }
    }
}
