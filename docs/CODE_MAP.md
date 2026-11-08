# 코드 흐름 한눈에 보기

## 신청 내역 API

코드는 backend/common/src/main/java/kr/go/edu/boundary/tasks에 있습니다.

~~~text
관리자 화면 → TaskController → TaskService → TaskRepository → PostgreSQL
~~~

- TaskController: URL과 HTTP 요청을 받아 서비스에 전달합니다.
- dto/CreateTaskRequest: 등록 요청의 필드와 필수값·길이 제약을 정의합니다.
- dto/ChangeTaskStatusRequest: 상태와 화면에서 조회했던 version을 받습니다.
- dto/TaskPage: 목록(items), 전체 개수(total), 페이지(page), 크기(size)를 반환합니다.
- TaskService: 중복 번호, 필수값, 수정 충돌을 판단하고 감사·이벤트 기록을 남깁니다.
- TaskRepository: SQL 실행과 DB의 한 행을 TaskRecord로 바꾸는 작업을 담당합니다.

예를 들어 상태 변경은 TaskController.status()에서 요청을 받아
TaskService.changeStatus()로 전달합니다. 서비스는 입력을 검사하고
TaskRepository.updateStatus()를 호출합니다. SQL은 id와 version이 모두 일치할 때만 갱신합니다.
갱신 결과가 0건이면 서비스가 충돌 오류를 반환합니다. 1건이면 저장된 내역을 읽고
감사 기록과 이벤트를 저장한 뒤 화면으로 돌려줍니다.

@Transactional은 서비스의 create()와 changeStatus()에 그대로 있습니다.
따라서 SQL을 다른 파일로 옮겨도 내역 변경·감사 기록·이벤트 기록은 같은 트랜잭션에 참여합니다.
API 주소, JSON 필드, 검증 규칙은 기존과 같습니다.

## 엑셀 적재

`ExcelImportService.importFile()`이 파일 크기와 확장자를 검사한 후 `ExcelImportParser.parse()`로 모든 행을 읽고 검증합니다. 파서는 DB를 사용하지 않으며, 헤더·필수값·수식·중복 신청번호 검사를 담당합니다. 검증이 끝나면 서비스가 신청번호로 기존 내역을 찾습니다. 새 번호는 추가하고, 값이 달라진 번호는 갱신합니다. 변경된 행마다 `OutboxWriter`가 전송 대기 기록을 남깁니다. 적재 건수와 감사 기록까지 **하나의 데이터베이스 트랜잭션**에 포함됩니다. 중간에 실패하면 전체 작업을 롤백합니다.

`ApplicationNumbers.clean()`은 전각 문자·대소문자·구분 기호 차이를 통일합니다. 직접 등록과 엑셀 적재가 같은 정제 신청번호를 사용합니다. 기존 행을 바꿀 때는 `version`도 비교해, 동시에 수정된 내역을 엑셀이 조용히 덮어쓰지 않게 합니다.

## 방문자 서명

`SigningService.issue()`는 10분짜리 일회용 링크를 만듭니다. 데이터베이스에는 토큰 원문 대신 해시만 보관합니다. `submit()`은 링크를 한 번만 소비하고 신청 내역의 버전을 확인한 뒤, 서명 이미지·당시 신청 내역·동의 문구·감사 기록을 함께 저장합니다. 하나라도 실패하면 링크 소비까지 롤백됩니다.

`sign.js`는 위치를 별도로 동의받은 경우에만 조회합니다. 위치 재확인 중에는 서명 제출을 기다리게 하고, 동의를 철회한 뒤 늦게 도착한 결과는 무시합니다.

## 화면

`admin.js`의 `api()`가 관리자 요청과 오류 처리를 담당합니다. `login.js`는 로그인 양식에 CSRF 토큰을 넣습니다. `preview/`는 가상 데이터만 보여 주며 저장 요청을 하지 않습니다. 화면 파일별 위치는 [화면 수정 안내](FRONTEND_GUIDE.md)를 보세요.

## 다음에 분리할 부분

현재 TaskService의 SQL 분리는 끝났습니다. ExcelImportService의 적재 SQL과 배치 기록,
SigningService의 서명 처리, AdminService의 계정 처리는 아직 서비스 안에 있습니다.
이 부분도 같은 방식으로 작은 단위로 나누고 트랜잭션 경계를 검증할 수 있습니다.

## 오류 응답과 비밀번호 검증

ApiSecurityErrors는 인증·권한·CSRF·비활성 계정 오류를 처리합니다.
API 응답 형식은 {"error":"안내 메시지"}이며 일반 관리자 페이지는 로그인 화면으로 이동합니다.
ApiErrors는 컨트롤러 입력 및 DB 중복 오류를 같은 JSON 형식으로 변환합니다.

| 상태 | 의미 | 화면에서 할 일 |
| --- | --- | --- |
| 400 | 잘못된 입력·JSON·ID·누락 파일 | 안내에 맞춰 입력 수정 |
| 401 | API 로그인 필요 또는 세션 만료 | 다시 로그인 |
| 403 | 권한 부족·CSRF 오류·비활성 계정 | 메시지에 따라 새로고침 또는 관리자 문의 |
| 409 | 중복 값 또는 수정 충돌 | 새로고침하여 현재 데이터 확인 |
| 413 | 업로드 용량 초과 | 파일 크기 축소 |

PasswordPolicy는 계정 생성·비밀번호 변경·최초 관리자 생성이 공유합니다.
BCrypt 제한에 맞춰 16자 이상, UTF-8 기준 72바이트 이하를 허용합니다.
영문·숫자는 최대 72자, 한글만 쓰면 최대 24자이며, 혼합 입력은 실제 바이트 길이를 검사합니다.
