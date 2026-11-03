# 사용 가이드

환자 모니터링 서버를 켜고, 확인하고, 끄는 방법과 자주 쓰는 명령어 모음입니다.
명령어는 모두 **프로젝트 폴더**(`IdeaProjects\patient-monitor`)에서 실행합니다.

---

## 1. 서버 켜기

### 방법 A — IntelliJ (가장 간단)

1. `src/main/java/com/patientmonitor/PatientMonitorApplication.java` 열기
2. `main` 옆 초록색 ▶ 클릭 → **Run 'PatientMonitorApplication'**
3. 아래 Run 창에 `Started PatientMonitorApplication` 이 나오면 준비 완료
4. 브라우저에서 http://localhost:8080

> 한 번 실행하고 나면 오른쪽 위 실행 버튼(▶) 이나 `Shift+F10` 으로 다시 실행할 수 있습니다.

### 방법 B — 터미널

```bash
.\run.cmd
```

- 빌드 → 서버 실행까지 한 번에 합니다. 처음에는 30초~1분 정도 걸립니다.
- `Started PatientMonitorApplication` 이 보이면 http://localhost:8080 접속
- Java 를 PATH 에 등록하지 않아도 IntelliJ 가 받아둔 JDK 21 을 자동으로 찾습니다.
- 이 터미널 창은 서버가 쓰고 있으니 닫지 말고 그대로 두세요.

### 로그인

대시보드를 쓰려면 로그인해야 합니다.

- 아이디: `admin`
- 비밀번호: 서버를 시작할 때 터미널(또는 IntelliJ Run 창)에 나오는 `임시 비밀번호`
- 기본 H2 는 재시작하면 초기화되므로 임시 비밀번호도 매번 바뀝니다. 고정하려면 실행 전에 환경변수를 지정하세요.

```powershell
$env:ADMIN_PASSWORD = '원하는 비밀번호'
.\run.cmd
```

### 회원가입과 계정 찾기

로그인 화면 아래 링크에서 이용합니다.

| 기능 | 동작 |
|---|---|
| 회원가입 | 가입 신청만 접수됩니다. `admin` 이 대시보드의 **가입 승인** 버튼에서 승인해야 로그인할 수 있습니다. |
| 아이디 찾기 | 이름과 이메일이 일치하면 가입한 이메일로 아이디를 보냅니다. |
| 비밀번호 찾기 | 아이디와 이메일이 일치하면 6자리 인증 코드를 보냅니다. 코드는 10분 유효, 5번 틀리면 잠기고, 재발급은 1분에 한 번입니다. |

> **아직 메일 서버가 연결되어 있지 않습니다.** 아이디와 인증 코드는 실제 메일 대신 서버 로그(터미널 또는 IntelliJ Run 창)에 `[메일 대신 로그]` 로 출력됩니다. 실제 운영 전에는 메일 발송 구현으로 바꿔야 합니다.

권한은 두 가지입니다.

| 권한 | 할 수 있는 것 |
|---|---|
| 일반 (`USER`) | 대시보드와 환자·바이탈·알람 API |
| 관리자 (`ADMIN`) | 일반 권한 전부 + 가입 승인/거절, H2 콘솔 |

### 실행 옵션

`run.cmd` 뒤에 붙이면 됩니다. (IntelliJ 에서는 Run → Edit Configurations → Program arguments 에 입력)

| 옵션 | 설명 |
|---|---|
| `--monitor.simulator.enabled=false` | 시뮬레이터 끄기 (측정값 자동 생성 중지) |
| `--monitor.simulator.spike-probability=0.3` | 이상치 발생 확률 올리기 (기본 0.05) → 알람 테스트용 |
| `--monitor.simulator.interval-ms=1000` | 측정 주기 1초로 변경 (기본 2000) |
| `--server.port=9090` | 다른 포트로 실행 |
| `--spring.profiles.active=mysql` | MySQL 사용 (아래 6번 참고) |

예) 알람이 자주 뜨게 실행:

```bash
.\run.cmd --monitor.simulator.spike-probability=0.3
```

---

## 2. 서버 끄기

| 상황 | 방법 |
|---|---|
| IntelliJ 로 켰을 때 | Run 창의 빨간 ■ (Stop) 버튼, 또는 `Ctrl+F2` |
| `run.cmd` 로 켰을 때 | 그 터미널에서 `Ctrl+C` → `Terminate batch job (Y/N)?` (일괄 작업을 끝내시겠습니까) 가 나오면 `Y` 입력 |
| 터미널을 닫아버렸거나 어디서 켰는지 모를 때 | 아무 터미널에서 `.\stop.cmd` |

```bash
.\stop.cmd
```

`stop.cmd` 는 8080 포트를 쓰고 있는 프로세스를 찾아 종료합니다.

---

## 3. 접속 주소

| 주소 | 내용 |
|---|---|
| http://localhost:8080 | 실시간 대시보드 |
| http://localhost:8080/h2-console | DB 직접 조회 (SQL 실행) |
| http://localhost:8080/api/patients | 환자 목록 (JSON) |
| http://localhost:8080/api/alarms?acknowledged=false | 미확인 알람 (JSON) |

### H2 콘솔 로그인 값

| 항목 | 값 |
|---|---|
| JDBC URL | `jdbc:h2:mem:patientdb` |
| User Name | `sa` |
| Password | (비워둠) |

H2 콘솔은 대시보드에 `admin` 으로 로그인한 상태에서만 열립니다.

로그인 후 SQL 예시:

```sql
-- 환자별 측정 건수와 평균 심박수
SELECT p.name, COUNT(*) AS cnt, AVG(v.heart_rate) AS avg_hr
FROM patient p JOIN vital_sign v ON v.patient_id = p.id
GROUP BY p.name;

-- 위급 알람 최근 10건
SELECT * FROM alarm WHERE severity = 'CRITICAL' ORDER BY created_at DESC LIMIT 10;
```

> 기본 H2 는 메모리 DB 라서 **서버를 끄면 데이터가 모두 초기화**됩니다. 다시 켜면 샘플 환자 3명부터 시작합니다.

---

## 4. 주요 명령어

| 명령어 | 용도 |
|---|---|
| `.\run.cmd` | 빌드 후 서버 실행 |
| `.\stop.cmd` | 8080 포트 서버 종료 |
| `.\gradlew.bat test` | 테스트 전체 실행 (결과: `build\reports\tests\test\index.html`) |
| `.\gradlew.bat bootJar` | 실행용 jar 만들기 (`build\libs\patient-monitor-0.1.0.jar`) |
| `.\gradlew.bat clean` | 빌드 결과물 삭제 |
| `git status` | 변경된 파일 확인 |
| `git log --oneline` | 커밋 기록 보기 |
| `git add -A` → `git commit -m "메시지"` | 변경 내용 커밋 |

> `gradlew.bat` 를 터미널에서 직접 쓸 때는 `JAVA_HOME` 이 설정돼 있어야 합니다 (7번 참고).
> 테스트는 IntelliJ 에서 `src/test/java` 폴더 우클릭 → **Run 'All Tests'** 로도 실행할 수 있습니다.

---

## 5. API 를 터미널에서 호출해보기 (PowerShell)

서버가 켜진 상태에서 **다른 터미널**을 열고 실행합니다.
API 도 로그인이 필요하므로, 먼저 로그인해서 세션(`$s`)을 만듭니다.
쓰기 요청(POST 등)에는 CSRF 토큰(`$h`)도 함께 보냅니다.

```powershell
# 로그인 (비밀번호는 입력창에 직접 입력)
$cred = Get-Credential -UserName admin -Message '환자 모니터링 로그인'
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null = Invoke-WebRequest -UseBasicParsing http://localhost:8080/login.html -WebSession $s
$t = ($s.Cookies.GetCookies('http://localhost:8080') | Where-Object Name -eq 'XSRF-TOKEN').Value
$null = Invoke-WebRequest -UseBasicParsing -Method Post http://localhost:8080/login -WebSession $s -Body @{ username = $cred.UserName; password = $cred.GetNetworkCredential().Password; _csrf = $t }
Invoke-RestMethod http://localhost:8080/api/me -WebSession $s
$h = @{ 'X-XSRF-TOKEN' = ($s.Cookies.GetCookies('http://localhost:8080') | Where-Object Name -eq 'XSRF-TOKEN').Value }
```

조회:

```powershell
# 입원 환자 목록
Invoke-RestMethod http://localhost:8080/api/patients?status=ADMITTED -WebSession $s

# 1번 환자 최신 측정값
Invoke-RestMethod http://localhost:8080/api/patients/1/vitals/latest -WebSession $s

# 미확인 위급 알람
Invoke-RestMethod "http://localhost:8080/api/alarms?acknowledged=false&severity=CRITICAL" -WebSession $s
```

측정값 직접 넣기 (장비 연동 흉내) — 대시보드에 바로 반영되고 SpO₂ 위급 알람이 생깁니다:

```powershell
$body = '{"heartRate":88,"systolicBp":125,"diastolicBp":82,"spo2":80,"temperature":36.9,"respiratoryRate":18}'
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/patients/1/vitals -ContentType 'application/json' -Body $body -WebSession $s -Headers $h
```

환자 등록 (한글이 들어가므로 UTF-8 로 변환해서 보냄):

```powershell
$body = '{"name":"홍길동","birthDate":"1970-01-01","gender":"M","roomNumber":"ICU-05","diagnosis":"관찰"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/patients -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -WebSession $s -Headers $h
```

알람 확인 처리:

```powershell
# 2번 환자의 미확인 알람 전부
Invoke-RestMethod -Method Post http://localhost:8080/api/alarms/patients/2/ack -WebSession $s -Headers $h
```

환자 등록·퇴원·알람 확인은 대시보드 화면에서도 할 수 있습니다.
전체 API 목록은 [README.md](../README.md#api) 참고.

---

## 6. MySQL 로 실행하기

1. MySQL 에서 DB 생성
   ```sql
   CREATE DATABASE patientdb CHARACTER SET utf8mb4;
   ```
2. 계정 정보를 환경변수로 지정하고 실행 (PowerShell)
   ```powershell
   $env:DB_USERNAME = 'root'
   $env:DB_PASSWORD = '비밀번호'
   .\run.cmd --spring.profiles.active=mysql
   ```
3. 처음 실행하면 테이블과 샘플 환자가 만들어집니다.
   재시작해도 데이터를 유지하려면 `src/main/resources/application-mysql.yml` 의 `mode: always` 를 `mode: never` 로 바꾸세요.
   (`always` 면 시작할 때마다 테이블을 지우고 다시 만듭니다.)

---

## 7. 문제 해결

| 증상 | 원인 / 해결 |
|---|---|
| `JAVA_HOME is not set and no 'java' command could be found` | 터미널이 Java 를 못 찾음 → `.\run.cmd` 를 쓰거나 IntelliJ 로 실행. `gradlew.bat` 를 직접 쓰려면 아래처럼 지정:<br>`$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12.1"` |
| `Port 8080 was already in use` | 서버가 이미 켜져 있음 → `.\stop.cmd` 후 다시 실행 |
| 브라우저에 "사이트에 연결할 수 없음" | 서버가 꺼져 있음 → 터미널에 `Started PatientMonitorApplication` 이 떴는지 확인 |
| 대시보드 오른쪽 위가 "재연결 중…" | 서버가 꺼졌거나 재시작 중 → 서버가 다시 켜지면 자동으로 연결됨 |
| 가입했는데 로그인이 안 됨 | 관리자 승인 전입니다 → `admin` 으로 로그인해 **가입 승인** 에서 승인 |
| 비밀번호 찾기 코드가 메일로 안 옴 | 메일 서버 미연결 상태입니다 → 서버 로그의 `[메일 대신 로그]` 에서 코드 확인 |
| 터미널 로그의 한글이 깨짐 | `gradlew bootRun` 으로 실행하면 Windows 터미널에서 한글 로그가 깨짐 → `.\run.cmd` 또는 IntelliJ 로 실행 (DB·화면의 한글은 정상) |
| 테스트가 `ClassNotFoundException: GradleWorkerMain` 으로 실패 | 사용자 폴더명이 한글이라 생기는 Gradle 문제 → `%USERPROFILE%\.gradle\gradle.properties` 에 `org.gradle.jvmargs=-Dfile.encoding=MS949` 가 있는지 확인 (이 PC 는 설정됨) |
| 데이터를 처음 상태로 되돌리고 싶음 | H2 는 서버를 껐다 켜면 초기화됨 |
