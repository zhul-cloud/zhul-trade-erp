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
import React, { isValidElement, useMemo, useState } from 'react';
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

/** 可设置的列：有标识、没有固定在两侧 */
const movable = (c: Col) => !c.fixed && colId(c) !== undefined;

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
  const order = state.order.filter((id) => ids.includes(id));
  ids.forEach((id, i) => {
    if (!order.includes(id)) order.splice(Math.min(i, order.length), 0, id);
  });
  const byId = new Map(columns.filter(movable).map((c) => [colId(c), c]));
  let next = 0;
  return columns.map((c) => {
    if (!movable(c)) return c;
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

const userKey = () => {
  try {
    const u = JSON.parse(localStorage.getItem('zhul_user') ?? '{}');
    return String(u?.userid ?? 'anon');
  } catch {
    return 'anon';
  }
};

const storageKey = (id: string) => `zhul_cols:v1:${userKey()}:${id}`;

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
  const hiddenKey = (opts.hidden ?? []).join('|');
  // 页面写的是列标识或列名，这里统一换成列标识
  const labelKey = columns.map((c) => `${colId(c)}=${colLabel(c)}`).join('|');
  const defaults = useMemo(() => {
    const want = new Set([
      ...AUDIT_KEYS,
      ...hiddenKey.split('|').filter(Boolean),
    ]);
    return labelKey
      .split('|')
      .map((x) => x.split('='))
      .filter(([cid, label]) => want.has(cid) || want.has(label))
      .map(([cid]) => cid);
  }, [hiddenKey, labelKey]);
  const arranged = useMemo(
    () => arrange(columns, state, defaults),
    [columns, state, defaults],
  );
  const update = (s: ColumnState) => {
    setState(s);
    save(id, s);
  };
  const items = arranged.filter(movable);
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
    if (!show && !isDef) hidden.push(cid);
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
    <div style={{ width: 232 }}>
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
    >
      <Button icon={<Columns3 size={16} />} aria-label="列设置：选择显示的列">
        列
      </Button>
    </Popover>
  ) : null;

  return { columns: arranged, settings };
}
