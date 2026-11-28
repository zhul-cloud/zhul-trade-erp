import { history, Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Input,
  Modal,
  Skeleton,
  Table,
  Tabs,
  Tooltip,
} from 'antd';
import {
  Check,
  CircleAlert,
  Download,
  Flag,
  Info,
  LoaderCircle,
  RefreshCw,
} from 'lucide-react';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { PageHeader } from '@/components/ListLayout';
import { usePageTitle, useTabActive } from '@/components/PageTabs/hooks';
import { UserName } from '@/components/UserName';
import { Card, Pill, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import { MONO } from './components';
import {
  type AcceptRecord,
  type CompareResult,
  type Diff,
  type DiffKey,
  fieldLabel,
  type ImportMatched,
  type ImportResult,
  jobPath,
  PERM_EDIT,
  readBizError,
  type Site,
  type SiteEntry,
  type SiteJob,
  siteApi,
  TAKEOVER_PENDING,
  WEBSITE_PATH,
} from './service';

/** 1-执行中、2-已完成、3-失败、4-已确认 */
const RUNNING = 1;
const DONE = 2;
const FAILED = 3;
const CONFIRMED = 4;

const diffKey = (d: DiffKey) => `${d.model}\n${d.field}`;
const str = (v: unknown) => (v == null ? '' : String(v));
const showValue = (v: unknown) =>
  v == null || v === ''
    ? '（空）'
    : typeof v === 'string'
      ? v
      : JSON.stringify(v);
const prettyValue = (v: unknown) =>
  v == null || v === ''
    ? '（空）'
    : typeof v === 'string'
      ? v
      : JSON.stringify(v, null, 2);

const duration = (from?: string, to?: string) => {
  if (!from || !to) return '';
  const s = Math.max(
    0,
    Math.round((new Date(to).getTime() - new Date(from).getTime()) / 1000),
  );
  return s >= 60 ? `${Math.floor(s / 60)} 分 ${s % 60} 秒` : `${s} 秒`;
};

const downloadCsv = (name: string, rows: string[][]) => {
  const esc = (s: string) => `"${s.replace(/"/g, '""')}"`;
  const text = rows.map((r) => r.map(esc).join(',')).join('\r\n');
  const url = URL.createObjectURL(
    new Blob([`﻿${text}`], { type: 'text/csv;charset=utf-8' }),
  );
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
};

// ================================================================ 小组件

const Stat: React.FC<{
  label: string;
  value: React.ReactNode;
  sub?: string;
  color?: string;
  active?: boolean;
}> = ({ label, value, sub, color, active }) => {
  const { palette } = useAppTheme();
  return (
    <Card
      style={{
        padding: '16px 18px',
        ...(active ? { borderColor: palette.link } : {}),
      }}
    >
      <div style={{ fontSize: 13, color: palette.sub }}>{label}</div>
      <div
        style={{
          fontSize: 24,
          fontWeight: 600,
          marginTop: 4,
          color: color ?? palette.ink,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {value}
      </div>
      {sub && (
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
          {sub}
        </div>
      )}
    </Card>
  );
};

const StatRow: React.FC<{ children: React.ReactNode; cols: number }> = ({
  children,
  cols,
}) => {
  const wide = useWide();
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${wide ? cols : 2}, minmax(0, 1fr))`,
        gap: 16,
        marginBottom: 16,
      }}
    >
      {children}
    </div>
  );
};

/** 页面底部的说明与操作 */
const Footer: React.FC<{
  tone: 'info' | 'warn' | 'mute';
  text: React.ReactNode;
  actions?: React.ReactNode;
}> = ({ tone, text, actions }) => {
  const { palette } = useAppTheme();
  const color =
    tone === 'warn'
      ? palette.orange
      : tone === 'info'
        ? palette.link
        : palette.mute;
  return (
    <div
      style={{
        position: 'sticky',
        bottom: 0,
        marginTop: 16,
        padding: '12px 0',
        background: palette.canvas,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          flexWrap: 'wrap',
          padding: '12px 18px',
          borderRadius: 14,
          border: `1px solid ${tone === 'warn' ? palette.orange : tone === 'info' ? palette.primaryLine : palette.hairline}`,
          background:
            tone === 'warn'
              ? palette.orangeSoft
              : tone === 'info'
                ? palette.primarySoft
                : palette.card,
        }}
      >
        <span style={{ flex: 1, minWidth: 240, fontSize: 13, color }}>
          {tone === 'warn' ? <CircleAlert size="1em" /> : <Info size="1em" />}{' '}
          {text}
        </span>
        {actions && (
          <span style={{ display: 'inline-flex', gap: 12 }}>{actions}</span>
        )}
      </div>
    </div>
  );
};

const Product: React.FC<{ brand?: string; model: string; name?: string }> = ({
  brand,
  model,
  name,
}) => {
  const { palette } = useAppTheme();
  return (
    <div>
      <span style={{ color: palette.sub }}>{brand || '—'}</span>
      <span style={{ color: palette.mute }}> · </span>
      <b style={{ fontFamily: MONO }}>{model}</b>
      {name && (
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
          {name}
        </div>
      )}
    </div>
  );
};

// ================================================================ 导入预览

const ImportView: React.FC<{
  job: SiteJob;
  site: Site;
  /** 最新一次导入、读取完成、站点接管中、有权限：可以确认 */
  confirmable: boolean;
  latestId?: number;
  onChanged: (j: SiteJob) => void;
}> = ({ job, site, confirmable, latestId, onChanged }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const r = job.result as ImportResult;
  const [saving, setSaving] = useState(false);
  const unmatched = r.unmatched ?? [];
  const missing = r.missing ?? [];

  const confirm = async () => {
    setSaving(true);
    try {
      onChanged(await siteApi.confirmImport(job.id));
      message.success(`已导入 ${r.matched.length} 个商品`);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const download = () =>
    downloadCsv(`未匹配商品-${site.name}.csv`, [
      ['品牌', '型号', '链接别名'],
      ...unmatched.map((u) => [str(u.brand), str(u.model), str(u.slug)]),
    ]);

  const matchedCols: TableColumnsType<ImportMatched> = [
    {
      title: '商品',
      key: 'product',
      width: 220,
      render: (_, m) => (
        <Product brand={m.brand} model={m.model} name={m.name} />
      ),
    },
    {
      title: '链接别名',
      dataIndex: 'slug',
      width: 170,
      ellipsis: true,
      render: (v: string) => (
        <span style={{ fontFamily: MONO, fontSize: 12 }}>{v}</span>
      ),
    },
    {
      title: '售价',
      align: 'right' as const,
      dataIndex: 'sellPrice',
      width: 90,
      render: (v?: number | null) =>
        v == null || Number.isNaN(Number(v)) ? (
          <span style={{ color: palette.mute }}>询价</span>
        ) : (
          <b style={{ fontVariantNumeric: 'tabular-nums' }}>
            ${Number(v).toFixed(2)}
          </b>
        ),
    },
    { title: '图片', dataIndex: 'photos', width: 56 },
    { title: '视频', dataIndex: 'videos', width: 56 },
    {
      title: '与 ERP 现有内容不同',
      dataIndex: 'diffFields',
      width: 230,
      render: (v: string[]) =>
        v?.length ? (
          <span style={{ display: 'inline-flex', gap: 6, flexWrap: 'wrap' }}>
            {v.slice(0, 4).map((f) => (
              <Pill key={f} tone="violet">
                {fieldLabel(f)}
              </Pill>
            ))}
            {v.length > 4 && (
              <Tooltip title={v.slice(4).map(fieldLabel).join('、')}>
                <span style={{ fontSize: 12, color: palette.mute }}>
                  等 {v.length} 项
                </span>
              </Tooltip>
            )}
          </span>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
  ];

  const entryCols: TableColumnsType<SiteEntry> = [
    { title: '品牌', key: 'brand', render: (_, u) => str(u.brand) || '—' },
    {
      title: '型号',
      key: 'model',
      render: (_, u) => <b style={{ fontFamily: MONO }}>{str(u.model)}</b>,
    },
    {
      title: '链接别名',
      key: 'slug',
      render: (_, u) => (
        <span style={{ fontFamily: MONO, fontSize: 12 }}>{str(u.slug)}</span>
      ),
    },
  ];

  const tabs = [
    {
      key: 'matched',
      label: `已匹配 ${r.matched.length}`,
      children: (
        <Table<ImportMatched>
          rowKey="productId"
          size="middle"
          columns={matchedCols}
          scroll={{ x: tableScrollX(matchedCols) }}
          dataSource={r.matched}
          pagination={{
            pageSize: 20,
            showSizeChanger: false,
            hideOnSinglePage: true,
          }}
          locale={{ emptyText: '没有匹配上的商品' }}
        />
      ),
    },
    {
      key: 'unmatched',
      label: `未匹配 ${unmatched.length}`,
      children: (
        <Table<SiteEntry>
          rowKey={(u) => `${str(u.brand)}\n${str(u.model)}\n${str(u.slug)}`}
          size="middle"
          columns={entryCols}
          dataSource={unmatched}
          pagination={{
            pageSize: 20,
            showSizeChanger: false,
            hideOnSinglePage: true,
          }}
          locale={{ emptyText: '站点上的商品都匹配上了' }}
        />
      ),
    },
    ...(missing.length
      ? [
          {
            key: 'missing',
            label: `站点已不存在 ${missing.length}`,
            children: (
              <>
                <div
                  style={{ fontSize: 13, color: palette.sub, marginBottom: 12 }}
                >
                  以前导入过、这次站点上没有的商品，ERP
                  里的站点商品和发品任务不删除，请确认站点上是否删错
                </div>
                <Table
                  rowKey="productId"
                  size="middle"
                  dataSource={missing}
                  pagination={false}
                  columns={[
                    { title: '品牌', dataIndex: 'brand' },
                    {
                      title: '型号',
                      dataIndex: 'model',
                      render: (v: string) => (
                        <b style={{ fontFamily: MONO }}>{v}</b>
                      ),
                    },
                  ]}
                />
              </>
            ),
          },
        ]
      : []),
    {
      key: 'fields',
      label: '字段说明',
      children: (
        <div
          style={{
            display: 'grid',
            gap: 12,
            fontSize: 13,
            color: palette.sub,
            lineHeight: 1.7,
          }}
        >
          <div>
            <b style={{ color: palette.ink }}>品牌、型号、系列、站点品类</b>
            ：始终取 ERP；链接别名用站点现在的，导入后锁定，上线链接不变
          </div>
          <div>
            <b style={{ color: palette.ink }}>
              描述、规格、FAQ、应用场景等内容
            </b>
            ：导入后先保留站点原有内容；ERP 里对应资料或营销内容更新后，改用 ERP
            的
          </div>
          <div>
            <b style={{ color: palette.ink }}>售价</b>
            ：取站点现在的售价，来源记为「站点导入」；以后按站点的售价规则重新计算
          </div>
          <div>
            <b style={{ color: palette.ink }}>站点独有字段</b>
            ：ERP 没有对应位置，原样保存，以后发布时原样带上
          </div>
          <div>
            <b style={{ color: palette.ink }}>与 ERP 现有内容不同</b>
            ：站点内容和 ERP
            按现有资料生成的内容不一样的字段，导入后保留站点的，可以在发品任务里改用
            ERP 内容
          </div>
        </div>
      ),
    },
  ];

  const side = (
    <div style={{ display: 'grid', gap: 16, alignContent: 'start' }}>
      <Card
        title={`未匹配 ${unmatched.length}`}
        extra={
          unmatched.length > 0 && (
            <Button
              type="link"
              style={{ padding: 0 }}
              icon={<Download size="1em" />}
              onClick={download}
            >
              下载清单
            </Button>
          )
        }
        style={{ padding: 20 }}
      >
        <div style={{ fontSize: 12, color: palette.mute, marginBottom: 12 }}>
          站点上有、商品档案里找不到的品牌 +
          型号。先在商品档案或商品候选里建好，再重新导入；不处理的以 ERP
          为准后原样留在站点上
        </div>
        {unmatched.length === 0 ? (
          <div style={{ fontSize: 13, color: palette.green }}>
            <Check size="1em" /> 全部匹配
          </div>
        ) : (
          <div style={{ display: 'grid', gap: 6, fontSize: 13 }}>
            {unmatched.slice(0, 5).map((u) => (
              <div
                key={`${str(u.brand)}\n${str(u.model)}`}
                style={{ display: 'flex', gap: 12 }}
              >
                <span style={{ color: palette.sub, width: 110, flexShrink: 0 }}>
                  {str(u.brand) || '—'}
                </span>
                <b style={{ fontFamily: MONO }}>{str(u.model)}</b>
              </div>
            ))}
            {unmatched.length > 5 && (
              <div style={{ color: palette.mute }}>
                还有 {unmatched.length - 5} 个
              </div>
            )}
          </div>
        )}
      </Card>
      <Card title="站点独有字段" style={{ padding: 20 }}>
        <div style={{ fontSize: 12, color: palette.mute, marginBottom: 12 }}>
          ERP 没有对应位置，原样保存在站点商品里，以后发布时原样带上
        </div>
        {r.siteOnlyFields?.length ? (
          <span style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap' }}>
            {r.siteOnlyFields.map((f) => (
              <Pill key={f} tone="gray">
                <span style={{ fontFamily: MONO }}>{f}</span>
              </Pill>
            ))}
          </span>
        ) : (
          <span style={{ fontSize: 13, color: palette.mute }}>没有</span>
        )}
      </Card>
      {!!r.warnings?.length && (
        <Card title="读取提示" style={{ padding: 20 }}>
          <div
            style={{
              display: 'grid',
              gap: 6,
              fontSize: 12,
              color: palette.orange,
            }}
          >
            {r.warnings.map((w) => (
              <div key={w}>{w}</div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );

  let footer: React.ReactNode;
  if (site.takeoverStatus !== TAKEOVER_PENDING) {
    footer = (
      <Footer
        tone="mute"
        text="站点已经以 ERP 为准，不能再导入；这次结果只能查看"
      />
    );
  } else if (job.status === CONFIRMED) {
    footer = (
      <Footer
        tone="mute"
        text={`这次导入已经确认，${r.matched.length} 个商品已建立站点商品。可以重新读取，再导入一次`}
      />
    );
  } else if (latestId && latestId !== job.id) {
    footer = (
      <Footer
        tone="mute"
        text="有更新的导入结果，这次结果只能查看"
        actions={<Link to={jobPath(latestId)}>查看最新结果</Link>}
      />
    );
  } else if (confirmable) {
    footer = (
      <Footer
        tone="info"
        text={`确认后：${r.matched.length} 个商品建立站点商品（链接别名锁定、售价来源「站点导入」），${site.name} 的发品任务标为「已发布」并带上线链接；不修改共享商品库、营销内容和素材库。可以重复导入`}
        actions={
          <>
            <Button onClick={() => history.push(`${WEBSITE_PATH}/${site.id}`)}>
              取消
            </Button>
            <Button
              type="primary"
              icon={<Check size="1em" />}
              loading={saving}
              disabled={r.matched.length === 0}
              onClick={confirm}
            >
              确认导入 {r.matched.length} 个
            </Button>
          </>
        }
      />
    );
  }

  return (
    <>
      <StatRow cols={4}>
        <Stat label="站点上的商品" value={r.total} />
        <Stat
          label="已匹配"
          value={r.matched.length}
          sub="按品牌 + 型号，不区分大小写"
          color={palette.green}
          active
        />
        <Stat
          label="未匹配"
          value={unmatched.length}
          sub="可下载清单"
          color={unmatched.length ? palette.orange : undefined}
        />
        <Stat
          label="站点已不存在"
          value={missing.length}
          sub="重复导入时列出"
          color={missing.length ? palette.red : undefined}
        />
      </StatRow>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide ? 'minmax(0, 1fr) 380px' : '1fr',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <Card style={{ padding: '8px 20px 20px' }}>
          {/* lint-ui-ignore page-tabs: 卡片里的分区页签 */}
          <Tabs items={tabs} />
        </Card>
        {side}
      </div>
      {footer}
    </>
  );
};

// ================================================================ 一致性比对

const CompareView: React.FC<{
  job: SiteJob;
  site: Site;
  /** 最新一次比对、比对完成、站点接管中、有权限：可以确认差异、开始以 ERP 为准 */
  editable: boolean;
  latestId?: number;
  onChanged: (j: SiteJob) => void;
  onTakeover: (s: Site) => void;
}> = ({ job, site, editable, latestId, onChanged, onTakeover }) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const r = job.result as CompareResult;
  const brands = r.brands ?? {};
  const accepted = useMemo(() => {
    const m = new Map<string, AcceptRecord>();
    // 还没有确认记录时后端返回空对象
    for (const a of Array.isArray(job.accepted) ? job.accepted : [])
      m.set(diffKey(a), a);
    return m;
  }, [job.accepted]);
  const diffs = r.differences ?? [];
  const pending =
    job.pending ?? diffs.filter((d) => !accepted.has(diffKey(d))).length;
  const [selected, setSelected] = useState<React.Key[]>([]);
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [one, setOne] = useState<Diff>();
  const [oneReason, setOneReason] = useState('');
  const [view, setView] = useState<Diff>();

  const accept = async (items: DiffKey[], why: string) => {
    if (!why.trim()) {
      message.error('请写明接受差异的原因');
      return false;
    }
    setSaving(true);
    try {
      onChanged(await siteApi.accept(job.id, items, why.trim()));
      message.success(`已确认接受 ${items.length} 条差异`);
      return true;
    } catch (e) {
      message.error(readBizError(e).message);
      return false;
    } finally {
      setSaving(false);
    }
  };

  const acceptSelected = async () => {
    const items = diffs
      .filter((d) => selected.includes(diffKey(d)))
      .map(({ model, field }) => ({ model, field }));
    if (await accept(items, reason)) {
      setSelected([]);
      setReason('');
    }
  };

  const takeover = () =>
    modal.confirm({
      title: '开始以 ERP 为准？',
      content:
        '之后这个站点的商品内容都从 ERP 发布，不能再导入、比对，也不能退回「接管中」。',
      okText: '开始以 ERP 为准',
      onOk: async () => {
        try {
          onTakeover(await siteApi.takeover(site.id));
          message.success('已开始以 ERP 为准');
        } catch (e) {
          message.error(readBizError(e).message);
          throw e;
        }
      },
    });

  const valueCell = (v: unknown, color: string) => (
    <div
      style={{
        fontFamily: MONO,
        fontSize: 12,
        color,
        display: '-webkit-box',
        WebkitLineClamp: 2,
        WebkitBoxOrient: 'vertical',
        overflow: 'hidden',
        wordBreak: 'break-all',
      }}
    >
      {showValue(v)}
    </div>
  );

  const diffCols: TableColumnsType<Diff> = [
    {
      title: '商品',
      key: 'product',
      width: 220,
      render: (_, d) => <Product brand={brands[d.model]} model={d.model} />,
    },
    {
      title: '字段',
      dataIndex: 'field',
      width: 120,
      render: (f: string) => fieldLabel(f),
    },
    {
      title: '站点现值',
      dataIndex: 'site',
      render: (v) => valueCell(v, palette.red),
    },
    {
      title: 'ERP 生成值',
      dataIndex: 'erp',
      render: (v) => valueCell(v, palette.green),
    },
    {
      title: '处理',
      key: 'action',
      width: 220,
      render: (_, d) => {
        const a = accepted.get(diffKey(d));
        return (
          <div>
            {a ? (
              <>
                <span style={{ color: palette.green, fontWeight: 600 }}>
                  <Check size="1em" /> 已接受
                </span>
                <div
                  style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}
                >
                  {a.by} · {a.reason}
                </div>
              </>
            ) : null}
            <span style={{ display: 'inline-flex', gap: 12 }}>
              {!a && editable && (
                <Button
                  type="link"
                  size="small"
                  style={{ padding: 0 }}
                  onClick={() => {
                    setOne(d);
                    setOneReason('');
                  }}
                >
                  确认接受
                </Button>
              )}
              <Button
                type="link"
                size="small"
                style={{ padding: 0 }}
                onClick={() => setView(d)}
              >
                查看
              </Button>
            </span>
          </div>
        );
      },
    },
  ];

  const modelList = (models: string[], note: string, empty: string) => (
    <>
      <div style={{ fontSize: 13, color: palette.sub, marginBottom: 12 }}>
        {note}
      </div>
      <Table
        rowKey={(m) => m}
        size="middle"
        dataSource={models}
        pagination={{
          pageSize: 20,
          showSizeChanger: false,
          hideOnSinglePage: true,
        }}
        locale={{ emptyText: empty }}
        columns={[
          {
            title: '品牌',
            key: 'brand',
            render: (_, m: string) => brands[m] || '—',
          },
          {
            title: '型号',
            key: 'model',
            render: (_, m: string) => <b style={{ fontFamily: MONO }}>{m}</b>,
          },
        ]}
      />
    </>
  );

  const tabs = [
    {
      key: 'diffs',
      label: `有差异 ${diffs.length}`,
      children: (
        <>
          {editable && pending > 0 && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                flexWrap: 'wrap',
                padding: '10px 14px',
                marginBottom: 12,
                borderRadius: 12,
                border: `1px solid ${selected.length ? palette.link : palette.hairline}`,
                // lint-ui-ignore primary-soft: 勾选后的批量操作条
                background: selected.length
                  ? palette.primarySoft
                  : palette.inset,
              }}
            >
              <b style={{ fontSize: 13 }}>
                {selected.length
                  ? `已选 ${selected.length} 条`
                  : '勾选差异后批量确认接受'}
              </b>
              <span style={{ flex: 1 }} />
              <Input
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="接受原因（必填）"
                maxLength={200}
                style={{ width: 300 }}
                aria-label="接受原因"
              />
              <Button
                disabled={!selected.length || !reason.trim()}
                loading={saving}
                onClick={acceptSelected}
              >
                批量确认接受
              </Button>
            </div>
          )}
          <Table<Diff>
            rowKey={diffKey}
            size="middle"
            columns={diffCols}
            dataSource={diffs}
            pagination={{
              pageSize: 50,
              showSizeChanger: false,
              hideOnSinglePage: true,
            }}
            rowSelection={
              editable && pending > 0
                ? {
                    selectedRowKeys: selected,
                    onChange: setSelected,
                    preserveSelectedRowKeys: true,
                    getCheckboxProps: (d) => ({
                      disabled: accepted.has(diffKey(d)),
                    }),
                  }
                : undefined
            }
            locale={{ emptyText: '站点内容和 ERP 生成的完全一致' }}
          />
        </>
      ),
    },
    {
      key: 'site',
      label: `只在站点上有 ${r.onlyOnSite?.length ?? 0}`,
      children: modelList(
        r.onlyOnSite ?? [],
        '没有匹配到商品档案的站点商品，保留在站点数据里，原样不动',
        '没有',
      ),
    },
    ...(r.onlyInErp?.length
      ? [
          {
            key: 'erp',
            label: `只在 ERP 里有 ${r.onlyInErp.length}`,
            children: modelList(
              r.onlyInErp,
              'ERP 里有站点商品、站点上还没有，以 ERP 为准后发布时会加到站点上',
              '没有',
            ),
          },
        ]
      : []),
  ];

  let footer: React.ReactNode;
  if (site.takeoverStatus !== TAKEOVER_PENDING) {
    footer = (
      <Footer
        tone="mute"
        text={
          <>
            <UserName value={site.takeoverBy} /> 于{' '}
            {formatDateTime(site.takeoverAt)} 开始以 ERP 为准
          </>
        }
      />
    );
  } else if (latestId && latestId !== job.id) {
    footer = (
      <Footer
        tone="mute"
        text="有更新的比对结果，这次结果只能查看"
        actions={<Link to={jobPath(latestId)}>查看最新结果</Link>}
      />
    );
  } else if (editable) {
    footer =
      pending > 0 ? (
        <Footer
          tone="warn"
          text={`还有 ${pending} 条差异没有确认。差异全部确认后才能开始以 ERP 为准；之后不能退回接管中`}
          actions={
            <Button icon={<Flag size="1em" />} disabled>
              开始以 ERP 为准
            </Button>
          }
        />
      ) : (
        <Footer
          tone="info"
          text="差异都已确认。开始以 ERP 为准后，这个站点的商品都从 ERP 发布，不能退回接管中"
          actions={
            <Button
              type="primary"
              icon={<Flag size="1em" />}
              onClick={takeover}
            >
              开始以 ERP 为准
            </Button>
          }
        />
      );
  }

  return (
    <>
      <StatRow cols={r.onlyInErp?.length ? 4 : 3}>
        <Stat label="一致" value={r.same} color={palette.green} />
        <Stat
          label="有差异"
          value={`${diffs.length} 条`}
          sub={`${diffs.length - pending} 条已接受`}
          color={pending ? palette.orange : undefined}
          active
        />
        <Stat
          label="只在站点上有"
          value={r.onlyOnSite?.length ?? 0}
          sub="保留在站点数据里，原样不动"
        />
        {!!r.onlyInErp?.length && (
          <Stat
            label="只在 ERP 里有"
            value={r.onlyInErp.length}
            sub="发布时加到站点上"
          />
        )}
      </StatRow>
      <Card style={{ padding: '8px 20px 20px' }}>
        {/* lint-ui-ignore page-tabs: 卡片里的分区页签 */}
        <Tabs items={tabs} />
      </Card>
      {footer}
      <Modal
        open={!!one}
        title="确认接受这条差异？"
        okText="确认接受"
        confirmLoading={saving}
        okButtonProps={{ disabled: !oneReason.trim() }}
        onCancel={() => setOne(undefined)}
        onOk={async () => {
          if (
            one &&
            (await accept([{ model: one.model, field: one.field }], oneReason))
          )
            setOne(undefined);
        }}
        destroyOnHidden
      >
        {one && (
          <div style={{ fontSize: 13, color: palette.sub, marginBottom: 12 }}>
            {brands[one.model] ?? ''}{' '}
            <b style={{ fontFamily: MONO }}>{one.model}</b> 的
            {fieldLabel(one.field)}：以 ERP 为准后发布时用 ERP 生成值
          </div>
        )}
        <Input.TextArea
          value={oneReason}
          onChange={(e) => setOneReason(e.target.value)}
          placeholder="接受原因（必填），如：只是小数位写法不同"
          maxLength={200}
          autoSize={{ minRows: 2, maxRows: 4 }}
          aria-label="接受原因"
        />
      </Modal>
      <Modal
        open={!!view}
        title={view ? `${view.model} · ${fieldLabel(view.field)}` : ''}
        footer={null}
        width={880}
        onCancel={() => setView(undefined)}
        destroyOnHidden
      >
        {view && (
          <div
            style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}
          >
            {(
              [
                ['站点现值', view.site, palette.red],
                ['ERP 生成值', view.erp, palette.green],
              ] as const
            ).map(([label, v, color]) => (
              <div key={label}>
                <div
                  style={{ fontSize: 12, color: palette.mute, marginBottom: 6 }}
                >
                  {label}
                </div>
                <pre
                  style={{
                    margin: 0,
                    padding: 12,
                    borderRadius: 10,
                    background: palette.inset,
                    color,
                    fontFamily: MONO,
                    fontSize: 12,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all',
                    maxHeight: 460,
                    overflow: 'auto',
                  }}
                >
                  {prettyValue(v)}
                </pre>
              </div>
            ))}
          </div>
        )}
      </Modal>
    </>
  );
};

// ================================================================ 页面

const SiteJobPage: React.FC = () => {
  const { jobId } = useParams<{ jobId: string }>();
  const id = Number(jobId);
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access[PERM_EDIT];
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const active = useTabActive();
  const [job, setJob] = useState<SiteJob>();
  const [site, setSite] = useState<Site>();
  const [latestId, setLatestId] = useState<number>();
  const [error, setError] = useState<string>();
  const [starting, setStarting] = useState(false);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      const j = await siteApi.job(id);
      const [s, jobs] = await Promise.all([
        siteApi.detail(j.siteId),
        siteApi.jobs(j.siteId),
      ]);
      setJob(j);
      setSite(s);
      setLatestId(jobs.find((x) => x.jobType === j.jobType)?.id);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);

  const running = job?.status === RUNNING;
  useEffect(() => {
    if (!running || !active) return undefined;
    const t = window.setInterval(load, 5000);
    return () => window.clearInterval(t);
  }, [running, active, load]);

  const isImport = job ? job.jobType === 'import' : true;
  const title = isImport ? '导入现有商品' : '一致性比对';
  usePageTitle(site ? `${title} · ${site.name}` : title);

  const pendingTakeover = site?.takeoverStatus === TAKEOVER_PENDING;
  const rerun = async () => {
    if (!site) return;
    setStarting(true);
    try {
      const j = await (isImport
        ? siteApi.startImport(site.id)
        : siteApi.startCompare(site.id));
      history.replace(jobPath(j.id));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setStarting(false);
    }
  };

  const description =
    job &&
    site &&
    (isImport
      ? `读取站点仓库 ${site.repoBranch || 'main'} 分支 · ${formatDateTime(job.endedAt ?? job.startedAt)}`
      : `用 ERP 数据在临时目录构建，不部署、不提交 · ${formatDateTime(job.endedAt ?? job.startedAt)}${
          job.endedAt ? ` · 用时 ${duration(job.startedAt, job.endedAt)}` : ''
        }`);

  let body: React.ReactNode;
  if (error) body = <ErrorHint message={error} onRetry={load} />;
  else if (!job || !site)
    body = (
      <Card>
        <Skeleton active />
      </Card>
    );
  else if (job.status === RUNNING)
    body = (
      <Card style={{ padding: 48, textAlign: 'center' }}>
        <LoaderCircle
          size="1em"
          className="zhul-spin"
          style={{ fontSize: 28, color: palette.link }}
        />
        <div style={{ fontSize: 16, fontWeight: 600, marginTop: 14 }}>
          {isImport ? '正在读取站点商品…' : '正在比对…'}
        </div>
        <div style={{ fontSize: 13, color: palette.mute, marginTop: 6 }}>
          {isImport
            ? '发布服务正在读取站点仓库里的商品数据，通常一两分钟'
            : '发布服务正在临时目录里用 ERP 数据构建站点，通常几分钟'}
          ；完成后自动刷新，可以先离开这个页面
        </div>
      </Card>
    );
  else if (job.status === FAILED)
    body = (
      <Card style={{ padding: 32 }}>
        <div style={{ fontSize: 16, fontWeight: 600, color: palette.red }}>
          <CircleAlert size="1em" /> {isImport ? '读取失败' : '比对失败'}
        </div>
        <div style={{ fontSize: 13, color: palette.sub, marginTop: 8 }}>
          {job.error || '发布服务没有返回原因'}
        </div>
      </Card>
    );
  else if (isImport)
    body = (
      <ImportView
        job={job}
        site={site}
        confirmable={
          canEdit &&
          pendingTakeover &&
          job.status === DONE &&
          latestId === job.id
        }
        latestId={latestId}
        onChanged={setJob}
      />
    );
  else
    body = (
      <CompareView
        job={job}
        site={site}
        editable={
          canEdit &&
          pendingTakeover &&
          job.status === DONE &&
          latestId === job.id
        }
        latestId={latestId}
        onChanged={setJob}
        onTakeover={setSite}
      />
    );

  return (
    <div>
      <PageHeader
        title={title}
        sub={description}
        extra={
          canEdit &&
          pendingTakeover &&
          job &&
          !running && (
            <Button
              icon={<RefreshCw size="1em" />}
              loading={starting}
              onClick={rerun}
            >
              {isImport ? '重新读取' : '重新比对'}
            </Button>
          )
        }
      />
      {body}
    </div>
  );
};

export default SiteJobPage;
