export function createIdempotencyKey(scope: string): string {
  if (
    typeof crypto === "undefined" ||
    typeof crypto.randomUUID !== "function"
  ) {
    throw new Error(
      "Secure idempotency key generation is unavailable in this environment.",
    );
  }

  return `${scope}:${crypto.randomUUID()}`;
}
