# web frontend scaffold

이 디렉토리는 B 구조(도메인 + 플로우 하이브리드) 기반 프론트엔드 초기 스캐폴드입니다.

## 구조 원칙
- app/: 라우트 엔트리(최소 조합)
- domain/: 도메인별 API/모델/UI
- features/: 사용자 액션(useCase) 단위
- widgets/: 페이지 조합 블록
- shared/: 공통 인프라/유틸

## API 기준
백엔드 컨트롤러 계약을 기준으로 구현합니다.
- accounts: /api/accounts
- transfers: /api/transfers
- rates: /api/rates
- interests: /api/accounts/{accountId}/interests/daily-accrual
