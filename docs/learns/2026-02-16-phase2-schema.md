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
