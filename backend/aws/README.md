# AWS 백엔드

공통 JAR을 aws 프로필로 실행합니다. 클라우드용 Java 코드를 복제하지 않습니다.

- config/application-aws.yml: RDS 접속과 서버 설정
- config/env.example: 주입할 환경변수와 TLS 연결 예시
- task-definition.example.json: ECS Fargate 배포 템플릿
- run.ps1: 필요한 환경변수를 설정한 상태에서 aws 프로필 실행

루트에서 mvn clean package 후 docker build -f backend/Dockerfile -t boundary-records . 로 이미지를 만듭니다.
ECR에 이미지를 올리고 ECS 작업 정의의 이미지, 역할 ARN, Secrets Manager ARN, 로그 그룹을
실제 환경 값으로 채우는 단계는 별도 배포 작업입니다. 현재 템플릿으로 AWS 자원을 생성하지 않습니다.

RDS PostgreSQL은 ECS에서 접근 가능한 네트워크에 두고 DB_URL/DB_USER/DB_PASSWORD를 주입합니다.
config/env.example의 verify-full 예시를 사용한다면 RDS CA 파일을 해당 경로에 제공해야 합니다.
공개 접속은 HTTPS 로드밸런서를 통해 제공하고 APP_PUBLIC_BASE_URL에 그 기본 주소를 설정합니다.
관리자 경로는 /master/index.html, 방문자 경로는 /user/index.html입니다.
AWS 모드에서는 로컬 HTTP 허용 옵션을 켜도 HTTP 공개 주소를 거부합니다.

로컬 PC의 DB와 RDS는 자동 동기화되지 않습니다. 태블릿은 선택한 서버의 API를 통해
그 서버가 연결된 DB만 사용합니다. APK 자체나 서버 선택 UI는 아직 포함하지 않습니다.
