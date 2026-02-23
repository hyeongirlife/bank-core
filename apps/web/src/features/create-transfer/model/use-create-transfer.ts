import { transferApi } from "@/domain/transfer/api/client";
import { TransferInput } from "@/domain/transfer/model/types";

function assertValidAccountId(accountId: number): void {
  if (!Number.isInteger(accountId) || accountId <= 0) {
    throw new Error("계좌 ID는 1 이상의 정수여야 합니다.");
  }
}

function assertValidAmount(amount: number): void {
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new Error("송금 금액은 0보다 큰 유효한 숫자여야 합니다.");
  }
}

export async function createTransfer(input: TransferInput) {
  assertValidAccountId(input.fromAccountId);
  assertValidAccountId(input.toAccountId);
  assertValidAmount(input.amount);
  return transferApi.create(input);
}
