import { history, Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Input, Select, Table } from 'antd';
import { Eye, History, Plus, Search, Settings, Waypoints } from 'lucide-react';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { UserName } from '@/components/UserName';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import {
  MONO,
  ModePill,
  RELEASE_TONE,
  ReleasesDrawer,
  StatusPill,
  TakeoverPill,
} from './components';
import {
  MODE_AUTO,
  PERM_EDIT,
  readBizError,
  type Site,
  siteApi,
  TAKEOVER_PENDING,
} from './service';

const Sub: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
      {children}
    </div>
  );
};

/** 站点商品：接管中看导入数，自动发布看已上线数，只复制看已发布数 */
const ProductsCell: React.FC<{ site: Site }> = ({ site: s }) => {
  const sum = s.summary;
  if (!sum) return <span>—</span>;
  const importing =
    s.publishMode === MODE_AUTO && s.takeoverStatus === TAKEOVER_PENDING;
  const [count, label] = importing
    ? [sum.importedCount, '已导入']
    : [sum.publishedCount, s.publishMode === MODE_AUTO ? '已上线' : '已发布'];
  return (
    <div>
      <b style={{ fontVariantNumeric: 'tabular-nums' }}>{count}</b>
      <Sub>
        {label} · 范围 {sum.scopeCount}
      </Sub>
    </div>
  );
};

const LastReleaseCell: React.FC<{ site: Site }> = ({ site: s }) => {
  const r = s.summary?.lastRelease;
  if (!r) return <span>—</span>;
  return (
    <div>
      <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
        <span style={{ fontFamily: MONO }}>{r.releaseNo}</span>
        <Pill tone={RELEASE_TONE[r.status] ?? 'gray'}>{r.statusName}</Pill>
      </span>
      <Sub>
        <UserName value={r.createBy} /> · {formatDateTime(r.createTime)} ·{' '}
        {r.itemCount} 个商品
      </Sub>
    </div>
  );
};

const TakeoverCell: React.FC<{ site: Site }> = ({ site: s }) => {
  const { palette } = useAppTheme();
  if (s.publishMode !== MODE_AUTO) {
    return (
      <div>
        <Pill tone="green">以 ERP 为准</Pill>
        <Sub>手工发布</Sub>
      </div>
    );
  }
  const pending = s.summary?.pendingDiffs;
  return (
    <div>
      <TakeoverPill site={s} />
      {s.takeoverStatus === TAKEOVER_PENDING && !!pending && (
        <div style={{ fontSize: 12, color: palette.orange, marginTop: 2 }}>
          {pending} 条差异未确认
        </div>
      )}
    </div>
  );
};

/** 页头帮助：原来表格下方的两条说明 */
const HELP =
  '网址在全平台唯一；令牌、私钥、IndexNow 密钥加密保存，只显示是否已配置和末四位。停用的站点不能发布；已发布的商品留在站点上';

const WebsitesPage: React.FC = () => {
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access[PERM_EDIT];
  const { palette } = useAppTheme();
  const [rows, setRows] = useState<Site[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<number>();
  const [releasesOf, setReleasesOf] = useState<Site>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await siteApi.list());
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(load);

  const shown = useMemo(() => {
    const k = keyword.trim().toLowerCase();
    return rows.filter(
      (s) =>
        (status === undefined || s.status === status) &&
        (!k ||
          s.name.toLowerCase().includes(k) ||
          s.url.toLowerCase().includes(k)),
    );
  }, [rows, keyword, status]);

  const columns: TableColumnsType<Site> = [
    {
      title: '站点',
      key: 'site',
      width: 240,
      fixed: 'left',
      render: (_, s) => (
        <div>
          <Link to={`/operations/websites/${s.id}`} style={{ fontWeight: 600 }}>
            {s.name}
          </Link>
          <div style={{ fontFamily: MONO, fontSize: 12, color: palette.sub }}>
            {s.url}
          </div>
        </div>
      ),
    },
    {
      title: '语言',
      key: 'languages',
      width: 100,
      render: (_, s) => (
        <span style={{ display: 'inline-flex', gap: 6 }}>
          {s.languages.map((l) => (
            <Pill key={l.code} tone="gray">
              {l.code.toUpperCase()}
            </Pill>
          ))}
        </span>
      ),
    },
    {
      title: '发布方式',
      key: 'mode',
      width: 110,
      render: (_, s) => <ModePill mode={s.publishMode} />,
    },
    {
      title: '接管状态',
      key: 'takeover',
      width: 150,
      render: (_, s) => <TakeoverCell site={s} />,
    },
    {
      title: '站点商品',
      key: 'products',
      width: 130,
      render: (_, s) => <ProductsCell site={s} />,
    },
    {
      title: '最近发布',
      key: 'release',
      width: 240,
      render: (_, s) => <LastReleaseCell site={s} />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) => <StatusPill status={v} />,
    },
    ...auditColumns<Site>(),
    {
      title: '操作',
      key: 'actions',
      width: rowActionsWidth(3),
      fixed: 'right',
      render: (_, s) => (
        <RowActions
          inline={[
            {
              key: 'open',
              label: canEdit ? '配置' : '查看',
              icon: canEdit ? <Settings size={18} /> : <Eye size={18} />,
              onClick: () => history.push(`/operations/websites/${s.id}`),
            },
            !!s.summary?.lastRelease && {
              key: 'releases',
              label: '发布记录',
              icon: <History size={18} />,
              onClick: () => setReleasesOf(s),
            },
          ]}
          more={[
            canEdit &&
              s.publishMode === MODE_AUTO &&
              s.takeoverStatus === TAKEOVER_PENDING && {
                key: 'takeover',
                label: '接管',
                icon: <Waypoints size="1em" />,
              },
          ]}
          onMore={() => history.push(`/operations/websites/${s.id}#takeover`)}
        />
      ),
    },
  ];

  const columnsSet = useColumnSettings('operations-websites', columns);

  return (
    <div>
      <PageHeader
        help={HELP}
        extra={
          canEdit && (
            <Button
              type="primary"
              icon={<Plus size="1em" />}
              onClick={() => history.push('/operations/websites/new')}
            >
              新建站点
            </Button>
          )
        }
      />
      <FilterCard
        onReset={() => {
          setKeyword('');
          setStatus(undefined);
        }}
      >
        <FilterField label="关键词" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="站点名称、网址"
            aria-label="关键词"
          />
        </FilterField>
        <FilterField label="状态">
          <Select
            allowClear
            value={status}
            onChange={setStatus}
            placeholder="全部"
            options={[
              { value: 1, label: '启用' },
              { value: 0, label: '停用' },
            ]}
            aria-label="状态"
          />
        </FilterField>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard
          settings={columnsSet.settings}
          count={shown.length}
          unit="个站点"
          loading={loading}
        >
          <Table<Site>
            rowKey="id"
            columns={columnsSet.columns}
            dataSource={shown}
            loading={loading}
            scroll={{ x: tableScrollX(columnsSet.columns) }}
            pagination={false}
            locale={{
              emptyText: tableEmpty(
                rows.length
                  ? '没有符合条件的站点'
                  : '还没有站点，点「新建站点」添加',
                loading,
              ),
            }}
          />
        </MainCard>
      )}
      <ReleasesDrawer
        site={releasesOf}
        onClose={() => setReleasesOf(undefined)}
      />
    </div>
  );
};

export default WebsitesPage;
