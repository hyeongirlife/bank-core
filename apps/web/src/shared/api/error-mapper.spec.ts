import { describe, expect, it } from "vitest";

import { mapApiError } from "./error-mapper";
import { ApiError } from "../types/http";

describe("mapApiError", () => {
  it("maps 400 to non-retryable input error", () => {
    const result = mapApiError(new ApiError("raw", 400));

    expect(result).toEqual({
      title: "입력 오류",
      message: "입력값을 확인해주세요.",
      retryable: false,
    });
  });

  it("maps 409 to retryable conflict error", () => {
    const result = mapApiError(new ApiError("raw", 409));

    expect(result).toEqual({
      title: "처리 충돌",
      message: "동시성 충돌이 발생했습니다. 다시 시도해주세요.",
      retryable: true,
    });
  });

  it("maps unknown errors to fallback UI message", () => {
    const result = mapApiError(new Error("boom"));

    expect(result).toEqual({
      title: "알 수 없는 오류",
      message: "잠시 후 다시 시도해주세요.",
      retryable: true,
    });
  });
});
