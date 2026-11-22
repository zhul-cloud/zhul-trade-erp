import {
  CommentOutlined,
  CompassOutlined,
  InboxOutlined,
  RiseOutlined,
  SolutionOutlined,
} from '@ant-design/icons';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { CHART_SERIES, mix } from '@/theme/palette';

/** 五个板块的图标 */
const BOARD_ICON: Record<number, React.ReactNode> = {
  1: <CompassOutlined />,
  2: <SolutionOutlined />,
  3: <InboxOutlined />,
  4: <CommentOutlined />,
  5: <RiseOutlined />,
};

/**
 * 浅色下文字色 = 板块色与正文色按这个比例混合，在 8% 浅底上不低于 4.5:1
 * （亮绿本身最浅，要多混一些正文色）
 */
const LIGHT_TEXT_WEIGHT: Record<number, number> = {
  1: 0.7,
  2: 0.7,
  3: 0.7,
  4: 0.7,
  5: 0.55,
};

/** 今日待办的视图编号（板块编号从 1 开始） */
export const VIEW_TODAY = 0;

export interface BoardStyle {
  /** 图形用色：图标、轨道、描边 */
  color: string;
  /** 文字色：浅色下加深、深色下提亮，在浅底上不低于 4.5:1 */
  text: string;
  /** 浅底：浅色 8%、深色 12% */
  soft: string;
  /** 实心小标（步骤序号、选中的「第 N 步」）的底色与字色 */
  badge: string;
  badgeInk: string;
  icon: React.ReactNode;
}

export type StepStyle = Omit<BoardStyle, 'icon'>;

/**
 * 按当前明暗模式取第 N 步（1–5）的颜色；客户跟进、供应链跟进两条链路共用。
 * 五步各用一种颜色，不随主题色变，方便一眼区分：前四步取图表系列色（蓝、青、棕黄、珊瑚），
 * 第 5 步用亮绿（图表系列的绿偏暗）；主题色留给「今日待办」和选中状态（2026-11-22 用户在对比图里选定）
 */
export const useStepStyle = (): ((step: number) => StepStyle) => {
  const { mode, palette } = useAppTheme();
  const light = mode === 'light';
  const colors = [...CHART_SERIES.slice(0, 4), palette.greenMain];
  return (step: number) => {
    const b = colors[step - 1] ? step : 2;
    const color = colors[b - 1];
    const text = mix(color, palette.ink, light ? LIGHT_TEXT_WEIGHT[b] : 0.9);
    return {
      color,
      text,
      soft: mix(color, palette.card, light ? 0.08 : 0.12),
      // 白字放在原色上只有 3.2–3.7:1，浅色用加深色作底，深色改用深字
      badge: light ? text : color,
      badgeInk: light ? '#fff' : palette.card,
    };
  };
};

/** 按当前明暗模式取客户跟进板块的颜色与图标 */
export const useBoardStyle = (): ((board: number) => BoardStyle) => {
  const step = useStepStyle();
  return (board: number) => {
    const b = BOARD_ICON[board] ? board : 2;
    return { ...step(b), icon: BOARD_ICON[b] };
  };
};
