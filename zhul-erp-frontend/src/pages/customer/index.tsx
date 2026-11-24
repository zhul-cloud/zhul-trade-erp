import { history, useAccess } from '@umijs/max';
import type { CheckboxProps, TableColumnsType, TableProps } from 'antd';
import {
  App,
  Button,
  Form,
  Input,
  Segmented,
  Select,
  Skeleton,
  Table,
  Tooltip,
} from 'antd';
import {
  ArrowLeftRight,
  Download,
  Eye,
  Pencil,
  Plus,
  Search,
  Trash2,
  Upload,
  Waves,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { useConfirmDanger } from '@/components/ConfirmDanger';
import EllipsisText from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  HeadHelp,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { StatusSwitch } from '@/components/StatusSwitch';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useCountries } from '@/pages/product/components/useCountries';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { LIST_PATH, Pill, RolePill, TransferModal } from './components';
import { DISABLE_CONFIRM_TEXT, ROLE_OPTIONS } from './constants';
import { PoolTab, PutIntoPoolModal } from './pool';
import CompletionTab from './profile/CompletionTab';
import DuplicatesTab from './profile/DuplicatesTab';
import { GRADE_NAMES, MODULE_SHORT, profileApi } from './profile/service';
import { doneOf, GradeBadge, Progress5, Stars } from './profile/ui';
import {
  type AssignableOwners,
  type CustomerListItem,
  type CustomerQuery,
  customerApi,
  readBizError,
} from './service';

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' },
];

const BIZ_GRADE_OPTIONS = [1, 2, 3, 4, 0].map((v) => ({
  value: v,
  label: GRADE_NAMES[v],
}));
const MODULE_OPTIONS = MODULE_SHORT.map((label, i) => ({
  value: i + 1,
  label: `${'①②③④⑤'[i]} ${label}`,
}));

type FilterForm = Omit<CustomerQuery, 'missingModules'> & {
  missingModules?: number[];
};

const compact = (q: FilterForm): CustomerQuery =>
  Object.fromEntries(
    Object.entries({
      ...q,
      missingModules: q.missingModules?.length
        ? q.missingModules.join(',')
        : undefined,
    }).filter(([, v]) => v !== undefined && v !== ''),
  ) as CustomerQuery;

/** 帮助文字：help-texts.md「客户管理列表」，客户名称的搜索范围以 search-scope.md 为准 */
export const HELP = {
  page: '档案完整度 5/5 为已建档；先补有成交的客户。报价单、PI、CI 从这里带出客户信息',
  name: '可搜：英文名、中文名、简称。客户编码、联系人在旁边的输入框里搜',
} as const;

const CustomerListPage: React.FC = () => {
  const { options: sourceOptions, labelOf: sourceLabel } =
    useDictOptions(DICT_SOURCE_CHANNEL);
  const { message, modal } = App.useApp();
  const confirmDanger = useConfirmDanger();
  const access = useAccess() as Record<string, boolean>;
  const { palette } = useAppTheme();
  const [form] = Form.useForm<FilterForm>();
  const { options: countryOptions, zhOf } = useCountries();

  const canAdd = !!access['partner:customer:add'];
  const canEdit = !!access['partner:customer:edit'];
  const canDelete = !!access['partner:customer:delete'];
  const canStatus = !!access['partner:customer:status'];
  const canTransfer = !!access['partner:customer:transfer'];
  const canExport = !!access['partner:customer:export'];
  const canMerge = !!access['partner:customer:merge'];
  const [tab, setTab] = useState<'all' | 'fill' | 'dups' | 'pool'>('all');
  const [dupCount, setDupCount] = useState<number>();
  const [poolCount, setPoolCount] = useState<number>();
  // 放入公海：负责人本人或有转移权限的人（后端校验），有编辑或转移权限时显示入口
  const [poolTargets, setPoolTargets] = useState<CustomerListItem[]>([]);
  const canPool = canEdit || canTransfer;
  const [sort, setSort] = useState<
    Pick<CustomerQuery, 'sortField' | 'sortOrder'>
  >({});
  // 表格上方的建档统计（按数据范围，不带其他筛选）
  const [counts, setCounts] = useState<{
    all: number;
    done: number;
    pending: number;
  }>();

  const [query, setQuery] = useState<CustomerQuery>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<CustomerListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [firstLoad, setFirstLoad] = useState(true);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<React.Key[]>([]);
  const [togglingId, setTogglingId] = useState<number>();
  const [exporting, setExporting] = useState(false);
  const [owners, setOwners] = useState<AssignableOwners>();
  const [transferTargets, setTransferTargets] = useState<CustomerListItem[]>(
    [],
  );

  // 「负责业务员」筛选和列只对数据范围为全部 / 自定义的人有意义
  const showOwner = owners?.scope === 'ALL' || owners?.scope === 'CUSTOM';
  const hasCondition = Object.keys(query).length > 0;

  useEffect(() => {
    customerApi
      .assignableOwners()
      .then(setOwners)
      .catch(() => undefined);
    profileApi
      .duplicates()
      .then((g) => setDupCount(g.length))
      .catch(() => undefined);
    customerApi
      .pool({ page: 1, pageSize: 1 })
      .then((r) => setPoolCount(r.total))
      .catch(() => undefined);
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const data = await customerApi.page({
        ...query,
        ...sort,
        page,
        pageSize,
      });
      if (data.records.length === 0 && data.total > 0 && page > 1) {
        setPage(page - 1);
        return;
      }
      setRows(data.records);
      setTotal(data.total);
      customerApi
        .profileCounts()
        .then(setCounts)
        .catch(() => undefined);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
      setFirstLoad(false);
    }
  }, [query, sort, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  // 建档状态在搜索条件里（下拉带数量），和其他条件一起查询、一起重置
  const search = () => {
    setQuery(compact(form.getFieldsValue()));
    setPage(1);
    setSelected([]);
  };

  const reset = () => {
    form.resetFields();
    search();
  };

  const toggleStatus = (record: CustomerListItem, checked: boolean) => {
    const apply = async () => {
      setTogglingId(record.id);
      try {
        await customerApi.updateStatus(record.id, checked ? 1 : 0);
        message.success(checked ? '已启用' : '已禁用');
        await load();
      } finally {
        setTogglingId(undefined);
      }
    };
    if (checked) {
      apply();
      return;
    }
    confirmDanger({
      title: `禁用客户「${record.displayName}」？`,
      content: DISABLE_CONFIRM_TEXT,
      okText: '禁用',
      onOk: apply,
    });
  };

  const confirmDelete = (record: CustomerListItem) => {
    confirmDanger({
      title: `删除客户「${record.displayName}（${record.customerCode}）」？`,
      content:
        '删除后不可恢复。如只是暂停合作，建议改为禁用。已有询盘或单据记录的客户不能删除。',
      onOk: async () => {
        try {
          await customerApi.remove(record.id);
          message.success('删除成功');
          setSelected((keys) => keys.filter((k) => k !== record.id));
          await load();
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });
  };

  const confirmBatchDelete = () => {
    confirmDanger({
      title: `删除已选的 ${selected.length} 个客户？`,
      content: '删除后不可恢复，已有询盘或单据记录的客户会被跳过。',
      onOk: async () => {
        const r = await customerApi.batchRemove(selected as number[]);
        const parts = [`已删除 ${r.deleted} 个`];
        if (r.referenced)
          parts.push(
            `${r.referenced} 个已有询盘或单据记录，不能删除，可改为禁用`,
          );
        if (r.missing) parts.push(`${r.missing} 个已不存在`);
        (r.referenced || r.missing ? message.warning : message.success)(
          parts.join('，'),
        );
        setSelected([]);
        await load();
      },
    });
  };

  const confirmExport = () => {
    modal.confirm({
      title: '导出客户',
      content: `将导出当前筛选条件下你可见的 ${total} 个客户。`,
      okText: '导出',
      cancelText: '取消',
      onOk: async () => {
        setExporting(true);
        try {
          const result = await customerApi.export(query);
          if (result.ok) message.success('已开始下载');
          else message.error(result.message);
        } finally {
          setExporting(false);
        }
      },
    });
  };

  const stacked = (
    top: React.ReactNode,
    bottom: React.ReactNode,
    topStyle?: React.CSSProperties,
  ) => (
    // 第二行没有内容就不显示，不放「—」占位（ui-redesign P4）
    <div style={{ lineHeight: 1.5, minWidth: 0 }}>
      <div style={topStyle}>{top}</div>
      {bottom ? (
        <div
          style={{
            fontSize: 12,
            color: palette.mute,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {bottom}
        </div>
      ) : null}
    </div>
  );

  const columns: TableColumnsType<CustomerListItem> = [
    {
      // 名称在前、编码在后（ui-redesign S9：编码是次要信息）
      title: '客户名称',
      dataIndex: 'name',
      width: 230,
      render: (_: string, r) => (
        <span title={r.displayName}>
          {r.nameMissing && <Pill tone="orange">未填客户名称</Pill>}
          {stacked(r.displayName, r.nameCn, {
            fontWeight: 600,
            color: palette.ink,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          })}
        </span>
      ),
    },
    {
      title: '客户编码',
      dataIndex: 'customerCode',
      width: 110,
      render: (v: string) => <span className="num">{v}</span>,
    },
    {
      title: '国家/地区',
      dataIndex: 'country',
      width: 110,
      render: (v: string) => zhOf(v) || '—',
    },
    {
      title: '主联系人',
      key: 'contact',
      width: 210,
      render: (_, r) =>
        stacked(
          [r.contactName, r.contactNameCn].filter(Boolean).join(' · ') || '—',
          r.contactEmail,
        ),
    },
    {
      title: (
        <HeadHelp
          title="经营等级"
          help="按近 12 个月的成交（最近一次下单、下单次数、金额）每天凌晨算出 S / A / B / C，S 最好；没有成交过的显示「未成交」。业务员可以上调或下调一级"
        />
      ),
      dataIndex: 'bizGrade',
      width: 130,
      sorter: true,
      showSorterTooltip: false,
      sortOrder:
        sort.sortField === 'bizGrade'
          ? sort.sortOrder === 'asc'
            ? 'ascend'
            : 'descend'
          : null,
      render: (v: number, r) =>
        r.bizGradeAdjust ? (
          <Tooltip
            title={`计算等级 ${GRADE_NAMES[r.bizGradeCalc]}，${r.bizGradeAdjust > 0 ? '上调' : '下调'}一级：${r.bizGradeAdjustReason || '—'}${r.bizGradeAdjustByName ? ` · ${r.bizGradeAdjustByName}` : ''}`}
          >
            <span>
              <GradeBadge value={v} adjusted />
            </span>
          </Tooltip>
        ) : (
          <GradeBadge value={v} />
        ),
    },
    {
      title: (
        <HeadHelp
          title="潜力等级"
          help="1–5 星，表示这个客户值不值得投入精力跟，所有客户都能评，也可以采纳背调报告的评分；没评的显示「未评」"
        />
      ),
      dataIndex: 'potentialRating',
      width: 130,
      sorter: true,
      showSorterTooltip: false,
      sortOrder:
        sort.sortField === 'potentialRating'
          ? sort.sortOrder === 'asc'
            ? 'ascend'
            : 'descend'
          : null,
      render: (v: number) => <Stars value={v} />,
    },
    {
      title: '档案完整度',
      dataIndex: 'profileMask',
      width: 150,
      sorter: true,
      showSorterTooltip: false,
      sortOrder:
        sort.sortField === 'profile'
          ? sort.sortOrder === 'asc'
            ? 'ascend'
            : 'descend'
          : null,
      render: (mask: number) => {
        const done = doneOf(mask);
        const missing = MODULE_SHORT.filter((_, i) => !(mask & (1 << i)));
        return (
          <Tooltip
            title={missing.length ? `缺：${missing.join('、')}` : '已建档'}
          >
            <span
              style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}
            >
              <span
                className="num"
                style={{
                  fontWeight: 600,
                  color: done === 5 ? palette.green : palette.orange,
                }}
              >
                {done}/5
              </span>
              <Progress5 mask={mask} />
            </span>
          </Tooltip>
        );
      },
    },
    {
      title: '客户角色',
      dataIndex: 'customerRole',
      width: 110,
      render: (v: number) => <RolePill value={v} />,
    },
    {
      title: '客户来源',
      dataIndex: 'sourceChannel',
      width: 150,
      render: (v: number, r) => (
        <EllipsisText
          text={
            r.aliLevel ? `${sourceLabel(v)} · ${r.aliLevel}` : sourceLabel(v)
          }
        />
      ),
    },
    ...(showOwner
      ? [
          {
            title: '负责业务员',
            dataIndex: 'ownerName',
            width: 110,
            render: (v?: string) => <EllipsisText text={v || '未分配'} />,
          },
        ]
      : []),
    {
      title: '状态',
      dataIndex: 'status',
      width: 72,
      render: (v: number, r) => (
        <StatusSwitch
          checked={v === 1}
          name={r.displayName}
          offText="禁用"
          disabled={!canStatus}
          loading={togglingId === r.id}
          onChange={(checked) => toggleStatus(r, checked)}
        />
      ),
    },
    // 列表页统一规范：创建时间、创建人、更新时间、更新人各一列
    ...auditColumns<CustomerListItem>(),
    {
      title: '操作',
      key: 'actions',
      // 常用的查看、编辑放行内，转移、放入公海、删除收进「更多」（ui-redesign S6）
      width: rowActionsWidth(canTransfer || canPool || canDelete ? 3 : 2),
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
            canEdit && {
              key: 'edit',
              label: '编辑',
              icon: <Pencil size={18} />,
              onClick: () => history.push(`${LIST_PATH}/${r.id}/edit`),
            },
          ]}
          more={[
            canTransfer && {
              key: 'transfer',
              label: '转移',
              icon: <ArrowLeftRight size={16} />,
            },
            canPool && {
              key: 'pool',
              label: '放入公海',
              icon: <Waves size={16} />,
            },
            canDelete && {
              key: 'delete',
              label: '删除',
              icon: <Trash2 size={16} />,
              danger: true,
            },
          ]}
          onMore={(key) => {
            if (key === 'transfer') setTransferTargets([r]);
            if (key === 'pool') setPoolTargets([r]);
            if (key === 'delete') confirmDelete(r);
          }}
        />
      ),
    },
  ];
  const columnsSet = useColumnSettings('customer', columns, {
    hidden: [
      '客户编码',
      '主联系人',
      '潜力等级',
      '档案完整度',
      '客户角色',
      '客户来源',
    ],
  });

  // 潜力等级、经营等级、档案完整度可以排序；取消排序回到默认（经营等级 S→C，再按潜力等级）
  const onTableChange: TableProps<CustomerListItem>['onChange'] = (
    _p,
    _f,
    sorter,
    extra,
  ) => {
    if (extra.action !== 'sort' || Array.isArray(sorter)) return;
    const field = (
      sorter.field === 'profileMask' ? 'profile' : sorter.field
    ) as CustomerQuery['sortField'];
    setSort(
      sorter.order
        ? {
            sortField: field,
            sortOrder: sorter.order === 'ascend' ? 'asc' : 'desc',
          }
        : {},
    );
    setPage(1);
  };

  const selectedRows = rows.filter((r) => selected.includes(r.id));

  /** 搜索条件里的下拉：标签在上，读屏名称与标签相同 */
  const select = (
    name: string,
    label: string,
    options: { value: string | number; label: string }[] | undefined,
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
  const input = (name: string, label: string, placeholder: string) => (
    <FilterField label={label}>
      <Form.Item name={name} noStyle>
        <Input
          allowClear
          placeholder={placeholder}
          aria-label={label}
          onPressEnter={() => search()}
        />
      </Form.Item>
    </FilterField>
  );
  const MORE_KEYS = [
    'country',
    'customerRole',
    'bizGrade',
    'missingModules',
    'sourceChannel',
    'ownerId',
    'status',
  ] as const;
  const moreCount = MORE_KEYS.filter((k) => {
    const v = (query as Record<string, unknown>)[k];
    return Array.isArray(v) ? v.length > 0 : v != null && v !== '';
  }).length;
  const withCount = (label: string, n?: number) =>
    n ? `${label} · ${n}` : label;

  return (
    <div>
      <PageHeader
        help={HELP.page}
        // 导入、导出、新增只属于「全部客户」页签：导出按这个页签的筛选条件
        extra={
          tab === 'all' && (
            <>
              <Tooltip title="批量导入即将上线">
                <span>
                  <Button type="text" icon={<Upload size={16} />} disabled>
                    导入
                  </Button>
                </span>
              </Tooltip>
              {canExport && (
                <Button
                  type="text"
                  icon={<Download size={16} />}
                  style={{ color: total === 0 ? undefined : palette.link }}
                  loading={exporting}
                  disabled={total === 0}
                  onClick={confirmExport}
                >
                  导出
                </Button>
              )}
              {canAdd && (
                <Button
                  type="primary"
                  icon={<Plus size={18} />}
                  onClick={() => history.push(`${LIST_PATH}/new`)}
                >
                  新增客户
                </Button>
              )}
            </>
          )
        }
      />
      <Segmented
        value={tab}
        onChange={(v) => setTab(v as 'all' | 'fill' | 'dups' | 'pool')}
        style={{ marginBottom: 16 }}
        options={[
          {
            value: 'all',
            label: `全部客户${tab === 'all' ? ` ${total}` : ''}`,
          },
          { value: 'fill', label: '档案补全' },
          {
            value: 'dups',
            label: `疑似重复${dupCount ? ` ${dupCount}` : ''}`,
          },
          {
            value: 'pool',
            label: `公海客户${poolCount ? ` ${poolCount}` : ''}`,
          },
        ]}
      />
      {tab === 'pool' ? (
        <PoolTab onCount={setPoolCount} />
      ) : tab === 'fill' ? (
        <CompletionTab />
      ) : tab === 'dups' ? (
        <DuplicatesTab
          canMerge={canMerge}
          onCount={setDupCount}
          onMerged={load}
        />
      ) : (
        <>
          <Form form={form} component={false}>
            <FilterCard
              onSearch={() => search()}
              onReset={reset}
              // 列宽 110：客户名称、客户编码、主要联系人、建档状态排一行
              minColumn={110}
              moreCount={moreCount}
              more={
                <>
                  {select('country', '国家/地区', countryOptions, {
                    showSearch: { optionFilterProp: 'label' },
                  })}
                  {select('customerRole', '客户角色', ROLE_OPTIONS)}
                  {select('bizGrade', '经营等级', BIZ_GRADE_OPTIONS)}
                  {select('missingModules', '缺的模块', MODULE_OPTIONS, {
                    mode: 'multiple',
                    maxTagCount: 'responsive',
                  })}
                  {select('sourceChannel', '客户来源', sourceOptions)}
                  {showOwner &&
                    select(
                      'ownerId',
                      '负责业务员',
                      owners?.owners.map((o) => ({
                        value: o.id,
                        label: o.name,
                      })),
                      { showSearch: { optionFilterProp: 'label' } },
                    )}
                  {select('status', '状态', STATUS_OPTIONS, {
                    // 状态下拉切换后立即查询（interface-style）
                    onChange: () => search(),
                  })}
                </>
              }
            >
              <FilterField label="客户名称" span={2} help={HELP.name}>
                <Form.Item name="name" noStyle>
                  <Input
                    allowClear
                    prefix={<Search size={16} />}
                    placeholder="英文名、中文名或简称"
                    aria-label="客户名称"
                    onPressEnter={() => search()}
                  />
                </Form.Item>
              </FilterField>
              {input('customerCode', '客户编码', '客户编码')}
              {input('contactName', '主要联系人', '联系人姓名')}
              {select(
                'profileStatus',
                '建档状态',
                [
                  { value: 'done', label: withCount('已建档', counts?.done) },
                  {
                    value: 'pending',
                    label: withCount('待完善', counts?.pending),
                  },
                ],
                // 切换建档状态立即查询（doc-list-unify 状态下拉的写法）
                { onChange: () => search() },
              )}
            </FilterCard>
          </Form>

          <MainCard
            settings={columnsSet.settings}
            left={
              selected.length > 0 ? (
                <>
                  <b style={{ color: palette.ink }}>
                    已选 <span className="num">{selected.length}</span> 条
                  </b>
                  <button
                    type="button"
                    className="zhul-link"
                    onClick={() => setSelected([])}
                  >
                    取消选择
                  </button>
                </>
              ) : (
                `共 ${total} 条`
              )
            }
            right={
              selected.length > 0 && (
                <div
                  role="toolbar"
                  aria-label="批量操作"
                  style={{ display: 'flex', gap: 8 }}
                >
                  {canTransfer && (
                    <Button
                      icon={<ArrowLeftRight size={16} />}
                      onClick={() => setTransferTargets(selectedRows)}
                    >
                      批量转移
                    </Button>
                  )}
                  {canPool && (
                    <Button
                      icon={<Waves size={16} />}
                      onClick={() => setPoolTargets(selectedRows)}
                    >
                      放入公海
                    </Button>
                  )}
                  {canDelete && (
                    <Button
                      danger
                      icon={<Trash2 size={16} />}
                      onClick={confirmBatchDelete}
                    >
                      批量删除
                    </Button>
                  )}
                </div>
              )
            }
          >
            {error ? (
              <ErrorHint message={error} onRetry={load} />
            ) : firstLoad ? (
              <Skeleton active paragraph={{ rows: 6 }} />
            ) : (
              <Table<CustomerListItem>
                rowKey="id"
                columns={columnsSet.columns}
                dataSource={rows}
                loading={loading}
                sticky
                scroll={{ x: tableScrollX(columnsSet.columns) }}
                onChange={onTableChange}
                rowSelection={{
                  selectedRowKeys: selected,
                  onChange: setSelected,
                  // antd 的类型里没有 aria-label，但会透传给复选框；读屏靠它知道选的是哪一行
                  getCheckboxProps: (r: CustomerListItem) =>
                    ({
                      'aria-label': `选择 ${r.displayName}`,
                    }) as unknown as Partial<CheckboxProps>,
                }}
                pagination={{
                  ...PAGER_STYLE,
                  current: page,
                  pageSize,
                  total,
                  pageSizeOptions: [10, 20, 50],
                  onChange: (p, s) => {
                    setPage(p);
                    setPageSize(s);
                    setSelected([]);
                  },
                }}
                locale={{
                  emptyText: tableEmpty(
                    hasCondition ? (
                      <EmptyHint
                        title="没有找到匹配的客户"
                        secondaryText="清空筛选"
                        onSecondary={reset}
                      />
                    ) : (
                      <EmptyHint
                        title="还没有客户档案"
                        description="成交或需要做单的客户建档后，报价单和 PI 会自动带出客户信息。"
                        actionText={canAdd ? '新增客户' : undefined}
                        onAction={() => history.push(`${LIST_PATH}/new`)}
                      />
                    ),
                    loading,
                  ),
                }}
              />
            )}
          </MainCard>
        </>
      )}

      <TransferModal
        open={transferTargets.length > 0}
        customers={transferTargets}
        onClose={() => setTransferTargets([])}
        onDone={() => {
          setTransferTargets([]);
          setSelected([]);
          load();
        }}
      />
      <PutIntoPoolModal
        open={poolTargets.length > 0}
        customers={poolTargets.map((c) => ({
          id: c.id,
          name: c.displayName,
          ownerName: c.ownerName,
        }))}
        onClose={() => setPoolTargets([])}
        onDone={() => {
          setPoolTargets([]);
          setSelected([]);
          setPoolCount((n) => (n ?? 0) + poolTargets.length);
          load();
        }}
      />
    </div>
  );
};

export default CustomerListPage;
