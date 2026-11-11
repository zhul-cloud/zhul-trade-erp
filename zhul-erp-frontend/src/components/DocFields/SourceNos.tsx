import { Link, useLocation } from '@umijs/max';
import { Popover } from 'antd';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/** 一个来源单据：编号与详情地址（没有可打开的页面时不传 href，只显示文字） */
export interface SourceRef {
  no?: string;
  href?: string;
}

const RefLink: React.FC<{ r: SourceRef & { no: string } }> = ({ r }) =>
  r.href ? (
    <Link to={r.href} style={{ whiteSpace: 'nowrap' }}>
      {r.no}
    </Link>
  ) : (
    <span style={{ whiteSpace: 'nowrap' }}>{r.no}</span>
  );

/**
 * 来源单号（spec system/navigation「来源单号」）。
 * - inline：详情页表头说明行，「来源单号 A、B」全部列出
 * - cell：列表单元格，一个显示单号，多个显示「A 等 N 个」，悬停列出全部
 * 没有来源时显示 empty（如「手动创建」），默认「—」。
 */
const SourceNos: React.FC<{
  refs: SourceRef[];
  mode?: 'inline' | 'cell';
  empty?: React.ReactNode;
}> = ({ refs, mode = 'cell', empty = '—' }) => {
  const { palette } = useAppTheme();
  const list = refs.filter((r): r is SourceRef & { no: string } => !!r.no);
  if (mode === 'inline') {
    return (
      <span>
        <span style={{ color: palette.mute }}>来源单号 </span>
        {list.length === 0
          ? empty
          : list.map((r, i) => (
              <React.Fragment key={`${r.no}-${r.href ?? ''}`}>
                {i > 0 && '、'}
                <RefLink r={r} />
              </React.Fragment>
            ))}
      </span>
    );
  }
  if (list.length === 0)
    return <span style={{ color: palette.mute }}>{empty}</span>;
  if (list.length === 1) return <RefLink r={list[0]} />;
  return (
    <Popover
      title={`来源单号（${list.length}）`}
      content={
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          {list.map((r) => (
            <RefLink key={`${r.no}-${r.href ?? ''}`} r={r} />
          ))}
        </div>
      }
    >
      <span style={{ whiteSpace: 'nowrap' }}>
        <RefLink r={list[0]} />
        <span style={{ color: palette.sub }}> 等 {list.length} 个</span>
      </span>
    </Popover>
  );
};

/**
 * 没有详情页的单据（供应商发货单、入库单、出库单）：打开所在列表并按单号筛好。
 * 列表页用 useUrlFilter 读出 keyword 与 tab 作为初始筛选。
 */
export const listHref = (path: string, no: string, tab?: string) =>
  `${path}?${new URLSearchParams(tab ? { keyword: no, tab } : { keyword: no })}`;

export const useUrlFilter = () => {
  const params = new URLSearchParams(useLocation().search);
  return { keyword: params.get('keyword') ?? '', tab: params.get('tab') };
};

export default SourceNos;
