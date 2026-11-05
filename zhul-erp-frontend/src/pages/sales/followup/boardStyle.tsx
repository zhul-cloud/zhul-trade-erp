import {
  CommentOutlined,
  CompassOutlined,
  InboxOutlined,
  RiseOutlined,
  SolutionOutlined,
} from '@ant-design/icons';
import type React from 'react';

/** 五个板块的颜色与图标（原型 K1「轨迹」版） */
export const BOARD_STYLE: Record<
  number,
  { color: string; soft: string; icon: React.ReactNode }
> = {
  1: { color: '#7E22CE', soft: '#F3E8FF', icon: <CompassOutlined /> },
  2: { color: '#4338CA', soft: '#E0E7FF', icon: <SolutionOutlined /> },
  3: { color: '#1D4ED8', soft: '#DBEAFE', icon: <InboxOutlined /> },
  4: { color: '#BE185D', soft: '#FCE7F3', icon: <CommentOutlined /> },
  5: { color: '#15803D', soft: '#DCFCE7', icon: <RiseOutlined /> },
};
export const TODAY_COLOR = '#4F46E5';
/** 今日待办的视图编号（板块编号从 1 开始） */
export const VIEW_TODAY = 0;

export const styleOf = (board: number) => BOARD_STYLE[board] ?? BOARD_STYLE[2];
