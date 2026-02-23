import { accountApi } from "@/domain/account/api/client";
import { CreateAccountInput } from "@/domain/account/model/types";

function assertValidCustomerId(customerId: number): void {
  if (!Number.isInteger(customerId) || customerId <= 0) {
    throw new Error("고객 ID는 1 이상의 정수여야 합니다.");
  }
}

function assertNotBlank(value: string, fieldName: string): void {
  if (value.trim().length === 0) {
    throw new Error(`${fieldName}은(는) 비어 있을 수 없습니다.`);
  }
}

export async function openAccount(input: CreateAccountInput) {
  assertValidCustomerId(input.customerId);
  assertNotBlank(input.productCode, "상품 코드");
  return accountApi.create(input);
}
