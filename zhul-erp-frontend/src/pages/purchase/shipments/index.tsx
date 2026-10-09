import { SearchOutlined } from '@ant-design/icons';
import { history, useAccess, useLocation } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Drawer,
  Input,
  Segmented,
  Select,
  Skeleton,
  Space,
  Table,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import {
  ArrivalCell,
  AttachmentWall,
  Card,
  DiffStatusPill,
  DiffTypePill,
  ReasonModal,
  ShipStatusPill,
  sub,
  trackingText,
  WAREHOUSE_PATHS,
  WarehousePageTitle,
} from '@/pages/warehouse/components';
import {
  type Discrepancy,
  discrepancyApi,
  readBizError,
  type Shipment,
  type ShipmentDetail,
  shipmentApi,
} from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import HandleDiscrepancyModal from './HandleDiscrepancyModal';
import ShipmentDrawer from './ShipmentDrawer';

type Tab = 'shipments' | 'diffs';

const SupplierShipments: React.FC = () => {
  const { search } = useLocation();
  const [tab, setTab] = useState<Tab>(
    new URLSearchParams(search).get('tab') === 'diffs' ? 'diffs' : 'shipments',
  );
  const [pending, setPending] = useState<number>();
  const refreshCount = useCallback(() => {
    discrepancyApi
      .counts()
      .then((c) => setPending(c.pending))
      .catch(() => setPending(undefined));
  }, []);
  useEffect(refreshCount, [refreshCount]);

  return (
    <div>
      <WarehousePageTitle
        crumbs={['供应商发货']}
        title="供应商发货"
        description="登记供应商发出的货；仓库验收后发现的少发、不良、多发在这里处理。"
      />
      <Segmented<Tab>
        value={tab}
        onChange={setTab}
        style={{ marginBottom: 16 }}
        options={[
          { value: 'shipments', label: '发货记录' },
          {
            value: 'diffs',
            label: pending ? `到货差异 · 待处理 ${pending}` : '到货差异',
          },
        ]}
      />
      {tab === 'shipments' ? (
        <ShipmentsTab />
      ) : (
        <DiffsTab onChanged={refreshCount} />
      )}
    </div>
  );
};

// ---------------------------------------------------------------- 发货记录

const ShipmentsTab: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess();
  const canShip = !!access['purchase:shipment:create'];
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<number>();
  const [query, setQuery] = useState<{ keyword?: string; status?: number }>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Shipment[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [viewing, setViewing] = useState<number>();
  const [editing, setEditing] = useState<number>();
  const [voiding, setVoiding] = useState<Shipment>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await shipmentApi.page({ ...query, page, pageSize });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, page, pageSize]);
  useEffect(() => {
    load();
  }, [load]);
  const closeEdit = useCallback(() => setEditing(undefined), []);

  const columns: TableColumnsType<Shipment> = [
    {
      title: '发货单 · 发货日期',
      dataIndex: 'sdNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <a onClick={() => setViewing(r.id)}>{v}</a>
          {sub(palette.mute, r.shipDate ?? '—')}
        </div>
      ),
    },
    {
      title: '采购单 · 采购对象',
      dataIndex: 'poNo',
      width: 210,
      render: (v: string, r) => (
        <div>
          <a onClick={() => history.push(WAREHOUSE_PATHS.order(r.poId))}>{v}</a>
          {sub(palette.mute, r.supplierName)}
        </div>
      ),
    },
    {
      title: '快递 · 单号',
      key: 'tracking',
      width: 200,
      render: (_, r) => (
        <div>
          <b style={{ color: palette.ink }}>{r.carrier || '—'}</b>
          {r.trackingNo && sub(palette.mute, r.trackingNo)}
        </div>
      ),
    },
    {
      title: '预计到货',
      dataIndex: 'expectedArrivalDate',
      width: 120,
      render: (v: string | undefined, r) => (
        <ArrivalCell date={v} overdue={r.arrivalOverdue} />
      ),
    },
    {
      title: '型号 · 数量',
      key: 'items',
      width: 240,
      render: (_, r) => (
        <div style={{ fontSize: 13 }}>
          {r.items.slice(0, 3).map((i) => (
            <div key={i.id}>
              {i.model} × {i.quantity}
            </div>
          ))}
          {r.items.length > 3 &&
            sub(
              palette.mute,
              `等 ${r.itemCount} 个型号，共 ${r.totalQuantity} 件`,
            )}
        </div>
      ),
    },
    {
      title: '附件',
      dataIndex: 'attachmentCount',
      width: 70,
      render: (v: number) =>
        v ? (
          <span style={{ color: palette.link }}>{v}</span>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '来源',
      dataIndex: 'sourceName',
      width: 100,
      render: (v: string, r) => (
        <div>
          <span
            style={{ color: r.source === 2 ? palette.orange : palette.sub }}
          >
            {v}
          </span>
          {r.directForwarderName &&
            sub(palette.violet, `直发 ${r.directForwarderName}`)}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 140,
      render: (v: number, r) => (
        <div>
          <ShipStatusPill value={v}>{r.statusName}</ShipStatusPill>
          {r.grNo && sub(palette.mute, r.grNo)}
          {r.voidReason && sub(palette.mute, r.voidReason)}
        </div>
      ),
    },
    { title: '采购员', dataIndex: 'purchaserName', width: 90 },
    ...auditColumns<Shipment>(),
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_, r) => (
        <Space size={12}>
          <a onClick={() => setViewing(r.id)}>查看</a>
          {r.status === 1 && canShip && (
            <>
              <a onClick={() => setEditing(r.id)}>修改</a>
              <a style={{ color: palette.red }} onClick={() => setVoiding(r)}>
                作废
              </a>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Card style={{ marginBottom: 16, padding: 20 }}>
        <div
          style={{
            display: 'flex',
            gap: 12,
            flexWrap: 'wrap',
            alignItems: 'center',
          }}
        >
          <Input
            allowClear
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={() => {
              setQuery({ keyword: keyword.trim() || undefined, status });
              setPage(1);
            }}
            placeholder="发货单号、采购单号、快递单号、型号、采购对象"
            style={{ width: 360 }}
            aria-label="关键词"
          />
          <Select
            allowClear
            placeholder="全部状态"
            value={status}
            onChange={setStatus}
            style={{ width: 140 }}
            options={[
              { value: 1, label: '在途' },
              { value: 2, label: '已入库' },
              { value: 3, label: '已作废' },
            ]}
            aria-label="状态"
          />
          <span style={{ flex: 1 }} />
          <Button
            type="primary"
            onClick={() => {
              setQuery({ keyword: keyword.trim() || undefined, status });
              setPage(1);
            }}
          >
            查询
          </Button>
        </div>
      </Card>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<Shipment>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 2000 }}
          locale={{
            emptyText: '还没有发货记录；在采购单详情点「登记发货」',
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
      )}
      <ShipmentView id={viewing} onClose={() => setViewing(undefined)} />
      <ShipmentDrawer
        open={editing !== undefined}
        shipmentId={editing}
        onClose={closeEdit}
        onSaved={() => {
          setEditing(undefined);
          load();
        }}
      />
      <ReasonModal
        open={!!voiding}
        title={`作废发货单 ${voiding?.sdNo ?? ''}`}
        description="作废后这批数量回到未发；已入库的发货单不能作废。"
        placeholder="如：供应商发错单号、重复登记"
        okText="作废"
        danger
        onCancel={() => setVoiding(undefined)}
        onOk={async (reason) => {
          if (!voiding) return;
          try {
            await shipmentApi.void(voiding.id, reason);
            setVoiding(undefined);
            message.success('发货单已作废');
            load();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </>
  );
};

/** 发货单详情：型号、快递、图片与视频 */
export const ShipmentView: React.FC<{
  id?: number;
  load?: (id: number) => Promise<ShipmentDetail>;
  onClose: () => void;
}> = ({ id, load = shipmentApi.detail, onClose }) => {
  const { palette } = useAppTheme();
  const [d, setD] = useState<ShipmentDetail>();
  const [error, setError] = useState<string>();
  useEffect(() => {
    if (id === undefined) return;
    setD(undefined);
    setError(undefined);
    load(id)
      .then(setD)
      .catch((e) => setError(readBizError(e).message));
  }, [id, load]);
  const s = d?.shipment;
  return (
    <Drawer
      open={id !== undefined}
      onClose={onClose}
      size="min(640px, 96vw)"
      title={s ? `发货单 ${s.sdNo}` : '发货单'}
      destroyOnHidden
    >
      {error ? (
        <ErrorHint message={error} onRetry={onClose} />
      ) : !s || !d ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          <div
            style={{
              display: 'flex',
              gap: 8,
              alignItems: 'center',
              flexWrap: 'wrap',
            }}
          >
            <ShipStatusPill value={s.status}>{s.statusName}</ShipStatusPill>
            <span style={{ color: palette.sub }}>
              {s.poNo} · {s.supplierName} · {s.sourceName}
              {s.directForwarderName && ` · 直发货代 ${s.directForwarderName}`}
            </span>
          </div>
          <div style={{ color: palette.sub }}>
            {trackingText(s.carrier, s.trackingNo)} · 发货日期{' '}
            {s.shipDate ?? '—'}
            {s.expectedArrivalDate && ` · 预计 ${s.expectedArrivalDate} 到`}
            {s.note && (
              <div style={{ color: palette.mute }}>备注：{s.note}</div>
            )}
            {s.voidReason && (
              <div style={{ color: palette.mute }}>
                作废原因：{s.voidReason}
              </div>
            )}
          </div>
          <Table
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={s.items}
            columns={[
              {
                title: '型号',
                dataIndex: 'model',
                render: (v: string, r) => (
                  <div>
                    <b>{v}</b>
                    {sub(
                      palette.mute,
                      [r.brand, r.category].filter(Boolean).join(' · '),
                    )}
                  </div>
                ),
              },
              { title: '发货数量', dataIndex: 'quantity', width: 100 },
            ]}
          />
          <div>
            <b
              style={{ color: palette.ink, display: 'block', marginBottom: 8 }}
            >
              发货图片与视频
            </b>
            <AttachmentWall ownerType="SHIPMENT" value={d.attachments} />
          </div>
        </div>
      )}
    </Drawer>
  );
};

// ---------------------------------------------------------------- 到货差异

const DiffsTab: React.FC<{ onChanged: () => void }> = ({ onChanged }) => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess();
  const canHandle = !!access['purchase:discrepancy:handle'];
  const [keyword, setKeyword] = useState('');
  const [type, setType] = useState<number>();
  const [status, setStatus] = useState<number | undefined>(1);
  const [query, setQuery] = useState<{
    keyword?: string;
    type?: number;
    status?: number;
  }>({
    status: 1,
  });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Discrepancy[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [handling, setHandling] = useState<Discrepancy>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await discrepancyApi.page({ ...query, page, pageSize });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, page, pageSize]);
  useEffect(() => {
    load();
  }, [load]);

  const apply = () => {
    setQuery({ keyword: keyword.trim() || undefined, type, status });
    setPage(1);
  };

  const resolutionDetail = (r: Discrepancy) => {
    const parts: string[] = [];
    if (r.discountAmount != null)
      parts.push(
        `折价 ${formatAmount(r.discountAmount, r.currencyCode ?? 'CNY')}`,
      );
    const t = [r.returnCarrier, r.returnTrackingNo].filter(Boolean).join(' ');
    if (t) parts.push(`退货 ${t}`);
    if (r.returnFreight != null)
      parts.push(`运费 ${formatAmount(r.returnFreight, 'CNY')}`);
    if (r.resolution === 7)
      parts.push(r.freeOfCharge ? '供应商白送，成本 ¥0' : '按采购价计成本');
    if (r.note) parts.push(r.note);
    return parts.join('；');
  };

  const columns: TableColumnsType<Discrepancy> = [
    {
      title: '入库单 · 日期',
      dataIndex: 'grNo',
      width: 160,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <b style={{ color: palette.ink }}>{v}</b>
          {sub(palette.mute, r.receivedDate ?? '—')}
        </div>
      ),
    },
    {
      title: '采购单 · 采购对象',
      dataIndex: 'poNo',
      width: 200,
      render: (v: string, r) => (
        <div>
          <a onClick={() => history.push(WAREHOUSE_PATHS.order(r.poId))}>{v}</a>
          {sub(palette.mute, r.supplierName)}
        </div>
      ),
    },
    {
      title: '型号',
      dataIndex: 'model',
      width: 190,
      render: (v: string, r) => (
        <div>
          <b style={{ color: palette.ink }}>{v}</b>
          {sub(
            palette.mute,
            [r.brand, r.category].filter(Boolean).join(' · ') || '—',
          )}
        </div>
      ),
    },
    {
      title: '差异',
      dataIndex: 'type',
      width: 100,
      render: (v: number, r) => (
        <DiffTypePill value={v}>
          {r.typeName} {r.quantity}
        </DiffTypePill>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number, r) => (
        <DiffStatusPill value={v}>{r.statusName}</DiffStatusPill>
      ),
    },
    {
      title: '处理方式',
      dataIndex: 'resolutionName',
      width: 240,
      render: (v: string | undefined, r) =>
        v ? (
          <div>
            <b style={{ color: palette.ink }}>{v}</b>
            {resolutionDetail(r) && sub(palette.mute, resolutionDetail(r))}
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    { title: '采购员', dataIndex: 'purchaserName', width: 90 },
    ...auditColumns<Discrepancy>(),
    {
      title: '操作',
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (_, r) =>
        !canHandle ? null : r.status === 1 ? (
          <a onClick={() => setHandling(r)}>处理</a>
        ) : (
          <a
            onClick={() =>
              modal.confirm({
                title: `重新打开「${r.model} ${r.typeName} ${r.quantity}」？`,
                content: `撤销「${r.resolutionName}」：采购单数量、暂存货与订单进度恢复到处理前，差异回到待处理。`,
                okText: '重新打开',
                onOk: async () => {
                  try {
                    await discrepancyApi.reopen(r.id);
                    message.success('已重新打开');
                    load();
                    onChanged();
                  } catch (e) {
                    message.error(readBizError(e).message);
                  }
                },
              })
            }
          >
            重新打开
          </a>
        ),
    },
  ];

  return (
    <>
      <Card style={{ marginBottom: 16, padding: 20 }}>
        <div
          style={{
            display: 'flex',
            gap: 12,
            flexWrap: 'wrap',
            alignItems: 'center',
          }}
        >
          <Select
            allowClear
            placeholder="全部类型"
            value={type}
            onChange={setType}
            style={{ width: 130 }}
            options={[
              { value: 1, label: '少发' },
              { value: 2, label: '不良' },
              { value: 3, label: '多发' },
            ]}
            aria-label="差异类型"
          />
          <Select
            allowClear
            placeholder="全部状态"
            value={status}
            onChange={setStatus}
            style={{ width: 130 }}
            options={[
              { value: 1, label: '待处理' },
              { value: 2, label: '已处理' },
            ]}
            aria-label="状态"
          />
          <Input
            allowClear
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={apply}
            placeholder="入库单、采购单、型号、采购对象"
            style={{ width: 300 }}
            aria-label="关键词"
          />
          <span style={{ flex: 1 }} />
          <Button type="primary" onClick={apply}>
            查询
          </Button>
        </div>
      </Card>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<Discrepancy>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 1900 }}
          locale={{
            emptyText:
              query.status === 1
                ? '没有待处理的到货差异'
                : '没有符合条件的到货差异',
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
      )}
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        仓库只记录实收、合格、不良；怎么处理由采购员和供应商沟通后决定。
      </div>
      <HandleDiscrepancyModal
        target={handling}
        onClose={() => setHandling(undefined)}
        onDone={() => {
          setHandling(undefined);
          load();
          onChanged();
        }}
      />
    </>
  );
};

export default SupplierShipments;
