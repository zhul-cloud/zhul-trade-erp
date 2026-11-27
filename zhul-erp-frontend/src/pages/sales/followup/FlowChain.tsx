import { Skeleton } from 'antd';
import { CalendarCheck } from 'lucide-react';
import React from 'react';
import { FlowStrip } from '@/components/FlowStrip';
import { Pill } from '@/components/Pill';
import { BOARD_ICON, VIEW_TODAY } from './boardStyle';
import { Card } from './components';
import { OPEN_BOARDS, type Overview } from './service';

/** 顶部链路：今日待办 + 五个板块（第 1–5 步），点卡片切换视图，当前卡片主题色描边（对比图 R10 方案 B） */
export const FlowChain: React.FC<{
  overview?: Overview;
  /** VIEW_TODAY 或板块编号 */
  active: number;
  onSelect: (view: number) => void;
}> = ({ overview, active, onSelect }) => {
  if (!overview)
    return (
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <Skeleton active paragraph={{ rows: 2 }} />
      </Card>
    );
  return (
    <FlowStrip
      title="跟进板块"
      loop="复购回到第 2 步"
      active={String(active)}
      onSelect={(k) => onSelect(Number(k))}
      lead={{
        key: String(VIEW_TODAY),
        name: '今日待办',
        icon: <CalendarCheck size="1em" />,
        value: overview.todayOverdue + overview.todayDue,
        unit: '位客户',
        sub: `逾期 ${overview.todayOverdue} · 今天 ${overview.todayDue}`,
      }}
      steps={overview.boards.map((b) => {
        const open = b.available && OPEN_BOARDS.includes(b.board);
        return {
          key: String(b.board),
          name: b.name,
          icon: BOARD_ICON[b.board],
          step: b.step,
          value: open ? b.customers : undefined,
          unit: '位客户',
          badge: (b.overdue > 0 || !!b.dormant) && (
            <span style={{ display: 'inline-flex', gap: 4 }}>
              {b.overdue > 0 && <Pill tone="red">逾期 {b.overdue}</Pill>}
              {!!b.dormant && <Pill tone="orange">沉睡 {b.dormant}</Pill>}
            </span>
          ),
          sub: [
            ...b.groups.map((g) => `${g.name} ${g.count}`),
            ...(b.nurture > 0 ? [`培育中 ${b.nurture}`] : []),
          ].join(' · '),
        };
      })}
    />
  );
};
