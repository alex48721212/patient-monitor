# Patient Monitor

실시간 환자 바이탈 모니터링 백엔드 (Java 21 · Spring Boot 3.3 · JdbcTemplate + SQL · H2/MySQL)

## 실행

> 켜기·끄기, 주요 명령어, API 호출 예시, 문제 해결은 **[사용 가이드](docs/GUIDE.md)** 에 정리되어 있습니다.

**방법 1 — IntelliJ (권장):** `PatientMonitorApplication` 파일을 열고 `main` 옆 ▶ 버튼 → 브라우저에서 http://localhost:8080

**방법 2 — 터미널 (Windows):**

```bash
.\run.cmd
```

`run.cmd` 는 `JAVA_HOME` 이 없으면 IntelliJ 가 받아둔 `%USERPROFILE%\.jdks` 의 JDK 21 을 찾아 씁니다.
`./gradlew bootRun` 을 직접 쓰려면 `JAVA_HOME` 이 설정되어 있어야 합니다.
서버 종료는 터미널에서 Ctrl+C.

- 기본은 H2 인메모리 DB → 설치 없이 실행, 재시작하면 초기화
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:patientdb`, user `sa`)
- MySQL: `CREATE DATABASE patientdb;` 후 `./gradlew bootRun --args="--spring.profiles.active=mysql"`
  (계정은 환경변수 `DB_USERNAME`, `DB_PASSWORD`)

## 실시간 대시보드

실행 후 http://localhost:8080 접속.

- 입원 환자별 카드에 HR · SpO₂ · NIBP · RR · TEMP 최신값과 최근 2분 추이 그래프
- 정상 범위를 벗어난 항목은 주황(주의) / 빨강 깜빡임(위급), 위급 알람은 화면 하단 토스트
- 오른쪽 패널에서 미확인 알람 확인 처리, 카드에서 환자별 일괄 확인 / 퇴원
- 10초 이상 측정값이 안 오면 카드가 흐려지고 "신호 없음" 표시
- 연결이 끊기면 자동 재연결 후 놓친 데이터를 다시 불러옴

서버는 `/ws/monitor` WebSocket 으로 아래 메시지를 보냅니다 (DB 커밋 이후에만 전송).

| type | data |
|---|---|
| `vital` | 새 측정값 |
| `alarm` | 새 알람 (환자 이름/병실 포함) |
| `patients-changed` | 환자 등록·수정·퇴원·삭제 → 목록 다시 조회 |
| `alarms-changed` | 알람 확인 처리 → 알람 다시 조회 |

## 구조

```
patient/  환자 등록·조회·수정·퇴원·삭제
vital/    바이탈 저장/조회/집계 + VitalSimulator(2초마다 입원 환자 측정값 생성)
alarm/    정상 범위 비교(AlarmEvaluator) → 알람 저장/조회/확인
realtime/ WebSocket 핸들러와 커밋 후 브로드캐스트(MonitorBroadcaster)
resources/schema.sql, data.sql   테이블 정의와 샘플 환자
resources/static/                대시보드 (index.html, dashboard.js, dashboard.css)
```

정상 범위와 시뮬레이터 주기/이상치 확률은 `application.yml` 의 `monitor.*` 에서 바꿉니다.
범위를 벗어나면 WARNING, 경계에서 10% 이상 벗어나면 CRITICAL.

## API

모든 API 는 로그인(세션 쿠키)이 필요하고, 쓰기 요청에는 CSRF 토큰(`X-XSRF-TOKEN` 헤더)이 필요합니다.

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/patients?status=ADMITTED` | 환자 목록 |
| GET | `/api/patients/{id}` | 환자 상세 |
| POST | `/api/patients` | 환자 등록 |
| PUT | `/api/patients/{id}` | 환자 수정 |
| POST | `/api/patients/{id}/discharge` | 퇴원 (시뮬레이터 대상에서 제외) |
| DELETE | `/api/patients/{id}` | 삭제 (바이탈·알람 함께 삭제) |
| POST | `/api/patients/{id}/vitals` | 측정값 입력 (장비 연동용) |
| GET | `/api/patients/{id}/vitals?limit=50` | 최근 측정값 (`from`,`to` 로 기간 조회) |
| GET | `/api/patients/{id}/vitals/latest` | 최신 측정값 |
| GET | `/api/patients/{id}/vitals/summary?minutes=10` | 최근 N분 평균/최소/최대 |
| GET | `/api/alarms?acknowledged=false&severity=CRITICAL&patientId=1` | 알람 검색 |
| GET | `/api/alarms/stats` | 환자별 미확인 알람 수 |
| POST | `/api/alarms/{id}/ack` | 알람 확인 |
| POST | `/api/alarms/patients/{patientId}/ack` | 환자 알람 일괄 확인 |

## 참고: 한글 사용자 폴더 문제

Windows 사용자 폴더명이 한글이면 Gradle 테스트 워커가 classpath 를 못 읽어
`ClassNotFoundException: GradleWorkerMain` 으로 실패합니다.
`~/.gradle/gradle.properties` 에 `org.gradle.jvmargs=-Dfile.encoding=MS949` 를 넣어 해결합니다 (이 PC 에는 설정됨).
