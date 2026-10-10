import { SearchOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  AutoComplete,
  Button,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Space,
  Table,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { CARRIERS } from '@/pages/purchase/shipments/ShipmentDrawer';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { HoldStatusPill, sub, WarehousePageTitle } from '../components';
import { type Hold, holdApi, readBizError } from '../service';

const ACTIONS: Record<number, { label: string; hint: string }> = {
  2: { label: '退回', hint: '寄回供应商，登记快递单号与运费' },
  3: { label: '报废', hint: '不能用了，直接报废' },
  4: { label: '转样品', hint: '留作样品或展示' },
};

const Holds: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess();
  const canHandle = !!access['warehouse:hold:handle'];
  const [status, setStatus] = useState<number | 'all'>(1);
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Hold[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [handling, setHandling] = useState<{ hold: Hold; status: number }>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await holdApi.page({
        keyword: query,
        status: status === 'all' ? undefined : status,
        page,
        pageSize,
      });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, status, page, pageSize]);
  useEffect(() => {
    load();
  }, [load]);

  const columns: TableColumnsType<Hold> = [
    {
      title: '型号 · 品牌 · 品类',
      dataIndex: 'model',
      width: 200,
      fixed: 'left',
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
    { title: '数量', dataIndex: 'quantity', width: 70 },
    {
      title: '成本单价',
      dataIndex: 'costPrice',
      width: 120,
      render: (v: number) => (
        <div>
          <b style={{ color: palette.ink }}>{formatAmount(v, 'CNY')}</b>
          {Number(v) === 0 && sub(palette.mute, '供应商白送')}
        </div>
      ),
    },
    {
      title: '来源',
      dataIndex: 'poNo',
      width: 200,
      render: (v: string, r) => (
        <div>
          <span style={{ color: palette.link }}>{v}</span>
          {sub(
            palette.mute,
            `${r.grNo ?? ''} · 多发 · ${r.supplierName ?? ''}`,
          )}
        </div>
      ),
    },
    {
      title: '位置',
      dataIndex: 'locationNote',
      width: 120,
      render: (v?: string) =>
        v || <span style={{ color: palette.mute }}>—</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 200,
      render: (v: number, r) => (
        <div>
          <HoldStatusPill value={v}>{r.statusName}</HoldStatusPill>
          {r.handleNote &&
            sub(
              palette.mute,
              `${r.handledByName ?? ''}：${r.handleNote}${
                r.returnTrackingNo
                  ? `（${[r.returnCarrier, r.returnTrackingNo].filter(Boolean).join(' ')}）`
                  : ''
              }`,
            )}
        </div>
      ),
    },
    ...auditColumns<Hold>(),
    {
      title: '操作',
      key: 'actions',
      width: 170,
      fixed: 'right',
      render: (_, r) =>
        r.status === 1 && canHandle ? (
          <Space size={12}>
            {[2, 3, 4].map((s) => (
              <a key={s} onClick={() => setHandling({ hold: r, status: s })}>
                {ACTIONS[s].label}
              </a>
            ))}
          </Space>
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
        crumbs={['暂存货']}
        title="暂存货"
        description="多发且没有退回的货：记下在哪、值多少；可以退回供应商、报废或转样品。"
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
        <Segmented<number | 'all'>
          value={status}
          onChange={(v) => {
            setStatus(v);
            setPage(1);
          }}
          options={[
            { value: 1, label: '暂存中' },
            { value: 2, label: '已退回' },
            { value: 3, label: '已报废' },
            { value: 4, label: '已转样品' },
            { value: 'all', label: '全部' },
          ]}
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={apply}
          placeholder="型号、来源采购单"
          style={{ width: 280 }}
          aria-label="关键词"
        />
        <span style={{ flex: 1 }} />
        <Button type="primary" onClick={apply}>
          查询
        </Button>
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<Hold>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 1700 }}
          locale={{
            emptyText:
              status === 1
                ? '没有暂存中的货；采购员处理多发时选「暂存」会出现在这里'
                : '没有符合条件的暂存货',
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
        「用到其他订单」放到第③期，与成本一起做；处置后不能撤回。
      </div>
      <HandleHoldModal
        target={handling}
        onClose={() => setHandling(undefined)}
        onDone={() => {
          setHandling(undefined);
          load();
        }}
      />
    </div>
  );
};

const HandleHoldModal: React.FC<{
  target?: { hold: Hold; status: number };
  onClose: () => void;
  onDone: () => void;
}> = ({ target, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [note, setNote] = useState('');
  const [carrier, setCarrier] = useState('');
  const [trackingNo, setTrackingNo] = useState('');
  const [freight, setFreight] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!target) return;
    setNote('');
    setCarrier('');
    setTrackingNo('');
    setFreight(null);
  }, [target]);
  if (!target) return null;
  const a = ACTIONS[target.status];
  const h = target.hold;
  return (
    <Modal
      open
      title={`${a.label} · ${h.model} × ${h.quantity}`}
      okText={a.label}
      okButtonProps={{ disabled: !note.trim(), loading: busy }}
      onCancel={onClose}
      onOk={async () => {
        setBusy(true);
        try {
          await holdApi.handle(h.id, {
            status: target.status,
            note: note.trim(),
            returnCarrier: carrier.trim(),
            returnTrackingNo: trackingNo.trim(),
            returnFreight: freight,
          });
          message.success(`已${a.label}`);
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <div style={{ color: palette.sub, marginBottom: 12 }}>
        {a.hint}；处置后不能撤回。
      </div>
      {target.status === 2 && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr 140px',
            gap: 10,
            marginBottom: 12,
          }}
        >
          <AutoComplete
            options={CARRIERS}
            value={carrier}
            onChange={setCarrier}
            placeholder="快递公司"
            aria-label="快递公司"
          />
          <Input
            maxLength={64}
            value={trackingNo}
            onChange={(e) => setTrackingNo(e.target.value)}
            placeholder="快递单号"
            aria-label="快递单号"
          />
          <InputNumber
            min={0}
            precision={2}
            prefix="¥"
            value={freight}
            onChange={setFreight}
            placeholder="运费"
            style={{ width: '100%' }}
            aria-label="运费"
          />
        </div>
      )}
      <Input.TextArea
        rows={3}
        maxLength={300}
        showCount
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder={target.status === 4 ? '如：给业务员做展示' : '请填写说明'}
        aria-label="说明"
      />
    </Modal>
  );
};

export default Holds;
