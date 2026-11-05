import { history } from '@umijs/max';
import { Skeleton } from 'antd';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { fmtDate, Pill } from './components';
import { type FollowupItem, followupApi, type OrderParts } from './service';

const FULFIL_STEPS = ['F1', 'F2', 'F3', 'F4', 'F5', 'F6'];

/** 履约进度：六格进度条 + 当前阶段，异常时为橙色（K10） */
export const ProgressCell: React.FC<{
  item: FollowupItem;
  expanded: boolean;
  onToggle: () => void;
}> = ({ item, expanded, onToggle }) => {
  const { palette } = useAppTheme();
  const at = FULFIL_STEPS.indexOf(item.stageCode);
  const warn = (item.exceptionFlags ?? 0) > 0;
  return (
    <div style={{ display: 'grid', gap: 4, minWidth: 140 }}>
      <div style={{ display: 'flex', gap: 3 }}>
        {FULFIL_STEPS.map((s, i) => (
          <span
            key={s}
            style={{
              width: 20,
              height: 5,
              borderRadius: 3,
              background:
                i < at
                  ? palette.link
                  : i === at
                    ? warn
                      ? palette.orange
                      : palette.link
                    : palette.hairline,
            }}
          />
        ))}
      </div>
      <span>
        <Pill tone={warn ? 'orange' : 'accent'}>
          {item.stageCode} {item.stageName}
        </Pill>
      </span>
      {(item.partCount ?? 0) > 1 && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onToggle();
          }}
          style={{
            all: 'unset',
            cursor: 'pointer',
            fontSize: 12,
            color: palette.link,
          }}
        >
          共 {item.partCount} 部分 · {expanded ? '收起' : '展开'}
        </button>
      )}
    </div>
  );
};

/** 订单履约行展开：每部分进度、已告诉客户的节点、分批出运（K12 下半部分），只读 */
export const PartsPanel: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  const [data, setData] = useState<OrderParts>();
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let alive = true;
    followupApi
      .parts(item.id)
      .then((d) => alive && setData(d))
      .catch(() => alive && setFailed(true));
    return () => {
      alive = false;
    };
  }, [item.id]);
  if (failed)
    return (
      <div style={{ color: palette.mute, padding: 8 }}>
        分部分进度加载失败，请刷新重试
      </div>
    );
  if (!data) return <Skeleton active paragraph={{ rows: 2 }} />;
  const th: React.CSSProperties = {
    textAlign: 'left',
    fontWeight: 500,
    fontSize: 12,
    color: palette.mute,
    padding: '6px 10px',
  };
  const td: React.CSSProperties = {
    fontSize: 13,
    color: palette.ink,
    padding: '8px 10px',
    borderTop: `1px solid ${palette.hairline}`,
    verticalAlign: 'top',
  };
  const sub = { fontSize: 12, color: palette.mute };
  return (
    <div style={{ display: 'grid', gap: 12, padding: '4px 8px 8px' }}>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              {[
                '部分',
                '商品',
                '来源',
                '阶段',
                '预计发货',
                '到仓',
                '现在的情况',
                '要告诉客户的',
              ].map((h) => (
                <th key={h} style={th}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.parts.map((p) => (
              <tr key={p.label}>
                <td style={{ ...td, fontWeight: 600, whiteSpace: 'nowrap' }}>
                  {p.label}
                </td>
                <td style={td}>{p.goods || '—'}</td>
                <td style={td}>
                  {p.poId ? (
                    <button
                      type="button"
                      onClick={() => history.push(`/purchase/orders/${p.poId}`)}
                      style={{
                        all: 'unset',
                        cursor: 'pointer',
                        color: palette.link,
                      }}
                    >
                      {p.poNo}
                    </button>
                  ) : (
                    p.poNo || '库存直发'
                  )}
                  <div style={sub}>{p.supplier}</div>
                </td>
                <td style={td}>
                  <Pill tone="accent">
                    {p.stageCode} {p.stageName}
                  </Pill>
                </td>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>
                  {p.expectedShipDate ? fmtDate(p.expectedShipDate) : '—'}
                </td>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>
                  {p.arrival || '—'}
                </td>
                <td style={td}>{p.situation || '—'}</td>
                <td style={td}>{p.tell || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
          gap: 16,
        }}
      >
        <div>
          <div style={{ fontSize: 12, fontWeight: 600, color: palette.ink }}>
            已经告诉客户的节点（来自跟进记录）
          </div>
          {data.told.length === 0 ? (
            <div style={{ ...sub, marginTop: 6 }}>还没有记录</div>
          ) : (
            data.told.map((t) => (
              <div
                key={`${t.date}-${t.node}`}
                style={{
                  display: 'flex',
                  gap: 10,
                  fontSize: 13,
                  marginTop: 6,
                  flexWrap: 'wrap',
                }}
              >
                <span style={{ color: palette.green }}>●</span>
                <span style={{ color: palette.sub }}>{fmtDate(t.date)}</span>
                <b style={{ color: palette.ink }}>{t.node}</b>
                <span style={{ color: palette.sub }}>{t.content}</span>
                {t.channelName && <span style={sub}>{t.channelName}</span>}
              </div>
            ))
          )}
        </div>
        {data.batches.length > 0 && (
          <div
            style={{
              background: palette.inset,
              borderRadius: 10,
              padding: '10px 12px',
            }}
          >
            <div style={{ fontSize: 12, fontWeight: 600, color: palette.ink }}>
              分批出运
            </div>
            {data.batches.map((b) => (
              <div key={b.shNo} style={{ fontSize: 13, marginTop: 6 }}>
                {b.shNo} · {b.carrier || '—'}
                {b.waybillNo && ` ${b.waybillNo}`} · {fmtDate(b.shippedDate)}{' '}
                出运 · 预计 {fmtDate(b.etaDate)} 送达
              </div>
            ))}
            <div style={{ ...sub, marginTop: 6 }}>
              整单以最后一批的预计送达进入售后评价
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
