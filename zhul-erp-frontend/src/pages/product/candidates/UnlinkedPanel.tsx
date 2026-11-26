import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Checkbox,
  Input,
  Modal,
  Popover,
  Radio,
  Table,
  Tag,
} from 'antd';
import { Link2, RotateCcw, Search, Sparkles } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { tableEmpty } from '@/components/EmptyState';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { Card } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { ProductSelect } from '@/pages/product/components/ProductSelect';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { AI_TASK_NOT_FOUND, type ProductOption } from '../service';
import {
  candidateApi,
  readBizError,
  type UnlinkedAi,
  type UnlinkedDocRef,
  type UnlinkedGroup,
} from './service';

/** 单据类型 → 详情页地址（打开后由标签页框架返回待关联页） */
const DOC_PATHS: Record<string, (id: number) => string> = {
  QT: (id) => `/quotation/quotations/${id}`,
  PI: (id) => `/sales/pi/${id}`,
  SO: (id) => `/sales/orders/${id}`,
  PO: (id) => `/purchase/orders/${id}`,
  SD: (id) => `/purchase/shipments/${id}`,
  GR: (id) => `/warehouse/receipts/${id}`,
  OB: (id) => `/warehouse/outbounds/${id}`,
  AS: (id) => `/sales/after-sales/${id}`,
  SR: (id) => `/sales/returns/${id}`,
  RR: (id) => `/warehouse/returns/${id}`,
  PR: (id) => `/purchase/returns/${id}`,
};

/** 每次最多交给 AI 的组数，与后端一致 */
export const AI_MAX = 200;
const AI_JOB_KEY = 'zhul:unlinked:ai';
const KIND = { MODEL: 1, FEE: 2, UNKNOWN: 3 } as const;

const readJob = (): UnlinkedAi | null => {
  try {
    const raw = localStorage.getItem(AI_JOB_KEY);
    return raw ? (JSON.parse(raw) as UnlinkedAi) : null;
  } catch {
    return null;
  }
};
const writeJob = (job: UnlinkedAi | null) => {
  try {
    if (job) localStorage.setItem(AI_JOB_KEY, JSON.stringify(job));
    else localStorage.removeItem(AI_JOB_KEY);
  } catch {
    // 无痕模式等写不进去时只是刷新后不再跟踪进度
  }
};

/** 单号链接：点开单据详情 */
const DocLink: React.FC<{ d: UnlinkedDocRef }> = ({ d }) => {
  const path = DOC_PATHS[d.type];
  return path ? (
    <button
      type="button"
      className="zhul-link num"
      onClick={() => history.push(path(d.id))}
    >
      {d.no}
    </button>
  ) : (
    <span className="num">{d.no}</span>
  );
};

/** 涉及单据：最近的 3 个单号，超出时「等 N 张」点开看最近 20 张 */
export const DocRefs: React.FC<{ g: UnlinkedGroup }> = ({ g }) => {
  const { palette } = useAppTheme();
  const refs = g.docRefs ?? [];
  const total = g.docCount ?? refs.length;
  if (refs.length === 0)
    return (
      <span style={{ color: palette.mute }}>
        {Object.entries(g.docs)
          .map(([k, n]) => `${k} ${n}`)
          .join(' · ')}
      </span>
    );
  return (
    <div style={{ display: 'grid', gap: 2, justifyItems: 'start' }}>
      {refs.slice(0, 3).map((d) => (
        <DocLink key={`${d.type}${d.id}`} d={d} />
      ))}
      {total > 3 && (
        <Popover
          trigger="click"
          title={`共 ${total} 张单据${total > refs.length ? `，显示最近 ${refs.length} 张` : ''}`}
          content={
            <div
              style={{
                display: 'grid',
                gap: 4,
                maxHeight: 320,
                overflow: 'auto',
                justifyItems: 'start',
              }}
            >
              {refs.map((d) => (
                <DocLink key={`${d.type}${d.id}`} d={d} />
              ))}
            </div>
          }
        >
          <button
            type="button"
            className="zhul-link"
            style={{ fontSize: 12, color: palette.mute }}
          >
            等 {total} 张
          </button>
        </Popover>
      )}
    </div>
  );
};

/**
 * 待关联单据：还没关联商品的单据明细按「品牌 + 型号」归并，
 * 选择商品或填写真实型号后整组关联（spec product/document-product-link）。
 * 可以批量 AI 识别型号（只给建议，采纳后才关联），运费等费用行可以「标为不是商品」移出清单（openspec unlinked-docs-enhance）。
 * 清单按需计算、按最近出现排序，不是业务单据列表，所以不带审计列。
 */
export const UnlinkedPanel: React.FC<{ onChanged: () => void }> = ({
  onChanged,
}) => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState<string>();
  const [marked, setMarked] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<UnlinkedGroup[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<UnlinkedGroup[]>([]);
  const [linking, setLinking] = useState<{
    group: UnlinkedGroup;
    adopt?: boolean;
  }>();
  const [aiJob, setAiJob] = useState<UnlinkedAi | null>(readJob);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const r = await candidateApi.unlinked({
        keyword: query,
        page,
        pageSize,
        marked,
      });
      setRows(r.records);
      setTotal(r.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, page, pageSize, marked]);

  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(load);

  const reload = React.useRef(load);
  reload.current = load;

  // 轮询运行中的 AI 识别；跑完提示结果并刷新清单
  useEffect(() => {
    if (!aiJob?.taskId) return undefined;
    const taskId = aiJob.taskId;
    const timer = setInterval(async () => {
      try {
        const r = await candidateApi.aiUnlinkedStatus(taskId);
        if (r.status !== 3 && r.status !== 4) return;
        setAiJob(null);
        writeJob(null);
        if (r.status === 4) {
          message.error(`AI 识别失败：${r.errorMessage || '未知原因'}`);
          return;
        }
        message.success(
          `AI 识别完成：${r.models ?? 0} 组是商品型号，${r.fees ?? 0} 组是费用，${r.unknown ?? 0} 组看不出；请核对后采纳`,
        );
        reload.current();
      } catch (e) {
        // 只有任务确实查不到（如换了账号）才停止跟踪；网络抖动下次继续查
        if (readBizError(e).errorCode !== AI_TASK_NOT_FOUND) return;
        setAiJob(null);
        writeJob(null);
      }
    }, 3000);
    return () => clearInterval(timer);
  }, [aiJob?.taskId, message]);

  const search = () => {
    setQuery(keyword.trim() || undefined);
    setPage(1);
  };

  const done = () => {
    setSelected([]);
    load();
    onChanged();
  };

  const aiRecognize = async () => {
    if (selected.length > AI_MAX) {
      message.warning(`每次最多 ${AI_MAX} 组，已选 ${selected.length} 组`);
      return;
    }
    setBusy(true);
    try {
      const r = await candidateApi.aiUnlinked(selected.map((g) => g.key));
      if (!r.taskId) {
        message.info('选中的组已关联或不在清单里，请刷新');
        return;
      }
      setAiJob(r);
      writeJob(r);
      setSelected([]);
      message.info(`已交给 AI 识别 ${r.total} 组，跑完会提示`);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const adoptable = selected.filter(
    (g) => g.aiKind === KIND.MODEL && g.aiModel,
  );
  const adopt = async () => {
    setBusy(true);
    try {
      const r = await candidateApi.adoptUnlinked(adoptable.map((g) => g.key));
      if (r.failures.length === 0) {
        message.success(`已采纳 ${r.adopted} 组，关联 ${r.lines} 条明细`);
      } else {
        modal.warning({
          title: `已采纳 ${r.adopted} 组，${r.failures.length} 组没有关联`,
          content: (
            <div style={{ display: 'grid', gap: 4 }}>
              {r.failures.map((f) => (
                <div key={f.key}>
                  {f.model ? `${f.model}：` : ''}
                  {f.message}
                </div>
              ))}
            </div>
          ),
        });
      }
      done();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const ignore = (groups: UnlinkedGroup[]) =>
    modal.confirm({
      title: '标为不是商品',
      okText: '标为不是商品',
      width: 520,
      content: (
        <div style={{ display: 'grid', gap: 10 }}>
          <div style={{ color: palette.sub }}>
            这 {groups.length}{' '}
            组从待关联清单移出，以后同样写法的明细也不再列入。单据与明细的原文、金额保持不变，勾选「显示已标记」可以恢复。
          </div>
          <div
            style={{
              display: 'grid',
              gap: 8,
              maxHeight: 280,
              overflow: 'auto',
              padding: '10px 12px',
              borderRadius: 10,
              background: palette.inset,
            }}
          >
            {groups.map((g) => (
              <div key={g.key}>
                <b style={{ color: palette.ink }}>
                  {g.brand ? `${g.brand} · ` : ''}
                  {g.model}
                </b>
                <div style={{ fontSize: 12, color: palette.mute }}>
                  {(g.docRefs ?? [])
                    .slice(0, 3)
                    .map((d) => d.no)
                    .join('、')}
                  {(g.docCount ?? 0) > 3 ? ` 等 ${g.docCount} 张` : ''}
                </div>
              </div>
            ))}
          </div>
        </div>
      ),
      onOk: async () => {
        try {
          const n = await candidateApi.ignoreUnlinked(groups.map((g) => g.key));
          message.success(`已标记 ${n} 组`);
          done();
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });

  const restore = async (groups: UnlinkedGroup[]) => {
    try {
      const n = await candidateApi.restoreUnlinked(groups.map((g) => g.key));
      message.success(`已恢复 ${n} 组，回到待关联清单`);
      done();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const aiCell = (g: UnlinkedGroup) => {
    if (g.aiKind === KIND.MODEL)
      return (
        <div style={{ display: 'grid', gap: 4 }}>
          <b className="num" style={{ color: palette.ink }}>
            {g.aiBrand ? `${g.aiBrand} · ` : ''}
            {g.aiModel}
          </b>
          <span style={{ display: 'inline-flex', gap: 8 }}>
            <Tag color="purple" style={{ marginInlineEnd: 0 }}>
              AI 建议
            </Tag>
            <button
              type="button"
              className="zhul-link"
              onClick={() => setLinking({ group: g, adopt: true })}
            >
              采纳
            </button>
          </span>
        </div>
      );
    if (g.aiKind === KIND.FEE)
      return (
        <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <Tag color="orange" style={{ marginInlineEnd: 0 }}>
            费用
          </Tag>
          <button
            type="button"
            className="zhul-link"
            onClick={() => ignore([g])}
          >
            标为不是商品
          </button>
        </span>
      );
    if (g.aiKind === KIND.UNKNOWN)
      return <span style={{ color: palette.mute }}>看不出</span>;
    return <span style={{ color: palette.mute }}>—</span>;
  };

  const columns: TableColumnsType<UnlinkedGroup> = [
    {
      title: '品牌 · 型号（单据原文）',
      key: 'model',
      width: 260,
      fixed: 'left',
      render: (_, g) => (
        <b style={{ color: palette.ink }}>
          {g.brand ? `${g.brand} · ` : ''}
          {g.model}
        </b>
      ),
    },
    {
      title: '涉及单据',
      key: 'docs',
      width: 180,
      render: (_, g) => <DocRefs g={g} />,
    },
    {
      title: '明细条数',
      dataIndex: 'lineCount',
      width: 90,
      align: 'right',
    },
    {
      title: '最近出现',
      dataIndex: 'lastSeenAt',
      width: 160,
      render: (v?: string) => formatDateTime(v),
    },
    ...(marked
      ? ([
          {
            title: '标记人',
            dataIndex: 'ignoredBy',
            width: 110,
            render: (v?: string) => v || '—',
          },
          {
            title: '标记时间',
            dataIndex: 'ignoredAt',
            width: 160,
            render: (v?: string) => formatDateTime(v),
          },
          {
            title: '操作',
            key: 'actions',
            width: rowActionsWidth(1),
            fixed: 'right',
            render: (_, g) => (
              <RowActions
                inline={[
                  {
                    key: 'restore',
                    label: '恢复',
                    icon: <RotateCcw size={18} />,
                    onClick: () => restore([g]),
                  },
                ]}
              />
            ),
          },
        ] as TableColumnsType<UnlinkedGroup>)
      : ([
          {
            title: 'AI 建议',
            key: 'ai',
            width: 260,
            render: (_, g) => aiCell(g),
          },
          {
            title: '可能是',
            key: 'suggestion',
            width: 240,
            render: (_, g) =>
              g.suggestion ? (
                <span>
                  {g.suggestion.brandName} · {g.suggestion.mpnDisplay}
                  {g.suggestion.candidate && (
                    <Tag color="orange" style={{ marginInlineStart: 6 }}>
                      候选
                    </Tag>
                  )}
                </span>
              ) : (
                <span style={{ color: palette.mute }}>—</span>
              ),
          },
          {
            title: '操作',
            key: 'actions',
            width: rowActionsWidth(1),
            fixed: 'right',
            render: (_, g) => (
              <RowActions
                inline={[
                  {
                    key: 'link',
                    label: '关联',
                    icon: <Link2 size={18} />,
                    onClick: () => setLinking({ group: g }),
                  },
                ]}
              />
            ),
          },
        ] as TableColumnsType<UnlinkedGroup>)),
  ];

  return (
    <>
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Input
          allowClear
          prefix={<Search size="1em" />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={search}
          placeholder="品牌、型号"
          style={{ width: 260 }}
          aria-label="关键词"
        />
        <Checkbox
          checked={marked}
          onChange={(e) => {
            setMarked(e.target.checked);
            setSelected([]);
            setPage(1);
          }}
        >
          显示已标记
        </Checkbox>
        {aiJob?.taskId && (
          <span style={{ fontSize: 12, color: palette.mute }}>
            <Sparkles size={16} /> AI 正在识别 {aiJob.total ?? ''} 组…
          </span>
        )}
        <span style={{ flex: 1 }} />
        <Button type="primary" onClick={search}>
          查询
        </Button>
      </div>
      {selected.length > 0 && (
        <div
          style={{
            display: 'flex',
            gap: 10,
            alignItems: 'center',
            padding: '10px 16px',
            borderRadius: 12,
            background: palette.primarySoft, // lint-ui-ignore primary-soft: 批量操作条（已选 N 组），属于选中状态
            marginBottom: 12,
          }}
        >
          <b style={{ color: palette.ink }}>已选 {selected.length} 组</b>
          {marked ? (
            <Button onClick={() => restore(selected)}>恢复</Button>
          ) : (
            <>
              <Button
                icon={<Sparkles size={16} />}
                loading={busy}
                disabled={!!aiJob?.taskId}
                onClick={aiRecognize}
              >
                {aiJob?.taskId ? 'AI 识别中' : 'AI 识别型号'}
              </Button>
              <Button
                disabled={adoptable.length === 0 || busy}
                title={
                  adoptable.length === 0
                    ? '选中的组还没有 AI 建议的型号'
                    : undefined
                }
                onClick={adopt}
              >
                批量采纳{adoptable.length ? ` ${adoptable.length} 组` : ''}
              </Button>
              <Button onClick={() => ignore(selected)}>标为不是商品</Button>
            </>
          )}
          <span style={{ flex: 1 }} />
          <button
            type="button"
            className="zhul-link"
            onClick={() => setSelected([])}
          >
            取消选择
          </button>
        </div>
      )}
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Card style={{ padding: 0 }}>
          <Table<UnlinkedGroup>
            rowKey="key"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
            rowSelection={{
              selectedRowKeys: selected.map((g) => g.key),
              preserveSelectedRowKeys: true,
              onChange: (_, picked) => setSelected(picked),
            }}
            locale={{
              emptyText: tableEmpty(
                marked ? '没有标为不是商品的型号' : '所有单据明细都已关联商品',
                loading,
              ),
            }}
            pagination={{
              ...PAGER_STYLE,
              current: page,
              pageSize,
              total,
              showTotal: (t: number) => `共 ${t} 组`,
              onChange: (p: number, s: number) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </Card>
      )}
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        {marked
          ? '标为不是商品的写法不再列入待关联清单；恢复后回到清单'
          : 'AI 只给建议，采纳或标记都要人点；单号点开单据详情，返回后回到待关联'}
      </div>
      <LinkModal
        group={linking?.group}
        adopt={linking?.adopt}
        onClose={() => setLinking(undefined)}
        onDone={() => {
          setLinking(undefined);
          done();
        }}
      />
    </>
  );
};

/** 关联一组明细：选择已有商品，或填写真实型号（商品库没有的进候选池）；采纳 AI 建议时预填真实型号 */
export const LinkModal: React.FC<{
  group?: UnlinkedGroup;
  /** 从「采纳」打开：默认填写真实型号，预填 AI 建议的品牌与型号 */
  adopt?: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ group, adopt, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [mode, setMode] = useState<'pick' | 'fill'>('pick');
  const [productId, setProductId] = useState<number>();
  const [brand, setBrand] = useState('');
  const [model, setModel] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (group) {
      const fromAi = !!adopt && !!group.aiModel;
      setMode(fromAi ? 'fill' : 'pick');
      setProductId(group.suggestion?.id);
      setBrand(fromAi && group.aiBrand ? group.aiBrand : group.brand);
      setModel(fromAi ? (group.aiModel ?? '') : '');
    }
  }, [group, adopt]);

  const submit = async () => {
    if (!group) return;
    setBusy(true);
    try {
      const n = await candidateApi.linkUnlinked(
        mode === 'pick'
          ? { key: group.key, productId }
          : { key: group.key, brand: brand.trim(), model: model.trim() },
      );
      message.success(`已关联 ${n} 条明细`);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const initial: ProductOption | undefined = group?.suggestion;
  return (
    <Modal
      open={!!group}
      title={group ? `关联商品 · ${group.model}` : '关联商品'}
      okText="关联"
      confirmLoading={busy}
      okButtonProps={{
        disabled: mode === 'pick' ? !productId : !model.trim(),
      }}
      onCancel={onClose}
      onOk={submit}
      destroyOnHidden
    >
      {group && (
        <div style={{ display: 'grid', gap: 12 }}>
          <div style={{ color: palette.sub }}>
            共 {group.lineCount} 条明细一起关联
            {group.docRefs?.length ? '，涉及单据：' : ''}
          </div>
          {!!group.docRefs?.length && (
            <div
              style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: '4px 12px',
                fontSize: 13,
              }}
            >
              <DocRefs g={group} />
            </div>
          )}
          <Radio.Group
            value={mode}
            onChange={(e) => setMode(e.target.value)}
            options={[
              { value: 'pick', label: '选择商品' },
              { value: 'fill', label: '填写真实型号' },
            ]}
          />
          {mode === 'pick' ? (
            <ProductSelect
              value={productId}
              initialOption={initial}
              onChange={(id) => setProductId(id)}
              style={{ width: '100%' }}
              aria-label="商品"
            />
          ) : (
            <div style={{ display: 'grid', gap: 8 }}>
              <Input
                value={brand}
                maxLength={64}
                onChange={(e) => setBrand(e.target.value)}
                placeholder="品牌"
                aria-label="品牌"
              />
              <Input
                value={model}
                maxLength={128}
                onChange={(e) => setModel(e.target.value)}
                placeholder="真实型号，如 6ES7214-1AG40-0XB0"
                aria-label="真实型号"
              />
              <span style={{ fontSize: 12, color: palette.mute }}>
                {adopt && group.aiModel ? '已按 AI 建议预填，核对后关联；' : ''}
                商品库有这个型号就直接关联，没有的进候选池等审核
              </span>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
};
