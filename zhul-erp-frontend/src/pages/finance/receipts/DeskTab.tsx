import {
  BankOutlined,
  FileTextOutlined,
  PaperClipOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Form, Input, Segmented, Select, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  Card,
  PATHS,
  RECEIPT_META,
  ReceiptStatusPill,
} from '@/pages/sales/components';
import { ConfirmReceiptModal } from '@/pages/sales/pi/dialogs';
import {
  orderApi,
  orderReceiptOwner,
  piApi,
  piReceiptOwner,
  type ReceiptDeskRow,
  type ReceiptOwner,
  type ReceiptResult,
  readBizError,
} from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';

interface Filters {
  keyword?: string;
  receiptStatus?: number;
}

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 收款管理 → 待确认：业务员上传水单后，财务在这里确认到账（现阶段由总经理兼任） */
const DeskTab: React.FC<{ onTotal?: (n: number) => void }> = ({ onTotal }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>({});
  const [all, setAll] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<ReceiptDeskRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [opening, setOpening] = useState<string>();
  const [owner, setOwner] = useState<ReceiptOwner<ReceiptResult>>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await piApi.receiptDesk({
        keyword: filters.keyword?.trim() || undefined,
        receiptStatus: filters.receiptStatus,
        all,
        page,
        pageSize,
      });
      setRows(res.records);
      setTotal(res.total);
      if (!all) onTotal?.(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [filters, all, page, pageSize, onTotal]);

  useEffect(() => {
    load();
  }, [load]);

  const rowKey = (r: ReceiptDeskRow) =>
    r.piId ? `P${r.piId}` : `S${r.orderId}`;

  /** 登记到账要用剩余金额、水单与收款账户：先取 PI（手动订单取订单）详情再打开弹窗 */
  const openConfirm = async (r: ReceiptDeskRow) => {
    setOpening(rowKey(r));
    try {
      setOwner(
        r.piId
          ? piReceiptOwner(await piApi.detail(r.piId))
          : orderReceiptOwner(await orderApi.detail(r.orderId as number)),
      );
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setOpening(undefined);
    }
  };

  const columns: TableColumnsType<ReceiptDeskRow> = [
    {
      title: 'PI / 订单编号',
      dataIndex: 'piNo',
      width: 170,
      fixed: 'left',
      render: (v: string | undefined, r) =>
        r.piId ? (
          <a onClick={() => history.push(PATHS.pi(r.piId as number))}>{v}</a>
        ) : (
          <div>
            <a onClick={() => history.push(PATHS.order(r.orderId as number))}>
              {r.soNo}
            </a>
            <div style={{ fontSize: 12, color: palette.mute }}>手动订单</div>
          </div>
        ),
    },
    {
      title: '客户 · 业务员',
      key: 'customer',
      width: 220,
      render: (_, r) => (
        <div>
          <div style={{ color: palette.ink, fontWeight: 500 }}>
            {r.customerName}
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {r.ownerName ?? '—'}
          </div>
        </div>
      ),
    },
    {
      title: '合计',
      dataIndex: 'totalAmount',
      width: 140,
      align: 'right',
      render: (v: number, r) => (
        <b style={num}>{formatAmount(v, r.currencyCode)}</b>
      ),
    },
    {
      title: '已到账',
      dataIndex: 'receivedAmount',
      width: 140,
      align: 'right',
      render: (v: number, r) => (
        <span style={{ ...num, color: v > 0 ? palette.green : palette.sub }}>
          {formatAmount(v, r.currencyCode)}
        </span>
      ),
    },
    {
      title: '剩余',
      dataIndex: 'remainingAmount',
      width: 140,
      align: 'right',
      render: (v: number, r) =>
        v < 0 ? (
          <span style={{ ...num, color: palette.orange }}>
            多收 {formatAmount(-v, r.currencyCode)}
          </span>
        ) : (
          <b style={{ ...num, color: v > 0 ? palette.orange : palette.green }}>
            {formatAmount(v, r.currencyCode)}
          </b>
        ),
    },
    {
      title: '待确认水单',
      key: 'slips',
      width: 220,
      render: (_, r) =>
        r.pendingSlips.length === 0 ? (
          <span style={{ fontSize: 12, color: palette.mute }}>
            没有待确认水单
          </span>
        ) : (
          <div style={{ display: 'grid', gap: 6 }}>
            {r.pendingSlips.map((s) => (
              <div key={s.id}>
                <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                  <FileTextOutlined style={{ color: palette.link }} />
                  <b style={num}>{formatAmount(s.amount, r.currencyCode)}</b>
                  <span style={{ fontSize: 12, color: palette.mute }}>
                    {s.receiptDate?.slice(5)}
                  </span>
                </div>
                {s.files.map((f, i) => (
                  <a
                    key={f.fileKey}
                    style={{ fontSize: 12, marginRight: 8 }}
                    onClick={() =>
                      (r.piId
                        ? piApi.openSlipFile(r.piId, s.id, i)
                        : orderApi.openSlipFile(r.orderId as number, s.id, i)
                      ).catch((e) => message.error((e as Error).message))
                    }
                  >
                    <PaperClipOutlined /> {f.fileName}
                  </a>
                ))}
              </div>
            ))}
          </div>
        ),
    },
    {
      title: '收款状态',
      dataIndex: 'receiptStatus',
      width: 110,
      render: (v: number) => <ReceiptStatusPill status={v} />,
    },
    {
      title: '销售订单',
      dataIndex: 'soNo',
      width: 150,
      render: (v?: string) => v ?? '—',
    },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_, r) => (
        <Button
          size="small"
          type={r.pendingSlips.length > 0 ? 'primary' : 'default'}
          icon={<BankOutlined />}
          loading={opening === rowKey(r)}
          onClick={() => openConfirm(r)}
        >
          登记到账
        </Button>
      ),
    },
  ];

  return (
    <div>
      <Card style={{ marginBottom: 16, padding: 20 }}>
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) => {
            setFilters(v);
            setPage(1);
          }}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '2fr 1fr auto auto',
              gap: 12,
              alignItems: 'end',
            }}
          >
            <Form.Item
              name="keyword"
              label="关键词"
              style={{ marginBottom: 0 }}
            >
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder="PI 编号（带不带前缀都可）、客户"
              />
            </Form.Item>
            <Form.Item
              name="receiptStatus"
              label="收款状态"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={Object.entries(RECEIPT_META).map(([k, v]) => ({
                  value: Number(k),
                  label: v.label,
                }))}
              />
            </Form.Item>
            <Segmented
              value={all ? 'all' : 'todo'}
              onChange={(v) => {
                setAll(v === 'all');
                setPage(1);
              }}
              options={[
                { value: 'todo', label: '待处理' },
                { value: 'all', label: '全部' },
              ]}
            />
            <Button type="primary" htmlType="submit">
              查询
            </Button>
          </div>
        </Form>
      </Card>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<ReceiptDeskRow>
          rowKey={rowKey}
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 1420 }}
          locale={{
            emptyText: all
              ? '没有符合条件的 PI'
              : '没有待处理的到账：业务员上传水单后会出现在这里',
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
      <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
        默认只列有待确认水单、或已有到账但没收齐的
        PI；收齐后切到「全部」仍可查到。登记规则与 PI 页的登记到账相同。
      </div>
      <ConfirmReceiptModal
        owner={owner}
        open={!!owner}
        onClose={() => setOwner(undefined)}
        onDone={() => {
          setOwner(undefined);
          load();
        }}
      />
    </div>
  );
};

export default DeskTab;
