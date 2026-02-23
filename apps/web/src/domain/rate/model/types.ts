export interface BaseRateUpsertInput {
  businessDate: string;
  rate: number;
}

export interface SpreadRateUpsertInput {
  productCode: string;
  businessDate: string;
  rate: number;
}

export interface PreferentialRateUpsertInput {
  productCode: string;
  conditionCode: string;
  businessDate: string;
  rate: number;
}

export interface ComposedRate {
  productCode: string;
  businessDate: string;
  conditionCodes: string[];
  baseRate: number;
  spreadRate: number;
  preferentialRate: number;
  appliedRate: number;
}
