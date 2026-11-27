import { Select } from 'antd';
import { ArrowLeftRight, Bot } from 'lucide-react';
import React, { useMemo } from 'react';
import { Pill } from '../components/Pills';
import type {
  CategoryOption,
  CompletenessSummary,
  Product,
  RefineResult,
  RefineState,
  RefineTask,
} from '../service';
import { useProductTheme } from '../theme';

/** 商品品类细分（spec product/category-refine）：列表上的「品类未细分」三档、AI 建议列、改选下拉 */

/** 一次 AI 细分、一次批量采纳的上限（与后端一致） */
export const REFINE_MAX = 200;

const JOB_KEY = 'zhul:product:category-refine';

/** 运行中的 AI 细分存在浏览器里，离开页面再回来继续看进度 */
export const readRefineJob = (): RefineTask | null => {
  try {
    const raw = localStorage.getItem(JOB_KEY);
    return raw ? (JSON.parse(raw) as RefineTask) : null;
  } catch {
    return null;
  }
};

export const writeRefineJob = (job: RefineTask | null) => {
  try {
    if (job) localStorage.setItem(JOB_KEY, JSON.stringify(job));
    else localStorage.removeItem(JOB_KEY);
  } catch {
    // 浏览器禁用存储时只是离开页面后看不到进度，不影响细分结果
  }
};

/** 跑完的结果：给出建议 X 个，判断不出 Y 个，跳过 N 个（含提交时跳过的和运行期间品类被改的） */
export const refineSummary = (job: RefineTask) => {
  const skipped = (job.skipped ?? 0) + (job.discarded ?? 0);
  return [
    `给出建议 ${job.suggested ?? 0} 个`,
    `判断不出 ${job.unknown ?? 0} 个`,
    skipped ? `跳过 ${skipped} 个` : '',
  ]
    .filter(Boolean)
    .join(' · ');
};

const TABS: {
  key: RefineState;
  label: string;
  field: keyof CompletenessSummary;
}[] = [
  { key: 'todo', label: '待 AI 细分', field: 'refineTodo' },
  { key: 'pending', label: '待确认建议', field: 'refinePending' },
  { key: 'ignored', label: '已忽略', field: 'refineIgnored' },
];

export const RefineTabs: React.FC<{
  summary?: CompletenessSummary;
  value: RefineState;
  onChange: (v: RefineState) => void;
}> = ({ summary, value, onChange }) => {
  const { palette } = useProductTheme();
  return (
    <div
      role="tablist"
      aria-label="品类未细分"
      style={{
        display: 'inline-flex',
        gap: 4,
        padding: 4,
        borderRadius: 12,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      {TABS.map((t) => {
        const on = t.key === value;
        return (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={on}
            onClick={() => onChange(t.key)}
            style={{
              height: 30,
              padding: '0 14px',
              borderRadius: 8,
              border: `1px solid ${on ? palette.primaryLine : 'transparent'}`,
              background: on ? palette.primarySoft : 'transparent',
              color: on ? palette.ink : palette.sub,
              fontWeight: on ? 600 : 400,
              fontSize: 13,
              cursor: 'pointer',
            }}
          >
            {t.label}{' '}
            <span
              className="num"
              style={{
                color: on ? palette.link : palette.mute,
                fontWeight: 600,
              }}
            >
              {summary?.[t.field] ?? 0}
            </span>
          </button>
        );
      })}
    </div>
  );
};

/** AI 建议的细分品类；跨一级品类时标「改一级品类」 */
export const AiSuggestion: React.FC<{ row: Product }> = ({ row }) => {
  const { palette } = useProductTheme();
  if (!row.aiCategoryName)
    return <span style={{ color: palette.mute }}>—</span>;
  return (
    <div style={{ display: 'grid', gap: 4, justifyItems: 'start' }}>
      <span style={{ fontWeight: 600, color: palette.ink }}>
        <Bot size="1em" style={{ color: palette.link, marginRight: 6 }} />
        {row.aiCategoryName}
      </span>
      {row.aiCategoryCross && (
        <Pill tone="orange">
          <ArrowLeftRight size="1em" /> 改一级品类
        </Pill>
      )}
    </div>
  );
};

/** 改选细分品类：当前一级品类下的排前面，AI 建议的那个标出来 */
export const RechooseSelect: React.FC<{
  row: Product;
  categories: CategoryOption[];
  value?: number;
  onChange: (v: number) => void;
}> = ({ row, categories, value, onChange }) => {
  const options = useMemo(() => {
    const name = (c: CategoryOption) => c.categoryNameZh || c.categoryName;
    const label = (c: CategoryOption) =>
      c.id === row.aiCategoryId ? `${name(c)}（AI 建议）` : name(c);
    const subs = categories.filter((c) => c.parentId);
    const mine = subs.filter((c) => c.parentId === row.categoryId);
    const rest = subs.filter((c) => c.parentId !== row.categoryId);
    const parentName = new Map(
      categories.filter((c) => !c.parentId).map((c) => [c.id, name(c)]),
    );
    const toOption = (c: CategoryOption) => ({
      value: c.id,
      label: label(c),
      search: `${name(c)} ${parentName.get(c.parentId ?? 0) ?? ''}`,
    });
    return [
      ...(mine.length
        ? [
            {
              label: `${row.categoryNameZh || row.categoryName}（当前一级品类）`,
              options: mine.map(toOption),
            },
          ]
        : []),
      { label: '其他一级品类', options: rest.map(toOption) },
    ];
  }, [categories, row]);
  return (
    <span
      style={{ display: 'block' }}
      onClick={(e) => e.stopPropagation()}
      onKeyDown={(e) => e.stopPropagation()}
    >
      <Select
        autoFocus
        defaultOpen
        size="small"
        // 撑满「AI 建议品类」单元格，不压到右边的列（UI-111）；下拉面板另设 280 宽
        style={{ width: '100%' }}
        popupMatchSelectWidth={280}
        placeholder="搜索细分品类"
        aria-label={`改选 ${row.mpnDisplay} 的细分品类`}
        showSearch={{ optionFilterProp: 'search' }}
        options={options}
        value={value}
        onChange={onChange}
      />
    </span>
  );
};

/** 采纳 / 忽略后有跳过的：逐条列出型号和原因 */
export const SkippedList: React.FC<{ result: RefineResult }> = ({ result }) => (
  <div style={{ display: 'grid', gap: 6, maxHeight: 280, overflow: 'auto' }}>
    {result.skipped.map((s) => (
      <div key={s.id} style={{ display: 'flex', gap: 12 }}>
        <b style={{ minWidth: 160 }}>{s.mpn}</b>
        <span>{s.reason}</span>
      </div>
    ))}
  </div>
);
