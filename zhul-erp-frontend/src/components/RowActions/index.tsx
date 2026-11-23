import { Dropdown } from 'antd';
import { Ellipsis } from 'lucide-react';
import React, { useState } from 'react';
import { type MoreAction, moreMenuItems } from '@/components/ListLayout';
import { IconButton } from '@/components/Shell/IconButton';

type Falsy = false | null | undefined | '' | 0;

export interface RowAction {
  key: string;
  label: string;
  icon: React.ReactNode;
  onClick: () => void;
  /** 有值时按钮置灰，悬停显示原因 */
  disabledReason?: string;
}

const SIZE = 32;
/** 相邻点击目标至少隔 8（操作列 3 位时宽 136，同原型） */
const GAP = 8;
/** 单元格左右内边距（Table cellPaddingInline 12） */
const PADDING = 24;

/**
 * 操作列宽：按图标位数计算（每个 32、间距 8，加单元格内边距），「更多」也算一位。
 * 和 tableScrollX 一起用，操作列不被截断、不遮住金额（ui-redesign S2）。
 */
export const rowActionsWidth = (slots: number) =>
  PADDING + slots * SIZE + Math.max(0, slots - 1) * GAP;

/** 一个图标位：本行不适用时留空位，每行图标位置不变 */
const Slot: React.FC<{ action: RowAction | Falsy }> = ({ action: a }) =>
  a ? (
    <IconButton
      size={SIZE}
      label={a.disabledReason ? `${a.label}：${a.disabledReason}` : a.label}
      icon={a.icon}
      aria-disabled={!!a.disabledReason}
      onClick={() => {
        if (!a.disabledReason) a.onClick();
      }}
      style={
        a.disabledReason ? { opacity: 0.4, cursor: 'not-allowed' } : undefined
      }
    />
  ) : (
    <span aria-hidden style={{ width: SIZE, flex: 'none' }} />
  );

/**
 * 表格操作列（公共组件，规范「组件标准 · 表格」，ui-redesign C5、S2、S6）：
 * 行内最多 2 个常用图标，其余收进「更多」，危险项排在菜单末尾、前面加分隔线。
 * inline 里本行不适用的写假值：留出空位，每行图标位置不变。
 * more 里的每项用 key 回调 onMore；本行没有可用的「更多」项时不显示「更多」。
 */
export const RowActions: React.FC<{
  inline?: (RowAction | Falsy)[];
  more?: (MoreAction | Falsy)[];
  onMore?: (key: string) => void;
}> = ({ inline = [], more = [], onMore }) => {
  const [open, setOpen] = useState(false);
  const list = more.filter(Boolean) as MoreAction[];
  const menu = [
    ...list.filter((a) => !a.danger),
    ...list.filter((a) => a.danger),
  ];
  return (
    // 菜单弹层经 portal 渲染，点置灰项、分隔线时事件仍按 React 树冒泡：在这里统一拦住，不触发行点击
    <div
      style={{ display: 'flex', alignItems: 'center', gap: GAP }}
      onClick={(e) => e.stopPropagation()}
    >
      {inline.length > 0 && <Slot action={inline[0]} />}
      {inline.length > 1 && <Slot action={inline[1]} />}
      {menu.length > 0 && (
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          onOpenChange={setOpen}
          menu={{
            items: moreMenuItems(menu),
            onClick: ({ key }) => onMore?.(key),
          }}
        >
          <IconButton
            size={SIZE}
            label="更多"
            pressed={open}
            icon={<Ellipsis size={18} />}
          />
        </Dropdown>
      )}
    </div>
  );
};
