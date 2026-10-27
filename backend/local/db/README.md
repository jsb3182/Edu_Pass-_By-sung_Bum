# 로컬 DB

local-db.yml은 로컬 프로필에서만 불러오는 PostgreSQL 접속 설정입니다.
PC 직접 실행의 기본 호스트는 127.0.0.1, Compose 실행은 DB_URL의 db 호스트를 사용합니다.
테이블 정의는 두 환경이 공유하는 backend/common/src/main/resources/db/migration/V1__init.sql에 있습니다.
DB 파일이나 실제 비밀번호는 이 폴더에 넣지 않습니다.
