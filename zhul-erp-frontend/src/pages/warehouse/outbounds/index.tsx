import {
  CarOutlined,
  CheckCircleOutlined,
  InboxOutlined,
  InfoCircleOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { history, Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Input, Select, Space, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import { ModelQtyCell } from '@/components/DocFields/DocPage';
import SourceNos, { useUrlFilter } from '@/components/DocFields/SourceNos';
import { ellipsisRender } from '@/components/EllipsisText';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import {
  kg,
  LOGISTICS_PATHS,
  ObStatusPill,
  PackTag,
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
import { DOC_PATHS, distinctOf, sub } from '../components';
import { HandOverModal, PackDrawer } from './dialogs';

type Tab = 'pending' | 'packed' | 'handed' | 'all';

const TAB_NAMES: Record<Tab, string> = {
  pending: '待打包',
  packed: '待交快递',
  handed: '已交货代',
  all: '全部',
};
const STATUS: Record<Tab, number | undefined> = {
  pending: 1,
  packed: 2,
  handed: 3,
  all: undefined,
};

/** 合并打包最多几张 */
const MERGE_MAX = 20;

const Outbounds: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
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
  const [merging, setMerging] = useState<number[]>();
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

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);
  useEffect(refreshCounts, [refreshCounts]);

  const changed = () => {
    load();
    refreshCounts();
  };

  const selectable = (tab === 'pending' || tab === 'packed') && canPack;

  /** 勾选变化；合并打包了的单同组一起勾选或取消，不在本页的同组单取详情补上 */
  const select = async (keys: React.Key[]) => {
    const pool = new Map<number, Outbound>(
      [...selected, ...rows].map((r) => [r.id, r]),
    );
    const prev = new Set(selected.map((r) => r.id));
    const next = new Set(keys.map(Number));
    for (const r of pool.values()) {
      if (!r.packId || next.has(r.id) === prev.has(r.id)) continue;
      for (const x of [r.id, ...(r.packMates ?? []).map((m) => m.id)])
        if (next.has(r.id)) next.add(x);
        else next.delete(x);
    }
    const missing = [...next].filter((x) => !pool.has(x));
    try {
      for (const o of await Promise.all(missing.map(outboundApi.detail)))
        pool.set(o.id, o);
    } catch (e) {
      message.error(readBizError(e).message);
      return;
    }
    setSelected(
      [...next].map((x) => pool.get(x)).filter((r): r is Outbound => !!r),
    );
  };

  /** 不能和已选的单一起处理：合并打包要同一客户、同一货代；交快递要同一货代 */
  const conflicts = (r: Outbound) => {
    const first = selected[0];
    if (!first || selected.some((s) => s.id === r.id)) return false;
    if (first.forwarderId !== r.forwarderId) return true;
    return (
      tab === 'pending' &&
      (first.customerId !== r.customerId || selected.length >= MERGE_MAX)
    );
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
          {(r.packMates?.length ?? 0) > 0 && <PackTag ob={r} />}
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
        <ModelQtyCell
          items={r.items}
          totalQty={r.totalQuantity}
          docLabel={`${r.obNo} · ${r.customerName ?? ''}`}
          onOpenDetail={() => history.push(DOC_PATHS.outbound(r.id))}
        />
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
          <div>
            {r.boxCount} 箱 · {kg(r.chargeableWeight)}
            {r.packId && sub(palette.mute, '整组')}
          </div>
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
  const changeTab = (t: Tab) => {
    setTab(t);
    setPage(1);
    setSelected([]);
  };

  return (
    <div>
      <PageHeader help="按业务员的发货通知打包：逐箱登记尺寸、重量和箱内型号，交国内快递发往深圳货代；同一客户、同一货代的几张单可以合并打包" />
      <FilterCard
        onSearch={apply}
        onReset={() => {
          setKeyword('');
          setQuery(undefined);
          changeTab('pending');
        }}
      >
        <FilterField
          label="关键词"
          span={2}
          help="可搜：销售出库单号、销售订单编号、出库型号、国内快递单号、客户名称"
        >
          <Input
            allowClear
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={apply}
            placeholder="出库单号、订单编号、客户"
            aria-label="关键词"
          />
        </FilterField>
        <FilterField
          label="状态"
          // 原表格下方按状态显示的脚注（排序说明删去）
          help={
            tab === 'packed'
              ? '勾选同一家货代的销售出库单合成一票快递，运费按计费重分摊'
              : tab === 'handed'
                ? '还没放进出运单的可以在详情里撤销交快递，回到待交快递'
                : tab === 'all'
                  ? '含已撤回的发货通知'
                  : undefined
          }
        >
          <Select<Tab>
            value={tab}
            onChange={changeTab}
            options={[
              {
                value: 'pending',
                label: counts?.pending
                  ? `待打包 · ${counts.pending}`
                  : '待打包',
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
            aria-label="状态"
          />
        </FilterField>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard
          left={
            selectable && selected.length > 0 ? (
              <>
                <CheckCircleOutlined style={{ color: palette.link }} />
                <b style={{ color: palette.ink }}>已选 {selected.length} 张</b>
                <span style={{ color: palette.sub }}>
                  {tab === 'pending'
                    ? `${selected[0].customerName ?? ''} · ${selected[0].forwarderName ?? ''}`
                    : selected[0].forwarderName}
                  {tab === 'packed' &&
                    selected.some((r) => r.packId) &&
                    ' · 合并打包的同组单已一起选中'}
                </span>
                <button
                  type="button"
                  className="zhul-link"
                  onClick={() => setSelected([])}
                >
                  取消选择
                </button>
              </>
            ) : (
              `${TAB_NAMES[tab]} ${total} 张`
            )
          }
          right={
            selectable &&
            selected.length > 0 && (
              <>
                {rows.some(conflicts) && (
                  <span style={{ fontSize: 12, color: palette.mute }}>
                    <InfoCircleOutlined />{' '}
                    {tab === 'pending'
                      ? selected.length >= MERGE_MAX
                        ? `一次最多合并 ${MERGE_MAX} 张`
                        : '灰掉的单客户或货代不同，不能一起合并'
                      : '灰掉的单货代不同，不能一起交快递'}
                  </span>
                )}
                {tab === 'pending' ? (
                  <Button
                    type="primary"
                    icon={<InboxOutlined />}
                    disabled={selected.length < 2}
                    title={
                      selected.length < 2 ? '再选一张才能合并打包' : undefined
                    }
                    onClick={() => setMerging(selected.map((r) => r.id))}
                  >
                    合并打包
                  </Button>
                ) : (
                  <Button
                    type="primary"
                    icon={<CarOutlined />}
                    onClick={() => setHanding(true)}
                  >
                    交快递
                  </Button>
                )}
              </>
            )
          }
        >
          <Table<Outbound>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
            rowSelection={
              selectable
                ? {
                    selectedRowKeys: selected.map((r) => r.id),
                    preserveSelectedRowKeys: true,
                    onChange: select,
                    getCheckboxProps: (r) => ({ disabled: conflicts(r) }),
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
              onChange: (p: number, s: number) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </MainCard>
      )}
      <PackDrawer
        id={packing}
        onClose={() => setPacking(undefined)}
        onDone={(packedId) => {
          // 保存期间换开了别的单：只刷新列表，不关掉正在看的抽屉（UI-017）
          setPacking((cur) => (cur === packedId ? undefined : cur));
          changed();
        }}
      />
      <PackDrawer
        mergeIds={merging}
        onClose={() => setMerging(undefined)}
        onDone={() => {
          setMerging(undefined);
          setSelected([]);
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
