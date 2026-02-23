export interface DailyInterestAccrualInput {
  businessDate: string;
  conditionCodes?: string[];
}

export interface DailyInterestAccrualResult {
  accountId: number;
  businessDate: string;
  baseRate: number;
  spreadRate: number;
  preferentialRate: number;
  appliedRate: number;
  balanceSnapshot: number;
  interestAmount: number;
  alreadyProcessed: boolean;
}
