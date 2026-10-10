import {
  CheckOutlined,
  DownloadOutlined,
  InfoCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  DatePicker,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Select,
  Skeleton,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { LOGISTICS_PATHS, StatementStatusPill } from '../components';
import {
  type Forwarder,
  forwarderApi,
  readBizError,
  type Statement,
  statementApi,
} from '../service';

type Line = NonNullable<Statement['lines']>[number];

const money = (v?: number | null) => formatAmount(v, 'CNY');
const diffText = (v: number) => (v > 0 ? `+${money(v)}` : money(v));

// ---------------------------------------------------------------- 新建

const CreateModal: React.FC<{
  open: boolean;
  forwarders: Forwarder[];
  onClose: () => void;
  onDone: (s: Statement) => void;
}> = ({ open, forwarders, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [forwarderId, setForwarderId] = useState<number>();
  const [month, setMonth] = useState<Dayjs>(dayjs());
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) {
      setForwarderId(forwarders.length === 1 ? forwarders[0].id : undefined);
      setMonth(dayjs());
    }
  }, [open, forwarders]);
  return (
    <Modal
      open={open}
      onCancel={onClose}
      title="新建对账单"
      destroyOnHidden
      okText="新建"
      okButtonProps={{ disabled: !forwarderId, loading: busy }}
      onOk={async () => {
        if (!forwarderId) return;
        setBusy(true);
        try {
          onDone(
            await statementApi.create(forwarderId, month.format('YYYY-MM')),
          );
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
    >
      <div style={{ display: 'grid', gap: 12 }}>
        <div>
          <div style={{ color: palette.sub, marginBottom: 6 }}>货代</div>
          <Select
            value={forwarderId}
            onChange={setForwarderId}
            placeholder="选择货代"
            options={forwarders.map((f) => ({ value: f.id, label: f.name }))}
            style={{ width: '100%' }}
            aria-label="货代"
          />
        </div>
        <div>
          <div style={{ color: palette.sub, marginBottom: 6 }}>对账月份</div>
          <DatePicker
            picker="month"
            value={month}
            allowClear={false}
            disabledDate={(d) => d.isAfter(dayjs(), 'month')}
            onChange={(d) => d && setMonth(d)}
            style={{ width: '100%' }}
            aria-label="对账月份"
          />
        </div>
        <div style={{ fontSize: 12, color: palette.mute }}>
          列出这个月已出运、还没对账的出运单，对账金额默认等于登记的运费。
        </div>
      </div>
    </Modal>
  );
};

// ---------------------------------------------------------------- 核对

const StatementDrawer: React.FC<{
  id?: number;
  canEdit: boolean;
  onClose: () => void;
  onChanged: () => void;
}> = ({ id, canEdit, onClose, onChanged }) => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const [st, setSt] = useState<Statement>();
  const [error, setError] = useState<string>();
  const [edits, setEdits] = useState<
    Record<number, { amount: number | null; note: string }>
  >({});
  const [busy, setBusy] = useState(false);

  const show = useCallback((s: Statement) => {
    setSt(s);
    setEdits(
      Object.fromEntries(
        (s.lines ?? []).map((l) => [
          l.id,
          { amount: l.statementAmount, note: l.note ?? '' },
        ]),
      ),
    );
  }, []);

  const load = useCallback(() => {
    if (id === undefined) return;
    setSt(undefined);
    setError(undefined);
    statementApi
      .detail(id)
      .then(show)
      .catch((e) => setError(readBizError(e).message));
  }, [id, show]);
  useEffect(load, [load]);

  const draft = st?.status === 1 && canEdit;
  const lines = st?.lines ?? [];
  const amountOf = (l: Line) =>
    draft ? (edits[l.id]?.amount ?? 0) : l.statementAmount;
  const ourTotal = lines.reduce((n, l) => n + Number(l.ourFreight), 0);
  const total = lines.reduce((n, l) => n + Number(amountOf(l)), 0);
  const diffLines = lines.filter(
    (l) => Math.abs(amountOf(l) - l.ourFreight) > 0.001,
  );
  const missingNote = draft && diffLines.find((l) => !edits[l.id]?.note.trim());
  const body = () =>
    lines.map((l) => ({
      id: l.id,
      statementAmount: edits[l.id]?.amount ?? 0,
      note: edits[l.id]?.note.trim(),
    }));

  const run = async (fn: () => Promise<Statement>, ok: string) => {
    setBusy(true);
    try {
      show(await fn());
      message.success(ok);
      onChanged();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const confirm = () => {
    if (!st) return;
    modal.confirm({
      title: '确认对账？',
      content: `${lines.length} 票出运单标记「已对账」，${
        diffLines.length
          ? `${diffLines.length} 票按对账金额更新运费并重新分摊，`
          : ''
      }确认后运费不能再改。`,
      okText: '确认对账',
      onOk: () => run(() => statementApi.confirm(st.id, body()), '对账已确认'),
    });
  };

  const stat = (
    title: string,
    value: React.ReactNode,
    hint: React.ReactNode,
  ) => (
    <div
      style={{
        padding: '14px 18px',
        borderRadius: 12,
        background: palette.inset,
      }}
    >
      <div style={{ fontSize: 13, color: palette.sub }}>{title}</div>
      <div
        style={{
          fontSize: 24,
          fontWeight: 700,
          color: palette.ink,
          margin: '4px 0',
        }}
      >
        {value}
      </div>
      <div style={{ fontSize: 12 }}>{hint}</div>
    </div>
  );

  const columns: TableColumnsType<Line> = [
    {
      title: '出运单',
      dataIndex: 'shNo',
      width: 150,
      render: (v: string, l) => (
        <a
          onClick={() =>
            history.push(`${LOGISTICS_PATHS.shipments}/${l.logisticsId}`)
          }
        >
          {v}
        </a>
      ),
    },
    { title: '出运日期', dataIndex: 'shippedDate', width: 110 },
    {
      title: '运单号',
      key: 'waybill',
      width: 150,
      render: (_, l) => (
        <div>
          <b style={{ color: palette.ink }}>{l.carrier}</b>
          <div style={{ fontSize: 12, color: palette.mute }}>{l.waybillNo}</div>
        </div>
      ),
    },
    { title: '客户', dataIndex: 'customerName', width: 180 },
    {
      title: '我们登记的运费',
      dataIndex: 'ourFreight',
      width: 130,
      render: (v: number) => money(v),
    },
    {
      title: '对账金额',
      key: 'amount',
      width: 140,
      render: (_, l) =>
        draft ? (
          <InputNumber
            value={edits[l.id]?.amount}
            min={0}
            precision={2}
            onChange={(v) =>
              setEdits((m) => ({
                ...m,
                [l.id]: { ...m[l.id], amount: v === null ? null : Number(v) },
              }))
            }
            style={{ width: 120 }}
            aria-label={`${l.shNo} 对账金额`}
          />
        ) : (
          money(l.statementAmount)
        ),
    },
    {
      title: '差额',
      key: 'diff',
      width: 110,
      render: (_, l) => {
        const d = Math.round((amountOf(l) - l.ourFreight) * 100) / 100;
        return d === 0 ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          <span style={{ color: palette.orange }}>{diffText(d)}</span>
        );
      },
    },
    {
      title: '说明',
      key: 'note',
      render: (_, l) =>
        draft ? (
          <Input
            value={edits[l.id]?.note}
            maxLength={200}
            status={
              missingNote && missingNote.id === l.id ? 'error' : undefined
            }
            placeholder={
              Math.abs(amountOf(l) - l.ourFreight) > 0.001
                ? '有差额，请写原因'
                : ''
            }
            onChange={(e) =>
              setEdits((m) => ({
                ...m,
                [l.id]: { ...m[l.id], note: e.target.value },
              }))
            }
            aria-label={`${l.shNo} 说明`}
          />
        ) : (
          l.note || <span style={{ color: palette.mute }}>—</span>
        ),
    },
  ];

  return (
    <Drawer
      open={id !== undefined}
      onClose={onClose}
      size="min(1180px, 96vw)"
      destroyOnHidden
      title={
        st
          ? `${st.forwarderName} · ${st.period.replace('-', ' 年 ')} 月`
          : '对账单'
      }
      extra={
        st && (
          <div style={{ display: 'flex', gap: 8 }}>
            <Button
              icon={<DownloadOutlined />}
              onClick={() =>
                statementApi
                  .export(st.id)
                  .catch((e) => message.error(readBizError(e).message))
              }
            >
              导出明细
            </Button>
            {draft && (
              <>
                <Button
                  loading={busy}
                  onClick={() =>
                    run(() => statementApi.save(st.id, body()), '已保存')
                  }
                >
                  保存
                </Button>
                <Button
                  type="primary"
                  icon={<CheckOutlined />}
                  loading={busy}
                  disabled={
                    !!missingNote ||
                    lines.some((l) => edits[l.id]?.amount === null)
                  }
                  onClick={confirm}
                >
                  确认对账
                </Button>
              </>
            )}
          </div>
        )
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !st ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              gap: 12,
            }}
          >
            {stat(
              '出运单',
              `${lines.length} 票`,
              <span style={{ color: palette.mute }}>{st.period} 已出运</span>,
            )}
            {stat(
              '我们登记',
              money(ourTotal),
              <span style={{ color: palette.mute }}>每票登记的运费</span>,
            )}
            {stat(
              '对账合计',
              money(total),
              diffLines.length ? (
                <span style={{ color: palette.orange }}>
                  差额 {diffText(Math.round((total - ourTotal) * 100) / 100)}（
                  {diffLines.length} 票）
                </span>
              ) : (
                <span style={{ color: palette.green }}>没有差额</span>
              ),
            )}
          </div>
          <Table<Line>
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={lines}
            columns={columns}
          />
          <div style={{ fontSize: 12, color: palette.mute }}>
            <InfoCircleOutlined />{' '}
            {st.status === 2
              ? `${st.confirmedByName ?? ''} 于 ${formatDateTime(st.confirmedAt)} 确认；这些出运单已对账，运费不能再改。`
              : '确认后这些出运单标记「已对账」，有差额的按对账金额更新运费并重新分摊，运费不能再改；有差额的必须写说明。'}
          </div>
          {draft && (
            <div>
              <Button
                danger
                type="text"
                onClick={() =>
                  modal.confirm({
                    title: '删除这张草稿？',
                    content: '出运单回到「未对账」，可以重新建对账单。',
                    okText: '删除',
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      try {
                        await statementApi.remove(st.id);
                        message.success('已删除');
                        onChanged();
                        onClose();
                      } catch (e) {
                        message.error(readBizError(e).message);
                      }
                    },
                  })
                }
              >
                删除草稿
              </Button>
            </div>
          )}
        </div>
      )}
    </Drawer>
  );
};

// ---------------------------------------------------------------- 列表

const Statements: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess();
  const canEdit = !!access['logistics:statement:edit'];
  const [forwarders, setForwarders] = useState<Forwarder[]>([]);
  const [forwarderId, setForwarderId] = useState<number>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Statement[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [creating, setCreating] = useState(false);
  const [viewing, setViewing] = useState<number>();

  useEffect(() => {
    forwarderApi
      .list()
      .then(setForwarders)
      .catch(() => setForwarders([]));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await statementApi.page({ page, pageSize, forwarderId });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, forwarderId]);

  useEffect(() => {
    load();
  }, [load]);

  const columns: TableColumnsType<Statement> = [
    {
      title: '货代 · 月份',
      key: 'name',
      width: 200,
      fixed: 'left',
      render: (_, r) => (
        <a onClick={() => setViewing(r.id)}>
          {r.forwarderName} · {r.period}
        </a>
      ),
    },
    {
      title: '出运单',
      dataIndex: 'shipmentCount',
      width: 80,
      render: (v: number) => `${v} 票`,
    },
    {
      title: '我们登记',
      dataIndex: 'ourTotal',
      width: 130,
      render: (v: number) => money(v),
    },
    {
      title: '对账合计',
      dataIndex: 'statementTotal',
      width: 130,
      render: (v: number) => money(v),
    },
    {
      title: '差额',
      dataIndex: 'diffTotal',
      width: 150,
      render: (v: number, r) =>
        Number(v) === 0 ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          <span style={{ color: palette.orange }}>
            {diffText(Number(v))}（{r.diffCount} 票）
          </span>
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number, r) => (
        <StatementStatusPill value={v}>{r.statusName}</StatementStatusPill>
      ),
    },
    {
      title: '确认',
      key: 'confirmed',
      width: 170,
      render: (_, r) =>
        r.confirmedAt ? (
          <div>
            {r.confirmedByName}
            <div style={{ fontSize: 12, color: palette.mute }}>
              {formatDateTime(r.confirmedAt)}
            </div>
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    ...auditColumns<Statement>(),
    {
      title: '操作',
      key: 'actions',
      width: 80,
      fixed: 'right',
      render: (_, r) => (
        <a onClick={() => setViewing(r.id)}>
          {r.status === 1 && canEdit ? '核对' : '查看'}
        </a>
      ),
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['货代对账']}
        title="货代对账"
        description="按货代的月结账单逐票核对运费；有差额的确认后更新运费并重新分摊。"
        actions={
          canEdit && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreating(true)}
            >
              新建对账单
            </Button>
          )
        }
      />
      <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
        <Select
          allowClear
          value={forwarderId}
          onChange={(v) => {
            setForwarderId(v);
            setPage(1);
          }}
          placeholder="全部货代"
          options={forwarders.map((f) => ({ value: f.id, label: f.name }))}
          style={{ width: 200 }}
          aria-label="货代"
        />
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Card style={{ padding: 0 }}>
          <Table<Statement>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 1600 }}
            locale={{
              emptyText: '还没有对账单；月底收到货代账单后点「新建对账单」',
            }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t: number) => `共 ${t} 条`,
              onChange: (p: number, s: number) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </Card>
      )}
      <CreateModal
        open={creating}
        forwarders={forwarders}
        onClose={() => setCreating(false)}
        onDone={(s) => {
          setCreating(false);
          load();
          setViewing(s.id);
        }}
      />
      <StatementDrawer
        id={viewing}
        canEdit={canEdit}
        onClose={() => setViewing(undefined)}
        onChanged={load}
      />
    </div>
  );
};

export default Statements;
