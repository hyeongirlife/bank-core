import { request } from "@/shared/api/http-client";
import { DailyInterestAccrualInput, DailyInterestAccrualResult } from "../model/types";

export const interestApi = {
  accrueDaily(accountId: number, input: DailyInterestAccrualInput) {
    return request<DailyInterestAccrualResult, DailyInterestAccrualInput>({
      method: "POST",
      path: `/api/accounts/${accountId}/interests/daily-accrual`,
      body: input,
      idempotencyScope: `interest-daily-${accountId}-${input.businessDate}`,
    });
  },
};
