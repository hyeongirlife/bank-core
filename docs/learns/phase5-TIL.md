# 2026-02-21 Phase 5 인프라 + 배포 학습 정리

## 오늘 한 것
- Phase 5 목표(컨테이너 이미지, k8s 매니페스트, GitHub Actions CI/CD) 구현
- 운영 리스크(무분별 배포, 시크릿 누락, 보안 설정 미흡, 롤아웃 검증 누락) 개선
- 배치 CronJob을 단발 실행(run-once) 모드로 동작하도록 애플리케이션 진입점 보강

## 구현 상세

### 1) Docker 이미지 빌드 파이프라인
- `Dockerfile` 추가
  - multi-stage build(Gradle JDK 21 → Temurin JRE 21)
  - `bootJar` 빌드 후 런타임 이미지에 jar만 복사
  - 런타임 non-root 사용자(`app`)로 실행

핵심 포인트:
- 빌드 도구/소스는 최종 이미지에서 제거되어 크기/공격면 축소
- 컨테이너 기본 권한을 non-root로 내려 보안 기본값 강화

### 2) Kubernetes 매니페스트 구성
- `k8s/namespace.yaml`
  - `minibank` 네임스페이스 선언
- `k8s/mysql-statefulset.yaml`
  - MySQL Service + StatefulSet
  - `MYSQL_ROOT_PASSWORD`와 앱 계정(`MYSQL_USER`/`MYSQL_PASSWORD`)을 분리
  - `volumeClaimTemplates`로 영속 스토리지 구성
  - 리소스 requests/limits + 보안 설정 추가
- `k8s/bank-core-deployment.yaml`
  - bank-core Service + Deployment(2 replicas)
  - readiness/liveness probe: `/actuator/health`
  - `SPRING_PROFILES_ACTIVE=prod`
  - 앱 전용 DB 계정(secret) 사용
  - `SPRING_TASK_SCHEDULING_ENABLED=false`로 배포 파드 스케줄러 비활성화
  - `securityContext`(runAsNonRoot, privilege escalation 차단)
  - 리소스 requests/limits 설정
- `k8s/interest-batch-cronjob.yaml`
  - 일별 스케줄(`10 0 * * *`) 실행
  - `concurrencyPolicy: Forbid` + `backoffLimit` 적용
  - `SPRING_TASK_SCHEDULING_ENABLED=false`로 내부 스케줄 중복 방지
  - 앱 전용 DB 계정(secret) 사용
  - 애플리케이션 run-once 인자(`--interest.batch.run-once=true`)로 단발 배치 실행
  - 보안/리소스 설정 추가
- `k8s/secret.example.yaml`
  - root/app 계정을 분리한 secret 템플릿 제공
    - `mysql-root-password`
    - `mysql-app-username`
    - `mysql-app-password`

### 3) 배치 run-once 진입점 보강
- `src/main/kotlin/com/bankcore/BankCoreApplication.kt`
  - `interest.batch.run-once=true`일 때만 동작하는 `ApplicationRunner` 추가
  - `DailyInterestAccrualScheduler.runDailyAccrualOnce()` 호출 후 정상 종료
- `src/main/kotlin/com/bankcore/interest/service/DailyInterestAccrualScheduler.kt`
  - `runOnce` 설정 주입
  - 스케줄 트리거는 run-once 모드에서 skip
  - 배치 본 실행 로직을 `executeAccrual()`로 분리
  - 실행 중 실패 카운트를 집계하고, 실패가 있으면 예외를 발생시켜 Job 실패로 반영

핵심 포인트:
- CronJob이 장기 실행 서버 프로세스로 남지 않고 1회 배치 수행 후 종료
- 실패를 로그만 남기고 삼키지 않고 Job 상태로 전파해 운영 감지 가능

### 4) GitHub Actions CI/CD
- `.github/workflows/cicd.yml`
  - `ci` job: main/development/feature/**에서 테스트 수행
  - `deploy` job: **main 브랜치에서만** 실행 (`if: github.ref == 'refs/heads/main'`)
  - OIDC 기반 AWS 자격 구성 → ECR 로그인 → 이미지 빌드/푸시
  - `envsubst` 설치 후 k8s 매니페스트 배포
  - 배포 전 `bank-core-secret` 존재 여부 fail-fast 검증
  - 배포 후 rollout status 검증(statefulset/deployment)

핵심 포인트:
- feature 브랜치의 무분별한 실환경 배포 차단
- 시크릿 누락 시 원인 명확한 실패
- apply 성공만으로 끝내지 않고 rollout 완료 여부 검증

### 5) 프로덕션 DB 인증 정보 정리
- `src/main/resources/application.yml`
  - prod datasource 계정을 환경변수 기반으로 주입 가능하도록 변경
  - `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` 우선 사용
  - 기존 `MYSQL_PASSWORD` fallback 유지

## 검증

### 실행한 테스트
- `./gradlew test --tests "com.bankcore.interest.service.DailyInterestAccrualSchedulerTest" --tests "com.bankcore.transfer.service.TransferServiceTest" --tests "com.bankcore.transfer.e2e.TransferConcurrencyReproductionTest" --tests "com.bankcore.transfer.e2e.TransferPessimisticLockIntegrationTest" --tests "com.bankcore.transfer.controller.TransferControllerTest"`

결과:
- 통과

## 배운 것
1. 배치 워크로드를 CronJob으로 올릴 때는 "앱 전체 기동"이 아니라 "단발 실행 후 종료" 경로를 명시적으로 제공해야 한다.
2. CI와 CD를 같은 워크플로에 두더라도, deploy 조건(main only)과 rollout 검증을 넣지 않으면 운영 리스크가 커진다.
3. 시크릿은 root/app 계정을 분리해 least-privilege를 보장해야 한다.
4. k8s 매니페스트에 securityContext, resources, concurrencyPolicy 같은 운영 기본값을 넣는 것이 장애/보안 대응 비용을 줄인다.
