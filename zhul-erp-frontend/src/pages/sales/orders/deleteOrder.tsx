import { CheckCircleOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { App, Button, Input, Modal, Skeleton } from 'antd';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  type Order,
  type OrderDeleteCheck,
  type OrderDeleteDoc,
  orderApi,
  readBizError,
} from '../service';

const SOURCE_NAMES: Record<number, string> = {
  2: '手动创建',
  3: '售后补发',
  4: '费用订单',
};

/** 关联单据清单：挡住删除的（红）或删除时一起处理的（橙） */
const DocList: React.FC<{
  title: string;
  tone: 'red' | 'orange';
  docs: OrderDeleteDoc[];
  onOpen: (path: string) => void;
}> = ({ title, tone, docs, onOpen }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        background: tone === 'red' ? palette.redSoft : palette.orangeSoft,
        borderRadius: 10,
        padding: '12px 14px',
        display: 'grid',
        gap: 8,
      }}
    >
      <b style={{ fontSize: 13, color: palette[tone] }}>{title}</b>
      {docs.map((d) => (
        <div
          key={`${d.kind}-${d.no}`}
          style={{ display: 'flex', gap: 12, alignItems: 'baseline' }}
        >
          <span style={{ width: 72, flex: 'none', color: palette.mute }}>
            {d.kind}
          </span>
          {d.path ? (
            <button
              type="button"
              className="zhul-link"
              onClick={() => onOpen(d.path as string)}
            >
              {d.no}
            </button>
          ) : (
            <span style={{ color: palette.ink }}>{d.no}</span>
          )}
          <span
            style={{ marginLeft: 'auto', color: palette.sub, flex: 'none' }}
          >
            {d.note}
          </span>
        </div>
      ))}
    </div>
  );
};

/** 删除销售订单：先查关联单据，按三档显示（spec sales/sales-order「删除销售订单」） */
export const DeleteOrderModal: React.FC<{
  order: Order;
  open: boolean;
  onClose: () => void;
  onDeleted: () => void;
}> = ({ order, open, onClose, onDeleted }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [check, setCheck] = useState<OrderDeleteCheck>();
  const [error, setError] = useState<string>();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setCheck(undefined);
    setError(undefined);
    setReason('');
    orderApi
      .deleteCheck(order.id)
      .then(setCheck)
      .catch((e) => setError(readBizError(e).message));
  }, [open, order.id]);

  const blocked = check?.level === 'BLOCKED';
  const openDoc = (path: string) => {
    onClose();
    history.push(path);
  };
  const submit = async () => {
    setBusy(true);
    try {
      await orderApi.remove(order.id, reason.trim());
      message.success('订单已删除');
      onDeleted();
    } catch (e) {
      const err = readBizError(e);
      if (err.errorCode === 'ORDER_DELETE_BLOCKED' && err.detail) {
        // 打开弹窗后有人登记了到账等，按最新结果显示
        setCheck(err.detail as unknown as OrderDeleteCheck);
      }
      message.error(err.message);
    } finally {
      setBusy(false);
    }
  };

  const origin =
    order.source === 1 ? `由 ${order.piNo} 转成` : SOURCE_NAMES[order.source];
  const summary = [
    origin,
    order.customerName,
    formatAmount(order.totalAmount, order.currencyCode),
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={560}
      title={
        blocked ? `订单 ${order.soNo} 不能删除` : `删除订单 ${order.soNo}？`
      }
      footer={
        blocked || error
          ? [
              <Button key="ok" type="primary" onClick={onClose}>
                知道了
              </Button>,
            ]
          : [
              <Button key="cancel" onClick={onClose}>
                取消
              </Button>,
              <Button
                key="delete"
                danger
                type="primary"
                loading={busy}
                disabled={!check || !reason.trim()}
                onClick={submit}
              >
                删除
              </Button>,
            ]
      }
    >
      <div style={{ display: 'grid', gap: 14 }}>
        <div style={{ color: palette.mute, fontSize: 13 }}>
          {blocked
            ? `先处理下面的单据，再回来删除${order.status === 1 ? '；或者改用「取消订单」' : ''}`
            : summary}
        </div>
        {error && <div style={{ color: palette.red }}>{error}</div>}
        {!check && !error && <Skeleton active paragraph={{ rows: 2 }} />}
        {check?.level === 'DIRECT' && (
          <div
            style={{
              display: 'flex',
              gap: 10,
              alignItems: 'center',
              background: palette.inset,
              borderRadius: 10,
              padding: '12px 14px',
              color: palette.sub,
            }}
          >
            <CheckCircleOutlined style={{ color: palette.green }} />
            没有关联单据，删除后不能恢复
          </div>
        )}
        {check?.level === 'CONFIRM' && (
          <DocList
            title="删除后这些单据会一起处理"
            tone="orange"
            docs={check.related}
            onOpen={openDoc}
          />
        )}
        {blocked && (
          <DocList
            title="挡住删除的单据"
            tone="red"
            docs={check.blockers}
            onOpen={openDoc}
          />
        )}
        {check && !blocked && (
          <div style={{ display: 'grid', gap: 6 }}>
            <span style={{ fontSize: 13, color: palette.mute }}>
              删除原因（必填）
            </span>
            <Input.TextArea
              rows={2}
              maxLength={200}
              showCount
              value={reason}
              placeholder="例如：录错客户、测试订单"
              onChange={(e) => setReason(e.target.value)}
              aria-label="删除原因"
            />
          </div>
        )}
      </div>
    </Modal>
  );
};
