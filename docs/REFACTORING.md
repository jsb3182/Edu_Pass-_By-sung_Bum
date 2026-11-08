# 1단계 구조 분리 기록

- 기존 Java 17 / Spring Boot / PostgreSQL과 업무 API를 유지했습니다.
- src/main/resources/static의 관리자·방문자 화면을 frontend/master와 frontend/user로 이동했습니다.
- 공통 CSS와 Bootstrap은 frontend/shared/assets로 이동했습니다.
- Java와 SQL, Java 테스트는 backend/common/src로 이동했습니다.
- 로컬 연결 설정은 backend/local/config 및 db, AWS 설정은 backend/aws/config로 나눴습니다.
- 루트 Maven 빌드는 화면과 프로필을 공통 JAR에 포함합니다.
- 로그인 이동, QR 링크, 정적 자산, 권한 필터, 테스트와 미리보기 경로를 갱신했습니다.
- 기존 페이지 주소는 새 경로로 리다이렉트합니다.
- local과 aws는 각각 run.ps1로 실행하며 업무 코드와 마이그레이션은 공유합니다.

이번 단계에는 APK 생성, 서버 자동 검색, 방문자 직접 접수, 승인/반려 워크플로 추가,
실시간 푸시, AWS 배포를 포함하지 않았습니다. 기존 서명 흐름을 유지하는 구조 변경입니다.

학습 순서: backend/common의 BoundaryRecordsApplication → pom.xml → local 설정과 DB 연결
→ TaskController → TaskService → frontend/master → frontend/user → AWS 설정.

검증: Maven clean package 성공(Java 테스트 13개), Node 테스트 1개 통과,
Docker Compose config 검사 및 PowerShell 실행 스크립트 구문 검사 통과.
JAR에 관리자·방문자 화면과 local/aws DB 설정이 포함되는 것을 확인했습니다.
Docker 엔진이 실행 중이지 않아 실제 PostgreSQL 연결, 태블릿 접속, AWS 배포는 미검증입니다.

## 2단계: 로컬 구동 검증 (2026-11-09)

backend 브랜치에서 전용 Docker PostgreSQL과 Java 서버를 실행했습니다.
실제 Flyway 초기 테이블 생성, 관리자 로그인, CSRF 거부, 신청 내역 저장·조회,
서명 링크 발급·방문자 조회를 확인했습니다. 개발 PC 포트는 28080이며,
PC별 설정은 Git에서 제외된 backend/local/.env에 있습니다.
init-env.ps1은 최초 설정 생성 후 재실행 시 기존 파일을 유지합니다.
휴대폰 접속과 AWS는 다음 단계입니다.

## 3단계: 신청 내역과 엑셀 처리의 책임 분리 (2026-11-09)

1. 요청·응답 record를 tasks/dto로 이동했습니다. JSON 이름과 검증 제약은 유지했습니다.
2. TaskService의 SQL·행 변환을 TaskRepository로 옮겼습니다. 트랜잭션은 서비스에 유지했습니다.
3. 엑셀 행 파싱을 ExcelImportParser로 분리했습니다. 적재 트랜잭션과 잠금 순서는 그대로입니다.
4. 중복·필수값·수정 충돌 및 엑셀 수식·불완전 행의 거부를 테스트하고, 실제 로컬 API 검증을 확장했습니다.

검증: Maven clean package와 Java 테스트 20개, Node 테스트 1개 통과.
새 JAR로 로컬 컨테이너를 갱신한 후 DB 저장·검색, 상태 변경, 오래된 버전의 갱신 거부,
관리자 로그인·서명 링크 조회를 확인했습니다. 검증용 신청 내역 1건이 추가됐습니다.
로컬 서버는 계속 28080 포트로 실행합니다.

## 4단계: 오류 처리와 비밀번호 검증 보완 (2026-11-09)

- API 인증 실패가 HTML 로그인 응답으로 이어지는 문제를 수정했습니다.
- 권한·CSRF·비활성 계정 응답을 한국어 JSON으로 통일했습니다.
- 잘못된 JSON·ID·페이지 번호, 파일 누락·용량 초과, DB 중복 제약 오류를 명확히 안내합니다.
- PasswordPolicy를 추가하여 계정 생성·변경·초기 관리자 생성의 검증을 통일했습니다.
- 기존 128자 검사가 BCrypt의 72바이트 제한과 맞지 않던 문제를 수정하고 화면 안내를 보강했습니다.

검증: Java 32개 및 Node 1개 테스트 통과. 실제 서버에서 로그인, 입력·파일·비밀번호 오류,
등록·검색·수정 충돌·서명 링크 조회까지 확인했습니다. 검증용 신청 내역 1건이 추가됐습니다.
