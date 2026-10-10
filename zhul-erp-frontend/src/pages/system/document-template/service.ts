import { request } from '@umijs/max';

export interface TemplateType {
  docType: number;
  name: string;
  /** 是否已实现生成（PI / CI / PL 随订单模块上线） */
  generatable: boolean;
  defaultVersionNo?: number;
  versionCount: number;
}

export interface TemplateVersion {
  id: number;
  docType: number;
  versionNo: number;
  note: string;
  fileName?: string;
  content?: string;
  builtin: boolean;
  enabled: boolean;
  isDefault: boolean;
  uploadedByName?: string;
  createTime: string;
  warnings?: string[];
}

export const TEXT_QUOTE = 5;
const BASE = '/api/v1/system/document-templates';
const quiet = { skipErrorHandler: true } as const;

const blob = async (url: string, params?: object) => {
  const res = (await request<Blob>(url, {
    method: 'GET',
    params,
    responseType: 'blob',
    getResponse: true,
    ...quiet,
  })) as unknown as { data: Blob; headers: Record<string, string | undefined> };
  if (res.data.type.includes('json')) {
    const body = JSON.parse(await res.data.text()) as { message?: string };
    throw new Error(body.message || '操作失败，请稍后重试');
  }
  const disposition = res.headers?.['content-disposition'] ?? '';
  const match = /filename\*=UTF-8''([^;]+)/.exec(disposition);
  return {
    data: res.data,
    name: match ? decodeURIComponent(match[1]) : 'download',
  };
};

const save = (data: Blob, name: string) => {
  const href = window.URL.createObjectURL(data);
  const a = document.createElement('a');
  a.href = href;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(href), 1000);
};

export const templateApi = {
  types: () =>
    request<{ data: TemplateType[] }>(BASE, { method: 'GET', ...quiet }).then(
      (r) => r.data,
    ),
  versions: (docType: number) =>
    request<{ data: TemplateVersion[] }>(`${BASE}/${docType}/versions`, {
      method: 'GET',
      ...quiet,
    }).then((r) => r.data),
  upload: (docType: number, file: File, note: string) => {
    const data = new FormData();
    data.append('file', file);
    data.append('note', note);
    return request<{ data: TemplateVersion }>(`${BASE}/${docType}/versions`, {
      method: 'POST',
      data,
      requestType: 'form',
      ...quiet,
    }).then((r) => r.data);
  },
  saveText: (content: string, note: string) =>
    request<{ data: TemplateVersion }>(`${BASE}/text-versions`, {
      method: 'POST',
      data: { content, note },
      ...quiet,
    }).then((r) => r.data),
  setDefault: (id: number) =>
    request(`${BASE}/versions/${id}/default`, { method: 'PUT', ...quiet }),
  setEnabled: (id: number, enabled: boolean) =>
    request(`${BASE}/versions/${id}/enabled`, {
      method: 'PUT',
      params: { enabled },
      ...quiet,
    }),
  download: async (id: number) => {
    const f = await blob(`${BASE}/versions/${id}/file`);
    save(f.data, f.name);
  },
  /** 用示例数据按指定版本导出：PDF 在新窗口打开，文字报价返回文本 */
  preview: async (id: number, format: 'pdf' | 'xlsx' | 'text') => {
    const f = await blob(`${BASE}/versions/${id}/preview`, {
      format: format === 'text' ? undefined : format,
    });
    if (format === 'text') return f.data.text();
    if (format === 'pdf') {
      window.open(window.URL.createObjectURL(f.data), '_blank', 'noopener');
    } else {
      save(f.data, f.name);
    }
    return undefined;
  },
};
