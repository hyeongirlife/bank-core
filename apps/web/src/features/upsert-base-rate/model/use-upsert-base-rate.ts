import { rateApi } from "@/domain/rate/api/client";
import { BaseRateUpsertInput } from "@/domain/rate/model/types";

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

export async function upsertBaseRate(input: BaseRateUpsertInput) {
  assertNotBlank(input.businessDate, "영업일");
  assertValidRate(input.rate);
  return rateApi.upsertBase(input);
}
