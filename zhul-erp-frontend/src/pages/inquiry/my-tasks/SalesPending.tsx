import {
  ClockCircleOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { App, Button, Checkbox, Skeleton } from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import {
  BrandGroupHeader,
  FoldToggle,
  fetchFirst,
  groupByBrand,
  LOAD_STEP,
  LoadMore,
  useFold,
} from '../shared/BrandGroups';
import { Card, LevelPill, Pill } from '../shared/components';
import { formatMinutes } from '../shared/constants';
import {
  type SalesPending as Data,
  inquiryApi,
  myTaskApi,
} from '../shared/service';

/** 剩余不到这么多分钟时倒计时标红 */
const SOON_MINUTES = 30;

/**
 * 我的询价 → 待认领：询盘负责人决定每个任务自己找还是转给采购，到期自动转给采购。
 * 结构与「询价分配 · 待分配」一致：按品牌分组、组头可全选已加载的任务、批量操作条、底部「加载更多」。
 */
const SalesPending: React.FC<{
  /** 认领或转采购后通知外层刷新页签计数 */
  onChanged: () => void;
  onViewPending: () => void;
}> = ({ onChanged, onViewPending }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [data, setData] = useState<Data | null>(null);
  const [error, setError] = useState('');
  /** 先显示 20 个，底部「加载更多」 */
  const [limit, setLimit] = useState(LOAD_STEP);
  const [more, setMore] = useState(false);
  const [selected, setSelected] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);
  const fold = useFold();

  const load = useCallback(async () => {
    setError('');
    try {
      const pages = await fetchFirst(limit, myTaskApi.salesPending);
      setData({ ...pages[0], tasks: pages.flatMap((x) => x.tasks) });
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setMore(false);
    }
  }, [limit]);

  // 加载更多时保留已勾选的任务；认领、转采购后由操作本身清空勾选
  useEffect(() => {
    load();
  }, [load]);

  const groups = useMemo(() => groupByBrand(data?.tasks ?? []), [data]);
  const brandSummary = useMemo(
    () => new Map((data?.brands ?? []).map((b) => [b.brand, b])),
    [data],
  );

  const act = async (
    ids: number[],
    fn: (ids: number[]) => Promise<unknown>,
    ok: string,
  ) => {
    setBusy(true);
    try {
      await fn(ids);
      message.success(ok);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
      // 失败多半是任务刚被自动转走，刷新后它就从列表消失了
      setSelected([]);
      load();
      onChanged();
    }
  };
  const claim = (ids: number[]) =>
    act(ids, myTaskApi.claim, '已领走，去待处理填价');
  const transfer = (ids: number[]) =>
    act(ids, inquiryApi.transferTasks, '已转给采购');

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!data) return <Skeleton active />;
  if (data.total === 0)
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
    <div style={{ display: 'grid', gap: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span style={{ color: palette.mute, fontSize: 12, flex: 1 }}>
          这里只有你负责的询盘。到时间没处理的任务会自动转给采购，之后就不能再认领。
        </span>
        <FoldToggle mode={fold.mode} onChange={fold.setAll} />
      </div>

      {selected.length > 0 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '12px 16px',
            borderRadius: 12,
            background: palette.accentSoft,
            border: `1px solid ${palette.accentLine}`,
          }}
        >
          <span style={{ color: palette.ink, fontWeight: 600 }}>
            已选 {selected.length} 个任务
          </span>
          <button
            type="button"
            className="zhul-link"
            onClick={() => setSelected([])}
          >
            取消选择
          </button>
          <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
            <Button
              type="primary"
              size="small"
              icon={<UserOutlined />}
              loading={busy}
              onClick={() => claim(selected)}
            >
              我来询价
            </Button>
            <Button
              size="small"
              icon={<SendOutlined />}
              loading={busy}
              onClick={() => transfer(selected)}
            >
              转采购
            </Button>
          </span>
        </div>
      )}

      {groups.map(([brand, tasks]) => {
        const summary = brandSummary.get(brand);
        const folded = fold.isFolded(brand);
        const all = tasks.every((t) => selected.includes(t.id));
        const some = tasks.some((t) => selected.includes(t.id));
        return (
          <Card key={brand} style={{ padding: 0, overflow: 'hidden' }}>
            <BrandGroupHeader
              brand={brand}
              count={tasks.length}
              summary={summary}
              folded={folded}
              onToggle={() => fold.toggle(brand)}
              checkbox={{
                checked: all,
                indeterminate: some && !all,
                onChange: (checked) =>
                  setSelected((prev) =>
                    checked
                      ? [...new Set([...prev, ...tasks.map((t) => t.id)])]
                      : prev.filter((id) => !tasks.some((t) => t.id === id)),
                  ),
              }}
              extra={
                summary?.minutes != null ? (
                  <span
                    style={{
                      color:
                        summary.minutes <= SOON_MINUTES
                          ? palette.red
                          : palette.mute,
                    }}
                  >
                    最早 {formatMinutes(summary.minutes)}后转给采购
                  </span>
                ) : undefined
              }
            />
            {!folded &&
              tasks.map((t) => {
                const soon = t.remainingMinutes <= SOON_MINUTES;
                const checked = selected.includes(t.id);
                return (
                  <div
                    key={t.id}
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '24px 300px minmax(0, 1fr) auto',
                      gap: 14,
                      alignItems: 'center',
                      padding: '14px 20px',
                      borderTop: `1px solid ${palette.hairline}`,
                      background: checked ? palette.hover : 'transparent',
                    }}
                  >
                    <Checkbox
                      aria-label={`选择 ${t.taskCode}`}
                      checked={checked}
                      onChange={(e) =>
                        setSelected((prev) =>
                          e.target.checked
                            ? [...prev, t.id]
                            : prev.filter((id) => id !== t.id),
                        )
                      }
                    />
                    <div style={{ display: 'grid', gap: 4 }}>
                      <span
                        style={{
                          display: 'inline-flex',
                          gap: 6,
                          alignItems: 'center',
                          flexWrap: 'wrap',
                        }}
                      >
                        <span style={{ color: palette.ink, fontWeight: 600 }}>
                          {t.taskCode}
                        </span>
                        <LevelPill value={t.level} />
                        {t.urgent && <Pill tone="red">紧急</Pill>}
                      </span>
                      <span style={{ color: palette.mute, fontSize: 12 }}>
                        {t.brand}
                        {t.category ? ` · ${t.category}` : ''} · {t.itemCount}{' '}
                        个型号
                      </span>
                      <span style={{ color: palette.sub, fontSize: 12 }}>
                        询盘 {t.inquiryCode}
                        {t.customerName ? ` · ${t.customerName}` : ''}
                      </span>
                    </div>
                    <div style={{ display: 'grid', gap: 2 }}>
                      <span style={{ color: palette.mute, fontSize: 12 }}>
                        自动转给采购
                      </span>
                      <span
                        style={{
                          fontWeight: 600,
                          color: soon ? palette.red : palette.sub,
                        }}
                      >
                        <ClockCircleOutlined />{' '}
                        {formatMinutes(t.remainingMinutes)}后转给采购
                      </span>
                    </div>
                    <span style={{ display: 'inline-flex', gap: 6 }}>
                      <Button
                        size="small"
                        loading={busy}
                        onClick={() => transfer([t.id])}
                      >
                        转采购
                      </Button>
                      <Button
                        size="small"
                        type="primary"
                        loading={busy}
                        onClick={() => claim([t.id])}
                      >
                        我来询价
                      </Button>
                    </span>
                  </div>
                );
              })}
          </Card>
        );
      })}

      <LoadMore
        shown={data.tasks.length}
        total={data.total}
        loading={more}
        onMore={() => {
          setMore(true);
          setLimit((n) => n + LOAD_STEP);
        }}
      />
    </div>
  );
};

export default SalesPending;
