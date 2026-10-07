import {
  ApartmentOutlined,
  CheckOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  RobotOutlined,
  SyncOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Input,
  InputNumber,
  Radio,
  Select,
  Skeleton,
  Table,
} from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import BrandCategoryPicker from '@/components/BrandCategoryPicker';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import {
  AgeText,
  Card,
  ConditionPill,
  LeadTimeText,
  PageTitle,
  Pill,
  SupplierText,
  TaxHint,
  useLifecycles,
  useWide,
} from '../shared/components';
import {
  CONFIDENCE,
  LIFECYCLE_DISCONTINUED,
  LIFECYCLE_UNKNOWN,
  PATHS,
  STATUS,
} from '../shared/constants';
import {
  type ConfirmRow,
  type Draft,
  type DraftRow,
  inquiryApi,
  type PriceMatch,
  readBizError,
} from '../shared/service';

interface Row extends DraftRow {
  key: string;
  /** 复用的历史询价记录；null 表示重新询价 */
  reuseQuoteId: number | null;
  matching?: boolean;
}

let seq = 0;

const savedKey = (id: number) => `inquiry-confirm-draft-${id}`;

const readSaved = (id: number): Row[] | null => {
  try {
    const raw = sessionStorage.getItem(savedKey(id));
    const rows = raw ? (JSON.parse(raw) as Row[]) : null;
    return (
      rows?.map((r) => ({ ...r, key: nextKey(), matching: false })) ?? null
    );
  } catch {
    return null;
  }
};

const writeSaved = (id: number, rows: Row[] | null) => {
  try {
    if (rows) sessionStorage.setItem(savedKey(id), JSON.stringify(rows));
    else sessionStorage.removeItem(savedKey(id));
  } catch {
    // 浏览器禁用存储时不影响确认，只是离开页面后不会恢复
  }
};
const nextKey = () => `r${++seq}`;

const emptyRow = (): Row => ({
  key: nextKey(),
  brand: '',
  category: '',
  confirmedModel: '',
  confidence: CONFIDENCE.CONFIRMED,
  quantity: 1,
  unit: '',
  lifecycle: LIFECYCLE_UNKNOWN,
  difficulty: 0,
  reuseQuoteId: null,
});

/** 解析确认：逐行核对型号，按品牌 + 归一化型号复用历史询价，确认后生成询价任务 */
const ConfirmPage: React.FC = () => {
  const { labelOf: sourceLabel } = useDictOptions(DICT_SOURCE_CHANNEL);
  const { lifecycleOptions } = useLifecycles();
  const wide = useWide();
  const { id } = useParams<{ id: string }>();
  const inquiryId = Number(id);
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [rows, setRows] = useState<Row[]>([]);
  const [expanded, setExpanded] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const lastLookup = useRef<Record<string, string>>({});

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const d = await inquiryApi.draft(inquiryId);
      setDraft(d);
      const init = d.rows.map((r) => ({
        ...r,
        key: nextKey(),
        reuseQuoteId: r.match?.defaultQuoteId ?? null,
      }));
      // 离开页面前填过的内容（存在本次浏览器会话里）优先恢复
      const saved = readSaved(inquiryId);
      const start = saved?.length ? saved : init.length ? init : [emptyRow()];
      setRows(start);
      for (const r of start)
        lastLookup.current[r.key] = `${r.brand}|${r.confirmedModel}`;
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [inquiryId]);

  useEffect(() => {
    if (draft && rows.some((r) => r.brand || r.confirmedModel))
      writeSaved(inquiryId, rows);
  }, [rows]);

  const patch = (key: string, p: Partial<Row>) =>
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...p } : r)));

  /** 品牌、型号改完后查历史询价；同品牌有价格时默认复用最低的全新原装 */
  const lookup = async (row: Row) => {
    const sig = `${row.brand.trim()}|${row.confirmedModel.trim()}`;
    if (
      !row.brand.trim() ||
      !row.confirmedModel.trim() ||
      lastLookup.current[row.key] === sig
    )
      return;
    lastLookup.current[row.key] = sig;
    patch(row.key, { matching: true });
    try {
      const match: PriceMatch = await inquiryApi.priceMatch(
        row.brand.trim(),
        row.confirmedModel.trim(),
      );
      patch(row.key, {
        match,
        matching: false,
        reuseQuoteId: match.defaultQuoteId ?? null,
      });
    } catch {
      patch(row.key, { matching: false });
    }
  };

  const reused = rows.filter((r) => r.reuseQuoteId);
  const pending = rows.filter((r) => !r.reuseQuoteId);
  const verifyCount = rows.filter(
    (r) => r.confidence >= CONFIDENCE.PENDING_VERIFY,
  ).length;
  const brands = new Set(
    rows
      .filter((r) => r.brand.trim())
      .map((r) => r.match?.brandKey ?? r.brand.trim().toLowerCase()),
  );

  const groups = useMemo(() => {
    const map = new Map<
      string,
      { brand: string; category: string; count: number }
    >();
    for (const r of pending) {
      if (!r.brand.trim()) continue;
      // 与后端拆任务同口径：品牌按别名识别后的匹配键分组（如「三菱」与「Mitsubishi」是同一组）
      const k = `${r.match?.brandKey ?? r.brand.trim().toLowerCase()}|${r.category.trim()}`;
      const g = map.get(k);
      if (g) g.count += 1;
      else
        map.set(k, {
          brand: r.brand.trim(),
          category: r.category.trim(),
          count: 1,
        });
    }
    return [...map.values()];
  }, [pending]);

  const confirm = async () => {
    const bad = rows.findIndex(
      (r) =>
        !r.brand.trim() ||
        !r.confirmedModel.trim() ||
        !r.quantity ||
        r.quantity < 1,
    );
    if (bad >= 0) {
      message.error(`第 ${bad + 1} 行请填写品牌、型号和数量`);
      return;
    }
    setSaving(true);
    try {
      const payload: ConfirmRow[] = rows.map(
        ({ key: _k, match: _m, matching: _x, ...r }) => r,
      );
      await inquiryApi.confirm(inquiryId, payload);
      message.success(
        pending.length
          ? `已确认，生成 ${groups.length} 个询价任务，等待采购负责人分配`
          : '已确认，全部复用历史价，可以去报价了',
      );
      writeSaved(inquiryId, null);
      history.replace(`${PATHS.inquiries}/${inquiryId}`);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const columns: TableColumnsType<Row> = [
    {
      title: '品牌 / 品类',
      width: 190,
      render: (_, r) => (
        <div style={{ display: 'grid', gap: 6 }}>
          <BrandCategoryPicker
            kind="brand"
            size="small"
            value={r.brand}
            status={r.brand.trim() ? undefined : 'error'}
            onChange={(v) => patch(r.key, { brand: v })}
            onSelect={(v) => lookup({ ...r, brand: v })}
            onBlur={() => lookup(r)}
          />
          <BrandCategoryPicker
            kind="category"
            size="small"
            value={r.category}
            onChange={(v) => patch(r.key, { category: v })}
          />
        </div>
      ),
    },
    {
      title: '型号',
      width: 200,
      render: (_, r) => (
        <div style={{ display: 'grid', gap: 6 }}>
          <Input
            size="small"
            placeholder="型号"
            value={r.confirmedModel}
            status={r.confirmedModel.trim() ? undefined : 'error'}
            onChange={(e) => patch(r.key, { confirmedModel: e.target.value })}
            onBlur={() => lookup(r)}
            onPressEnter={() => lookup(r)}
          />
          {r.confidence === CONFIDENCE.CORRECTED && (
            <Pill tone="cyan">已纠正 · 原文 {r.originalModel}</Pill>
          )}
          {r.confidence >= CONFIDENCE.PENDING_VERIFY && (
            <Pill tone="orange">
              <WarningOutlined /> 待核实
              {r.originalModel ? ` · 原文 ${r.originalModel}` : ''}
            </Pill>
          )}
        </div>
      ),
    },
    {
      title: '描述',
      width: 340,
      render: (_, r) => (
        <Input.TextArea
          size="small"
          placeholder="规格、用途等，可不填"
          autoSize={{ minRows: 1, maxRows: 6 }}
          maxLength={300}
          value={r.description}
          onChange={(e) => patch(r.key, { description: e.target.value })}
        />
      ),
    },
    {
      title: '数量',
      width: 130,
      render: (_, r) => (
        <span style={{ display: 'inline-flex', gap: 6 }}>
          <InputNumber
            size="small"
            min={1}
            precision={0}
            value={r.quantity}
            style={{ width: 70 }}
            onChange={(v) => patch(r.key, { quantity: v ?? 1 })}
          />
          <Input
            size="small"
            placeholder="单位"
            value={r.unit}
            style={{ width: 50 }}
            onChange={(e) => patch(r.key, { unit: e.target.value })}
          />
        </span>
      ),
    },
    {
      title: '生命周期',
      width: 170,
      render: (_, r) => (
        <div style={{ display: 'grid', gap: 6 }}>
          <Select
            size="small"
            value={r.lifecycle}
            options={lifecycleOptions}
            onChange={(v) => patch(r.key, { lifecycle: v })}
          />
          {r.lifecycle === LIFECYCLE_DISCONTINUED && (
            <Input
              size="small"
              placeholder="替代型号"
              value={r.replacementModel}
              onChange={(e) =>
                patch(r.key, { replacementModel: e.target.value })
              }
            />
          )}
        </div>
      ),
    },
    {
      title: '价格来源',
      width: 250,
      render: (_, r) => {
        if (r.matching)
          return (
            <span style={{ color: palette.mute }}>
              <SyncOutlined spin /> 正在查历史询价…
            </span>
          );
        const same = r.match?.sameBrand ?? [];
        if (r.reuseQuoteId) {
          const q = same.find((x) => x.id === r.reuseQuoteId);
          return (
            <div style={{ display: 'grid', gap: 4 }}>
              <span>
                <Pill tone="cyan" dot>
                  复用历史价
                </Pill>
              </span>
              {q && (
                <span style={{ fontSize: 12, color: palette.sub }}>
                  {formatAmount(q.unitPriceCny)} · <AgeText days={q.daysAgo} />
                </span>
              )}
            </div>
          );
        }
        return (
          <div style={{ display: 'grid', gap: 4 }}>
            <span>
              <Pill tone="orange" dot>
                待询价
              </Pill>
            </span>
            {same.length > 0 && (
              <span style={{ fontSize: 12, color: palette.mute }}>
                历史询价有 {same.length} 条，已选重新询价
              </span>
            )}
            {same.length === 0 && (r.match?.otherBrands.length ?? 0) > 0 && (
              <span style={{ fontSize: 12, color: palette.mute }}>
                其他品牌有同型号记录，可展开查看
              </span>
            )}
          </div>
        );
      },
    },
    {
      title: '',
      width: 48,
      render: (_, r) => (
        <Button
          type="text"
          size="small"
          aria-label="删除这一行"
          icon={<DeleteOutlined />}
          onClick={() => setRows((prev) => prev.filter((x) => x.key !== r.key))}
        />
      ),
    },
  ];

  const expandedRow = (r: Row) => {
    const same = r.match?.sameBrand ?? [];
    const others = r.match?.otherBrands ?? [];
    return (
      <div style={{ display: 'grid', gap: 10, padding: '4px 8px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <DatabaseOutlined style={{ color: palette.cyan }} />
          <span style={{ fontWeight: 600, color: palette.ink }}>
            历史询价里 {r.brand} {r.confirmedModel}{' '}
            的价格（同品牌、型号写法不同也会归到一起）
          </span>
          <span
            style={{ marginLeft: 'auto', color: palette.mute, fontSize: 12 }}
          >
            价格太旧？
          </span>
          <Button
            size="small"
            icon={<SyncOutlined />}
            disabled={!r.reuseQuoteId}
            onClick={() => patch(r.key, { reuseQuoteId: null })}
          >
            改为重新询价
          </Button>
        </div>
        <Radio.Group
          value={r.reuseQuoteId}
          onChange={(e) => patch(r.key, { reuseQuoteId: e.target.value })}
          style={{ display: 'grid', gap: 6 }}
        >
          {same.map((q) => (
            <Radio
              key={q.id}
              value={q.id}
              style={{
                padding: '8px 12px',
                borderRadius: 10,
                background:
                  r.reuseQuoteId === q.id ? palette.accentSoft : 'transparent',
                border: `1px solid ${r.reuseQuoteId === q.id ? palette.accentLine : 'transparent'}`,
              }}
            >
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 14,
                }}
              >
                <b style={{ color: palette.ink, minWidth: 90 }}>
                  {formatAmount(q.unitPriceCny)}
                </b>
                <TaxHint
                  taxIncluded={q.taxIncluded}
                  taxRate={q.taxRate}
                  unitPrice={q.unitPrice}
                />
                <ConditionPill value={q.itemCondition} />
                <SupplierText channel={q.channel} shopName={q.shopName} />
                <span style={{ color: palette.sub }}>
                  <LeadTimeText value={q.leadTime} />
                </span>
                <AgeText days={q.daysAgo} date={q.quotedAt} />
                {r.match?.defaultQuoteId === q.id && (
                  <span style={{ color: palette.link, fontSize: 12 }}>
                    默认：全新原装最低价
                  </span>
                )}
              </span>
            </Radio>
          ))}
        </Radio.Group>
        {same.length === 0 && (
          <span style={{ color: palette.mute }}>
            同品牌没有这个型号的历史价格，需要询价。
          </span>
        )}
        {others.length > 0 && (
          <div
            style={{
              display: 'flex',
              gap: 8,
              padding: '8px 12px',
              borderRadius: 10,
              border: `1px solid ${palette.hairline}`,
              color: palette.mute,
              fontSize: 12,
            }}
          >
            <InfoCircleOutlined />
            其他品牌也有同型号记录（仅作参考，不默认复用）：
            {others
              .slice(0, 3)
              .map((q) => `${q.brand} ${formatAmount(q.unitPriceCny)}`)
              .join('；')}
          </div>
        )}
      </div>
    );
  };

  if (loading) {
    return <Skeleton active paragraph={{ rows: 10 }} />;
  }
  if (error || !draft) {
    return <ErrorHint message={error || '加载失败'} onRetry={load} />;
  }
  // 待确认：确认 AI 解析结果；待解析 / 解析失败：手动录入（不改状态，确认时才离开原状态）
  const editable = [
    STATUS.PENDING_PARSE,
    STATUS.PARSE_FAILED,
    STATUS.PENDING_CONFIRM,
  ] as number[];
  if (!editable.includes(draft.inquiry.status)) {
    history.replace(`${PATHS.inquiries}/${inquiryId}`);
    return null;
  }

  const inq = draft.inquiry;
  const manual = inq.status !== STATUS.PENDING_CONFIRM || inq.parseMode === 2;
  const reparse = () =>
    modal.confirm({
      title: manual ? '改用 AI 解析？' : '重新解析这条询盘？',
      content:
        '按询盘内容和附件让 AI 解析，本页已填写的内容会丢弃，解析完成后回到这里确认。',
      okText: '重新解析',
      cancelText: '取消',
      onOk: async () => {
        try {
          await (inq.status === STATUS.PENDING_PARSE
            ? inquiryApi.startParse(inquiryId)
            : inquiryApi.retryParse(inquiryId));
          writeSaved(inquiryId, null);
          message.success('已提交 AI 解析，完成后可重新确认');
          history.push(`${PATHS.inquiries}/${inquiryId}`);
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });
  return (
    <div>
      <PageTitle
        crumbs={['客户询盘', inq.inquiryCode]}
        title={manual ? '手动录入型号' : '确认解析结果'}
        description={`${inq.customerName} · ${sourceLabel(inq.source)} · ${inq.inquiryDate}${inq.urgent ? ' · 紧急' : ''}`}
        actions={
          <>
            <Button
              onClick={() => history.push(`${PATHS.inquiries}/${inquiryId}`)}
            >
              {manual ? '稍后再录入' : '稍后再确认'}
            </Button>
            <Button icon={<RobotOutlined />} onClick={reparse}>
              {manual ? '改用 AI 解析' : '重新解析'}
            </Button>
            <Button
              type="primary"
              icon={<CheckOutlined />}
              loading={saving}
              onClick={confirm}
            >
              确认，生成询价任务
            </Button>
          </>
        }
      />
      <div
        style={{
          display: 'flex',
          gap: 8,
          flexWrap: 'wrap',
          marginBottom: 16,
          alignItems: 'center',
        }}
      >
        <Pill tone="gray">共 {rows.length} 个型号</Pill>
        <Pill tone="gray">{brands.size} 个品牌</Pill>
        <Pill tone="cyan">
          <DatabaseOutlined /> {reused.length} 个复用历史价
        </Pill>
        <Pill tone="orange">{pending.length} 个待询价</Pill>
        {verifyCount > 0 && (
          <Pill tone="orange">
            <WarningOutlined /> {verifyCount} 个待核实
          </Pill>
        )}
        <Button
          size="small"
          icon={<PlusOutlined />}
          style={{ marginLeft: 'auto' }}
          onClick={() => setRows((prev) => [...prev, emptyRow()])}
        >
          手动加一行
        </Button>
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide ? 'minmax(0, 1fr) 360px' : 'minmax(0, 1fr)',
          gap: 20,
          alignItems: 'start',
        }}
      >
        <Table<Row>
          rowKey="key"
          size="middle"
          scroll={{ x: 1330 }}
          columns={columns}
          dataSource={rows}
          pagination={false}
          locale={{ emptyText: '还没有型号，点「手动加一行」开始填写' }}
          expandable={{
            expandedRowKeys: expanded,
            onExpandedRowsChange: (keys) => setExpanded(keys as string[]),
            rowExpandable: (r) =>
              (r.match?.sameBrand.length ?? 0) > 0 ||
              (r.match?.otherBrands.length ?? 0) > 0,
            expandedRowRender: expandedRow,
          }}
        />
        <div style={{ display: 'grid', gap: 16 }}>
          <Card
            title="拆分预览"
            extra={
              <span style={{ color: palette.mute, fontSize: 12 }}>
                只含待询价的型号
              </span>
            }
          >
            {groups.length === 0 ? (
              <div style={{ color: palette.mute }}>
                没有需要询价的型号，确认后直接进入可报价。
              </div>
            ) : (
              <div style={{ display: 'grid', gap: 10 }}>
                {groups.map((g, i) => (
                  <div
                    key={`${g.brand}|${g.category}`}
                    style={{
                      display: 'flex',
                      gap: 12,
                      alignItems: 'center',
                      padding: 12,
                      borderRadius: 12,
                      background: palette.inset,
                    }}
                  >
                    <span
                      style={{
                        width: 30,
                        height: 30,
                        borderRadius: 8,
                        background: palette.accentSoft,
                        color: palette.link,
                        fontWeight: 700,
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      {String.fromCharCode(65 + (i % 26))}
                    </span>
                    <span style={{ display: 'grid' }}>
                      <span style={{ color: palette.ink, fontWeight: 600 }}>
                        {g.brand}
                        {g.category ? ` · ${g.category}` : ''}
                      </span>
                      <span style={{ color: palette.mute, fontSize: 12 }}>
                        {g.count} 个型号
                      </span>
                    </span>
                  </div>
                ))}
                <div style={{ color: palette.mute, fontSize: 12 }}>
                  <ApartmentOutlined /> 确认后进入分配工作台，由采购负责人分配。
                </div>
              </div>
            )}
          </Card>
          <Card title="原始内容">
            <div
              style={{
                whiteSpace: 'pre-wrap',
                color: palette.sub,
                maxHeight: 280,
                overflow: 'auto',
                lineHeight: 1.7,
              }}
            >
              {draft.rawContent || (
                <span style={{ color: palette.mute }}>没有正文</span>
              )}
            </div>
            {draft.attachments.length > 0 && (
              <div style={{ display: 'grid', gap: 6, marginTop: 12 }}>
                {draft.attachments.map((a) => (
                  <a
                    key={a.id}
                    onClick={async () => {
                      const url = await inquiryApi
                        .attachmentBlobUrl(inquiryId, a.id)
                        .catch(() => '');
                      if (url) window.open(url, '_blank', 'noopener');
                    }}
                  >
                    {a.fileName}
                  </a>
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
};

export default ConfirmPage;
