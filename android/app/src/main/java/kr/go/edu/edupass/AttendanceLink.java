package kr.go.edu.edupass;

import java.net.URI;

record AttendanceLink(URI url) {
    private static final String PAGE = "/attendance/index.html";

    static AttendanceLink parse(String input, boolean allowPrivateHttp) {
        URI uri;
        try {
            uri = URI.create(input.trim());
        } catch (IllegalArgumentException exception) {
            throw invalidLink();
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getPort() > 65535 || uri.getPort() == 0
                || !PAGE.equals(uri.getRawPath()) || uri.getRawFragment() == null
                || !uri.getRawFragment().matches("token=[A-Za-z0-9_-]{43}")) {
            throw invalidLink();
        }
        if (!"https".equals(scheme) && !("http".equals(scheme) && allowPrivateHttp && privateHost(host))) {
            throw new IllegalArgumentException(allowPrivateHttp
                    ? "HTTPS 주소 또는 사설망 IP를 사용하는 출석 링크를 입력해 주세요."
                    : "원격 출석은 HTTPS 행사 링크만 열 수 있습니다.");
        }
        return new AttendanceLink(uri);
    }

    boolean permitsNavigation(URI target) {
        return sameOrigin(target) && PAGE.equals(target.getRawPath());
    }

    boolean permitsResource(URI target) {
        if (!sameOrigin(target)) return false;
        String path = target.getRawPath();
        return path.startsWith("/attendance/") || path.startsWith("/shared/assets/")
                || path.startsWith("/api/public/attendance/") || "/api/csrf".equals(path);
    }

    private boolean sameOrigin(URI target) {
        return url.getScheme().equals(target.getScheme()) && url.getHost().equalsIgnoreCase(target.getHost())
                && target.getRawUserInfo() == null && effectivePort(url) == effectivePort(target);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
    }

    private static boolean privateHost(String host) {
        if ("localhost".equalsIgnoreCase(host) || "[::1]".equals(host)) return true;
        if (!host.matches("(0|[1-9][0-9]{0,2})(\\.(0|[1-9][0-9]{0,2})){3}")) return false;
        String[] parts = host.split("\\.");
        for (String part : parts) if (Integer.parseInt(part) > 255) return false;
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        return first == 10 || first == 127 || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    private static IllegalArgumentException invalidLink() {
        return new IllegalArgumentException("관리자가 발급한 행사 출석 링크 전체를 붙여 넣어 주세요.");
    }
}
