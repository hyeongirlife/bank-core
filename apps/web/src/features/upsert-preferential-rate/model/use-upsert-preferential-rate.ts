import { rateApi } from "@/domain/rate/api/client";
import { PreferentialRateUpsertInput } from "@/domain/rate/model/types";

function assertNotBlank(value: string, fieldName: string): void {
  if (value.trim().length === 0) {
    throw new Error(`${fieldName}은(는) 비어 있을 수 없습니다.`);
  }
}

function assertValidRate(rate: number): void {
  if (!Number.isFinite(rate) || rate <= 0) {
    throw new Error("금리는 0보다 큰 유효한 숫자여야 합니다.");
  }
}

export async function upsertPreferentialRate(
  input: PreferentialRateUpsertInput,
) {
  assertNotBlank(input.productCode, "상품 코드");
  assertNotBlank(input.conditionCode, "조건 코드");
  assertNotBlank(input.businessDate, "영업일");
  assertValidRate(input.rate);
  return rateApi.upsertPreferential(input);
}
