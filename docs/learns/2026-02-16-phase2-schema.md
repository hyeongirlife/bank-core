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
