# Android companion — Phase 2 / 3 TODO

아직 Android 앱이나 Gradle 프로젝트는 구현하지 않았습니다. 현재 빌드할 APK는 없습니다.

Phase 2: Kotlin 앱, Samsung Health Data SDK 권한 및 최근 running 조회, 상세 화면.
도메인 경계: `SamsungHealthRepository`, `RunMapper`, `SplitCalculator`, `RouteSimplifier`.
SplitCalculator는 원본 시간/거리 시계열에서 1 km 경계를 보간하고, pause와 누락 데이터를 처리합니다.
원본으로 구간을 계산한 다음 route만 Douglas–Peucker로 단순화합니다.
기본 tolerance는 5m (설정 가능, 권장 조정 범위 3–10m), 시작/종료점 유지, 고정 점 개수 강제 없음.

Phase 3: `GitHubPublisher`, `PublishedRunStore`, Settings, Keystore 암호화 PAT,
사용자가 선택한 기록만 Publish, stable session ID 기반 중복 방지와 원격 파일 충돌 처리.

SDK 배포 방식과 기기/앱 요구사항은 구현 시 Samsung 공식 문서에서 확인해야 합니다.
Samsung Health 권한은 앱 내부에서 요청하고 원본 export/account 정보는 repository에 저장하지 않습니다.
