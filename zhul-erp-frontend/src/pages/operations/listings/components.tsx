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
    tone: 'accent',
    empty:
      '没有待发布的商品；资料核对、某个平台的营销内容写完后，会自动生成这个平台的发品任务',
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

export type Tone = 'gray' | 'accent' | 'orange' | 'violet' | 'green' | 'mute';

/** 各平台标签：平台简称 + 这个平台的状态或提醒 */
export const PlatformTaskChip: React.FC<{ task: ListingTask }> = ({ task }) => {
  const [tone, text]: [Tone, string] =
    task.status === 1
      ? task.needMedia
        ? ['orange', '素材待补']
        : ['accent', '待发布']
      : task.status === 2
        ? task.contentUpdated
          ? ['orange', '内容有更新']
          : task.needReplace
            ? ['violet', '待替换素材']
            : ['green', '已发布']
        : ['mute', '已取消'];
  return (
    <Pill tone={tone}>
      {PLATFORM_META[task.platform]?.short ?? task.platformLabel} {text}
    </Pill>
  );
};
