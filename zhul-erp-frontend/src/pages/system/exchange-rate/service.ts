import { request } from '@umijs/max';

export interface ExchangeRate {
  currencyCode: string;
  /** 1 外币 = rate 人民币；没有设置时为空 */
  rate?: number;
  sourceName?: string;
  updatedByName?: string;
  rateTime?: string;
}

export interface ExchangeRateLog {
  currencyCode: string;
  oldRate?: number;
  newRate: number;
  operatorName?: string;
  operatedAt: string;
}

const BASE = '/api/v1/system/exchange-rates';
const quiet = { skipErrorHandler: true } as const;

export const exchangeRateApi = {
  list: () =>
    request<{ data: ExchangeRate[] }>(BASE, { method: 'GET', ...quiet }).then(
      (r) => r.data,
    ),
  save: (currency: string, rate: number) =>
    request<{ data: ExchangeRate }>(`${BASE}/${currency}`, {
      method: 'PUT',
      data: { rate },
      ...quiet,
    }).then((r) => r.data),
  logs: (currency: string) =>
    request<{ data: ExchangeRateLog[] }>(`${BASE}/${currency}/logs`, {
      method: 'GET',
      ...quiet,
    }).then((r) => r.data),
};
