# KRDS 화면 적용 범위

방문 기록 시스템은 KRDS의 밝은 모드 팔레트와 서체를 사용합니다. 프로젝트의 Bootstrap 기반 화면에 적용할 토큰은 `frontend/shared/assets/css/krds-theme.css`, 업무별 배치는 `frontend/shared/assets/css/app.css`에 있습니다.

| 적용 요소 | 화면에서 확인할 부분 |
| --- | --- |
| 색상과 서체 | 밝은 회색 바탕, 남색 서비스 헤더, 파란색 주요 버튼, Pretendard GOV 본문 |
| 탐색 | 상단 업무 메뉴, 현재 메뉴 밑줄과 `aria-current`, 본문 바로가기, 현재 위치 |
| 입력과 표 | 48px 기본 조작 높이, 필수 입력 표시, 표의 열 제목, 좁은 화면에서 표 안쪽 가로 스크롤 |
| 서명 | 순서가 있는 단계 표시, 선택 위치 동의, 저장 완료와 확인 번호 |
| 초점 | 입력 요소의 키보드 초점 표시, 화면 전환 시 페이지 제목과 완료 제목에 초점 이동 |

공통 헤더에는 방문 기록 시스템 자체 표식을 사용합니다. 웹폰트는 Pretendard GOV 1.3.9를 포함하며, 외부 CDN 호출 없이 서비스합니다. 글꼴의 SIL Open Font License 1.1은 `frontend/shared/assets/fonts/OFL.txt`에 있습니다.

색상 토큰은 행정안전부의 범정부 UI/UX 디자인시스템 HTML Component Kit 1.0.3에서 가져왔습니다. KRDS는 공공누리 제1유형에 따라 제공됩니다. [KRDS 개발자 가이드](https://www.krds.go.kr/html/site/outline/outline_03.html) · [KRDS 저작권](https://www.krds.go.kr/html/site/utility/utility_06.html)

서체와 컴포넌트는 다음 지침을 참고했습니다. [타이포그래피](https://www.krds.go.kr/html/site/style/style_03.html) · [헤더](https://www.krds.go.kr/html/site/component/component_02_03.html) · [표](https://www.krds.go.kr/html/site/component/component_04_11.html) · [단계 표시기](https://www.krds.go.kr/html/site/component/component_07_01.html) · [Pretendard GOV 배포본](https://github.com/orioncactus/pretendard/releases/tag/v1.3.9)
