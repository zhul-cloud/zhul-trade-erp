import { WarningOutlined } from '@ant-design/icons';
import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Badge,
  Button,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Space,
  Table,
  Tabs,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ellipsisRender } from '@/components/EllipsisText';
import { Card, PageTitle } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import { readBizError } from '@/pages/sales/service';
import {
  AttachmentWall,
  DOC_PATHS,
  UploadTile,
} from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { ACCEPT_PROOF, ProofLinks } from '../payables/components';
import {
  keyOf,
  num,
  PayModal,
  STATUS_META,
  StatusPill,
  sourceCell,
} from './components';
import {
  type AdvanceItem,
  BX_STATUS,
  type RefundReminder,
  type Reimbursement,
  reimburseApi,
} from './service';

const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

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
  const access = useAccess() as Record<string, boolean>;
  // 有「报销付款」权限（管理员、财务）的可以看所有人的垫付，并代别人提交
  const canAll = !!access['finance:reimburse:pay'];
  const [scope, setScope] = useState<'mine' | 'all'>('mine');
  const [person, setPerson] = useState<number>();
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
        reimburseApi.pending(scope === 'all'),
        reimburseApi.reminders(),
      ]);
      setItems(list);
      setReminders(rem);
      // 标签上的数字只算本人的
      onCount(list.filter((i) => i.mine).length);
      setSelected((s) => s.filter((k) => list.some((i) => keyOf(i) === k)));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [message, onCount, scope]);

  useEffect(() => {
    load();
  }, [load]);

  const people = useMemo(() => {
    const m = new Map<number, string>();
    for (const i of items) {
      if (i.advanceUserId != null && !m.has(i.advanceUserId)) {
        m.set(i.advanceUserId, i.advanceUserName || '—');
      }
    }
    return [...m].map(([value, label]) => ({ value, label }));
  }, [items]);
  const shown = useMemo(
    () =>
      scope === 'all' && person != null
        ? items.filter((i) => i.advanceUserId === person)
        : items,
    [items, scope, person],
  );
  const chosen = useMemo(
    () => items.filter((i) => selected.includes(keyOf(i))),
    [items, selected],
  );
  const chosenPeople = new Set(chosen.map((i) => i.advanceUserId)).size;
  const total = chosen.reduce((s, i) => s + i.amount, 0);
  const missing = chosen.filter((i) => i.proofs.length === 0).length;

  const submit = async () => {
    setSaving(true);
    try {
      const list = await reimburseApi.submit(
        chosen.map((i) => ({ sourceType: i.sourceType, sourceId: i.sourceId })),
      );
      message.success(
        `已提交报销单 ${list.map((b) => b.reimburseNo).join('、')}`,
      );
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
    ...(scope === 'all'
      ? [
          {
            title: '垫付人',
            key: 'person',
            width: 120,
            render: (_: unknown, i: AdvanceItem) => (
              <span style={{ whiteSpace: 'nowrap' }}>
                {i.advanceUserName || '—'}
                {i.mine && <span style={{ color: palette.mute }}>（我）</span>}
              </span>
            ),
          },
        ]
      : []),
    { title: '来源单号', key: 'source', width: 240, render: sourceCell },
    { title: '日期', dataIndex: 'occurDate', width: 110 },
    {
      title: '说明',
      width: 240,
      render: ellipsisRender,
      dataIndex: 'description',
    },
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
        {canAll && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              marginBottom: 14,
              flexWrap: 'wrap',
            }}
          >
            <Segmented<'mine' | 'all'>
              value={scope}
              onChange={(v) => {
                setScope(v);
                setPerson(undefined);
                setSelected([]);
              }}
              options={[
                { value: 'mine', label: '我的' },
                { value: 'all', label: '全部人员' },
              ]}
            />
            {scope === 'all' && (
              <Select
                allowClear
                showSearch={{ optionFilterProp: 'label' }}
                placeholder="垫付人"
                style={{ width: 160 }}
                value={person}
                onChange={setPerson}
                options={people}
              />
            )}
            {scope === 'all' && (
              <span style={{ color: palette.mute, fontSize: 12 }}>
                可以代别人提交：勾选多人的垫付时，每人生成一张报销单，报销人是垫付本人
              </span>
            )}
          </div>
        )}
        <Table<AdvanceItem>
          rowKey={keyOf}
          loading={loading}
          columns={columns}
          dataSource={shown}
          pagination={false}
          rowSelection={{
            selectedRowKeys: selected,
            onChange: (keys) => setSelected(keys as string[]),
          }}
          locale={{ emptyText: '没有待报销的垫付' }}
          scroll={{ x: tableScrollX(columns) }}
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
            已选 {chosen.length} 项
            {chosenPeople > 1 &&
              `（${chosenPeople} 人，将生成 ${chosenPeople} 张报销单）`}
            ，合计{' '}
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

/** 报销单列表；详情、撤回、单张登记已付与撤销已付在详情页（change finance-expense-docs） */
const _ListTab: React.FC<{ refreshSignal: number }> = ({ refreshSignal }) => {
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
  const [paying, setPaying] = useState<Reimbursement[]>();
  const [picked, setPicked] = useState<Reimbursement[]>([]);

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

  const columns: TableColumnsType<Reimbursement> = [
    {
      title: '编号',
      dataIndex: 'reimburseNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => (
        <Link to={DOC_PATHS.reimbursement(r.id)}>{v}</Link>
      ),
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
      width: 90,
      fixed: 'right',
      render: (_, r) => <Link to={DOC_PATHS.reimbursement(r.id)}>查看</Link>,
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
          rowSelection={
            canPay
              ? {
                  selectedRowKeys: picked.map((r) => r.id),
                  preserveSelectedRowKeys: true,
                  getCheckboxProps: (r) => ({
                    disabled: r.status !== BX_STATUS.PENDING,
                  }),
                  onChange: (_, list) => setPicked(list.filter(Boolean)),
                }
              : undefined
          }
          scroll={{ x: tableScrollX(columns) }}
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
        {picked.length > 0 && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              marginTop: 14,
              padding: '12px 18px',
              borderRadius: 12,
              background: palette.inset,
              flexWrap: 'wrap',
            }}
          >
            <span>
              已选 {picked.length} 张（
              {new Set(picked.map((r) => r.applicantId)).size} 人），合计{' '}
              <b style={{ ...num, fontSize: 18 }}>
                {formatAmount(
                  picked.reduce((s, r) => s + r.totalAmount, 0),
                  'CNY',
                )}
              </b>
            </span>
            {picked.length > 50 && (
              <span style={{ color: palette.orange, fontSize: 12 }}>
                一次最多 50 张
              </span>
            )}
            <span style={{ flex: 1 }} />
            <Button onClick={() => setPicked([])}>取消选择</Button>
            <Button
              type="primary"
              disabled={picked.length > 50}
              onClick={() => setPaying(picked)}
            >
              批量登记已付
            </Button>
          </div>
        )}
      </Card>
      <PayModal
        targets={paying}
        onClose={() => setPaying(undefined)}
        onDone={() => {
          setPicked([]);
          load();
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
      <PageTitle description="勾选自己的待报销项提交，暂不审批；快递费、退货运费在这里补传截图。供应商退到你手里的钱，登记「已收到退款」后会在报销时冲减。" />
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
            children: <_ListTab refreshSignal={signal} />,
          },
        ]}
      />
    </div>
  );
};

export default ReimbursementPage;
