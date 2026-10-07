import {
  CheckCircleFilled,
  InfoCircleOutlined,
  SearchOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Empty,
  Input,
  InputNumber,
  Modal,
  Skeleton,
} from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { Pill } from '../components';
import {
  type CandidateCustomer,
  type CandidateQuotation,
  type Pi,
  piApi,
  readBizError,
} from '../service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 选中的报价行 → 数量 */
type Picked = Map<number, number>;

const QuotationLines: React.FC<{
  q: CandidateQuotation;
  picked: Picked;
  onChange: (next: Picked) => void;
}> = ({ q, picked, onChange }) => {
  const { palette } = useAppTheme();
  const all =
    q.items.length > 0 && q.items.every((i) => picked.has(i.quotationItemId));
  const some = q.items.some((i) => picked.has(i.quotationItemId));
  const toggleAll = (checked: boolean) => {
    const next = new Map(picked);
    for (const i of q.items) {
      if (checked)
        next.set(i.quotationItemId, next.get(i.quotationItemId) ?? i.quantity);
      else next.delete(i.quotationItemId);
    }
    onChange(next);
  };
  return (
    <div style={{ display: 'grid', gap: 6 }}>
      <Checkbox
        checked={all}
        indeterminate={!all && some}
        onChange={(e) => toggleAll(e.target.checked)}
        style={{ fontSize: 12, color: palette.sub }}
      >
        全选
      </Checkbox>
      {q.items.map((i) => {
        const checked = picked.has(i.quotationItemId);
        return (
          <div
            key={i.quotationItemId}
            style={{
              display: 'grid',
              gridTemplateColumns: '24px minmax(0, 1fr) 96px 110px 120px',
              gap: 10,
              alignItems: 'center',
              padding: '8px 10px',
              borderRadius: 10,
              background: checked ? palette.accentSoft : palette.inset,
              border: `1px solid ${checked ? palette.accentLine : palette.hairline}`,
            }}
          >
            <Checkbox
              checked={checked}
              aria-label={`选择 ${i.model}`}
              onChange={(e) => {
                const next = new Map(picked);
                if (e.target.checked) next.set(i.quotationItemId, i.quantity);
                else next.delete(i.quotationItemId);
                onChange(next);
              }}
            />
            <div style={{ minWidth: 0 }}>
              <div style={{ fontWeight: 600, color: palette.ink }}>
                {i.model}
                {i.won && (
                  <span style={{ marginLeft: 8 }}>
                    <Pill tone="green">已成交</Pill>
                  </span>
                )}
                {i.inPiNo && (
                  <span style={{ marginLeft: 8 }}>
                    <Pill tone="orange">已在 {i.inPiNo} 中</Pill>
                  </span>
                )}
              </div>
              <div style={{ fontSize: 12, color: palette.mute }}>
                {[i.brand, i.category].filter(Boolean).join(' · ')}
              </div>
            </div>
            <InputNumber
              size="small"
              min={1}
              precision={0}
              disabled={!checked}
              value={checked ? picked.get(i.quotationItemId) : i.quantity}
              onChange={(v) => {
                const next = new Map(picked);
                next.set(i.quotationItemId, v ?? 1);
                onChange(next);
              }}
              aria-label="数量"
              style={{ width: '100%' }}
            />
            <span style={{ ...num, textAlign: 'right', color: palette.sub }}>
              {formatAmount(i.unitPrice, q.currencyCode)}
            </span>
            <b style={{ ...num, textAlign: 'right' }}>
              {formatAmount(
                i.unitPrice * (picked.get(i.quotationItemId) ?? i.quantity),
                q.currencyCode,
              )}
            </b>
          </div>
        );
      })}
    </div>
  );
};

/**
 * 新建 PI：传 quotationId 时按这张报价单开（带入全部型号，可取消勾选、改数量）；
 * 不传时先选客户，再从该客户已发送 / 部分成交的报价单里挑型号（同一币种）。
 */
const NewPiModal: React.FC<{
  open: boolean;
  quotationId?: number;
  onClose: () => void;
  onCreated: (pi: Pi) => void;
}> = ({ open, quotationId, onClose, onCreated }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [customers, setCustomers] = useState<CandidateCustomer[]>();
  const [keyword, setKeyword] = useState('');
  const [customer, setCustomer] = useState<CandidateCustomer>();
  const [quotations, setQuotations] = useState<CandidateQuotation[]>();
  const [picked, setPicked] = useState<Picked>(new Map());
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setError(undefined);
    setPicked(new Map());
    setQuotations(undefined);
    setCustomer(undefined);
    if (quotationId) {
      piApi
        .candidateQuotation(quotationId)
        .then((q) => {
          setQuotations([q]);
          // 默认带入全部未成交的型号
          setPicked(
            new Map(
              q.items
                .filter((i) => !i.won)
                .map((i) => [i.quotationItemId, i.quantity]),
            ),
          );
        })
        .catch((e) => setError(readBizError(e).message));
    } else {
      setCustomers(undefined);
      piApi
        .candidateCustomers()
        .then(setCustomers)
        .catch((e) => setError(readBizError(e).message));
    }
  }, [open, quotationId]);

  const pickCustomer = (c: CandidateCustomer) => {
    setCustomer(c);
    setQuotations(undefined);
    setPicked(new Map());
    piApi
      .candidateQuotations(c.customerId)
      .then(setQuotations)
      .catch((e) => setError(readBizError(e).message));
  };

  const currencies = useMemo(() => {
    const set = new Set<string>();
    for (const q of quotations ?? []) {
      if (q.items.some((i) => picked.has(i.quotationItemId)))
        set.add(q.currencyCode);
    }
    return [...set];
  }, [quotations, picked]);

  const pickedTotal = useMemo(() => {
    let t = 0;
    for (const q of quotations ?? []) {
      for (const i of q.items) {
        const qty = picked.get(i.quotationItemId);
        if (qty) t += i.unitPrice * qty;
      }
    }
    return t;
  }, [quotations, picked]);

  const create = async () => {
    setBusy(true);
    try {
      const pi = await piApi.create(
        [...picked.entries()].map(([quotationItemId, quantity]) => ({
          quotationItemId,
          quantity,
        })),
      );
      message.success(`已生成草稿 PI ${pi.piNo}`);
      onCreated(pi);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const mixed = currencies.length > 1;
  const single = quotationId ? quotations?.[0] : undefined;
  const shownCustomers = (customers ?? []).filter(
    (c) =>
      !keyword.trim() ||
      c.customerName?.toLowerCase().includes(keyword.trim().toLowerCase()),
  );

  const quotationBlock = (q: CandidateQuotation) => (
    <div
      key={q.quotationId}
      style={{
        padding: 14,
        borderRadius: 12,
        border: `1px solid ${palette.hairline}`,
        background: palette.card,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 10,
        }}
      >
        <b style={{ color: palette.ink }}>{q.quotationNo}</b>
        <Pill tone={q.status === 6 ? 'green' : 'accent'} dot>
          {q.statusName}
        </Pill>
        <span style={{ fontSize: 12, color: palette.mute }}>
          {q.sentAt ? `${formatDateTime(q.sentAt).slice(0, 16)} 发送` : ''}
        </span>
        <span style={{ ...num, marginLeft: 'auto', color: palette.sub }}>
          {formatAmount(q.totalAmount, q.currencyCode)}
        </span>
      </div>
      <QuotationLines q={q} picked={picked} onChange={setPicked} />
      {quotationId && q.fees.length > 0 && (
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
          <InfoCircleOutlined /> 一并带入费用：
          {q.fees
            .map(
              (f) => `${f.feeName} ${formatAmount(f.amount, q.currencyCode)}`,
            )
            .join('、')}
        </div>
      )}
    </div>
  );

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={quotationId ? 760 : 960}
      title={
        quotationId
          ? `按报价单开 PI${single ? ` · ${single.quotationNo}` : ''}`
          : '新建 PI · 从报价单挑型号'
      }
      footer={[
        <span
          key="sum"
          style={{ float: 'left', lineHeight: '32px', color: palette.sub }}
        >
          已选 {picked.size} 个型号
          {picked.size > 0 && !mixed && currencies[0] && (
            <b style={{ ...num, marginLeft: 8, color: palette.ink }}>
              {formatAmount(pickedTotal, currencies[0])}
            </b>
          )}
        </span>,
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="ok"
          type="primary"
          loading={busy}
          disabled={picked.size === 0 || mixed}
          onClick={create}
        >
          生成草稿 PI
        </Button>,
      ]}
    >
      {error ? (
        <Alert type="error" showIcon title={error} />
      ) : quotationId ? (
        !single ? (
          <Skeleton active paragraph={{ rows: 6 }} />
        ) : (
          <>
            <div style={{ fontSize: 13, color: palette.sub, marginBottom: 12 }}>
              取消勾选客户不要的型号、按客户确认的数量修改；单价默认取报价单售价，生成后在
              PI 上还能调整。
            </div>
            {quotationBlock(single)}
          </>
        )
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '280px minmax(0, 1fr)',
            gap: 16,
            minHeight: 420,
          }}
        >
          <div style={{ display: 'grid', gap: 8, alignContent: 'start' }}>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索客户"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <div style={{ fontSize: 12, color: palette.mute }}>
              只列出有「已发送 / 部分成交」报价单的客户
            </div>
            <div
              style={{
                display: 'grid',
                gap: 6,
                maxHeight: 460,
                overflow: 'auto',
              }}
            >
              {!customers ? (
                <Skeleton active paragraph={{ rows: 5 }} />
              ) : shownCustomers.length === 0 ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="没有可开 PI 的报价单"
                />
              ) : (
                shownCustomers.map((c) => {
                  const active = customer?.customerId === c.customerId;
                  return (
                    <button
                      type="button"
                      key={c.customerId}
                      onClick={() => pickCustomer(c)}
                      style={{
                        all: 'unset',
                        cursor: 'pointer',
                        padding: '10px 12px',
                        borderRadius: 10,
                        background: active ? palette.accentSoft : palette.inset,
                        border: `1px solid ${active ? palette.accentLine : palette.hairline}`,
                      }}
                    >
                      <div
                        style={{
                          fontWeight: 600,
                          color: palette.ink,
                          display: 'flex',
                          gap: 6,
                        }}
                      >
                        {c.customerName}
                        {active && (
                          <CheckCircleFilled style={{ color: palette.link }} />
                        )}
                      </div>
                      <div style={{ fontSize: 12, color: palette.mute }}>
                        {[c.country, `${c.quotationCount} 张报价单`]
                          .filter(Boolean)
                          .join(' · ')}
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>
          <div
            style={{
              display: 'grid',
              gap: 12,
              alignContent: 'start',
              maxHeight: 520,
              overflow: 'auto',
            }}
          >
            {mixed && (
              <Alert
                type="warning"
                showIcon
                icon={<WarningOutlined />}
                title={`所选报价单币种不同（${currencies.join('、')}），请分开开 PI`}
              />
            )}
            {!customer ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="先在左边选一个客户"
              />
            ) : !quotations ? (
              <Skeleton active paragraph={{ rows: 8 }} />
            ) : (
              <>
                <div style={{ fontSize: 13, color: palette.sub }}>
                  {customer.customerName}{' '}
                  的报价单（按发送时间从新到旧），可以跨多张报价单挑型号；没有费用行时默认加
                  Shipping Cost、Bank Charge 两行。
                </div>
                {quotations.map(quotationBlock)}
              </>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
};

export default NewPiModal;
