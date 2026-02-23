export function formatMoney(value: string | number): string {
  const amount = typeof value === "string" ? Number(value) : value;
  if (!Number.isFinite(amount)) {
    return "0";
  }

  return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 2 }).format(
    amount,
  );
}
