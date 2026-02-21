# 2026-02-21 Phase 6 문서화 학습 정리

## 오늘 한 것
- `README.md` 트랜잭션 벤치마크를 플레이스홀더에서 실측 수치 기반(분산락/비관적락 우선)으로 업데이트
- 분산 락/비관적 락 전략의 동시 송금 테스트 결과를 기준으로 TPS 산정 근거를 문서화
- `docs/plans/development-plan.md`의 Phase 6 상태를 완료 기준에 맞춰 동기화

## 구현 상세

### 1) README 벤치마크 수치 반영
- 대상: `README.md`
- 반영 수치
  - 분산 락 (`DISTRIBUTED`): **24.3 req/s**
  - 비관적 락 (`PESSIMISTIC`): **74.1 req/s**
  - 낙관적 락 (`@Version`): **측정 예정** (Phase 4-2에서 충돌 정책 구현 후 동일 조건으로 측정)

### 2) TPS 산정 기준 명시
- 공식: `TPS = 총 요청 수 / 테스트케이스 실행 시간(초)`
- 조건
  - 동일 계좌쌍 20건 동시 송금
  - 출금 계좌 초기 잔액 1000, 요청당 100 송금
- 근거 테스트
  - `TransferConcurrencyReproductionTest` (DISTRIBUTED)
  - `TransferPessimisticLockIntegrationTest` (PESSIMISTIC)

### 3) 개발 계획 문서 상태 동기화
- 대상: `docs/plans/development-plan.md`
- 변경
  - `Phase 6: 문서화 + 이력서`를 완료 상태로 갱신
  - 블로그 포스팅은 선택 항목으로 분리해 별도 진행 상태로 표기

## 검증
- 재실행 테스트
  - `./gradlew test --tests "com.bankcore.transfer.e2e.TransferConcurrencyReproductionTest" --tests "com.bankcore.transfer.e2e.TransferPessimisticLockIntegrationTest"`
- 결과: 통과

## 배운 것
1. 벤치마크 문서는 측정값뿐 아니라 측정 조건/공식/근거 테스트까지 함께 남겨야 재현성이 높다.
2. 전략 비교 표는 절대 성능 결론이 아니라 현재 시나리오 기준 관측치임을 명시해야 한다.
3. 현재 수치는 3개 전략 중 2개 전략 관측치이므로, 최종 전략 비교 결론은 낙관적 락 측정 완료 후 내려야 한다.
4. 계획 문서와 README 상태를 같은 배치에서 동기화하면 진행 추적이 쉬워진다.
