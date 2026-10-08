import {
  ArrowRightOutlined,
  CheckOutlined,
  DownOutlined,
  FormOutlined,
  InboxOutlined,
  RightOutlined,
  SearchOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  App,
  Button,
  Checkbox,
  Drawer,
  Input,
  Segmented,
  Select,
  Skeleton,
  Spin,
  Tag,
} from 'antd';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  CustomerTypePill,
  LevelPill,
  Pill,
  useWide,
} from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  type PickCustomer,
  type PickInquiry,
  type PickItem,
  type Quotation,
  type QuoteInquiry,
  quotationApi,
  readBizError,
} from './service';

type Mode = 'inquiry' | 'items';

const READY = 6;
const SOURCING = 5;
const QUOTED = 7;
const GROUP_LABEL: Record<number, string> = {
  [READY]: '可报价',
  [SOURCING]: '询价中',
  [QUOTED]: '已报价',
};
const GROUP_TONE = {
  [READY]: 'green',
  [SOURCING]: 'accent',
  [QUOTED]: 'violet',
} as const;

/** 滚动到底时调用 onReach（列表底部放一个哨兵元素） */
const useSentinel = (onReach: () => void, enabled: boolean) => {
  const ref = useRef<HTMLDivElement>(null);
  const cb = useRef(onReach);
  cb.current = onReach;
  useEffect(() => {
    const el = ref.current;
    if (!el || !enabled) return undefined;
    const io = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) cb.current();
    });
    io.observe(el);
    return () => io.disconnect();
  }, [enabled]);
  return ref;
};

/** 把命中的关键字高亮 */
const Highlight: React.FC<{ text: string; keyword?: string }> = ({
  text,
  keyword,
}) => {
  const { palette } = useAppTheme();
  const kw = keyword?.trim();
  if (!kw) return <>{text}</>;
  const i = text.toLowerCase().indexOf(kw.toLowerCase());
  if (i < 0) return <>{text}</>;
  return (
    <>
      {text.slice(0, i)}
      <mark
        style={{
          background: palette.accentSoft,
          color: palette.link,
          padding: 0,
          borderRadius: 3,
        }}
      >
        {text.slice(i, i + kw.length)}
      </mark>
      {text.slice(i + kw.length)}
    </>
  );
};

const GroupLabel: React.FC<{ status: number; count?: number }> = ({
  status,
  count,
}) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        margin: '16px 0 8px',
      }}
    >
      <Pill tone={GROUP_TONE[status as keyof typeof GROUP_TONE] ?? 'gray'} dot>
        {GROUP_LABEL[status]}
        {count != null ? ` ${count}` : ''}
      </Pill>
      <span style={{ flex: 1, height: 1, background: palette.hairline }} />
    </div>
  );
};

const deadlineText = (date?: string) => {
  if (!date) return { text: '', tone: 'mute' as const };
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const d = new Date(`${date}T00:00:00`);
  const diff = Math.round((d.getTime() - today.getTime()) / 86400000);
  if (diff < 0) return { text: `已过期 ${-diff} 天`, tone: 'red' as const };
  if (diff === 0) return { text: '今天截止', tone: 'orange' as const };
  return { text: `截止 ${date.slice(5)}`, tone: 'mute' as const };
};

// ---------------------------------------------------------------- 按询盘报价

const ByInquiryPanel: React.FC<{
  selected: QuoteInquiry[];
  onChange: (v: QuoteInquiry[]) => void;
  /** 已有草稿的询盘：点击直接打开那张草稿 */
  onOpenDraft: (quotationId: number) => void;
}> = ({ selected, onChange, onOpenDraft }) => {
  const { palette } = useAppTheme();
  const [keyword, setKeyword] = useState('');
  const [readyOnly, setReadyOnly] = useState(false);
  const [dueToday, setDueToday] = useState(false);
  const [rows, setRows] = useState<QuoteInquiry[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<{ ready: number; sourcing: number }>();
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const reqId = useRef(0);

  const load = useCallback(
    async (p: number) => {
      const id = ++reqId.current;
      setLoading(true);
      setError(undefined);
      try {
        const res = await quotationApi.quoteInquiries({
          keyword: keyword.trim() || undefined,
          readyOnly,
          dueToday,
          page: p,
          pageSize: 20,
        });
        if (id !== reqId.current) return;
        setRows((old) => (p === 1 ? res.records : [...old, ...res.records]));
        setTotal(res.total);
        setCounts({ ready: res.readyCount, sourcing: res.sourcingCount });
        setPage(p);
      } catch (e) {
        if (id === reqId.current) setError(readBizError(e).message);
      } finally {
        if (id === reqId.current) setLoading(false);
      }
    },
    [keyword, readyOnly, dueToday],
  );

  useEffect(() => {
    const t = window.setTimeout(() => load(1), 300);
    return () => window.clearTimeout(t);
  }, [load]);

  const more = rows.length < total;
  const sentinel = useSentinel(() => {
    if (!loading && more) load(page + 1);
  }, more);

  const customerId = selected[0]?.customerId;
  const toggle = (r: QuoteInquiry) => {
    if (selected.some((s) => s.inquiryId === r.inquiryId)) {
      onChange(selected.filter((s) => s.inquiryId !== r.inquiryId));
    } else {
      onChange([...selected, r]);
    }
  };

  const groups = [READY, SOURCING]
    .map((s) => ({ status: s, rows: rows.filter((r) => r.status === s) }))
    .filter((g) => g.rows.length > 0);

  return (
    <div>
      <div
        style={{
          display: 'flex',
          gap: 8,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="搜索客户或询盘编号"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          style={{ width: 300 }}
        />
        <Tag.CheckableTag checked={readyOnly} onChange={setReadyOnly}>
          只看可报价
        </Tag.CheckableTag>
        <Tag.CheckableTag checked={dueToday} onChange={setDueToday}>
          今天截止
        </Tag.CheckableTag>
        <span style={{ marginLeft: 'auto', color: palette.sub, fontSize: 13 }}>
          {counts
            ? `可报价 ${counts.ready} 个 · 询价中 ${counts.sourcing} 个 · 都还没报过价`
            : ''}
        </span>
      </div>

      {error && (
        <div style={{ color: palette.red, margin: '16px 0' }}>
          {error}{' '}
          <a onClick={() => load(1)} style={{ marginLeft: 8 }}>
            重试
          </a>
        </div>
      )}
      {!error && rows.length === 0 && loading && (
        <Skeleton active style={{ marginTop: 16 }} />
      )}
      {!error && rows.length === 0 && !loading && (
        <div
          style={{ padding: '48px 0', textAlign: 'center', color: palette.sub }}
        >
          {keyword || readyOnly || dueToday
            ? '没有符合条件的询盘，换个条件试试'
            : '你的询盘都已经报过价了。新的询盘回价后会出现在这里'}
        </div>
      )}

      {groups.map((g) => (
        <div key={g.status}>
          <GroupLabel
            status={g.status}
            count={g.status === READY ? counts?.ready : counts?.sourcing}
          />
          <div style={{ display: 'grid', gap: 8 }}>
            {g.rows.map((r) => {
              const checked = selected.some((s) => s.inquiryId === r.inquiryId);
              const otherCustomer =
                customerId != null && r.customerId !== customerId;
              const noPrice = r.pricedCount === 0;
              const disabled = !checked && (otherCustomer || noPrice);
              const dl = deadlineText(r.quoteDeadline);
              const draftId = checked ? undefined : r.draftQuotationId;
              const rowStyle: React.CSSProperties = {
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '12px 16px',
                borderRadius: 12,
                background: checked ? palette.accentSoft : palette.inset,
                border: `1px solid ${checked ? palette.accentLine : palette.hairline}`,
                cursor: disabled ? 'not-allowed' : 'pointer',
                transition: 'background 150ms ease-out',
              };
              const body = (
                <>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div
                      style={{
                        display: 'flex',
                        gap: 8,
                        alignItems: 'center',
                        color: disabled ? palette.mute : palette.ink,
                        fontWeight: 600,
                      }}
                    >
                      {r.customerName}
                      <CustomerTypePill type={r.customerType} />
                    </div>
                    <div
                      style={{
                        display: 'flex',
                        gap: 10,
                        alignItems: 'center',
                        fontSize: 12,
                        color: palette.mute,
                        marginTop: 4,
                      }}
                    >
                      <span style={{ color: palette.link }}>
                        {r.inquiryCode}
                      </span>
                      <span>{r.inquiryDate}</span>
                      <LevelPill value={r.level} />
                      {r.urgent && <Pill tone="red">紧急</Pill>}
                    </div>
                  </div>
                  <span
                    style={{
                      color: palette.sub,
                      fontSize: 13,
                      width: 80,
                      textAlign: 'right',
                    }}
                  >
                    {r.itemCount} 个型号
                  </span>
                  <span
                    style={{
                      width: 96,
                      fontSize: 13,
                      color:
                        r.pricedCount >= r.itemCount
                          ? palette.green
                          : palette.sub,
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    已回价 {r.pricedCount}/{r.itemCount}
                  </span>
                  <span
                    style={{
                      width: 96,
                      fontSize: 12,
                      color:
                        dl.tone === 'red'
                          ? palette.red
                          : dl.tone === 'orange'
                            ? palette.orange
                            : palette.mute,
                    }}
                  >
                    {dl.text}
                  </span>
                  <span
                    style={{ width: 150, fontSize: 12, textAlign: 'right' }}
                  >
                    {otherCustomer && !checked ? (
                      <span style={{ color: palette.mute }}>
                        不同客户需要分开报价
                      </span>
                    ) : noPrice ? (
                      <span style={{ color: palette.mute }}>还没有回价</span>
                    ) : r.draftQuotationNo ? (
                      <span
                        style={{
                          display: 'inline-flex',
                          flexDirection: 'column',
                          alignItems: 'flex-end',
                          gap: 2,
                          whiteSpace: 'nowrap',
                        }}
                      >
                        <span style={{ color: palette.orange }}>
                          已有草稿 {r.draftQuotationNo}
                        </span>
                        {r.draftQuotationId != null && (
                          <span style={{ color: palette.link }}>
                            继续编辑 →
                          </span>
                        )}
                      </span>
                    ) : null}
                  </span>
                </>
              );
              if (draftId != null) {
                // 已有草稿：不参与勾选合并，点击直接打开那张草稿继续编辑
                return (
                  <button
                    type="button"
                    key={r.inquiryId}
                    onClick={() => onOpenDraft(draftId)}
                    style={{
                      ...rowStyle,
                      cursor: 'pointer',
                      width: '100%',
                      textAlign: 'left',
                      font: 'inherit',
                    }}
                    aria-label={`打开草稿 ${r.draftQuotationNo}`}
                  >
                    <FormOutlined
                      style={{ color: palette.orange, fontSize: 16 }}
                    />
                    {body}
                  </button>
                );
              }
              return (
                // biome-ignore lint/a11y/noLabelWithoutControl: 内含 antd Checkbox（渲染为 input），点整行切换勾选
                <label key={r.inquiryId} style={rowStyle}>
                  <Checkbox
                    checked={checked}
                    disabled={disabled}
                    onChange={() => toggle(r)}
                  />
                  {body}
                </label>
              );
            })}
          </div>
        </div>
      ))}
      <div ref={sentinel} style={{ height: 1 }} />
      {rows.length > 0 && (
        <div
          style={{
            textAlign: 'center',
            color: palette.mute,
            fontSize: 12,
            margin: '16px 0',
          }}
        >
          {loading ? <Spin size="small" /> : null} 已显示 {rows.length} /{' '}
          {total} 个询盘
          {more ? '，滚动到底自动加载下一批' : ''}
        </div>
      )}
    </div>
  );
};

// ---------------------------------------------------------------- 挑选型号报价

const ItemRow: React.FC<{
  item: PickItem;
  checked: boolean;
  keyword?: string;
  onToggle: () => void;
}> = ({ item, checked, keyword, onToggle }) => {
  const { palette } = useAppTheme();
  const disabled = !item.pickable;
  return (
    // biome-ignore lint/a11y/noLabelWithoutControl: 内含 antd Checkbox（渲染为 input），点整行切换勾选
    <label
      style={{
        display: 'grid',
        gridTemplateColumns: '24px minmax(0, 1fr) 130px 160px',
        alignItems: 'center',
        gap: 12,
        padding: '10px 12px',
        borderRadius: 10,
        background: checked ? palette.accentSoft : 'transparent',
        cursor: disabled ? 'not-allowed' : 'pointer',
      }}
    >
      <Checkbox checked={checked} disabled={disabled} onChange={onToggle} />
      <div style={{ minWidth: 0 }}>
        <div
          style={{
            fontWeight: 600,
            color: disabled ? palette.mute : palette.ink,
          }}
        >
          <Highlight text={item.model} keyword={keyword} />
        </div>
        <div style={{ fontSize: 12, color: palette.mute }}>
          {[item.brand, item.category, `${item.quantity} pcs`]
            .filter(Boolean)
            .join(' · ')}
        </div>
      </div>
      <span
        style={{
          textAlign: 'right',
          fontVariantNumeric: 'tabular-nums',
          color: palette.sub,
        }}
      >
        {item.costPrice != null
          ? formatAmount(item.costPrice, 'CNY')
          : item.noStock
            ? '无货'
            : '—'}
      </span>
      <span style={{ fontSize: 12, textAlign: 'right' }}>
        {disabled ? (
          <span style={{ color: palette.mute }}>{item.disabledReason}</span>
        ) : item.draftQuotationNo ? (
          <span style={{ color: palette.orange }}>
            已在报价单 {item.draftQuotationNo} 草稿中
          </span>
        ) : item.quoted ? (
          <span style={{ color: palette.violet }}>已报过价</span>
        ) : item.noStock ? (
          <span style={{ color: palette.orange }}>无货，报价单上标明无货</span>
        ) : null}
      </span>
    </label>
  );
};

export const PickItemsPanel: React.FC<{
  customer?: PickCustomer;
  onCustomer: (c?: PickCustomer) => void;
  selected: Map<number, number>;
  onChange: (v: Map<number, number>) => void;
  /** 追加型号时客户固定为报价单的客户 */
  lockCustomer?: boolean;
}> = ({ customer, onCustomer, selected, onChange, lockCustomer }) => {
  const { palette } = useAppTheme();
  const [customers, setCustomers] = useState<PickCustomer[]>([]);
  const [customerKw, setCustomerKw] = useState('');
  const [keyword, setKeyword] = useState('');
  const [readyOnly, setReadyOnly] = useState(false);
  const [unquotedOnly, setUnquotedOnly] = useState(false);
  const [rows, setRows] = useState<PickInquiry[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [showAll, setShowAll] = useState<Set<number>>(new Set());
  const reqId = useRef(0);

  useEffect(() => {
    const t = window.setTimeout(() => {
      quotationApi
        .pickCustomers(customerKw.trim() || undefined)
        .then(setCustomers)
        .catch(() => setCustomers([]));
    }, 250);
    return () => window.clearTimeout(t);
  }, [customerKw]);

  const load = useCallback(
    async (p: number) => {
      if (!customer) return;
      const id = ++reqId.current;
      setLoading(true);
      setError(undefined);
      try {
        const res = await quotationApi.pickInquiries({
          customerId: customer.customerId,
          keyword: keyword.trim() || undefined,
          readyOnly,
          unquotedOnly,
          page: p,
          pageSize: 10,
        });
        if (id !== reqId.current) return;
        setRows((old) => (p === 1 ? res.records : [...old, ...res.records]));
        setTotal(res.total);
        setPage(p);
        if (p === 1) {
          // 排在最前的 3 个询盘默认展开；搜索时全部展开
          setExpanded(
            new Set(
              (keyword.trim() ? res.records : res.records.slice(0, 3)).map(
                (r) => r.inquiryId,
              ),
            ),
          );
          setShowAll(new Set());
        }
      } catch (e) {
        if (id === reqId.current) setError(readBizError(e).message);
      } finally {
        if (id === reqId.current) setLoading(false);
      }
    },
    [customer, keyword, readyOnly, unquotedOnly],
  );

  useEffect(() => {
    if (!customer) {
      setRows([]);
      setTotal(0);
      return undefined;
    }
    const t = window.setTimeout(() => load(1), 300);
    return () => window.clearTimeout(t);
  }, [load, customer]);

  const more = rows.length < total;
  const sentinel = useSentinel(() => {
    if (!loading && more) load(page + 1);
  }, more);

  const toggleItem = (inquiryId: number, itemId: number) => {
    const next = new Map(selected);
    if (next.has(itemId)) next.delete(itemId);
    else next.set(itemId, inquiryId);
    onChange(next);
  };
  const selectAll = (inq: PickInquiry) => {
    const next = new Map(selected);
    // 无货型号也一起选：报价单上标明无货或替代型号，客户不用再问
    const pickable = inq.items.filter((i) => i.pickable);
    const allOn = pickable.every((i) => next.has(i.itemId));
    for (const i of pickable) {
      if (allOn) next.delete(i.itemId);
      else next.set(i.itemId, inq.inquiryId);
    }
    onChange(next);
  };

  const groups = [READY, SOURCING, QUOTED]
    .map((s) => ({ status: s, rows: rows.filter((r) => r.status === s) }))
    .filter((g) => g.rows.length > 0);

  return (
    <div>
      <div style={{ marginBottom: 4, color: palette.sub, fontSize: 13 }}>
        客户 <span style={{ color: palette.red }}>*</span>
      </div>
      <Select
        showSearch={{ filterOption: false, onSearch: setCustomerKw }}
        style={{ width: '100%' }}
        placeholder="搜索客户名称"
        disabled={lockCustomer}
        value={customer?.customerId}
        onChange={(v) => onCustomer(customers.find((c) => c.customerId === v))}
        options={(lockCustomer && customer ? [customer] : customers).map(
          (c) => ({
            value: c.customerId,
            label: `${c.customerName}${c.country ? ` · ${c.country}` : ''} · ${c.inquiryCount} 个询盘`,
          }),
        )}
        notFoundContent="你负责的询盘里没有匹配的客户"
      />
      <div style={{ fontSize: 12, color: palette.mute, margin: '4px 0 12px' }}>
        只列出你负责范围内、还有询盘可报的客户；币种与贸易术语从客户档案带出
      </div>

      {customer && (
        <div
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索型号或询盘编号"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 300 }}
          />
          <Tag.CheckableTag checked={readyOnly} onChange={setReadyOnly}>
            只看可报价
          </Tag.CheckableTag>
          <Tag.CheckableTag checked={unquotedOnly} onChange={setUnquotedOnly}>
            只看未报过价的型号
          </Tag.CheckableTag>
          <span
            style={{ marginLeft: 'auto', color: palette.sub, fontSize: 13 }}
          >
            共 {total} 个询盘 · 可报价在前，其次询价中、已报价
          </span>
        </div>
      )}

      {error && (
        <div style={{ color: palette.red, margin: '16px 0' }}>
          {error}
          <a onClick={() => load(1)} style={{ marginLeft: 8 }}>
            重试
          </a>
        </div>
      )}
      {customer && !error && rows.length === 0 && loading && (
        <Skeleton active style={{ marginTop: 16 }} />
      )}
      {customer && !error && rows.length === 0 && !loading && (
        <div
          style={{ padding: '48px 0', textAlign: 'center', color: palette.sub }}
        >
          {keyword
            ? `没有找到包含「${keyword}」的型号或询盘`
            : '这个客户暂时没有可报价的型号'}
        </div>
      )}

      {groups.map((g) => (
        <div key={g.status}>
          <GroupLabel status={g.status} />
          <div style={{ display: 'grid', gap: 10 }}>
            {g.rows.map((inq) => {
              const open = expanded.has(inq.inquiryId);
              const picked = inq.items.filter((i) =>
                selected.has(i.itemId),
              ).length;
              const pickable = inq.items.filter((i) => i.pickable);
              const visible = showAll.has(inq.inquiryId)
                ? inq.items
                : inq.items.slice(0, 5);
              return (
                <div
                  key={inq.inquiryId}
                  style={{
                    border: `1px solid ${palette.hairline}`,
                    borderRadius: 12,
                    padding: '10px 12px',
                    background: palette.inset,
                  }}
                >
                  <div
                    style={{ display: 'flex', alignItems: 'center', gap: 10 }}
                  >
                    <Checkbox
                      checked={pickable.length > 0 && picked >= pickable.length}
                      indeterminate={picked > 0 && picked < pickable.length}
                      disabled={pickable.length === 0}
                      onChange={() => selectAll(inq)}
                      aria-label="全选可报价型号"
                    />
                    <button
                      type="button"
                      aria-label={open ? '收起' : '展开'}
                      onClick={() => {
                        const next = new Set(expanded);
                        if (open) next.delete(inq.inquiryId);
                        else next.add(inq.inquiryId);
                        setExpanded(next);
                      }}
                      style={{
                        all: 'unset',
                        cursor: 'pointer',
                        color: palette.sub,
                        padding: 4,
                      }}
                    >
                      {open ? <DownOutlined /> : <RightOutlined />}
                    </button>
                    <span style={{ color: palette.link, fontWeight: 600 }}>
                      <Highlight text={inq.inquiryCode} keyword={keyword} />
                    </span>
                    <Pill
                      tone={
                        GROUP_TONE[inq.status as keyof typeof GROUP_TONE] ??
                        'gray'
                      }
                      dot
                    >
                      {GROUP_LABEL[inq.status]}
                    </Pill>
                    <span style={{ fontSize: 12, color: palette.mute }}>
                      {inq.inquiryDate}
                    </span>
                    <span
                      style={{
                        marginLeft: 'auto',
                        display: 'flex',
                        gap: 12,
                        alignItems: 'center',
                      }}
                    >
                      {picked > 0 && (
                        <Pill tone="accent">已选 {picked} 个</Pill>
                      )}
                      <span style={{ fontSize: 12, color: palette.mute }}>
                        已回价 {inq.pricedCount} / 共 {inq.itemCount} 个型号
                      </span>
                      {open && pickable.length > 0 && (
                        <a
                          onClick={() => selectAll(inq)}
                          style={{ fontSize: 12 }}
                        >
                          全选可报价型号
                        </a>
                      )}
                    </span>
                  </div>
                  {open && (
                    <div style={{ marginTop: 6 }}>
                      {visible.map((it) => (
                        <ItemRow
                          key={it.itemId}
                          item={it}
                          keyword={keyword}
                          checked={selected.has(it.itemId)}
                          onToggle={() => toggleItem(inq.inquiryId, it.itemId)}
                        />
                      ))}
                      {inq.items.length > 5 && !showAll.has(inq.inquiryId) && (
                        <a
                          style={{
                            display: 'inline-block',
                            margin: '6px 12px',
                            fontSize: 13,
                          }}
                          onClick={() =>
                            setShowAll(new Set(showAll).add(inq.inquiryId))
                          }
                        >
                          <DownOutlined /> 展开其余 {inq.items.length - 5}{' '}
                          个型号
                        </a>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      ))}
      <div ref={sentinel} style={{ height: 1 }} />
      {rows.length > 0 && (
        <div
          style={{
            textAlign: 'center',
            color: palette.mute,
            fontSize: 12,
            margin: '16px 0',
          }}
        >
          {loading ? <Spin size="small" /> : null} 已显示 {rows.length} /{' '}
          {total} 个询盘
          {more ? '，滚动到底自动加载下一批' : ''}
        </div>
      )}
    </div>
  );
};

// ---------------------------------------------------------------- 选择报价形式

const ModeCard: React.FC<{
  active: boolean;
  icon: React.ReactNode;
  title: string;
  description: string;
  points: string[];
  onClick: () => void;
}> = ({ active, icon, title, description, points, onClick }) => {
  const { palette } = useAppTheme();
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        all: 'unset',
        display: 'flex',
        gap: 14,
        padding: 20,
        borderRadius: 14,
        cursor: 'pointer',
        background: active ? palette.accentSoft : palette.inset,
        border: `1.5px solid ${active ? palette.link : palette.hairline}`,
        transition: 'border-color 150ms ease-out, background 150ms ease-out',
      }}
    >
      <span
        style={{
          width: 44,
          height: 44,
          borderRadius: 12,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: palette.card,
          color: active ? palette.link : palette.sub,
          fontSize: 20,
          flex: 'none',
        }}
      >
        {icon}
      </span>
      <span style={{ flex: 1 }}>
        <span
          style={{
            display: 'block',
            fontSize: 16,
            fontWeight: 700,
            color: palette.ink,
          }}
        >
          {title}
        </span>
        <span
          style={{
            display: 'block',
            color: palette.sub,
            fontSize: 13,
            margin: '4px 0 10px',
          }}
        >
          {description}
        </span>
        {points.map((p) => (
          <span
            key={p}
            style={{
              display: 'block',
              fontSize: 13,
              color: palette.sub,
              lineHeight: '24px',
            }}
          >
            <CheckOutlined
              style={{
                color: active ? palette.green : palette.mute,
                marginRight: 8,
              }}
            />
            {p}
          </span>
        ))}
      </span>
      <span
        aria-hidden
        style={{
          width: 16,
          height: 16,
          borderRadius: 8,
          border: `2px solid ${active ? palette.link : palette.control}`,
          background: active ? palette.link : 'transparent',
          flex: 'none',
        }}
      />
    </button>
  );
};

// ---------------------------------------------------------------- 抽屉

const NewQuotationModal: React.FC<{
  open: boolean;
  onClose: () => void;
  onCreated: (q: Quotation) => void;
}> = ({ open, onClose, onCreated }) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const [step, setStep] = useState<'choose' | 'pick'>('choose');
  const [mode, setMode] = useState<Mode>('inquiry');
  const [inquiries, setInquiries] = useState<QuoteInquiry[]>([]);
  const [customer, setCustomer] = useState<PickCustomer>();
  const [items, setItems] = useState<Map<number, number>>(new Map());
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setStep('choose');
      setMode('inquiry');
      setInquiries([]);
      setCustomer(undefined);
      setItems(new Map());
    }
  }, [open]);

  const hasSelection = inquiries.length > 0 || items.size > 0;
  const switchMode = (next: Mode) => {
    if (next === mode) return;
    const apply = () => {
      setMode(next);
      setInquiries([]);
      setItems(new Map());
      setCustomer(undefined);
    };
    if (hasSelection) {
      modal.confirm({
        title: '切换报价形式？',
        content: '切换后会清空已选的询盘或型号',
        okText: '切换',
        onOk: apply,
      });
    } else {
      apply();
    }
  };

  const summary = useMemo(() => {
    if (mode === 'inquiry') {
      if (inquiries.length === 0) return null;
      const bring = inquiries.reduce((s, i) => s + i.pricedCount, 0);
      const pending = inquiries.filter((i) => i.pricedCount < i.itemCount);
      return {
        main: `已选 ${inquiries.length} 个询盘 · ${inquiries[0].customerName} · 将带入约 ${bring} 个型号`,
        hint: pending
          .map(
            (i) =>
              `${i.inquiryCode} 还有 ${i.itemCount - i.pricedCount} 个型号在询价，回价后可以再报`,
          )
          .join('；'),
      };
    }
    if (items.size === 0) return null;
    return {
      main: `已选 ${items.size} 个型号，来自 ${new Set(items.values()).size} 个询盘`,
      hint: '同一型号可能出现在多个询盘里，各自独立成行',
    };
  }, [mode, inquiries, items]);

  const create = async () => {
    setSubmitting(true);
    try {
      const q = await quotationApi.create(
        mode === 'inquiry'
          ? { inquiryIds: inquiries.map((i) => i.inquiryId) }
          : { itemIds: [...items.keys()] },
      );
      if (q.pendingItemCount) {
        message.info(`还有 ${q.pendingItemCount} 个型号在询价，回价后可以再报`);
      } else {
        message.success(`报价单 ${q.quotationNo} 已生成`);
      }
      onCreated(q);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size={wide ? 1000 : '100%'}
      destroyOnHidden
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          新建报价单
          {step === 'pick' && (
            <>
              <Segmented<Mode>
                value={mode}
                onChange={switchMode}
                options={[
                  {
                    value: 'inquiry',
                    label: '按询盘报价',
                    icon: <InboxOutlined />,
                  },
                  {
                    value: 'items',
                    label: '挑选型号报价',
                    icon: <UnorderedListOutlined />,
                  },
                ]}
              />
              <span
                style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}
              >
                切换形式会清空已选内容
              </span>
            </>
          )}
        </div>
      }
      footer={
        step === 'choose' ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ fontSize: 12, color: palette.mute }}>
              从客户询盘详情点「去报价」时不经过这一步，直接按询盘报价
            </span>
            <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
              <Button onClick={onClose}>取消</Button>
              <Button
                type="primary"
                icon={<ArrowRightOutlined />}
                onClick={() => setStep('pick')}
              >
                下一步
              </Button>
            </span>
          </div>
        ) : (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontWeight: 600, color: palette.ink }}>
                {summary?.main ??
                  (mode === 'inquiry'
                    ? '勾选要报价的询盘（同一客户可以多选）'
                    : '先选客户，再勾选要报价的型号')}
              </div>
              {summary?.hint && (
                <div
                  style={{
                    fontSize: 12,
                    color: mode === 'inquiry' ? palette.orange : palette.mute,
                    marginTop: 2,
                  }}
                >
                  {summary.hint}
                </div>
              )}
            </div>
            <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
              <Button onClick={onClose}>取消</Button>
              <Button
                type="primary"
                icon={<ArrowRightOutlined />}
                disabled={!summary}
                loading={submitting}
                onClick={create}
              >
                生成报价单
              </Button>
            </span>
          </div>
        )
      }
    >
      {step === 'choose' ? (
        <div style={{ display: 'grid', gap: 16 }}>
          <ModeCard
            active={mode === 'inquiry'}
            icon={<InboxOutlined />}
            title="按询盘报价"
            description="从还没报价的询盘里选，带入询盘里全部已回价的型号。适合把待报价的询盘一个个报掉。"
            points={[
              '可报价的询盘排在最前，其次是询价中',
              '可以勾选同一客户的多个询盘合并成一张',
              '询价中的询盘先报已回价的型号，其余回价后再报',
            ]}
            onClick={() => setMode('inquiry')}
          />
          <ModeCard
            active={mode === 'items'}
            icon={<UnorderedListOutlined />}
            title="挑选型号报价"
            description="先选客户，再从他的多个询盘里挑型号组合成一张报价单。适合客户只要其中几个型号，或要跨询盘合并。"
            points={[
              '按型号或询盘编号搜索',
              '询盘多时分批加载、折叠展示',
              '已报过、已在草稿里的型号会提示',
            ]}
            onClick={() => setMode('items')}
          />
        </div>
      ) : mode === 'inquiry' ? (
        <ByInquiryPanel
          selected={inquiries}
          onChange={setInquiries}
          onOpenDraft={(qid) => {
            onClose();
            history.push(`/quotation/quotations/${qid}`);
          }}
        />
      ) : (
        <PickItemsPanel
          customer={customer}
          onCustomer={(c) => {
            setCustomer(c);
            setItems(new Map());
          }}
          selected={items}
          onChange={setItems}
        />
      )}
    </Drawer>
  );
};

export default NewQuotationModal;
