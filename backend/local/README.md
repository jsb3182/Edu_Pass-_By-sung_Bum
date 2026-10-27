# 로컬 PC·태블릿 연결

## Docker로 PC에서 서버와 DB 실행

프로젝트 루트에서 실행합니다. 먼저 Docker Desktop의 Linux 엔진을 켜세요.

~~~powershell
mvn clean package
Copy-Item backend/local/env.example backend/local/.env
~~~

.env의 DB_PASSWORD와 BOOTSTRAP_ADMIN_PASSWORD를 본인 값으로 변경합니다.
첫 관리자 비밀번호는 16자 이상으로 지정합니다.

~~~powershell
docker compose --env-file backend/local/.env -f backend/local/compose.yml config --quiet
docker compose --env-file backend/local/.env -f backend/local/compose.yml up -d --build
~~~

PC에서 http://127.0.0.1:8080/master/index.html을 엽니다.
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
