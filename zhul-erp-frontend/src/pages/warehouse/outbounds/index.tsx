import { CarOutlined, SearchOutlined } from '@ant-design/icons';
import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Input, Segmented, Space, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import { ModelQtyCell } from '@/components/DocFields/DocPage';
import SourceNos, { useUrlFilter } from '@/components/DocFields/SourceNos';
import { ellipsisRender } from '@/components/EllipsisText';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import {
  kg,
  LOGISTICS_PATHS,
  ObStatusPill,
} from '@/pages/logistics/components';
import {
  type Outbound,
  outboundApi,
  readBizError,
} from '@/pages/logistics/service';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  Card,
  DOC_PATHS,
  distinctOf,
  sub,
  WarehousePageTitle,
} from '../components';
import { HandOverModal, PackDrawer } from './dialogs';

type Tab = 'pending' | 'packed' | 'handed' | 'all';
const STATUS: Record<Tab, number | undefined> = {
  pending: 1,
  packed: 2,
  handed: 3,
  all: undefined,
};

const Outbounds: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess();
  const canPack = !!access['warehouse:outbound:pack'];
  const url = useUrlFilter();
  const [tab, setTab] = useState<Tab>(
    url.tab === 'packed' || url.tab === 'handed' || url.tab === 'all'
      ? url.tab
      : 'pending',
  );
  const [counts, setCounts] = useState<{ pending: number; packed: number }>();
  const [keyword, setKeyword] = useState(url.keyword);
  const [query, setQuery] = useState(url.keyword || undefined);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<Outbound[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [packing, setPacking] = useState<number>();
  const [selected, setSelected] = useState<Outbound[]>([]);
  const [handing, setHanding] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await outboundApi.page({
        page,
        pageSize,
        keyword: query,
        status: STATUS[tab],
        all: tab === 'all',
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

  const action = (r: Outbound) => {
    if (tab === 'pending' && canPack)
      return (
        <button
          type="button"
          className="zhul-link"
          onClick={() => setPacking(r.id)}
        >
          打包
        </button>
      );
    if (tab === 'packed' && canPack)
      return (
        <button
          type="button"
          className="zhul-link"
          onClick={() => setPacking(r.id)}
        >
          修改装箱
        </button>
      );
    return null;
  };

  const columns: TableColumnsType<Outbound> = [
    {
      title: '出库单号 · 通知日期',
      dataIndex: 'obNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <Link to={DOC_PATHS.outbound(r.id)}>{v}</Link>
          {sub(palette.mute, formatDateTime(r.createTime).slice(0, 10))}
        </div>
      ),
    },
    {
      title: '客户',
      key: 'customer',
      width: 200,
      render: (_, r) => <CustomerCell name={r.customerName} />,
    },
    {
      title: '型号 · 数量',
      key: 'items',
      width: 200,
      render: (_, r) => (
        <ModelQtyCell items={r.items} totalQty={r.totalQuantity} />
      ),
    },
    {
      title: '品牌',
      key: 'brands',
      width: 120,
      render: (_, r) => (
        <TagList values={distinctOf(r.items, 'brand')} label="品牌" />
      ),
    },
    {
      title: '品类',
      key: 'categories',
      width: 130,
      render: (_, r) => (
        <TagList values={distinctOf(r.items, 'category')} label="品类" />
      ),
    },
    {
      title: '货代',
      dataIndex: 'forwarderName',
      width: 110,
      render: ellipsisRender,
    },
    {
      title: '箱数 · 计费重',
      key: 'boxes',
      width: 130,
      render: (_, r) =>
        r.boxCount ? (
          `${r.boxCount} 箱 · ${kg(r.chargeableWeight)}`
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '国内快递',
      key: 'courier',
      width: 170,
      render: (_, r) =>
        r.courier ? (
          <div>
            <b style={{ color: palette.ink }}>{r.courier.carrier}</b>
            {sub(
              palette.mute,
              `${r.courier.trackingNo ?? ''}${
                r.courier.outboundCount > 1
                  ? ` · ${r.courier.outboundCount} 单共用`
                  : ''
              }`,
            )}
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v: number, r) => (
        <div>
          <ObStatusPill value={v}>{r.statusName}</ObStatusPill>
          {r.source === 2 && sub(palette.orange, r.sourceName)}
        </div>
      ),
    },
    {
      title: '来源单号',
      key: 'so',
      width: 160,
      render: (_, r) => (
        <SourceNos
          refs={[{ no: r.soNo, href: LOGISTICS_PATHS.salesOrder(r.soId) }]}
        />
      ),
    },
    {
      title: '业务员',
      dataIndex: 'ownerName',
      width: 90,
      render: ellipsisRender,
    },
    ...auditColumns<Outbound>(),
    {
      title: '操作',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_, r) => (
        <Space size={12}>
          {action(r)}
          <Link to={DOC_PATHS.outbound(r.id)}>查看</Link>
        </Space>
      ),
    },
  ];

  const apply = () => {
    setQuery(keyword.trim() || undefined);
    setPage(1);
  };

  return (
    <div>
      <WarehousePageTitle description="按业务员的发货通知打包：逐箱登记尺寸、重量和箱内型号，交国内快递发往深圳货代。" />
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
            { value: 'all', label: '全部' },
          ]}
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={apply}
          placeholder="销售出库单号、订单号、客户、型号"
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
            scroll={{ x: tableScrollX(columns) }}
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
                  ? '没有待打包的销售出库单'
                  : tab === 'packed'
                    ? '没有待交快递的销售出库单'
                    : tab === 'handed'
                      ? '还没有交货代的销售出库单'
                      : '还没有销售出库单',
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
          ? '按通知时间从早到晚；一个箱子只装一张销售出库单的货'
          : tab === 'packed'
            ? '勾选同一家货代的销售出库单合成一票快递，运费按计费重分摊'
            : tab === 'handed'
              ? '还没放进出运单的可以在详情里撤销交快递，回到待交快递'
              : '含已撤回的发货通知'}
      </div>
      <PackDrawer
        id={packing}
        onClose={() => setPacking(undefined)}
        onDone={(packedId) => {
          // 保存期间换开了别的单：只刷新列表，不关掉正在看的抽屉（UI-017）
          setPacking((cur) => (cur === packedId ? undefined : cur));
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
    </div>
  );
};

export default Outbounds;
