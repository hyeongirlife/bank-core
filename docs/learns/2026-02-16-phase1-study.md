# 2026-02-16 Phase 1 완료 후 학습 정리

## 오늘 한 것
- Kotlin + Spring Boot 3.4.3 + JDK 21 + Gradle 프로젝트 세팅
- 도메인형 패키지 구조 (account, rate, interest)
- docker-compose (MySQL 8.0 + Redis 7)
- Flyway 마이그레이션, Spring Profiles (local/prod)
- Spring Actuator로 헬스체크 대체

## Phase 2 전에 공부할 것

### 1. Kotlin 기본 (30분)
- `data class`: equals, hashCode, copy 자동 생성
- null safety: `?`, `!!`, `?.let {}`, `?:` (엘비스 연산자)
- `val` / `var`: val 우선 (불변 선호)
- `sealed class`: 상태 표현 (AccountStatus 등)
- `extension function`: BigDecimal 유틸 만들 때 유용
- 참고: https://kotlinlang.org/docs/basic-syntax.html

### 2. Spring Boot 핵심 (2-3시간)
- DI / IoC: `@Component` 스캔, 생성자 주입
- `@Transactional`: 프록시 기반 동작, 전파 속성, 롤백 조건
- Spring Data JPA: `JpaRepository`, 쿼리 메서드, `@Query`
- 참고: https://www.baeldung.com/spring-boot

### 3. JPA / Hibernate
- 엔티티 매핑: `@Entity`, `@Id`, `@GeneratedValue`
- 연관관계: `@ManyToOne`, `@OneToMany`
- 영속성 컨텍스트: 1차 캐시, 더티 체킹 (save 안 해도 변경 감지)
- ⚠️ TypeORM/Prisma와 다르게 영속성 컨텍스트 개념이 핵심

### 4. Flyway
- 네이밍: `V1__init.sql`, `V2__create_account.sql`
- 한번 적용된 마이그레이션은 수정 불가 (체크섬 검증)

### 5. Gradle Kotlin DSL
- `allOpen` 플러그인: JPA 엔티티가 open 클래스여야 하는 이유 (프록시)
- npm/yarn 대응: Java 세계의 패키지 매니저 + 빌드 도구

### 6. BigDecimal (Phase 3 대비)
```kotlin
// ❌ 부동소수점 오류
0.1 + 0.2  // 0.30000000000000004

// ✅ BigDecimal
BigDecimal("0.1") + BigDecimal("0.2")  // 0.3

// 원단위 절사
BigDecimal("1234.567").setScale(0, RoundingMode.DOWN)  // 1234
```

## 학습 우선순위
1. 지금 바로: Kotlin 기본 문법 (30분)
2. Phase 2 전: Spring DI + JPA 엔티티 매핑 + @Transactional (2-3시간)
3. Phase 3 전: BigDecimal (30분)
4. Phase 4 전: JPA 락 전략 - @Lock, @Version (1시간)
