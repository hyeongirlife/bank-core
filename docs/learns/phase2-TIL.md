# 2026-02-16 Phase 2 DB 스키마 설계

## 오늘 한 것
- account, product, transaction 테이블 설계
- Flyway 마이그레이션 V2__create_account_tables.sql 작성
- docker-compose + bootRun으로 마이그레이션 동작 확인

## 배운 것

### Flyway
- DB 스키마 버전 관리 도구 (Git처럼 DB 변경 이력 추적)
- 앱 시작 시 `flyway_schema_history` 테이블을 확인해서 미적용 SQL만 순서대로 실행
- 파일 네이밍: `V{버전}__{설명}.sql` (언더스코어 2개 필수)
- 한번 적용된 파일은 수정 불가 (체크섬 검증) → 변경 필요 시 새 버전 파일 추가
- TypeScript 세계의 Prisma migrate, TypeORM migration:run과 유사

### DB 스키마 설계 포인트
- 금액 컬럼은 `DECIMAL(18,2)` — 부동소수점 오류 방지
- 금리 컬럼은 `DECIMAL(5,4)` — 소수점 4자리까지 (예: 3.5% = 0.0350)
- 계좌 상태는 `ENUM('ACTIVE', 'CLOSED')` — DB 레벨에서 값 제한
- 거래 유형은 `ENUM('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT')`
- 계좌번호에 UNIQUE + 인덱스, 거래내역에 계좌+일시 복합 인덱스

### Docker 볼륨과 Flyway의 관계
- `docker-compose down` → 컨테이너 삭제, 볼륨(데이터)은 유지
- `docker-compose down -v` → 볼륨까지 삭제 (DB 초기화)
- 볼륨이 남아있으면 재시작 시 Flyway가 이미 적용된 마이그레이션은 건너뜀

### 도메인형 패키지 구조를 선택한 이유

패키지 구조는 크게 계층형(Layer-based)과 도메인형(Domain-based) 두 가지가 있다.

**계층형** — controller/, service/, repository/ 등 기술 계층으로 분류
**도메인형** — product/, account/, transaction/ 등 비즈니스 도메인으로 분류

도메인형을 선택한 이유:
- **응집도**: 계좌 관련 코드가 `account/` 하나에 모여 있어서, 기능 수정 시 한 폴더만 보면 됨
- **확장성**: 도메인이 늘어나도 독립적으로 패키지를 추가하면 끝. 계층형은 각 폴더에 파일이 계속 쌓임
- **MSA 전환 용이**: 도메인 단위로 이미 분리되어 있어서 별도 서비스로 떼어내기 쉬움
- **접근 제어**: 도메인 간 불필요한 의존을 패키지 레벨에서 제한 가능

## 다음 할 것
- JPA 엔티티 매핑 (Account, Product, Transaction)
- Repository 인터페이스 + JPA 구현체
- UseCase (계좌 개설, 조회, 해지, 송금)
- Controller + DTO

### 계좌 개설 API 설계 시 실무 관점 고민

**Bean Validation으로 입력 검증 강화**
- `@field:Positive` (customerId), `@field:NotBlank` (productCode) 적용
- Controller에 `@Valid` 추가하여 잘못된 요청을 서비스 레이어 진입 전에 차단
- 금융 시스템에서는 입력 검증이 첫 번째 방어선

**계좌번호 충돌 시 재생성 로직**
- 랜덤 생성 방식은 극히 낮은 확률로 중복 발생 가능
- `existsByAccountNumber`로 중복 체크 후 최대 10회 재시도
- DB UNIQUE 제약만으로는 에러가 발생하므로, 애플리케이션 레벨에서 선제 대응

**추가로 고려해야 할 것들 (면접 대비)**
- **도메인 이벤트**: 계좌 개설 → 알림, 감사 로그 등 다른 도메인에 이벤트 발행 (MSA 간 데이터 정합성)
- **Audit Trail**: 개설 요청자, 채널(APP/WEB) 등 메타데이터 기록
- **Transactional Outbox**: 이벤트 발행과 DB 저장의 원자성 보장 패턴

### 멱등성 (Idempotency)

**왜 필요한가**
- 금융 시스템에서 동일 요청이 중복 처리되면 계좌가 2개 생기는 사고 발생
- 네트워크 타임아웃, 클라이언트 재시도, 로드밸런서 retry 등에서 발생 가능
- "같은 요청은 몇 번을 보내도 결과가 동일해야 한다"는 원칙

**구현 방식**
- 클라이언트가 `Idempotency-Key` 헤더에 고유 키를 포함하여 요청
- Redis `SETNX` (SET if Not eXists)로 키를 원자적으로 선점 → 동시 요청 중 하나만 통과
- 처리 완료 후 응답을 Redis에 저장 (TTL 24시간)
- 같은 키로 재요청 시 저장된 응답을 그대로 반환
- 아직 처리 중이면 409 Conflict 반환

**면접 포인트**
- "Redis가 죽으면?" → 요청 거부 (안전 우선) or DB fallback
- "TTL은 왜 24시간?" → 클라이언트 재시도 윈도우를 고려한 값. 비즈니스 요구에 따라 조절
- "SETNX가 왜 중요?" → 분산 환경에서 동시에 같은 키로 요청이 와도 하나만 선점되는 원자적 연산

### Spring Interceptor

**개념**
- Controller 실행 전/후에 공통 로직을 끼워넣는 메커니즘
- Filter와 비슷하지만, Spring MVC 레벨에서 동작 (Handler 정보 접근 가능)

**요청 흐름**
```
Client → Filter → DispatcherServlet → Interceptor.preHandle → Controller
                                    ← Interceptor.afterCompletion ←
```

**왜 Interceptor로 구현했나**
- 멱등성은 횡단 관심사 (Cross-cutting concern) — 계좌 개설, 송금 등 여러 API에 공통 적용
- Controller/Service 코드를 수정하지 않고 기능 추가 가능
- `addPathPatterns("/api/**")`로 적용 범위를 유연하게 제어

**Filter vs Interceptor**
- Filter: Servlet 레벨, Spring 컨텍스트 밖, 모든 요청에 적용
- Interceptor: Spring MVC 레벨, 빈 주입 가능, Handler 정보 접근 가능
- 멱등성처럼 비즈니스 로직과 가까운 관심사는 Interceptor가 적합

### 분산 락을 활용한 동시성 제어

**문제 상황**
- 같은 고객이 같은 상품으로 동시에 계좌 개설 요청 → 둘 다 count=0으로 읽고 둘 다 통과 → 계좌 2개 생성
- DB count 체크만으로는 race condition 방지 불가

**해결: 4단계 방어 체계**
```
1차: 멱등성 키 (Redis, TTL 24h) — 같은 요청 재시도 차단
2차: 분산 락 (Redis SETNX, TTL 5s) — customer:product 단위 동시성 직렬화
3차: DB count 체크 — maxAccountPerCustomer 비즈니스 룰 검증
4차: DB UNIQUE 제약 — 최후 안전망 (1인 1계좌 상품 한정)
```

**비관적 락 vs 분산 락 — 실무 판단 기준**
- 비관적 락 (`SELECT FOR UPDATE`): Product row 단위 잠금 → 같은 상품 개설하는 모든 고객이 직렬화 → 트래픽 높으면 병목
- 분산 락 (Redis): customer+product 조합 단위 잠금 → 다른 고객끼리는 병렬 처리 가능
- 고객 대면 **고트래픽** 서비스는 customer+product 단위 병렬성이 중요해 분산 락을 자주 선택한다. 내부 관리자용 저트래픽 시스템은 구현 단순성 측면에서 비관적 락으로도 충분할 수 있다

**분산 락 TTL을 5초로 잡은 이유**
- 계좌 개설 트랜잭션은 보통 수백ms 내 완료
- 프로세스가 죽어도 5초면 자동 해제 → 다른 요청 진행 가능
- 너무 길면 장애 시 고객 대기, 너무 짧으면 트랜잭션 완료 전 락 해제 위험
- 운영에서는 고정값으로 끝내지 않고 `TTL >= (P99 처리시간 + 네트워크/Redis 지연 버퍼)` 기준으로 잡고, 실제 지표를 보며 조정하는 것이 안전

**면접 포인트**
- "Redis 장애 시?" → 분산 락만 의존하면 중복 생성 위험 → DB UNIQUE 제약이 최후 방어선
- "Redisson 안 쓰고 직접 구현?" → 단순 SETNX+TTL이면 충분. Redisson은 재진입 락, 페어 락 등 고급 기능 필요 시 도입
- "락 안에서 @Transactional?" → 락 범위가 트랜잭션보다 넓어야 함. 트랜잭션 커밋 후 락 해제가 원칙

### 추가 학습: 분산 락 해제 안정성 보완 (2026-02-19)

**기존 구현의 위험 포인트**
- 락 값이 고정 문자열(`LOCKED`)이고 `finally`에서 `delete(lockKey)`를 호출하면,
  TTL 만료 후 다른 요청이 같은 락 키를 선점한 상황에서 이전 요청이 새 락을 삭제할 수 있음

**개선 방식**
- 락 획득 시 UUID 토큰을 값으로 저장
- 락 해제 시 Lua 스크립트로 `get(key) == token`일 때만 `del(key)` 수행
- 핵심은 "락 획득"보다 "소유자만 해제"를 보장하는 것

**Spring Data Redis 구현 포인트**
- `DefaultRedisScript` 사용 시 `scriptText` 프로퍼티가 아니라 `setScriptText(...)` 사용
- 결과 타입도 `setResultType(Long::class.java)`로 지정

**예외 처리에서 배운 점**
- `finally`에서 unlock 실패가 발생하면 원래 비즈니스 예외를 덮어쓸 수 있음
- action 예외를 우선 보존하고, unlock 예외는 `addSuppressed`로 붙이는 구조가 안전함

**테스트에서 배운 점**
- `setIfAbsent`에 넣은 락 토큰과 `execute(unlockScript, ...)`에 전달한 토큰이 같은지 검증해야 회귀 방지 가능
- 락 로직 테스트는 "호출 여부"보다 "동일 토큰 사용" 같은 안전 속성을 검증하는 것이 중요함

### 추가 학습: 계좌 해지 API 구현 및 검증 (2026-02-20)

**요구사항 확정 정책 반영**
- 잔액 0원인 계좌만 해지 허용
- 이미 해지된 계좌 재요청은 `409 Conflict`
- 분산 락 키는 계좌 단위 공통키 `lock:account:{id}` 사용

**서비스 레이어에서 상태 전이 규칙을 명시**
- `closeAccount(id)`에서 분산 락 획득 후 조회/검증/저장을 한 트랜잭션 안에서 처리
- 검증 순서:
  1) 미존재 → `NoSuchElementException` (404)
  2) 이미 CLOSED → `IllegalStateException` (409)
  3) 잔액 non-zero → `IllegalStateException` (409)
- 상태 전이는 immutable 방식(`copy`)으로 반영: `ACTIVE -> CLOSED`, `closedAt/updatedAt` 갱신

**BigDecimal 비교에서 배운 점**
- `BigDecimal("0.00") != BigDecimal.ZERO`는 scale 차이로 false가 될 수 있음
- 금액 비교는 `compareTo(BigDecimal.ZERO) != 0`처럼 값 비교로 처리해야 안전함

**API/예외 응답 계약 유지**
- 엔드포인트: `POST /api/accounts/{id}/close`
- 성공 시 `200 OK + AccountResponse`
- 실패는 기존 Controller `@ExceptionHandler` 매핑을 재사용(400/409/404)

**멱등성과 해지 API의 결합 동작**
- `/api/**`의 POST는 기존 Idempotency 인터셉터가 자동 적용
- 같은 `Idempotency-Key` 재시도는 기존 성공 응답(200)을 재사용
- 다른 키로 같은 계좌 재해지는 비즈니스 검증에 의해 409

**테스트 전략 (RED → GREEN)**
- Service 테스트:
  - 정상 해지
  - 미존재 계좌
  - 이미 해지 계좌
  - 잔액 존재 계좌
  - 락 키가 `account:{id}`인지 검증
- Controller 테스트:
  - 200 / 404 / 409 / 409 매핑 검증
- `./gradlew test` 전체 통과로 회귀 확인

**수동 검증 자산 정리**
- Apidog import용 컬렉션 파일 작성:
  - `docs/apidog/account-close-phase2.collection.json`
- 시나리오 5종(정상/재해지-다른키/재시도-같은키/미존재/잔액존재) 포함

**운영 관점 메모**
- Docker 검증은 로컬 Docker daemon 실행 상태에 의존
- daemon 미기동 시 `Cannot connect to the Docker daemon` 에러로 컨테이너/실 API 검증이 차단됨
- 이 경우 Docker Desktop 기동 후 `docker compose up -d`부터 재실행 필요

### 수동 E2E 검증 로그 (Docker + Local BootRun)

**실행 환경**
- Docker Compose: MySQL(3306), Redis(6379)
- 애플리케이션: `./gradlew bootRun` (local profile)
- 헬스체크: `/actuator/health` = `UP`

**사전 데이터**
- Product: `SAV001`
- Account #1: 잔액 0, ACTIVE
- Account #2: 잔액 100, ACTIVE

**시나리오 결과**
1) 정상 해지 (`POST /api/accounts/1/close`, key=`close-1-k1`)
   - 결과: `200`, `status=CLOSED`, `closedAt!=null`
2) 동일 계좌 재해지(다른 키, key=`close-1-k3`)
   - 결과: `409`, `{"error":"이미 해지된 계좌입니다"}`
3) 동일 멱등 키 재시도(같은 키, key=`close-1-k1`)
   - 결과: `200`, 최초 성공 응답 재사용
4) 미존재 계좌 해지 (`/api/accounts/99999999/close`)
   - 결과: `404`, `{"error":"계좌를 찾을 수 없습니다: 99999999"}`
5) 잔액 있는 계좌 해지 (`/api/accounts/2/close`)
   - 결과: `409`, `{"error":"잔액이 남아있는 계좌는 해지할 수 없습니다"}`

**DB 최종 상태 확인**
- Account #1: `CLOSED`, `closed_at` 설정됨
- Account #2: `ACTIVE` 유지, `closed_at` = `NULL`

### 추가 학습: 면접 답변용 — 왜 `transfer` API를 별도로 두는가

**한 줄 답변**
- 송금은 기술적으로 `출금+입금`이지만, 도메인적으로는 **단일 트랜잭션**이라 `/api/transfers`로 묶어야 정합성을 보장할 수 있다.

**면접 포인트 (핵심 5가지)**
1. **원자성(All-or-Nothing)**
   - 출금/입금을 각각 API로 분리하면 중간 실패 시 partial success 위험이 커진다.
2. **검증 일원화**
   - 동일계좌 금지, 미존재/해지/잔액 부족 규칙을 한 유스케이스에서 강제할 수 있다.
3. **동시성 제어**
   - 단일 계좌 락이 아니라 `account-transfer:{min}:{max}` pair lock으로 두 계좌 동시 갱신을 안전하게 처리한다.
4. **감사/원장 요건**
   - 송금 1건당 `TRANSFER_OUT` + `TRANSFER_IN` 2건을 남겨 추적 가능성을 확보한다.
5. **재시도 안정성**
   - 클라이언트는 transfer 1요청만 재시도하면 되어 멱등성 관리가 단순해진다.

**요약 멘트**
- “송금은 내부적으로 출금+입금 단계를 가지지만, 외부 계약은 `transfer`라는 하나의 비즈니스 트랜잭션으로 제공해야 금융 도메인 정합성과 운영 안정성을 동시에 만족합니다.”
