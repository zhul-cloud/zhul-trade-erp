import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Button, Checkbox, Popover } from 'antd';
import { Columns3, GripVertical } from 'lucide-react';
import React, {
  isValidElement,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/**
 * 列表列设置（ui-redesign D55，对比图 R15 方案 B）：
 * 列表默认只显示做判断和操作需要的列，其余列（含「创建」「更新」）收进工具栏右侧的「列」按钮，
 * 用户勾选显示、拖动排序、可恢复默认；设置按登录用户、按页面记在本机。
 * 固定在左右两侧的列（单号、操作）始终显示，不参与设置。
 */

/** 默认收起的列：「创建」「更新」两列（列表页统一规范第 2 条：默认收进列设置，必须能打开） */
export const AUDIT_KEYS = ['create', 'update'];

type Col = {
  key?: React.Key;
  dataIndex?: unknown;
  title?: unknown;
  fixed?: unknown;
  hidden?: boolean;
};

export type ColumnState = {
  order: string[];
  shown: string[];
  hidden: string[];
};

const EMPTY: ColumnState = { order: [], shown: [], hidden: [] };

/** 列的标识：key，没有 key 时用 dataIndex */
export const colId = (c: Col): string | undefined => {
  if (c.key !== undefined && c.key !== null) return String(c.key);
  if (Array.isArray(c.dataIndex)) return c.dataIndex.join('.');
  if (typeof c.dataIndex === 'string' || typeof c.dataIndex === 'number')
    return String(c.dataIndex);
  return undefined;
};

/** 列名：字符串标题，或 HeadHelp 这类带 title 属性的标题 */
export const colLabel = (c: Col): string => {
  if (typeof c.title === 'string') return c.title;
  if (isValidElement(c.title)) {
    const t = (c.title.props as { title?: unknown }).title;
    if (typeof t === 'string') return t;
  }
  return colId(c) ?? '';
};

/**
 * 可设置的列：有标识、没有固定在两侧，且不是第一列。
 * 第一列是身份列（单号、客户名称、用户等），没固定在左时也始终显示、不能拖走
 */
const movable = (c: Col, i: number) =>
  i > 0 && !c.fixed && colId(c) !== undefined;

/**
 * 按设置排好列：可设置的列按保存的顺序排在原来可设置列占的位置上（固定列位置不变），
 * 隐藏的列加 hidden（antd 表格不渲染，tableScrollX 也不计宽度）。
 * 新加的列（保存时还没有）按原顺序插入，显示与否按默认。
 */
export const arrange = <C extends Col>(
  columns: readonly C[],
  state: ColumnState,
  defaultHidden: readonly string[],
): C[] => {
  const ids = columns.filter(movable).map((c) => colId(c) as string);
  // 存储被改坏时顺序里可能有重复，去重后再用
  const saved = Array.from(new Set(state.order));
  const order = saved.filter((id) => ids.includes(id));
  ids.forEach((id, i) => {
    if (!order.includes(id)) order.splice(Math.min(i, order.length), 0, id);
  });
  const byId = new Map(columns.filter(movable).map((c) => [colId(c), c]));
  let next = 0;
  return columns.map((c, i) => {
    if (!movable(c, i)) return c;
    const id = order[next++];
    const col = byId.get(id) as C;
    const hide = state.hidden.includes(id)
      ? true
      : state.shown.includes(id)
        ? false
        : defaultHidden.includes(id);
    // hideInTable 给 ProTable 用（它不认 hidden）
    return hide ? { ...col, hidden: true, hideInTable: true } : col;
  });
};

/** 列宽：没写宽度的按 120 计（同 tableScrollX） */
const widthOf = (c: Col & { width?: unknown }) =>
  typeof c.width === 'number' ? c.width : 120;

/**
 * 宽屏自动补列（2026-11-24 用户：允许滚动、信息齐全、不浪费空间）：
 * 表格区放得下时，把默认收起、用户没有手动关掉的列按列顺序补上（「创建」「更新」最后补），
 * 直到放不下为止。只补不减：窄屏不自动收起列，放不下就横向滚动。
 * room 为表格区宽度减去当前显示列的宽度；返回补上的列标识。
 */
export const autoFill = <C extends Col>(
  columns: readonly C[],
  state: ColumnState,
  defaultHidden: readonly string[],
  room: number,
): { columns: C[]; auto: Set<string> } => {
  const auto = new Set<string>();
  if (room <= 0) return { columns: [...columns], auto };
  const candidates = columns
    .filter((c, i) => {
      const cid = colId(c);
      return (
        c.hidden &&
        movable(c, i) &&
        cid !== undefined &&
        defaultHidden.includes(cid) &&
        !state.hidden.includes(cid)
      );
    })
    .sort(
      (a, b) =>
        Number(AUDIT_KEYS.includes(colId(a) as string)) -
        Number(AUDIT_KEYS.includes(colId(b) as string)),
    );
  let left = room;
  for (const c of candidates) {
    const w = widthOf(c as C & { width?: unknown });
    if (w > left) break;
    left -= w;
    auto.add(colId(c) as string);
  }
  return {
    columns: columns.map((c) =>
      auto.has(colId(c) as string)
        ? { ...c, hidden: false, hideInTable: false }
        : c,
    ),
    auto,
  };
};

/** 按租户加登录账号区分（不同租户可能有同名账号） */
const userKey = () => {
  try {
    const u = JSON.parse(localStorage.getItem('zhul_user') ?? '{}');
    return `${u?.tenantName ?? ''}/${u?.userid ?? 'anon'}`;
  } catch {
    return 'anon';
  }
};

/** 列设置在浏览器存储里的键（测试里预置设置也用它） */
export const storageKey = (id: string) => `zhul_cols:v1:${userKey()}:${id}`;

const load = (id: string): ColumnState => {
  try {
    const raw = localStorage.getItem(storageKey(id));
    if (!raw) return EMPTY;
    const v = JSON.parse(raw);
    const arr = (x: unknown) =>
      Array.isArray(x) ? x.filter((s) => typeof s === 'string') : [];
    return { order: arr(v.order), shown: arr(v.shown), hidden: arr(v.hidden) };
  } catch {
    return EMPTY;
  }
};

const save = (id: string, s: ColumnState) => {
  try {
    if (s === EMPTY) localStorage.removeItem(storageKey(id));
    else localStorage.setItem(storageKey(id), JSON.stringify(s));
  } catch {
    // 浏览器存储不可用（隐私模式等）：设置只在本次打开有效
  }
};

const Row: React.FC<{
  id: string;
  label: string;
  checked: boolean;
  disabled: boolean;
  onToggle: (v: boolean) => void;
}> = ({ id, label, checked, disabled, onToggle }) => {
  const { palette } = useAppTheme();
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id });
  return (
    <div
      ref={setNodeRef}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        height: 32,
        padding: '0 6px 0 2px',
        borderRadius: 6,
        background: isDragging ? palette.hover : undefined,
        position: 'relative',
        zIndex: isDragging ? 1 : undefined,
      }}
    >
      <button
        type="button"
        aria-label={`拖动调整「${label}」的位置`}
        {...attributes}
        {...listeners}
        style={{
          display: 'inline-flex',
          border: 0,
          background: 'none',
          padding: 4,
          cursor: 'grab',
          color: palette.faint,
          touchAction: 'none',
        }}
      >
        <GripVertical size={14} />
      </button>
      <Checkbox
        checked={checked}
        disabled={disabled}
        onChange={(e) => onToggle(e.target.checked)}
        style={{ flex: 1, minWidth: 0, color: palette.ink }}
      >
        {label}
      </Checkbox>
    </div>
  );
};

/**
 * 列设置 hook：传页面标识（如 'sales-orders'）和完整列定义，返回按设置处理后的列和「列」按钮。
 * hidden：页面默认收起的列，写列标识（key / dataIndex）或列名都行（「创建」「更新」总是默认收起，不用写）。
 * 默认显示的列加起来不超过 1043px（1366 宽屏幕表格区的宽度），一屏放下不横向滚动。
 * 按钮放进 MainCard 的 settings。
 */
export function useColumnSettings<C extends Col>(
  id: string,
  columns: readonly C[],
  opts: { hidden?: readonly string[] } = {},
): { columns: C[]; settings: React.ReactNode } {
  const { palette } = useAppTheme();
  const [state, setState] = useState<ColumnState>(() => load(id));
  // 页面写的是列标识或列名，这里统一换成列标识（每次渲染列定义都是新数组，按内容比较避免重算）
  const want = new Set([...AUDIT_KEYS, ...(opts.hidden ?? [])]);
  const found = columns
    .filter((c) => want.has(colId(c) ?? '') || want.has(colLabel(c)))
    .map((c) => colId(c) as string);
  const foundKey = JSON.stringify(found);
  const defaults = useMemo(() => JSON.parse(foundKey) as string[], [foundKey]);
  const trigger = useRef<HTMLButtonElement>(null);
  // 表格区宽度：从「列」按钮往上找到同时装着表格的容器，减去它的左右内边距、勾选列和展开列
  const [avail, setAvail] = useState(0);
  useEffect(() => {
    const start = trigger.current;
    if (!start || typeof ResizeObserver === 'undefined') return;
    let box: HTMLElement | null = start.parentElement;
    while (box && !box.querySelector('.ant-table')) box = box.parentElement;
    if (!box) return;
    const el = box;
    // 量表格本身的宽度：「列」按钮可能放在卡片外（页头、筛选行），找到的容器比表格宽，会多补列
    const table = el.querySelector<HTMLElement>('.ant-table-wrapper');
    const measure = () => {
      const cs = getComputedStyle(el);
      const pad =
        parseFloat(cs.paddingLeft || '0') + parseFloat(cs.paddingRight || '0');
      const sel = el.querySelector<HTMLElement>(
        'th.ant-table-selection-column',
      );
      const expand = el.querySelector<HTMLElement>(
        'th.ant-table-row-expand-icon-cell',
      );
      const w = Math.floor(
        (table?.clientWidth || el.clientWidth - pad) -
          (sel?.offsetWidth ?? 0) -
          (expand?.offsetWidth ?? 0),
      );
      setAvail((old) => (Math.abs(old - w) >= 1 ? w : old));
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    if (table) ro.observe(table);
    return () => ro.disconnect();
  });
  const arranged = useMemo(() => {
    const base = arrange(columns, state, defaults);
    const used = base
      .filter((c) => !c.hidden)
      .reduce((sum, c) => sum + widthOf(c as C & { width?: unknown }), 0);
    return autoFill(base, state, defaults, avail ? avail - used : 0);
  }, [columns, state, defaults, avail]);
  const update = (s: ColumnState) => {
    setState(s);
    save(id, s);
  };
  const items = arranged.columns.filter(movable);
  const nameOf = (cid: unknown) => {
    const c = items.find((x) => colId(x) === String(cid));
    return c ? colLabel(c) : String(cid);
  };
  const [open, setOpen] = useState(false);
  const panel = useRef<HTMLDivElement>(null);
  // 打开后焦点进到第一个勾选框，键盘用户能直接操作（弹层渲染在页面末尾，Tab 走不到）；等弹层渲染完再移
  useEffect(() => {
    if (!open) return;
    let tries = 0;
    let t = 0;
    const go = () => {
      const input = panel.current?.querySelector<HTMLInputElement>('input');
      if (input) input.focus();
      else if (tries++ < 10) t = window.setTimeout(go, 30);
    };
    t = window.setTimeout(go, 0);
    return () => window.clearTimeout(t);
  }, [open]);
  const visible = items.filter((c) => !c.hidden).length;
  const isDefault =
    state.hidden.length === 0 &&
    state.shown.length === 0 &&
    state.order.length === 0;

  const toggle = (cid: string, show: boolean) => {
    const hidden = state.hidden.filter((x) => x !== cid);
    const shown = state.shown.filter((x) => x !== cid);
    const isDef = defaults.includes(cid);
    // 和默认一致就不记，以后默认变了能跟上
    if (show && isDef) shown.push(cid);
    // 宽屏自动补上的列，手动关掉要记下来，否则下次又被补上
    if (!show && (!isDef || arranged.auto.has(cid))) hidden.push(cid);
    update({ ...state, hidden, shown });
  };

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );
  const ids = items.map((c) => colId(c) as string);
  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    const from = ids.indexOf(String(active.id));
    const to = ids.indexOf(String(over.id));
    update({ ...state, order: arrayMove(ids, from, to) });
  };

  const content = (
    <div ref={panel} role="dialog" aria-label="列设置" style={{ width: 232 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 2px 6px 8px',
        }}
      >
        <span style={{ fontSize: 13, fontWeight: 600, color: palette.ink }}>
          显示的列
        </span>
        <Button
          type="link"
          size="small"
          disabled={isDefault}
          onClick={() => update(EMPTY)}
        >
          恢复默认
        </Button>
      </div>
      <div style={{ maxHeight: 360, overflowY: 'auto' }}>
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={onDragEnd}
          accessibility={{
            screenReaderInstructions: {
              draggable:
                '按空格键拿起，用上下方向键移动，再按空格键放下；按 Esc 取消',
            },
            announcements: {
              onDragStart: ({ active }) => `已拿起「${nameOf(active.id)}」`,
              onDragOver: ({ active, over }) =>
                over
                  ? `「${nameOf(active.id)}」移到「${nameOf(over.id)}」的位置`
                  : `「${nameOf(active.id)}」不在列表上`,
              onDragEnd: ({ active, over }) =>
                over
                  ? `已把「${nameOf(active.id)}」放到「${nameOf(over.id)}」的位置`
                  : `已放下「${nameOf(active.id)}」`,
              onDragCancel: ({ active }) =>
                `已取消，「${nameOf(active.id)}」回到原位`,
            },
          }}
        >
          <SortableContext items={ids} strategy={verticalListSortingStrategy}>
            {items.map((c) => {
              const cid = colId(c) as string;
              const checked = !c.hidden;
              return (
                <Row
                  key={cid}
                  id={cid}
                  label={colLabel(c)}
                  checked={checked}
                  // 至少留一列可设置的列
                  disabled={checked && visible <= 1}
                  onToggle={(v) => toggle(cid, v)}
                />
              );
            })}
          </SortableContext>
        </DndContext>
      </div>
    </div>
  );

  const settings = items.length ? (
    <Popover
      content={content}
      trigger="click"
      placement="bottomRight"
      arrow={false}
      open={open}
      onOpenChange={(v) => {
        setOpen(v);
        // 关闭（含 Esc）后焦点回到按钮
        if (!v) trigger.current?.focus();
      }}
    >
      <Button
        ref={trigger}
        icon={<Columns3 size={16} />}
        aria-label="列设置：选择显示的列"
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        列
      </Button>
    </Popover>
  ) : null;

  return { columns: arranged.columns, settings };
}
