# 화면 수정 안내

## 먼저 화면 보기

`preview/admin.html`을 파일 탐색기에서 열면 관리자 화면을 볼 수 있습니다. 상단 링크로 `preview/login.html`과 `preview/sign.html`도 열 수 있습니다. 이 미리보기에는 가상 자료만 있으며, 버튼을 눌러도 서버에 저장하거나 위치를 조회하지 않습니다.

## 실제 화면 파일

| 파일 | 역할 |
| --- | --- |
| `frontend/master/index.html` | 관리자 화면의 글자, 입력칸, 표 |
| `frontend/master/js/admin.js` | 관리자 메뉴, 조회, 등록, 엑셀 적재 버튼 |
| `frontend/master/login.html` | 관리자 로그인 화면 |
| `frontend/master/js/login.js` | 로그인 양식의 CSRF 토큰 준비 |
| `frontend/user/index.html` | 태블릿 방문자 서명 화면 |
| `frontend/user/js/sign.js` | 서명 그리기, 동의, 위치 확인, 저장 |
| `frontend/shared/assets/css/app.css` | 두 화면의 색상, 크기, 모바일 배치 |
| `frontend/shared/assets/css/krds-theme.css` | 공공서비스 색상 토큰·본문 바로가기·현재 위치·키보드 초점 |
| `frontend/shared/assets/vendor/bootstrap.min.css` | 로컬에 포함한 Bootstrap 4.5 기본 스타일 |

`preview/` 폴더는 화면 검토용입니다. 실제 화면 문구나 배치를 바꿀 때는 `frontend/`의 파일을 수정하세요.

## 수정할 때 기억할 점

1. HTML의 `id`는 JavaScript가 화면 요소를 찾는 이름입니다. 이름을 바꾸면 같은 이름을 JavaScript에서도 바꿔야 합니다.
2. 서버에서 받은 이름과 메모는 `textContent`로 표시합니다. `innerHTML`로 바꾸면 입력값이 화면 코드로 실행될 수 있습니다.
3. 관리자 페이지의 데이터 요청은 `admin.js`의 `api()`에 모여 있습니다. 화면을 바꿀 때 API 주소나 전송 형식을 함께 확인하세요.
4. 위치는 방문자의 별도 동의 후 `checkLocation()`에서만 요청합니다. 미리보기에는 위치 요청이 없습니다.
5. 노트북 로컬과 AWS 원격은 같은 화면 코드를 사용합니다. 현재 접속한 서버의 모드는 관리자 대시보드에서 표시합니다.

현재 화면은 별도 프론트엔드 빌드 도구가 없는 HTML, CSS, JavaScript입니다. 추후 Vue 화면을 도입하더라도 JSON API를 그대로 사용할 수 있도록 분리해 두었습니다.

Bootstrap의 그리드, 카드, 버튼, 표, 입력칸 클래스를 사용합니다. 색상과 간격은 `app.css`에서 덮어씁니다. Bootstrap 파일은 프로젝트에 포함되어 있어 CDN 접속이 필요하지 않습니다. Bootstrap의 MIT 라이선스는 `frontend/shared/assets/vendor/LICENSE.bootstrap.txt`에 있습니다.
KRDS 관련 적용 범위와 공식 패키지 확인 사항은 [공공서비스 디자인 적용 범위](KRDS_ADAPTATION.md)에 적었습니다.
