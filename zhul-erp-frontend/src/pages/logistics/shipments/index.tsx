import { InboxOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { history, Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Checkbox, Input, Select, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { kg, LOGISTICS_PATHS, ShStatusPill } from '../components';
import {
  type Forwarder,
  forwarderApi,
  type Logistics,
  logisticsApi,
  type PendingGroup,
  readBizError,
} from '../service';

const sub = (color: string, text: React.ReactNode) => (
  <div style={{ fontSize: 12, color }}>{text}</div>
);

/** 待出运的货：按客户 + 货代分组，勾选后新建出运单 */
const PendingPanel: React.FC<{ canEdit: boolean; version: number }> = ({
  canEdit,
  version,
}) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [groups, setGroups] = useState<PendingGroup[]>([]);
  /** key: ob-{id} / sd-{id} */
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState<string>();

  useEffect(() => {
    logisticsApi
      .pending()
      .then((g) => {
        setGroups(g);
        setPicked(
          new Set(
            g.flatMap((x) => [
              ...x.outbounds.map((o) => `ob-${o.id}`),
              ...x.directs.map((d) => `sd-${d.id}`),
            ]),
          ),
        );
      })
      .catch(() => setGroups([]));
  }, [version]);

  if (groups.length === 0) return null;

  const toggle = (key: string, on: boolean) =>
    setPicked((s) => {
      const n = new Set(s);
      if (on) n.add(key);
      else n.delete(key);
      return n;
    });

  const create = async (g: PendingGroup) => {
    const outboundIds = g.outbounds
      .filter((o) => picked.has(`ob-${o.id}`))
      .map((o) => o.id);
    const directShipmentIds = g.directs
      .filter((d) => picked.has(`sd-${d.id}`))
      .map((d) => d.id);
    const key = `${g.customerId}-${g.forwarderId}`;
    setBusy(key);
    try {
      const sh = await logisticsApi.create({
        customerId: g.customerId,
        forwarderId: g.forwarderId,
        outboundIds,
        directShipmentIds,
      });
      message.success(`已新建出运单 ${sh.shNo}`);
      history.push(`${LOGISTICS_PATHS.shipments}/${sh.id}`);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(undefined);
    }
  };

  return (
    <Card style={{ padding: 18, marginBottom: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <InboxOutlined style={{ color: palette.link }} />
        <b style={{ color: palette.ink }}>
          待出运的货（已交货代，还没放进出运单）
        </b>
        <span style={{ flex: 1 }} />
        <span style={{ fontSize: 12, color: palette.mute }}>
          按客户分组，勾选后新建出运单
        </span>
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(460px, 1fr))',
          gap: 12,
        }}
      >
        {groups.map((g) => {
          const key = `${g.customerId}-${g.forwarderId}`;
          const keys = [
            ...g.outbounds.map((o) => `ob-${o.id}`),
            ...g.directs.map((d) => `sd-${d.id}`),
          ];
          const n = keys.filter((k) => picked.has(k)).length;
          return (
            <div
              key={key}
              style={{
                padding: 14,
                borderRadius: 10,
                background: palette.inset,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  marginBottom: 8,
                }}
              >
                <Checkbox
                  checked={n === keys.length}
                  indeterminate={n > 0 && n < keys.length}
                  onChange={(e) => {
                    for (const k of keys) toggle(k, e.target.checked);
                  }}
                  aria-label={`全选 ${g.customerName ?? ''}`}
                />
                <b style={{ color: palette.ink }}>{g.customerName}</b>
                <Pill tone="accent">{g.forwarderName}</Pill>
                <span style={{ flex: 1 }} />
                {canEdit && (
                  <Button
                    type="primary"
                    size="small"
                    icon={<PlusOutlined />}
                    disabled={n === 0}
                    loading={busy === key}
                    onClick={() => create(g)}
                  >
                    新建出运单
                  </Button>
                )}
              </div>
              {g.outbounds.map((o) => (
                <div
                  key={o.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    fontSize: 13,
                    padding: '3px 0',
                  }}
                >
                  <Checkbox
                    checked={picked.has(`ob-${o.id}`)}
                    onChange={(e) => toggle(`ob-${o.id}`, e.target.checked)}
                    aria-label={`选择 ${o.obNo}`}
                  />
                  <b style={{ color: palette.ink }}>{o.obNo}</b>
                  <span style={{ color: palette.mute }}>
                    {o.soNo} · {o.boxCount} 箱 · {kg(o.chargeableWeight)}
                  </span>
                  <span style={{ flex: 1 }} />
                  <span style={{ color: palette.sub }}>
                    {[o.courier?.carrier, o.courier?.trackingNo]
                      .filter(Boolean)
                      .join(' ')}
                  </span>
                </div>
              ))}
              {g.directs.map((d) => (
                <div
                  key={d.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    fontSize: 13,
                    padding: '3px 0',
                  }}
                >
                  <Checkbox
                    checked={picked.has(`sd-${d.id}`)}
                    onChange={(e) => toggle(`sd-${d.id}`, e.target.checked)}
                    aria-label={`选择 ${d.sdNo}`}
                  />
                  <b style={{ color: palette.ink }}>{d.sdNo}</b>
                  <span style={{ color: palette.mute }}>
                    {d.soNos.join('、')} · 直发货代 · 待确认实收
                  </span>
                  <span style={{ flex: 1 }} />
                  <span style={{ color: palette.sub }}>
                    {[d.carrier, d.trackingNo].filter(Boolean).join(' ')}
                  </span>
                </div>
              ))}
            </div>
          );
        })}
      </div>
    </Card>
  );
};

const Shipments: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess();
  const canEdit = !!access['logistics:shipment:edit'];
  const [forwarders, setForwarders] = useState<Forwarder[]>([]);
  const [keyword, setKeyword] = useState('');
  const [forwarderId, setForwarderId] = useState<number>();
  const [status, setStatus] = useState<number>();
  const [query, setQuery] = useState<{
    keyword?: string;
    forwarderId?: number;
    status?: number;
  }>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Logistics[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [version, setVersion] = useState(0);

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
      const res = await logisticsApi.page({ page, pageSize, ...query });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, query]);

  useEffect(() => {
    load();
  }, [load]);

  const open = (id: number) =>
    history.push(`${LOGISTICS_PATHS.shipments}/${id}`);

  const columns: TableColumnsType<Logistics> = [
    {
      title: '出运单',
      dataIndex: 'shNo',
      width: 160,
      fixed: 'left',
      render: (v: string, r) => <a onClick={() => open(r.id)}>{v}</a>,
    },
    { title: '客户', dataIndex: 'customerName', width: 200 },
    { title: '货代', dataIndex: 'forwarderName', width: 120 },
    {
      title: '出库单 · 订单',
      key: 'orders',
      width: 230,
      render: (_, r) => (
        <div>
          {r.outboundCount} 张出库单
          <div style={{ fontSize: 12 }}>
            {r.orders.map((o, i) => (
              <React.Fragment key={o.id}>
                {i > 0 && '、'}
                <Link to={LOGISTICS_PATHS.salesOrder(o.id)}>{o.soNo}</Link>
              </React.Fragment>
            ))}
          </div>
        </div>
      ),
    },
    {
      title: '箱数 · 计费重',
      key: 'boxes',
      width: 120,
      render: (_, r) => (
        <div>
          <b style={{ color: palette.ink }}>{r.boxCount} 箱</b>
          {sub(palette.mute, kg(r.chargeableWeight))}
        </div>
      ),
    },
    {
      title: '运单号',
      key: 'waybill',
      width: 160,
      render: (_, r) =>
        r.waybillNo ? (
          <div>
            <b style={{ color: palette.ink }}>{r.carrier}</b>
            {sub(palette.mute, r.waybillNo)}
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '出运日期',
      dataIndex: 'shippedDate',
      width: 110,
      render: (v?: string) =>
        v ?? <span style={{ color: palette.mute }}>—</span>,
    },
    {
      title: '运费',
      dataIndex: 'freight',
      width: 120,
      render: (v?: number | null) =>
        v === null || v === undefined ? '—' : formatAmount(v, 'CNY'),
    },
    {
      title: '对账',
      dataIndex: 'reconciled',
      width: 80,
      render: (v: boolean, r) =>
        r.status !== 2 ? (
          '—'
        ) : v ? (
          <Pill tone="green">已对账</Pill>
        ) : (
          <Pill tone="gray">未对账</Pill>
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number, r) => (
        <ShStatusPill value={v}>{r.statusName}</ShStatusPill>
      ),
    },
    { title: '业务员', dataIndex: 'ownerName', width: 90 },
    ...auditColumns<Logistics>(),
    {
      title: '操作',
      key: 'actions',
      width: 70,
      fixed: 'right',
      render: (_, r) => <a onClick={() => open(r.id)}>查看</a>,
    },
  ];

  const apply = () => {
    setQuery({ keyword: keyword.trim() || undefined, forwarderId, status });
    setPage(1);
    setVersion((v) => v + 1);
  };

  return (
    <div>
      <PageTitle
        crumbs={['出运单']}
        title="出运单"
        description="同一客户、同一货代的货合成一票：生成 CI/PL，货代申报后登记运单号、面单和运费。"
      />
      <PendingPanel canEdit={canEdit} version={version} />
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Input
          allowClear
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={apply}
          placeholder="出运单号、订单号、运单号、客户"
          style={{ width: 300 }}
          aria-label="关键词"
        />
        <Select
          allowClear
          value={forwarderId}
          onChange={setForwarderId}
          placeholder="全部货代"
          options={forwarders.map((f) => ({ value: f.id, label: f.name }))}
          style={{ width: 160 }}
          aria-label="货代"
        />
        <Select
          allowClear
          value={status}
          onChange={setStatus}
          placeholder="全部状态"
          options={[
            { value: 1, label: '待出运' },
            { value: 2, label: '已出运' },
            { value: 3, label: '已作废' },
          ]}
          style={{ width: 130 }}
          aria-label="状态"
        />
        <span style={{ flex: 1 }} />
        <Button type="primary" onClick={apply}>
          查询
        </Button>
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Card style={{ padding: 0 }}>
          <Table<Logistics>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 2100 }}
            locale={{
              emptyText:
                '还没有出运单；货交到货代后，在上面「待出运的货」里新建',
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
    </div>
  );
};

export default Shipments;
