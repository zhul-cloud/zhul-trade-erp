import { Button, Drawer } from 'antd';
import { PanelRightOpen } from 'lucide-react';
import React, { useState } from 'react';
import { useTabActive } from '@/components/PageTabs/hooks';

const read = (key: string) => {
  try {
    return localStorage.getItem(key) === '1';
  } catch {
    return false;
  }
};

const write = (key: string, open: boolean) => {
  try {
    localStorage.setItem(key, open ? '1' : '0');
  } catch {
    // 无痕窗口等存不了时只是不记住，不影响使用
  }
};

/**
 * 可收起的右侧面板（D74，对比图 R33 方案 B）：列表旁的辅助信息（如跟进页的作战卡）不再常驻占一栏，
 * 默认收起，表头右侧一个按钮打开，从右侧滑出盖在列表上；不加遮罩，列表照常可点（面板内容跟随选中行）。
 * 开关状态按人记在本机（storageKey）。
 */
export function useSidePanel(storageKey: string, title: string) {
  const [open, setOpen] = useState(() => read(storageKey));
  // 抽屉挂在 body 上：切到别的标签页时页面只是隐藏，抽屉要跟着收起，回来再显示
  const active = useTabActive();
  const set = (v: boolean) => {
    setOpen(v);
    write(storageKey, v);
  };
  const button = (
    <Button
      icon={<PanelRightOpen size={16} />}
      aria-pressed={open}
      onClick={() => set(!open)}
    >
      {title}
    </Button>
  );
  const panel = (children: React.ReactNode) => (
    // 面板内容自带标题（如「作战卡 · S7 成交推进」），抽屉头只留关闭按钮
    <Drawer
      open={open && active}
      onClose={() => set(false)}
      mask={false}
      size={400}
      styles={{ body: { padding: 16 } }}
    >
      {children}
    </Drawer>
  );
  return { open, button, panel };
}
