import { InboxOutlined, SearchOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Input, Segmented, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import {
  Card,
  ReceiptStatusPill,
  sub,
  WarehousePageTitle,
} from '../components';
import {
  type Receipt,
  readBizError,
  receiptApi,
  type Shipment,
} from '../service';
import { AcceptDrawer, DirectReceiveDrawer, ReceiptView } from './dialogs';

type Tab = 'pending' | 'received';

const Receipts: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess();
  const canEdit = !!access['warehouse:receipt:create'];
  const [tab, setTab] = useState<Tab>('pending');
  const [pending, setPending] = useState<number>();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [ships, setShips] = useState<Shipment[]>([]);
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [accepting, setAccepting] = useState<number>();
  const [direct, setDirect] = useState(false);
  const [viewing, setViewing] = useState<number>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const q = { keyword: query, page, pageSize };
      if (tab === 'pending') {
        const res = await receiptApi.pending(q);
        setShips(res.records);
        setTotal(res.total);
      } else {
        const res = await receiptApi.page(q);
        setReceipts(res.records);
        setTotal(res.total);
      }
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [tab, query, page, pageSize]);

  const refreshCount = useCallback(() => {
    receiptApi
      .counts()
      .then((c) => setPending(c.pending))
      .catch(() => setPending(undefined));
  }, []);

  useEffect(() => {
    load();
  }, [load]);
  useEffect(refreshCount, [refreshCount]);

  const afterReceive = (receiptId: number) => {
    setAccepting(undefined);
    setDirect(false);
    load();
    refreshCount();
    setViewing(receiptId);
  };

  const shipColumns: TableColumnsType<Shipment> = [
    {
      title: '发货单',
      dataIndex: 'sdNo',
      width: 160,
      render: (v: string, r) => (
        <div>
          <b style={{ color: palette.ink }}>{v}</b>
          {r.source === 2 && sub(palette.orange, r.sourceName)}
        </div>
      ),
    },
    {
      title: '采购单 · 采购对象',
      dataIndex: 'poNo',
      width: 210,
      render: (v: string, r) => (
        <div>
          <span style={{ color: palette.link }}>{v}</span>
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
    { title: '发货日期', dataIndex: 'shipDate', width: 120 },
    {
      title: '型号 · 数量',
      key: 'items',
      width: 260,
      render: (_, r) => (
        <div style={{ fontSize: 13 }}>
          {r.items.map((i) => (
            <div key={i.id}>
              {i.model} × {i.quantity}
            </div>
          ))}
        </div>
      ),
    },
    { title: '采购员', dataIndex: 'purchaserName', width: 90 },
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
      title: '操作',
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (_, r) =>
        canEdit ? <a onClick={() => setAccepting(r.id)}>验收入库</a> : null,
    },
  ];

  const receiptColumns: TableColumnsType<Receipt> = [
    {
      title: '入库单 · 收货日期',
      dataIndex: 'grNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <a onClick={() => setViewing(r.id)}>{v}</a>
          {sub(palette.mute, r.receivedDate ?? '—')}
        </div>
      ),
    },
    { title: '发货单', dataIndex: 'sdNo', width: 150 },
    {
      title: '采购单 · 采购对象',
      dataIndex: 'poNo',
      width: 210,
      render: (v: string, r) => (
        <div>
          <span style={{ color: palette.ink }}>{v}</span>
          {sub(palette.mute, r.supplierName)}
        </div>
      ),
    },
    {
      title: '型号数',
      dataIndex: 'itemCount',
      width: 80,
    },
    {
      title: '合格 · 不良 · 差异',
      key: 'qty',
      width: 170,
      render: (_, r) => (
        <span>
          <span style={{ color: palette.green }}>合格 {r.qualifiedQty}</span>
          {r.defectiveQty > 0 && (
            <span style={{ color: palette.red }}> · 不良 {r.defectiveQty}</span>
          )}
          {r.discrepancyCount > 0 && (
            <span style={{ color: palette.orange }}>
              {' '}
              · {r.discrepancyCount} 条差异
            </span>
          )}
        </span>
      ),
    },
    { title: '收货人', dataIndex: 'receivedByName', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: number, r) => (
        <ReceiptStatusPill value={v}>{r.statusName}</ReceiptStatusPill>
      ),
    },
    ...auditColumns<Receipt>(),
    {
      title: '操作',
      key: 'actions',
      width: 70,
      fixed: 'right',
      render: (_, r) => <a onClick={() => setViewing(r.id)}>查看</a>,
    },
  ];

  const apply = () => {
    setQuery(keyword.trim() || undefined);
    setPage(1);
  };
  const pagination = {
    current: page,
    pageSize,
    total,
    showSizeChanger: true,
    showTotal: (t: number) => `共 ${t} 条`,
    onChange: (p: number, s: number) => {
      setPage(p);
      setPageSize(s);
    },
  };

  return (
    <div>
      <WarehousePageTitle
        crumbs={['入库验收']}
        title="入库验收"
        description="按发货单验收：只记录实收、合格、不良，差异由采购员处理。"
        actions={
          canEdit && (
            <Button icon={<InboxOutlined />} onClick={() => setDirect(true)}>
              直接收货
            </Button>
          )
        }
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
          }}
          options={[
            {
              value: 'pending',
              label: pending ? `待收货 · ${pending}` : '待收货',
            },
            { value: 'received', label: '已入库' },
          ]}
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={apply}
          placeholder={
            tab === 'pending'
              ? '快递单号、采购单号、型号、采购对象'
              : '入库单号、发货单号、快递单号、采购单号、型号'
          }
          style={{ width: 340 }}
          aria-label="关键词"
        />
        <span style={{ flex: 1 }} />
        <Button type="primary" onClick={apply}>
          查询
        </Button>
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : tab === 'pending' ? (
        <Card style={{ padding: 0 }}>
          <Table<Shipment>
            rowKey="id"
            columns={shipColumns}
            dataSource={ships}
            loading={loading}
            scroll={{ x: 1300 }}
            locale={{
              emptyText: query
                ? '没有匹配的在途发货单；货到了但没有发货单，点右上角「直接收货」'
                : '没有待收货的发货单',
            }}
            pagination={pagination}
          />
        </Card>
      ) : (
        <Table<Receipt>
          rowKey="id"
          columns={receiptColumns}
          dataSource={receipts}
          loading={loading}
          scroll={{ x: 1800 }}
          locale={{ emptyText: '还没有入库单' }}
          pagination={pagination}
        />
      )}
      {tab === 'pending' && (
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
          按发货日期从早到晚；没有发货单的货点「直接收货」，系统补一条发货记录并标「仓库补登」。
        </div>
      )}
      <AcceptDrawer
        shipmentId={accepting}
        onClose={() => setAccepting(undefined)}
        onDone={(d) => afterReceive(d.receipt.id)}
      />
      <DirectReceiveDrawer
        open={direct}
        onClose={() => setDirect(false)}
        onDone={(d) => afterReceive(d.receipt.id)}
      />
      <ReceiptView
        id={viewing}
        onClose={() => setViewing(undefined)}
        onChanged={() => {
          load();
          refreshCount();
        }}
      />
    </div>
  );
};

export default Receipts;
