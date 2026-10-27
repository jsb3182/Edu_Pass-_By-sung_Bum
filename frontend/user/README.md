# 방문자 화면

index.html과 js/sign.js가 기존 방문자 서명 흐름을 담당합니다.
서버에서 /user/index.html#token=발급된토큰으로 엽니다. 토큰 없이 열면 안내 메시지를 표시합니다.
공통 CSS는 ../shared/assets, 전용 자산은 assets에 둡니다.

현재 API는 같은 서버의 /api/public 및 /api/csrf를 호출합니다.
APK 대비 화면 소스 경계만 나눴으며 Android/Capacitor 프로젝트는 아직 없습니다.
다음 단계에서 서버 주소 선택과 WebView 또는 패키징 방식을 정해야 합니다.
원격 웹 화면을 로드하는 경우 서버의 /user/index.html 주소를 사용하고,
HTML을 APK에 내장하는 경우 API 기본 주소·CORS·쿠키·CSRF 구성을 함께 구현해야 합니다.
DB 비밀번호를 앱에 넣거나 DB 포트를 직접 호출하지 않습니다.
