# Bank-Core 개발 계획

## 프로젝트 개요

- **프로젝트명**: bank-core
- **목적**: 토스뱅크 Server Developer (수신/금리) 공고 대비 사이드 프로젝트
- **기술 스택**: Kotlin + Spring Boot 3.4.3 + JDK 21 + Gradle, MySQL 8.0, Redis 7, TestContainer, JUnit 5, Kubernetes (EKS), GitHub Actions
- **아키텍처**: 단일 모듈 + 도메인형 패키지 구조 (나중에 멀티모듈로 리팩토링)
- **인프라**: 기존 EKS 클러스터에 Pod 추가 (MySQL StatefulSet + PVC, 기존 Redis 공유)
- **브랜치 전략**: main ← development ← feature/* (worktree 방식)

## Phase 계획

### Phase 1: 프로젝트 세팅 (1-2일) ✅ 완료
- Kotlin + Spring Boot 3.4.3 + Gradle (JDK 21)
- 도메인형 패키지 구조 (account, rate, interest)
- docker-compose (MySQL 8.0 + Redis 7)
- Flyway 마이그레이션
- Spring Profiles (local / prod)
- Spring Actuator 헬스체크

### Phase 2: 계좌 도메인 (3-5일)
- DB 스키마: account, transaction, product 테이블
- 계좌 개설/조회/해지 API
- 송금 API (출금 + 입금 단일 트랜잭션)
- 거래 내역 기록 (audit trail)
- 잔액 부족, 해지 계좌 송금 등 예외 처리
- 패키지: account/domain, account/application, account/infra, account/api

#### Phase 2-추가: 패키지 구조 전환 계획 (클린 아키텍처 전환 아님)
- **목적**: 기능 변경 없이 코드 역할을 명확히 분리해 유지보수성을 높임
- **핵심 원칙**:
  - 파일 위치/네이밍만 정리하고 기존 동작은 유지
  - 의존성 역전(Port/Adapter)까지는 하지 않음
  - 서비스가 JPA/Redis 구현체를 직접 참조하는 현재 방식은 유지

- **전환 범위(도메인별 동일 규칙 적용)**
  - `api`: Controller, 요청/응답 DTO, API 예외 매핑
  - `application`: Service(유스케이스 오케스트레이션, 트랜잭션 경계)
  - `domain`: Entity/Enum/도메인 상태 모델
  - `infra`: Repository, 번호 생성기, 락/외부 저장소 연동

- **비범위(이번 전환에서 하지 않음)**
  - 클린 아키텍처식 Port 인터페이스 분리
  - Application 계층의 프레임워크 완전 독립화
  - 대규모 비즈니스 로직 재작성

- **실행 단계**
  1. 현재 클래스별 1:1 이동 매핑표 확정 (account/transfer/transaction)
  2. 패키지 이동 + `package` 선언/`import` 일괄 정리
  3. 컴파일 확인 및 주요 API 수동 스모크 테스트
  4. 회귀 테스트 실행 (`account/transfer` 대상 + 전체 `./gradlew test`)

- **완료 기준**
  - 외부 API 스펙/동작 변화 없음
  - 계좌/송금/원장 관련 테스트 전부 통과
  - 코드 탐색 시 계층 책임(api/application/domain/infra)이 일관되게 보임

### Phase 3: 금리 + 이자 도메인 (3-5일) 진행 중
- ✅ DB 스키마: base_rate, spread_rate, preferential_rate, interest_log 테이블
- ✅ 금리 산출 API: 기준금리 + 가산금리 + 우대금리 조합
- ⏳ BigDecimal 기반 이자 계산 (단리/복리, 원단위 절사)
- ✅ 일별 이자 계산 배치 (@Scheduled)
- ✅ 멱등성 키 기반 재처리 안전성
- ✅ 중도해지 이자 계산

### Phase 4: 트랜잭션 정합성 (3-4일) 진행 중
#### Phase 4-1 (진행 중)
- ✅ 동시 송금 정합성 재현 테스트
- ✅ 비관적 락 (SELECT ... FOR UPDATE) 전략 도입
- ✅ Testcontainers 기반 동시성 통합 테스트

#### Phase 4-2 (예정)
- ⏳ 낙관적 락 충돌 처리 정책(재시도/백오프) 도입
- ⏳ 충돌 시나리오 테스트 확장

#### Phase 4-3 (예정)
- ⏳ 분산락/비관적락/낙관락 전략별 TPS 벤치마크
- ⏳ README 벤치마크 결과 반영

### Phase 5: 인프라 + 배포 (2-3일)
- Dockerfile (멀티스테이지 빌드)
- k8s 매니페스트 (namespace, MySQL StatefulSet + PVC AZ 고정, Deployment, CronJob)
- GitHub Actions CI/CD (OIDC → ECR → kubectl apply)

### Phase 6: 문서화 + 이력서 (1-2일)
- README 벤치마크 수치 업데이트
- 이력서 프로젝트 섹션 추가
- 블로그 포스팅 (선택)

## 주요 기능

### 수신 업무
- 예금 계좌 개설/조회/해지
- 송금 처리
- 이자 계산 배치

### 여신 업무
- 금리 산출 엔진 (기준금리 + 가산금리 + 우대금리)

### 트랜잭션 정합성
- 락 전략 비교 + 벤치마크

## 타임라인

- **Week 1**: Phase 1 + 2
- **Week 2**: Phase 3
- **Week 3**: Phase 4
- **Week 4**: Phase 5 + 6

## 패키지 구조

```
src/main/kotlin/com/bankcore/
├── account/
│   ├── domain/
│   ├── application/
│   ├── infra/
│   └── api/
├── rate/
│   ├── domain/
│   ├── application/
│   ├── infra/
│   └── api/
└── interest/
    ├── domain/
    ├── application/
    ├── infra/
    └── api/
```

## 기술적 고려사항

- **동시성 제어**: 비관적/낙관적 락, 분산 락 비교
- **데이터 정합성**: 트랜잭션 격리 수준, 멱등성 보장
- **성능**: TPS 벤치마크, 인덱스 최적화
- **확장성**: 멀티모듈 리팩토링 준비
- **운영**: 헬스체크, 모니터링, 로깅