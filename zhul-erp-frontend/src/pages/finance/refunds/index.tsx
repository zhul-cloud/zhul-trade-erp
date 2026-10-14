import { PlusOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Checkbox,
  DatePicker,
  Input,
  Modal,
  Select,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import { orderApi, readBizError } from '@/pages/sales/service';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { useDictTexts } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { ProofLinks } from '../payables/components';
import RefundModal from './RefundModal';
import {
  DICT_REFUND_REASON,
  REFUND_METHODS,
  type Refund,
  refundApi,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 经营统计跳转时 URL 带的日期范围 */
const rangeFromUrl = (): [Dayjs, Dayjs] | null => {
  const p = new URLSearchParams(window.location.search);
  const from = p.get('dateFrom');
  const to = p.get('dateTo');
  return from && to ? [dayjs(from), dayjs(to)] : null;
};

/** 先选订单再登记退款 */
const PickOrderModal: React.FC<{
  open: boolean;
  onClose: () => void;
  onPick: (soId: number) => void;
}> = ({ open, onClose, onPick }) => {
  const [options, setOptions] = useState<{ value: number; label: string }[]>(
    [],
  );
  const [soId, setSoId] = useState<number>();
  const search = (keyword: string) =>
    orderApi
      .page({ keyword, page: 1, pageSize: 20 })
      .then((res) =>
        setOptions(
          res.records
            .filter((o) => o.status === 1)
            .map((o) => ({
              value: o.id,
              label: `${o.soNo} · ${o.customerName ?? ''} · ${formatAmount(o.totalAmount, o.currencyCode)}`,
            })),
        ),
      )
      .catch(() => setOptions([]));
  useEffect(() => {
    if (open) {
      setSoId(undefined);
      search('');
    }
  }, [open]);
  return (
    <Modal
      open={open}
      title="选择要退款的订单"
      okText="下一步"
      cancelText="取消"
      okButtonProps={{ disabled: !soId }}
      onOk={() => soId && onPick(soId)}
      onCancel={onClose}
      destroyOnHidden
    >
      <Select
        style={{ width: '100%' }}
        placeholder="按订单号或客户搜索"
        value={soId}
        onChange={setSoId}
        options={options}
        showSearch={{ filterOption: false, onSearch: search }}
      />
    </Modal>
  );
};

/** 财务管理 → 退款单：给客户的退款，冲减订单毛利 */
const RefundPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['finance:refund:edit'];
  const reasons = useDictTexts(DICT_REFUND_REASON);
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [reason, setReason] = useState<string>();
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(rangeFromUrl);
  const [showVoid, setShowVoid] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Refund[]>([]);
  const [total, setTotal] = useState(0);
  const [sum, setSum] = useState(0);
  const [loading, setLoading] = useState(true);
  const [picking, setPicking] = useState(false);
  const [soId, setSoId] = useState<number>();
  const [voiding, setVoiding] = useState<Refund>();

  const load = useCallback(async () => {
    setLoading(true);
    const q = {
      keyword: query || undefined,
      reason,
      dateFrom: range?.[0].format('YYYY-MM-DD'),
      dateTo: range?.[1].format('YYYY-MM-DD'),
      showVoid: showVoid || undefined,
    };
    try {
      const [res, t] = await Promise.all([
        refundApi.page({ ...q, page, pageSize }),
        refundApi.total(q),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setSum(t);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, reason, range, showVoid, page, pageSize, message]);

  useEffect(() => {
    load();
  }, [load]);

  const columns: TableColumnsType<Refund> = [
    {
      title: '编号',
      dataIndex: 'refundNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => (
        <span
          style={{
            textDecoration: r.status === 2 ? 'line-through' : undefined,
          }}
        >
          {v}
        </span>
      ),
    },
    { title: '退款日期', dataIndex: 'refundDate', width: 110 },
    {
      title: '订单 · 客户',
      key: 'so',
      width: 240,
      render: (_, r) => (
        <div>
          <a onClick={() => history.push(`/sales/orders/${r.soId}`)}>
            {r.soNo}
          </a>
          <div style={{ fontSize: 12, color: palette.sub }}>
            {r.customerName}
          </div>
        </div>
      ),
    },
    { title: '原因', dataIndex: 'reasonName', width: 120 },
    {
      title: '金额',
      key: 'amount',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.amount, r.currencyCode)}</span>
      ),
    },
    {
      title: '人民币',
      dataIndex: 'amountCny',
      width: 120,
      align: 'right',
      render: (v: number) => <b style={num}>{formatAmount(v, 'CNY')}</b>,
    },
    {
      title: '退款方式',
      dataIndex: 'method',
      width: 120,
      render: (v: number) => REFUND_METHODS.find((m) => m.value === v)?.label,
    },
    {
      title: '凭证',
      key: 'files',
      width: 90,
      render: (_, r) => <ProofLinks files={r.attachments} />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (v: number, r) =>
        v === 2 ? (
          <div>
            <Pill tone="mute">已作废</Pill>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.voidReason}
            </div>
          </div>
        ) : (
          <Pill tone="green">有效</Pill>
        ),
    },
    ...auditColumns<Refund>(),
    {
      title: '操作',
      key: 'op',
      width: 90,
      fixed: 'right',
      render: (_, r) =>
        canEdit && r.status === 1 ? (
          <a onClick={() => setVoiding(r)}>作废</a>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['退款单']}
        title="退款单"
        description="给客户的退款：关联订单，冲减订单毛利；经营统计按退款月份单列一行。"
        actions={
          canEdit && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setPicking(true)}
            >
              登记退款
            </Button>
          )
        }
      />
      <div
        style={{
          display: 'inline-block',
          padding: '16px 18px',
          borderRadius: 14,
          background: palette.card,
          border: `1px solid ${palette.hairline}`,
          marginBottom: 16,
          minWidth: 240,
        }}
      >
        <div style={{ fontSize: 13, color: palette.sub }}>
          退款合计（人民币）
        </div>
        <div
          style={{
            ...num,
            fontSize: 22,
            fontWeight: 800,
            color: palette.orange,
            marginTop: 4,
          }}
        >
          {formatAmount(sum, 'CNY')}
        </div>
      </div>
      <Card>
        <div
          style={{
            display: 'flex',
            gap: 10,
            flexWrap: 'wrap',
            marginBottom: 14,
            alignItems: 'center',
          }}
        >
          <Input
            allowClear
            placeholder="编号、订单号、客户"
            style={{ width: 200 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={() => {
              setQuery(keyword.trim());
              setPage(1);
            }}
          />
          <DatePicker.RangePicker
            value={range}
            onChange={(v) => {
              setRange(v?.[0] && v[1] ? [v[0], v[1]] : null);
              setPage(1);
            }}
          />
          <Select
            allowClear
            placeholder="原因"
            style={{ width: 140 }}
            value={reason}
            onChange={(v) => {
              setReason(v);
              setPage(1);
            }}
            options={reasons.map((r) => ({ value: r.code, label: r.name }))}
          />
          <Checkbox
            checked={showVoid}
            onChange={(e) => setShowVoid(e.target.checked)}
          >
            显示已作废
          </Checkbox>
          <Button
            onClick={() => {
              setQuery(keyword.trim());
              setPage(1);
            }}
          >
            查询
          </Button>
        </div>
        <Table<Refund>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1800 }}
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
      <PickOrderModal
        open={picking}
        onClose={() => setPicking(false)}
        onPick={(id) => {
          setPicking(false);
          setSoId(id);
        }}
      />
      <RefundModal
        open={!!soId}
        soId={soId}
        onClose={() => setSoId(undefined)}
        onDone={load}
      />
      <ReasonModal
        open={!!voiding}
        title={`作废 ${voiding?.refundNo ?? ''}`}
        description="作废后不再冲减订单毛利，也不再计入经营统计。"
        okText="作废"
        danger
        onCancel={() => setVoiding(undefined)}
        onOk={async (why) => {
          if (!voiding) return;
          await refundApi.voidRefund(voiding.id, why);
          message.success('已作废');
          setVoiding(undefined);
          load();
        }}
      />
    </div>
  );
};

export default RefundPage;
