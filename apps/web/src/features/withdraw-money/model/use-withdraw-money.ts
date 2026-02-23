import { accountApi } from "@/domain/account/api/client";

function assertValidAmount(amount: number): void {
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new Error("출금 금액은 0보다 큰 유효한 숫자여야 합니다.");
  }
}

function assertValidAccountId(accountId: number): void {
  if (!Number.isInteger(accountId) || accountId <= 0) {
    throw new Error("계좌 ID는 1 이상의 정수여야 합니다.");
  }
}

export async function withdrawMoney(accountId: number, amount: number) {
  assertValidAccountId(accountId);
  assertValidAmount(amount);
  return accountApi.withdraw(accountId, { amount });
}
