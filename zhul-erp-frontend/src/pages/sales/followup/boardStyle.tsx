import {
  Compass,
  IdCard,
  Inbox,
  MessageSquare,
  TrendingUp,
} from 'lucide-react';
import React from 'react';

/**
 * 五个板块的图标。颜色只用在顶部链路卡片的图标块上（按步骤取，见 FlowStrip，R11 方案 B）；
 * 列表里的阶段标签中性，主题色只表示当前查看的板块
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
