import { Select } from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { type ProductOption, productApi } from '../service';

/**
 * 商品选择器（C01），供询盘、报价等模块复用：
 * 远程搜索，输入防抖 300ms，只返回启用且未删除的商品，展示「展示型号 · 品牌 · 品类」。
 * 匹配归一化型号前缀和产品名称，所以 `6ES7 214` 与 `6es7214` 结果一致。
 */
export const ProductSelect: React.FC<{
  value?: number;
  onChange?: (id: number | undefined, option?: ProductOption) => void;
  brandId?: number;
  placeholder?: string;
  disabled?: boolean;
  style?: React.CSSProperties;
  'aria-label'?: string;
}> = ({
  value,
  onChange,
  brandId,
  placeholder = '输入型号或产品名称搜索',
  disabled,
  style,
  ...rest
}) => {
  const [options, setOptions] = useState<ProductOption[]>([]);
  const [loading, setLoading] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const seq = useRef(0);

  useEffect(() => () => clearTimeout(timer.current), []);

  const search = (keyword: string) => {
    clearTimeout(timer.current);
    if (!keyword.trim()) {
      setOptions([]);
      return;
    }
    timer.current = setTimeout(async () => {
      const mine = ++seq.current;
      setLoading(true);
      try {
        const list = await productApi.search(keyword, brandId, 20);
        // 只采用最后一次请求的结果，避免慢的旧请求覆盖新结果
        if (mine === seq.current) setOptions(list);
      } catch {
        if (mine === seq.current) setOptions([]);
      } finally {
        if (mine === seq.current) setLoading(false);
      }
    }, 300);
  };

  const selectOptions = useMemo(
    () =>
      options.map((o) => ({
        value: o.id,
        label: `${o.mpnDisplay} · ${o.brandName} · ${o.categoryName}`,
      })),
    [options],
  );

  return (
    <Select
      showSearch={{ onSearch: search, filterOption: false }}
      allowClear
      value={value}
      disabled={disabled}
      loading={loading}
      placeholder={placeholder}
      options={selectOptions}
      onChange={(id) =>
        onChange?.(
          id,
          options.find((o) => o.id === id),
        )
      }
      notFoundContent="没有找到启用的商品，换个型号写法试试"
      style={{ minWidth: 280, ...style }}
      {...rest}
    />
  );
};
