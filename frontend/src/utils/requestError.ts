/** 从 Umi request / Axios 错误中提取业务 message */
export function getRequestErrorMessage(error: unknown, fallback = '请求失败'): string {
  if (!error || typeof error !== 'object') {
    return fallback;
  }
  const err = error as {
    response?: { data?: { message?: string; code?: number } };
    info?: { message?: string };
    message?: string;
  };
  return (
    err.response?.data?.message ||
    err.info?.message ||
    err.message ||
    fallback
  );
}

export function isUnauthorizedError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false;
  }
  const err = error as {
    name?: string;
    response?: { status?: number; data?: { code?: number } };
    info?: { code?: number };
  };
  if (err.name === 'UnauthorizedError') return true;
  if (err.response?.status === 401) return true;
  if (err.response?.data?.code === 20001) return true;
  if (err.info?.code === 20001) return true;
  return false;
}

export function isAuthEndpoint(url?: string): boolean {
  if (!url) return false;
  return url.includes('/auth/login') || url.includes('/auth/refresh');
}
