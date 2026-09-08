# 작업 인수인계

최종 작성: 2026-09-08 (Asia/Seoul). 다음 작업에서 상태가 바뀌면 이 문서도 갱신합니다.

## 재개 지점

**Phase 1 웹과 Phase 2 Android Samsung Health reader 구현·로컬/실기기 기본 검증 완료.**
다음 개발 범위는 Phase 3 GitHub Publish입니다. Phase 2를 처음부터 다시 만들 필요는 없습니다.

- 저장소: https://github.com/Jaeho211/sammy-running
- 로컬: `E:\Gits\sammy-running`
- 브랜치: `main`
- Phase 1 커밋: `9b12ecf` — `Build Phase 1 father-son running log web prototype`
- Phase 2 변경은 아직 작업 트리에 있으며 커밋하지 않았습니다. 다른 변경을 덮어쓰지 말고 `git status`로 확인합니다.
- Samsung Health SDK AAR과 로컬 JDK는 Git 제외 경로에 있습니다.

## 먼저 읽을 문서

1. [최초 요구사항](requirements.md): Phase 1–3 전체 범위와 핵심 제약.
2. [README](../README.md): 전체 구조, 웹 실행, Pages/PWA, 예정된 PAT 구성.
3. [Run JSON 계약](run-schema.md): Android와 웹이 공유하는 데이터 형식.
4. [Android 안내](../android/README.md): 빌드, SDK 설치, 데이터 처리와 실기기 확인 항목.

## 완료한 것과 검증 범위

| 영역 | 현재 상태 |
| --- | --- |
| 웹 | RUN LOG 대시보드·전체 기록·상세·기록 배지, 정적 JSON, Leaflet/OSM, PWA 구현 |
| 웹 검증 | 도메인 테스트 6개와 production build 통과 |
| 배포 | GitHub Pages workflow 구현. 실제 Pages 활성화/배포 성공은 미확인 |
| Android core | 모델, `RunMapper`, `SplitCalculator`, `RouteSimplifier`, fixture와 JVM 테스트 구현 |
| Android reader | Samsung Health Data SDK 1.1.0 권한, 최근 90일 running/track running 페이지 읽기, 최신순 중복 제거 구현 |
| Android UI | 목록, 상세, OSM 경로, split 안내, 심박·케이던스, 3–10m 허용 오차 구현 |
| Android 빌드 | demo/samsung debug APK 및 두 flavor lint 통과(0 errors) |
| 계약 검증 | Android 생성 fixture 3개가 웹 Run JSON v1 스키마 통과 |
| 실기기 | SM-S926N에서 두 APK 설치·실행, demo 목록/상세, 실제 권한·최근 기록 목록·상세·경로·지표 렌더링 확인 |
| 아직 미검증 | 운동/경로 권한 거부·재요청, 실내 기록, 화면 회전, 실제 pause 사례, 여러 실제 기록의 split 조건 |

2026-09-08 실기기 확인 중 지도가 세로 스크롤을 가로채는 문제를 수정했습니다. 이제 한 손가락 세로 드래그는
상세 페이지를 스크롤하고, 가로 드래그와 두 손가락 제스처는 지도에 전달됩니다. 실제로 지도 아래의 split 안내,
split 안내와 비활성 Publish 버튼까지 스크롤되는 것을 확인했습니다.

## Phase 2 구현 결정

- SDK가 제공한 요약 distance/duration/speed/heart rate/cadence를 우선 사용합니다.
- split은 제공 split → 원본 누적 거리/active time → 조건을 충족하는 원본 GPS 순서로 계산합니다.
- pause 위치, 전체 시간 범위 또는 거리 일치가 불충분하면 split을 추측하지 않고 이유를 표시합니다.
- 원본으로 split을 계산한 뒤 route에 구면 Douglas–Peucker를 적용합니다. 기본 5m, UI에서 3–10m 조정합니다.
- 유효하지 않은 좌표가 섞이면 점을 이어 붙이지 않고 전체 경로 표시를 생략합니다.
- SDK ID와 원본 시계열은 JSON에 넣지 않습니다. Android 생성 JSON은 웹 v1 계약으로 교차 검증합니다.
- 러닝별 title/comment는 수집하거나 JSON에 저장하지 않습니다. Phase 3 전에는 Publish 성공 상태를 표시하지 않습니다.

## 다시 검증하는 명령

루트 PowerShell에서 JDK/SDK 환경을 설정한 뒤 실행합니다.

```powershell
$env:JAVA_HOME = (Get-ChildItem .tools/jdk -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = 'C:\Android\sdk'
.\android\gradlew.bat -p android :core:test :app:assembleDemoDebug :app:lintDemoDebug :app:assembleSamsungDebug :app:lintSamsungDebug
node android/scripts/check-web-contract.mjs
npm test
npm run build
```

## 다음 작업 순서: Phase 3

1. Settings에 owner/repository/branch(기본 `main`)/fine-grained PAT 입력을 추가합니다.
2. PAT를 Android Keystore 기반 암호화 저장소에 보관합니다. 코드·로그·JSON에는 절대 넣지 않습니다.
3. `GitHubPublisher`를 만들고 Contents API로 `data/runs/YYYY-MM-DDTHHMMSS.json`을 커밋합니다.
4. 커밋 메시지는 `Add run: YYYY-MM-DD 3.24km` 형식을 사용합니다.
5. `PublishedRunStore`에서 stable session ID, 없으면 startTime+distance+duration 조합으로 중복을 막습니다.
6. 중복 탭, 네트워크 실패 후 재시도, 원격 파일 존재, 동일 초 파일명 충돌을 처리합니다.
7. 원격 성공을 확인한 뒤에만 `Published ✓`를 표시합니다.
8. Publish → Actions → Pages → iPad 흐름을 검증합니다.

## 계속 지킬 제약과 알려진 한계

- 단일 monorepo이며 backend/database를 추가하지 않습니다. iPad는 정적 웹만 사용합니다.
- 사용자가 Publish한 기록만 함께 달린 기록입니다. 자동 분류나 `withDad` 체크박스는 없습니다.
- 원본 GPS/export/계정 정보는 저장소에 넣지 않습니다. 단순화 경로의 시작·종료점은 유지합니다.
- 웹 최고 1 km는 `distanceMeters === 1000`인 완전 구간만 비교합니다.
- Samsung Health SDK는 실제 휴대폰과 개발용 Data Read 설정이 필요합니다.
- GPS가 세션 전체 시간 범위를 덮지 않거나 pause 위치를 알 수 없는 실제 기록은 route가 보여도 split이 생략될 수 있습니다.
- SDK/AndroidX 최신 버전 알림 등 lint warning 5개가 있으나 lint error는 없습니다.
- 실제 iPad 홈 화면 설치와 원격 Pages 성공은 아직 확인하지 않았습니다.
- Phase 3 PAT/commit/중복 방지/Published 상태는 미구현입니다.
