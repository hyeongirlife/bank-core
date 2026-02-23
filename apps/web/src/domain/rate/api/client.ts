import { request } from "@/shared/api/http-client";
import {
  BaseRateUpsertInput,
  ComposedRate,
  PreferentialRateUpsertInput,
  SpreadRateUpsertInput,
} from "../model/types";

export const rateApi = {
  upsertBase(input: BaseRateUpsertInput) {
    return request<BaseRateUpsertInput, BaseRateUpsertInput>({
      method: "POST",
      path: "/api/rates/base",
      body: input,
      idempotencyScope: `rate-base-${input.businessDate}`,
    });
  },

  upsertSpread(input: SpreadRateUpsertInput) {
    return request<SpreadRateUpsertInput, SpreadRateUpsertInput>({
      method: "POST",
      path: "/api/rates/spread",
      body: input,
      idempotencyScope: `rate-spread-${input.productCode}-${input.businessDate}`,
    });
  },

  upsertPreferential(input: PreferentialRateUpsertInput) {
    return request<PreferentialRateUpsertInput, PreferentialRateUpsertInput>({
      method: "POST",
      path: "/api/rates/preferential",
      body: input,
      idempotencyScope: `rate-pref-${input.productCode}-${input.conditionCode}-${input.businessDate}`,
    });
  },

  getComposed(params: { productCode: string; businessDate: string; conditionCodes?: string[] }) {
    const search = new URLSearchParams();
    search.set("productCode", params.productCode);
    search.set("businessDate", params.businessDate);
    (params.conditionCodes ?? []).forEach((code) => search.append("conditionCodes", code));

    return request<ComposedRate>({
      method: "GET",
      path: `/api/rates/composed?${search.toString()}`,
    });
  },
};
