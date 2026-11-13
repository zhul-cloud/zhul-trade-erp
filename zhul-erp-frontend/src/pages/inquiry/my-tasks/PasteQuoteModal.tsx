import {
  ArrowLeftOutlined,
  CheckOutlined,
  InfoCircleOutlined,
  LoadingOutlined,
  RobotOutlined,
  ScanOutlined,
  SnippetsOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Checkbox,
  Input,
  InputNumber,
  Modal,
  Select,
  Table,
  type TableColumnsType,
} from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { Pill, useQuoteDicts } from '../shared/components';
import {
  CHANNEL_OPTIONS,
  CHANNEL_SUPPLIER,
  CHANNEL_TAOBAO,
  channelLabel,
} from '../shared/constants';
import {
  myTaskApi,
  type PasteOtherTask,
  type PasteRow,
  type QuoteEntry,
  readBizError,
} from '../shared/service';
import {
  AI_RAW_MAX,
  AI_WAIT_MS,
  aiSummary,
  editPrice,
  lineCount,
  type PasteCarry,
  RULE_RAW_MAX,
  shownPrice,
} from './pasteAi';
import SupplierSelect from './SupplierSelect';

const POLL_MS = 2000;

const banner = (bg: string, border: string): React.CSSProperties => ({
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  padding: '10px 14px',
  borderRadius: 10,
  background: bg,
  border: `1px solid ${border}`,
});

/** 预览行：识别结果 + 采购的修改 */
interface Row extends PasteRow {
  key: number;
}

export interface PasteFill {
  itemId: number;
  entry: QuoteEntry;
}

const STATUS: Record<
  PasteRow['status'],
  { label: string; tone: 'green' | 'orange' | 'red' }
> = {
  MATCHED: { label: '已识别', tone: 'green' },
  UNMATCHED: { label: '未匹配', tone: 'orange' },
  NO_PRICE: { label: '缺单价', tone: 'red' },
};

/** 一行能不能填：有型号、有单价、型号没报给客户 */
const ready = (r: Row) =>
  !!r.itemId && r.unitPrice != null && r.unitPrice > 0 && !r.locked;

/**
 * 粘贴报价：选渠道店铺 → 粘贴原文，按规则识别或 AI 识别 → 预览可改 → 填进草稿（不保存）。
 * AI 识别异步执行、可取消；原文里属于本人其他任务的型号列在底部，点了打开那个任务并带上原文。
 */
const PasteQuoteModal: React.FC<{
  open: boolean;
  taskId: number;
  taskCode: string;
  items: { id: number; model: string; locked?: boolean }[];
  /** 从其他任务跳过来时带上的原文与渠道店铺 */
  carry?: PasteCarry;
  onClose: () => void;
  onFill: (fills: PasteFill[], source: string) => void;
  onOpenTask: (carry: PasteCarry) => void;
}> = ({
  open,
  taskId,
  taskCode,
  items,
  carry,
  onClose,
  onFill,
  onOpenTask,
}) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const { conditionOptions, leadTimeOptions } = useQuoteDicts();
  const [channel, setChannel] = useState(CHANNEL_TAOBAO);
  const [shopName, setShopName] = useState('');
  const [supplierId, setSupplierId] = useState<number>();
  const [text, setText] = useState('');
  const [rows, setRows] = useState<Row[] | null>(null);
  const [common, setCommon] = useState('');
  const [busy, setBusy] = useState(false);
  const [aiOn, setAiOn] = useState(false);
  /** 当前预览来自 AI 识别 */
  const [aiMode, setAiMode] = useState(false);
  /** 正在跑的 AI 识别与已用秒数；失败原因 */
  const [aiRun, setAiRun] = useState<{ id: number; started: number }>();
  const [elapsed, setElapsed] = useState(0);
  const [aiError, setAiError] = useState('');
  const [others, setOthers] = useState<PasteOtherTask[]>([]);
  const [unknown, setUnknown] = useState<string[]>([]);
  const [showUnknown, setShowUnknown] = useState(false);
  /** 每次发起或取消都换一个编号，过期的轮询自己停下 */
  const runRef = useRef(0);

  // 租户是否开启 AI：弹窗随任务详情一直挂着，打开页面时查一次
  useEffect(() => {
    myTaskApi
      .pasteAiEnabled()
      .then(setAiOn)
      .catch(() => setAiOn(false));
  }, []);

  const stopAi = useCallback(() => {
    runRef.current += 1;
    setAiRun(undefined);
  }, []);

  useEffect(() => {
    if (!open) return;
    setRows(null);
    setAiError('');
    if (carry?.taskId === taskId) {
      setText(carry.text);
      setChannel(carry.channel);
      setShopName(carry.shopName);
      setSupplierId(carry.supplierId);
    }
  }, [open, carry, taskId]);

  // 关掉弹窗或换任务时，正在跑的识别作废（后端也标为已取消，释放「一人一个」的名额）
  useEffect(() => {
    if (open || !aiRun) return;
    myTaskApi.pasteAiCancel(taskId, aiRun.id).catch(() => undefined);
    stopAi();
  }, [open, aiRun, taskId, stopAi]);

  useEffect(() => {
    if (!aiRun) return undefined;
    const t = window.setInterval(
      () => setElapsed(Math.round((Date.now() - aiRun.started) / 1000)),
      1000,
    );
    return () => window.clearInterval(t);
  }, [aiRun]);

  const source = `${channelLabel(channel)} · ${shopName.trim() || '—'}`;
  const canParse =
    !!text.trim() &&
    (channel === CHANNEL_SUPPLIER ? !!supplierId : !!shopName.trim());

  const parse = async () => {
    setBusy(true);
    setAiError('');
    try {
      const r = await myTaskApi.pastePreview(taskId, text);
      setAiMode(false);
      setRows(r.rows.map((x, i) => ({ ...x, key: i })));
      setCommon(r.common);
      if (r.rows.length === 0)
        message.info('没有认出带型号或价格的行，请检查原文');
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const parseAi = async () => {
    stopAi();
    const run = runRef.current;
    setAiError('');
    setElapsed(0);
    let id: number;
    try {
      id = (await myTaskApi.pasteAi(taskId, text)).aiTaskId;
    } catch (e) {
      setAiError(readBizError(e).message);
      return;
    }
    if (runRef.current !== run) return;
    const started = Date.now();
    setAiRun({ id, started });
    const fail = (msg: string) => {
      if (runRef.current !== run) return;
      setAiRun(undefined);
      setAiError(msg);
    };
    const poll = async () => {
      if (runRef.current !== run) return;
      if (Date.now() - started > AI_WAIT_MS) {
        fail('AI 识别超时');
        return;
      }
      try {
        const r = await myTaskApi.pasteAiResult(taskId, id);
        if (runRef.current !== run) return;
        if (r.status === 'RUNNING') {
          window.setTimeout(poll, POLL_MS);
          return;
        }
        if (r.status === 'FAILED' || !r.preview) {
          fail(r.errorMessage || 'AI 识别失败');
          return;
        }
        setAiRun(undefined);
        setAiMode(true);
        setRows(r.preview.rows.map((x, i) => ({ ...x, key: i })));
        setCommon(r.preview.common);
        setOthers(r.preview.otherTasks ?? []);
        setUnknown(r.preview.unknownModels ?? []);
        setShowUnknown(false);
      } catch (e) {
        fail(readBizError(e).message);
      }
    };
    window.setTimeout(poll, POLL_MS);
  };

  const cancelAi = () => {
    if (aiRun) myTaskApi.pasteAiCancel(taskId, aiRun.id).catch(() => undefined);
    stopAi();
  };

  const carryTo = (o: PasteOtherTask) =>
    onOpenTask({ taskId: o.taskId, text, channel, shopName, supplierId });

  const patch = (key: number, p: Partial<Row>) =>
    setRows((rs) =>
      (rs ?? []).map((r) => (r.key === key ? { ...r, ...p } : r)),
    );

  const fillable = (rows ?? []).filter(ready);
  const countOf = (k: PasteRow['status']) =>
    (rows ?? []).filter((r) => r.status === k).length;
  const dupModels = new Set(
    fillable.map((r) => r.itemId).filter((id, i, all) => all.indexOf(id) !== i),
  );

  const fill = () => {
    onFill(
      fillable.map((r) => ({
        itemId: r.itemId as number,
        entry: {
          channel,
          shopName: shopName.trim(),
          supplierId: channel === CHANNEL_SUPPLIER ? supplierId : undefined,
          unitPrice: r.unitPrice,
          taxIncluded: r.taxIncluded,
          taxRate: r.taxIncluded ? (r.taxRate ?? 13) : undefined,
          itemCondition: r.itemCondition || undefined,
          leadTime: r.leadTime || undefined,
          note: r.note || undefined,
        },
      })),
      source,
    );
  };

  const aiColumns: TableColumnsType<Row> = [
    {
      title: '型号',
      dataIndex: 'model',
      width: 170,
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
    },
    {
      title: '原文',
      dataIndex: 'raw',
      width: 220,
      render: (v: string) => (
        <span style={{ fontSize: 12, color: palette.sub }}>{v}</span>
      ),
    },
    {
      title: '单价（不含税）',
      key: 'price',
      width: 150,
      render: (_, r) => (
        <div style={{ display: 'grid', gap: 2 }}>
          <InputNumber
            value={shownPrice(r) ?? null}
            min={0}
            precision={2}
            placeholder="填单价"
            disabled={r.locked}
            status={r.unitPrice == null ? 'error' : undefined}
            onChange={(v) =>
              patch(r.key, editPrice(r, v === null ? null : Number(v)))
            }
            style={{ width: 130 }}
            aria-label={`${r.model} 单价`}
          />
          {r.taxIncluded && r.unitPrice != null && (
            <span style={{ fontSize: 12, color: palette.red }}>
              含税 {r.unitPrice.toFixed(2)} · {r.taxRate ?? 13}%
            </span>
          )}
        </div>
      ),
    },
    {
      title: '货况',
      key: 'cond',
      width: 130,
      render: (_, r) => (
        <Select
          value={r.itemCondition || undefined}
          options={conditionOptions}
          disabled={r.locked}
          placeholder="—"
          onChange={(v: number) => patch(r.key, { itemCondition: v })}
          style={{ width: 118 }}
          aria-label={`${r.model} 货况`}
        />
      ),
    },
    {
      title: '货期',
      key: 'lead',
      width: 120,
      render: (_, r) => (
        <Select
          value={r.leadTime || undefined}
          allowClear
          placeholder="—"
          options={leadTimeOptions}
          disabled={r.locked}
          onChange={(v?: number) => patch(r.key, { leadTime: v ?? 0 })}
          style={{ width: 108 }}
          aria-label={`${r.model} 货期`}
        />
      ),
    },
    {
      title: '备注',
      key: 'note',
      width: 160,
      render: (_, r) => (
        <Input
          value={r.note}
          maxLength={200}
          disabled={r.locked}
          onChange={(e) => patch(r.key, { note: e.target.value })}
          aria-label={`${r.model} 备注`}
        />
      ),
    },
    {
      title: '把握',
      key: 'confidence',
      width: 110,
      render: (_, r) =>
        r.locked ? (
          <Pill tone="violet">已报给客户</Pill>
        ) : r.confidence === 'CHECK' ? (
          <Pill tone="orange">请核对</Pill>
        ) : (
          <Pill tone="green">确定</Pill>
        ),
    },
  ];

  const modelOptions = items
    .filter((i) => !i.locked)
    .map((i) => ({ value: i.id, label: i.model }));

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={rows ? 1160 : 720}
      destroyOnHidden
      title={
        <span>
          {rows ? <ScanOutlined /> : <SnippetsOutlined />}{' '}
          {rows
            ? aiMode
              ? '粘贴报价 · 核对后填入'
              : `识别结果 · ${source}`
            : `粘贴报价 · ${taskCode}`}
        </span>
      }
      footer={
        rows ? (
          <>
            {aiMode ? (
              <Button onClick={onClose}>取消</Button>
            ) : (
              <Button
                icon={<ArrowLeftOutlined />}
                onClick={() => setRows(null)}
              >
                返回修改原文
              </Button>
            )}
            <Button
              type="primary"
              icon={<CheckOutlined />}
              disabled={fillable.length === 0}
              onClick={fill}
            >
              {aiMode
                ? `填入 ${fillable.length} 条（不保存）`
                : `填入 ${fillable.length} 条报价`}
            </Button>
          </>
        ) : aiOn ? (
          <>
            <Button
              loading={busy}
              disabled={
                !canParse || !!aiRun || text.trim().length > RULE_RAW_MAX
              }
              title={
                text.trim().length > RULE_RAW_MAX
                  ? `按规则识别最多 ${RULE_RAW_MAX} 字`
                  : undefined
              }
              onClick={parse}
            >
              按规则识别
            </Button>
            <Button
              type="primary"
              icon={<RobotOutlined />}
              disabled={!canParse || !!aiRun || busy}
              onClick={parseAi}
            >
              AI 识别
            </Button>
          </>
        ) : (
          <>
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              icon={<ScanOutlined />}
              loading={busy}
              disabled={!canParse}
              onClick={parse}
            >
              识别
            </Button>
          </>
        )
      }
    >
      {!rows ? (
        <div style={{ display: 'grid', gap: 14 }}>
          <div style={{ color: palette.sub }}>
            一次粘贴一家店铺或供应商的回复；只在本任务的 {items.length}{' '}
            个型号里匹配。
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '160px 1fr',
              gap: 12,
            }}
          >
            <div>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                渠道 <span style={{ color: palette.red }}>*</span>
              </div>
              <Select
                value={channel}
                options={CHANNEL_OPTIONS}
                onChange={(v) => {
                  setChannel(v);
                  setSupplierId(undefined);
                }}
                style={{ width: '100%' }}
                aria-label="渠道"
              />
            </div>
            <div>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                {channel === CHANNEL_SUPPLIER ? '供应商' : '店铺名称'}{' '}
                <span style={{ color: palette.red }}>*</span>
              </div>
              {channel === CHANNEL_SUPPLIER ? (
                <SupplierSelect
                  value={supplierId}
                  label={shopName}
                  onChange={(id, name) => {
                    setSupplierId(id);
                    setShopName(name);
                  }}
                />
              ) : (
                <Input
                  value={shopName}
                  maxLength={100}
                  onChange={(e) => setShopName(e.target.value)}
                  placeholder="如 施耐德工控旗舰店"
                  aria-label="店铺名称"
                />
              )}
            </div>
          </div>
          <div>
            <div style={{ color: palette.sub, marginBottom: 6 }}>
              店家回复原文 <span style={{ color: palette.red }}>*</span>
            </div>
            <Input.TextArea
              value={text}
              rows={8}
              maxLength={aiOn ? AI_RAW_MAX : RULE_RAW_MAX}
              disabled={!!aiRun}
              onChange={(e) => setText(e.target.value)}
              placeholder={
                'LXM32AD30N4，要 1 台  3140  现货\nBMH1003P16A2A  2562  5周\n未税包邮 全新原装'
              }
              aria-label="店家回复原文"
            />
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
              {aiOn
                ? '可以整段粘贴，AI 只挑出这个任务里的型号；按规则识别要求每行一个型号。'
                : '每行一个型号；「要 2 台」这类数量会被排除；「未税包邮 全新原装」这类没有型号的说明会套用到所有行。'}
            </div>
          </div>
          {aiRun && (
            <div style={banner(palette.accentSoft, palette.accentLine)}>
              <LoadingOutlined style={{ color: palette.link }} />
              <span style={{ color: palette.link, flex: 1 }}>
                AI 识别中 · 已用 {elapsed} 秒
              </span>
              <Button type="link" size="small" onClick={cancelAi}>
                取消
              </Button>
            </div>
          )}
          {aiError && !aiRun && (
            <div style={banner(palette.redSoft, palette.red)}>
              <InfoCircleOutlined style={{ color: palette.red }} />
              <span style={{ color: palette.ink, flex: 1 }}>
                {aiError}，可以重试或点「按规则识别」
              </span>
            </div>
          )}
        </div>
      ) : (
        <div
          style={{
            display: 'grid',
            gap: 14,
            gridTemplateColumns: 'minmax(0, 1fr)',
          }}
        >
          {aiMode ? (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                flexWrap: 'wrap',
              }}
            >
              <Pill tone="accent">AI 识别</Pill>
              <span style={{ color: palette.sub }}>
                {source} · 原文 {lineCount(text)} 行
              </span>
              <span style={{ flex: 1 }} />
              <span style={{ color: palette.sub, fontSize: 13 }}>
                {aiSummary(rows, items.length)}
              </span>
              <Button type="link" size="small" onClick={() => setRows(null)}>
                重新识别
              </Button>
            </div>
          ) : (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                flexWrap: 'wrap',
              }}
            >
              {(['MATCHED', 'UNMATCHED', 'NO_PRICE'] as const).map(
                (k) =>
                  countOf(k) > 0 && (
                    <Pill key={k} tone={STATUS[k].tone}>
                      {STATUS[k].label} {countOf(k)}
                    </Pill>
                  ),
              )}
              <span style={{ flex: 1 }} />
              <span style={{ fontSize: 12, color: palette.mute }}>
                每格都可以改；没有型号或单价的行不会填入
              </span>
            </div>
          )}
          {common && !aiMode && (
            <div
              style={{
                padding: '10px 12px',
                borderRadius: 10,
                background: palette.inset,
                color: palette.sub,
                fontSize: 12,
              }}
            >
              <InfoCircleOutlined /> 整段说明「{common}
              」：已套用到没写明含税、货况、货期的行，原文写进每行备注。
            </div>
          )}
          <Table<Row>
            rowKey="key"
            size="small"
            pagination={false}
            dataSource={rows}
            scroll={{ x: 'max-content', y: 420 }}
            locale={{
              emptyText: aiMode
                ? '原文里没有找到这个任务的型号'
                : '没有认出带型号或价格的行',
            }}
            onRow={(r) =>
              aiMode && r.confidence === 'CHECK' && !r.locked
                ? { style: { background: palette.orangeSoft } }
                : {}
            }
            columns={
              aiMode
                ? aiColumns
                : [
                    {
                      title: '原文',
                      dataIndex: 'raw',
                      width: 240,
                      render: (v: string) => (
                        <span style={{ fontSize: 12, color: palette.sub }}>
                          {v}
                        </span>
                      ),
                    },
                    {
                      title: '对应型号',
                      key: 'model',
                      width: 200,
                      render: (_, r) => (
                        <Select
                          value={r.itemId ?? undefined}
                          allowClear
                          placeholder="选择任务型号"
                          options={modelOptions}
                          status={!r.itemId ? 'warning' : undefined}
                          onChange={(v?: number) =>
                            patch(r.key, { itemId: v ?? null })
                          }
                          style={{ width: 180 }}
                          aria-label={`${r.raw} 对应型号`}
                        />
                      ),
                    },
                    {
                      title: '单价（CNY）',
                      key: 'price',
                      width: 120,
                      render: (_, r) => (
                        <InputNumber
                          value={r.unitPrice ?? null}
                          min={0}
                          precision={2}
                          placeholder="填单价"
                          status={r.unitPrice == null ? 'error' : undefined}
                          onChange={(v) =>
                            patch(r.key, {
                              unitPrice: v === null ? null : Number(v),
                            })
                          }
                          style={{ width: 106 }}
                          aria-label={`${r.raw} 单价`}
                        />
                      ),
                    },
                    {
                      title: '含税',
                      key: 'tax',
                      width: 60,
                      render: (_, r) => (
                        <Checkbox
                          checked={r.taxIncluded}
                          onChange={(e) =>
                            patch(r.key, { taxIncluded: e.target.checked })
                          }
                          aria-label={`${r.raw} 含税`}
                        />
                      ),
                    },
                    {
                      title: '货况',
                      key: 'cond',
                      width: 130,
                      render: (_, r) => (
                        <Select
                          value={r.itemCondition || undefined}
                          options={conditionOptions}
                          onChange={(v: number) =>
                            patch(r.key, { itemCondition: v })
                          }
                          style={{ width: 118 }}
                          aria-label={`${r.raw} 货况`}
                        />
                      ),
                    },
                    {
                      title: '货期',
                      key: 'lead',
                      width: 120,
                      render: (_, r) => (
                        <Select
                          value={r.leadTime || undefined}
                          allowClear
                          placeholder="—"
                          options={leadTimeOptions}
                          onChange={(v?: number) =>
                            patch(r.key, { leadTime: v ?? 0 })
                          }
                          style={{ width: 108 }}
                          aria-label={`${r.raw} 货期`}
                        />
                      ),
                    },
                    {
                      title: '状态',
                      key: 'status',
                      width: 130,
                      render: (_, r) =>
                        r.locked ? (
                          <Pill tone="violet">已报给客户，不能改</Pill>
                        ) : ready(r) ? (
                          <span
                            style={{
                              display: 'inline-flex',
                              gap: 4,
                              flexWrap: 'wrap',
                            }}
                          >
                            <Pill tone="green">将填入</Pill>
                            {dupModels.has(r.itemId) && (
                              <Pill tone="orange">同型号多行</Pill>
                            )}
                          </span>
                        ) : (
                          <Pill tone={!r.itemId ? 'orange' : 'red'}>
                            {!r.itemId ? '未匹配' : '缺单价'}
                          </Pill>
                        ),
                    },
                  ]
            }
          />
          {aiMode && others.length > 0 && (
            <div style={{ display: 'grid', gap: 8 }}>
              <b style={{ color: palette.ink }}>原文里的其他型号</b>
              {others.map((o) => (
                <div
                  key={o.taskId}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '10px 14px',
                    borderRadius: 10,
                    background: palette.inset,
                  }}
                >
                  <button
                    type="button"
                    className="zhul-link"
                    onClick={() => carryTo(o)}
                  >
                    {o.taskCode}
                  </button>
                  <span style={{ color: palette.sub, flex: 1 }}>
                    {[o.brand, o.category, `${o.modelCount} 个型号`]
                      .filter(Boolean)
                      .join(' · ')}
                  </span>
                  <Button type="link" size="small" onClick={() => carryTo(o)}>
                    打开并带上原文
                  </Button>
                </div>
              ))}
            </div>
          )}
          {aiMode && unknown.length > 0 && (
            <div style={{ color: palette.sub, fontSize: 13 }}>
              不在你的任务里 {unknown.length} 个{' '}
              <Button
                type="link"
                size="small"
                onClick={() => setShowUnknown((v) => !v)}
              >
                {showUnknown ? '收起' : '展开'}
              </Button>
              {showUnknown && (
                <div style={{ color: palette.mute, marginTop: 4 }}>
                  {unknown.join('、')}
                </div>
              )}
            </div>
          )}
          {!aiMode && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              <InfoCircleOutlined />{' '}
              同一渠道、同一店铺已有的草稿会被覆盖，其余追加；填入后不会自动保存，核对后点「保存草稿」或「提交回价」。
            </div>
          )}
        </div>
      )}
    </Modal>
  );
};

export default PasteQuoteModal;
