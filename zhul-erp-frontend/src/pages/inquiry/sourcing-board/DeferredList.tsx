import { CalendarOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import { EmptyHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { Card, LevelPill } from '../shared/components';
import type { DeferredGroup } from '../shared/service';

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

/** 已延后：按回来的日期分组，每行是一张询盘延后到同一天的任务 */
const DeferredList: React.FC<{
  groups: DeferredGroup[];
  canAssign: boolean;
  busy: boolean;
  onOpenInquiry: (id: number) => void;
  onOpenTask: (id: number) => void;
  onChangeDate: (g: DeferredGroup) => void;
  onNow: (g: DeferredGroup) => void;
}> = ({
  groups,
  canAssign,
  busy,
  onOpenInquiry,
  onOpenTask,
  onChangeDate,
  onNow,
}) => {
  const { palette } = useAppTheme();
  if (groups.length === 0) {
    return (
      <Card>
        <EmptyHint
          title="没有延后的任务"
          description="在待分配里勾选任务点「延后」，到期当天会自动回来。"
        />
      </Card>
    );
  }
  const byDay = new Map<string, DeferredGroup[]>();
  for (const g of groups) {
    byDay.set(g.deferUntil, [...(byDay.get(g.deferUntil) ?? []), g]);
  }
  return (
    <>
      {[...byDay.entries()].map(([day, rows]) => {
        const d = dayjs(day);
        const whole = rows.filter((r) => r.wholeInquiry && r.taskCount > 1);
        const tasks = rows.reduce((n, r) => n + r.taskCount, 0);
        return (
          <Card key={day} style={{ padding: 0, overflow: 'hidden' }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '14px 20px',
              }}
            >
              <CalendarOutlined style={{ color: palette.link }} />
              <b style={{ color: palette.ink, fontSize: 15 }}>
                {d.format('MM-DD')} {WEEK[d.day()]}回来
              </b>
              <span style={{ color: palette.mute, fontSize: 12 }}>
                {whole.length ? `整张询盘 ${whole.length} 张 · ` : ''}
                {tasks} 个任务
              </span>
            </div>
            {rows.map((g) => (
              <div
                key={`${g.customerInquiryId}-${day}`}
                style={{
                  display: 'grid',
                  gridTemplateColumns:
                    'minmax(0, 1.2fr) minmax(0, 1fr) minmax(0, 1.2fr) auto',
                  gap: 14,
                  alignItems: 'center',
                  padding: '14px 20px',
                  borderTop: `1px solid ${palette.hairline}`,
                }}
              >
                <div style={{ display: 'grid', gap: 4 }}>
                  <span style={{ display: 'inline-flex', gap: 6 }}>
                    <button
                      type="button"
                      className="zhul-link"
                      onClick={() =>
                        g.taskCount === 1
                          ? onOpenTask(g.taskIds[0])
                          : onOpenInquiry(g.customerInquiryId)
                      }
                    >
                      {g.taskCode ?? g.inquiryCode}
                    </button>
                    <LevelPill value={g.level} />
                  </span>
                  <span style={{ color: palette.sub, fontSize: 12 }}>
                    {[g.customerName, g.salesName && `业务员 ${g.salesName}`]
                      .filter(Boolean)
                      .join(' · ')}
                  </span>
                </div>
                <div style={{ display: 'grid', gap: 2 }}>
                  <b style={{ color: palette.ink }}>{g.taskCount} 个任务</b>
                  <span style={{ color: palette.mute, fontSize: 12 }}>
                    {g.taskCount === 1
                      ? [g.brand, g.category, `${g.itemCount} 个型号`]
                          .filter(Boolean)
                          .join(' · ')
                      : `共 ${g.itemCount} 个型号${g.wholeInquiry ? ' · 整张询盘' : ''}`}
                  </span>
                </div>
                <div style={{ display: 'grid', gap: 2, minWidth: 0 }}>
                  <span
                    style={{
                      color: g.reason ? palette.ink : palette.mute,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {g.reason || '—'}
                  </span>
                  <span style={{ color: palette.mute, fontSize: 12 }}>
                    {g.deferByName ?? ''}
                    {g.deferTime
                      ? ` · ${dayjs(g.deferTime).format('MM-DD HH:mm')} 延后`
                      : ''}
                  </span>
                </div>
                {canAssign && (
                  <span style={{ display: 'inline-flex', gap: 8 }}>
                    <Button size="small" onClick={() => onChangeDate(g)}>
                      改日期
                    </Button>
                    <Button
                      size="small"
                      type="primary"
                      loading={busy}
                      onClick={() => onNow(g)}
                    >
                      现在处理
                    </Button>
                  </span>
                )}
              </div>
            ))}
          </Card>
        );
      })}
      <span style={{ color: palette.mute, fontSize: 12 }}>
        延后的任务不参与按推荐、按规则和自动分配；到期当天自动回到待分配。
      </span>
    </>
  );
};

export default DeferredList;
