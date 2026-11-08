# 로컬 PC·태블릿 연결

## Docker로 PC에서 서버와 DB 실행

프로젝트 루트에서 실행합니다. 먼저 Docker Desktop의 Linux 엔진을 켜세요.

~~~powershell
mvn clean package
./backend/local/init-env.ps1
~~~

init-env.ps1이 DB와 최초 관리자용 무작위 비밀번호를 생성합니다.
계정은 .env의 BOOTSTRAP_ADMIN_USER와 BOOTSTRAP_ADMIN_PASSWORD에서 확인합니다.
이미 .env가 있으면 덮어쓰지 않습니다. .env는 Git에서 제외되어 다른 PC에 전달되지 않습니다.
8080이 사용 중이면 최초 생성 시 ./backend/local/init-env.ps1 -Port 18080으로 실행하세요.
기존 설정의 포트를 바꾸려면 .env의 LOCAL_HTTP_PORT와 APP_PUBLIC_BASE_URL을 함께 변경합니다.

~~~powershell
docker compose --env-file backend/local/.env -f backend/local/compose.yml config --quiet
docker compose --env-file backend/local/.env -f backend/local/compose.yml up -d --build
~~~

PC에서 http://127.0.0.1:8080/master/index.html을 엽니다.
-Port 18080으로 설정했다면 http://127.0.0.1:18080/master/index.html을 엽니다.
DB 데이터는 boundary-local 프로젝트의 boundary_db 이름 있는 볼륨에 유지됩니다.
DB의 5432 포트는 외부에 공개하지 않습니다. 태블릿은 서버의 8080 포트로만 접속합니다.

중지: docker compose --env-file backend/local/.env -f backend/local/compose.yml down
데이터를 보존하려면 down에 -v를 붙이지 마세요.

## 같은 Wi-Fi의 태블릿·휴대폰으로 개발 테스트

1. PC에서 ipconfig로 Wi-Fi/LAN 어댑터의 IPv4 주소를 확인합니다.
2. .env에서 LOCAL_BIND_ADDRESS=0.0.0.0으로 변경합니다.
3. APP_PUBLIC_BASE_URL=http://실제PC주소:8080으로 변경합니다.
4. APP_ALLOW_INSECURE_HTTP=true, SESSION_COOKIE_SECURE=false로 설정합니다.
5. 위의 compose up -d --build 명령을 다시 실행합니다.
6. PC 방화벽에서 사설 네트워크의 TCP 8080 인바운드를 허용합니다.
7. PC와 같은 네트워크의 태블릿에서 관리자가 발급한 QR을 엽니다.

예: PC 주소가 192.168.0.10이면 관리자 URL은 http://192.168.0.10:8080/master/index.html입니다.
태블릿에서 localhost 또는 127.0.0.1은 태블릿 자신이므로 PC 주소를 사용해야 합니다.
공유기의 게스트망/기기 격리 설정은 기기 간 연결을 막을 수 있습니다.
PC IP가 바뀌면 APP_PUBLIC_BASE_URL도 변경하고 서명 링크를 새로 발급합니다.
서버를 자동으로 찾는 기능은 아직 없습니다.

HTTP는 로컬 개발 테스트용 명시적 옵션입니다. 브라우저의 위치 확인 기능은 일반 LAN HTTP에서
제한되므로 위치까지 테스트하려면 태블릿이 신뢰하는 인증서의 HTTPS 주소를 사용하세요.
HTTPS 프록시 사용 시 APP_PUBLIC_BASE_URL=https://호스트, SESSION_COOKIE_SECURE=true,
APP_ALLOW_INSECURE_HTTP=false로 설정합니다.

## PC에 설치한 PostgreSQL과 Java로 실행

PostgreSQL에 boundary DB와 접속 계정을 먼저 만듭니다.
연결 설정은 db/local-db.yml, 테이블 생성 SQL은 ../common/src/main/resources/db/migration에 있습니다.
서버 시작 시 Flyway가 테이블을 생성합니다.

~~~powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/boundary'
$env:DB_USER = 'boundary'
$env:DB_PASSWORD = '본인이설정한DB비밀번호'
$env:BOOTSTRAP_ADMIN_USER = 'admin'
$env:BOOTSTRAP_ADMIN_PASSWORD = '본인이설정한16자이상비밀번호'
$env:APP_NODE_ID = 'office-notebook-01'
$env:SERVER_ADDRESS = '0.0.0.0'
$env:APP_PUBLIC_BASE_URL = 'http://192.168.0.10:8080'
$env:APP_ALLOW_INSECURE_HTTP = 'true'
$env:SESSION_COOKIE_SECURE = 'false'
./backend/local/run.ps1
~~~

IP와 비밀번호를 실제 값으로 바꿔 실행합니다. run.ps1은 .env를 자동으로 읽지 않습니다.
직접 실행은 위 PowerShell 환경변수를, Docker는 --env-file을 사용합니다.
SQLite 설정은 제공하지 않습니다. 현재 스키마와 SQL은 PostgreSQL용입니다.

## 서버가 실제로 연결됐는지 확인

서버가 시작된 후 프로젝트 루트에서 실행합니다. Node.js 20 이상이 필요합니다.

~~~powershell
node backend/local/smoke-test.cjs
~~~

DB 상태, 관리자 로그인, CSRF 차단, 신청 내역 저장·조회, 서명 링크 발급과 방문자 조회를 검사합니다.
매번 이름이 로컬 연결 검증용인 신청 내역 1건과 10분짜리 서명 세션 1건을 저장합니다.
실제 서명은 제출하지 않습니다. 개발용 DB에서 실행하세요.
로그인 비밀번호를 변경했다면 .env의 BOOTSTRAP_ADMIN_PASSWORD도 현재 값으로 맞춰야 합니다.
이 값을 바꾸는 것만으로 DB에 이미 있는 관리자 비밀번호가 변경되지는 않습니다.

다른 PC에서는 저장소를 받은 뒤 backend 브랜치로 이동하고 위 초기 설정부터 실행합니다.
DB 데이터와 .env는 GitHub로 동기화되지 않으므로 PC마다 독립적으로 생성됩니다.
