import { Popover, Spin } from 'antd';
import React, { useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  type ItemHistoryDetail,
  type MyTaskItem,
  myTaskApi,
  readBizError,
} from '../shared/service';

/** 型号描述下的「历史询价」：最近成交价，没有时最近询价；点开看最近 5 次成交与 5 条询价 */
const ItemHistory: React.FC<{ taskId: number; item: MyTaskItem }> = ({
  taskId,
  item,
}) => {
  const { palette } = useAppTheme();
  const [detail, setDetail] = useState<ItemHistoryDetail>();
  const [error, setError] = useState('');
  const load = () => {
    if (detail) return;
    myTaskApi
      .history(taskId, item.id)
      .then(setDetail)
      .catch((e) => setError(readBizError(e).message));
  };
  const summary =
    item.historyKind == null
      ? '暂无'
      : `${item.historyKind === 'DEAL' ? '最近成交' : '最近询价'} ${formatAmount(item.historyPrice ?? 0)}（${item.historyDate}）`;
  const label = (t: string) => (
    <div style={{ fontSize: 12, color: palette.mute, margin: '8px 0 4px' }}>
      {t}
    </div>
  );
  const content = error ? (
    <span style={{ color: palette.red }}>{error}</span>
  ) : !detail ? (
    <Spin size="small" />
  ) : (
    <div style={{ width: 360, fontSize: 13 }}>
      {label('最近成交（采购订单，不含税）')}
      {detail.deals.length === 0 ? (
        <span style={{ color: palette.mute }}>暂无</span>
      ) : (
        detail.deals.map((d) => (
          <div
            key={`${d.poNo}-${d.date}-${d.price}`}
            style={{ display: 'flex', gap: 10 }}
          >
            <b style={{ width: 90 }}>{formatAmount(d.price)}</b>
            <span>{d.date}</span>
            <span style={{ color: palette.sub }}>
              {[d.poNo, d.supplierName].filter(Boolean).join(' · ')}
            </span>
          </div>
        ))
      )}
      {label('最近询价（不含本任务）')}
      {detail.quotes.length === 0 ? (
        <span style={{ color: palette.mute }}>暂无</span>
      ) : (
        detail.quotes.map((q, i) => (
          <div
            // biome-ignore lint/suspicious/noArrayIndexKey: 询价记录只读展示，顺序固定
            key={i}
            style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}
          >
            <b style={{ width: 90 }}>{formatAmount(q.price)}</b>
            <span>{q.date}</span>
            <span style={{ color: palette.sub }}>
              {[
                q.channelName,
                q.shopName,
                q.conditionName,
                q.leadTimeName,
                q.freeShipping ? '' : '不包邮',
              ]
                .filter(Boolean)
                .join(' · ')}
            </span>
          </div>
        ))
      )}
    </div>
  );
  return (
    <div style={{ fontSize: 12, marginTop: 4, color: palette.sub }}>
      历史询价：
      {item.historyKind == null ? (
        <span style={{ color: palette.mute }}>{summary}</span>
      ) : (
        <Popover
          trigger="click"
          title={`${item.model} 历史询价`}
          content={content}
          onOpenChange={(open) => open && load()}
        >
          <a>{summary}</a>
        </Popover>
      )}
    </div>
  );
};

export default ItemHistory;
