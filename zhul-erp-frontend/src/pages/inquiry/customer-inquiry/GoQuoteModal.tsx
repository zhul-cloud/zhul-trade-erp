import { Checkbox, Modal } from 'antd';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { Pill } from '../shared/components';
import { ITEM_NO_STOCK, ITEM_PENDING } from '../shared/constants';
import type { InquiryItem } from '../shared/service';

/**
 * 询盘详情「去报价」：有型号还在询价时先选型号。
 * 已回价（含无货）的默认勾选；还在询价的也能勾（默认不勾），带入后按无货报，询价任务不受影响。
 */
const GoQuoteModal: React.FC<{
  open: boolean;
  title: string;
  items: InquiryItem[];
  busy: boolean;
  onCancel: () => void;
  onOk: (itemIds: number[]) => void;
}> = ({ open, title, items, busy, onCancel, onOk }) => {
  const { palette } = useAppTheme();
  const [picked, setPicked] = useState<Set<number>>(new Set());

  useEffect(() => {
    if (open) {
      setPicked(
        new Set(
          items.filter((i) => i.quoteStatus !== ITEM_PENDING).map((i) => i.id),
        ),
      );
    }
  }, [open, items]);

  const pending = items.filter((i) => i.quoteStatus === ITEM_PENDING).length;
  const toggle = (id: number) => {
    const next = new Set(picked);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setPicked(next);
  };

  return (
    <Modal
      open={open}
      title={title}
      okText={`带入 ${picked.size} 个型号`}
      okButtonProps={{ disabled: picked.size === 0, loading: busy }}
      onCancel={onCancel}
      onOk={() => onOk(items.filter((i) => picked.has(i.id)).map((i) => i.id))}
      destroyOnHidden
      width={600}
    >
      <div style={{ color: palette.sub, marginBottom: 12 }}>
        已回价 {items.length - pending} / 共 {items.length} 个型号 · {pending}{' '}
        个还在询价
      </div>
      <div
        style={{
          border: `1px solid ${palette.hairline}`,
          borderRadius: 12,
          maxHeight: 420,
          overflowY: 'auto',
        }}
      >
        {items.map((i, idx) => {
          const isPending = i.quoteStatus === ITEM_PENDING;
          const cost = i.selectedQuote?.unitPriceCny;
          return (
            // biome-ignore lint/a11y/noLabelWithoutControl: 内含 antd Checkbox（渲染为 input），点整行切换勾选
            <label
              key={i.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '10px 14px',
                cursor: 'pointer',
                borderTop: idx ? `1px solid ${palette.hairline}` : undefined,
                background: picked.has(i.id) ? palette.accentSoft : undefined,
              }}
            >
              <Checkbox
                checked={picked.has(i.id)}
                onChange={() => toggle(i.id)}
              />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, color: palette.ink }}>
                  {i.confirmedModel || i.originalModel}
                </div>
                <div style={{ fontSize: 12, color: palette.mute }}>
                  {[i.brand, `数量 ${i.quantity}`].filter(Boolean).join(' · ')}
                </div>
              </div>
              <span style={{ fontSize: 12, textAlign: 'right' }}>
                {isPending ? (
                  <Pill tone="orange">未回价 · 按无货报</Pill>
                ) : cost != null ? (
                  <span style={{ color: palette.sub }}>
                    采购成本价 {formatAmount(cost, 'CNY')}
                  </span>
                ) : i.quoteStatus === ITEM_NO_STOCK ? (
                  <Pill tone="gray">无货</Pill>
                ) : null}
              </span>
            </label>
          );
        })}
      </div>
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 10 }}>
        勾了未回价的型号：采购那边的询价任务照常进行，之后回了价可以「修改（出新版本）」改成正常报价。
      </div>
    </Modal>
  );
};

export default GoQuoteModal;
