import { request } from "@/shared/api/http-client";
import { Account, BalanceChangeInput, CreateAccountInput } from "../model/types";

export const accountApi = {
  create(input: CreateAccountInput) {
    return request<Account, CreateAccountInput>({
      method: "POST",
      path: "/api/accounts",
      body: input,
      idempotencyScope: "account-create",
    });
  },

  getById(id: number) {
    return request<Account>({
      method: "GET",
      path: `/api/accounts/${id}`,
    });
  },

  deposit(id: number, input: BalanceChangeInput) {
    return request<Account, BalanceChangeInput>({
      method: "POST",
      path: `/api/accounts/${id}/deposit`,
      body: input,
      idempotencyScope: `account-deposit-${id}`,
    });
  },

  withdraw(id: number, input: BalanceChangeInput) {
    return request<Account, BalanceChangeInput>({
      method: "POST",
      path: `/api/accounts/${id}/withdraw`,
      body: input,
      idempotencyScope: `account-withdraw-${id}`,
    });
  },

  close(id: number) {
    return request<Account>({
      method: "POST",
      path: `/api/accounts/${id}/close`,
      idempotencyScope: `account-close-${id}`,
    });
  },
};
