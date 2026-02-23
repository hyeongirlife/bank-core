import { ApiError } from "../types/http";

export interface UiError {
  title: string;
  message: string;
  retryable: boolean;
}

export function mapApiError(error: unknown): UiError {
  if (error instanceof ApiError) {
    if (error.status === 400) {
      return {
        title: "입력 오류",
        message: "입력값을 확인해주세요.",
        retryable: false,
      };
    }
    if (error.status === 404) {
      return {
        title: "데이터 없음",
        message: "요청한 데이터를 찾을 수 없습니다.",
        retryable: false,
      };
    }
    if (error.status === 409) {
      return {
        title: "처리 충돌",
        message: "동시성 충돌이 발생했습니다. 다시 시도해주세요.",
        retryable: true,
      };
    }
    if (error.status === 408 || error.status === 0) {
      return {
        title: "네트워크 오류",
        message: "네트워크 상태를 확인한 뒤 다시 시도해주세요.",
        retryable: true,
      };
    }

    return {
      title: "요청 실패",
      message: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.",
      retryable: true,
    };
  }

  return {
    title: "알 수 없는 오류",
    message: "잠시 후 다시 시도해주세요.",
    retryable: true,
  };
}
