import {
  BankOutlined,
  FileTextOutlined,
  PaperClipOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Form, Input, Select, Table } from 'antd';
import { Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerSub } from '@/components/DocFields';
import {
  FilterCard,
  FilterField,
  HelpTip,
  MainCard,
  QuickFilters,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
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
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';

interface Filters {
  keyword?: string;
  receiptStatus?: number;
}

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 帮助文字：help-texts.md「收款管理」，关键词范围以 search-scope.md 为准 */
export const DESK_HELP = {
  keyword:
    '可搜：PI 编号（不带前缀也能搜到）、订单编号（只限收款记在订单上的手动订单和费用订单）、客户名称或简称',
  todo: '只列有待确认水单、或已有到账但没收齐的 PI；收齐后取消「只看待处理」仍可查到。登记规则与 PI 页的登记到账相同',
} as const;

/** 收款单 → 待确认：业务员上传水单后，财务在这里确认到账（现阶段由总经理兼任） */
const DeskTab: React.FC<{ onTotal?: (n: number) => void }> = ({ onTotal }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>({});
  const search = () => {
    setFilters(form.getFieldsValue());
    setPage(1);
  };
  const [all, setAll] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
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

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

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
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(PATHS.pi(r.piId as number))}
          >
            {v}
          </button>
        ) : (
          <div>
            <button
              type="button"
              className="zhul-link"
              onClick={() => history.push(PATHS.order(r.orderId as number))}
            >
              {r.soNo}
            </button>
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
          <div style={{ color: palette.ink, fontWeight: 600 }}>
            {r.customerName}
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            <CustomerSub
              country={r.customerCountry}
              contact={r.customerContact}
              empty={null}
            />
            {r.ownerName && <div>业务员 {r.ownerName}</div>}
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
                  <button
                    type="button"
                    className="zhul-link"
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
                  </button>
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
      <Form form={form} component={false}>
        <FilterCard
          onSearch={search}
          onReset={() => {
            form.resetFields();
            setFilters({});
            setAll(false);
            setPage(1);
          }}
        >
          <FilterField label="关键词" span={2} help={DESK_HELP.keyword}>
            <Form.Item name="keyword" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="PI 编号、订单编号、客户"
                aria-label="关键词"
                onPressEnter={search}
              />
            </Form.Item>
          </FilterField>
          <FilterField label="收款状态">
            <Form.Item name="receiptStatus" noStyle>
              <Select
                allowClear
                placeholder="全部"
                aria-label="收款状态"
                options={Object.entries(RECEIPT_META).map(([k, v]) => ({
                  value: Number(k),
                  label: v.label,
                }))}
                // 状态下拉切换后立即查询
                onChange={(v) => {
                  setFilters({ ...form.getFieldsValue(), receiptStatus: v });
                  setPage(1);
                }}
              />
            </Form.Item>
          </FilterField>
        </FilterCard>
      </Form>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard
          left={`共 ${total} 条`}
          quick={
            <span
              style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
            >
              <QuickFilters
                options={[{ value: 'todo', label: '只看待处理' }]}
                value={all ? [] : ['todo']}
                onChange={(v) => {
                  setAll(v.length === 0);
                  setPage(1);
                }}
              />
              <HelpTip title={DESK_HELP.todo} />
            </span>
          }
        >
          <Table<ReceiptDeskRow>
            rowKey={rowKey}
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
            locale={{
              emptyText: all
                ? '没有符合条件的 PI'
                : '没有待处理的到账：业务员上传水单后会出现在这里',
            }}
            pagination={{
              ...PAGER_STYLE,
              current: page,
              pageSize,
              total,
              onChange: (p, s) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </MainCard>
      )}
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
