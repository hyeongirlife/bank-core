import { env } from "../config/env";
import { ApiError, ApiErrorResponse, HttpMethod } from "../types/http";
import { createIdempotencyKey } from "./idempotency-key";

const DEFAULT_TIMEOUT_MS = 10_000;

interface RequestOptions<TBody> {
  method: HttpMethod;
  path: string;
  body?: TBody;
  idempotencyScope?: string;
  headers?: Record<string, string>;
  timeoutMs?: number;
}

export async function request<TResponse, TBody = unknown>(
  options: RequestOptions<TBody>,
): Promise<TResponse> {
  const headers: Record<string, string> = {
    ...(options.headers ?? {}),
  };

  const hasBody = options.body !== undefined;
  if (hasBody) {
    headers["Content-Type"] = headers["Content-Type"] ?? "application/json";
  }

  if (options.method === "POST") {
    const idempotencyScope = options.idempotencyScope ?? "post-request";
    headers["Idempotency-Key"] =
      headers["Idempotency-Key"] ?? createIdempotencyKey(idempotencyScope);
  }

  const controller = new AbortController();
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const timeout = setTimeout(() => controller.abort(), timeoutMs);

  let response: Response;
  try {
    response = await fetch(`${env.apiBaseUrl}${options.path}`, {
      method: options.method,
      headers,
      body: hasBody ? JSON.stringify(options.body) : undefined,
      signal: controller.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new ApiError("요청 시간이 초과되었습니다. 다시 시도해주세요.", 408);
    }
    throw new ApiError(
      "네트워크 오류가 발생했습니다. 다시 시도해주세요.",
      0,
      error,
    );
  } finally {
    clearTimeout(timeout);
  }

  if (!response.ok) {
    let payload: ApiErrorResponse | undefined;
    try {
      payload = (await response.json()) as ApiErrorResponse;
    } catch {
      payload = undefined;
    }

    throw new ApiError(
      payload?.error ?? `${response.status} 요청 실패`,
      response.status,
      payload,
    );
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  const contentType = response.headers.get("Content-Type") ?? "";
  if (!contentType.toLowerCase().includes("application/json")) {
    return undefined as TResponse;
  }

  try {
    return (await response.json()) as TResponse;
  } catch (error) {
    throw new ApiError(
      "응답 처리 중 오류가 발생했습니다. 다시 시도해주세요.",
      response.status,
      error,
    );
  }
}
