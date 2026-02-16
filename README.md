# bank-core

예금 계좌 관리 및 대출 금리 산출 시스템

수신(예금/송금/이자)과 여신(금리 산출) 핵심 업무를 Kotlin + Spring Boot로 구현하고,
동시성 환경에서의 트랜잭션 정합성을 검증하는 프로젝트입니다.

## 아키텍처

```
┌─────────────────────────────────────────┐
│              EKS Cluster                │
│                                         │
│  ┌─────────────┐    ┌────────────────┐  │
│  │  bank-core  │    │ interest-batch │  │
│  │   API Pod   │    │   CronJob      │  │
│  └──────┬──────┘    └───────┬────────┘  │
│         │                   │           │
│  ┌──────▼───────────────────▼────────┐  │
│  │     MySQL (StatefulSet + PVC)     │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │     Redis (분산 락)                │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

## 기술 스택

- Kotlin, Spring Boot 3, Gradle
- MySQL 8.0, Redis 7
- TestContainer, JUnit 5
- Kubernetes (EKS), GitHub Actions

## 주요 기능

### 수신 (Savings)
- 예금 계좌 개설 / 조회 / 해지
- 계좌 간 송금 (단일 트랜잭션 보장)
- 일별 이자 계산 배치 (단리/복리, 멱등성 보장)
- 중도해지 이자 계산

### 여신 (Loan)
- 금리 산출 엔진 (기준금리 + 가산금리 + 우대금리)
- BigDecimal 기반 정밀 계산 (원단위 절사)

### 트랜잭션 정합성
- 동시 송금 시 잔액 일관성 보장
- 락 전략 비교: 비관적 락 / 낙관적 락 / 분산 락(Redis)
- 전략별 TPS 벤치마크

## 프로젝트 구조

```
bank-core/
├── app-api/           # REST API
├── app-batch/         # 이자 계산 배치
├── domain/            # 엔티티, 리포지토리, 도메인 로직
├── common/            # BigDecimal 유틸, 공통 예외
├── infra/
│   └── k8s/           # Kubernetes 매니페스트
├── docker-compose.yml
└── build.gradle.kts
```

## 로컬 실행

```bash
# 1. MySQL + Redis 실행
docker-compose up -d

# 2. API 서버 실행
./gradlew :app-api:bootRun

# 3. 테스트
./gradlew test
```

## API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/accounts` | 계좌 개설 |
| GET | `/api/accounts/{id}` | 계좌 조회 |
| POST | `/api/accounts/{id}/close` | 계좌 해지 |
| POST | `/api/transfers` | 송금 |
| GET | `/api/accounts/{id}/transactions` | 거래 내역 조회 |
| POST | `/api/rates/calculate` | 금리 산출 |

## 트랜잭션 벤치마크 결과

| 전략 | TPS | 정합성 | 비고 |
|------|-----|--------|------|
| 락 없음 | - | ❌ | 잔액 불일치 발생 |
| 비관적 락 | - | ✅ | 충돌 많은 환경에 적합 |
| 낙관적 락 | - | ✅ | 충돌 적은 환경에 적합 |
| 분산 락 | - | ✅ | 멀티 인스턴스 환경 |

> 벤치마크 수치는 구현 후 채워질 예정

## 배포

GitHub Actions → ECR → EKS (기존 클러스터)

```bash
# 수동 배포
kubectl apply -f infra/k8s/
```
