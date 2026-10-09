import { CarOutlined, SearchOutlined } from '@ant-design/icons';
import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Input, Segmented, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { kg, LOGISTICS_PATHS } from '@/pages/logistics/components';
import {
  type Outbound,
  outboundApi,
  readBizError,
} from '@/pages/logistics/service';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { Card, ReasonModal, sub, WarehousePageTitle } from '../components';
import { HandOverModal, OutboundView, PackDrawer } from './dialogs';

type Tab = 'pending' | 'packed' | 'handed';
const STATUS: Record<Tab, number> = { pending: 1, packed: 2, handed: 3 };

const Outbounds: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess();
  const canPack = !!access['warehouse:outbound:pack'];
  const [tab, setTab] = useState<Tab>('pending');
  const [counts, setCounts] = useState<{ pending: number; packed: number }>();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Outbound[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [packing, setPacking] = useState<number>();
  const [selected, setSelected] = useState<Outbound[]>([]);
  const [handing, setHanding] = useState(false);
  const [viewing, setViewing] = useState<number>();
  const [undoing, setUndoing] = useState<Outbound>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await outboundApi.page({
        page,
        pageSize,
        keyword: query,
        status: STATUS[tab],
      });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [tab, query, page, pageSize]);

  const refreshCounts = useCallback(() => {
    outboundApi
      .counts()
      .then(setCounts)
      .catch(() => setCounts(undefined));
  }, []);

  useEffect(() => {
    load();
  }, [load]);
  useEffect(refreshCounts, [refreshCounts]);

  const changed = () => {
    load();
    refreshCounts();
  };

  const orderCell = (_: unknown, r: Outbound) => (
    <div>
      <Link to={LOGISTICS_PATHS.salesOrder(r.soId)}>{r.soNo}</Link>
      {sub(palette.mute, r.customerName)}
    </div>
  );
  const itemsCell = (_: unknown, r: Outbound) => (
    <div style={{ fontSize: 13 }}>
      {r.items.map((i) => (
        <div key={i.id}>
          {i.model} × {i.quantity}
        </div>
      ))}
    </div>
  );
  const obCell = (v: string, r: Outbound) => (
    <a onClick={() => setViewing(r.id)}>{v}</a>
  );

  const columns: TableColumnsType<Outbound> =
    tab === 'pending'
      ? [
          { title: '出库单', dataIndex: 'obNo', width: 160, render: obCell },
          { title: '订单 · 客户', key: 'so', width: 220, render: orderCell },
          { title: '货代', dataIndex: 'forwarderName', width: 120 },
          { title: '型号 · 数量', key: 'items', width: 260, render: itemsCell },
          {
            title: '备注',
            dataIndex: 'note',
            width: 160,
            render: (v?: string) =>
              v || <span style={{ color: palette.mute }}>—</span>,
          },
          { title: '业务员', dataIndex: 'ownerName', width: 90 },
          ...auditColumns<Outbound>(),
          {
            title: '操作',
            key: 'actions',
            width: 80,
            fixed: 'right',
            render: (_, r) =>
              canPack ? <a onClick={() => setPacking(r.id)}>打包</a> : null,
          },
        ]
      : tab === 'packed'
        ? [
            { title: '出库单', dataIndex: 'obNo', width: 160, render: obCell },
            { title: '订单 · 客户', key: 'so', width: 220, render: orderCell },
            { title: '货代', dataIndex: 'forwarderName', width: 120 },
            {
              title: '型号 · 数量',
              key: 'items',
              width: 240,
              render: itemsCell,
            },
            { title: '箱数', dataIndex: 'boxCount', width: 70 },
            {
              title: '计费重',
              dataIndex: 'chargeableWeight',
              width: 110,
              render: (v: number) => kg(v),
            },
            {
              title: '打包',
              key: 'packed',
              width: 170,
              render: (_, r) => (
                <div>
                  {r.packedByName}
                  {sub(palette.mute, formatDateTime(r.packedAt))}
                </div>
              ),
            },
            ...auditColumns<Outbound>(),
            {
              title: '操作',
              key: 'actions',
              width: 90,
              fixed: 'right',
              render: (_, r) =>
                canPack ? (
                  <a onClick={() => setPacking(r.id)}>修改装箱</a>
                ) : null,
            },
          ]
        : [
            { title: '出库单', dataIndex: 'obNo', width: 160, render: obCell },
            { title: '订单 · 客户', key: 'so', width: 220, render: orderCell },
            { title: '货代', dataIndex: 'forwarderName', width: 120 },
            { title: '箱数', dataIndex: 'boxCount', width: 70 },
            {
              title: '国内快递',
              key: 'courier',
              width: 200,
              render: (_, r) => (
                <div>
                  <b style={{ color: palette.ink }}>
                    {[r.courier?.carrier, r.courier?.trackingNo]
                      .filter(Boolean)
                      .join(' ') || '—'}
                  </b>
                  {sub(
                    palette.mute,
                    `${r.courier?.sentDate ?? ''}${
                      (r.courier?.outboundCount ?? 0) > 1
                        ? ` · ${r.courier?.outboundCount} 单共用`
                        : ''
                    }`,
                  )}
                </div>
              ),
            },
            {
              title: '分到运费',
              key: 'share',
              width: 120,
              render: (_, r) => formatAmount(r.courier?.share, 'CNY'),
            },
            {
              title: '出运单',
              dataIndex: 'shNo',
              width: 150,
              render: (v?: string) =>
                v || <span style={{ color: palette.mute }}>待出运</span>,
            },
            ...auditColumns<Outbound>(),
            {
              title: '操作',
              key: 'actions',
              width: 110,
              fixed: 'right',
              render: (_, r) =>
                canPack && !r.logisticsId ? (
                  <a onClick={() => setUndoing(r)}>撤销交货代</a>
                ) : null,
            },
          ];

  const apply = () => {
    setQuery(keyword.trim() || undefined);
    setPage(1);
  };

  return (
    <div>
      <WarehousePageTitle
        crumbs={['出库打包']}
        title="出库打包"
        description="按业务员的发货通知打包：逐箱登记尺寸、重量和箱内型号，交国内快递发往深圳货代。"
      />
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Segmented<Tab>
          value={tab}
          onChange={(t) => {
            setTab(t);
            setPage(1);
            setSelected([]);
          }}
          options={[
            {
              value: 'pending',
              label: counts?.pending ? `待打包 · ${counts.pending}` : '待打包',
            },
            {
              value: 'packed',
              label: counts?.packed
                ? `待交快递 · ${counts.packed}`
                : '待交快递',
            },
            { value: 'handed', label: '已交货代' },
          ]}
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={apply}
          placeholder="出库单号、订单号、客户、型号"
          style={{ width: 320 }}
          aria-label="关键词"
        />
        <span style={{ flex: 1 }} />
        {tab === 'packed' && canPack && (
          <Button
            icon={<CarOutlined />}
            disabled={selected.length === 0}
            onClick={() => setHanding(true)}
          >
            交国内快递{selected.length ? ` · ${selected.length}` : ''}
          </Button>
        )}
        <Button type="primary" onClick={apply}>
          查询
        </Button>
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Card style={{ padding: 0 }}>
          <Table<Outbound>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 1700 }}
            rowSelection={
              tab === 'packed' && canPack
                ? {
                    selectedRowKeys: selected.map((r) => r.id),
                    onChange: (_, rs) => setSelected(rs),
                    getCheckboxProps: (r) => ({
                      disabled:
                        selected.length > 0 &&
                        selected[0].forwarderId !== r.forwarderId,
                    }),
                  }
                : undefined
            }
            locale={{
              emptyText:
                tab === 'pending'
                  ? '没有待打包的出库单'
                  : tab === 'packed'
                    ? '没有待交快递的出库单'
                    : '还没有交货代的出库单',
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
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        {tab === 'pending'
          ? '按通知时间从早到晚；一个箱子只装一张出库单的货'
          : tab === 'packed'
            ? '勾选同一家货代的出库单合成一票快递，运费按计费重分摊'
            : '还没放进出运单的可以撤销交货代，回到待交快递'}
      </div>
      <PackDrawer
        id={packing}
        onClose={() => setPacking(undefined)}
        onDone={() => {
          setPacking(undefined);
          changed();
        }}
      />
      <HandOverModal
        open={handing}
        outbounds={selected}
        onClose={() => setHanding(false)}
        onDone={() => {
          setHanding(false);
          setSelected([]);
          changed();
        }}
      />
      <OutboundView id={viewing} onClose={() => setViewing(undefined)} />
      <ReasonModal
        open={!!undoing}
        title={`撤销交货代 · ${undoing?.obNo ?? ''}`}
        description="撤销后出库单回到「待交快递」，订单型号进度随之回退；共用快递的其他出库单重新分摊运费。"
        okText="撤销"
        danger
        onCancel={() => setUndoing(undefined)}
        onOk={async (reason) => {
          if (!undoing) return;
          try {
            await outboundApi.undoHandOver(undoing.id, reason);
            message.success('已撤销交货代');
            setUndoing(undefined);
            changed();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default Outbounds;
