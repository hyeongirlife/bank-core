function parseApiBaseUrl(value: string): string {
  const normalized = value.trim().replace(/\/+$/, "");

  let parsed: URL;
  try {
    parsed = new URL(normalized);
  } catch {
    throw new Error("NEXT_PUBLIC_API_BASE_URL must be a valid URL.");
  }

  const isDevelopment = process.env.NODE_ENV === "development";
  if (!isDevelopment && parsed.protocol !== "https:") {
    throw new Error(
      "NEXT_PUBLIC_API_BASE_URL must use https outside development environment.",
    );
  }

  return normalized;
}

function resolveApiBaseUrl(): string {
  const value = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (value && value.trim().length > 0) {
    return parseApiBaseUrl(value);
  }

  if (process.env.NODE_ENV === "development") {
    return "http://localhost:8080";
  }

  throw new Error(
    "NEXT_PUBLIC_API_BASE_URL is required outside development environment.",
  );
}

export const env = {
  apiBaseUrl: resolveApiBaseUrl(),
};
