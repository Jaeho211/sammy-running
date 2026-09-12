# Father-Son Running Log

> 2026-09-08 후속 결정: 제품 이름은 **RUN LOG**로 변경한다. 웹은 초등학교 5학년이 친구에게 보여줘도
> 유치하지 않은 중립적인 스포츠 기록 화면으로 구성하며, 아빠·아들 관계를 전면 문구로 강조하지 않는다.
> 러닝별 Title/Comment 입력과 JSON 필드는 사용하지 않는다. 아래 최초 예시의 관련 항목보다 이 결정을 우선한다.

아들과 함께 달린 기록을 모아서 iPad에서 볼 수 있는 개인용 러닝 로그 웹사이트를 만들고 싶다.

전체 시스템은 가능한 한 단순하게 유지한다.

## Goal

Galaxy Watch로 기록한 Samsung Health 달리기 기록 중에서 내가 직접 선택한 기록만 GitHub repository에 저장하고, GitHub Pages로 배포되는 웹사이트에서 보여준다.

별도의 backend server나 database는 사용하지 않는다.

전체 구조는 다음과 같다.

```text
Galaxy Watch
  ↓
Samsung Health
  ↓
Android companion app
  ↓
사용자가 함께 달린 기록 선택
  ↓
GitHub REST API
  ↓
repository의 data/runs/*.json
  ↓
static web app build
  ↓
GitHub Pages
  ↓
iPad Safari / PWA
```

---

# Repository structure

가능하면 하나의 repository 안에 Android 앱과 web 앱을 같이 관리한다.

예:

```text
father-son-running/
├── android/
├── web/
├── data/
│   └── runs/
├── .github/
│   └── workflows/
├── README.md
└── docs/
```

필요하면 더 좋은 monorepo 구조로 변경해도 된다.

---

# 1. Android App

Kotlin으로 구현한다.

목적은 Samsung Health에 저장된 달리기 기록을 읽고, 내가 아들과 같이 달린 기록만 선택해서 GitHub에 publish하는 것이다.

Android 앱 자체는 최소한의 UI만 제공한다.

## Main screen

최근 running exercise들을 최신순으로 보여준다.

각 항목에는 최소한 다음 정보를 표시한다.

* date / start time
* distance
* duration
* average pace

예:

```text
Recent Runs

2026-09-07
3.24 km
23:51
7'22"/km

2026-09-05
5.10 km
36:20
7'07"/km
```

한 기록을 선택하면 상세 화면으로 이동한다.

---

# 2. Run detail screen

다음 정보를 보여준다.

* 날짜
* 시작 시각
* distance
* duration
* average pace
* 1 km splits
* route preview
* heart rate가 존재하면 average / max heart rate
* cadence가 존재하면 average cadence

별도 제목이나 메모 입력 없이 게시 버튼만 제공한다.

```text
[ Publish ]
```

별도의 `withDad` checkbox는 필요 없다.

Publish 버튼을 누른 기록 자체가 "아들과 함께 달린 기록"이라는 의미이다.

---

# 3. Samsung Health integration

Samsung Health Data SDK를 사용해서 running exercise session을 읽는다.

가능한 경우 다음 데이터를 가져온다.

* exercise start/end time
* duration
* distance
* GPS route
* heart rate
* cadence
* speed

Samsung Health API에서 이미 제공하는 값은 재계산하지 않아도 된다.

하지만 1 km split이 직접 제공되지 않는 경우 GPS/time-series data를 이용해서 계산한다.

페이스 단위는 기본적으로:

```text
min/km
```

를 사용한다.

---

# 4. Run JSON schema

GitHub에는 한 번의 running session을 하나의 JSON 파일로 저장한다.

예:

```json
{
  "schemaVersion": 1,
  "id": "2026-09-07T080231",
  "date": "2026-09-07",
  "startTime": "2026-09-07T08:02:31+09:00",

  "distanceMeters": 3241,
  "durationSeconds": 1431,

  "averagePaceSecondsPerKm": 441,

  "splits": [
    {
      "distanceMeters": 1000,
      "durationSeconds": 461
    },
    {
      "distanceMeters": 1000,
      "durationSeconds": 444
    },
    {
      "distanceMeters": 1000,
      "durationSeconds": 418
    },
    {
      "distanceMeters": 241,
      "durationSeconds": 108
    }
  ],

  "heartRate": {
    "average": 132,
    "max": 158
  },

  "cadence": {
    "average": 156
  },

  "route": [
    {
      "lat": 37.5101,
      "lng": 127.0402
    }
  ]
}
```

Optional data가 없는 경우 null을 저장하기보다는 해당 field 자체를 생략해도 된다.

---

# 5. GPS route

GitHub에는 Samsung Health에서 읽은 유효한 GPS point를 원래 순서와 개수 그대로 저장한다.

시작점/종료점을 제거하거나 위치를 숨길 필요는 없다.

경로 단순화나 고정 point count 제한을 적용하지 않는다.
JSON에는 lat/lng만 저장하고 좌표별 timestamp와 원본 Samsung Health export는 제외한다.

---

# 6. GitHub publishing

Android 앱의 Publish 버튼을 누르면 GitHub REST API를 사용해 repository에 JSON 파일을 commit한다.

파일 경로:

```text
data/runs/YYYY-MM-DDTHHMMSS.json
```

예:

```text
data/runs/2026-09-07T080231.json
```

Commit message:

```text
Add run: 2026-09-07 3.24km
```

같은 형식을 사용한다.

같은 run을 실수로 두 번 publish하지 않도록 한다.

Samsung Health exercise/session의 stable identifier가 있다면 local state에 저장해서 published 여부를 관리한다.

stable identifier가 없다면 다음 조합으로 deterministic ID를 만든다.

```text
startTime + distance + duration
```

Android 앱에서 최근 기록 목록에 이미 publish된 기록이면:

```text
Published ✓
```

라고 표시한다.

---

# 7. GitHub authentication

이 앱은 개인 sideload 용도다.

별도의 OAuth backend는 만들지 않는다.

GitHub fine-grained PAT를 사용한다.

PAT는 repository Contents read/write 권한만 가지도록 한다.

Token을 source code에 hard-code하지 않는다.

Android Keystore를 이용해서 안전하게 저장한다.

처음 실행 시 Settings 화면에서 token과 다음 값을 입력할 수 있게 한다.

```text
GitHub owner
GitHub repository
GitHub branch
GitHub token
```

기본 branch는 `main`.

---

# 8. Web application

웹사이트는 iPad Safari에서 보는 것이 주 목적이다.

React 기반으로 구현한다.

Vite를 우선 고려한다.

Next.js가 꼭 필요한 이유가 없다면 사용하지 않는다.

웹사이트는 완전히 static하게 build 가능해야 한다.

GitHub Pages에서 동작해야 한다.

---

# 9. Web pages

MVP에서는 다음 3개 화면 정도면 충분하다.

## Home / Dashboard

아들이 보고 달리기에 흥미를 느끼는 것이 가장 중요하다.

예:

```text
🏃 Our Running Adventure

Total Distance
42.7 km

Runs Together
12

Longest Run
5.2 km

Best 1K
6:42
```

그리고 최근 달리기 3~5개를 보여준다.

---

## Runs

전체 러닝 기록을 최신순으로 보여준다.

예:

```text
Sep 7

🏃 3.24 km
⏱ 23:51
⚡ 7'22"/km
```

카드를 누르면 detail 화면으로 이동한다.

---

## Run Detail

다음 정보를 표시한다.

```text
Sep 7 · 08:02

3.24 km
23:51
7'22"/km

Splits

1 km   7:41
2 km   7:24
3 km   6:58 ⭐
0.24   1:48
```

그리고 GPS route를 map 위에 표시한다.

MapLibre GL JS 또는 Leaflet을 사용할 수 있다.

가능하면 API key가 필요 없는 지도 tile/source 구성을 우선한다.

---

# 10. Child-friendly UX

일반적인 Strava clone처럼 만들지 않는다.

대상은 초등학생이다.

"운동 분석"보다 "성장과 성취"가 중심이다.

Dashboard에서 다음을 강조한다.

* 함께 달린 횟수
* 누적 거리
* 가장 긴 달리기
* 가장 빠른 1 km
* 지난번보다 좋아진 기록
* milestone

예:

```text
🏆 New Record!

처음으로 1km를
7분 안에 달렸어요!

6:58
```

---

# 11. Achievements

MVP에서도 간단한 achievement 계산 구조를 만들어 둔다.

JSON에 achievement를 저장할 필요는 없다.

현재 모든 run 데이터를 기반으로 web build/runtime에서 계산해도 된다.

예:

```text
FIRST_RUN
FIRST_1K
FIRST_3K
FIRST_5K

TOTAL_10K
TOTAL_25K
TOTAL_50K
TOTAL_100K

RUNS_5
RUNS_10
RUNS_20

FASTEST_1K
LONGEST_RUN
```

처음부터 너무 복잡한 gamification 시스템은 만들지 않는다.

확장 가능한 구조만 만든다.

---

# 12. Static data loading

별도의 API server는 없다.

웹앱 build 시 또는 client side에서 repository에 저장된 JSON 파일들을 읽는다.

가능하면 GitHub REST API를 매번 호출하지 않는다.

권장 방식은 build 단계에서:

```text
data/runs/*.json
```

을 읽어 하나의 static artifact를 만드는 것이다.

예:

```text
web/public/generated/runs.json
```

또는 Vite에서 JSON을 직접 import할 수 있는 구조도 괜찮다.

중요한 것은 production 사이트가 GitHub API token을 필요로 해서는 안 된다는 점이다.

---

# 13. GitHub Actions

main branch가 update되면 자동으로 웹사이트를 build하고 GitHub Pages에 deploy한다.

즉 Android에서 Publish:

```text
Publish
  ↓
JSON commit
  ↓
GitHub Actions triggered
  ↓
Vite build
  ↓
GitHub Pages deploy
```

가 자동으로 이어져야 한다.

---

# 14. PWA

iPad에서 홈 화면에 추가해서 앱처럼 사용할 수 있게 최소한의 PWA 설정을 한다.

필요 항목:

* manifest
* application name
* icons placeholder
* standalone display
* theme/background metadata

offline-first 구현까지는 MVP에서 필요 없다.

---

# 15. Privacy

사이트가 public GitHub Pages가 될 가능성이 있으므로 다음 정보는 표시하지 않는다.

* 정확한 집 주소 같은 textual location
* Samsung Health account information
* 이름 외 개인정보
* 원본 raw Samsung Health export

GPS route 자체는 표시해도 된다.

시작점과 종료점을 제거하거나 masking할 필요는 없다.

GitHub에는 전체 GPS 좌표를 저장하지만 좌표별 timestamp와 원본 export는 저장하지 않는다.

---

# 16. Engineering requirements

코드는 향후 확장이 쉽도록 작성한다.

특히 다음 영역을 분리한다.

Android:

```text
SamsungHealthRepository
RunMapper
SplitCalculator
GitHubPublisher
PublishedRunStore
```

Web:

```text
RunRepository
statistics
achievements
formatters
map components
```

domain model과 UI code를 섞지 않는다.

---

# 17. Tests

적어도 순수 로직 부분에는 unit test를 작성한다.

특히:

```text
SplitCalculator
pace calculation
aggregate statistics
achievement calculation
```

GPS 데이터가 없는 indoor run이나 incomplete data도 crash하지 않도록 한다.

---

# 18. Sample data

실제 Samsung Health 연결 없이도 web 개발이 가능하도록 sample run JSON을 5개 정도 만든다.

서로 다른 기록을 사용해서 다음 상태를 테스트할 수 있게 한다.

* 첫 달리기
* 3 km 달리기
* 5 km 달리기
* 새로운 best 1K
* 새로운 longest run

route도 서울의 임의의 fake GPS coordinates를 사용한다.

실제 개인 위치는 사용하지 않는다.

---

# 19. Implementation order

한 번에 모든 기능을 만들려고 하지 말고 아래 순서로 진행한다.

## Phase 1

Web-only prototype.

* repository structure
* sample JSON
* Dashboard
* Runs list
* Run detail
* route map
* statistics
* achievements
* GitHub Pages deployment
* PWA

여기까지 먼저 완성한다.

## Phase 2

Android Samsung Health reader.

* Samsung Health permission
* recent running sessions
* run detail
* split calculation
* GPS route
* GPS route

## Phase 3

GitHub publishing.

* PAT setup
* GitHub Contents API
* Publish
* duplicate prevention
* Published status
* end-to-end deployment validation

---

# 20. README

README에는 반드시 다음을 문서화한다.

* project purpose
* architecture
* local web development
* Android build instructions
* Samsung Health setup
* GitHub PAT creation/configuration
* GitHub Pages configuration
* JSON schema
* GPS route 저장 방식

---

# Important constraints

다음 사항은 지켜라.

1. Supabase/Firebase/database를 추가하지 않는다.
2. 별도의 backend server를 추가하지 않는다.
3. iOS/iPad native app을 만들지 않는다.
4. iPad는 GitHub Pages 웹사이트만 사용한다.
5. 내가 혼자 달린 기록을 자동으로 판단하려 하지 않는다.
6. Android에서 내가 Publish한 기록만 아들과 함께 달린 기록이다.
7. 전체 GPS lat/lng는 저장하되 좌표별 timestamp와 원본 Samsung Health export는 저장하지 않는다.
8. 시작/종료 지점 privacy masking은 하지 않는다.
9. GPS 좌표는 단순화하지 않고 원래 순서와 개수를 유지한다.
10. 처음부터 복잡한 authentication/user account 시스템을 만들지 않는다.
11. MVP를 과도하게 확장하지 않는다.

먼저 현재 repository 상태를 확인한 다음, Phase 1부터 구현하라.

구현하면서 합리적인 세부사항은 스스로 결정하되, 위 architecture나 핵심 constraint를 변경해야 할 경우에는 임의로 변경하지 말고 이유를 설명하라.

각 phase가 끝날 때마다:

* 구현한 내용
* 주요 파일
* 실행 방법
* 남은 TODO
* 현재 known limitation

을 짧게 정리하라.
