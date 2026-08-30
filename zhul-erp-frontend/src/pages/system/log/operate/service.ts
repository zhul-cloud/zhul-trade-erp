import { request } from '@umijs/max';

export interface OperateLogItem {
  id: number;
  operatorName: string;
  menu: string;
  operation: string;
  result: 0 | 1;
  ip: string;
  operateTime: string;
}

export interface OperateLogDetail extends OperateLogItem {
  contentEmpty: boolean;
  contentBroken: boolean;
  rawContent?: string;
  before?: unknown;
  after?: unknown;
}

export interface OperateLogQueryParams {
  current?: number;
  pageSize?: number;
  operatorName?: string;
  menu?: string;
  operation?: string;
  result?: 0 | 1;
  startTime?: string;
  endTime?: string;
}

export async function getOperateLogList(
  params: OperateLogQueryParams,
): Promise<{ data: OperateLogItem[]; total: number; success: boolean }> {
  const res = await request('/api/v1/system/logs/operate', {
    method: 'GET',
    params: {
      page: params.current,
      pageSize: params.pageSize,
      operatorName: params.operatorName,
      menu: params.menu,
      operation: params.operation,
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

export const getOperateModules = (): Promise<string[]> =>
  request('/api/v1/system/logs/operate/modules', { method: 'GET' }).then(
    (r) => r.data ?? [],
  );

export const getOperateLogDetail = (id: number): Promise<OperateLogDetail> =>
  request(`/api/v1/system/logs/operate/${id}`, { method: 'GET' }).then(
    (r) => r.data,
  );

export async function exportOperateLogs(
  params: Omit<OperateLogQueryParams, 'current' | 'pageSize'>,
): Promise<{ ok: true } | { ok: false; message: string }> {
  const res = await request('/api/v1/system/logs/operate/export', {
    method: 'GET',
    params: {
      operatorName: params.operatorName,
      menu: params.menu,
      operation: params.operation,
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
  link.download = `操作日志_${new Date()
    .toISOString()
    .slice(0, 10)
    .replace(/-/g, '')}.xlsx`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
  return { ok: true };
}
