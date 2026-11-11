# SMPUtil

친구들끼리 쓰는 Paper 서버의 모든 플레이어용 생활 기능 플러그인. 한글 닉네임, 집·스폰·텔레포트 요청, 거래, 도전과제 공유, 접속자 목록.
게임 규칙과 그 결정 이유는 `docs/DESIGN.md`를 기준으로 한다. 작업 전에 반드시 읽는다.

## 함께 쓰는 플러그인

같은 서버에서 `Econet`(경제), `SMPUtil`(플레이어 생활 기능), `AdminUtil`(관리자 도구)이 함께 돈다. 저장소는 각각 따로 있다.

- 서로의 코드를 의존하지 않는다. 각자 단독으로 설치해도 동작해야 한다.
- 연결이 필요하면 **데이터로** 한다(예: 플레이어 PDC 키). 약속한 키는 DESIGN.md에 표로 적혀 있고, 바꾸면 상대 저장소 DESIGN.md도 함께 바꿔야 한다.
- 코드 스타일과 Paper 레이어 규칙은 Econet 저장소의 `CLAUDE.md`와 같게 맞춘다. 판단이 애매하면 그쪽에서 이미 정한 방식을 따른다.

## 빌드

- Kotlin 2.4.21, JDK 25 툴체인, Paper API 26.3, Gradle 버전 카탈로그(`gradle/libs.versions.toml`), shadow, run-paper. Econet과 같은 버전을 쓴다.
- 패키지 `dev.doyu.smputil`. 모듈은 하나로 시작한다.
- 의존성은 버전 카탈로그로만 추가한다.
- Econet과 다른 점 하나: paper-api 대신 **paperweight-userdev의 26.3 dev bundle**을 쓴다(`paper-dev-bundle` 버전은 빌드 번호까지 고정). 닉네임 패킷에 서버 내부 클래스가 필요해서다(DESIGN 3장). dev bundle에 paper-api가 들어 있다.
  - 첫 빌드는 Mojang 서버 jar를 받아 디컴파일하므로 1~2분 걸린다. 네트워크에 `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `repo.papermc.io`가 필요하다.

```
./gradlew build
./gradlew runServer
```

### 배포 (`./gradlew deploy`)

Econet 저장소(master)의 배포 파이프라인과 같다. 구현은 `buildSrc/src/main/kotlin/econet/deploy/`, 태스크 등록은 루트 `build.gradle.kts` 맨 아래.

- `deployUpload`(shadowJar → SFTP로 `plugins/`) → `deployRestart`(Pterodactyl Client API `power` = restart). 둘을 묶은 것이 `deploy`.
- 접속 정보는 저장소에 두지 않는다. `~/.gradle/gradle.properties` 또는 환경 변수(`econet.sftp.host` → `ECONET_SFTP_HOST`)에서 읽고, 없으면 실행할 때 키 이름을 밝혀 실패한다.
  - 이유: 비밀번호·API 키가 커밋되지 않게. 배포하지 않는 빌드는 설정 없이 돌아야 한다.
- 설정 키 이름은 `econet.sftp.*`, `econet.panel.*` 그대로다. SMPUtil로 바꾸지 않는다.
  - 이유: Econet·SMPUtil·AdminUtil이 같은 서버를 쓰므로 접속 정보 파일 하나를 세 프로젝트가 공유한다. `buildSrc`의 패키지 이름(`econet.deploy`)도 같은 이유로 그대로 두었다. 세 저장소의 배포 코드를 서로 복사해 올 때 차이가 이름뿐이게 하려는 것이다.
- SFTP 연산만 쓴다(sshj, 순수 Java). Pterodactyl은 셸·scp를 열어 주지 않는다. sshj 버전은 버전 카탈로그에 있다.
- 서버의 파일명은 버전 없이 `rootProject.name` 기준 `SMPUtil.jar`로 고정한다.
  - 이유: 버전이 올라도 같은 이름을 덮어쓰면 되고, 버전이 다른 jar가 둘 남아 플러그인이 두 번 로드되는 일이 없다.
- 순서: `SMPUtil.jar.uploading`으로 업로드 → 크기 확인 → **이 플러그인의 옛 파일**(`SMPUtil.jar`, 옛 `SMPUtil-*.jar`, `*.jar.uploading`)만 삭제 → 최종 이름으로 rename.
  - 이유: 업로드가 실패해도 돌아가던 jar가 남고, 임시 파일은 `.jar`로 끝나지 않아 서버가 반쯤 쓴 파일을 읽지 않는다.
  - **`Econet.jar`, `AdminUtil.jar` 같은 형제 플러그인 파일은 절대 지우지 않는다.** 이름 접두사까지 정확히 맞춰 거르고(`ownedRemoteFiles`), 이 규칙은 `DeploySettingsTest`에서 확인한다.
- 서버 신원은 `~/.ssh/known_hosts` 또는 `econet.sftp.hostKey`(SHA256 지문)로 확인한다. 둘 다 없으면 서버 지문을 알려 주며 멈춘다.
  - 이유: 확인 없이 접속하면 가로챈 서버에 비밀번호를 넘길 수 있다.
- 인증은 키(`keyPath`) → 비밀번호 순으로 시도한다. 패널 URL은 https만 허용한다(로컬 시험 서버 제외).
- buildSrc의 테스트는 `./gradlew build`에 묶여 있지 않다(실제로 확인했다). 배포 코드를 고치면 `./gradlew -p buildSrc test`를 따로 돌린다.

## 비밀 정보 (절대 규칙)

- 서버 주소, 계정, 비밀번호, 토큰을 **저장소 안의 어떤 파일에도** 적지 않는다. 빌드 스크립트, 설정 예시, 테스트, 주석, 커밋 메시지 모두 포함.
- 배포 태스크가 필요하면 접속 정보는 사용자의 `~/.gradle/gradle.properties`에서 읽는다(`val deployHost: String by project` 등). 비밀번호가 아니라 SSH 키 경로를 쓴다.
  - 이유: 옛 Utility의 빌드 스크립트에 서버 비밀번호가 평문으로 들어 있었다.

## 작업 방식

- `main`에 직접 푸시하지 않는다. 단계마다 브랜치를 만들고 PR로 올린다.
- 단계마다 빌드와 테스트가 통과해야 다음으로 넘어간다.
- 판단이 애매하면 멈추지 말고 가장 합리적인 쪽으로 진행하되, PR 설명의 "판단한 것" 항목에 남긴다.
- 실제 서버에서 확인한 사실(이벤트 순서, 버전별 동작 등)은 이 문서에 "실제 서버에서 확인"이라고 적어 둔다. 다음 작업자가 다시 확인하지 않게 하기 위해서다.

## Paper 레이어 규칙

- 로직은 Bukkit과 분리해 단위 테스트하고, Paper 연결부(명령어·리스너)는 얇게 유지한다.
  - 이유: 실제 서버 없이 검증할 수 있는 범위를 최대로 넓히기 위해.
- 명령어는 Paper Brigadier lifecycle(`LifecycleEvents.COMMANDS`)에 등록한다. 영어 이름 + 한글 별칭.
  - Econet에서 Paper 26.3의 한글 별칭 등록·실행을 실제 서버로 확인했다.
- 권한은 명령어마다 `smputil.<기능>` 형식으로 둔다. `isOp` 직접 검사는 쓰지 않는다.
- 상태를 `companion object`나 전역 가변 컬렉션에 두지 않는다. 플러그인이 만든 객체가 갖고, 재시작해도 남아야 하는 것은 저장한다.
  - 이유: 옛 Utility는 전역 리스트에 상태를 둬서 재시작하면 풀렸고, 어디서 바뀌는지 추적하기 어려웠다.
- 특수 아이템(도구, UI 버튼 등)은 **PDC 태그로 식별**한다. 표시 이름으로 식별하지 않는다.
  - 이유: 모루로 이름만 바꿔 위조할 수 있다.
- 설정 값이 틀리면 기본값으로 덮지 않고 시작 시 실패한다. 빠진 키는 번들 `config.yml` 값으로 채워진다.
- 플레이어 메시지는 Adventure `Component`로 만든다. `§` 색 코드 문자열은 쓰지 않는다.
- 이름으로 플레이어를 찾을 때 `getOfflinePlayer(name)`을 쓰지 않는다. 모르는 이름에도 가짜 UUID를 만든다. 접속한 적 있는 플레이어만 받는다.

## 코드 스타일: 잘 읽히는 클린 코드

- 구현이 하나뿐이면 인터페이스를 만들지 않는다.
  - 이유: 추상화는 갈아 끼울 실제 이유가 생겼을 때 만든다.
- `Manager`/`Handler`/`Util` 같은 이름 금지. 역할이 이름에 드러나게 짓는다.
  - 이유: 이런 이름은 무엇이든 담을 수 있어서 결국 무엇이든 담게 된다.
- 스코프 함수(`let`/`also`/`run`/`apply`)는 중첩하지 않는다. 한 단계까지만.
  - 이유: 중첩되면 `it`/`this`가 무엇을 가리키는지 추적해야 한다.
- sealed 결과는 `when`으로 끝까지 받고 `else`로 뭉개지 않는다.
  - 이유: 새 실패 케이스가 추가되면 컴파일러가 처리하지 않은 곳을 알려 준다.
- 주석은 "왜"만 쓴다. 무엇을 하는지는 코드가 말한다.
  - 이유: "무엇" 주석은 코드와 쉽게 어긋난다.
- 한 번만 쓰이는 짧은 로직은 굳이 함수로 빼지 않는다.
  - 이유: 읽는 사람이 정의를 찾아 왔다 갔다 하게 만든다.
- 테스트는 `kotlin.test`, 코루틴 테스트는 `runBlocking<Unit>`.
  - 이유: 마지막 식이 값을 돌려주면 테스트 메서드가 void가 아니게 되어 JUnit이 조용히 건너뛴다.

## 테스트

- `kotlin.test`. 시간이 걸리는 규칙(요청 만료 등)은 `Clock`을 주입해 테스트한다.
- Bukkit과 분리한 순수 로직: `RequestBoard`(요청·만료), `HomeBook`, `TradeSession`(아이템 총합 무작위 테스트), `missingCriteria`, `NicknameRules`, `withShownName`, `readSettings`.

## 구현 메모

### 실제 서버에서 확인 (Paper 26.3 build 246, `runServer`, 콘솔)

- 플러그인이 오류 없이 켜지고 꺼진다. 11개 명령어와 한글 별칭(`/접속자`, `/도전과제공유` 등)이 콘솔에서 실행된다.
- `/syncadv on`이 `state.yml`에 저장된다.
- 클라이언트 접속 테스트는 아직 못 했다(이름표·탭 목록·거래 화면). mineflayer(minecraft-data)가 26.1까지만 지원한다.

### Paper 26.3 서버 코드에서 확인

- 패킷의 플레이어 이름은 `ByteBufCodecs.PLAYER_NAME = stringUtf8(16)`이다. 16자(UTF-16 단위)를 넘으면 **인코딩에서 예외가 나 받는 사람의 접속이 끊긴다.** 그래서 `nickname.max-length`는 16을 넘으면 시작 시 실패한다. 한글 16자는 48바이트라 바이트 상한(16×3) 안에 든다.
- `PlayerJoinEvent`는 `PlayerList.placeNewPlayer`에서 플레이어 정보 패킷(`createPlayerInitializing`)보다 먼저 불린다. 그래서 LOWEST에서 닉네임을 불러오고 파이프라인을 끼우면 첫 패킷부터 닉네임으로 나간다.
- 플레이어 정보 패킷은 묶음(bundle) 패킷에 담겨 나가지 않는다. `NicknameRewriter`는 묶음을 열어 보지 않는다.
- 채팅은 `ChatProcessor`가 Adventure `displayName`을 쓴다. 사망 메시지는 `CombatTracker`가 진짜 이름(`getDisplayName` → 프로필 이름)으로 만들므로 이벤트에서 바꾼다. 입장·퇴장 메시지도 같다.
- `getRespawnLocation(true)`는 `findRespawnAndUseSpawnBlock(..., false)`라 리스폰 정박기 충전을 쓰지 않는다.

### 닉네임 방식의 예상되는 한계 (클라이언트 동작이라 아직 확인하지 못함)

- 클라이언트는 스코어보드 팀 소속을 프로필 이름으로 찾는다. 닉네임이 있는 플레이어는 다른 사람 화면에서 팀 색·접두사가 적용되지 않는다.
- 바닐라 명령어의 플레이어 이름 자동완성은 클라이언트가 탭 목록의 프로필 이름(=닉네임)으로 만든다. `/tp 홍길동`처럼 제안되지만 서버는 진짜 이름만 받는다. SMPUtil 명령어는 둘 다 받는다.
