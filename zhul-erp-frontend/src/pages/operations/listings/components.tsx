import React from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { PLATFORM_META } from '@/pages/product/products/cards/SeoCard';
import type { ListingTab, ListingTask } from './service';

export const TABS: {
  key: ListingTab;
  label: string;
  tone: Tone;
  empty: string;
}[] = [
  { key: 'all', label: '全部', tone: 'gray', empty: '还没有发品任务' },
  {
    key: 'pending',
    label: '待发布',
    tone: 'orange',
    empty:
      '没有待发布的商品；资料核对、某个平台的营销内容写完后，会自动生成这个平台的发品任务',
  },
  {
    key: 'unconfirmed',
    label: '待确认上线',
    tone: 'cyan',
    empty: '没有待确认上线的商品；自动发布到独立站后，在这里确认线上页面',
  },
  {
    key: 'updated',
    label: '内容有更新',
    tone: 'orange',
    empty: '已发布的商品营销内容都没有更新',
  },
  {
    key: 'replace',
    label: '待替换素材',
    tone: 'violet',
    empty: '没有需要替换参考素材的商品',
  },
  {
    key: 'published',
    label: '已发布',
    tone: 'green',
    empty: '还没有全部发完的商品',
  },
  {
    key: 'cancelled',
    label: '已取消',
    tone: 'mute',
    empty: '没有全部取消的商品',
  },
];

export type Tone =
  | 'gray'
  | 'accent'
  | 'cyan'
  | 'orange'
  | 'violet'
  | 'green'
  | 'mute';

/** 一个任务的状态与提醒：列表标签、详情页平台切换共用 */
export const taskState = (task: ListingTask): [Tone, string] =>
  task.status === 1
    ? task.needMedia
      ? ['orange', '素材待补']
      : ['accent', '待发布']
    : task.status === 4
      ? ['cyan', '发布中']
      : task.status === 5
        ? ['cyan', '已上线待确认']
        : task.status === 2
          ? task.contentUpdated
            ? ['orange', '内容有更新']
            : task.needReplace
              ? ['violet', '待替换素材']
              : ['green', '已发布']
          : ['mute', '已取消'];

/** 平台简称；独立站带站点名（「独立站 · 站点名」） */
export const platformShort = (task: ListingTask) =>
  task.platform === 'site'
    ? task.platformLabel
    : (PLATFORM_META[task.platform]?.short ?? task.platformLabel);

/** 各平台标签：平台简称 + 这个平台的状态或提醒 */
export const PlatformTaskChip: React.FC<{ task: ListingTask }> = ({ task }) => {
  const [tone, text] = taskState(task);
  return (
    <Pill tone={tone}>
      {platformShort(task)} {text}
    </Pill>
  );
};
