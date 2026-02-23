import { request } from "@/shared/api/http-client";
import { TransferInput, TransferResult } from "../model/types";

export const transferApi = {
  create(input: TransferInput) {
    return request<TransferResult, TransferInput>({
      method: "POST",
      path: "/api/transfers",
      body: input,
      idempotencyScope: "transfer-create",
    });
  },
};
