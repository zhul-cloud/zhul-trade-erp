import {
  Button,
  Cascader,
  DatePicker,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Popover,
  Select,
  Tooltip,
  TreeSelect,
} from 'antd';
import dayjs from 'dayjs';
import { Check, Ellipsis, Info, SlidersHorizontal, X } from 'lucide-react';
import React, {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  useSyncExternalStore,
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
  'data-zhul-filter'?: string;
  onKeyDownCapture?: React.KeyboardEventHandler<HTMLElement>;
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

/**
 * 操作行左侧勾选后的「已选 N 条 · 取消选择」（ui-design-patterns「操作行」：勾选后结果数变为已选数，批量操作随勾选出现在右侧）。
 * extra 放在已选数与取消选择之间，写勾选内容的补充说明（如出库单的客户 · 货代）。
 */
export const SelectionCount: React.FC<{
  count: number;
  /** 量词，默认「条」 */
  unit?: string;
  extra?: React.ReactNode;
  onClear: () => void;
}> = ({ count, unit = '条', extra, onClear }) => {
  const { palette } = useAppTheme();
  return (
    <>
      <b style={{ color: palette.ink }}>
        已选 <span className="num">{count}</span> {unit}
      </b>
      {extra}
      <button type="button" className="zhul-link" onClick={onClear}>
        取消选择
      </button>
    </>
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

/** 两段文字有没有相同的两个字以上（「主要联系人」和「联系人姓名」） */
const overlaps = (a: string, b: string) => {
  for (let i = 0; i + 2 <= a.length; i += 1) {
    if (b.includes(a.slice(i, i + 2))) return true;
  }
  return false;
};

/**
 * 文本框的提示在名称作前缀后：和名称重复（「客户编码 客户编码」）或写「全部」（看着像下拉）时去掉，
 * 只留不重复的说明（如关键词的搜索范围、格式示例）。
 */
const textPlaceholder = (placeholder: unknown, label: string) =>
  typeof placeholder === 'string' &&
  (placeholder === '全部' || overlaps(placeholder, label))
    ? undefined
    : placeholder;

/**
 * 把名称作为前缀放进输入框（ui-redesign D22「订单状态 全部 ▾」）：穿过 Form.Item 找到控件；
 * 控件不支持前缀时返回 undefined，由调用方把名称放在框外。
 */
const withPrefix = (
  node: React.ReactNode,
  prefix: React.ReactNode,
  label = '',
): React.ReactNode | undefined => {
  if (!React.isValidElement(node)) return undefined;
  if (node.type === Form.Item) {
    const inner = withPrefix(
      (node.props as { children?: React.ReactNode }).children,
      prefix,
      label,
    );
    return inner === undefined
      ? undefined
      : React.cloneElement(node, {}, inner);
  }
  // 自定义选择器（如供应商搜索）把 prefix 转给内部的 Select 时，加 filterPrefix 标记即可同样放名称
  if (
    !PREFIXABLE.has(node.type) &&
    !(node.type as { filterPrefix?: boolean }).filterPrefix
  )
    return undefined;
  const text = node.type === Input || node.type === Input.Search;
  return React.cloneElement(
    node as React.ReactElement<{
      prefix?: React.ReactNode;
      suffixIcon?: React.ReactNode;
      placeholder?: unknown;
    }>,
    // 日期范围去掉日历图标：两个日期加箭头已看得出是日期，省出位置不截断日期
    node.type === DatePicker.RangePicker
      ? { prefix, suffixIcon: null }
      : text
        ? {
            prefix,
            placeholder: textPlaceholder(
              (node.props as { placeholder?: unknown }).placeholder,
              label,
            ),
          }
        : { prefix },
  );
};

/** 文本框内右侧放帮助图标（穿过 Form.Item）；不是文本框时返回 undefined */
const withSuffix = (
  node: React.ReactNode,
  help: React.ReactNode,
): React.ReactNode | undefined => {
  if (!React.isValidElement(node)) return undefined;
  if (node.type === Form.Item) {
    const inner = withSuffix(
      (node.props as { children?: React.ReactNode }).children,
      help,
    );
    return inner === undefined
      ? undefined
      : React.cloneElement(node, {}, inner);
  }
  if (
    node.type !== Input ||
    (node.props as { suffix?: React.ReactNode }).suffix !== undefined
  )
    return undefined;
  return React.cloneElement(
    node as React.ReactElement<{ suffix?: React.ReactNode }>,
    { suffix: <HelpTip title={help} /> },
  );
};

/**
 * 搜索条件区的一项。一行模式（默认）：名称放进输入框作前缀，关键词占两格；控件不支持前缀时名称在框左侧。
 * 网格模式：名称在上、输入在下。名称旁可放帮助图标。
 */
export const FilterField: React.FC<{
  label: string;
  /** auto：按内容宽度（如统计期间的快捷期间加日期范围） */
  span?: 1 | 2 | 'auto';
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
    // 日期范围放进名称后至少 300 宽，两个日期不截断
    const range = controlOf(children)?.type === DatePicker.RangePicker;
    const min =
      span === 'auto' ? undefined : range ? 300 : span === 2 ? 220 : 140;
    // 控件自带前缀（关键词的搜索图标）：不放名称（提示文字已说明），帮助图标放在框右侧
    const own =
      (controlOf(children)?.props as { prefix?: React.ReactNode } | undefined)
        ?.prefix !== undefined;
    const prefixed = own
      ? undefined
      : withPrefix(
          React.Children.only(children),
          <span style={{ marginInlineEnd: 4 }}>{name}</span>,
          label,
        );
    // 自带前缀的文本框（关键词）：帮助图标放进框内右侧，不夹在两个输入框之间
    const withHelp =
      own && help ? withSuffix(React.Children.only(children), help) : undefined;
    return (
      <div
        style={{
          // 关键词占两格（220–360），日期范围 300–360，其余 140–220；auto 按内容
          // 能换行的弹性布局里，项目按基准宽度先换行、不会先收窄：基准取最小宽度，放得下就排一行再均分余量
          flex: span === 'auto' ? '0 0 auto' : `1 1 ${min}px`,
          minWidth: min,
          maxWidth:
            span === 'auto' ? undefined : span === 2 || range ? 360 : 220,
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
          {prefixed ?? withHelp ?? children}
        </div>
        {own && help && withHelp === undefined && <HelpTip title={help} />}
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
  /**
   * 数量：写在名称后，等宽加粗，各页不再自己拼「名称 · 数量」；为 0 或没有时不显示。
   * 可以是复合值（如应收「CNY 1,200.00 · 3 张」）
   */
  count?: React.ReactNode;
  /** 数量用错误色（逾期、缺货等需要处理的）：由调用方判断，数量为 0 时传 false */
  danger?: boolean;
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
      <span
        aria-hidden
        className="zhul-quick-label"
        style={{ fontSize: 13, color: palette.mute }}
      >
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
              height: 28,
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              padding: on ? '0 12px 0 8px' : '0 12px',
              border: 0,
              borderRadius: 999,
              background: on ? palette.primarySoft : palette.neutralSoft,
              color: palette.sub,
              fontSize: 13,
              fontWeight: on ? 600 : 400,
              cursor: 'pointer',
            }}
          >
            {on && (
              <Check size={14} aria-hidden style={{ color: palette.ink }} />
            )}
            {o.label}
            {o.count != null && o.count !== 0 && o.count !== false && (
              <>
                {/* 读屏念「已逾期 3」而不是「已逾期3」 */}
                <span className="zhul-sr-only"> </span>
                <span
                  className="num"
                  style={{
                    marginLeft: 2,
                    fontWeight: 600,
                    color: o.danger ? palette.red : palette.ink,
                  }}
                >
                  {o.count}
                </span>
              </>
            )}
          </button>
        );
      })}
    </fieldset>
  );
};

/**
 * 「更多筛选」里已生效条件的胶囊：搜索条件区算出来，交给紧挨着它下面的主卡显示。
 * 按搜索条件区的实例区分（不按页面路径），同一页多个页签、同一路径开了两个标签时互不串。
 */
const chipStore = new Map<string, FilterChipItem[]>();
const chipListeners = new Set<() => void>();
const publishChips = (id: string, chips: FilterChipItem[]) => {
  const prev = chipStore.get(id) ?? [];
  const key = (list: FilterChipItem[]) =>
    list.map((c) => `${c.key}=${String(c.label)}`).join('|');
  if (key(prev) === key(chips)) return;
  if (chips.length) chipStore.set(id, chips);
  else chipStore.delete(id);
  for (const l of chipListeners) l();
};
const useAutoChips = (id?: string) =>
  useSyncExternalStore(
    (l) => {
      chipListeners.add(l);
      return () => {
        chipListeners.delete(l);
      };
    },
    () => (id ? chipStore.get(id) : undefined),
    () => undefined,
  );

/**
 * 主卡找它上面的搜索条件区：同级的前面几个元素（或其中包含的），最多往上找 3 层；
 * 到页签面板就停，别的页签里的搜索条件区不算。
 */
const findFilterId = (from: Element | null): string | undefined => {
  let cur: Element | null = from;
  for (let level = 0; cur && level < 3; level += 1) {
    for (
      let sib = cur.previousElementSibling;
      sib;
      sib = sib.previousElementSibling
    ) {
      if (sib.classList.contains('ant-tabs')) continue;
      const hit = sib.matches('[data-zhul-filter]')
        ? sib
        : sib.querySelector('[data-zhul-filter]');
      if (hit) return hit.getAttribute('data-zhul-filter') ?? undefined;
    }
    if (cur.classList.contains('ant-tabs-tabpane')) return undefined;
    cur = cur.parentElement;
  }
  return undefined;
};

interface MoreField {
  label: string;
  /** Form.Item 的字段名（受控于表单）；没有时值和 onChange 在控件自己身上 */
  name?: unknown;
  control: React.ReactElement;
}

/** 能显示成胶囊的控件；自定义控件（供应商选择、数量区间等）拿不到显示名称，不出胶囊 */
const CHIP_CONTROLS = new Set<unknown>([
  Select,
  DatePicker,
  DatePicker.RangePicker,
  Input,
  Input.Search,
  InputNumber,
  Cascader,
  TreeSelect,
]);
const TEXT_INPUTS = new Set<unknown>([Input, Input.Search]);

/** 找出「更多筛选」里的每一项：名称、表单字段名、控件 */
const collectFields = (node: React.ReactNode, out: MoreField[] = []) => {
  React.Children.forEach(node, (child) => {
    if (!React.isValidElement(child)) return;
    const props = child.props as {
      label?: string;
      children?: React.ReactNode;
      name?: unknown;
    };
    if (child.type === FilterField) {
      const inner = React.Children.toArray(props.children)[0];
      const control = controlOf(inner);
      if (!control) return;
      const name =
        React.isValidElement(inner) && inner.type === Form.Item
          ? (inner.props as { name?: unknown }).name
          : undefined;
      out.push({ label: props.label ?? '', name, control });
      return;
    }
    collectFields(props.children, out);
  });
  return out;
};

/** 条件值的显示文字：下拉取选项名称，日期按天，其余转文字；自定义控件、选项里找不到的值不显示胶囊 */
const displayOf = (control: React.ReactElement, value: unknown): string => {
  if (!CHIP_CONTROLS.has(control.type)) return '';
  if (value === undefined || value === null || value === '') return '';
  if (Array.isArray(value) && value.length === 0) return '';
  const props = control.props as {
    options?: { value?: unknown; label?: React.ReactNode }[];
  };
  const one = (v: unknown): string => {
    if (dayjs.isDayjs(v)) return v.format('YYYY-MM-DD');
    // 地址栏带来的值是字符串、选项是数字时也认
    const opt =
      props.options?.find((o) => o.value === v) ??
      props.options?.find((o) => String(o.value) === String(v));
    if (opt && typeof opt.label === 'string')
      return opt.label.replace(/ · \d+$/, '');
    if (props.options) return '';
    if (typeof v === 'string' || typeof v === 'number') return String(v);
    return '';
  };
  if (Array.isArray(value)) {
    const parts = value.map(one);
    if (parts.some((x) => !x)) return '';
    return control.type === DatePicker.RangePicker
      ? parts.join(' ~ ')
      : parts.join('、');
  }
  return one(value);
};

type FormInstance = ReturnType<typeof Form.useFormInstance>;

const fieldValue = (f: MoreField, form?: FormInstance) =>
  f.name !== undefined && form
    ? form.getFieldValue(f.name as string)
    : (f.control.props as { value?: unknown }).value;

/**
 * 算出「更多筛选」里已生效条件的胶囊（S16）。只显示最近一次查询用的条件：
 * 点「查询」、在条件里回车、打开页面时记下当时的值；在更多筛选里改了没查询，不出胶囊。
 * 表单页去掉后清空字段并立即查询；受控页调控件的 onChange（文本框传空的输入事件），等页面用新值重渲染后再查询。
 * 去掉时一律读最新一次渲染的控件和查询函数，不用旧闭包里的条件。
 */
const useMoreChips = (
  id: string,
  more: React.ReactNode,
  open: boolean,
  onSearch?: () => void,
) => {
  const form = Form.useFormInstance() as FormInstance | undefined;
  const fields = more ? collectFields(more) : [];
  // 订阅表单值：没有表单时传空，不会报警告
  Form.useWatch([], form);
  const latest = useRef({ fields, form, onSearch });
  latest.current = { fields, form, onSearch };
  const [applied, setApplied] = useState<Record<string, unknown>>({});
  const snap = useCallback(
    () =>
      setApplied(
        Object.fromEntries(
          latest.current.fields.map((f) => [
            f.label,
            fieldValue(f, latest.current.form),
          ]),
        ),
      ),
    [],
  );
  const clear = useCallback(() => setApplied({}), []);
  // 打开页面时地址栏、默认值带进来的条件算已生效
  useEffect(snap, []);
  const [pending, setPending] = useState(false);
  useEffect(() => {
    if (!pending) return;
    setPending(false);
    latest.current.onSearch?.();
  }, [pending]);
  const remove = useCallback((label: string) => {
    const { fields: fs, form: fm, onSearch: search } = latest.current;
    const f = fs.find((x) => x.label === label);
    setApplied((a) => ({ ...a, [label]: undefined }));
    if (!f) return;
    if (f.name !== undefined && fm) {
      fm.setFieldValue(f.name as string, undefined);
      search?.();
      return;
    }
    const change = (f.control.props as { onChange?: (v: unknown) => void })
      .onChange;
    change?.(
      TEXT_INPUTS.has(f.control.type) ? { target: { value: '' } } : undefined,
    );
    setPending(true);
  }, []);
  const chips: FilterChipItem[] = open
    ? []
    : fields.flatMap((f) => {
        const text = displayOf(f.control, applied[f.label]);
        if (!text) return [];
        return [
          {
            key: f.label,
            label: `${f.label}：${text}`,
            onRemove: () => remove(f.label),
          },
        ];
      });
  useEffect(() => {
    publishChips(id, chips);
  });
  useEffect(() => () => publishChips(id, []), [id]);
  return { snap, clear };
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
  const filterId = useId();
  const line = layout === 'line';
  const { snap, clear } = useMoreChips(filterId, more, open, onSearch);
  const search =
    onSearch &&
    (() => {
      snap();
      onSearch();
    });
  const reset =
    onReset &&
    (() => {
      clear();
      onReset();
    });
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
        // 视口不到 1400 时只显示图标和生效数量（1366 下四个常用条件也排一行），悬停和读屏仍是「更多筛选」
        <Tooltip title="更多筛选" placement="bottom">
          <Button
            type="text"
            aria-label={moreCount > 0 ? `更多筛选 · ${moreCount}` : '更多筛选'}
            aria-expanded={open}
            aria-controls={moreId}
            icon={<SlidersHorizontal size={16} />}
            onClick={() => setOpen((v) => !v)}
            style={{ color: palette.link, paddingInline: 6 }}
          >
            <span className="zhul-more-text">更多筛选</span>
            {moreCount > 0 && <span className="num"> · {moreCount}</span>}
          </Button>
        </Tooltip>
      )}
      {reset && <Button onClick={reset}>重置</Button>}
      {search && (
        <Button type="primary" onClick={search}>
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
        data-zhul-filter={filterId}
        // 在文本框里回车查询时，记下这次查询用的「更多筛选」条件；下拉、日期里的回车只是选中选项，不算查询
        onKeyDownCapture={(e) => {
          if (
            e.key === 'Enter' &&
            e.target instanceof HTMLInputElement &&
            !e.target.closest('.ant-select, .ant-picker, .ant-cascader')
          )
            setTimeout(snap);
        }}
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

/**
 * 选了就生效的看板（没有「查询」按钮）用的「筛选」按钮：次要条件（品牌、品类、热度等）收进弹层，
 * 工具栏保持一行（2026-11-25，供应链跟进各板块 1366 宽换两行）。按钮样式同搜索条件区的「更多筛选」，
 * 显示生效数量；已生效的条件另用 FilterChip 显示在工具栏上，能直接去掉。
 */
export const FilterPopover: React.FC<{
  /** 已生效的条件数 */
  count: number;
  /** label 为空时不显示名称行（如一组勾选项） */
  items: { label: string; node: React.ReactNode }[];
}> = ({ count, items }) => {
  const { palette } = useAppTheme();
  const [open, setOpen] = useState(false);
  const panel = useRef<HTMLDivElement>(null);
  // 打开后焦点进到第一个条件（弹层渲染在页面末尾，Tab 走不到）；等弹层渲染完再移
  useEffect(() => {
    if (!open) return;
    let tries = 0;
    let t = 0;
    const go = () => {
      const el = panel.current?.querySelector<HTMLElement>('input');
      if (el) el.focus();
      else if (tries++ < 10) t = window.setTimeout(go, 30);
    };
    t = window.setTimeout(go, 0);
    return () => window.clearTimeout(t);
  }, [open]);
  return (
    <Popover
      trigger="click"
      placement="bottomRight"
      arrow={false}
      open={open}
      onOpenChange={setOpen}
      content={
        <div
          ref={panel}
          role="dialog"
          aria-label="筛选"
          style={{ width: 240, display: 'grid', gap: 12 }}
        >
          {items.map((it) => (
            <div key={it.label} style={{ display: 'grid', gap: 4 }}>
              {it.label && (
                <span style={{ fontSize: 12, color: palette.mute }}>
                  {it.label}
                </span>
              )}
              {it.node}
            </div>
          ))}
        </div>
      }
    >
      <Button
        type="text"
        aria-label={count > 0 ? `筛选 · ${count}` : '筛选'}
        aria-haspopup="dialog"
        aria-expanded={open}
        icon={<SlidersHorizontal size={16} />}
        style={{ color: palette.link, paddingInline: 6 }}
      >
        筛选
        {count > 0 && <span className="num"> · {count}</span>}
      </Button>
    </Popover>
  );
};

export interface FilterChipItem {
  key: string;
  /** 「品牌：西门子」 */
  label: React.ReactNode;
  onRemove: () => void;
}

/** 已生效的条件胶囊（「更多筛选」收起时显示在操作行，S16）：主题色浅底，点 × 去掉这个条件 */
export const FilterChip: React.FC<{ item: FilterChipItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  return (
    <span
      style={{
        height: 28,
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        padding: '0 4px 0 10px',
        borderRadius: 14,
        background: palette.primarySoft, // lint-ui-ignore primary-soft: 已生效的筛选条件胶囊，属于选中状态
        color: palette.sub,
        fontSize: 13,
        whiteSpace: 'nowrap',
      }}
    >
      {item.label}
      <button
        type="button"
        aria-label={`去掉条件 ${typeof item.label === 'string' ? item.label : ''}`}
        onClick={item.onRemove}
        style={{
          width: 20,
          height: 20,
          display: 'grid',
          placeItems: 'center',
          padding: 0,
          border: 0,
          borderRadius: '50%',
          background: 'transparent',
          color: palette.mute,
          cursor: 'pointer',
        }}
      >
        <X size={14} aria-hidden />
      </button>
    </span>
  );
};

/**
 * 主内容区：操作行（左：结果数或已选，后面是快捷筛选 quick 与已生效条件 chips；右：批量操作）+ 表格。
 * 快捷筛选放在这里，不放搜索条件区（ui-redesign D22）。
 * 结果数传 count（和 unit，默认「条」）：count 没有值、或加载中且为 0 时不显示，不在转圈时闪「共 0 条」（S17）。
 */
export const MainCard: React.FC<{
  count?: number;
  unit?: string;
  loading?: boolean;
  left?: React.ReactNode;
  right?: React.ReactNode;
  quick?: React.ReactNode;
  chips?: FilterChipItem[];
  /** 列设置按钮（useColumnSettings 返回的 settings），放在工具栏最右 */
  settings?: React.ReactNode;
  /** 表格等内容；分组卡片放在主卡外面时（如采购需求按订单），主卡只剩操作行，可不传 */
  children?: React.ReactNode;
}> = ({
  count,
  unit = '条',
  loading,
  left: leftNode,
  right,
  quick,
  chips: own,
  settings,
  children,
}) => {
  const counted =
    count === undefined || (loading && !count)
      ? undefined
      : `共 ${count} ${unit}`;
  const left =
    counted && leftNode ? (
      <>
        {counted}
        {leftNode}
      </>
    ) : (
      (counted ?? leftNode)
    );
  const { palette } = useAppTheme();
  const probe = useRef<HTMLSpanElement>(null);
  const [filterId, setFilterId] = useState<string>();
  useLayoutEffect(() => {
    const id = findFilterId(probe.current?.parentElement ?? null);
    if (id !== filterId) setFilterId(id);
  });
  const auto = useAutoChips(filterId);
  const chips = [...(own ?? []), ...(auto ?? [])];
  const hasChips = chips.length > 0;
  return (
    <Panel style={{ padding: 16, minWidth: 0 }}>
      <span ref={probe} hidden />
      {(left || right || quick || hasChips || settings) && (
        <div
          className="zhul-toolbar"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
            minHeight: 32,
            marginBottom: children ? 12 : 0,
            color: palette.sub,
            fontSize: 13,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              flexWrap: 'wrap',
              flex: 1,
              minWidth: 0,
            }}
          >
            {left}
            {quick}
            {chips.map((c) => (
              <FilterChip key={c.key} item={c} />
            ))}
          </div>
          {right}
          {settings}
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
