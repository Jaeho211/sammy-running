# RUN LOG

선택한 러닝 기록을 iPad에서 확인하는 개인 러닝 로그입니다.
**Phase 1 웹, Phase 2 Android reader, Phase 3 GitHub Publish 구현.** 저장소에는 Android에서 직접 Publish한 실제 기록만 둡니다.
백엔드, 데이터베이스, 웹 인증이나 GitHub API 토큰 없이 정적으로 빌드됩니다.

## 다음 작업을 시작할 때

먼저 [작업 인수인계](docs/handoff.md)를 읽으세요. 완료·미검증 상태, 다음 구현 순서와 완료 기준을 정리했습니다.
[최초 요구사항](docs/requirements.md)은 전체 범위와 핵심 제약의 기준입니다.
데이터 형식은 [JSON 계약](docs/run-schema.md), Android 현황은 [Android 계획](android/README.md)을 참고하세요.

## 구조

```text
android/                 Kotlin Samsung Health reader, demo 빌드, 도메인 테스트
data/runs/*.json          1 session = 1 JSON
web/src/domain/           스키마, 통계, 배지, 포맷 및 테스트
web/src/RunRepository.js  정적 데이터 로딩
web/src/components/      Leaflet 경로 지도
web/scripts/             빌드 전 JSON 검증 및 병합
.github/workflows/        테스트 → Vite build → Pages
docs/                    데이터 계약
```

최종 흐름: Galaxy Watch → Samsung Health → Android에서 직접 선택/Publish →
GitHub Contents API → data/runs JSON commit → Actions → GitHub Pages → iPad.
Publish한 기록이 함께 달린 기록입니다. 혼자 달린 기록을 자동 분류하지 않습니다.

## 로컬 실행

Node.js 22.12 이상 (또는 24 LTS)에서 저장소 루트에서 실행합니다.

```sh
npm ci
npm run dev
npm test
npm run build
npm run preview
```

개발 기본 주소는 http://127.0.0.1:5173, preview는 http://127.0.0.1:4173 입니다.
데이터 파일을 변경하면 dev 서버를 다시 시작해 정적 데이터 묶음을 갱신합니다.
build는 스키마, 중복 ID, 파일명 일치를 검사하고 `web/public/generated/runs.json`을 생성합니다.
잘못된 데이터는 조용히 생략하지 않고 빌드를 실패시킵니다. 빈 data/runs도 지원합니다.
생성 파일은 Git에서 제외되며 배포 산출물에는 포함됩니다.

## GitHub Pages

1. 저장소 Settings → Pages → Build and deployment → Source를 **GitHub Actions**로 선택합니다.
2. 코드를 main에 push합니다. main 갱신마다 테스트/빌드/배포됩니다. PR에서는 빌드만 합니다.
3. Actions의 배포 URL에서 확인합니다. 이 저장소 기본 주소는 https://jaeho211.github.io/sammy-running/ 입니다.

Vite 상대 base와 hash 경로를 사용하므로 repository 하위 경로와 detail 새로고침을 지원합니다.
워크플로 파일 추가만으로 GitHub 설정이 변경되지는 않습니다. 실제 원격 배포 확인은 별도입니다.
설정 근거: [Vite Pages 가이드](https://vite.dev/guide/static-deploy),
[GitHub Pages workflow](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages).

## iPad / PWA

Safari로 배포 URL을 열고 공유 → 홈 화면에 추가합니다. standalone manifest,
192/512px 아이콘, 180px Apple touch icon, 테마 메타데이터를 포함합니다.
서비스 워커와 오프라인 캐시는 구현하지 않았습니다. 지도 타일과 선택적 Google Fonts는 인터넷이 필요하며,
폰트를 받지 못하면 시스템 폰트로 표시합니다. API 키가 필요 없는 OpenStreetMap 타일을 사용하고
저작자 표시를 유지합니다. GPS 없는 실내 기록은 지도 대신 안내를 표시합니다.

## 데이터·기록 배지

[데이터 계약](docs/run-schema.md)을 참고하세요. 최고 1 km는 정확히 1000m인 완전 구간만 비교합니다.
짧은 마지막 구간은 원래 거리와 소요 시간을 표시하고 최고 1 km에서 제외합니다.
최근 기록이 이전 모든 기록의 최고 1 km보다 빠르면 개선 배너를 표시합니다.
배지는 누적 데이터에서 계산하며 JSON에 저장하지 않습니다. 러닝별 제목이나 메모는 기록하지 않습니다.

## Android / Samsung Health (Phase 2)

Kotlin/Gradle 프로젝트, Samsung Health Data SDK 1.1.0 연결, 최근 90일 달리기 목록,
상세·지도·구간·심박·케이던스, 제목/메모 입력을 구현했습니다. Publish는 아직 비활성화되어 있습니다.
실제 SDK를 포함한 Samsung APK와 가상 기록 demo APK를 빌드할 수 있습니다.
[Android 설치·빌드 안내](android/README.md)에 JDK/SDK 설정, 개발용 Samsung Health 설정,
검증 명령과 실기기 확인 항목을 정리했습니다.

## GitHub 로그인 / Publish (Phase 3)

Android의 **GitHub 설정 → GitHub로 로그인**에서 GitHub App Device Flow로 인증합니다. 앱이 인증 코드를 복사하고
브라우저를 열므로 PAT를 직접 입력하지 않습니다. GitHub App은 `sammy-running`에만 설치하고 repository
**Contents: Read and write** 권한만 사용합니다. Client ID는 공개 앱 식별자로 빌드에 포함하지만 client secret이나
private key는 사용하지 않습니다. Access/refresh token은 Android Keystore의 AES-GCM 키로 암호화해 저장합니다.
만료된 access token은 유효한 refresh token으로 자동 갱신하며 설정 화면에서 로그아웃할 수 있습니다.
상세 화면에서 Publish하면 Contents API로 `data/runs/<id>.json`을 create-only commit하고, 원격 성공 확인 후에만
`Published ✓`를 저장·표시합니다. 원격의 같은 경로에 동일 JSON이 있으면 상태를 복구하고, 다른 내용이면 덮어쓰지 않습니다.

## GPS 경로 및 개인정보

Android는 GPS/time-series 원본으로 구간을 계산하고 유효한 GPS 좌표를 원래 순서와 개수 그대로 게시합니다.
시작/종료점도 보존하며 masking하지 않습니다. 좌표별 timestamp와 원본 Samsung Health export는 저장소에 넣지 않습니다.
Android demo flavor의 가상 기록은 테스트에만 사용하며 웹 저장소에는 실제 Publish 기록만 둡니다.
전체 GPS 좌표 보존과 구간 계산은 JVM 테스트로 검증합니다.
집 주소 텍스트, Health 계정 정보, 원본 export는 데이터 계약에 포함하지 않습니다.

## 남은 단계와 한계

- Phase 2: reader 코드·APK·핵심 로직 테스트와 실기기 기본 읽기 확인 완료. 남은 검증 범위는 [인수인계](docs/handoff.md) 참고.
- Phase 3: GitHub App 로그인 → 실제 기록 commit → Actions → Pages 반영과 Published 상태까지 실기기에서 확인했습니다.
- 실제 iPad 홈 화면 설치는 미검증입니다.
- 시계열 없는 데이터는 존재하는 요약만 표시하며 구간 기록을 추측하지 않습니다.
