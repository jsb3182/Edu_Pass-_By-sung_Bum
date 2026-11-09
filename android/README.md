# 교육 출석부 Android 앱

관리자가 발급한 행사 링크를 붙여 넣으면 WebView가 해당 서버의 Vue 출석 화면을 엽니다. 방문자는 교육청 소속, 직급, 성함, 전화번호와 서명을 등록합니다. 관리자 화면은 노트북 브라우저에서 사용합니다.

| 앱 | 연결 |
| --- | --- |
| 로컬 `local` | 노트북과 같은 사설망의 서버. HTTPS를 지원하며 사설 IPv4의 HTTP는 테스트에 사용할 수 있습니다. |
| 원격 `remote` | 휴대전화 데이터나 다른 네트워크에서 접속할 수 있는 서버. 신뢰된 인증서가 있는 HTTPS 주소를 사용합니다. |

## Android Studio에서 빌드

1. Windows에서는 `C:\Dev\EduPass`처럼 영문 경로에 저장소를 받은 뒤 Android Studio에서 `android`를 엽니다. SDK 경로도 영문으로 설정합니다.
2. Gradle JDK를 17로 설정하고 SDK Manager에서 Android SDK Platform 35와 Build Tools 35.0.0을 설치합니다.
3. Gradle 동기화 후 Build Variants에서 `localDebug` 또는 `remoteDebug`를 선택합니다.
4. Windows에서는 저장소 루트에서 아래 명령으로 두 설치용 APK를 함께 만들 수 있습니다.

~~~powershell
cd android
.\gradlew.bat testLocalDebugUnitTest testRemoteDebugUnitTest assembleLocalDebug assembleRemoteDebug
~~~

로컬 APK는 `app/build/outputs/apk/local/debug/app-local-debug.apk`, 원격 APK는 `app/build/outputs/apk/remote/debug/app-remote-debug.apk`에 생성됩니다. 테스트 빌드는 디버그 키로 서명합니다. 기관 배포용 빌드는 기관에서 관리하는 키로 서명해 배포합니다.

## 노트북에서 행사 운영

1. `test` 브랜치를 받아 프로젝트 루트의 로컬 실행 스크립트로 서버를 실행합니다. 네트워크 설정은 [로컬 실행 안내](../backend/local/README.md)를 참고합니다.
2. 관리자 로그인 후 **행사 출석부 → 새 행사 만들기**에서 행사명과 날짜를 입력합니다.
3. 행사 링크를 복사해 태블릿에서 앱의 **행사 출석 링크**에 붙여 넣습니다. 링크에는 행사 접수 권한이 있으므로 참가자에게만 공유합니다.
4. 방문자는 정보를 입력하고 서명한 뒤 저장합니다. **다음 방문자 등록**을 누르면 입력과 서명이 초기화됩니다.
5. 관리자 화면에서 행사별 명단을 확인하고 **엑셀 다운로드**로 전화번호와 서명이 포함된 출석부를 저장합니다. 종료 후 **접수 마감**을 누릅니다.

노트북과 태블릿이 다른 기기라면 행사 링크에 노트북에서만 열리는 `127.0.0.1` 대신 태블릿에서 접속할 수 있는 주소를 사용합니다. 실제 출석 정보는 로컬과 원격 모두 신뢰된 인증서가 설정된 HTTPS 주소로 받으세요. 원격 서버 구성은 [AWS 실행 안내](../backend/aws/README.md)를 참고하세요.
