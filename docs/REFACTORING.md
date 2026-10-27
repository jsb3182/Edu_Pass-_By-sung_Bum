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
