import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type MutableEnv = {
  NODE_ENV?: string;
  NEXT_PUBLIC_API_BASE_URL?: string;
};

const mutableEnv = process.env as MutableEnv;
const ORIGINAL_NODE_ENV = mutableEnv.NODE_ENV;
const ORIGINAL_API_BASE_URL = mutableEnv.NEXT_PUBLIC_API_BASE_URL;

async function loadRequest() {
  vi.resetModules();
  const module = await import("./http-client");
  return module.request;
}

beforeEach(() => {
  mutableEnv.NODE_ENV = "test";
  mutableEnv.NEXT_PUBLIC_API_BASE_URL = "https://api.example.com";
  vi.restoreAllMocks();
});

afterEach(() => {
  mutableEnv.NODE_ENV = ORIGINAL_NODE_ENV;
  mutableEnv.NEXT_PUBLIC_API_BASE_URL = ORIGINAL_API_BASE_URL;
  vi.restoreAllMocks();
});

describe("http-client request", () => {
  it("adds idempotency key for POST requests", async () => {
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(
        new Response(JSON.stringify({ ok: true }), { status: 200 }),
      );

    const request = await loadRequest();
    await request<{ ok: boolean }>({
      method: "POST",
      path: "/api/accounts",
      body: { customerId: 1, productCode: "SAV001" },
      idempotencyScope: "account-create",
    });

    const [, options] = fetchSpy.mock.calls[0] ?? [];
    const headers = options?.headers as Record<string, string>;

    expect(headers["Idempotency-Key"]).toContain("account-create:");
  });

  it("throws ApiError for non-ok responses", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ error: "invalid request" }), {
        status: 400,
      }),
    );

    const request = await loadRequest();
    await expect(
      request<{ ok: boolean }>({
        method: "GET",
        path: "/api/accounts/1",
      }),
    ).rejects.toMatchObject({
      name: "ApiError",
      status: 400,
      message: "invalid request",
    });
  });

  it("returns undefined for 204 responses", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 204 }),
    );

    const request = await loadRequest();
    const response = await request<void>({
      method: "POST",
      path: "/api/accounts/1/close",
      idempotencyScope: "account-close",
    });

    expect(response).toBeUndefined();
  });

  it("returns undefined for non-json success responses", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("ok", {
        status: 200,
        headers: { "Content-Type": "text/plain" },
      }),
    );

    const request = await loadRequest();
    const response = await request<void>({
      method: "GET",
      path: "/api/health",
    });

    expect(response).toBeUndefined();
  });

  it("throws timeout ApiError when fetch aborts", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(
      new DOMException("The operation was aborted.", "AbortError"),
    );

    const request = await loadRequest();
    await expect(
      request<{ ok: boolean }>({
        method: "GET",
        path: "/api/accounts/1",
        timeoutMs: 1,
      }),
    ).rejects.toEqual(
      expect.objectContaining({
        name: "ApiError",
        status: 408,
        message: "요청 시간이 초과되었습니다. 다시 시도해주세요.",
      }),
    );
  });

  it("throws network ApiError for unknown fetch failures", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("socket closed"));

    const request = await loadRequest();
    await expect(
      request<{ ok: boolean }>({
        method: "GET",
        path: "/api/accounts/1",
      }),
    ).rejects.toEqual(
      expect.objectContaining({
        name: "ApiError",
        status: 0,
        message: "네트워크 오류가 발생했습니다. 다시 시도해주세요.",
      }),
    );
  });

  it("throws when secure idempotency key generation is unavailable", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { status: 200 }),
    );

    const originalCrypto = globalThis.crypto;
    Object.defineProperty(globalThis, "crypto", {
      value: undefined,
      configurable: true,
    });

    try {
      const request = await loadRequest();
      await expect(
        request<{ ok: boolean }>({
          method: "POST",
          path: "/api/transfers",
          body: { fromAccountId: 1, toAccountId: 2, amount: 1000 },
          idempotencyScope: "transfer-create",
        }),
      ).rejects.toThrow(
        "Secure idempotency key generation is unavailable in this environment.",
      );
    } finally {
      Object.defineProperty(globalThis, "crypto", {
        value: originalCrypto,
        configurable: true,
      });
    }
  });

  it("throws structured ApiError payload for failed calls", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ error: "conflict" }), { status: 409 }),
    );

    const request = await loadRequest();
    await expect(
      request<{ ok: boolean }>({ method: "GET", path: "/api/transfers" }),
    ).rejects.toEqual(
      expect.objectContaining({
        name: "ApiError",
        status: 409,
        message: "conflict",
      }),
    );
  });

  it("throws ApiError when json parsing fails on json response", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("not-json", {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const request = await loadRequest();
    await expect(
      request<{ ok: boolean }>({ method: "GET", path: "/api/transfers" }),
    ).rejects.toEqual(
      expect.objectContaining({
        name: "ApiError",
        status: 200,
        message: "응답 처리 중 오류가 발생했습니다. 다시 시도해주세요.",
      }),
    );
  });
});
