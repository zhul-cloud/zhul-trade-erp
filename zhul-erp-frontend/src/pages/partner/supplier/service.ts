import { request } from '@umijs/max';

/** 一个主营品牌：正式品牌或待确认品牌；categories 为空表示该品牌全部品类 */
export interface SupplierProductScope {
  brandId: number | null;
  brandName: string;
  pending: boolean;
  categories: { id: number; name: string }[];
}

export interface SupplierItem {
  id: number;
  supplierCode: string;
  name: string;
  shortName: string;
  supplierType: number;
  industry: number;
  creditCode: string;
  legalRepresentative: string;
  /** 注册资本，单位万元人民币 */
  registeredCapital?: number | null;
  /** yyyy-MM-dd */
  establishedDate?: string | null;
  country: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  /** 省/市/区，用 / 分隔 */
  region: string;
  address: string;
  bankName: string;
  /** 列表、详情里是脱敏值；编辑取数接口返回明文 */
  bankAccount: string;
  productScopes: SupplierProductScope[];
  remark: string;
  status: number;
  createTime: string;
  createBy: string;
  updateTime: string;
  updateBy: string;
}

export interface SupplierQuery {
  supplierCode?: string;
  name?: string;
  creditCode?: string;
  supplierType?: number;
  status?: number;
  brandId?: number;
  categoryId?: number;
}

export interface SupplierFormValues {
  supplierCode?: string;
  name: string;
  shortName?: string;
  supplierType: number;
  industry?: number;
  status: number;
  creditCode?: string;
  legalRepresentative?: string;
  /** 以字符串提交，避免金额经过浮点数 */
  registeredCapital?: string | null;
  establishedDate?: string | null;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  region?: string;
  address?: string;
  bankName?: string;
  bankAccount?: string;
  remark?: string;
  productScopes: {
    brandId?: number;
    brandName?: string;
    categoryIds: number[];
  }[];
}

export interface BizErrorInfo {
  errorCode?: string;
  message: string;
}

/** 读取接口错误：业务错误码在 info.data.errorCode；参数校验失败（HTTP 400）的原因在 response.data.message */
export function readBizError(error: unknown): BizErrorInfo {
  const e = error as {
    message?: string;
    info?: { data?: { errorCode?: string } };
    response?: { data?: { message?: string } };
  };
  return {
    errorCode: e?.info?.data?.errorCode,
    message: e?.response?.data?.message ?? e?.message ?? '操作失败，请稍后重试',
  };
}

/** 页面自己处理错误时使用：不弹全局错误提示 */
const quiet = { skipErrorHandler: true } as const;
const BASE = '/api/v1/masterdata/suppliers';

export const supplierApi = {
  page: (params: SupplierQuery & { page: number; pageSize: number }) =>
    request<{ data: { records: SupplierItem[]; total: number } }>(
      `${BASE}/page`,
      { method: 'GET', params, ...quiet },
    ).then((r) => r.data),

  /** 不存在或已删除时返回 null */
  get: (id: number) =>
    request<{ data: SupplierItem | null }>(`${BASE}/${id}`, {
      method: 'GET',
      ...quiet,
    }).then((r) => r.data),

  /** 编辑页取数，银行账号为明文 */
  getForm: (id: number) =>
    request<{ data: SupplierItem }>(`${BASE}/${id}/form`, {
      method: 'GET',
      ...quiet,
    }).then((r) => r.data),

  /** 管理页新增：编码是唯一标识，同名允许存在，所以带 force 跳过"同名提示复用" */
  create: (data: SupplierFormValues) =>
    request(BASE, { method: 'POST', data: { ...data, force: true }, ...quiet }),

  update: (id: number, data: SupplierFormValues) =>
    request(`${BASE}/${id}`, { method: 'PUT', data, ...quiet }),

  updateStatus: (id: number, status: number) =>
    request(`${BASE}/${id}/status`, { method: 'PUT', data: { status } }),

  remove: (id: number) => request(`${BASE}/${id}`, { method: 'DELETE' }),

  batchRemove: (ids: number[]) =>
    request<{ data: { deleted: number; skipped: number } }>(
      `${BASE}/batch-delete`,
      { method: 'POST', data: { ids } },
    ).then((r) => r.data),

  /** 导出当前筛选结果为 xlsx 并触发下载 */
  async export(
    params: SupplierQuery,
  ): Promise<{ ok: true } | { ok: false; message: string }> {
    const res = await request(`${BASE}/export`, {
      method: 'GET',
      params,
      responseType: 'blob',
      getResponse: true,
      ...quiet,
    });
    const blob: Blob = res.data;
    if (blob.type.includes('json')) {
      try {
        const parsed = JSON.parse(await blob.text());
        return { ok: false, message: parsed.message || '导出失败，请稍后重试' };
      } catch {
        return { ok: false, message: '导出失败，请稍后重试' };
      }
    }
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `供应商基础信息_${new Date()
      .toISOString()
      .slice(0, 10)
      .replace(/-/g, '')}.xlsx`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    return { ok: true };
  },
};

// ---------- 省市区 ----------

export interface RegionNode {
  code: string;
  name: string;
  children?: RegionNode[];
}

// 区划是静态数据，整个页面生命周期里只请求一次
let regionCache: Promise<RegionNode[]> | null = null;
export const loadRegions = (): Promise<RegionNode[]> => {
  if (!regionCache) {
    regionCache = request<{ data: RegionNode[] }>(
      '/api/v1/masterdata/regions',
      { method: 'GET' },
    )
      .then((r) => r.data)
      .catch((e) => {
        regionCache = null;
        throw e;
      });
  }
  return regionCache;
};
