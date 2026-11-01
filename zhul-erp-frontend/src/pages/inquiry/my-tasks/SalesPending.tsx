import { ClockCircleOutlined } from '@ant-design/icons';
import { App, Button, Checkbox, Skeleton, Space } from 'antd';
import React, { useMemo, useState } from 'react';
import { EmptyHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { Card, LevelPill, Pill } from '../shared/components';
import { formatMinutes } from '../shared/constants';
import {
  inquiryApi,
  myTaskApi,
  type SalesPendingTask,
} from '../shared/service';

/** 我的询价 → 待认领：询盘负责人决定每个任务自己找还是转给采购，到期自动转给采购 */
const SalesPending: React.FC<{
  tasks: SalesPendingTask[] | null;
  onChanged: () => void;
  onViewPending: () => void;
}> = ({ tasks, onChanged, onViewPending }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [selected, setSelected] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);

  const groups = useMemo(() => {
    const map = new Map<number, SalesPendingTask[]>();
    (tasks ?? []).forEach((t) => {
      map.set(t.customerInquiryId, [
        ...(map.get(t.customerInquiryId) ?? []),
        t,
      ]);
    });
    return [...map.values()];
  }, [tasks]);

  const act = async (
    ids: number[],
    fn: (ids: number[]) => Promise<unknown>,
    ok: string,
  ) => {
    setBusy(true);
    try {
      await fn(ids);
      message.success(ok);
      setSelected((s) => s.filter((id) => !ids.includes(id)));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
      // 失败多半是任务刚被自动转走，刷新后它就从列表消失了
      onChanged();
    }
  };
  const claim = (ids: number[]) =>
    act(ids, myTaskApi.claim, '已领走，去待处理填价');
  const transfer = (ids: number[]) =>
    act(ids, inquiryApi.transferTasks, '已转给采购');

  if (!tasks) return <Skeleton active />;
  if (tasks.length === 0)
    return (
      <Card>
        <EmptyHint
          title="没有等你认领的任务"
          description="询盘确认后，拆出的任务会先到这里。"
          actionText="查看待处理"
          onAction={onViewPending}
        />
      </Card>
    );
  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <div style={{ color: palette.sub, fontSize: 13 }}>
        这里只有你负责的询盘。到时间没处理的任务会自动转给采购，之后就不能再认领。
      </div>
      {selected.length > 0 && (
        <Card style={{ padding: '12px 16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <b style={{ color: palette.ink }}>已选 {selected.length} 个任务</b>
            <Space style={{ marginLeft: 'auto' }}>
              <Button loading={busy} onClick={() => transfer(selected)}>
                转采购
              </Button>
              <Button
                type="primary"
                loading={busy}
                onClick={() => claim(selected)}
              >
                我来询价
              </Button>
            </Space>
          </div>
        </Card>
      )}
      {groups.map((group) => {
        const head = group[0];
        return (
          <Card key={head.customerInquiryId}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: 10,
                marginBottom: 8,
              }}
            >
              <b style={{ color: palette.ink }}>{head.inquiryCode}</b>
              <LevelPill value={head.level} />
              {head.urgent && <Pill tone="red">紧急</Pill>}
              <span style={{ color: palette.sub }}>{head.customerName}</span>
              {head.quoteDeadline && (
                <span
                  style={{
                    marginLeft: 'auto',
                    color: palette.mute,
                    fontSize: 12,
                  }}
                >
                  报价截止 {head.quoteDeadline}
                </span>
              )}
            </div>
            {group.map((t) => {
              const soon = t.remainingMinutes <= 30;
              return (
                <div
                  key={t.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: 16,
                    padding: '10px 0',
                  }}
                >
                  <Checkbox
                    aria-label={`选择 ${t.taskCode}`}
                    checked={selected.includes(t.id)}
                    onChange={(e) =>
                      setSelected((s) =>
                        e.target.checked
                          ? [...s, t.id]
                          : s.filter((id) => id !== t.id),
                      )
                    }
                  />
                  <span
                    style={{ width: 170, color: palette.link, fontWeight: 600 }}
                  >
                    {t.taskCode}
                  </span>
                  <span style={{ flex: '1 1 200px', color: palette.ink }}>
                    {t.brand}
                    {t.category ? ` · ${t.category}` : ''}
                  </span>
                  <span style={{ width: 80, color: palette.sub }}>
                    {t.itemCount} 个型号
                  </span>
                  <span
                    style={{
                      width: 210,
                      fontWeight: 600,
                      color: soon ? palette.red : palette.sub,
                    }}
                  >
                    <ClockCircleOutlined /> {formatMinutes(t.remainingMinutes)}
                    后转给采购
                  </span>
                  <Space>
                    <Button loading={busy} onClick={() => transfer([t.id])}>
                      转采购
                    </Button>
                    <Button
                      type="primary"
                      loading={busy}
                      onClick={() => claim([t.id])}
                    >
                      我来询价
                    </Button>
                  </Space>
                </div>
              );
            })}
          </Card>
        );
      })}
    </div>
  );
};

export default SalesPending;
