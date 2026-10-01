import { request } from '@umijs/max';

/** 一个主营品牌：正式品牌或待确认品牌；categories 为空表示该品牌全部品类 */
export interface SupplierProductScope {
  brandId: number | null;
  brandName: string;
  pending: boolean;
  categories: { id: number; name: string }[];
}

/** 收款账户。详情里账号、手机号脱敏，编辑取数里为明文；身份证号任何时候都只给脱敏值 */
export interface SupplierAccount {
  id: number;
  /** 1-对公、2-对私 */
  accountType: number;
  accountName: string;
  bankName: string;
  accountNo: string;
  payeePhone: string;
  payeeIdNoMasked: string;
  defaultAccount: boolean;
}

/** 已保存的附件（不含存储路径，经下载接口获取文件） */
export interface SupplierAttachment {
  id: number;
  /** 1-营业执照、2-开户许可证、3-资质证书、4-合同、5-其他 */
  category: number;
  fileName: string;
  fileSize: number;
  contentType: string;
  createBy: string;
  createTime: string;
}

/** 上传接口返回：保存供应商时带上 fileKey 才生效 */
export interface UploadedAttachment {
  fileKey: string;
  fileName: string;
  fileSize: number;
  contentType: string;
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
  wechat: string;
  /** 省/市/区，用 / 分隔 */
  region: string;
  address: string;
  /** 列表里为空，详情与编辑取数才有 */
  accounts: SupplierAccount[];
  /** 列表里为空，详情与编辑取数才有 */
  attachments: SupplierAttachment[];
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
  wechat?: string;
  region?: string;
  address?: string;
  remark?: string;
  /** 带 id 为更新已有账户；payeeIdNo 不传表示沿用原值、空串表示清空 */
  accounts: {
    id?: number;
    accountType: number;
    accountName: string;
    bankName: string;
    accountNo: string;
    payeePhone?: string;
    payeeIdNo?: string;
    defaultAccount: boolean;
  }[];
  /** 带 id 为保留已有附件；不带 id 时 fileKey 为上传接口返回值 */
  attachments: {
    id?: number;
    category: number;
    fileName?: string;
    fileKey?: string;
  }[];
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

  /** 管理页新增：编码由系统生成，同名允许存在，所以带 force 跳过"同名提示复用" */
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

  /** 上传附件到私有存储，返回的 fileKey 随供应商保存才生效 */
  uploadAttachment: (file: File) => {
    const data = new FormData();
    data.append('file', file);
    return request<{ data: UploadedAttachment }>(`${BASE}/attachments`, {
      method: 'POST',
      data,
      requestType: 'form',
      ...quiet,
    }).then((r) => r.data);
  },

  /** 取附件文件（接口需要登录与权限，不能直接用 <a href>），返回浏览器本地地址，用完需 revoke */
  attachmentBlobUrl: async (supplierId: number, attachmentId: number) => {
    const blob: Blob = await request(
      `${BASE}/${supplierId}/attachments/${attachmentId}`,
      {
        method: 'GET',
        params: { inline: true },
        responseType: 'blob',
        ...quiet,
      },
    );
    return window.URL.createObjectURL(blob);
  },

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
