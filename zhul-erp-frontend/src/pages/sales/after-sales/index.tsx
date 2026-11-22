import { history, useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Form, Input, Select, Table } from 'antd';
import dayjs from 'dayjs';
import { Eye, Plus, Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import SourceNos from '@/components/DocFields/SourceNos';
import { ellipsisRender } from '@/components/EllipsisText';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  QuickFilters,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { IconButton } from '@/components/Shell';
import { Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import { searchCustomers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { useDictTexts } from '@/utils/dict';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { readBizError } from '../service';
import { CreateAfterSalesModal, useOwnerOptions } from './dialogs';
import {
  type AfterSales,
  AS_STATUS,
  AS_STATUS_TONE,
  afterSalesApi,
  DICT_LEVEL,
  DICT_REASON,
  LEVEL_TONE,
  levelShort,
} from './service';
import { DUE_TONE, dueLeftText } from './sla';

type Filters = {
  keyword?: string;
  status?: number;
  customerId?: number;
  levelCode?: string;
  reasonCode?: string;
  ownerId?: number;
};

/** 快捷筛选：已逾期（需要更新客户）、已逾期或快到期（工作台带过来的口径） */
const QUICK_OVERDUE = 'overdue';
const QUICK_SOON = 'soon';

/** 帮助文字：help-texts.md「售后管理列表」，口径取自 AfterSalesService.counts() */
export const HELP = {
  page: '客户收货后的问题：跟进进度、退货、退款和费用都记在售后单上',
  closedThisMonth: '这个月已结案的售后单数',
  keyword: '可搜：售后单号、订单编号、型号',
} as const;

/** 销售管理 → 售后管理（spec sales/after-sales） */
const AfterSalesPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const levels = useDictTexts(DICT_LEVEL);
  const reasons = useDictTexts(DICT_REASON);
  const owners = useOwnerOptions();
  const [search] = useSearchParams();
  const [form] = Form.useForm<Filters>();
  /** 工作台带过来：overdue=1 只看逾期，overdue=soon 已逾期或快到期 */
  const [due, setDue] = useState<'overdue' | 'soon' | undefined>(() => {
    const v = search.get('overdue');
    return v === '1' ? 'overdue' : v === 'soon' ? 'soon' : undefined;
  });
  // 默认看处理中；从工作台带逾期条件跳来时看全部状态。
  // 工作台「售后要更新客户」带上本人，列表与待办数字同口径（audit UI-081）
  const [defaults] = useState<Filters>(() => {
    const owner = Number(search.get('ownerId'));
    return {
      status: search.get('overdue') ? undefined : AS_STATUS.OPEN,
      ownerId: Number.isInteger(owner) && owner > 0 ? owner : undefined,
    };
  });
  const [filters, setFilters] = useState<Filters>(defaults);
  const [customerOptions, setCustomerOptions] = useState<
    { value: number; label: string }[]
  >([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<AfterSales[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [counts, setCounts] = useState<{
    open: number;
    waiting: number;
    overdue: number;
    dueSoon: number;
    closedThisMonth: number;
  }>();
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, c] = await Promise.all([
        afterSalesApi.page({
          page,
          pageSize,
          status: filters.status,
          keyword: filters.keyword?.trim() || undefined,
          customerId: filters.customerId,
          levelCode: filters.levelCode,
          reasonCode: filters.reasonCode,
          ownerId: filters.ownerId,
          overdue: due === 'overdue' || undefined,
          dueSoon: due === 'soon' || undefined,
        }),
        afterSalesApi.counts(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, filters, due, message]);

  const searchNow = () => {
    setFilters(form.getFieldsValue());
    setPage(1);
  };

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  const searchCust = async (kw: string) => {
    try {
      const res = await searchCustomers(kw);
      setCustomerOptions(
        res.map((c) => ({ value: c.id, label: c.displayName ?? c.name })),
      );
    } catch {
      setCustomerOptions([]);
    }
  };

  const sub = (t?: React.ReactNode, color?: string) =>
    t ? (
      <div style={{ fontSize: 12, color: color ?? palette.mute }}>{t}</div>
    ) : null;

  const columns: TableColumnsType<AfterSales> = [
    {
      title: '售后单',
      dataIndex: 'asNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => (
        <button
          type="button"
          className="zhul-link"
          onClick={() => history.push(`/sales/after-sales/${r.id}`)}
        >
          {v}
        </button>
      ),
    },
    {
      title: '客户',
      key: 'customer',
      width: 200,
      render: (_, r) => (
        <CustomerCell
          name={r.customerName}
          country={r.customerCountry}
          contact={r.customerContact}
        />
      ),
    },
    {
      title: '型号',
      dataIndex: 'itemSummary',
      width: 200,
      ellipsis: true,
      render: (v?: string, r?: AfterSales) => (
        <div>
          <div>{v || '—'}</div>
          {sub(r?.symptom)}
        </div>
      ),
    },
    {
      title: '等级',
      dataIndex: 'levelCode',
      width: 80,
      render: (v: string, r) => (
        <Pill tone={LEVEL_TONE[v] ?? 'gray'}>{levelShort(v, r.levelName)}</Pill>
      ),
    },
    {
      title: '原因 · 方案',
      key: 'reason',
      width: 170,
      render: (_, r) => (
        <div>
          <div
            style={{
              color:
                r.reasonCode === 'INVESTIGATING' ? palette.orange : palette.sub,
            }}
          >
            {r.reasonName}
          </div>
          {sub(r.solutionNames.join('、') || '还没定方案')}
        </div>
      ),
    },
    {
      title: '来源单号',
      key: 'source',
      width: 150,
      render: (_, r) => (
        <SourceNos
          refs={[
            {
              no: r.soNo,
              href: r.soId ? `/sales/orders/${r.soId}` : undefined,
            },
          ]}
        />
      ),
    },
    {
      title: '负责人',
      dataIndex: 'ownerName',
      width: 90,
      render: ellipsisRender,
    },
    {
      title: '下一步 · 截止',
      key: 'next',
      width: 250,
      render: (_, r) => {
        const tone = r.dueState ? DUE_TONE[r.dueState] : 'gray';
        const color =
          tone === 'red'
            ? palette.red
            : tone === 'orange'
              ? palette.orange
              : undefined;
        return (
          <div>
            <div style={{ color: palette.sub }}>{r.nextAction || '—'}</div>
            {r.dueAt ? (
              <div style={{ marginTop: 2 }}>
                <Pill tone={tone}>{r.dueTypeName}</Pill>
                {sub(
                  `${dayjs(r.dueAt).format('MM-DD HH:mm')}${r.dueState === 'NORMAL' ? '' : ` · ${dueLeftText(r)}`}`,
                  color,
                )}
              </div>
            ) : (
              r.status === AS_STATUS.WAITING &&
              sub('待客户确认 · 没有手填时间，不提醒')
            )}
          </div>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 110,
      render: (v: string, r) => (
        <Pill tone={AS_STATUS_TONE[r.status] ?? 'gray'}>{v}</Pill>
      ),
    },
    ...auditColumns<AfterSales>(),
    {
      title: '操作',
      key: 'op',
      width: 64,
      fixed: 'right',
      render: (_, r) => (
        <IconButton
          label="查看"
          size={32}
          icon={<Eye size={18} />}
          onClick={() => history.push(`/sales/after-sales/${r.id}`)}
        />
      ),
    },
  ];

  /** 搜索条件里的下拉：标签在上，读屏名称与标签相同 */
  const select = (
    name: keyof Filters,
    label: string,
    options: { value: string | number; label: string }[],
    extra?: React.ComponentProps<typeof Select>,
  ) => (
    <FilterField label={label}>
      <Form.Item name={name} noStyle>
        <Select
          allowClear
          placeholder="全部"
          aria-label={label}
          options={options}
          {...extra}
        />
      </Form.Item>
    </FilterField>
  );
  /** 下拉选项后面带数量（stat-cards.md：状态并入下拉） */
  const withCount = (label: string, n?: number) =>
    n ? `${label} · ${n}` : label;

  return (
    <div>
      <PageHeader
        help={HELP.page}
        extra={
          <Button
            type="primary"
            icon={<Plus size={18} />}
            onClick={() => setCreating(true)}
          >
            新建售后单
          </Button>
        }
      />
      {counts && (
        <SummaryBar
          items={[
            {
              key: 'closed',
              label: '本月结案',
              help: HELP.closedThisMonth,
              value: counts.closedThisMonth,
            },
          ]}
        />
      )}
      <Form form={form} initialValues={defaults} component={false}>
        <FilterCard
          onSearch={searchNow}
          onReset={() => {
            // 重置回默认视图：处理中，不带逾期条件
            const d = { status: AS_STATUS.OPEN };
            form.resetFields();
            form.setFieldsValue({ ...d, ownerId: undefined });
            setFilters(d);
            setDue(undefined);
            setPage(1);
          }}
          // 列宽 110：关键词、状态、客户排一行
          minColumn={110}
          moreCount={
            [filters.levelCode, filters.reasonCode, filters.ownerId].filter(
              (v) => v != null,
            ).length
          }
          more={
            <>
              {select(
                'levelCode',
                '等级',
                levels.map((l) => ({ value: l.code, label: l.name })),
              )}
              {select(
                'reasonCode',
                '原因',
                reasons.map((r) => ({ value: r.code, label: r.name })),
              )}
              {select('ownerId', '负责人', owners, {
                showSearch: { optionFilterProp: 'label' },
              })}
            </>
          }
          quick={
            <QuickFilters
              options={[
                {
                  value: QUICK_OVERDUE,
                  label: withCount('需要更新客户', counts?.overdue),
                },
                {
                  value: QUICK_SOON,
                  label: withCount(
                    '已逾期或快到期',
                    counts && counts.overdue + counts.dueSoon,
                  ),
                },
              ]}
              value={due ? [due] : []}
              onChange={(v) => {
                // 两个条件互斥，取刚点的那个；逾期单可能在处理中或待客户确认，状态放开看全部
                const next = v.find((x) => x !== due) as typeof due;
                setDue(next);
                if (next) {
                  // 只放开状态，其他条件仍按上次查询的（没点查询的输入不生效，同 PI 列表）
                  form.setFieldValue('status', undefined);
                  setFilters((f) => ({ ...f, status: undefined }));
                }
                setPage(1);
              }}
            />
          }
        >
          <FilterField label="关键词" span={2} help={HELP.keyword}>
            <Form.Item name="keyword" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="售后单号、订单编号、型号"
                aria-label="关键词"
                onPressEnter={searchNow}
              />
            </Form.Item>
          </FilterField>
          {select(
            'status',
            '状态',
            [
              {
                value: AS_STATUS.OPEN,
                label: withCount('处理中', counts?.open),
              },
              {
                value: AS_STATUS.WAITING,
                label: withCount('待客户确认', counts?.waiting),
              },
              { value: AS_STATUS.CLOSED, label: '已结案' },
              { value: AS_STATUS.CANCELLED, label: '已撤销' },
            ],
            // 切换状态立即查询（doc-list-unify 状态下拉的写法）
            { onChange: () => searchNow() },
          )}
          <FilterField label="客户">
            <Form.Item name="customerId" noStyle>
              <Select<number>
                allowClear
                showSearch={{ filterOption: false, onSearch: searchCust }}
                placeholder="全部"
                options={customerOptions}
                onFocus={() => customerOptions.length === 0 && searchCust('')}
                aria-label="客户"
              />
            </Form.Item>
          </FilterField>
        </FilterCard>
      </Form>
      <MainCard left={`共 ${total} 条`}>
        <Table<AfterSales>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText:
              filters.status === AS_STATUS.OPEN
                ? '没有处理中的售后单；客户反馈问题时点右上角「新建售后单」，或在订单详情点「售后」'
                : '没有符合条件的售后单',
          }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t, [from, to]) => `${from}-${to} 共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      </MainCard>
      <CreateAfterSalesModal
        open={creating}
        onClose={() => setCreating(false)}
        onDone={(a) => {
          setCreating(false);
          history.push(`/sales/after-sales/${a.id}`);
        }}
      />
    </div>
  );
};

export default AfterSalesPage;
