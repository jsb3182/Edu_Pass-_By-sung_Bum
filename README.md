# 방문·서명 기록 시스템

Java 17 / Spring Boot / PostgreSQL 기반입니다. 프론트엔드 소스는 역할별로 분리하고,
백엔드는 공통 Java 코드에 local 또는 aws 실행 설정을 적용합니다.

## 폴더 구조

~~~text
frontend/
  user/       방문자 태블릿 화면: index.html, js/sign.js, assets/, tests/
  master/     관리자 웹 화면: index.html, login.html, js/, assets/
  shared/     두 화면에서 함께 사용하는 CSS와 Bootstrap
backend/
  common/     pom.xml, src/main/java, src/main/resources, src/test/java
  local/      run.ps1, compose.yml, config/application-local.yml, db/local-db.yml
  aws/        run.ps1, config/application-aws.yml, config/env.example, task-definition.example.json
  Dockerfile  두 환경에서 사용하는 공통 컨테이너 이미지
preview/      서버 연결 없이 확인하는 가상 화면
pom.xml       루트에서 전체 백엔드를 빌드하는 Maven 집계 파일
~~~

local과 aws에 업무 코드를 복제하지 않습니다. 두 실행 스크립트가 common의
BoundaryRecordsApplication을 각각 다른 프로필로 실행합니다. DB와 실행 환경은 서로 독립적이며
자동 동기화나 자동 서버 검색은 구현하지 않았습니다.

## 빌드와 테스트

프로젝트 루트에서 실행합니다. JDK 17, Maven 3.9+, JavaScript 테스트용 Node.js가 필요합니다.

~~~powershell
mvn clean package
node --test frontend/user/tests/sign-location.test.js
~~~

Maven이 PATH에 없다면 설치된 Maven의 mvn.cmd 전체 경로로 실행하세요.
이번 작업 환경에는 검증용 Maven이 .tools/apache-maven-3.9.9/bin/mvn.cmd에 있습니다.
.tools는 Git에 포함되지 않습니다.

결과는 backend/common/target/boundary-records-0.1.0.jar입니다.
Maven이 frontend의 실제 화면과 두 프로필 설정을 JAR에 포함합니다.
화면을 수정한 후에도 다시 빌드해야 합니다. target 내부 파일은 수정하지 않습니다.

## 실행

- [로컬 PC·태블릿 연결](backend/local/README.md)
- [AWS 실행 설정](backend/aws/README.md)
- [파일별 화면 수정 안내](docs/FRONTEND_GUIDE.md)
- [기존 업무 코드 흐름](docs/CODE_MAP.md)
- [이번 구조 변경 기록](docs/REFACTORING.md)

서버 시작 후 관리자 주소는 /master/index.html, 로그인은 /master/login.html입니다.
방문자 주소는 /user/index.html#token=발급된토큰입니다.
기존 /admin.html, /login.html, /sign.html 링크는 새 화면으로 이동합니다.

프론트엔드 소스는 분리했지만 배포 시에는 백엔드와 같은 주소로 제공합니다.
현재 fetch('/api/...')와 세션·CSRF 인증을 유지하기 위한 구성입니다.
실제 화면은 파일을 더블클릭해서 실행하지 않고 서버를 통해 엽니다.
파일로 볼 때는 preview/admin.html을 사용하세요.

## 현재 구현된 기능과 다음 단계

- 관리자: 신청 내역 조회·등록·상태 변경, Excel 적재, 방문자 서명 링크·QR 발급,
  서명·위치 내역 확인, 계정·감사 기록 조회
- 방문자: 관리자가 발급한 일회용 링크로 내역 확인, Canvas 서명, 선택적 위치 동의, 제출
- DB: Flyway 마이그레이션, 트랜잭션, 감사 기록, 전송 대기 이벤트 저장
- 접근 제어: 관리자 세션, 역할 검사, CSRF, 비활성 계정 차단

방문 목적·이름·소속은 현재 관리자가 링크 발급 때 입력합니다. 방문자가 직접 목적과 정보를 입력하는
접수 흐름, 전용 승인/반려 기능, 실시간 푸시 현황판은 이번 구조 변경에서 새로 구현하지 않았습니다.
APK 프로젝트와 서버 주소 선택·검색 기능도 다음 단계입니다.
APK를 붙일 때는 DB에 직접 접속하지 않고 선택한 서버의 API를 사용합니다.

로컬 DB와 AWS RDS는 모두 PostgreSQL을 사용합니다. 기존 SQL이 PostgreSQL 전용 기능을
사용하므로 SQLite로 바꾸려면 별도 데이터 계층 수정이 필요합니다.

## 기본 설정

DB_PASSWORD, APP_NODE_ID, APP_PUBLIC_BASE_URL 등은 환경변수로 주입합니다.
로컬 기본 DB 주소는 jdbc:postgresql://127.0.0.1:5432/boundary입니다.
AWS는 DB_URL, DB_USER도 반드시 지정합니다.
최초 관리자 생성에는 BOOTSTRAP_ADMIN_USER와 16자 이상 BOOTSTRAP_ADMIN_PASSWORD가 필요합니다.
실제 비밀번호와 .env 파일은 Git에 포함하지 않습니다.

## 엑셀과 서명

엑셀 첫 시트 헤더는 신청번호, 신청자, 학교명, 진행상태입니다. 10MB·5,000행 이내의 .xlsx를
지원하며, 오류가 있으면 전체 적재를 취소합니다. 신청번호는 정규화하여 중복을 검사합니다.
서명 링크는 10분짜리 일회용이며 DB에는 토큰 원문 대신 해시를 저장합니다.
서명 당시 내역 스냅샷·동의 문구·감사 기록을 함께 저장합니다.
