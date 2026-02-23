import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type MutableEnv = {
  NODE_ENV?: string;
  NEXT_PUBLIC_API_BASE_URL?: string;
};

const mutableEnv = process.env as MutableEnv;
const ORIGINAL_NODE_ENV = mutableEnv.NODE_ENV;
const ORIGINAL_API_BASE_URL = mutableEnv.NEXT_PUBLIC_API_BASE_URL;

async function loadEnvModule() {
  vi.resetModules();
  return import("./env");
}

beforeEach(() => {
  mutableEnv.NODE_ENV = ORIGINAL_NODE_ENV;
  mutableEnv.NEXT_PUBLIC_API_BASE_URL = ORIGINAL_API_BASE_URL;
});

afterEach(() => {
  mutableEnv.NODE_ENV = ORIGINAL_NODE_ENV;
  mutableEnv.NEXT_PUBLIC_API_BASE_URL = ORIGINAL_API_BASE_URL;
});

describe("env config", () => {
  it("trims trailing slash from configured base URL", async () => {
    mutableEnv.NODE_ENV = "production";
    mutableEnv.NEXT_PUBLIC_API_BASE_URL = "https://api.example.com/";

    const { env } = await loadEnvModule();

    expect(env.apiBaseUrl).toBe("https://api.example.com");
  });

  it("rejects non-https URL outside development", async () => {
    mutableEnv.NODE_ENV = "production";
    mutableEnv.NEXT_PUBLIC_API_BASE_URL = "http://api.example.com";

    await expect(loadEnvModule()).rejects.toThrow(
      "NEXT_PUBLIC_API_BASE_URL must use https outside development environment.",
    );
  });

  it("falls back to localhost in development", async () => {
    mutableEnv.NODE_ENV = "development";
    mutableEnv.NEXT_PUBLIC_API_BASE_URL = "";

    const { env } = await loadEnvModule();

    expect(env.apiBaseUrl).toBe("http://localhost:8080");
  });

  it("throws when base URL is missing outside development", async () => {
    mutableEnv.NODE_ENV = "test";
    mutableEnv.NEXT_PUBLIC_API_BASE_URL = "";

    await expect(loadEnvModule()).rejects.toThrow(
      "NEXT_PUBLIC_API_BASE_URL is required outside development environment.",
    );
  });
});
