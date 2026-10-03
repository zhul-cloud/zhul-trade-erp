import { Select } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { searchSuppliers } from '@/services/zhul/masterdata';

/** 渠道为「供应商」时，从供应商主数据里搜索选择；店铺名以主数据名称为准 */
const SupplierSelect: React.FC<{
  value?: number;
  /** 已保存记录的供应商名称，搜索前用来显示当前值 */
  label?: string;
  status?: 'error';
  onChange: (supplierId: number | undefined, name: string) => void;
}> = ({ value, label, status, onChange }) => {
  const [options, setOptions] = useState<{ value: number; label: string }[]>(
    [],
  );
  const [loading, setLoading] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const search = (keyword: string) => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(async () => {
      setLoading(true);
      try {
        const list = await searchSuppliers(keyword.trim() || undefined);
        setOptions(list.map((s) => ({ value: s.id, label: s.name })));
      } catch {
        setOptions([]);
      } finally {
        setLoading(false);
      }
    }, 250);
  };

  useEffect(() => {
    search('');
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  const merged =
    value && label && !options.some((o) => o.value === value)
      ? [{ value, label }, ...options]
      : options;

  return (
    <Select
      size="small"
      allowClear
      placeholder="搜索供应商"
      value={value}
      status={status}
      loading={loading}
      options={merged}
      showSearch={{ onSearch: search, filterOption: false }}
      notFoundContent={loading ? '搜索中…' : '没有找到，请先在供应商管理中新建'}
      onChange={(v?: number) =>
        onChange(v, merged.find((o) => o.value === v)?.label ?? '')
      }
    />
  );
};

export default SupplierSelect;
