import {
  ArrowsAltOutlined,
  DownOutlined,
  RightOutlined,
  ShrinkOutlined,
} from '@ant-design/icons';
import { Checkbox, Pagination, Segmented } from 'antd';
import React, { useCallback, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { Pill } from './components';
import type { BrandGroup } from './service';

/** 把本页任务按品牌分组，保持接口返回的先后顺序 */
export const groupByBrand = <T extends { brand: string }>(tasks: T[]) => {
  const map = new Map<string, T[]>();
  for (const t of tasks) {
    const list = map.get(t.brand) ?? [];
    list.push(t);
    map.set(t.brand, list);
  }
  return [...map.entries()];
};

/**
 * 品牌分组的展开 / 折叠：先选「全部展开 / 全部折叠」，再记录单独点开或收起的品牌。
 * 翻页、切换页签时保留，新出现的品牌跟随全部的选择。
 */
export const useFold = () => {
  const [mode, setMode] = useState<'open' | 'fold'>('open');
  const [toggled, setToggled] = useState<Set<string>>(new Set());
  const isFolded = useCallback(
    (brand: string) => (mode === 'fold') !== toggled.has(brand),
    [mode, toggled],
  );
  const toggle = useCallback((brand: string) => {
    setToggled((prev) => {
      const next = new Set(prev);
      if (next.has(brand)) next.delete(brand);
      else next.add(brand);
      return next;
    });
  }, []);
  const setAll = useCallback((m: 'open' | 'fold') => {
    setMode(m);
    setToggled(new Set());
  }, []);
  return { mode, isFolded, toggle, setAll };
};

export const FoldToggle: React.FC<{
  mode: 'open' | 'fold';
  onChange: (m: 'open' | 'fold') => void;
}> = ({ mode, onChange }) => (
  <Segmented
    size="small"
    value={mode}
    onChange={(v) => onChange(v as 'open' | 'fold')}
    options={[
      { value: 'open', label: '全部展开', icon: <ArrowsAltOutlined /> },
      { value: 'fold', label: '全部折叠', icon: <ShrinkOutlined /> },
    ]}
  />
);

/** 品牌组头：点击展开 / 收起；本页只有部分任务时写明全部页的总数 */
export const BrandGroupHeader: React.FC<{
  brand: string;
  /** 本页这个品牌的任务数 */
  count: number;
  summary?: BrandGroup;
  folded: boolean;
  onToggle: () => void;
  /** 组头勾选，不传则不显示 */
  checkbox?: {
    checked: boolean;
    indeterminate: boolean;
    onChange: (v: boolean) => void;
  };
  /** 组头右侧的说明，如最久等待 */
  extra?: React.ReactNode;
}> = ({ brand, count, summary, folded, onToggle, checkbox, extra }) => {
  const { palette } = useAppTheme();
  const total = summary?.total ?? count;
  // 按钮去掉默认样式，组头看起来仍是一整行
  const plain: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    padding: 0,
    border: 0,
    background: 'transparent',
    color: 'inherit',
    font: 'inherit',
    cursor: 'pointer',
  };
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '14px 20px',
        background: palette.inset,
      }}
    >
      <button
        type="button"
        aria-label={folded ? `展开 ${brand}` : `收起 ${brand}`}
        aria-expanded={!folded}
        onClick={onToggle}
        style={plain}
      >
        {folded ? (
          <RightOutlined style={{ color: palette.mute, fontSize: 12 }} />
        ) : (
          <DownOutlined style={{ color: palette.mute, fontSize: 12 }} />
        )}
      </button>
      {checkbox && (
        <Checkbox
          aria-label={`选择本页 ${brand} 的任务`}
          checked={checkbox.checked}
          indeterminate={checkbox.indeterminate}
          onChange={(e) => checkbox.onChange(e.target.checked)}
        />
      )}
      <button
        type="button"
        aria-expanded={!folded}
        onClick={onToggle}
        style={{ ...plain, flex: 1, minWidth: 0, textAlign: 'left' }}
      >
        <span style={{ fontSize: 15, fontWeight: 700, color: palette.ink }}>
          {brand}
        </span>
        <Pill tone="gray">
          {folded || total === count
            ? `${total} 个任务`
            : `本页 ${count} 个 · 共 ${total} 个`}
        </Pill>
        {!!summary?.urgent && <Pill tone="red">紧急 {summary.urgent}</Pill>}
        {!!summary?.timeout && <Pill tone="red">超时 {summary.timeout}</Pill>}
        {extra && (
          <span
            style={{ marginLeft: 'auto', fontSize: 12, whiteSpace: 'nowrap' }}
          >
            {extra}
          </span>
        )}
      </button>
    </div>
  );
};

/** 底部分页：共几个任务 + 页码 + 每页条数 */
export const TaskPager: React.FC<{
  total: number;
  page: number;
  pageSize: number;
  onChange: (page: number, pageSize: number) => void;
}> = ({ total, page, pageSize, onChange }) => {
  const { palette } = useAppTheme();
  if (total === 0) return null;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
      <span style={{ color: palette.mute, fontSize: 12 }}>
        共 {total} 个任务
      </span>
      <Pagination
        style={{ marginLeft: 'auto' }}
        current={page}
        pageSize={pageSize}
        total={total}
        showSizeChanger
        totalBoundaryShowSizeChanger={0}
        pageSizeOptions={[10, 20, 50, 100]}
        onChange={onChange}
      />
    </div>
  );
};
