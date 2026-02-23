export type AccountStatus = "ACTIVE" | "CLOSED";

export interface Account {
  id: number;
  customerId: number;
  accountNumber: string;
  productCode: string;
  productName: string;
  balance: number;
  status: AccountStatus;
  openedAt: string;
  closedAt: string | null;
}

export interface CreateAccountInput {
  customerId: number;
  productCode: string;
  maturityDate?: string | null;
}

export interface BalanceChangeInput {
  amount: number;
}
