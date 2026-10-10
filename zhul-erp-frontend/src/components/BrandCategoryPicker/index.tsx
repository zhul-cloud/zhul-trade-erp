import { AutoComplete, Select } from 'antd';
import React, { useEffect, useState } from 'react';
import { brandApi, categoryApi } from '@/pages/product/service';

/**
 * 品牌 / 品类选择：取商品主数据做下拉搜索，品牌可按别名搜（如「三菱」找到 Mitsubishi），
 * 品类按一级品类分组、中英文名都能搜。值统一存名称（品类取中文名）。
 *
 * - 单选（默认）：可直接输入主数据里没有的值，适合询盘里出现的冷门品牌
 * - multiple：多选；allowCustom 为真时也可输入新值（回车确认）
 */

interface PickOption {
  value: string;
  label: React.ReactNode;
  /** 用于搜索的全部写法：名称、英文名、别名，小写后用 | 连接 */
  search: string;
}

interface PickGroup {
  label: string;
  options: PickOption[];
}

type Kind = 'brand' | 'category';

/** 同一页面多个选择器共用一次请求；失败时下次挂载重试 */
const cache: Partial<Record<Kind, Promise<PickOption[] | PickGroup[]>>> = {};

const loadBrands = () =>
  brandApi.options().then((list) =>
    list.map((b) => ({
      value: b.brandName,
      label: b.aliases?.length
        ? `${b.brandName}（${b.aliases.join('、')}）`
        : b.brandName,
      search: [b.brandName, ...(b.aliases ?? [])].join('|').toLowerCase(),
    })),
  );

const loadCategories = () =>
  categoryApi.options(0).then((list) => {
    const seen = new Set<string>();
    const toOption = (c: (typeof list)[number]): PickOption | null => {
      const value = c.categoryNameZh || c.categoryName;
      if (seen.has(value)) return null;
      seen.add(value);
      return {
        value,
        label: value,
        search: `${value}|${c.categoryName}`.toLowerCase(),
      };
    };
    return list
      .filter((c) => !c.parentId)
      .map((p) => ({
        label: p.categoryNameZh || p.categoryName,
        options: [p, ...list.filter((c) => c.parentId === p.id)]
          .map(toOption)
          .filter((o): o is PickOption => !!o),
      }))
      .filter((g) => g.options.length);
  });

const load = (kind: Kind) => {
  let p = cache[kind];
  if (!p) {
    p = kind === 'brand' ? loadBrands() : loadCategories();
    p.catch(() => {
      delete cache[kind];
    });
    cache[kind] = p;
  }
  return p;
};

const useOptions = (kind: Kind) => {
  const [options, setOptions] = useState<PickOption[] | PickGroup[]>([]);
  useEffect(() => {
    let alive = true;
    load(kind)
      .then((o) => alive && setOptions(o))
      .catch(() => alive && setOptions([]));
    return () => {
      alive = false;
    };
  }, [kind]);
  return options;
};

const matches = (input: string, option?: object) => {
  const search = (option as { search?: unknown } | undefined)?.search;
  return (
    typeof search === 'string' && search.includes(input.trim().toLowerCase())
  );
};

interface CommonProps {
  kind: Kind;
  placeholder?: string;
  size?: 'small' | 'middle' | 'large';
  status?: 'error' | 'warning';
  style?: React.CSSProperties;
  disabled?: boolean;
}

interface SingleProps extends CommonProps {
  multiple?: false;
  value?: string;
  onChange?: (value: string) => void;
  /** 从下拉里选中一项（输入时不触发） */
  onSelect?: (value: string) => void;
  onBlur?: () => void;
}

interface MultipleProps extends CommonProps {
  multiple: true;
  value?: string[];
  onChange?: (value: string[]) => void;
  /** 允许输入主数据里没有的值，默认允许 */
  allowCustom?: boolean;
}

export type BrandCategoryPickerProps = SingleProps | MultipleProps;

const BrandCategoryPicker: React.FC<BrandCategoryPickerProps> = (props) => {
  const options = useOptions(props.kind);
  const placeholder =
    props.placeholder ??
    (props.kind === 'brand' ? '品牌，可搜索' : '品类，可搜索');
  const popupWidth = props.kind === 'brand' ? 260 : 200;

  if (props.multiple) {
    return (
      <Select<string[]>
        mode={props.allowCustom === false ? 'multiple' : 'tags'}
        size={props.size}
        status={props.status}
        style={props.style}
        disabled={props.disabled}
        placeholder={placeholder}
        value={props.value}
        onChange={props.onChange}
        options={options}
        showSearch={{ filterOption: matches }}
        tokenSeparators={[',', '，', '、']}
        popupMatchSelectWidth={popupWidth}
      />
    );
  }
  return (
    <AutoComplete
      size={props.size}
      status={props.status}
      style={props.style}
      disabled={props.disabled}
      placeholder={placeholder}
      value={props.value}
      onChange={props.onChange}
      onSelect={(v: string) => props.onSelect?.(v)}
      onBlur={props.onBlur}
      options={options}
      showSearch={{ filterOption: matches }}
      popupMatchSelectWidth={popupWidth}
    />
  );
};

export default BrandCategoryPicker;
