export interface TransferInput {
  fromAccountId: number;
  toAccountId: number;
  amount: number;
}

export interface TransferResult {
  fromAccountId: number;
  toAccountId: number;
  amount: number;
  fromBalance: number;
  toBalance: number;
  transferredAt: string;
}
