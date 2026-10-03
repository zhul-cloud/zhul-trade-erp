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
