import { history, useAccess, useModel } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, DatePicker, Form, Input, Select, Table } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { Eye, Plus, Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import EllipsisText from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  QuickFilters,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { getUserOptions } from '@/services/zhul/users';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { MigratedPill, Pill, StagePill } from './components';
import { CATEGORY_ACTIVE, LIST_PATH } from './constants';
import RegisterDrawer from './RegisterDrawer';
import { rate } from './StatsPanel';
import {
  type OpportunityItem,
  type OpportunityQuery,
  type OpportunityStage,
  type OpportunitySummary,
  opportunityApi,
  readBizError,
} from './service';

interface FilterState {
  keyword?: string;
  sourceChannel?: number;
  stage?: string;
  ownerId?: number;
  range?: [Dayjs, Dayjs];
}

const toQuery = (f: FilterState): OpportunityQuery => ({
  keyword: f.keyword?.trim() || undefined,
  sourceChannel: f.sourceChannel,
  stage: f.stage,
  ownerId: f.ownerId,
  from: f.range?.[0]?.format('YYYY-MM-DD'),
  to: f.range?.[1]?.format('YYYY-MM-DD'),
});

const QUICK_TODAY = 'today';

/** 帮助文字：help-texts.md「商机管理列表」，关键词范围以 search-scope.md 为准，口径取自 OpportunityServiceImpl.summary() */
export const HELP = {
  page: '记录每个新客户从首次接触到赢单的过程；老客户的新需求请直接新建询盘',
  keyword:
    '可搜：商机编号；客户编号、名称、联系人、邮箱、WhatsApp。包含匹配，整段文字一起匹配',
  weekNew: '本周（周一起）首次接触的商机数',
  monthValid: '本月首次接触的商机里，进入过 S3（有效）的比例',
} as const;

const OpportunityPage: React.FC = () => {
  const { options: sourceOptions, labelOf: sourceLabel } = useDictOptions(
    DICT_SOURCE_CHANNEL,
    '未设置',
  );
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const { initialState } = useModel('@@initialState');
  const canAdd = !!access['crm:opportunity:add'];

  const [form] = Form.useForm<FilterState>();
  const [query, setQuery] = useState<OpportunityQuery>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<OpportunityItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [summary, setSummary] = useState<OpportunitySummary | null>(null);
  const [stages, setStages] = useState<OpportunityStage[]>([]);
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [todayOnly, setTodayOnly] = useState(false);

  useEffect(() => {
    opportunityApi
      .stages()
      .then(setStages)
      .catch(() => undefined);
    // 负责人筛选：有用户管理权限时才能取到名单，取不到就不显示这个筛选项
    getUserOptions()
      .then((res) =>
        setOwners(res.map((u) => ({ value: u.id, label: u.name }))),
      )
      .catch(() => setOwners([]));
  }, []);

  const loadSummary = useCallback(() => {
    opportunityApi
      .summary()
      .then(setSummary)
      .catch(() => setSummary(null));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      // 快捷筛选「今日新增」：首次接触日期取今天，与汇总口径一致
      const today = dayjs().format('YYYY-MM-DD');
      const res = await opportunityApi.page({
        ...query,
        ...(todayOnly ? { from: today, to: today } : {}),
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
  }, [query, todayOnly, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时表格与顶部统计卡片一起重新取数（audit UI-055）
  useOnReactivate(() => {
    load();
    loadSummary();
  });

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  const activeIndex = (code: string) =>
    stages
      .filter((s) => s.category === CATEGORY_ACTIVE)
      .findIndex((s) => s.code === code);

  const search = () => {
    setPage(1);
    setQuery(toQuery(form.getFieldsValue()));
  };
  const reset = () => {
    form.resetFields();
    setTodayOnly(false);
    setPage(1);
    setQuery({});
  };

  const columns: TableColumnsType<OpportunityItem> = [
    {
      title: '商机编号 · 首次接触',
      dataIndex: 'opportunityCode',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(`${LIST_PATH}/${r.id}`)}
          >
            {v}
          </button>
          {r.migrated && (
            <div style={{ marginTop: 4 }}>
              <MigratedPill migrated />
            </div>
          )}
          <div className="num" style={{ fontSize: 12, color: palette.mute }}>
            {r.firstContactDate}
          </div>
        </div>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 260,
      render: (_, r) => (
        <div style={{ minWidth: 0 }}>
          <CustomerCell
            name={r.customerName}
            country={r.country}
            contact={r.customerContact}
          />
          {r.customerNameMissing && (
            <div style={{ marginTop: 4 }}>
              <Pill tone="orange">未填客户名称</Pill>
            </div>
          )}
        </div>
      ),
    },
    {
      title: '来源渠道',
      dataIndex: 'sourceChannel',
      width: 160,
      render: (v: number, r) => (
        <EllipsisText
          text={
            r.aliLevel ? `${sourceLabel(v)} · ${r.aliLevel}` : sourceLabel(v)
          }
        />
      ),
    },
    {
      title: '阶段',
      dataIndex: 'stageCode',
      width: 140,
      render: (_, r) => (
        <StagePill
          code={r.stageCode}
          name={r.stageName}
          category={r.stageCategory}
          index={activeIndex(r.stageCode)}
        />
      ),
    },
    {
      title: '需求摘要',
      dataIndex: 'demandSummary',
      width: 260,
      render: (v: string) => <EllipsisText text={v} />,
    },
    {
      title: '负责人',
      dataIndex: 'ownerName',
      width: 100,
      render: (v?: string) => <EllipsisText text={v || '未分配'} />,
    },
    ...auditColumns<OpportunityItem>(),
    {
      title: '操作',
      key: 'op',
      width: rowActionsWidth(1),
      fixed: 'right',
      render: (_, r) => (
        <RowActions
          inline={[
            {
              key: 'view',
              label: '查看',
              icon: <Eye size={18} />,
              onClick: () => history.push(`${LIST_PATH}/${r.id}`),
            },
          ]}
        />
      ),
    },
  ];

  const firstStage = stages.find((s) => s.category === CATEGORY_ACTIVE)?.code;
  const stageOptions = [
    { value: 'ACTIVE', label: '进行中' },
    { value: 'CLOSED', label: '已结束' },
    ...stages.map((s) => {
      const name =
        s.category === CATEGORY_ACTIVE ? `${s.code} ${s.name}` : s.name;
      // 原「待跟进（S1）」卡片的数量并入阶段下拉（stat-cards.md 状态类）
      const n = s.code === firstStage ? summary?.firstStageCount : undefined;
      return { value: s.code, label: n ? `${name} · ${n}` : name };
    }),
  ];

  /** 搜索条件里的下拉：标签在上，读屏名称与标签相同 */
  const select = (
    name: keyof FilterState,
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

  const noQuery = JSON.stringify(query) === '{}' && !todayOnly;

  return (
    <div>
      <PageHeader
        help={HELP.page}
        extra={
          canAdd && (
            <Button
              type="primary"
              icon={<Plus size={18} />}
              onClick={() => setDrawerOpen(true)}
            >
              登记商机
            </Button>
          )
        }
      />
      {summary && (
        <SummaryBar
          items={[
            {
              key: 'week',
              label: '本周新增',
              help: HELP.weekNew,
              value: summary.weekNew,
              sub: `上周 ${summary.lastWeekNew}`,
            },
            {
              key: 'valid',
              label: '本月有效率',
              help: HELP.monthValid,
              value: rate(summary.monthValid, summary.monthNew),
            },
          ]}
        />
      )}

      <Form<FilterState> form={form} component={false}>
        <FilterCard
          onSearch={search}
          onReset={reset}
          // 列宽 110：关键词、阶段、首次接触日期排一行
          minColumn={110}
          moreCount={
            [query.sourceChannel, query.ownerId].filter(Boolean).length
          }
          more={
            <>
              {select('sourceChannel', '来源渠道', sourceOptions)}
              {owners.length > 0 &&
                select('ownerId', '负责人', owners, {
                  showSearch: { optionFilterProp: 'label' },
                })}
            </>
          }
        >
          <FilterField label="关键词" span={2} help={HELP.keyword}>
            <Form.Item name="keyword" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="商机编号、客户、联系人"
                aria-label="关键词"
                onPressEnter={search}
              />
            </Form.Item>
          </FilterField>
          {select('stage', '阶段', stageOptions, {
            // 切换阶段立即查询（doc-list-unify 状态下拉的写法）
            onChange: () => search(),
          })}
          <FilterField
            label="首次接触日期"
            span={2}
            htmlFor="opportunity-first-contact"
          >
            <Form.Item name="range" noStyle>
              <DatePicker.RangePicker
                id={{ start: 'opportunity-first-contact' }}
                style={{ width: '100%' }}
                // 「今日新增」打开时日期固定取今天，日期框置灰，避免看起来还按这里的范围查
                disabled={todayOnly}
              />
            </Form.Item>
          </FilterField>
        </FilterCard>
      </Form>

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !loading && total === 0 && noQuery ? (
        <MainCard>
          <EmptyHint
            title="还没有商机"
            description="接到新客户时登记一条商机，每天各渠道的新增和有效情况会自动统计。"
            actionText={canAdd ? '登记商机' : undefined}
            onAction={canAdd ? () => setDrawerOpen(true) : undefined}
          />
        </MainCard>
      ) : (
        <MainCard
          count={total}
          loading={loading}
          quick={
            <QuickFilters
              options={[
                {
                  value: QUICK_TODAY,
                  label: summary?.todayNew
                    ? `今日新增 · ${summary.todayNew}`
                    : '今日新增',
                },
              ]}
              value={todayOnly ? [QUICK_TODAY] : []}
              onChange={(v) => {
                setTodayOnly(v.includes(QUICK_TODAY));
                setPage(1);
              }}
            />
          }
        >
          <Table<OpportunityItem>
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={rows}
            scroll={{ x: tableScrollX(columns) }}
            locale={{
              emptyText: tableEmpty(
                '没有符合条件的商机，换个筛选条件试试',
                loading,
              ),
            }}
            onRow={(r) => ({
              style: { cursor: 'pointer' },
              onDoubleClick: () => history.push(`${LIST_PATH}/${r.id}`),
            })}
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
      <RegisterDrawer
        open={drawerOpen}
        ownerName={initialState?.currentUser?.name}
        onClose={() => setDrawerOpen(false)}
        onSaved={() => {
          load();
          loadSummary();
        }}
      />
    </div>
  );
};

export default OpportunityPage;
