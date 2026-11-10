import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
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
  Segmented,
  Select,
  Space,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import { ellipsisRender } from '@/components/EllipsisText';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { auditColumns, SupplierPicker } from '@/pages/purchase/components';
import {
  DISP,
  type DisposeLine,
  RT_STATUS,
  RT_STATUS_TONE,
  returnApi,
  type SalesReturn,
} from '@/pages/sales/after-sales/service';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { AttachmentWall, ReasonModal, WarehousePageTitle } from '../components';
import type { Attachment } from '../service';

const DISP_OPTIONS = [
  { value: DISP.SUPPLIER, label: '退回供应商' },
  { value: DISP.HOLD, label: '转闲置库存' },
  { value: DISP.SCRAP, label: '报废' },
];
const DISP_TONE: Record<number, 'orange' | 'cyan' | 'gray'> = {
  1: 'orange',
  2: 'cyan',
  3: 'gray',
};

// ---------------------------------------------------------------- 验收

const ReceiveModal: React.FC<{
  data?: SalesReturn;
  onClose: () => void;
  onDone: () => void;
}> = ({ data, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [qty, setQty] = useState<
    Record<number, { r: number | null; q: number | null }>
  >({});
  const [note, setNote] = useState('');
  const [files, setFiles] = useState<Attachment[]>([]);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!data) return;
    setDate(dayjs());
    const m: Record<number, { r: number | null; q: number | null }> = {};
    data.items.forEach((i) => {
      m[i.id] = { r: i.quantity, q: i.quantity };
    });
    setQty(m);
    setNote('');
    setFiles([]);
  }, [data]);
  if (!data) return null;
  return (
    <Modal
      open
      onCancel={onClose}
      width={640}
      title={
        <div>
          <div>退货验收 {data.returnNo}</div>
          <div style={{ fontSize: 12, color: palette.mute, fontWeight: 400 }}>
            客户 {data.customerName} · 售后单 {data.asNo}
          </div>
        </div>
      }
      okText="确认验收"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        setBusy(true);
        try {
          await returnApi.receive(data.id, {
            receivedDate: date.format('YYYY-MM-DD'),
            note: note.trim() || undefined,
            items: data.items.map((i) => ({
              itemId: i.id,
              receivedQty: qty[i.id]?.r ?? 0,
              qualifiedQty: qty[i.id]?.q ?? 0,
            })),
            attachmentIds: files.map((f) => f.id),
          });
          message.success('已验收，交采购员处理去向');
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        {data.items.map((i) => {
          const v = qty[i.id] ?? { r: null, q: null };
          const bad = (v.r ?? 0) - (v.q ?? 0);
          return (
            <div
              key={i.id}
              style={{ display: 'flex', gap: 12, alignItems: 'center' }}
            >
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, color: palette.ink }}>
                  {i.model}
                </div>
                <div
                  style={{
                    fontSize: 12,
                    color: bad > 0 ? palette.orange : palette.mute,
                  }}
                >
                  退货 {i.quantity} 个{bad > 0 ? ` · 不良 ${bad} 个` : ''}
                </div>
              </div>
              <InputNumber<number>
                min={0}
                max={i.quantity}
                precision={0}
                value={v.r}
                onChange={(n) =>
                  setQty((s) => ({ ...s, [i.id]: { ...v, r: n } }))
                }
                prefix="实收"
                style={{ width: 130 }}
                aria-label={`${i.model} 实收`}
              />
              <InputNumber<number>
                min={0}
                max={v.r ?? 0}
                precision={0}
                value={v.q}
                onChange={(n) =>
                  setQty((s) => ({ ...s, [i.id]: { ...v, q: n } }))
                }
                prefix="合格"
                style={{ width: 130 }}
                aria-label={`${i.model} 合格`}
              />
            </div>
          );
        })}
        <div
          style={{ display: 'grid', gridTemplateColumns: '180px 1fr', gap: 12 }}
        >
          <DatePicker
            value={date}
            allowClear={false}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(d) => d && setDate(d)}
            aria-label="收货日期"
          />
          <Input
            value={note}
            maxLength={300}
            onChange={(e) => setNote(e.target.value)}
            placeholder="说明（选填），如 1 个外壳烧焦"
            aria-label="说明"
          />
        </div>
        <AttachmentWall
          ownerType="SALES_RETURN"
          value={files}
          onChange={setFiles}
          max={20}
        />
      </Space>
    </Modal>
  );
};

// ---------------------------------------------------------------- 去向

type Row = DisposeLine & { key: number; supplierName?: string };

const DisposeModal: React.FC<{
  data?: SalesReturn;
  onClose: () => void;
  onDone: () => void;
}> = ({ data, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [rows, setRows] = useState<Row[]>([]);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!data) return;
    const out: Row[] = [];
    data.items.forEach((i, n) => {
      const received = i.receivedQty ?? 0;
      const qualified = i.qualifiedQty ?? 0;
      if (received - qualified > 0) {
        out.push({
          key: n * 10 + 1,
          itemId: i.id,
          disposition: DISP.SUPPLIER,
          quantity: received - qualified,
          supplierId: i.supplierId ?? undefined,
          supplierName: i.supplierName,
        });
      }
      if (qualified > 0) {
        out.push({
          key: n * 10 + 2,
          itemId: i.id,
          disposition: DISP.HOLD,
          quantity: qualified,
        });
      }
    });
    setRows(out);
  }, [data]);
  if (!data) return null;
  const set = (key: number, patch: Partial<Row>) =>
    setRows((s) => s.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  const effect = rows.reduce(
    (acc, r) => {
      const item = data.items.find((i) => i.id === r.itemId);
      if (r.disposition === DISP.SUPPLIER) {
        acc.credit += Number(r.supplierRefundCny ?? 0);
        acc.freight += Number(r.freightCny ?? 0);
      } else if (r.disposition === DISP.HOLD) {
        acc.credit += Number(item?.unitCost ?? 0) * (r.quantity ?? 0);
      }
      return acc;
    },
    { credit: 0, freight: 0 },
  );
  return (
    <Modal
      open
      onCancel={onClose}
      width={820}
      title={
        <div>
          <div>处理去向 {data.returnNo}</div>
          <div style={{ fontSize: 12, color: palette.mute, fontWeight: 400 }}>
            处理后不能撤回；数量可以拆到不同去向，合计等于实收
          </div>
        </div>
      }
      okText="确认处理"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        setBusy(true);
        try {
          await returnApi.dispose(
            data.id,
            rows.map(({ key: _k, supplierName: _s, ...r }) => r),
          );
          message.success('退货已处理');
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={12} style={{ width: '100%' }}>
        {data.items.map((i) => (
          <div key={i.id}>
            <div
              style={{
                padding: '8px 12px',
                borderRadius: 10,
                background: palette.accentSoft,
                fontSize: 13,
                marginBottom: 8,
              }}
            >
              <b>{i.model}</b>：实收 {i.receivedQty ?? 0} · 合格{' '}
              {i.qualifiedQty ?? 0} · 不良{' '}
              {(i.receivedQty ?? 0) - (i.qualifiedQty ?? 0)}
              {i.unitCost != null &&
                ` · 原采购成本 ${formatAmount(i.unitCost, 'CNY')} / 个`}
              {i.poNo && ` · ${i.poNo}`}
            </div>
            {rows
              .filter((r) => r.itemId === i.id)
              .map((r) => (
                <div
                  key={r.key}
                  style={{
                    display: 'flex',
                    gap: 8,
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    padding: '8px 0',
                  }}
                >
                  <Select
                    value={r.disposition}
                    onChange={(v) => set(r.key, { disposition: v })}
                    options={DISP_OPTIONS}
                    style={{ width: 130 }}
                    aria-label="去向"
                  />
                  <InputNumber<number>
                    min={0}
                    precision={0}
                    value={r.quantity}
                    onChange={(v) => set(r.key, { quantity: v ?? 0 })}
                    style={{ width: 90 }}
                    aria-label="数量"
                  />
                  {r.disposition === DISP.SUPPLIER && (
                    <>
                      <div style={{ width: 200 }}>
                        <SupplierPicker
                          value={r.supplierId}
                          label={r.supplierName}
                          onChange={(id, name) =>
                            set(r.key, { supplierId: id, supplierName: name })
                          }
                        />
                      </div>
                      <Input
                        value={r.trackingNo}
                        onChange={(e) =>
                          set(r.key, { trackingNo: e.target.value })
                        }
                        placeholder="快递单号"
                        style={{ width: 130 }}
                        aria-label="退回快递单号"
                      />
                      <InputNumber<number>
                        min={0}
                        precision={2}
                        value={r.freightCny ?? null}
                        onChange={(v) =>
                          set(r.key, { freightCny: v ?? undefined })
                        }
                        prefix="运费 ¥"
                        style={{ width: 130 }}
                        aria-label="退回运费"
                      />
                      <InputNumber<number>
                        min={0}
                        precision={2}
                        value={r.supplierRefundCny ?? null}
                        onChange={(v) =>
                          set(r.key, { supplierRefundCny: v ?? undefined })
                        }
                        prefix="供应商退款 ¥"
                        style={{ width: 170 }}
                        aria-label="供应商退款或抵扣"
                      />
                    </>
                  )}
                  {r.disposition === DISP.SCRAP && (
                    <Input
                      value={r.remark}
                      onChange={(e) => set(r.key, { remark: e.target.value })}
                      placeholder="报废原因"
                      style={{ width: 240 }}
                      aria-label="报废原因"
                    />
                  )}
                  <Button
                    type="text"
                    icon={<DeleteOutlined />}
                    aria-label="删除这一行"
                    onClick={() =>
                      setRows((s) => s.filter((x) => x.key !== r.key))
                    }
                  />
                </div>
              ))}
            <Button
              size="small"
              type="dashed"
              icon={<PlusOutlined />}
              onClick={() =>
                setRows((s) => [
                  ...s,
                  {
                    key: Date.now(),
                    itemId: i.id,
                    disposition: DISP.SCRAP,
                    quantity: 0,
                  },
                ])
              }
            >
              再拆一个去向
            </Button>
          </div>
        ))}
        <div
          style={{
            padding: '12px 14px',
            borderRadius: 12,
            background: palette.greenSoft,
            fontSize: 13,
          }}
        >
          原订单 {data.soNo} 成本：冲减{' '}
          <b>{formatAmount(effect.credit, 'CNY')}</b>
          {effect.freight > 0 &&
            `，退回运费 +${formatAmount(effect.freight, 'CNY')}`}
          <div style={{ fontSize: 12, color: palette.mute }}>
            退回供应商按供应商退款冲减；转闲置按原采购成本冲减；报废不冲减。本期退回供应商只登记金额，不和应付、付款打通。
          </div>
        </div>
      </Space>
    </Modal>
  );
};

// ---------------------------------------------------------------- 页面

/** 仓库管理 → 退货入库（spec warehouse/sales-return） */
const ReturnsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canReceive = !!access['warehouse:return:receive'];
  const canDispose = !!access['purchase:return:dispose'];
  const [status, setStatus] = useState<number | 'all'>(RT_STATUS.PENDING);
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<SalesReturn[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<{ pending: number; received: number }>();
  const [loading, setLoading] = useState(true);
  const [viewing, setViewing] = useState<SalesReturn>();
  const [receiving, setReceiving] = useState<SalesReturn>();
  const [disposing, setDisposing] = useState<SalesReturn>();
  const [unreceiving, setUnreceiving] = useState<SalesReturn>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, c] = await Promise.all([
        returnApi.page({
          page,
          pageSize,
          status: status === 'all' ? undefined : status,
          keyword: query || undefined,
        }),
        returnApi.counts(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, status, query, message]);
  useEffect(() => {
    load();
  }, [load]);

  // 售后单详情点退货单号时带 ?id=
  useEffect(() => {
    const id = Number(new URLSearchParams(window.location.search).get('id'));
    if (id) {
      returnApi
        .detail(id)
        .then(setViewing)
        .catch(() => undefined);
    }
  }, []);

  const open = async (r: SalesReturn, then: (x: SalesReturn) => void) => {
    try {
      then(await returnApi.detail(r.id));
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };
  const done = () => {
    setReceiving(undefined);
    setDisposing(undefined);
    setViewing(undefined);
    load();
  };

  const sub = (t?: React.ReactNode) =>
    t ? <div style={{ fontSize: 12, color: palette.mute }}>{t}</div> : null;

  const columns: TableColumnsType<SalesReturn> = [
    {
      title: '退货单',
      dataIndex: 'returnNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => <a onClick={() => open(r, setViewing)}>{v}</a>,
    },
    {
      title: '售后单 · 订单',
      key: 'as',
      width: 170,
      render: (_, r) => (
        <div>
          <a
            onClick={() => history.push(`/sales/after-sales/${r.afterSalesId}`)}
          >
            {r.asNo}
          </a>
          {sub(r.soNo)}
        </div>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 200,
      render: (v: string, r) => (
        <CustomerCell
          name={v}
          country={r.customerCountry}
          type={r.customerType}
          contact={r.customerContact}
        />
      ),
    },
    {
      title: '型号 · 数量',
      dataIndex: 'itemSummary',
      width: 220,
      render: ellipsisRender,
    },
    {
      title: '快递',
      key: 'express',
      width: 160,
      render: (_, r) => (
        <div>
          {r.carrier || '—'}
          {sub(r.trackingNo)}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 100,
      render: (v: string, r) => (
        <Pill tone={RT_STATUS_TONE[r.status] ?? 'gray'}>{v}</Pill>
      ),
    },
    ...auditColumns<SalesReturn>(),
    {
      title: '操作',
      key: 'op',
      width: 130,
      fixed: 'right',
      render: (_, r) =>
        r.status === RT_STATUS.PENDING && canReceive ? (
          <Button
            size="small"
            type="primary"
            onClick={() => open(r, setReceiving)}
          >
            验收
          </Button>
        ) : r.status === RT_STATUS.RECEIVED ? (
          <Space size={4}>
            {canDispose && (
              <Button size="small" onClick={() => open(r, setDisposing)}>
                处理去向
              </Button>
            )}
            {canReceive && (
              <Button
                size="small"
                type="link"
                onClick={() => setUnreceiving(r)}
              >
                撤回
              </Button>
            )}
          </Space>
        ) : null,
    },
  ];

  return (
    <div>
      <WarehousePageTitle description="客户寄回的货：仓库验收实收与合格数量，采购员处理去向（退回供应商、转闲置库存、报废）。" />
      <Card style={{ padding: 16 }}>
        <div
          style={{
            display: 'flex',
            gap: 12,
            flexWrap: 'wrap',
            marginBottom: 12,
          }}
        >
          <Segmented<number | 'all'>
            value={status}
            onChange={(v) => {
              setStatus(v);
              setPage(1);
            }}
            options={[
              {
                value: RT_STATUS.PENDING,
                label: `待入库 ${counts?.pending ?? ''}`,
              },
              {
                value: RT_STATUS.RECEIVED,
                label: `待处理 ${counts?.received ?? ''}`,
              },
              { value: RT_STATUS.DONE, label: '已处理' },
              { value: RT_STATUS.VOID, label: '已作废' },
              { value: 'all', label: '全部' },
            ]}
          />
          <Input.Search
            allowClear
            placeholder="退货单号、售后单号、订单号、快递单号"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={(v) => {
              setQuery(v.trim());
              setPage(1);
            }}
            style={{ width: 300 }}
            aria-label="关键词"
          />
        </div>
        <Table<SalesReturn>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText:
              status === RT_STATUS.PENDING
                ? '没有待入库的退货；业务员在售后单上发起退货后会出现在这里'
                : '没有符合条件的退货单',
          }}
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
      <ReceiveModal
        data={receiving}
        onClose={() => setReceiving(undefined)}
        onDone={done}
      />
      <DisposeModal
        data={disposing}
        onClose={() => setDisposing(undefined)}
        onDone={done}
      />
      <ReasonModal
        open={!!unreceiving}
        title={`撤回入库 ${unreceiving?.returnNo ?? ''}`}
        description="验收数量录错时撤回，退货单回到待入库后重新验收。"
        okText="撤回"
        onCancel={() => setUnreceiving(undefined)}
        onOk={async (why) => {
          if (!unreceiving) return;
          await returnApi.unreceive(unreceiving.id, why);
          message.success('已撤回入库');
          setUnreceiving(undefined);
          load();
        }}
      />
      <Drawer
        open={!!viewing}
        onClose={() => setViewing(undefined)}
        size="large"
        title={viewing ? `退货单 ${viewing.returnNo}` : ''}
      >
        {viewing && (
          <Space orientation="vertical" size={14} style={{ width: '100%' }}>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <Pill tone={RT_STATUS_TONE[viewing.status] ?? 'gray'}>
                {viewing.statusName}
              </Pill>
              <span style={{ color: palette.sub }}>
                {viewing.customerName} · 售后单 {viewing.asNo} · 订单{' '}
                {viewing.soNo}
              </span>
            </div>
            <div style={{ fontSize: 13, color: palette.sub }}>
              快递 {viewing.carrier || '—'} {viewing.trackingNo}
              {viewing.remark ? ` · ${viewing.remark}` : ''}
            </div>
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={viewing.items}
              columns={[
                { title: '型号', width: 160, dataIndex: 'model' },
                { title: '退货', dataIndex: 'quantity', width: 70 },
                {
                  title: '实收',
                  dataIndex: 'receivedQty',
                  width: 70,
                  render: (v) => v ?? '—',
                },
                {
                  title: '合格',
                  dataIndex: 'qualifiedQty',
                  width: 70,
                  render: (v) => v ?? '—',
                },
              ]}
            />
            {viewing.receivedDate && (
              <div style={{ fontSize: 13, color: palette.sub }}>
                {viewing.receivedDate} 由 {viewing.receivedByName ?? '—'} 验收
                {viewing.receiveNote ? `：${viewing.receiveNote}` : ''}
              </div>
            )}
            {(viewing.attachments ?? []).length > 0 && (
              <AttachmentWall
                ownerType="SALES_RETURN"
                value={viewing.attachments ?? []}
              />
            )}
            {viewing.dispositions.length > 0 && (
              <>
                <b style={{ color: palette.ink }}>去向</b>
                {viewing.dispositions.map((d) => (
                  <div
                    key={d.id}
                    style={{
                      display: 'flex',
                      gap: 8,
                      alignItems: 'center',
                      fontSize: 13,
                    }}
                  >
                    <Pill tone={DISP_TONE[d.disposition] ?? 'gray'}>
                      {d.dispositionName}
                    </Pill>
                    <span>
                      {d.model} × {d.quantity}
                      {d.supplierName ? ` · ${d.supplierName}` : ''}
                      {d.remark ? ` · ${d.remark}` : ''}
                    </span>
                    <span style={{ marginLeft: 'auto', color: palette.green }}>
                      {Number(d.costCny) > 0
                        ? `冲减 ${formatAmount(d.costCny, 'CNY')}`
                        : ''}
                      {Number(d.freightCny) > 0
                        ? ` · 运费 ${formatAmount(d.freightCny, 'CNY')}`
                        : ''}
                    </span>
                  </div>
                ))}
              </>
            )}
            {viewing.status === RT_STATUS.VOID && (
              <div style={{ color: palette.mute }}>
                作废原因：{viewing.voidReason}
              </div>
            )}
          </Space>
        )}
      </Drawer>
    </div>
  );
};

export default ReturnsPage;
