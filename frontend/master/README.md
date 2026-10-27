# 관리자 웹

서버의 /master/index.html에서 실행합니다. 로그인은 /master/login.html입니다.
관리자 화면 동작은 js/admin.js, 로그인 준비는 js/login.js에 있습니다.
전용 자산은 assets, 공통 스타일은 ../shared/assets에 둡니다.
API는 기존 /api/admin 경로를 유지하며 서버에서 계정 권한을 검사합니다.
