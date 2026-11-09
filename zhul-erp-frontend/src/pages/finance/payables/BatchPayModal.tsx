import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  DatePicker,
  InputNumber,
  Modal,
  Segmented,
  Select,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import {
  type BankOption,
  bankOptionsApi,
  readBizError,
} from '@/pages/sales/service';
import { AttachmentWall } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { currencyTotals, type Group, groupPayables } from './batch';
import { ACCEPT_PROOF, Field, newKey, PayeeAccountPicker } from './components';
import { METHOD, type Payable, type PayeeAccount, paymentApi } from './service';

interface GroupInput {
  bankAccountId?: number;
  payeeAccountId?: number;
  payeeAccounts: PayeeAccount[];
  amount: number | null;
  amountCny: number | null;
  files: Attachment[];
}

/** 这一组还差什么；都填好返回空串 */
const missing = (g: Group, v: GroupInput) => {
  if (!v.bankAccountId) return '还差我方付款账户';
  if (!v.amount || v.amount <= 0) return '还差付款金额';
  if (v.amount > g.unpaid) return '付款金额超过未付合计';
  if (g.currency !== 'CNY' && !v.amountCny) return '还差实付人民币';
  if (v.files.length === 0) return '还差付款截图';
  return '';
};

/** 批量付款（spec finance/supplier-payment「批量付款」）：按收款方 + 币种分组，每组一笔付款 */
export const BatchPayModal: React.FC<{
  rows: Payable[];
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ rows, open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const groups = useMemo(() => groupPayables(rows), [rows]);
  const [payDate, setPayDate] = useState<Dayjs>(dayjs());
  const [method, setMethod] = useState<number>(METHOD.COMPANY);
  const [accounts, setAccounts] = useState<BankOption[]>([]);
  const [inputs, setInputs] = useState<Record<string, GroupInput>>({});
  const [key, setKey] = useState(newKey);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setKey(newKey());
    setPayDate(dayjs());
    setMethod(METHOD.COMPANY);
    setInputs(
      Object.fromEntries(
        groups.map((g) => [
          g.key,
          {
            payeeAccounts: [],
            amount: g.unpaid,
            amountCny: null,
            files: [],
          },
        ]),
      ),
    );
    bankOptionsApi
      .options()
      .then((list) => setAccounts(list.filter((a) => a.enabled)))
      .catch(() => setAccounts([]));
    for (const g of groups) {
      paymentApi
        .payeeAccounts(g.rows[0].id)
        .then((list) =>
          setInputs((cur) => ({
            ...cur,
            [g.key]: { ...cur[g.key], payeeAccounts: list },
          })),
        )
        .catch(() => undefined);
    }
  }, [open, groups]);

  // 我方账户按币种与付款方式（对公 / 对私）筛选；没选或不在范围内时选第一个
  const accountsOf = (currency: string) =>
    accounts.filter(
      (a) =>
        a.currencyCode === currency &&
        (a.accountType === 2) === (method === METHOD.PRIVATE),
    );
  useEffect(() => {
    setInputs((cur) => {
      const next = { ...cur };
      for (const g of groups) {
        const v = next[g.key];
        if (!v) continue;
        const list = accounts.filter(
          (a) =>
            a.currencyCode === g.currency &&
            (a.accountType === 2) === (method === METHOD.PRIVATE),
        );
        if (!list.some((a) => a.id === v.bankAccountId)) {
          next[g.key] = {
            ...v,
            bankAccountId: (list.find((a) => a.isDefault) ?? list[0])?.id,
          };
        }
      }
      return next;
    });
  }, [accounts, method, groups]);

  const patch = (k: string, p: Partial<GroupInput>) =>
    setInputs((cur) => ({ ...cur, [k]: { ...cur[k], ...p } }));

  const problems = groups.map((g) =>
    inputs[g.key] ? missing(g, inputs[g.key]) : '加载中',
  );

  const submit = async () => {
    const bad = groups.findIndex((_, i) => problems[i]);
    if (bad >= 0) {
      message.warning(`${groups[bad].payeeName}：${problems[bad]}`);
      return;
    }
    setSaving(true);
    try {
      const done = await paymentApi.batch({
        method,
        payDate: payDate.format('YYYY-MM-DD'),
        idempotencyKey: key,
        groups: groups.map((g) => {
          const v = inputs[g.key];
          return {
            bankAccountId: v.bankAccountId as number,
            payeeAccountId: v.payeeAccountId,
            amount: v.amount as number,
            amountCny:
              g.currency === 'CNY' ? undefined : (v.amountCny ?? undefined),
            attachmentIds: v.files.map((f) => f.id),
            payableIds: g.rows.map((r) => r.id),
          };
        }),
      });
      message.success(`已登记 ${done.length} 笔付款`);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={900}
      destroyOnHidden
      title="批量付款"
      footer={[
        <Button key="c" onClick={onClose}>
          取消
        </Button>,
        <Button key="ok" type="primary" loading={saving} onClick={submit}>
          确认付款（{groups.length} 笔）
        </Button>,
      ]}
    >
      <div style={{ display: 'grid', gap: 16 }}>
        <div style={{ display: 'flex', gap: 16, alignItems: 'flex-end' }}>
          <Field label="付款日期" required width={200}>
            <DatePicker
              value={payDate}
              allowClear={false}
              style={{ width: '100%' }}
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
              onChange={(d) => d && setPayDate(d)}
            />
          </Field>
          <Field label="付款方式" required>
            <Segmented<number>
              value={method}
              onChange={setMethod}
              options={[
                { value: METHOD.COMPANY, label: '公司对公' },
                { value: METHOD.PRIVATE, label: '私对私' },
              ]}
            />
          </Field>
          <div style={{ flex: 1, textAlign: 'right' }}>
            <div style={{ fontSize: 12, color: palette.mute }}>
              共 {groups.length} 笔付款
            </div>
            <b style={{ fontSize: 14 }}>{currencyTotals(rows)}</b>
          </div>
        </div>

        {groups.map((g, i) => {
          const v = inputs[g.key];
          if (!v) return null;
          const problem = problems[i];
          const own = accountsOf(g.currency);
          return (
            <div
              key={g.key}
              style={{
                display: 'grid',
                gap: 12,
                padding: '14px 16px',
                borderRadius: 12,
                background: palette.inset,
                border: `1px solid ${problem ? palette.accentLine : palette.hairline}`,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <b style={{ fontSize: 15 }}>{g.payeeName}</b>
                <Pill tone="cyan">{g.currency}</Pill>
                <span style={{ fontSize: 12, color: palette.mute }}>
                  {g.rows.length} 张应付
                </span>
                <span style={{ flex: 1 }} />
                {problem ? (
                  <span style={{ fontSize: 12, color: palette.orange }}>
                    <ExclamationCircleOutlined /> {problem}
                  </span>
                ) : (
                  <span style={{ fontSize: 12, color: palette.green }}>
                    <CheckCircleOutlined /> 已填好
                  </span>
                )}
              </div>
              <div style={{ display: 'grid', gap: 2 }}>
                {g.rows.map((r) => (
                  <div
                    key={r.id}
                    style={{
                      display: 'flex',
                      fontSize: 12,
                      color: palette.sub,
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    <span style={{ flex: 1 }}>
                      {r.payableNo}
                      {r.nextDueDate ? ` · ${r.nextDueDate} 到期` : ''}
                    </span>
                    {formatAmount(r.unpaidAmount)}
                  </div>
                ))}
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <Field label="我方付款账户" required flex>
                  <Select
                    value={v.bankAccountId}
                    onChange={(id) => patch(g.key, { bankAccountId: id })}
                    style={{ width: '100%' }}
                    options={own.map((a) => ({
                      value: a.id,
                      label: `${a.bankName} · ${a.accountName} ${a.accountNoMasked}`,
                    }))}
                    notFoundContent={`没有${method === METHOD.PRIVATE ? '对私' : '对公'}的 ${g.currency} 账户，请先在「收款账户」里添加`}
                  />
                </Field>
                <Field label="对方收款账户" flex>
                  <PayeeAccountPicker
                    method={method}
                    accounts={v.payeeAccounts}
                    value={v.payeeAccountId}
                    onChange={(id) => patch(g.key, { payeeAccountId: id })}
                    supplierId={g.supplierId}
                  />
                </Field>
              </div>
              <div
                style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}
              >
                <Field label={`付款金额（${g.currency}）`} required width={200}>
                  <InputNumber
                    value={v.amount}
                    min={0.01}
                    max={g.unpaid}
                    precision={2}
                    style={{ width: '100%' }}
                    onChange={(n) => patch(g.key, { amount: n })}
                  />
                </Field>
                {g.currency !== 'CNY' && (
                  <Field label="实付人民币" required width={200}>
                    <InputNumber
                      value={v.amountCny}
                      min={0.01}
                      precision={2}
                      style={{ width: '100%' }}
                      onChange={(n) => patch(g.key, { amountCny: n })}
                    />
                  </Field>
                )}
                <Field label="付款截图" required flex>
                  <AttachmentWall
                    ownerType="PAYMENT"
                    value={v.files}
                    onChange={(files) => patch(g.key, { files })}
                    max={10}
                    accept={ACCEPT_PROOF}
                  />
                </Field>
              </div>
            </div>
          );
        })}
        <div style={{ fontSize: 12, color: palette.mute }}>
          付款金额默认等于未付合计；改小时按到期日从早到晚依次核销。任何一笔保存失败，整批都不保存。
        </div>
      </div>
    </Modal>
  );
};
