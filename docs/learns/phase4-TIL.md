# 2026-02-21 Phase 4 트랜잭션 정합성 학습 정리

## 오늘 한 것
- `docs/plans/development-plan.md`에서 Phase 3 `중도해지 이자 계산`을 `✅`로 반영
- Phase 4를 4-1/4-2/4-3으로 세분화하여 진행 상태 추적 가능하게 정리
- 동시 송금 정합성 재현 테스트 및 비관적 락 통합 테스트 추가
- 송금 락 전략(분산락/비관적락) 설정 분기 도입
- 비관적 락 전략의 컨트롤러 예외 매핑(409) 보강
- 단위/통합 테스트 보강 및 타깃 회귀 검증

## 구현 상세

### 1) 락 전략 분기 도입 (하위 호환 유지)
- `TransferService`에 `transfer.lock.strategy` 설정 기반 분기 추가
- 기본값은 `DISTRIBUTED`로 두어 기존 API 동작과 호환 유지
- `PESSIMISTIC` 선택 시 DB row lock 경로 사용

핵심 포인트:
- 락 획득 순서를 `minId -> maxId`로 고정해 데드락 가능성 완화
- 비즈니스 규칙(동일 계좌 금지/잔액 부족/해지 계좌)은 기존과 동일

### 2) Repository 비관적 락 메서드 추가
- `AccountRepository.findByIdForUpdate(id)` 추가
- `@Lock(PESSIMISTIC_WRITE)` + JPQL 쿼리 사용

### 3) 컨트롤러 예외 계약 보강
- 기존 낙관락 충돌(409) 매핑 유지
- `PessimisticLockingFailureException`, `CannotAcquireLockException`도 409로 매핑
- 전략 전환 시에도 클라이언트가 같은 충돌 계약을 사용 가능

### 4) 동시성 테스트 기반 확장
- `TestcontainersIntegrationBase` 추가
  - `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`
  - Redis `GenericContainer` 실행 후 동적 프로퍼티 주입
- `application-test.yml`
  - MySQL Testcontainers JDBC 유지
  - Redis host/port를 `${TEST_REDIS_HOST}`, `${TEST_REDIS_PORT}`로 주입
  - 기본 락 전략 `DISTRIBUTED` 유지

신규 E2E 테스트:
- `TransferConcurrencyReproductionTest`
  - 동일 계좌쌍 동시 송금 시 총합 보존/음수 잔액 미발생/거래내역 건수 일치 검증
- `TransferPessimisticLockIntegrationTest`
  - 비관적 락 전략에서 정합성 유지 및 최소 1건 이상 성공 검증

### 5) 단위 테스트 보강
- `TransferServiceTest`에 전략 분기 시나리오 추가
  - 기본 분산락 경로
  - 비관적 락 경로(ID 정렬 잠금 순서)
  - 비관적 락 경로 계좌 미존재 예외
  - 미지원 전략값 예외
- 기존 핵심 비즈니스 규칙 회귀 테스트 유지

## 테스트 실행 결과

### 타깃 테스트 (성공)
- `./gradlew test --tests "com.bankcore.transfer.service.TransferServiceTest"`
- `./gradlew test --tests "com.bankcore.transfer.e2e.TransferConcurrencyReproductionTest"`
- `./gradlew test --tests "com.bankcore.transfer.e2e.TransferPessimisticLockIntegrationTest"`
- `./gradlew test --tests "com.bankcore.transfer.controller.TransferControllerTest"`
- 통합 실행:
  - `./gradlew test --tests "com.bankcore.transfer.service.TransferServiceTest" --tests "com.bankcore.transfer.e2e.TransferConcurrencyReproductionTest" --tests "com.bankcore.transfer.e2e.TransferPessimisticLockIntegrationTest" --tests "com.bankcore.transfer.controller.TransferControllerTest"`

### 전체 회귀
- `./gradlew test`는 기존에도 존재하던 외부 MySQL 연결 의존 테스트(`AccountLifecycleE2ETest`, `BaseRateRepositoryTest` 등)로 실패
- 이번 Phase4-1 변경으로 추가된 타깃 테스트들은 모두 독립적으로 통과

## 배운 것
1. 동시성 테스트는 애플리케이션 로직뿐 아니라 인프라 의존(특히 Redis)까지 self-contained로 맞춰야 재현성이 올라간다.
2. 비관적 락 전략 도입 시 락 획득 순서 고정은 선택이 아니라 필수에 가깝다.
3. 락 전략을 교체해도 컨트롤러 예외 계약(409 충돌 응답)을 유지해야 API 소비자 영향이 최소화된다.
4. 동시성 테스트에서는 "정확히 N건 성공" 같은 brittle assertion보다 성공 건수 기반 상태 검증이 안정적이다.

## 다음 단계 (Phase 4-2 / 4-3)
- 낙관적 락 충돌 처리 정책(재시도/백오프) 도입
- 충돌 시나리오 테스트 확장
- 분산락/비관적락/낙관락 전략별 TPS 측정 및 README 반영
