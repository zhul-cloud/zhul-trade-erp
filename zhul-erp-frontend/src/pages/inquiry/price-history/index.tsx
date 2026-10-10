import { SearchOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, DatePicker, Form, Input, Select, Table } from 'antd';
import type { Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  AgeText,
  Card,
  ConditionPill,
  LeadTimeText,
  PageTitle,
  SupplierText,
  TaxHint,
  useQuoteDicts,
} from '../shared/components';
import { CHANNEL_OPTIONS, PATHS } from '../shared/constants';
import {
  type PriceHistoryGroup,
  priceHistoryApi,
  readBizError,
} from '../shared/service';

interface Filters {
  model?: string;
  brand?: string;
  itemCondition?: number;
  channel?: number;
  range?: [Dayjs, Dayjs];
}

/** 历史询价：同一型号以前问到的价格，询价和报价前先查一查 */
const PriceHistoryPage: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canSeeSupplier = !!access['inquiry:supplier:view'];
  const { conditionOptions } = useQuoteDicts();
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<PriceHistoryGroup[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await priceHistoryApi.page({
        model: filters.model?.trim() || undefined,
        brand: filters.brand?.trim() || undefined,
        itemCondition: filters.itemCondition,
        channel: filters.channel,
        dateFrom: filters.range?.[0]?.format('YYYY-MM-DD'),
        dateTo: filters.range?.[1]?.format('YYYY-MM-DD'),
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
  }, [filters, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  const columns: TableColumnsType<PriceHistoryGroup> = [
    {
      title: '品牌 / 型号',
      render: (_, r) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
          <span style={{ color: palette.ink, fontWeight: 600 }}>{r.model}</span>
          <span style={{ color: palette.mute, fontSize: 12 }}>
            {r.brand}
            {r.category ? ` · ${r.category}` : ''}
          </span>
        </span>
      ),
    },
    {
      title: '最低价',
      width: 130,
      render: (_, r) =>
        r.minPriceCny == null ? (
          <span style={{ color: palette.mute }}>无货</span>
        ) : (
          <b style={{ color: palette.green }}>{formatAmount(r.minPriceCny)}</b>
        ),
    },
    {
      title: '最高价',
      width: 130,
      render: (_, r) =>
        r.maxPriceCny == null ? '—' : formatAmount(r.maxPriceCny),
    },
    { title: '记录数', dataIndex: 'recordCount', width: 90 },
    {
      title: '最近询价',
      width: 190,
      render: (_, r) => <AgeText days={r.daysAgo} date={r.lastQuotedAt} />,
    },
  ];

  const expanded = (g: PriceHistoryGroup) => (
    <div style={{ display: 'grid', gap: 2, padding: '0 12px' }}>
      {g.records.map((q) => (
        <div
          key={q.id}
          style={{
            display: 'grid',
            gridTemplateColumns: '110px 100px 1.4fr 100px 90px 170px 1fr',
            gap: 12,
            alignItems: 'center',
            padding: '8px 0',
            borderBottom: `1px solid ${palette.hairline}`,
          }}
        >
          <b style={{ color: q.noStock ? palette.mute : palette.ink }}>
            {q.noStock ? '无货' : formatAmount(q.unitPriceCny)}
          </b>
          <span>
            {q.noStock ? '' : <ConditionPill value={q.itemCondition} />}
          </span>
          <span>
            <SupplierText channel={q.channel} shopName={q.shopName} />
            {!q.noStock && (
              <span style={{ marginLeft: 6 }}>
                <TaxHint
                  taxIncluded={q.taxIncluded}
                  taxRate={q.taxRate}
                  unitPrice={q.unitPrice}
                />
              </span>
            )}
          </span>
          <span style={{ color: palette.sub }}>
            {q.noStock ? q.note : <LeadTimeText value={q.leadTime} />}
          </span>
          <span style={{ color: palette.sub }}>{q.quotedByName ?? '—'}</span>
          <AgeText days={q.daysAgo} date={q.quotedAt} />
          <span style={{ textAlign: 'right' }}>
            {q.customerInquiryId && (
              <a
                style={{ fontSize: 12 }}
                onClick={() =>
                  history.push(`${PATHS.inquiries}/${q.customerInquiryId}`)
                }
              >
                来自 {q.customerInquiryCode}
              </a>
            )}
          </span>
        </div>
      ))}
    </div>
  );

  return (
    <div>
      <PageTitle
        crumbs={['历史询价']}
        title="历史询价"
        description="同一型号以前问到的价格都在这里，询价和报价前先查一查。同品牌、型号写法不同（大小写、空格、连字符）也会归到一起。"
      />
      <Card style={{ marginBottom: 16, padding: 20 }}>
        <Form
          form={form}
          layout="vertical"
          onFinish={() => {
            setPage(1);
            setFilters(form.getFieldsValue());
          }}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: canSeeSupplier
                ? '2fr 1fr 1fr 1fr 1.5fr auto'
                : '2fr 1fr 1fr 1.5fr auto',
              gap: 12,
              alignItems: 'end',
            }}
          >
            <Form.Item name="model" label="型号" style={{ marginBottom: 0 }}>
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder="输入型号的一部分，如 HG-SN"
              />
            </Form.Item>
            <Form.Item name="brand" label="品牌" style={{ marginBottom: 0 }}>
              <Input allowClear placeholder="如 Mitsubishi 或 三菱" />
            </Form.Item>
            <Form.Item
              name="itemCondition"
              label="货况"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={conditionOptions}
              />
            </Form.Item>
            {canSeeSupplier && (
              <Form.Item
                name="channel"
                label="渠道"
                style={{ marginBottom: 0 }}
              >
                <Select
                  allowClear
                  placeholder="全部"
                  options={CHANNEL_OPTIONS}
                />
              </Form.Item>
            )}
            <Form.Item
              name="range"
              label="询价日期"
              style={{ marginBottom: 0 }}
            >
              <DatePicker.RangePicker style={{ width: '100%' }} />
            </Form.Item>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button
                onClick={() => {
                  form.resetFields();
                  setPage(1);
                  setFilters({});
                }}
              >
                重置
              </Button>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
            </div>
          </div>
        </Form>
      </Card>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<PriceHistoryGroup>
          rowKey={(r) => `${r.brandKey}|${r.modelKey}`}
          columns={columns}
          dataSource={rows}
          loading={loading}
          expandable={{ expandedRowRender: expanded }}
          locale={{
            emptyText:
              filters.model || filters.brand
                ? '没有找到这个型号的历史价格，需要询价'
                : '还没有历史询价，采购提交回价后会出现在这里',
          }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 个型号`,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      )}
    </div>
  );
};

export default PriceHistoryPage;
