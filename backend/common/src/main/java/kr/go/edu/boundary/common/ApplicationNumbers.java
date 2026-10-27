package kr.go.edu.boundary.common;

import java.text.Normalizer;
import java.util.Locale;

public final class ApplicationNumbers {
    private ApplicationNumbers() { }

    public static String clean(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("신청번호가 비어 있습니다.");
        }
        // 전각 문자, 대소문자, 구분 기호 차이로 같은 신청번호가 중복 저장되지 않게 합니다.
        String folded = Normalizer.normalize(raw, Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);
        StringBuilder normalizedValue = new StringBuilder(folded.length());
        folded.codePoints().filter(Character::isLetterOrDigit).forEach(normalizedValue::appendCodePoint);
        String normalized = normalizedValue.toString();
        if (normalized.isBlank() || normalized.length() > 120) {
            throw new IllegalArgumentException("신청번호 형식이 올바르지 않습니다.");
        }
        return normalized;
    }
}

