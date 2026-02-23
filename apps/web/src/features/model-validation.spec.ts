import { describe, expect, it, vi } from "vitest";

vi.mock("@/domain/account/api/client", () => ({
  accountApi: {
    create: vi.fn(async (input: unknown) => input),
    close: vi.fn(async (id: number) => ({ id })),
    deposit: vi.fn(async (id: number, body: { amount: number }) => ({
      id,
      amount: body.amount,
    })),
    withdraw: vi.fn(async (id: number, body: { amount: number }) => ({
      id,
      amount: body.amount,
    })),
  },
}));

vi.mock("@/domain/transfer/api/client", () => ({
  transferApi: {
    create: vi.fn(async (input: unknown) => input),
  },
}));

vi.mock("@/domain/rate/api/client", () => ({
  rateApi: {
    upsertBase: vi.fn(async (input: unknown) => input),
    upsertSpread: vi.fn(async (input: unknown) => input),
    upsertPreferential: vi.fn(async (input: unknown) => input),
    getComposed: vi.fn(async (input: unknown) => input),
  },
}));

import { calculateComposedRate } from "@/features/calculate-composed-rate/model/use-calculate-composed-rate";
import { closeAccount } from "@/features/close-account/model/use-close-account";
import { createTransfer } from "@/features/create-transfer/model/use-create-transfer";
import { depositMoney } from "@/features/deposit-money/model/use-deposit-money";
import { openAccount } from "@/features/open-account/model/use-open-account";
import { upsertBaseRate } from "@/features/upsert-base-rate/model/use-upsert-base-rate";
import { upsertPreferentialRate } from "@/features/upsert-preferential-rate/model/use-upsert-preferential-rate";
import { upsertSpreadRate } from "@/features/upsert-spread-rate/model/use-upsert-spread-rate";
import { withdrawMoney } from "@/features/withdraw-money/model/use-withdraw-money";

describe("feature model validation", () => {
  it("rejects invalid amount in deposit flow", async () => {
    await expect(depositMoney(1, 0)).rejects.toThrow(
      "입금 금액은 0보다 큰 유효한 숫자여야 합니다.",
    );
  });

  it("rejects invalid account id in withdraw flow", async () => {
    await expect(withdrawMoney(0, 1000)).rejects.toThrow(
      "계좌 ID는 1 이상의 정수여야 합니다.",
    );
  });

  it("rejects invalid account id in close flow", async () => {
    await expect(closeAccount(NaN)).rejects.toThrow(
      "계좌 ID는 1 이상의 정수여야 합니다.",
    );
  });

  it("rejects blank product code in open-account flow", async () => {
    await expect(
      openAccount({ customerId: 1, productCode: "  ", maturityDate: null }),
    ).rejects.toThrow("상품 코드은(는) 비어 있을 수 없습니다.");
  });

  it("rejects invalid transfer amount", async () => {
    await expect(
      createTransfer({ fromAccountId: 1, toAccountId: 2, amount: -1 }),
    ).rejects.toThrow("송금 금액은 0보다 큰 유효한 숫자여야 합니다.");
  });

  it("rejects invalid base rate", async () => {
    await expect(
      upsertBaseRate({ businessDate: "2026-02-23", rate: 0 }),
    ).rejects.toThrow("금리는 0보다 큰 유효한 숫자여야 합니다.");
  });

  it("rejects blank product code in spread rate", async () => {
    await expect(
      upsertSpreadRate({
        productCode: "",
        businessDate: "2026-02-23",
        rate: 0.01,
      }),
    ).rejects.toThrow("상품 코드은(는) 비어 있을 수 없습니다.");
  });

  it("rejects blank condition code in preferential rate", async () => {
    await expect(
      upsertPreferentialRate({
        productCode: "SAV001",
        conditionCode: "",
        businessDate: "2026-02-23",
        rate: 0.01,
      }),
    ).rejects.toThrow("조건 코드은(는) 비어 있을 수 없습니다.");
  });

  it("normalizes condition codes for composed-rate lookup", async () => {
    await expect(
      calculateComposedRate({
        productCode: "SAV001",
        businessDate: "2026-02-23",
        conditionCodes: [" SALARY_TRANSFER ", "", "CARD_USAGE"],
      }),
    ).resolves.toEqual({
      productCode: "SAV001",
      businessDate: "2026-02-23",
      conditionCodes: ["SALARY_TRANSFER", "CARD_USAGE"],
    });
  });
});
