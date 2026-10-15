import { WarningOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Badge,
  Button,
  DatePicker,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';
import { auditColumns } from '@/pages/purchase/components';
import {
  type BankOption,
  bankOptionsApi,
  readBizError,
} from '@/pages/sales/service';
import {
  AttachmentWall,
  ReasonModal,
  UploadTile,
} from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { ACCEPT_PROOF, ProofLinks } from '../payables/components';
import {
  type AdvanceItem,
  BX_STATUS,
  ITEM_NAMES,
  type RefundReminder,
  type Reimbursement,
  reimburseApi,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const ITEM_TONE: Record<number, Tone> = {
  1: 'accent',
  2: 'violet',
  3: 'orange',
  4: 'cyan',
  5: 'green',
};
const STATUS_META: Record<number, { label: string; tone: Tone }> = {
  [BX_STATUS.PENDING]: { label: '待付款', tone: 'orange' },
  [BX_STATUS.PAID]: { label: '已报销', tone: 'green' },
  [BX_STATUS.WITHDRAWN]: { label: '已撤回', tone: 'mute' },
};
const keyOf = (i: AdvanceItem) => `${i.sourceType}:${i.sourceId}`;
const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

const StatusPill: React.FC<{ status: number }> = ({ status }) => {
  const m = STATUS_META[status];
  return m ? <Pill tone={m.tone}>{m.label}</Pill> : null;
};

/** 登记已收到退款：供应商退到员工手里 */
const RefundReceivedModal: React.FC<{
  target?: RefundReminder;
  onClose: () => void;
  onDone: () => void;
}> = ({ target, onClose, onDone }) => {
  const { message } = App.useApp();
  const [amount, setAmount] = useState<number | null>(null);
  const [amountCny, setAmountCny] = useState<number | null>(null);
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [files, setFiles] = useState<Attachment[]>([]);
  const [remark, setRemark] = useState('');
  const [key, setKey] = useState(newKey);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (!target) return;
    setAmount(target.creditAmount);
    setAmountCny(null);
    setDate(dayjs());
    setFiles([]);
    setRemark('');
    setKey(newKey());
  }, [target]);
  const submit = async () => {
    if (!target || !amount || files.length === 0) {
      message.error('请填写金额并上传收款截图');
      return;
    }
    setSaving(true);
    try {
      await reimburseApi.refundReceived({
        payableId: target.payableId,
        amount,
        amountCny:
          target.currencyCode !== 'CNY' ? (amountCny ?? undefined) : undefined,
        receivedDate: date.format('YYYY-MM-DD'),
        attachmentIds: files.map((f) => f.id),
        remark: remark.trim() || undefined,
        idempotencyKey: key,
      });
      message.success('已登记，退款会在报销时冲减');
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
      title={`登记已收到退款 · ${target?.payeeName ?? ''}`}
      okText="确认登记"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <Space orientation="vertical" size={12} style={{ width: '100%' }}>
        <div style={{ fontSize: 13 }}>
          {target?.payableNo}（{target?.sourceNo}）欠款{' '}
          {formatAmount(target?.creditAmount, target?.currencyCode)}
        </div>
        <InputNumber<number>
          aria-label="退款金额"
          min={0.01}
          max={target?.creditAmount}
          precision={2}
          style={{ width: '100%' }}
          value={amount}
          onChange={setAmount}
          prefix={target?.currencyCode}
        />
        {target && target.currencyCode !== 'CNY' && (
          <InputNumber<number>
            aria-label="收到的人民币"
            min={0}
            precision={2}
            style={{ width: '100%' }}
            value={amountCny}
            onChange={setAmountCny}
            prefix="CNY"
            placeholder="收到的人民币"
          />
        )}
        <DatePicker
          value={date}
          onChange={(d) => d && setDate(d)}
          disabledDate={(d) => d.isAfter(dayjs(), 'day')}
          allowClear={false}
        />
        <AttachmentWall
          ownerType="PAYMENT"
          value={files}
          onChange={setFiles}
          max={10}
          accept={ACCEPT_PROOF}
        />
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

/** 待报销：本人的垫付，勾选提交；快递费、退货运费在这里补传截图 */
const PendingTab: React.FC<{
  onSubmitted: () => void;
  onCount: (n: number) => void;
}> = ({ onSubmitted, onCount }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [items, setItems] = useState<AdvanceItem[]>([]);
  const [reminders, setReminders] = useState<RefundReminder[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [refund, setRefund] = useState<RefundReminder>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [list, rem] = await Promise.all([
        reimburseApi.pending(),
        reimburseApi.reminders(),
      ]);
      setItems(list);
      setReminders(rem);
      onCount(list.length);
      setSelected((s) => s.filter((k) => list.some((i) => keyOf(i) === k)));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [message, onCount]);

  useEffect(() => {
    load();
  }, [load]);

  const chosen = useMemo(
    () => items.filter((i) => selected.includes(keyOf(i))),
    [items, selected],
  );
  const total = chosen.reduce((s, i) => s + i.amount, 0);
  const missing = chosen.filter((i) => i.proofs.length === 0).length;

  const submit = async () => {
    setSaving(true);
    try {
      const bx = await reimburseApi.submit(
        chosen.map((i) => ({ sourceType: i.sourceType, sourceId: i.sourceId })),
      );
      message.success(`已提交报销单 ${bx.reimburseNo}`);
      setSelected([]);
      load();
      onSubmitted();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const columns: TableColumnsType<AdvanceItem> = [
    {
      title: '来源',
      dataIndex: 'sourceType',
      width: 100,
      render: (v: number) => <Pill tone={ITEM_TONE[v]}>{ITEM_NAMES[v]}</Pill>,
    },
    { title: '单据', dataIndex: 'sourceNo', width: 170 },
    { title: '日期', dataIndex: 'occurDate', width: 110 },
    { title: '说明', dataIndex: 'description' },
    {
      title: '金额（CNY）',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number) => (
        <b style={{ ...num, color: v < 0 ? palette.green : palette.ink }}>
          {formatAmount(v, 'CNY')}
        </b>
      ),
    },
    {
      title: '截图',
      key: 'proof',
      width: 150,
      render: (_, i) =>
        i.proofs.length > 0 ? (
          <ProofLinks files={i.proofs} />
        ) : i.proofUploadable ? (
          <Space size={6}>
            <UploadTile
              ownerType={i.proofOwnerType as 'COURIER_PROOF'}
              accept={ACCEPT_PROOF}
              size={34}
              onUploaded={async (a) => {
                try {
                  await reimburseApi.addProof(i.sourceType, i.sourceId, [a.id]);
                  message.success('截图已补传');
                  load();
                } catch (e) {
                  message.error(readBizError(e).message);
                }
              }}
            />
            <span style={{ color: palette.orange, fontSize: 12 }}>
              补传截图
            </span>
          </Space>
        ) : (
          <span style={{ color: palette.orange, fontSize: 12 }}>
            原单据没有截图
          </span>
        ),
    },
  ];

  return (
    <div>
      {reminders.length > 0 && (
        <Alert
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          style={{ marginBottom: 16 }}
          title={`有 ${reminders.length} 笔供应商欠款还没退回`}
          description={
            <Space orientation="vertical" style={{ width: '100%' }}>
              {reminders.map((r) => (
                <div
                  key={r.payableId}
                  style={{ display: 'flex', alignItems: 'center', gap: 10 }}
                >
                  <span>
                    {r.payeeName} · {r.payableNo}（{r.sourceNo}）欠款{' '}
                    <b>{formatAmount(r.creditAmount, r.currencyCode)}</b> · 已{' '}
                    {r.days} 天
                  </span>
                  <Button size="small" onClick={() => setRefund(r)}>
                    登记已收到退款
                  </Button>
                </div>
              ))}
            </Space>
          }
        />
      )}
      <Card>
        <Table<AdvanceItem>
          rowKey={keyOf}
          loading={loading}
          columns={columns}
          dataSource={items}
          pagination={false}
          rowSelection={{
            selectedRowKeys: selected,
            onChange: (keys) => setSelected(keys as string[]),
          }}
          locale={{ emptyText: '没有待报销的垫付' }}
          scroll={{ x: 900 }}
        />
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            marginTop: 14,
            flexWrap: 'wrap',
          }}
        >
          <span>
            已选 {chosen.length} 项，合计{' '}
            <b style={{ ...num, fontSize: 18 }}>{formatAmount(total, 'CNY')}</b>
          </span>
          {missing > 0 && (
            <span style={{ color: palette.orange, fontSize: 12 }}>
              {missing} 项还没截图，补传后才能提交
            </span>
          )}
          <span style={{ flex: 1 }} />
          <Button
            type="primary"
            loading={saving}
            disabled={chosen.length === 0 || missing > 0 || total <= 0}
            onClick={submit}
          >
            提交报销单
          </Button>
        </div>
      </Card>
      <RefundReceivedModal
        target={refund}
        onClose={() => setRefund(undefined)}
        onDone={load}
      />
    </div>
  );
};

/** 财务登记报销已付 */
const PayModal: React.FC<{
  target?: Reimbursement;
  onClose: () => void;
  onDone: () => void;
}> = ({ target, onClose, onDone }) => {
  const { message } = App.useApp();
  const [accounts, setAccounts] = useState<BankOption[]>([]);
  const [accountId, setAccountId] = useState<number>();
  /** 1-公司对公、3-私对私（法人等个人账户付给员工） */
  const [method, setMethod] = useState(1);
  const [payerAccount, setPayerAccount] = useState('');
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [files, setFiles] = useState<Attachment[]>([]);
  const [remark, setRemark] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (!target) return;
    setMethod(1);
    setPayerAccount('');
    setDate(dayjs());
    setFiles([]);
    setRemark('');
    bankOptionsApi
      .options()
      .then((list) => {
        const ok = list.filter((a) => a.enabled && a.currencyCode === 'CNY');
        setAccounts(ok);
        setAccountId((ok.find((a) => a.isDefault) ?? ok[0])?.id);
      })
      .catch(() => setAccounts([]));
  }, [target]);
  const submit = async () => {
    if (!target || files.length === 0) {
      message.error('请上传转账截图');
      return;
    }
    if (method === 1 && !accountId) {
      message.error('请选择付款账户');
      return;
    }
    if (method === 3 && !payerAccount.trim()) {
      message.error('私对私请填写付款个人账户');
      return;
    }
    setSaving(true);
    try {
      await reimburseApi.pay(target.id, {
        method,
        bankAccountId: method === 1 ? accountId : undefined,
        payerAccount: method === 3 ? payerAccount.trim() : undefined,
        payDate: date.format('YYYY-MM-DD'),
        attachmentIds: files.map((f) => f.id),
        remark: remark.trim() || undefined,
      });
      message.success('已登记已付');
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
      title={`登记已付 · ${target?.reimburseNo ?? ''} · ${formatAmount(target?.totalAmount, 'CNY')}`}
      okText="确认已付"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <Space orientation="vertical" size={12} style={{ width: '100%' }}>
        <Segmented<number>
          value={method}
          onChange={setMethod}
          options={[
            { label: '公司对公', value: 1 },
            { label: '私对私', value: 3 },
          ]}
        />
        {method === 1 ? (
          <Select
            aria-label="付款账户"
            style={{ width: '100%' }}
            value={accountId}
            onChange={setAccountId}
            options={accounts.map((a) => ({
              value: a.id,
              label: `${a.bankName} · ${a.accountName} ${a.accountNoMasked}`,
            }))}
          />
        ) : (
          <Input
            aria-label="付款个人账户"
            maxLength={100}
            placeholder="付款个人账户，如 法人 张三 · 招行尾号 1234"
            value={payerAccount}
            onChange={(e) => setPayerAccount(e.target.value)}
          />
        )}
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

/** 报销单列表与详情 */
const ListTab: React.FC<{ refreshSignal: number }> = ({ refreshSignal }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canPay = !!access['finance:reimburse:pay'];
  const [status, setStatus] = useState<number>();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<Reimbursement[]>([]);
  const [total, setTotal] = useState(0);
  const [pendingTotal, setPendingTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<Reimbursement>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [paying, setPaying] = useState<Reimbursement>();
  const [reason, setReason] = useState<{
    kind: 'withdraw' | 'unpay';
    bx: Reimbursement;
  }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, t] = await Promise.all([
        reimburseApi.page({
          page,
          pageSize,
          status,
          keyword: query || undefined,
        }),
        reimburseApi.pendingTotal(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setPendingTotal(t);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, status, query, message]);

  useEffect(() => {
    load();
  }, [load, refreshSignal]);

  const open = async (id: number) => {
    setDetailLoading(true);
    setDetail(undefined);
    try {
      setDetail(await reimburseApi.detail(id));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setDetailLoading(false);
    }
  };

  const refresh = () => {
    load();
    if (detail) open(detail.id);
  };

  const columns: TableColumnsType<Reimbursement> = [
    {
      title: '编号',
      dataIndex: 'reimburseNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => <a onClick={() => open(r.id)}>{v}</a>,
    },
    { title: '提交人', dataIndex: 'applicantName', width: 100 },
    {
      title: '提交时间',
      dataIndex: 'submittedAt',
      width: 170,
      render: (v: string) => formatDateTime(v),
    },
    { title: '项数', dataIndex: 'lineCount', width: 70 },
    {
      title: '合计',
      dataIndex: 'totalAmount',
      width: 130,
      align: 'right',
      render: (v: number) => <b style={num}>{formatAmount(v, 'CNY')}</b>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => <StatusPill status={v} />,
    },
    {
      title: '付款日期',
      dataIndex: 'payDate',
      width: 110,
      render: (v?: string) => v || '—',
    },
    ...auditColumns<Reimbursement>(),
    {
      title: '操作',
      key: 'op',
      width: 120,
      fixed: 'right',
      render: (_, r) => (
        <Space size={10}>
          {canPay && r.status === BX_STATUS.PENDING && (
            <a onClick={() => setPaying(r)}>登记已付</a>
          )}
          {r.canWithdraw && (
            <a onClick={() => setReason({ kind: 'withdraw', bx: r })}>撤回</a>
          )}
          {!canPay && !r.canWithdraw && <a onClick={() => open(r.id)}>查看</a>}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div
        style={{ display: 'flex', gap: 14, flexWrap: 'wrap', marginBottom: 16 }}
      >
        <div
          style={{
            flex: '0 1 260px',
            padding: '16px 18px',
            borderRadius: 14,
            background: palette.card,
            border: `1px solid ${palette.hairline}`,
          }}
        >
          <div style={{ fontSize: 13, color: palette.sub }}>待付款合计</div>
          <div
            style={{
              ...num,
              fontSize: 22,
              fontWeight: 800,
              color: palette.orange,
              marginTop: 4,
            }}
          >
            {formatAmount(pendingTotal, 'CNY')}
          </div>
        </div>
      </div>
      <Card>
        <div
          style={{
            display: 'flex',
            gap: 10,
            marginBottom: 14,
            flexWrap: 'wrap',
          }}
        >
          <Input
            allowClear
            placeholder="编号、提交人"
            style={{ width: 200 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={() => {
              setQuery(keyword.trim());
              setPage(1);
            }}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 120 }}
            value={status}
            onChange={(v) => {
              setStatus(v);
              setPage(1);
            }}
            options={Object.entries(STATUS_META).map(([k, m]) => ({
              value: Number(k),
              label: m.label,
            }))}
          />
          <Button
            onClick={() => {
              setQuery(keyword.trim());
              setPage(1);
            }}
          >
            查询
          </Button>
        </div>
        <Table<Reimbursement>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1400 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      </Card>
      <Drawer
        open={detailLoading || !!detail}
        onClose={() => setDetail(undefined)}
        size="min(860px, 96vw)"
        destroyOnHidden
        title={
          detail ? (
            <Space>
              {detail.reimburseNo}
              <StatusPill status={detail.status} />
            </Space>
          ) : (
            '报销单'
          )
        }
        extra={
          detail ? (
            <Space>
              {detail.canWithdraw && (
                <Button
                  onClick={() => setReason({ kind: 'withdraw', bx: detail })}
                >
                  撤回
                </Button>
              )}
              {canPay && detail.status === BX_STATUS.PENDING && (
                <Button type="primary" onClick={() => setPaying(detail)}>
                  登记已付
                </Button>
              )}
              {canPay && detail.status === BX_STATUS.PAID && (
                <Button
                  danger
                  onClick={() => setReason({ kind: 'unpay', bx: detail })}
                >
                  撤销已付
                </Button>
              )}
            </Space>
          ) : null
        }
      >
        {!detail ? (
          <Spin />
        ) : (
          <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            <div style={{ color: palette.sub }}>
              {detail.applicantName} · {formatDateTime(detail.submittedAt)} 提交
              · {detail.lineCount} 项 · 合计{' '}
              <b style={{ color: palette.ink }}>
                {formatAmount(detail.totalAmount, 'CNY')}
              </b>
              {detail.status === BX_STATUS.WITHDRAWN &&
                detail.withdrawReason &&
                ` · 撤回原因：${detail.withdrawReason}`}
            </div>
            <Table<AdvanceItem>
              rowKey={keyOf}
              size="small"
              pagination={false}
              dataSource={detail.lines}
              columns={[
                {
                  title: '来源',
                  dataIndex: 'sourceType',
                  width: 100,
                  render: (v: number) => (
                    <Pill tone={ITEM_TONE[v]}>{ITEM_NAMES[v]}</Pill>
                  ),
                },
                { title: '单据', dataIndex: 'sourceNo', width: 160 },
                { title: '说明', dataIndex: 'description' },
                {
                  title: '金额',
                  dataIndex: 'amount',
                  width: 120,
                  align: 'right',
                  render: (v: number) => formatAmount(v, 'CNY'),
                },
                {
                  title: '截图',
                  key: 'p',
                  width: 90,
                  render: (_, i) => <ProofLinks files={i.proofs} />,
                },
              ]}
            />
            {detail.status === BX_STATUS.PAID && (
              <Card>
                <b>已付</b>
                <div style={{ color: palette.sub, marginTop: 6 }}>
                  {detail.payDate} ·{' '}
                  {detail.payMethod === 3
                    ? `私对私 · ${detail.payPayerAccount}`
                    : detail.payAccountName}{' '}
                  · {detail.paidBy} 登记 · 付款单 {detail.paymentNo}
                </div>
                <div style={{ marginTop: 6 }}>
                  转账截图 <ProofLinks files={detail.payProofs} />
                </div>
              </Card>
            )}
          </Space>
        )}
      </Drawer>
      <PayModal
        target={paying}
        onClose={() => setPaying(undefined)}
        onDone={refresh}
      />
      <ReasonModal
        open={!!reason}
        title={
          reason?.kind === 'unpay'
            ? `撤销已付 ${reason.bx.reimburseNo}`
            : `撤回 ${reason?.bx.reimburseNo ?? ''}`
        }
        description={
          reason?.kind === 'unpay'
            ? '报销单回到「待付款」，对应的报销付款单作废。'
            : '撤回后各项回到待报销，补传的截图保留。'
        }
        okText={reason?.kind === 'unpay' ? '撤销已付' : '撤回'}
        danger
        onCancel={() => setReason(undefined)}
        onOk={async (why) => {
          if (!reason) return;
          await (reason.kind === 'unpay'
            ? reimburseApi.unpay(reason.bx.id, why)
            : reimburseApi.withdraw(reason.bx.id, why));
          message.success(reason.kind === 'unpay' ? '已撤销已付' : '已撤回');
          setReason(undefined);
          refresh();
        }}
      />
    </div>
  );
};

/** 财务管理 → 报销单：员工提交本人的垫付，财务登记已付 */
const ReimbursementPage: React.FC = () => {
  const [tab, setTab] = useState('pending');
  const [count, setCount] = useState<number>();
  const [signal, setSignal] = useState(0);
  const onCount = useCallback((n: number) => setCount(n), []);
  return (
    <div>
      <PageTitle
        crumbs={['报销单']}
        title="报销单"
        description="勾选自己的待报销项提交，暂不审批；快递费、退货运费在这里补传截图。供应商退到你手里的钱，登记「已收到退款」后会在报销时冲减。"
      />
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: 'pending',
            label: (
              <span
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                待报销 {count ? <Badge count={count} color="orange" /> : null}
              </span>
            ),
            children: (
              <PendingTab
                onCount={onCount}
                onSubmitted={() => {
                  setSignal((s) => s + 1);
                }}
              />
            ),
          },
          {
            key: 'list',
            label: '报销单',
            children: <ListTab refreshSignal={signal} />,
          },
        ]}
      />
    </div>
  );
};

export default ReimbursementPage;
