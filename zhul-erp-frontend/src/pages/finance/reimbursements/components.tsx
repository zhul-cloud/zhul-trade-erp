import { Link } from '@umijs/max';
import { App, DatePicker, Input, Modal, Segmented, Select, Space } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';
import {
  type BankOption,
  bankOptionsApi,
  readBizError,
} from '@/pages/sales/service';
import { AttachmentWall } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { ACCEPT_PROOF } from '../payables/components';
import {
  type AdvanceItem,
  BX_STATUS,
  ITEM,
  ITEM_NAMES,
  type Reimbursement,
  reimburseApi,
} from './service';

export const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
export const ITEM_TONE: Record<number, Tone> = {
  1: 'accent',
  2: 'violet',
  3: 'orange',
  4: 'cyan',
  5: 'green',
};
export const STATUS_META: Record<number, { label: string; tone: Tone }> = {
  [BX_STATUS.PENDING]: { label: '待付款', tone: 'orange' },
  [BX_STATUS.PAID]: { label: '已报销', tone: 'green' },
  [BX_STATUS.WITHDRAWN]: { label: '已撤回', tone: 'mute' },
};
export const keyOf = (i: AdvanceItem) => `${i.sourceType}:${i.sourceId}`;

/** 来源单号：单据类型标签 + 单号 */
export const sourceCell = (_: unknown, i: AdvanceItem) => (
  <span style={{ whiteSpace: 'nowrap' }}>
    <Pill tone={ITEM_TONE[i.sourceType]}>{ITEM_NAMES[i.sourceType]}</Pill>{' '}
    {(i.sourceType === ITEM.PAYMENT || i.sourceType === ITEM.REFUND) &&
    i.sourceNo ? (
      <Link to={`/finance/payments/${i.sourceId}`}>{i.sourceNo}</Link>
    ) : i.sourceType === ITEM.EXPENSE && i.sourceNo ? (
      <Link to={`/finance/expenses/${i.sourceId}`}>{i.sourceNo}</Link>
    ) : i.sourceType === ITEM.RETURN_FREIGHT && i.sourceNo ? (
      <Link to={`/purchase/returns/${i.sourceId}`}>{i.sourceNo}</Link>
    ) : (
      i.sourceNo || '—'
    )}
  </span>
);
export const StatusPill: React.FC<{ status: number }> = ({ status }) => {
  const m = STATUS_META[status];
  return m ? <Pill tone={m.tone}>{m.label}</Pill> : null;
};

/** 财务登记报销已付 */
export const PayModal: React.FC<{
  /** 一张为单张登记，多张为批量登记 */
  targets?: Reimbursement[];
  onClose: () => void;
  onDone: () => void;
}> = ({ targets, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const target = targets?.length ? targets : undefined;
  const batch = (target?.length ?? 0) > 1;
  const sum = (target ?? []).reduce((s, r) => s + r.totalAmount, 0);
  // 按报销人分组，组内按编号
  const groups = useMemo(() => {
    const m = new Map<string, Reimbursement[]>();
    for (const r of target ?? []) {
      const k = r.applicantName;
      m.set(k, [...(m.get(k) ?? []), r]);
    }
    return [...m.entries()];
  }, [target]);
  const [accounts, setAccounts] = useState<BankOption[]>([]);
  const [accountId, setAccountId] = useState<number>();
  /** 1-公司对公、3-私对私（法人等个人账户付给员工） */
  const [method, setMethod] = useState(1);
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [files, setFiles] = useState<Attachment[]>([]);
  const [remark, setRemark] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (!target) return;
    setMethod(1);
    setDate(dayjs());
    setFiles([]);
    setRemark('');
    bankOptionsApi
      .options()
      .then((list) => {
        const ok = list.filter((a) => a.enabled && a.currencyCode === 'CNY');
        setAccounts(ok);
        const pub = ok.filter((a) => a.accountType !== 2);
        setAccountId((pub.find((a) => a.isDefault) ?? pub[0])?.id);
      })
      .catch(() => setAccounts([]));
  }, [target]);
  const submit = async () => {
    if (!target || files.length === 0) {
      message.error('请上传转账截图');
      return;
    }
    if (!accountId) {
      message.error('请选择付款账户');
      return;
    }
    setSaving(true);
    try {
      const body = {
        method,
        bankAccountId: accountId,
        payDate: date.format('YYYY-MM-DD'),
        attachmentIds: files.map((f) => f.id),
        remark: remark.trim() || undefined,
      };
      if (batch) {
        await reimburseApi.batchPay({
          ...body,
          ids: target.map((r) => r.id),
        });
        message.success(`已登记 ${target.length} 张报销单已付`);
      } else {
        await reimburseApi.pay(target[0].id, body);
        message.success('已登记已付');
      }
      onDone();
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      open={!!target}
      title={
        batch
          ? `批量登记已付 · ${target?.length} 张 · ${formatAmount(sum, 'CNY')}`
          : `登记已付 · ${target?.[0].reimburseNo ?? ''} · ${formatAmount(target?.[0].totalAmount, 'CNY')}`
      }
      okText={batch ? `确认已付（${target?.length} 张）` : '确认已付'}
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <Space orientation="vertical" size={12} style={{ width: '100%' }}>
        {batch && (
          <div
            style={{
              padding: '6px 14px',
              borderRadius: 10,
              background: palette.inset,
              maxHeight: 220,
              overflowY: 'auto',
            }}
          >
            {groups.map(([who, list]) => (
              <div key={who} style={{ padding: '8px 0' }}>
                <div
                  style={{ display: 'flex', justifyContent: 'space-between' }}
                >
                  <b>
                    {who} · {list.length} 张
                  </b>
                  <b style={num}>
                    {formatAmount(
                      list.reduce((s, r) => s + r.totalAmount, 0),
                      'CNY',
                    )}
                  </b>
                </div>
                {list.map((r) => (
                  <div
                    key={r.id}
                    style={{ ...num, fontSize: 12, color: palette.sub }}
                  >
                    {r.reimburseNo} {formatAmount(r.totalAmount, 'CNY')}
                  </div>
                ))}
              </div>
            ))}
          </div>
        )}
        <Segmented<number>
          value={method}
          onChange={(m) => {
            setMethod(m);
            // 公司对公选对公账户，私对私选对私账户
            const list = accounts.filter(
              (a) => (a.accountType === 2) === (m === 3),
            );
            setAccountId((list.find((a) => a.isDefault) ?? list[0])?.id);
          }}
          options={[
            { label: '公司对公', value: 1 },
            { label: '私对私', value: 3 },
          ]}
        />
        <Select
          aria-label="付款账户"
          style={{ width: '100%' }}
          value={accountId}
          onChange={setAccountId}
          placeholder={method === 3 ? '选择对私账户' : '选择对公账户'}
          notFoundContent={`没有${method === 3 ? '对私' : '对公'}的人民币账户，请先在「收款账户」里添加`}
          options={accounts
            .filter((a) => (a.accountType === 2) === (method === 3))
            .map((a) => ({
              value: a.id,
              label: `${a.bankName} · ${a.accountName} ${a.accountNoMasked}`,
            }))}
        />
        <DatePicker
          value={date}
          onChange={(d) => d && setDate(d)}
          disabledDate={(d) => d.isAfter(dayjs(), 'day')}
          allowClear={false}
        />
        <AttachmentWall
          ownerType="REIMBURSE"
          value={files}
          onChange={setFiles}
          max={10}
          accept={ACCEPT_PROOF}
        />
        {batch && (
          <div style={{ fontSize: 12, color: palette.mute }}>
            截图会挂到每张报销单上；确认后每张报销单各生成一张报销付款单
          </div>
        )}
        <Input
          placeholder="说明（选填）"
          value={remark}
          onChange={(e) => setRemark(e.target.value)}
          maxLength={500}
        />
      </Space>
    </Modal>
  );
};
