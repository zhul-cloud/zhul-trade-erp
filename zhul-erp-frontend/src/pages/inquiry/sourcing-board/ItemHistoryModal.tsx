import { HistoryOutlined } from '@ant-design/icons';
import { Modal, Skeleton, Timeline } from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import {
  ConditionPill,
  formatCny,
  LeadTimeText,
  Pill,
  SupplierText,
  TaxHint,
} from '../shared/components';
import {
  boardApi,
  type ItemHistoryEntry,
  readBizError,
} from '../shared/service';

/** 型号的修改记录：各采购每次回价的版本、采购负责人调整成本价，按时间倒序 */
const ItemHistoryModal: React.FC<{
  item?: { id: number; model: string };
  onClose: () => void;
}> = ({ item, onClose }) => {
  const { palette } = useAppTheme();
  const [entries, setEntries] = useState<ItemHistoryEntry[] | null>(null);
  const [error, setError] = useState('');

  const load = async (id: number) => {
    setEntries(null);
    setError('');
    try {
      setEntries(await boardApi.itemHistory(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  };

  useEffect(() => {
    if (item) load(item.id);
  }, [item?.id]);

  const head = (e: ItemHistoryEntry) => (
    <span
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        flexWrap: 'wrap',
      }}
    >
      <b style={{ color: palette.ink }}>{e.action}</b>
      <span style={{ color: palette.sub }}>{e.operatorName || '—'}</span>
      <span style={{ color: palette.mute, fontSize: 12 }}>
        {dayjs(e.time).format('YYYY-MM-DD HH:mm:ss')}
      </span>
      {e.type === 'QUOTE' &&
        (e.current ? (
          <Pill tone="green">当前有效</Pill>
        ) : (
          <Pill tone="gray">已被替换</Pill>
        ))}
    </span>
  );

  return (
    <Modal
      open={!!item}
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <HistoryOutlined /> 修改记录 · {item?.model}
        </span>
      }
      footer={null}
      width={720}
      onCancel={onClose}
      destroyOnHidden
    >
      {error ? (
        <ErrorHint message={error} onRetry={() => item && load(item.id)} />
      ) : !entries ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : entries.length === 0 ? (
        <div
          style={{
            color: palette.mute,
            padding: '24px 0',
            textAlign: 'center',
          }}
        >
          还没有回价记录
        </div>
      ) : (
        <Timeline
          style={{ marginTop: 16 }}
          items={entries.map((e) => ({
            color: e.type === 'COST' ? 'orange' : e.current ? 'green' : 'gray',
            content: (
              <div style={{ display: 'grid', gap: 6 }}>
                {head(e)}
                {e.type === 'COST' ? (
                  <span style={{ color: palette.sub, fontSize: 13 }}>
                    {e.note}
                  </span>
                ) : (
                  (e.quotes ?? []).map((q) => (
                    <span
                      key={q.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        flexWrap: 'wrap',
                        fontSize: 13,
                        color: e.current ? palette.ink : palette.mute,
                        textDecoration: e.current ? undefined : 'line-through',
                      }}
                    >
                      {q.noStock ? (
                        <span>无货{q.note ? ` · ${q.note}` : ''}</span>
                      ) : (
                        <>
                          <b style={{ minWidth: 80 }}>
                            {formatCny(q.unitPriceCny)}
                          </b>
                          <TaxHint
                            taxIncluded={q.taxIncluded}
                            taxRate={q.taxRate}
                            unitPrice={q.unitPrice}
                          />
                          <ConditionPill value={q.itemCondition} />
                          <LeadTimeText value={q.leadTime} />
                          <SupplierText
                            channel={q.channel}
                            shopName={q.shopName}
                          />
                          {q.recommended && <Pill tone="green">推荐</Pill>}
                        </>
                      )}
                    </span>
                  ))
                )}
              </div>
            ),
          }))}
        />
      )}
    </Modal>
  );
};

export default ItemHistoryModal;
