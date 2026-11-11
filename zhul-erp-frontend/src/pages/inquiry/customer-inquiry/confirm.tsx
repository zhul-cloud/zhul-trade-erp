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
  Collapse,
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
import { type CategoryOption, categoryApi } from '@/pages/product/service';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import { tableScrollX } from '@/utils/tableScroll';
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
import { FileTile, useFilePreview } from '../shared/FilePreview';
import { movePlate, type PreviewFile, plateOwners } from '../shared/files';
import QuoteExtras from '../shared/QuoteExtras';
import {
  type ConfirmRow,
  type Draft,
  type DraftRow,
  inquiryApi,
  type PriceMatch,
  readBizError,
} from '../shared/service';
import { UnparsedAlert } from './ParseProgress';

interface Row extends DraftRow {
  key: string;
  /** 复用的询价记录；null 表示重新询价 */
  reuseQuoteId: number | null;
  matching?: boolean;
  /** 改品牌、品类的过程中冻结的分组键，选中或失焦后清空，行才换组（避免边输入边跳） */
  groupKey?: string;
}

/** 组内每页型号数 */
const GROUP_PAGE_SIZE = 20;
/** 没填品牌的行单独一组，排在最前 */
const NO_BRAND = '';

let seq = 0;

const savedKey = (id: number | string) => `inquiry-confirm-draft-${id}`;

const readSaved = (id: number | string): Row[] | null => {
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

const writeSaved = (id: number | string, rows: Row[] | null) => {
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

/** 历史价超过这个天数时提醒考虑重新询价 */
const STALE_DAYS = 90;

/** 最近一次的询价记录 */
const latestOf = (quotes: { id: number; daysAgo?: number }[]) =>
  quotes.reduce<{ id: number; daysAgo?: number } | undefined>(
    (best, q) =>
      q.daysAgo != null && (best?.daysAgo == null || q.daysAgo < best.daysAgo)
        ? q
        : best,
    undefined,
  )?.id;

/** 解析确认：逐行核对型号，按品牌 + 归一化型号复用询价记录，确认后生成询价任务 */
const ConfirmPage: React.FC = () => {
  const { labelOf: sourceLabel } = useDictOptions(DICT_SOURCE_CHANNEL);
  const { lifecycleOptions } = useLifecycles();
  const wide = useWide();
  const { id } = useParams<{ id: string }>();
  const inquiryId = Number(id);
  // 追加型号：?append=批次ID，只确认这次追加的内容
  const appendId =
    Number(new URLSearchParams(window.location.search).get('append')) ||
    undefined;
  const store = appendId ? `${inquiryId}-a${appendId}` : inquiryId;
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [rows, setRows] = useState<Row[]>([]);
  const preview = useFilePreview();
  /** 铭牌图片 → 对应的行（一张图只给一行） */
  const owners = useMemo(() => plateOwners(rows), [rows]);
  const plates = draft?.nameplates ?? [];
  const endpointOf = (f: PreviewFile) =>
    inquiryApi.attachmentEndpoint(inquiryId, f.id);
  const rowLabel = (key?: string) => {
    const i = rows.findIndex((r) => r.key === key);
    return i < 0
      ? '未对应型号'
      : `第 ${i + 1} 行${rows[i].confirmedModel ? ` · ${rows[i].confirmedModel}` : ''}`;
  };
  const openPlate = (list: PreviewFile[], f: PreviewFile) =>
    preview.open(
      list,
      list.indexOf(f),
      endpointOf,
      (x) => `铭牌图片 · ${rowLabel(owners.get(x.id))}`,
    );
  const [expanded, setExpanded] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [categories, setCategories] = useState<CategoryOption[]>([]);
  const [categoriesReady, setCategoriesReady] = useState(false);
  /** 展开的分组；首次加载完按「有待核实 / 只有一组」初始化 */
  const [openGroups, setOpenGroups] = useState<string[] | null>(null);
  const [groupPages, setGroupPages] = useState<Record<string, number>>({});
  const lastGroupOf = useRef<Map<string, string> | null>(null);
  const lastLookup = useRef<Record<string, string>>({});
  const toggleExpanded = (key: string) =>
    setExpanded((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key],
    );

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const d = appendId
        ? await inquiryApi.appendDraft(inquiryId, appendId)
        : await inquiryApi.draft(inquiryId);
      setDraft(d);
      const init = d.rows.map((r) => ({
        ...r,
        key: nextKey(),
        reuseQuoteId: r.match?.defaultQuoteId ?? null,
      }));
      // 离开页面前填过的内容（存在本次浏览器会话里）优先恢复
      const saved = readSaved(store);
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
    categoryApi
      .options(0)
      .then(setCategories)
      .catch(() => {})
      .finally(() => setCategoriesReady(true));
  }, []);

  const categoryName = (c: CategoryOption) =>
    c.categoryNameZh || c.categoryName;
  /** 下拉里的品类名对应的品类；手填的文字对应不上时为空，确认时由后端按文字匹配 */
  const categoryIdOf = (text: string) =>
    categories.find((c) => categoryName(c) === text.trim())?.id ?? null;
  /** 一级品类：细分取上级；不在清单里时为空 */
  const rootOf = (categoryId?: number | null) => {
    const c = categories.find((x) => x.id === categoryId);
    if (!c) return undefined;
    return c.parentId ? categories.find((x) => x.id === c.parentId) : c;
  };

  /** 与后端拆任务同口径：品牌按别名识别后的匹配键 + 一级品类（「伺服驱动器」「伺服电机」同属「伺服与运动」）；没关联品类的按品类文字 */
  const keyOf = (r: Row) => {
    if (!r.brand.trim()) return NO_BRAND;
    const root = rootOf(r.categoryId);
    return `${r.match?.brandKey ?? r.brand.trim().toLowerCase()}|${root ? `c:${root.id}` : `t:${r.category.trim()}`}`;
  };
  const groupKeyOf = (r: Row) => r.groupKey ?? keyOf(r);
  /** 改品牌、品类时先冻结当前分组，选中或失焦后再换组 */
  const freeze = (r: Row) => r.groupKey ?? keyOf(r);
  const settle = (key: string) =>
    setRows((prev) =>
      prev.map((r) => (r.key === key ? { ...r, groupKey: undefined } : r)),
    );

  const tableGroups = useMemo(() => {
    const map = new Map<
      string,
      {
        key: string;
        brand: string;
        category: string;
        items: { row: Row; no: number }[];
      }
    >();
    rows.forEach((r, i) => {
      const k = groupKeyOf(r);
      let g = map.get(k);
      if (!g) {
        const root = rootOf(r.categoryId);
        g = {
          key: k,
          brand: k === NO_BRAND ? '未填品牌' : r.brand.trim(),
          category:
            k === NO_BRAND ? '' : root ? categoryName(root) : r.category.trim(),
          items: [],
        };
        map.set(k, g);
      }
      // 组名取组内第一个填了品牌的行（正在改品牌的行清空时组名不跟着变）
      if (!g.brand && k !== NO_BRAND) g.brand = r.brand.trim();
      g.items.push({ row: r, no: i + 1 });
    });
    const list = [...map.values()];
    const blank = list.filter((g) => g.key === NO_BRAND);
    return [...blank, ...list.filter((g) => g.key !== NO_BRAND)];
  }, [rows, categories]);

  // 初始展开：有待核实型号的组，只有一组时直接展开；之后行换组、新加行时展开所到的组
  useEffect(() => {
    if (loading || !categoriesReady || rows.length === 0) return;
    const now = new Map(rows.map((r) => [r.key, groupKeyOf(r)]));
    const before = lastGroupOf.current;
    lastGroupOf.current = now;
    if (!before || openGroups === null) {
      setOpenGroups(
        tableGroups.length === 1
          ? [tableGroups[0].key]
          : tableGroups
              .filter((g) =>
                g.items.some(
                  ({ row }) => row.confidence >= CONFIDENCE.PENDING_VERIFY,
                ),
              )
              .map((g) => g.key),
      );
      return;
    }
    const moved = [...now]
      .filter(([k, g]) => before.get(k) !== g)
      .map(([, g]) => g);
    if (moved.length)
      setOpenGroups((prev) => [...new Set([...(prev ?? []), ...moved])]);
  }, [rows, loading, categoriesReady]);

  useEffect(() => {
    if (draft && rows.some((r) => r.brand || r.confirmedModel))
      writeSaved(store, rows);
  }, [rows]);

  const patch = (key: string, p: Partial<Row>) =>
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...p } : r)));

  /** 品牌、型号改完后查询价记录；同品牌有价格时默认复用最低的全新原装 */
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
      const k = keyOf(r);
      if (k === NO_BRAND) continue;
      const g = map.get(k);
      if (g) g.count += 1;
      else {
        const root = rootOf(r.categoryId);
        map.set(k, {
          brand: r.brand.trim(),
          category: root ? categoryName(root) : r.category.trim(),
          count: 1,
        });
      }
    }
    return [...map.values()];
  }, [pending, categories]);

  /** 展开出错行所在的组、翻到它所在的页并滚动过去 */
  const reveal = (rowKey: string) => {
    const g = tableGroups.find((x) =>
      x.items.some(({ row }) => row.key === rowKey),
    );
    if (!g) return;
    const at = g.items.findIndex(({ row }) => row.key === rowKey);
    setOpenGroups((prev) => [...new Set([...(prev ?? []), g.key])]);
    setGroupPages((prev) => ({
      ...prev,
      [g.key]: Math.floor(at / GROUP_PAGE_SIZE) + 1,
    }));
    setTimeout(
      () =>
        document
          .querySelector(`[data-row-key="${rowKey}"]`)
          ?.scrollIntoView({ block: 'center', behavior: 'smooth' }),
      100,
    );
  };

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
      reveal(rows[bad].key);
      return;
    }
    setSaving(true);
    try {
      const payload: ConfirmRow[] = rows.map(
        ({ key: _k, match: _m, matching: _x, ...r }) => r,
      );
      if (appendId)
        await inquiryApi.confirmAppend(inquiryId, appendId, payload);
      else await inquiryApi.confirm(inquiryId, payload);
      message.success(
        pending.length
          ? `已确认，生成 ${groups.length} 个询价任务，等待采购负责人分配`
          : '已确认，全部复用历史价，可以去报价了',
      );
      writeSaved(store, null);
      history.replace(`${PATHS.inquiries}/${inquiryId}`);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const columns: TableColumnsType<Row> = [
    {
      title: '#',
      width: 48,
      render: (_, r) => (
        <span style={{ color: palette.mute, fontSize: 12 }}>
          {rows.findIndex((x) => x.key === r.key) + 1}
        </span>
      ),
    },
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
            onChange={(v) => patch(r.key, { brand: v, groupKey: freeze(r) })}
            onSelect={(v) => {
              lookup({ ...r, brand: v });
              settle(r.key);
            }}
            onBlur={() => {
              lookup(r);
              settle(r.key);
            }}
          />
          <BrandCategoryPicker
            kind="category"
            size="small"
            value={r.category}
            onChange={(v) =>
              patch(r.key, {
                category: v,
                categoryId: categoryIdOf(v),
                groupKey: freeze(r),
              })
            }
            onSelect={() => settle(r.key)}
            onBlur={() => settle(r.key)}
          />
        </div>
      ),
    },
    {
      title: '型号 / 订货号',
      width: 240,
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
          <Input
            size="small"
            prefix={<span style={{ color: palette.mute }}>订货号</span>}
            placeholder="选填，多个用逗号隔开"
            value={r.orderCode ?? ''}
            maxLength={255}
            onChange={(e) => patch(r.key, { orderCode: e.target.value })}
            aria-label="订货号"
          />
          {(() => {
            const mine = plates.filter((p) => owners.get(p.id) === r.key);
            if (mine.length === 0) return null;
            return (
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                {mine.map((f) => (
                  <FileTile
                    key={f.id}
                    file={f}
                    endpoint={endpointOf(f)}
                    width={40}
                    height={30}
                    showName={false}
                    onOpen={() => openPlate(mine, f)}
                  />
                ))}
                <span style={{ fontSize: 12, color: palette.link }}>
                  {mine.length} 张铭牌
                </span>
              </div>
            );
          })()}
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
      title: '描述（中 / EN）',
      width: 360,
      render: (_, r) => (
        <div style={{ display: 'grid', gap: 6, width: 336 }}>
          <Input.TextArea
            size="small"
            placeholder="中文：规格、用途，采购询价用，可不填"
            autoSize={{ minRows: 1, maxRows: 6 }}
            maxLength={300}
            value={r.description}
            onChange={(e) => patch(r.key, { description: e.target.value })}
            aria-label="中文描述"
          />
          <Input.TextArea
            size="small"
            placeholder="EN：给客户看（报价单、PI），可不填"
            autoSize={{ minRows: 1, maxRows: 6 }}
            maxLength={300}
            value={r.descriptionEn}
            onChange={(e) => patch(r.key, { descriptionEn: e.target.value })}
            aria-label="英文描述"
          />
        </div>
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
              <SyncOutlined spin /> 正在查询价记录…
            </span>
          );
        const same = r.match?.sameBrand ?? [];
        const actions: React.CSSProperties = {
          display: 'flex',
          gap: 12,
          fontSize: 12,
        };
        if (r.reuseQuoteId) {
          const q = same.find((x) => x.id === r.reuseQuoteId);
          return (
            <div style={{ display: 'grid', gap: 4 }}>
              <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <Pill tone="cyan" dot>
                  复用历史价
                </Pill>
                {q && (q.daysAgo ?? 0) > STALE_DAYS && (
                  <Pill tone="orange">超过 {STALE_DAYS} 天</Pill>
                )}
              </span>
              {q && (
                <span style={{ fontSize: 12, color: palette.sub }}>
                  {formatAmount(q.unitPriceCny)} · <AgeText days={q.daysAgo} />
                </span>
              )}
              <span style={actions}>
                <a onClick={() => toggleExpanded(r.key)}>
                  {same.length} 条历史价 · 换一个
                </a>
                <a onClick={() => patch(r.key, { reuseQuoteId: null })}>
                  改为重新询价
                </a>
              </span>
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
              <span style={actions}>
                <span style={{ color: palette.mute }}>
                  有 {same.length} 条历史价
                </span>
                <a
                  onClick={() =>
                    patch(r.key, {
                      reuseQuoteId: r.match?.defaultQuoteId ?? same[0].id,
                    })
                  }
                >
                  改回复用历史价
                </a>
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
            询价记录里 {r.brand} {r.confirmedModel}{' '}
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
                <QuoteExtras quote={q} />
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
                {latestOf(same) === q.id && (
                  <span style={{ color: palette.link, fontSize: 12 }}>
                    最新
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
  if (!appendId && !editable.includes(draft.inquiry.status)) {
    history.replace(`${PATHS.inquiries}/${inquiryId}`);
    return null;
  }

  const inq = draft.inquiry;
  const manual = appendId
    ? draft.rows.length === 0
    : inq.status !== STATUS.PENDING_CONFIRM || inq.parseMode === 2;
  const reparse = () =>
    modal.confirm({
      title: manual ? '改用 AI 解析？' : '重新解析这条询盘？',
      content:
        '按询盘内容和附件让 AI 解析，本页已填写的内容会丢弃，解析完成后回到这里确认。',
      okText: '重新解析',
      cancelText: '取消',
      onOk: async () => {
        try {
          await (appendId
            ? inquiryApi.retryAppend(inquiryId, appendId)
            : inq.status === STATUS.PENDING_PARSE
              ? inquiryApi.startParse(inquiryId)
              : inquiryApi.retryParse(inquiryId));
          writeSaved(store, null);
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
        crumbs={[<MenuLink key="list" to={PATHS.inquiries} />, inq.inquiryCode]}
        title={
          appendId
            ? manual
              ? '追加型号：手动录入'
              : '追加型号：确认解析结果'
            : manual
              ? '手动录入型号'
              : '确认解析结果'
        }
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
            {reused.length > 0 && (
              <Button
                icon={<SyncOutlined />}
                onClick={() =>
                  setRows((prev) =>
                    prev.map((x) => ({ ...x, reuseQuoteId: null })),
                  )
                }
              >
                全部改为重新询价
              </Button>
            )}
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
      {plates.length > 0 && (
        <Card style={{ padding: 16, marginBottom: 16 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginBottom: 12,
            }}
          >
            <b style={{ color: palette.ink }}>铭牌图片</b>
            <Pill tone="green">
              已对应 {plates.filter((p) => owners.has(p.id)).length}
            </Pill>
            {plates.some((p) => !owners.has(p.id)) && (
              <Pill tone="orange">
                未对应 {plates.filter((p) => !owners.has(p.id)).length}
              </Pill>
            )}
            <span
              style={{ marginLeft: 'auto', fontSize: 12, color: palette.mute }}
            >
              点图片预览；改对应的行用图片下面的下拉。删掉某行时，对应这行的图变为未对应
            </span>
          </div>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            {plates.map((f) => {
              const owner = owners.get(f.id);
              return (
                <div key={f.id} style={{ display: 'grid', gap: 8, width: 180 }}>
                  <FileTile
                    file={f}
                    endpoint={endpointOf(f)}
                    width={180}
                    height={120}
                    showName={false}
                    active={!owner}
                    onOpen={() => openPlate(plates, f)}
                  />
                  <Select
                    size="small"
                    style={{ width: 180 }}
                    value={owner ?? ''}
                    status={owner ? undefined : 'warning'}
                    aria-label={`${f.fileName} 对应的行`}
                    popupMatchSelectWidth={false}
                    onChange={(key: string) =>
                      setRows((prev) => movePlate(prev, f.id, key || undefined))
                    }
                    options={[
                      ...rows.map((r, i) => ({
                        value: r.key,
                        label: `第 ${i + 1} 行${r.confirmedModel ? ` · ${r.confirmedModel}` : ''}`,
                      })),
                      { value: '', label: '未对应型号' },
                    ]}
                  />
                </div>
              );
            })}
          </div>
        </Card>
      )}
      <UnparsedAlert parts={draft.unparsed} />
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
          style={{ marginLeft: 'auto' }}
          onClick={() => setOpenGroups(tableGroups.map((g) => g.key))}
        >
          全部展开
        </Button>
        <Button size="small" onClick={() => setOpenGroups([])}>
          全部收起
        </Button>
        <Button
          size="small"
          icon={<PlusOutlined />}
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
        {rows.length === 0 ? (
          <Card>
            <span style={{ color: palette.mute }}>
              还没有型号，点「手动加一行」开始填写
            </span>
          </Card>
        ) : (
          <Collapse
            activeKey={openGroups ?? []}
            onChange={(keys) => setOpenGroups(keys as string[])}
            style={{ background: 'transparent', display: 'grid', gap: 12 }}
            bordered={false}
            items={tableGroups.map((g) => {
              const total = g.items.length;
              const pages = Math.ceil(total / GROUP_PAGE_SIZE);
              const page = Math.min(groupPages[g.key] ?? 1, pages);
              const waiting = g.items.filter(
                ({ row }) => !row.reuseQuoteId,
              ).length;
              const verify = g.items.filter(
                ({ row }) => row.confidence >= CONFIDENCE.PENDING_VERIFY,
              ).length;
              const open = (openGroups ?? []).includes(g.key);
              return {
                key: g.key,
                style: {
                  borderRadius: 14,
                  overflow: 'hidden',
                  border: `1px solid ${open ? palette.accentLine : palette.hairline}`,
                  background: palette.card,
                },
                styles: { body: { padding: 0 } },
                label: (
                  <span
                    style={{
                      display: 'flex',
                      flexWrap: 'wrap',
                      gap: 8,
                      alignItems: 'center',
                    }}
                  >
                    <b
                      style={{
                        color:
                          g.key === NO_BRAND ? palette.orange : palette.ink,
                        fontSize: 15,
                      }}
                    >
                      {g.brand}
                    </b>
                    {g.category && (
                      <span style={{ color: palette.sub }}>· {g.category}</span>
                    )}
                    <Pill tone="gray">{total} 个型号</Pill>
                    <span style={{ color: palette.mute, fontSize: 12 }}>
                      待询价 {waiting} · 复用历史价 {total - waiting}
                    </span>
                    {verify > 0 && (
                      <Pill tone="orange">
                        <WarningOutlined /> {verify} 个待核实
                      </Pill>
                    )}
                  </span>
                ),
                extra:
                  open && pages > 1 ? (
                    <span style={{ color: palette.mute, fontSize: 12 }}>
                      第 {page} / {pages} 页
                    </span>
                  ) : undefined,
                children: (
                  <Table<Row>
                    rowKey="key"
                    size="middle"
                    scroll={{ x: tableScrollX(columns) }}
                    columns={columns}
                    dataSource={g.items.map(({ row }) => row)}
                    pagination={{
                      current: page,
                      pageSize: GROUP_PAGE_SIZE,
                      hideOnSinglePage: true,
                      showSizeChanger: false,
                      showTotal: (t: number) =>
                        `本组 ${t} 个 · 每页 ${GROUP_PAGE_SIZE} 个`,
                      onChange: (p: number) =>
                        setGroupPages((prev) => ({ ...prev, [g.key]: p })),
                    }}
                    expandable={{
                      expandedRowKeys: expanded,
                      onExpandedRowsChange: (keys) =>
                        setExpanded((prev) => [
                          ...prev.filter(
                            (k) => !g.items.some(({ row }) => row.key === k),
                          ),
                          ...(keys as string[]),
                        ]),
                      rowExpandable: (r) =>
                        (r.match?.sameBrand.length ?? 0) > 0 ||
                        (r.match?.otherBrands.length ?? 0) > 0,
                      expandedRowRender: expandedRow,
                    }}
                  />
                ),
              };
            })}
          />
        )}
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
                  <ApartmentOutlined /> 确认后进入询价分配，由采购负责人分配。
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
              <div
                style={{
                  display: 'flex',
                  gap: 12,
                  flexWrap: 'wrap',
                  marginTop: 12,
                }}
              >
                {draft.attachments.map((a, i) => (
                  <FileTile
                    key={a.id}
                    file={a}
                    endpoint={endpointOf(a)}
                    width={88}
                    height={66}
                    onOpen={() =>
                      preview.open(
                        draft.attachments,
                        i,
                        endpointOf,
                        () => '询盘附件',
                      )
                    }
                  />
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>
      {preview.node}
    </div>
  );
};

export default ConfirmPage;
