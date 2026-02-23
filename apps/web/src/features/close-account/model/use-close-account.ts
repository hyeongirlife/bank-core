import { accountApi } from "@/domain/account/api/client";

function assertValidAccountId(accountId: number): void {
  if (!Number.isInteger(accountId) || accountId <= 0) {
    throw new Error("계좌 ID는 1 이상의 정수여야 합니다.");
  }
}

export async function closeAccount(accountId: number) {
  assertValidAccountId(accountId);
  return accountApi.close(accountId);
}
