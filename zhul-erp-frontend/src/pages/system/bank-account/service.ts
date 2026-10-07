import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

export interface BankAccount {
  id: number;
  currencyCode: string;
  bankName: string;
  accountName: string;
  accountNoMasked: string;
  swiftCode?: string;
  country?: string;
  bankAddress?: string;
  bankCode?: string;
  branchCode?: string;
  remark?: string;
  isDefault: boolean;
  enabled: boolean;
  updateTime?: string;
  updateBy?: string;
}

export interface SaveBankAccount {
  currencyCode: string;
  bankName: string;
  accountName: string;
  accountNo: string;
  swiftCode?: string;
  country?: string;
  bankAddress?: string;
  bankCode?: string;
  branchCode?: string;
  remark?: string;
}

const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(
  method: string,
  url: string,
  data?: unknown,
  params?: object,
) =>
  request<{ data: T }>(url, { method, data, params, ...quiet }).then(
    (r) => r.data,
  );

const API = '/api/v1/system/bank-accounts';

export const bankAccountApi = {
  list: () => get<BankAccount[]>(API),
  /** 编辑时取完整账号 */
  get: (id: number) => get<BankAccount & { accountNo: string }>(`${API}/${id}`),
  create: (body: SaveBankAccount) => send<BankAccount>('POST', API, body),
  update: (id: number, body: SaveBankAccount) =>
    send<BankAccount>('PUT', `${API}/${id}`, body),
  setDefault: (id: number) => send<void>('PUT', `${API}/${id}/default`),
  setEnabled: (id: number, enabled: boolean) =>
    send<void>('PUT', `${API}/${id}/enabled`, undefined, { enabled }),
};

export const prefixApi = {
  get: () =>
    get<{ prefix: string }>('/api/v1/system/document-numbering/prefix'),
  save: (prefix: string) =>
    send<{ prefix: string }>(
      'PUT',
      '/api/v1/system/document-numbering/prefix',
      {
        prefix,
      },
    ),
};
