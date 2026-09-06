# Run JSON v1

실행 가능한 계약: `web/src/domain/run.js`의 Zod 스키마.
파일명은 `data/runs/YYYY-MM-DDTHHMMSS.json`, `id`와 일치해야 합니다.

| 필드 | 단위 / 조건 |
| --- | --- |
| schemaVersion | 정수 1 |
| id | YYYY-MM-DDTHHMMSS, 전체에서 유일 |
| date | 유효한 YYYY-MM-DD, 기록 현지 날짜 |
| startTime | ISO 8601, timezone offset 포함 |
| title | 문자열, 최대 200자 |
| comment | 선택, 최대 4000자 |
| distanceMeters | 유한한 수, 0 이상 |
| durationSeconds | 유한한 수, 0 초과 |
| averagePaceSecondsPerKm | 선택, 0 초과. 없으면 거리/시간에서 계산 |
| splits | 선택, distanceMeters/durationSeconds 각각 0 초과 |
| heartRate | 선택, average/max bpm, 0 초과 |
| cadence | 선택, average spm, 0 초과 |
| route | 선택, 단순화된 lat/lng 배열, 위도 ±90 / 경도 ±180 |

선택 필드는 없으면 생략합니다. null은 사용하지 않습니다. route가 없거나 점이 두 개 미만이면 지도를 표시하지 않습니다.
split이 없으면 최고 1 km를 만들지 않습니다. 알려지지 않은 최상위 필드는 빌드 오류입니다.
거리 0 기록에는 계산 페이스가 없습니다. 구간/요약은 SDK가 제공하는 값이 다를 수 있어 합계를 강제하지 않습니다.
Phase 3에서는 session stable ID를 Android 로컬 상태로 관리하고 원격 파일 충돌도 확인해야 합니다.
동일 초에 다른 session이 시작하는 드문 파일명 충돌 시 덮어쓰지 말고 충돌을 알려야 합니다.
