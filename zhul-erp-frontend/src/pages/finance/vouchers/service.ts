import { request } from '@umijs/max';

/** 业务凭证（openspec inventory-vouchers，spec finance/business-voucher） */

const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

export const CATEGORIES = [
  { value: 1, label: '采购入库', tone: 'accent' },
  { value: 2, label: '销售成本结转', tone: 'violet' },
  { value: 3, label: '其他出库', tone: 'orange' },
  { value: 4, label: '成本调整', tone: 'green' },
] as const;

export const voucherPath = (month: string) => `/finance/vouchers/${month}`;

export interface VoucherMonth {
  month: string;
  /** false：上线前结账、还没有凭证 */
  generated: boolean;
  voucherCount?: number;
  debitTotal?: number;
  currencyCode: string;
  /** 核对差额，0 为相符 */
  reconDiff?: number;
  exportCount?: number;
  exportedByName?: string;
  exportedAt?: string;
}

export interface VoucherEntry {
  itemKey: string;
  itemName: string;
  accountCode: string;
  accountName: string;
  partnerId?: number;
  partnerName?: string;
  debit?: number;
  credit?: number;
}

export interface Voucher {
  id: number;
  voucherDate: string;
  category: number;
  categoryName: string;
  sourceDocType: string;
  sourceDocId: number;
  sourceDocNo: string;
  bizType: string;
  summary: string;
  /** 摘要拆开：动作、单号、往来单位 */
  action: string;
  partnerText: string;
  debitTotal: number;
  entries: VoucherEntry[];
}

export interface VoucherMonthDetail {
  month: string;
  currencyCode: string;
  generated: boolean;
  generatedByName?: string;
  generatedAt?: string;
  exportCount?: number;
  exportedByName?: string;
  exportedAt?: string;
  /** 期初 + 凭证里库存商品净额 + 不生成凭证的变动 = 期末 */
  recon: {
    openingAmount: number;
    stockNetAmount: number;
    noVoucherAmount: number;
    closingAmount: number;
    diff: number;
    noVoucherItems: { bizType: string; name: string; amount: number }[];
  };
  categoryCounts: Record<string, number>;
  debitTotal: number;
  missingItems: string[];
  periodCogsAmount: number;
  returnInAmount: number;
  vouchers: Voucher[];
}

export interface VoucherAccount {
  itemKey: string;
  group: string;
  itemName: string;
  accountCode: string;
  accountName: string;
}

export interface VoucherSupplier {
  supplierId: number;
  supplierName: string;
  supplierCode: string;
  partnerCode: string;
}

/** 带业务错误码的错误，readBizError 能读出 errorCode 与 detail */
class BizError extends Error {
  info: { data: { errorCode?: string; detail?: Record<string, unknown> } };
  constructor(
    message: string,
    errorCode?: string,
    detail?: Record<string, unknown>,
  ) {
    super(message);
    this.info = { data: { errorCode, detail } };
  }
}

const V = '/api/v1/finance/vouchers';

/** 导出 T+ 凭证表；接口返回 JSON 时是业务错误（VOUCHER_EXPORT_CONFIRM、VOUCHER_ACCOUNT_MISSING） */
const exportMonth = async (month: string, confirmed: boolean) => {
  const res = (await request<Blob>(`${V}/months/${month}/export`, {
    method: 'POST',
    params: { confirmed },
    responseType: 'blob',
    getResponse: true,
    ...quiet,
  })) as unknown as {
    data: Blob;
    headers: Record<string, string | undefined>;
  };
  if (res.data.type.includes('json')) {
    const r = JSON.parse(await res.data.text()) as {
      message?: string;
      data?: { errorCode?: string; detail?: Record<string, unknown> };
    };
    throw new BizError(
      r.message || '导出失败，请稍后重试',
      r.data?.errorCode,
      r.data?.detail,
    );
  }
  const disposition = res.headers?.['content-disposition'] ?? '';
  const match = /filename\*=UTF-8''([^;]+)/.exec(disposition);
  const href = window.URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = href;
  a.download = match ? decodeURIComponent(match[1]) : `业务凭证-${month}.xlsx`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(href), 1000);
};

export const voucherApi = {
  months: () => get<VoucherMonth[]>(`${V}/months`),
  detail: (month: string) => get<VoucherMonthDetail>(`${V}/months/${month}`),
  generate: (month: string) =>
    send<VoucherMonthDetail>('POST', `${V}/months/${month}/generate`),
  export: exportMonth,
  accounts: () => get<VoucherAccount[]>(`${V}/accounts`),
  saveAccounts: (
    items: Pick<VoucherAccount, 'itemKey' | 'accountCode' | 'accountName'>[],
  ) => send<VoucherAccount[]>('PUT', `${V}/accounts`, { items }),
  suppliers: (keyword?: string) =>
    get<VoucherSupplier[]>(`${V}/suppliers`, { keyword }),
  saveSupplier: (supplierId: number, partnerCode: string) =>
    send<VoucherSupplier>('PUT', `${V}/suppliers/${supplierId}`, {
      partnerCode,
    }),
};
