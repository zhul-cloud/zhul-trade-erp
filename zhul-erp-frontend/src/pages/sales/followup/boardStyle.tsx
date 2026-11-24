import {
  Compass,
  IdCard,
  Inbox,
  MessageSquare,
  TrendingUp,
} from 'lucide-react';
import React from 'react';

/**
 * 五个板块的图标。板块不再各用一种颜色（对比图 R10 方案 B，2026-11-24 用户确认）：
 * 步骤靠「第 n 步」、图标和箭头区分，阶段标签用中性色，主题色只表示当前查看的板块
 */
export const BOARD_ICON: Record<number, React.ReactNode> = {
  1: <Compass size="1em" />,
  2: <IdCard size="1em" />,
  3: <Inbox size="1em" />,
  4: <MessageSquare size="1em" />,
  5: <TrendingUp size="1em" />,
};

/** 今日待办的视图编号（板块编号从 1 开始） */
export const VIEW_TODAY = 0;
