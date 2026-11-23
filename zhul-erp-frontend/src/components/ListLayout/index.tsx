import {
  Button,
  Cascader,
  DatePicker,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Select,
  Tooltip,
  TreeSelect,
} from 'antd';
import { Check, Ellipsis, Info, SlidersHorizontal } from 'lucide-react';
import React, {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
} from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { CARD_SHADOW } from '@/theme/palette';
import { formatAmount } from '@/utils/format';
import { useMenu } from '@/utils/menuName';

/**
 * 页面骨架（openspec ui-redesign 设计第 3 节，UI 规范「列表页统一规范」）：
 * 页头 PageHeader → 汇总行 SummaryBar → 搜索条件区 FilterCard（含更多筛选、快捷筛选）→ 主内容区 MainCard；
 * 详情页的分区用 Section，口径说明用 HelpTip。用法见 ui-design-patterns.md「骨架组件」。
 */

/** 卡片：圆角 10；浅色柔和阴影不加边框，深色加边框不用阴影 */
export const Panel: React.FC<{
  children: React.ReactNode;
  style?: React.CSSProperties;
  as?: 'section' | 'div';
  'aria-label'?: string;
}> = ({ children, style, as = 'section', ...rest }) => {
  const { palette, mode } = useAppTheme();
  const Tag = as;
  return (
    <Tag
      {...rest}
      style={{
        background: palette.card,
        borderRadius: 10,
        boxShadow: mode === 'light' ? CARD_SHADOW : 'none',
        border: mode === 'light' ? 'none' : `1px solid ${palette.hairline}`,
        ...style,
      }}
    >
      {children}
    </Tag>
  );
};

/** 帮助图标：口径、匹配规则等背景知识；只在鼠标悬停时显示（按用户决定豁免可见焦点要求） */
export const HelpTip: React.FC<{
  title: React.ReactNode;
  /** 读屏用的文字：title 不是纯文字时写上，否则读屏只能读到「说明」 */
  label?: string;
}> = ({ title, label }) => {
  const { palette } = useAppTheme();
  return (
    <Tooltip title={title}>
      <span
        role="img"
        aria-label={label ?? (typeof title === 'string' ? title : '说明')}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          color: palette.mute,
          cursor: 'help',
        }}
      >
        <Info size={14} />
      </span>
    </Tooltip>
  );
};

/** 表头文字 + 帮助图标；点帮助图标不触发表头排序（排序列另设 showSorterTooltip: { target: 'sorter-icon' }） */
export const HeadHelp: React.FC<{ title: string; help: React.ReactNode }> = ({
  title,
  help,
}) => (
  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
    {title}
    {/* 只拦截冒泡：点帮助图标不排序；图标本身靠悬停 */}
    <span onClick={(e) => e.stopPropagation()}>
      <HelpTip title={help} />
    </span>
  </span>
);

/**
 * 页头：左侧页面标题，右侧主操作（每个页头最多一个主按钮）。
 * 列表页不用传 title：取当前页面在「菜单管理」里的名称。
 * 详情页 title 为单号，tags 为状态标签（跟在单号后面，不算标题文字），sub 为标题下一行的副信息（客户 · 来源单号 · 负责人 · 创建时间）。
 */
export const PageHeader: React.FC<{
  title?: React.ReactNode;
  /** 页面口径或规则说明，放在标题旁的帮助图标里（页头不写说明文字） */
  help?: React.ReactNode;
  tags?: React.ReactNode;
  sub?: React.ReactNode;
  extra?: React.ReactNode;
}> = ({ title, help, tags, sub, extra }) => {
  const { palette } = useAppTheme();
  const menu = useMenu();
  return (
    <div
      style={{
        display: 'flex',
        alignItems: sub ? 'flex-start' : 'center',
        justifyContent: 'space-between',
        gap: 16,
        marginBottom: sub ? 16 : 12,
        minHeight: 36,
        flexWrap: 'wrap',
      }}
    >
      <div style={{ minWidth: 0 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            flexWrap: 'wrap',
            minHeight: 36,
          }}
        >
          <h1
            style={{
              margin: 0,
              fontSize: 20,
              fontWeight: 600,
              color: palette.ink,
              minWidth: 0,
            }}
          >
            {title ?? menu?.name}
          </h1>
          {help && <HelpTip title={help} />}
          {tags}
        </div>
        {sub && (
          <div style={{ marginTop: 2, fontSize: 13, color: palette.sub }}>
            {sub}
          </div>
        )}
      </div>
      {extra && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            minHeight: 36,
            flexWrap: 'wrap',
          }}
        >
          {extra}
        </div>
      )}
    </div>
  );
};

export interface MoreAction {
  key: string;
  label: string;
  icon?: React.ReactNode;
  danger?: boolean;
  /** 有值时菜单项置灰，原因写在名称下一行（置灰项收不到悬停和焦点，不能用 Tooltip） */
  disabledReason?: React.ReactNode;
}

/** 「更多」菜单项：危险操作排在后面，前面还有普通操作时用分隔线隔开（ui-design-patterns「详情页头」）；详情页头与表格操作列共用 */
export const moreMenuItems = (list: MoreAction[]) => {
  const firstDanger = list.findIndex((a) => a.danger);
  return list.flatMap((a, i) => [
    ...(i > 0 && i === firstDanger ? [{ type: 'divider' as const }] : []),
    {
      key: a.key,
      icon: a.icon,
      danger: a.danger,
      disabled: !!a.disabledReason,
      label: a.disabledReason ? (
        <span>
          {a.label}
          <span style={{ display: 'block', fontSize: 12 }}>
            {a.disabledReason}
          </span>
        </span>
      ) : (
        a.label
      ),
    },
  ]);
};

/**
 * 详情页头的「更多」菜单：撤回、冲销、作废等危险或少用的操作收在这里（每个页头最多一个主按钮）。
 * 没有可显示的操作时不渲染。
 */
export const MoreActions: React.FC<{
  /** 可直接写 `条件 && { ... }`，假值会被略过 */
  items: (MoreAction | false | null | undefined | '' | 0)[];
  onClick: (key: string) => void;
}> = ({ items, onClick }) => {
  const list = items.filter(Boolean) as MoreAction[];
  if (list.length === 0) return null;
  const menuItems = moreMenuItems(list);
  return (
    <Dropdown
      trigger={['click']}
      placement="bottomRight"
      menu={{
        items: menuItems,
        onClick: ({ key }) => onClick(key),
      }}
    >
      <Button
        type="text"
        shape="circle"
        icon={<Ellipsis size={18} />}
        aria-label="更多操作"
      />
    </Dropdown>
  );
};

export interface SummaryItem {
  key?: string;
  label: string;
  /** 口径从名称看不出时写在这里，名称旁显示帮助图标 */
  help?: React.ReactNode;
  value: React.ReactNode;
  /** 金额的币种：有币种时数值按 formatAmount 显示（如「CNY 386,420.00」） */
  currency?: string;
  /** 数字后面的一行副文字 */
  sub?: React.ReactNode;
  /** 宽度不够时可以整段隐藏副文字；只给页面别处也能看到的副文字设 true */
  subHideable?: boolean;
}

/** 用于判断内容是否变了（变了要重新测量副文字放不放得下） */
const summaryKey = (items: SummaryItem[]) =>
  items
    .map((it) =>
      [it.label, it.value, it.sub]
        .map((v) => (typeof v === 'string' || typeof v === 'number' ? v : ''))
        .join('|'),
    )
    .join(';');

const SUMMARY_GAP = 32;

/**
 * 汇总行：页头下方一行指标，每项「名称 + 数字」，可带一行副文字。
 * 宽度不够时，subHideable 的副文字整段隐藏（不截断）；隐藏后仍放不下就换行，不裁掉后面的指标。
 */
export const SummaryBar: React.FC<{ items: SummaryItem[] }> = ({ items }) => {
  const { palette } = useAppTheme();
  const box = useRef<HTMLDivElement>(null);
  const [hideSub, setHideSub] = useState(false);
  const hideRef = useRef(hideSub);
  hideRef.current = hideSub;
  // 副文字都显示时，各项排成一行所需的宽度；隐藏后用它判断宽度够了没有
  const full = useRef(0);
  const key = summaryKey(items);

  const measure = useCallback(() => {
    const el = box.current;
    if (!el) return;
    if (!hideRef.current) {
      const kids = Array.from(el.children) as HTMLElement[];
      full.current =
        kids.reduce((w, k) => w + k.scrollWidth, 0) +
        SUMMARY_GAP * Math.max(0, kids.length - 1);
    }
    const tooWide = full.current > el.clientWidth + 1;
    if (tooWide !== hideRef.current) setHideSub(tooWide);
  }, []);

  // 内容变了：先恢复显示副文字，再按新内容测量
  useLayoutEffect(() => {
    setHideSub(false);
  }, [key]);
  useLayoutEffect(() => {
    measure();
  }, [key, hideSub, measure]);
  useEffect(() => {
    const el = box.current;
    if (!el || typeof ResizeObserver === 'undefined') return undefined;
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [measure]);

  return (
    <div
      ref={box}
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'flex-end',
        columnGap: SUMMARY_GAP,
        rowGap: 8,
        marginBottom: 16,
        whiteSpace: 'nowrap',
      }}
    >
      {items.map((it) => (
        <div
          key={it.key ?? it.label}
          style={{ display: 'flex', flexDirection: 'column', gap: 2 }}
        >
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              fontSize: 12,
              color: palette.mute,
            }}
          >
            {it.label}
            {it.help && <HelpTip title={it.help} />}
          </span>
          <span
            style={{ display: 'inline-flex', alignItems: 'baseline', gap: 8 }}
          >
            <span
              className="num"
              style={{ fontSize: 18, fontWeight: 700, color: palette.ink }}
            >
              {it.currency &&
              (typeof it.value === 'number' || typeof it.value === 'string')
                ? formatAmount(it.value, it.currency)
                : it.value}
            </span>
            {it.sub && !(hideSub && it.subHideable) && (
              <span
                className="num"
                style={{ fontSize: 12, color: palette.mute }}
              >
                {it.sub}
              </span>
            )}
          </span>
        </div>
      ))}
    </div>
  );
};

/** 搜索条件区排成一行（line，默认）还是网格（grid，标签在上） */
const FilterLayoutContext = React.createContext<'line' | 'grid'>('grid');

/** 支持 prefix 的 antd 输入控件：名称放进框内作前缀 */
const PREFIXABLE = new Set<unknown>([
  Select,
  Input,
  InputNumber,
  DatePicker,
  DatePicker.RangePicker,
  Cascader,
  TreeSelect,
]);

/** 穿过 Form.Item 找到真正的输入控件 */
const controlOf = (node: React.ReactNode): React.ReactElement | undefined => {
  if (!React.isValidElement(node)) return undefined;
  if (node.type === Form.Item) {
    return controlOf((node.props as { children?: React.ReactNode }).children);
  }
  return node;
};

/**
 * 把名称作为前缀放进输入框（ui-redesign D22「订单状态 全部 ▾」）：穿过 Form.Item 找到控件；
 * 控件不支持前缀时返回 undefined，由调用方把名称放在框外。
 */
const withPrefix = (
  node: React.ReactNode,
  prefix: React.ReactNode,
): React.ReactNode | undefined => {
  if (!React.isValidElement(node)) return undefined;
  if (node.type === Form.Item) {
    const inner = withPrefix(
      (node.props as { children?: React.ReactNode }).children,
      prefix,
    );
    return inner === undefined
      ? undefined
      : React.cloneElement(node, {}, inner);
  }
  if (!PREFIXABLE.has(node.type)) return undefined;
  return React.cloneElement(
    node as React.ReactElement<{ prefix?: React.ReactNode }>,
    {
      prefix,
    },
  );
};

/**
 * 搜索条件区的一项。一行模式（默认）：名称放进输入框作前缀，关键词占两格；控件不支持前缀时名称在框左侧。
 * 网格模式：名称在上、输入在下。名称旁可放帮助图标。
 */
export const FilterField: React.FC<{
  label: string;
  span?: 1 | 2;
  help?: React.ReactNode;
  /** 输入框的 id：标签与它关联（没有 aria-label 的输入，如日期范围，靠它读出名称） */
  htmlFor?: string;
  children: React.ReactNode;
}> = ({ label, span = 1, help, htmlFor, children }) => {
  const { palette } = useAppTheme();
  const layout = React.useContext(FilterLayoutContext);
  const name = (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        fontSize: layout === 'line' ? 14 : 13,
        fontWeight: 400,
        color: layout === 'line' ? palette.mute : palette.sub,
        whiteSpace: 'nowrap',
      }}
    >
      {htmlFor ? <label htmlFor={htmlFor}>{label}</label> : label}
      {help && <HelpTip title={help} />}
    </span>
  );
  if (layout === 'line') {
    // 控件自带前缀（关键词的搜索图标）：不放名称（提示文字已说明），帮助图标放在框右侧
    const own =
      (controlOf(children)?.props as { prefix?: React.ReactNode } | undefined)
        ?.prefix !== undefined;
    const prefixed = own
      ? undefined
      : withPrefix(
          React.Children.only(children),
          <span style={{ marginInlineEnd: 4 }}>{name}</span>,
        );
    return (
      <div
        style={{
          // 先收窄再换行：关键词、日期范围占两格（260–360，放得下两个日期），其余 150–240
          flex: span === 2 ? '1 1 280px' : '1 1 170px',
          minWidth: span === 2 ? 260 : 140,
          maxWidth: span === 2 ? 360 : 220,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        {prefixed === undefined && !own && name}
        <div
          style={{
            flex: 1,
            minWidth: 0,
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          {prefixed ?? children}
        </div>
        {own && help && <HelpTip title={help} />}
      </div>
    );
  }
  return (
    <div
      style={{
        gridColumn: span === 2 ? 'span 2' : undefined,
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        minWidth: 0,
      }}
    >
      {name}
      {children}
    </div>
  );
};

export interface QuickFilterOption {
  value: string;
  label: React.ReactNode;
}

/**
 * 快捷筛选（参考站 Chips）：高 30、全圆角；未选中为中性浅底，选中为主题色浅底、主题色字、前面加勾选。
 * 可多选，点一下选中、再点取消。
 */
export const QuickFilters: React.FC<{
  options: QuickFilterOption[];
  value: string[];
  onChange: (value: string[]) => void;
  label?: string;
}> = ({ options, value, onChange, label = '快捷筛选' }) => {
  const { palette } = useAppTheme();
  return (
    <fieldset
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        flexWrap: 'wrap',
        border: 0,
        margin: 0,
        padding: 0,
        minWidth: 0,
      }}
    >
      <legend className="zhul-sr-only">{label}</legend>
      <span aria-hidden style={{ fontSize: 13, color: palette.mute }}>
        {label}
      </span>
      {options.map((o) => {
        const on = value.includes(o.value);
        return (
          <button
            key={o.value}
            type="button"
            aria-pressed={on}
            onClick={() =>
              onChange(
                on ? value.filter((v) => v !== o.value) : [...value, o.value],
              )
            }
            style={{
              height: 30,
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              padding: on ? '0 12px 0 8px' : '0 12px',
              border: 0,
              borderRadius: 999,
              background: on ? palette.primarySoft : palette.neutralSoft,
              color: on ? palette.link : palette.sub,
              fontSize: 13,
              fontWeight: on ? 600 : 400,
              cursor: 'pointer',
            }}
          >
            {on && <Check size={16} aria-hidden />}
            {o.label}
          </button>
        );
      })}
    </fieldset>
  );
};

/**
 * 搜索条件区（ui-redesign D22）：默认排成一行——常用条件的名称在输入框内作前缀，右侧「更多筛选」「重置」「查询」；
 * 放不下时自动换行。more 为收起来的其余条件，按钮显示生效数量 moreCount；
 * quick 为快捷筛选：应放到主卡操作行（MainCard 的 quick），这里只为还没迁的页面保留。
 * 统计页的条件选了就生效，不传 onSearch、onReset，不显示「重置」「查询」。layout="grid" 为名称在上的网格（条件多、需要逐项说明的统计页）。
 */
export const FilterCard: React.FC<{
  children: React.ReactNode;
  onSearch?: () => void;
  onReset?: () => void;
  more?: React.ReactNode;
  moreCount?: number;
  quick?: React.ReactNode;
  layout?: 'line' | 'grid';
  /** 网格模式的最小列宽，默认 170 */
  minColumn?: number;
}> = ({
  children,
  onSearch,
  onReset,
  more,
  moreCount = 0,
  quick,
  layout = 'line',
  minColumn = 170,
}) => {
  const { palette } = useAppTheme();
  const [open, setOpen] = useState(false);
  const moreId = useId();
  const line = layout === 'line';
  const buttons = (
    <div
      style={{
        flex: 'none',
        display: 'flex',
        gap: 8,
        alignItems: 'center',
        minHeight: 40,
      }}
    >
      {more && (
        <Button
          type="text"
          aria-expanded={open}
          aria-controls={moreId}
          icon={<SlidersHorizontal size={16} />}
          onClick={() => setOpen((v) => !v)}
          style={{ color: palette.link, paddingInline: 6 }}
        >
          更多筛选
          {moreCount > 0 && <span className="num"> · {moreCount}</span>}
        </Button>
      )}
      {onReset && (
        <Button danger onClick={onReset}>
          重置
        </Button>
      )}
      {onSearch && (
        <Button type="primary" onClick={onSearch}>
          查询
        </Button>
      )}
    </div>
  );
  // 收起时只是隐藏，不卸载：条件里没受控的输入值不会丢
  const moreFields = more && (
    <div id={moreId} style={{ display: open ? 'contents' : 'none' }}>
      {more}
    </div>
  );
  return (
    <FilterLayoutContext.Provider value={layout}>
      <Panel
        style={{ marginBottom: 16, padding: line ? 16 : 20 }}
        aria-label="搜索条件"
      >
        {line ? (
          // 按钮固定在第一行右侧；窄屏时只有条件换行
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
            <div
              style={{
                flex: 1,
                minWidth: 0,
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                flexWrap: 'wrap',
              }}
            >
              {children}
              {moreFields}
            </div>
            {buttons}
          </div>
        ) : (
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12 }}>
            <div
              style={{
                flex: 1,
                minWidth: 0,
                display: 'grid',
                gridTemplateColumns: `repeat(auto-fill, minmax(${minColumn}px, 1fr))`,
                gap: 12,
                alignItems: 'end',
              }}
            >
              {children}
              {moreFields}
            </div>
            {buttons}
          </div>
        )}
        {quick && (
          <div
            style={{
              marginTop: 16,
              paddingTop: 16,
              borderTop: `1px solid ${palette.hairline}`,
            }}
          >
            {quick}
          </div>
        )}
      </Panel>
    </FilterLayoutContext.Provider>
  );
};

/** 主内容区：操作行（左：结果数或已选；右：批量操作）+ 表格 */
export const MainCard: React.FC<{
  left?: React.ReactNode;
  right?: React.ReactNode;
  children: React.ReactNode;
}> = ({ left, right, children }) => {
  const { palette } = useAppTheme();
  return (
    <Panel style={{ padding: 16, minWidth: 0 }}>
      {(left || right) && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            flexWrap: 'wrap',
            marginBottom: 12,
            color: palette.sub,
            fontSize: 13,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              flex: 1,
              minWidth: 0,
            }}
          >
            {left}
          </div>
          {right}
        </div>
      )}
      {children}
    </Panel>
  );
};

/** 卡片标题（18/500，同 Section 标题）：给自己画卡片头部的组件用 */
export const CardTitle: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const { palette } = useAppTheme();
  return (
    <h2
      style={{ margin: 0, fontSize: 16, fontWeight: 600, color: palette.ink }}
    >
      {children}
    </h2>
  );
};

/** 详情页分区：卡片标题 18/500，可带帮助图标、标题旁计数（meta）和右侧操作；id 加在标题上，用于页内定位 */
export const Section: React.FC<{
  title: React.ReactNode;
  help?: React.ReactNode;
  /** 标题旁的一小段说明，如「4 个型号 · 23 件」 */
  meta?: React.ReactNode;
  extra?: React.ReactNode;
  children: React.ReactNode;
  id?: string;
  style?: React.CSSProperties;
}> = ({ title, help, meta, extra, children, id, style }) => {
  const { palette } = useAppTheme();
  return (
    <Panel style={{ padding: '20px 24px', minWidth: 0, ...style }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 16,
        }}
      >
        <h2
          id={id}
          style={{
            margin: 0,
            fontSize: 16,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          {title}
        </h2>
        {help && <HelpTip title={help} />}
        {meta && (
          <span style={{ fontSize: 13, color: palette.mute }}>{meta}</span>
        )}
        {extra && (
          <div
            style={{
              marginInlineStart: 'auto',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
          >
            {extra}
          </div>
        )}
      </div>
      {children}
    </Panel>
  );
};
