import { rateApi } from "@/domain/rate/api/client";

function assertNotBlank(value: string, fieldName: string): void {
  if (value.trim().length === 0) {
    throw new Error(`${fieldName}은(는) 비어 있을 수 없습니다.`);
  }
}

function normalizeConditionCodes(conditionCodes?: string[]): string[] {
  return (conditionCodes ?? []).map((code) => code.trim()).filter(Boolean);
}

export async function calculateComposedRate(input: {
  productCode: string;
  businessDate: string;
  conditionCodes?: string[];
}) {
  assertNotBlank(input.productCode, "상품 코드");
  assertNotBlank(input.businessDate, "영업일");
  return rateApi.getComposed({
    ...input,
    conditionCodes: normalizeConditionCodes(input.conditionCodes),
  });
}
