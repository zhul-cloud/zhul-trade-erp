import { request } from '@umijs/max';

export interface LoginLogItem {
  id: number;
  operatorName: string;
  ip: string;
  location: string;
  browser: string;
  os: string;
  result: 0 | 1;
  online: boolean;
  checkable: boolean;
  tokenId: string | null;
  operateTime: string;
}

export interface LoginLogQueryParams {
  current?: number;
  pageSize?: number;
  operatorName?: string;
  ip?: string;
  result?: 0 | 1;
  startTime?: string;
  endTime?: string;
}

export async function getLoginLogList(
  params: LoginLogQueryParams,
): Promise<{ data: LoginLogItem[]; total: number; success: boolean }> {
  const res = await request('/api/v1/system/logs/login', {
    method: 'GET',
    params: {
      page: params.current,
      pageSize: params.pageSize,
      operatorName: params.operatorName,
      ip: params.ip,
      result: params.result,
      startTime: params.startTime,
      endTime: params.endTime,
    },
  });
  return {
    data: res.data?.records ?? [],
    total: res.data?.total ?? 0,
    success: true,
  };
}

export interface ForceLogoutResult {
  successCount: number;
  failCount: number;
}

export const forceLogout = (tokenIds: string[]): Promise<ForceLogoutResult> =>
  request('/api/v1/system/logs/login/force-logout', {
    method: 'POST',
    data: { tokenIds },
  }).then((r) => r.data);

export async function exportLoginLogs(
  params: Omit<LoginLogQueryParams, 'current' | 'pageSize'>,
): Promise<{ ok: true } | { ok: false; message: string }> {
  const res = await request('/api/v1/system/logs/login/export', {
    method: 'GET',
    params: {
      operatorName: params.operatorName,
      ip: params.ip,
      result: params.result,
      startTime: params.startTime,
      endTime: params.endTime,
    },
    responseType: 'blob',
    getResponse: true,
    skipErrorHandler: true,
  });

  const blob: Blob = res.data;
  if (blob.type.includes('json')) {
    const text = await blob.text();
    try {
      const parsed = JSON.parse(text);
      return { ok: false, message: parsed.message || '导出失败，请稍后重试' };
    } catch {
      return { ok: false, message: '导出失败，请稍后重试' };
    }
  }

  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  const ts = new Date()
    .toISOString()
    .replace(/[-:]/g, '')
    .replace('T', '_')
    .slice(0, 15);
  link.download = `登录日志_${ts}.xlsx`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
  return { ok: true };
}
