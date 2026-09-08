# Android companion · Phase 2

Kotlin 앱에서 Samsung Health의 최근 90일 running / track running 기록을 읽습니다.
최신순 목록, 상세, 경로 지도, 1 km 구간, 선택적 심박·케이던스를 표시합니다.
러닝별 제목이나 메모는 기록하지 않습니다. Publish는 Phase 3에서 연결하며 현재 비활성화되어 있습니다.

## 빌드

JDK 17, Android SDK Platform 35 / Build Tools 35.0.0이 필요합니다.
Gradle Wrapper 8.11.1, Android Gradle Plugin 8.9.2, Kotlin 2.1.20을 사용합니다.
`JAVA_HOME`을 JDK에, `ANDROID_HOME`을 Android SDK에 지정합니다.
Android Studio에서는 이 `android` 폴더를 열고 Gradle JDK를 17로 지정합니다.

저장소 루트의 Windows PowerShell:

```powershell
.\android\gradlew.bat -p android :core:test :app:assembleDemoDebug :app:lintDemoDebug
.\android\gradlew.bat -p android :app:assembleSamsungDebug :app:lintSamsungDebug
node android/scripts/check-web-contract.mjs
```

macOS/Linux에서는 `./android/gradlew -p android ...`를 사용합니다.
공유 JSON 검사에는 루트 `npm ci`가 필요하며 `:core:test`에서 생성한 가상 기록만 읽습니다.

- 데모 APK: `app/build/outputs/apk/demo/debug/app-demo-debug.apk`
- 실제 연결 APK: `app/build/outputs/apk/samsung/debug/app-samsung-debug.apk`
- 설치: `adb install -r android/app/build/outputs/apk/samsung/debug/app-samsung-debug.apk`
- 패키지: `com.sammy.running` (데모는 `com.sammy.running.demo`, 동시 설치 가능)

이번 PC에서 검증용 JDK는 Git 제외 폴더 `.tools/jdk/`에 설치했습니다.
필요하면 루트 PowerShell에서 현재 셸에만 설정할 수 있습니다.

```powershell
$env:JAVA_HOME = (Get-ChildItem .tools/jdk -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = 'C:\Android\sdk'
```

## Samsung SDK 설치

1. [공식 다운로드](https://developer.samsung.com/health/data/overview.html)에서 Samsung 계정으로 로그인하고 SDK v1.1.0을 받습니다.
2. 압축 안 `1.1.0/libs/samsung-health-data-api-1.1.0.aar`를 `android/app/libs/` 바로 아래에 넣습니다.
3. 이 폴더에는 AAR 하나만 둡니다. SDK 바이너리와 다운로드 폴더는 Git에 올리지 않습니다.
4. `assembleSamsungDebug`를 실행합니다. SDK 없이도 `assembleDemoDebug`와 `:core:test`는 가능합니다.

## 개발용 휴대폰 설정

공식 요구사항: Android 10(API 29) 이상, Samsung Health 6.30.2 이상, **실제 휴대폰**.
Samsung SDK는 에뮬레이터를 지원하지 않습니다. Watch 기록은 먼저 휴대폰 Samsung Health로 동기화합니다.

개인 개발·테스트에서는 Samsung Health → 설정 → Samsung Health 정보 → 버전 영역을 빠르게 10회 이상 누르고,
Developer mode (Samsung Health Data SDK) → Developer Mode for Data Read를 켭니다.
Samsung Health 7.00.6에서는 아래쪽 **Samsung Health Data SDK** 메뉴 안의 **Developer mode** 스위치가
읽기를 활성화합니다(실기기 확인). App package name / Access code는 쓰기 테스트용이므로 입력하지 않습니다.
위쪽 **Samsung Health SDK for Android** 메뉴는 구형 SDK용입니다.
휴대폰 USB 디버깅을 허용하고 `adb devices`에서 `device` 상태를 확인한 뒤 APK를 설치합니다.
앱에서 **Samsung Health 연결**을 눌러 운동 읽기 권한을 허용합니다. 경로 권한은 거부해도 요약 기록을 읽습니다.
경로가 필요할 때 목록의 **경로 읽기 권한 요청**으로 다시 요청할 수 있습니다.

이 설정은 개발·디버깅 전용이며 일반 사용자 배포에는 Samsung 파트너 등록이 필요합니다.
앱은 Samsung Health에 쓰기 권한을 요청하지 않습니다.

## 구조와 데이터 처리

- `core/`: Android에 의존하지 않는 모델, `RunMapper`, `SplitCalculator`, `RouteSimplifier`, 테스트.
- `app/src/main/`: 공통 UI, `SamsungHealthRepository` 경계와 메모리 상태.
- `app/src/samsung/`: 실제 SDK 1.1.0 어댑터, 권한 및 페이지별 읽기.
- `app/src/demo/`: DEMO 표시와 가상 기록 reader. 연결 실패를 데모 데이터로 대체하지 않습니다.
- `scripts/check-web-contract.mjs`: Android 생성 JSON을 기존 웹 Zod 스키마로 검증.

SDK 요약의 distance/duration/meanSpeed/meanHeartRate/maxHeartRate/meanCadence를 우선 사용합니다.
거리 누락은 `거리 없음`으로 표시하고 JSON 변환은 거부합니다.
심박은 UI에서 존재하는 값을 각각 표시하며 v1 JSON에는 average/max가 모두 유효할 때만 포함합니다.
기록 offset이 없으면 UTC로 표시하고 안내합니다. SDK UID와 세션 시작 시각·타입은 메모리 식별에만 사용합니다.

### 1 km 구간

제공된 split → 원본 누적 거리/운동 시간 → 조건을 만족하는 원본 GPS 순서입니다.
SDK 1.1.0의 공개 ExerciseSession API에는 split·누적 거리 시계열·pause 구간이 없어 실제 연결에서는 GPS를 사용합니다.
1,000m 경계를 선형 보간하고 마지막 부분 구간도 보존합니다.
알려진 pause는 activeSeconds에서 제외합니다. 이동 중 30초보다 큰 sample 공백·역행·비유한 값은 거부합니다.

GPS 계산은 전체 시작/끝을 1초 이내로 덮고 운동 시간과 전체 경과 시간 차이가 1초 이내인 기록에 한정합니다.
GPS와 요약 거리 차이가 `max(50m, 5%)`보다 크면 구간을 생략합니다.
GPS 구간은 오차가 있는 계산값임을 표시하며 요약 거리에 맞춰 임의 배율로 보정하지 않습니다.
Pause 위치가 불명확한 기록, 실내/누락 시계열에는 추측한 구간을 만들지 않고 사유를 표시합니다.

### 경로 단순화와 지도

**원본으로 split을 계산한 뒤** 저장·표시용 경로만 Douglas–Peucker로 단순화합니다.
구면상의 미터 거리, 기본 오차 5m, 화면에서 3–10m 조정이 가능합니다.
시작·종료점을 유지하고 위치 masking이나 고정 점 개수 제한은 하지 않습니다.
유효하지 않은 좌표가 섞이면 점을 임의로 연결하지 않고 경로 표시를 생략합니다.
지도는 osmdroid / OpenStreetMap이며 타일 사용 시 네트워크가 필요합니다.
원본 route와 건강 기록은 파일·로그·GitHub에 기록하지 않습니다. 지도 타일은 앱 캐시에 저장됩니다.

## 실기기 완료 체크

- 운동·경로 허용 / 운동 거부 / 경로만 거부 후 재요청
- 최근 실외·실내 기록 목록 및 Samsung Health 요약값 대조
- 경로 시작·종료점, 형태, 허용 오차 변경
- Pause 기록의 구간 생략과 불완전 기록 안내
- 화면 회전·뒤로가기·연결 실패 후 재시도

2026-09-08 SM-S926N 실기기에서 demo/samsung APK 설치·실행, 실제 Samsung Health 권한 상태 확인,
최근 기록 목록, 한 실외 기록의 상세·경로·심박·케이던스 표시를 확인했습니다. 지도 위 한 손가락 세로 드래그로
상세 하단까지 스크롤되는 것도 확인했습니다. 권한 거부·경로만 거부 후 재요청, 실내 기록, 실제 pause 사례,
회전과 여러 기록의 split 조건은 아직 확인하지 않았습니다.
컴파일·JVM 테스트 결과와 위 실기기 확인 범위를 구분합니다.
Phase 3의 PAT / Keystore / GitHub commit / 중복 방지 / Published 상태는 미구현입니다.

## 공식 API 근거 (2026-09-08 확인)

- [SDK 요구사항과 다운로드](https://developer.samsung.com/health/data/overview.html)
- [개발 모드](https://developer.samsung.com/health/data/guide/developer-mode.html)
- [ExerciseSession 필드](https://developer.samsung.com/health/data/api-reference/-shd/com.samsung.android.sdk.health.data.data.entries/-exercise-session/index.html)
- [ExerciseLocation](https://developer.samsung.com/health/data/api-reference/-shd/com.samsung.android.sdk.health.data.data.entries/-exercise-location/index.html)
- [권한](https://developer.samsung.com/health/data/api-reference/-shd/com.samsung.android.sdk.health.data.permission/-permission/index.html)
- [페이지 요청](https://developer.samsung.com/health/data/api-reference/-shd/com.samsung.android.sdk.health.data.request/-read-data-request/-dual-time-builder/index.html)
