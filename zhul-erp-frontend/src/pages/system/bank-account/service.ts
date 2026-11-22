import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

/** 账户类型：1-对公、2-对私 */
export const ACCOUNT_TYPE = { PUBLIC: 1, PRIVATE: 2 } as const;

/** 付款渠道：1-银行卡、2-支付宝、3-微信（后两种只能是对私账户） */
export const PAY_CHANNEL = { BANK: 1, ALIPAY: 2, WECHAT: 3 } as const;
export const PAY_CHANNEL_NAMES: Record<number, string> = {
  1: '银行卡',
  2: '支付宝',
  3: '微信',
};

export interface BankAccount {
  id: number;
  accountType: number;
  /** 付款渠道：1-银行卡、2-支付宝、3-微信 */
  payChannel?: number;
  /** 所属事业部（公司设置了事业部时必选；默认账户按事业部 + 币种各一个） */
  businessUnitId?: number;
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
  accountType: number;
  payChannel?: number;
  /** 所属事业部（公司设置了事业部时必选；默认账户按事业部 + 币种各一个） */
  businessUnitId?: number;
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
