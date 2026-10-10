import { request } from '@umijs/max';
import { useEffect, useState } from 'react';

/** 来源渠道：客户、商机、客户询盘共用，在「系统管理 / 字典管理」维护 */
export const DICT_SOURCE_CHANNEL = 'crm_source_channel';

/** 货况、货期：采购回价、导入询价结果、历史询价共用 */
export const DICT_ITEM_CONDITION = 'inquiry_item_condition';
export const DICT_LEAD_TIME = 'inquiry_lead_time';

/** 型号生命周期：码值 2-停产可填替代型号，3-待查为默认值 */
export const DICT_LIFECYCLE = 'inquiry_lifecycle';

/** 询盘等级 S/A/B/C：码值越小越优先，默认 3-B */
export const DICT_INQUIRY_LEVEL = 'inquiry_level';

/** 询价税率：含税报价的增值税税率，字典值为百分比整数，默认 13 */
export const DICT_TAX_RATE = 'inquiry_tax_rate';

export interface DictOption {
  value: number;
  label: string;
  enabled: boolean;
}

interface DictItem {
  itemName: string;
  itemValue: string;
  status: number;
}

/** 同一字典类型在页面生命周期内只请求一次，失败时下次调用重试 */
const cache = new Map<string, Promise<DictOption[]>>();

const loadDict = (dictType: string) => {
  let p = cache.get(dictType);
  if (!p) {
    p = request<{ data: DictItem[] }>('/api/v1/system/dict-items', {
      params: { dictType },
    }).then((res) =>
      (res.data ?? [])
        .map((d) => ({
          value: Number(d.itemValue),
          label: d.itemName,
          enabled: d.status === 1,
        }))
        .filter((o) => Number.isInteger(o.value)),
    );
    p.catch(() => cache.delete(dictType));
    cache.set(dictType, p);
  }
  return p;
};

/** 整数码值字典：options 只含启用项用于下拉，labelOf 含停用项用于展示历史数据；未加载完时显示 fallback */
export const useDictOptions = (dictType: string, fallback = '—') => {
  const [all, setAll] = useState<DictOption[]>([]);
  useEffect(() => {
    let alive = true;
    loadDict(dictType)
      .then((o) => alive && setAll(o))
      .catch(() => alive && setAll([]));
    return () => {
      alive = false;
    };
  }, [dictType]);
  const options = all
    .filter((o) => o.enabled)
    .map(({ value, label }) => ({ value, label }));
  const labelOf = (v?: number | null) =>
    all.find((o) => o.value === v)?.label ?? fallback;
  return { options, labelOf };
};

// ---------------------------------------------------------------- 文字字典（单据上显示英文的下拉）

/** 报价单、PI 的下拉字典：字典带英文名，单据上显示英文；都可以直接填写字典以外的内容 */
export const DICT_TRADE_TERM_PLACE = 'trade_term_place';
export const DICT_WARRANTY = 'warranty';
export const DICT_PI_DELIVERY_TIME = 'pi_delivery_time';
export const DICT_PI_PAYMENT_TERM = 'pi_payment_term';
export const DICT_PORT_OF_SHIPMENT = 'port_of_shipment';

export interface DictText {
  code: string;
  /** 保存与显示在单据上的文字：英文名，没有英文名时用中文名 */
  text: string;
  /** 中文名，作为下拉里的说明 */
  name: string;
  isDefault: boolean;
}

interface DictTextItem {
  itemCode: string;
  itemName: string;
  itemNameEn?: string;
  isDefault?: number;
  status: number;
}

const textCache = new Map<string, Promise<DictText[]>>();

const loadTexts = (dictType: string) => {
  let p = textCache.get(dictType);
  if (!p) {
    p = request<{ data: DictTextItem[] }>('/api/v1/system/dict-items', {
      params: { dictType },
    }).then((res) =>
      (res.data ?? [])
        .filter((d) => d.status === 1)
        .map((d) => ({
          code: d.itemCode,
          text: d.itemNameEn || d.itemName,
          name: d.itemName,
          isDefault: d.isDefault === 1,
        })),
    );
    p.catch(() => textCache.delete(dictType));
    textCache.set(dictType, p);
  }
  return p;
};

/** 启用的文字字典项（按字典排序） */
export const useDictTexts = (dictType: string) => {
  const [items, setItems] = useState<DictText[]>([]);
  useEffect(() => {
    let alive = true;
    loadTexts(dictType)
      .then((o) => alive && setItems(o))
      .catch(() => alive && setItems([]));
    return () => {
      alive = false;
    };
  }, [dictType]);
  return items;
};

// ---------------------------------------------------------------- 付款方式（线上 / 线下）

export interface PaymentMethod {
  code: string;
  name: string;
  /** 字典项的值为 ONLINE 时是线上付款方式 */
  online: boolean;
  isDefault: boolean;
}

let paymentMethodsCache: Promise<PaymentMethod[]> | undefined;

/** 启用的付款方式；online 传 true / false 时只返回线上 / 线下项 */
export const usePaymentMethods = (online?: boolean) => {
  const [items, setItems] = useState<PaymentMethod[]>([]);
  useEffect(() => {
    let alive = true;
    if (!paymentMethodsCache) {
      paymentMethodsCache = request<{
        data: (DictTextItem & { itemValue?: string })[];
      }>('/api/v1/system/dict-items', {
        params: { dictType: 'payment_method' },
      }).then((res) =>
        (res.data ?? [])
          .filter((d) => d.status === 1)
          .map((d) => ({
            code: d.itemCode,
            name: d.itemName,
            online: (d.itemValue ?? '').toUpperCase() === 'ONLINE',
            isDefault: d.isDefault === 1,
          })),
      );
      paymentMethodsCache.catch(() => {
        paymentMethodsCache = undefined;
      });
    }
    paymentMethodsCache
      .then((list) => alive && setItems(list))
      .catch(() => alive && setItems([]));
    return () => {
      alive = false;
    };
  }, []);
  return online == null ? items : items.filter((m) => m.online === online);
};
