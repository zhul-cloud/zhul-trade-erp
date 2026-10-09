import {
  CalendarOutlined,
  CheckOutlined,
  DeleteOutlined,
  FallOutlined,
  FilePdfOutlined,
  FileProtectOutlined,
  HistoryOutlined,
  PlusOutlined,
  StopOutlined,
  UploadOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Select,
  Skeleton,
  Space,
  Table,
  Upload,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { CURRENCIES } from '@/pages/quotation/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { bargainOf, netPrice, rateOf, round2, termsTotal } from '../calc';
import {
  BargainText,
  Card,
  PATHS,
  PaymentTermsEditor,
  Pill,
  PO_STATUS,
  PoStatusPill,
  PurchasePageTitle,
  StatCard,
  SupplierPicker,
  sub,
} from '../components';
import {
  type PaymentTerm,
  type PoItem,
  type PurchaseOrder,
  poApi,
  readBizError,
  type SavePoBody,
} from '../service';

interface Draft {
  supplierId?: number;
  supplierName?: string;
  currencyCode: string;
  taxIncluded: boolean;
  taxRate: number;
  paymentTerms: PaymentTerm[];
  contractNo: string;
  contractAmount: number | null;
  items: Record<number, { quantity: number; unitPrice: number | null }>;
  fees: { key: number; feeName: string; amount: number | null }[];
}

let feeKey = 0;

const toDraft = (po: PurchaseOrder): Draft => ({
  supplierId: po.supplierId,
  supplierName: po.supplierName,
  currencyCode: po.currencyCode,
  taxIncluded: po.taxIncluded,
  taxRate: po.taxIncluded ? Number(po.taxRate) : 13,
  paymentTerms: po.paymentTerms,
  contractNo: po.contractNo ?? '',
  contractAmount: po.contractAmount ?? null,
  items: Object.fromEntries(
    po.items.map((i) => [
      i.id,
      { quantity: i.quantity, unitPrice: i.unitPrice ?? null },
    ]),
  ),
  fees: po.fees.map((f) => ({
    key: ++feeKey,
    feeName: f.feeName,
    amount: Number(f.amount),
  })),
});

const toBody = (po: PurchaseOrder, d: Draft): SavePoBody => ({
  supplierId: po.status === PO_STATUS.DRAFT ? d.supplierId : undefined,
  currencyCode: d.currencyCode,
  taxIncluded: d.taxIncluded,
  taxRate: d.taxIncluded ? d.taxRate : 0,
  paymentTerms: d.paymentTerms,
  contractNo: d.contractNo.trim() || undefined,
  contractAmount: d.contractAmount,
  items: po.items.map((i) => ({
    id: i.id,
    quantity: d.items[i.id]?.quantity ?? i.quantity,
    unitPrice: d.items[i.id]?.unitPrice ?? null,
  })),
  fees: d.fees
    .filter((f) => f.feeName.trim() && f.amount != null)
    .map((f) => ({ feeName: f.feeName.trim(), amount: f.amount as number })),
});

const PurchaseOrderDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const poId = Number(id);
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess();
  const canEdit = !!access['purchase:order:create'];
  const canCancel = !!access['purchase:order:cancel'];
  const [po, setPo] = useState<PurchaseOrder>();
  const [error, setError] = useState<string>();
  const [draft, setDraft] = useState<Draft>();
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [picked, setPicked] = useState<number[]>([]);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [moveOpen, setMoveOpen] = useState(false);

  const apply = useCallback((p: PurchaseOrder) => {
    setPo(p);
    setDraft(toDraft(p));
    setPicked([]);
  }, []);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      const p = await poApi.detail(poId);
      apply(p);
      setEditing(p.status === PO_STATUS.DRAFT);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [poId, apply]);

  useEffect(() => {
    load();
  }, [load]);

  const isDraft = po?.status === PO_STATUS.DRAFT;
  const editable =
    !!po && canEdit && po.status !== PO_STATUS.CANCELLED && editing;

  // 即时预览：同币种用当前汇率；换了外币时保存后按系统汇率换算
  const preview = useMemo(() => {
    if (!po || !draft) return undefined;
    const rate =
      draft.currencyCode === po.currencyCode ? Number(po.exchangeRate) : 1;
    const tax = draft.taxIncluded ? draft.taxRate : 0;
    let goods = 0;
    let bargain = 0;
    let target = 0;
    let missing = 0;
    const lines = new Map<
      number,
      { net: number | null; bargain: number | null }
    >();
    for (const i of po.items) {
      const d = draft.items[i.id];
      const price = d?.unitPrice ?? null;
      const qty = d?.quantity ?? i.quantity;
      if (price == null) missing++;
      goods += (price ?? 0) * qty;
      const net = netPrice(price, rate, draft.taxIncluded, tax);
      const b = bargainOf(
        i.targetPrice,
        price,
        qty,
        rate,
        draft.taxIncluded,
        tax,
      );
      if (b != null && i.targetPrice != null) {
        bargain += b;
        target += i.targetPrice * qty;
      }
      lines.set(i.id, { net: net == null ? null : round2(net), bargain: b });
    }
    const fees = draft.fees.reduce((s, f) => s + (f.amount ?? 0), 0);
    return {
      rate,
      goods: round2(goods),
      fees: round2(fees),
      total: round2(goods + fees),
      bargain: round2(bargain),
      target: round2(target),
      bargainRate: rateOf(round2(bargain), round2(target)),
      missing,
      lines,
      rateUnknown:
        draft.currencyCode !== po.currencyCode && draft.currencyCode !== 'CNY',
    };
  }, [po, draft]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!po || !draft || !preview) return <Skeleton active />;

  const setItem = (itemId: number, patch: Partial<Draft['items'][number]>) =>
    setDraft((d) =>
      d
        ? {
            ...d,
            items: { ...d.items, [itemId]: { ...d.items[itemId], ...patch } },
          }
        : d,
    );

  const save = async (quiet = false) => {
    setSaving(true);
    try {
      const p = await poApi.save(poId, toBody(po, draft));
      apply(p);
      if (!quiet) message.success('已保存');
      return p;
    } catch (e) {
      message.error(readBizError(e).message);
      return undefined;
    } finally {
      setSaving(false);
    }
  };

  const termsBad =
    draft.paymentTerms.length > 1 && termsTotal(draft.paymentTerms) !== 100;

  const removeItems = (ids: number[]) =>
    modal.confirm({
      title: `移除 ${ids.length} 个型号？`,
      content: '移除后这些数量回到需求池，可以再生成到别的采购单。',
      okText: '移除',
      onOk: async () => {
        try {
          const p = await poApi.removeItems(poId, ids);
          if (!p) {
            message.success('草稿已没有型号，已删除');
            history.push(PATHS.orders);
            return;
          }
          apply(p);
          message.success('已移除');
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });

  const deleteDraft = () =>
    modal.confirm({
      title: '删除这张草稿？',
      content: '各型号数量回到需求池。',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await poApi.remove(poId);
          message.success('草稿已删除');
          history.push(PATHS.orders);
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });

  const columns: TableColumnsType<PoItem> = [
    {
      title: '型号 · 品牌',
      dataIndex: 'model',
      width: 200,
      render: (v: string, r) => (
        <div>
          <b>{v}</b>
          {r.brand && sub(palette.mute, r.brand)}
          {r.orderCancelled && <Pill tone="red">来源订单已取消</Pill>}
        </div>
      ),
    },
    {
      title: '来源订单 · 客户',
      dataIndex: 'soNo',
      width: 220,
      render: (v: string, r) => (
        <div>
          <a onClick={() => history.push(PATHS.salesOrder(r.soId))}>{v}</a>
          {sub(
            palette.mute,
            [r.customerName, r.customerCountry].filter(Boolean).join(' · '),
          )}
        </div>
      ),
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      width: 100,
      render: (v: number, r) =>
        editable ? (
          <div>
            <InputNumber
              min={1}
              max={r.maxQuantity}
              precision={0}
              value={draft.items[r.id]?.quantity}
              onChange={(n) => setItem(r.id, { quantity: n ?? 1 })}
              style={{ width: 80 }}
              aria-label={`${r.model} 数量`}
            />
            {sub(palette.mute, `最多 ${r.maxQuantity}`)}
          </div>
        ) : (
          v
        ),
    },
    {
      title: '目标价',
      dataIndex: 'targetPrice',
      width: 110,
      align: 'right',
      render: (v?: number | null) =>
        v == null ? (
          <span style={{ fontSize: 12, color: palette.mute }}>没有目标价</span>
        ) : (
          formatAmount(v, 'CNY')
        ),
    },
    {
      title: `单价（${draft.currencyCode} · ${draft.taxIncluded ? `含税 ${draft.taxRate}%` : '不含税'}）`,
      dataIndex: 'unitPrice',
      width: 160,
      render: (v: number | null | undefined, r) =>
        editable ? (
          <InputNumber
            min={0}
            precision={2}
            value={draft.items[r.id]?.unitPrice}
            placeholder="填单价"
            status={
              draft.items[r.id]?.unitPrice == null ? 'warning' : undefined
            }
            onChange={(n) => setItem(r.id, { unitPrice: n })}
            style={{ width: 130 }}
            aria-label={`${r.model} 单价`}
          />
        ) : v == null ? (
          <span style={{ color: palette.orange }}>未填</span>
        ) : (
          <b>{formatAmount(v, po.currencyCode)}</b>
        ),
    },
    {
      title: '不含税单价',
      key: 'net',
      width: 120,
      align: 'right',
      render: (_, r) => {
        const n = preview.lines.get(r.id)?.net;
        return n == null ? '—' : formatAmount(n, 'CNY');
      },
    },
    {
      title: '砍价',
      key: 'bargain',
      width: 130,
      render: (_, r) => {
        const b = preview.lines.get(r.id)?.bargain;
        const qty = draft.items[r.id]?.quantity ?? r.quantity;
        if (r.targetPrice != null && draft.items[r.id]?.unitPrice == null) {
          return (
            <span style={{ fontSize: 12, color: palette.orange }}>
              待填单价
            </span>
          );
        }
        return (
          <BargainText
            amount={b}
            rate={r.targetPrice == null ? null : rateOf(b, r.targetPrice * qty)}
          />
        );
      },
    },
    ...(isDraft && editable
      ? [
          {
            title: '',
            key: 'actions',
            width: 150,
            render: (_: unknown, r: PoItem) => (
              <Space size={12}>
                <a
                  onClick={() => {
                    setPicked([r.id]);
                    setMoveOpen(true);
                  }}
                >
                  改到其他供应商
                </a>
                <Button
                  type="text"
                  size="small"
                  icon={<DeleteOutlined />}
                  aria-label={`移除 ${r.model}`}
                  onClick={() => removeItems([r.id])}
                />
              </Space>
            ),
          },
        ]
      : []),
  ];

  const title = po.poNo ?? `草稿采购单 · ${po.supplierName ?? ''}`;
  const contractDiff =
    draft.contractAmount == null
      ? null
      : round2(draft.contractAmount - preview.total);

  return (
    <div>
      <PurchasePageTitle
        crumbs={[
          <a key="list" onClick={() => history.push(PATHS.orders)}>
            采购单
          </a>,
          po.poNo ?? '草稿',
        ]}
        title={
          <span
            style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}
          >
            {title}
            <PoStatusPill status={po.status} />
          </span>
        }
        description={
          <>
            {po.supplierName} · {po.purchaserName}
            {po.orderDate
              ? ` · ${po.orderDate} 下单`
              : ` · ${dayjs(po.createTime).format('YYYY-MM-DD')} 创建 · 确认下单时分配编号`}
            {po.status === PO_STATUS.CANCELLED &&
              ` · ${po.cancelledByName ?? ''} 取消：${po.cancelReason ?? ''}`}
          </>
        }
        actions={
          <Space>
            {isDraft && canCancel && (
              <Button danger icon={<DeleteOutlined />} onClick={deleteDraft}>
                删除草稿
              </Button>
            )}
            {po.status === PO_STATUS.ORDERED && canCancel && !editing && (
              <Button
                icon={<StopOutlined />}
                onClick={() => setCancelOpen(true)}
              >
                取消采购单
              </Button>
            )}
            {po.status === PO_STATUS.ORDERED && canEdit && !editing && (
              <Button onClick={() => setEditing(true)}>修改</Button>
            )}
            {po.status === PO_STATUS.ORDERED && editing && (
              <Button
                onClick={() => {
                  setDraft(toDraft(po));
                  setEditing(false);
                }}
              >
                放弃修改
              </Button>
            )}
            {editable && (
              <Button
                loading={saving}
                disabled={termsBad}
                onClick={async () => {
                  const p = await save();
                  if (p && p.status === PO_STATUS.ORDERED) setEditing(false);
                }}
              >
                {isDraft ? '保存草稿' : '保存修改'}
              </Button>
            )}
            {isDraft && canEdit && (
              <Button
                type="primary"
                icon={<CheckOutlined />}
                disabled={termsBad}
                onClick={() => setConfirmOpen(true)}
              >
                确认下单
              </Button>
            )}
          </Space>
        }
      />

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 16,
          marginBottom: 16,
        }}
      >
        <StatCard
          icon={<WalletOutlined />}
          color={palette.link}
          label="合计"
          value={formatAmount(preview.total, draft.currencyCode)}
          hint={
            preview.missing > 0
              ? `${preview.missing} 行未填单价`
              : `货款 ${formatAmount(preview.goods, draft.currencyCode)} + 费用 ${formatAmount(preview.fees, draft.currencyCode)}`
          }
          hintColor={preview.missing > 0 ? palette.orange : undefined}
        />
        <StatCard
          icon={<FallOutlined />}
          color={preview.bargain < 0 ? palette.red : palette.green}
          label="砍价合计"
          value={formatAmount(preview.bargain, 'CNY')}
          hint={
            preview.bargainRate == null
              ? '填好单价后计算'
              : `整体砍价率 ${preview.bargainRate.toFixed(2)}% · 目标金额 ${formatAmount(preview.target, 'CNY')}`
          }
          hintColor={preview.bargain < 0 ? palette.red : palette.green}
        />
        <StatCard
          icon={<CalendarOutlined />}
          color={palette.violet}
          label="付款条件"
          value={
            draft.paymentTerms.length === 0
              ? '未填'
              : draft.paymentTerms.length === 1
                ? '一次付清'
                : draft.paymentTerms
                    .map((t) => `${Number(t.percent)}%`)
                    .join(' + ')
          }
          hint={po.paymentTermsText || '确认下单前须填写'}
        />
        <StatCard
          icon={<FileProtectOutlined />}
          color={palette.orange}
          label="供应商合同"
          value={
            po.attachments.length ? `${po.attachments.length} 个文件` : '未上传'
          }
          hint={
            contractDiff
              ? `合同金额与采购单合计相差 ${formatAmount(Math.abs(contractDiff), draft.currencyCode)}`
              : po.contractNo
                ? `合同编号 ${po.contractNo}`
                : '选填，货值低的可以不传'
          }
          hintColor={contractDiff ? palette.orange : undefined}
        />
      </div>

      {editable && (
        <Card style={{ marginBottom: 16, padding: 20 }}>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns:
                'minmax(260px, 2fr) 140px minmax(260px, 1.4fr)',
              gap: 16,
              alignItems: 'end',
            }}
          >
            <div>
              <div
                style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}
              >
                供应商
              </div>
              <SupplierPicker
                style={{ width: '100%' }}
                value={draft.supplierId}
                label={draft.supplierName}
                onChange={(v, name) =>
                  setDraft((d) =>
                    d ? { ...d, supplierId: v, supplierName: name } : d,
                  )
                }
              />
              {!isDraft &&
                sub(palette.mute, '已下单的不能改供应商，可以取消后重新下单')}
            </div>
            <div>
              <div
                style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}
              >
                币种
              </div>
              <Select
                style={{ width: '100%' }}
                value={draft.currencyCode}
                options={['CNY', ...CURRENCIES.filter((c) => c !== 'CNY')].map(
                  (c) => ({
                    value: c,
                    label: c,
                  }),
                )}
                onChange={(v) =>
                  setDraft((d) => (d ? { ...d, currencyCode: v } : d))
                }
                aria-label="币种"
              />
            </div>
            <div>
              <div
                style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}
              >
                含税
              </div>
              <Space>
                <Button
                  type={draft.taxIncluded ? 'primary' : 'default'}
                  ghost={draft.taxIncluded}
                  onClick={() =>
                    setDraft((d) => (d ? { ...d, taxIncluded: true } : d))
                  }
                >
                  含税
                </Button>
                <Button
                  type={!draft.taxIncluded ? 'primary' : 'default'}
                  ghost={!draft.taxIncluded}
                  onClick={() =>
                    setDraft((d) => (d ? { ...d, taxIncluded: false } : d))
                  }
                >
                  不含税
                </Button>
                {draft.taxIncluded && (
                  <InputNumber
                    min={0}
                    max={100}
                    precision={2}
                    value={draft.taxRate}
                    suffix="%"
                    style={{ width: 100 }}
                    onChange={(v) =>
                      setDraft((d) => (d ? { ...d, taxRate: v ?? 0 } : d))
                    }
                    aria-label="税率"
                  />
                )}
              </Space>
            </div>
          </div>
          {preview.rateUnknown && (
            <Alert
              style={{ marginTop: 12 }}
              type="info"
              showIcon
              title={`${draft.currencyCode} 保存后按系统汇率换算人民币，砍价随之重算`}
            />
          )}
        </Card>
      )}

      <Card style={{ marginBottom: 16, padding: 20 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            marginBottom: 12,
            flexWrap: 'wrap',
          }}
        >
          <b style={{ color: palette.ink, fontSize: 15 }}>
            型号（{po.items.length}）
          </b>
          <span style={{ fontSize: 12, color: palette.mute }}>
            不含税单价 = 单价 × 汇率 ÷ (1 + 税率)；砍价 = (目标价 − 不含税单价)
            × 数量；目标价为转订单时锁定的采购成本价（不含税）
          </span>
          <span style={{ flex: 1 }} />
          {isDraft && editable && picked.length > 0 && (
            <Space>
              <span style={{ color: palette.sub }}>
                已选 {picked.length} 行
              </span>
              <Button size="small" onClick={() => setMoveOpen(true)}>
                改到其他供应商
              </Button>
              <Button size="small" onClick={() => removeItems(picked)}>
                移除
              </Button>
            </Space>
          )}
        </div>
        {isDraft && (
          <Alert
            style={{ marginBottom: 12 }}
            type="info"
            showIcon
            title="单价要谈好后填写，不会预填目标价；某个型号要换供应商时点「改到其他供应商」。"
          />
        )}
        <Table<PoItem>
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={po.items}
          pagination={false}
          scroll={{ x: 1100 }}
          rowSelection={
            isDraft && editable
              ? {
                  selectedRowKeys: picked,
                  onChange: (keys) => setPicked(keys as number[]),
                }
              : undefined
          }
        />
        <div style={{ marginTop: 14, display: 'grid', gap: 8 }}>
          <b style={{ color: palette.ink }}>其他费用</b>
          {draft.fees.length === 0 && !editable && (
            <span style={{ color: palette.mute }}>无</span>
          )}
          {draft.fees.map((f) =>
            editable ? (
              <Space key={f.key}>
                <Input
                  value={f.feeName}
                  placeholder="如运费、包装费"
                  maxLength={64}
                  style={{ width: 220 }}
                  onChange={(e) =>
                    setDraft((d) =>
                      d
                        ? {
                            ...d,
                            fees: d.fees.map((x) =>
                              x.key === f.key
                                ? { ...x, feeName: e.target.value }
                                : x,
                            ),
                          }
                        : d,
                    )
                  }
                  aria-label="费用名称"
                />
                <InputNumber
                  min={0}
                  precision={2}
                  value={f.amount}
                  prefix={draft.currencyCode}
                  style={{ width: 160 }}
                  onChange={(v) =>
                    setDraft((d) =>
                      d
                        ? {
                            ...d,
                            fees: d.fees.map((x) =>
                              x.key === f.key ? { ...x, amount: v } : x,
                            ),
                          }
                        : d,
                    )
                  }
                  aria-label="费用金额"
                />
                <Button
                  type="text"
                  icon={<DeleteOutlined />}
                  aria-label="删除费用"
                  onClick={() =>
                    setDraft((d) =>
                      d
                        ? { ...d, fees: d.fees.filter((x) => x.key !== f.key) }
                        : d,
                    )
                  }
                />
              </Space>
            ) : (
              <span key={f.key} style={{ color: palette.sub }}>
                {f.feeName} {formatAmount(f.amount ?? 0, po.currencyCode)}
              </span>
            ),
          )}
          {editable && (
            <Button
              size="small"
              type="dashed"
              icon={<PlusOutlined />}
              style={{ width: 120 }}
              onClick={() =>
                setDraft((d) =>
                  d
                    ? {
                        ...d,
                        fees: [
                          ...d.fees,
                          { key: ++feeKey, feeName: '', amount: null },
                        ],
                      }
                    : d,
                )
              }
            >
              添加费用
            </Button>
          )}
          <span style={{ fontSize: 12, color: palette.mute }}>
            其他费用计入合计，不参与砍价
          </span>
        </div>
      </Card>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(380px, 1fr))',
          gap: 16,
        }}
      >
        <Card style={{ padding: 20 }}>
          <b style={{ color: palette.ink, display: 'block', marginBottom: 12 }}>
            <CalendarOutlined /> 付款条件
          </b>
          <PaymentTermsEditor
            value={draft.paymentTerms}
            disabled={!editable}
            onChange={(terms) =>
              setDraft((d) => (d ? { ...d, paymentTerms: terms } : d))
            }
          />
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
            默认取供应商的默认付款条件；本期只记录，应付与付款提醒在后续上线。
          </div>
        </Card>
        <ContractCard
          po={po}
          draft={draft}
          editable={editable || (canEdit && po.status !== PO_STATUS.CANCELLED)}
          fieldsEditable={editable}
          contractDiff={contractDiff}
          onDraft={(patch) => setDraft((d) => (d ? { ...d, ...patch } : d))}
          onChanged={(p) => {
            setPo(p);
          }}
        />
        <Card style={{ padding: 20 }}>
          <b style={{ color: palette.ink, display: 'block', marginBottom: 12 }}>
            <HistoryOutlined /> 操作日志
          </b>
          <div
            style={{
              display: 'grid',
              gap: 8,
              maxHeight: 320,
              overflow: 'auto',
            }}
          >
            {po.logs.map((l, i) => (
              <div
                // biome-ignore lint/suspicious/noArrayIndexKey: 日志只追加，按位置展示
                key={i}
                style={{
                  display: 'grid',
                  gridTemplateColumns: '120px 56px 1fr',
                  gap: 8,
                  fontSize: 12,
                }}
              >
                <span style={{ color: palette.mute }}>
                  {dayjs(l.createTime).format('MM-DD HH:mm')}
                </span>
                <span style={{ color: palette.sub }}>{l.operatorName}</span>
                <span style={{ color: palette.sub }}>
                  <b>{l.action}</b> {l.content}
                </span>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <ConfirmModal
        open={confirmOpen}
        po={po}
        total={formatAmount(preview.total, draft.currencyCode)}
        missing={preview.missing}
        onClose={() => setConfirmOpen(false)}
        onConfirm={async (date) => {
          const saved = await save(true);
          if (!saved) return;
          try {
            const p = await poApi.confirm(poId, date.format('YYYY-MM-DD'));
            apply(p);
            setEditing(false);
            setConfirmOpen(false);
            message.success(`已下单，编号 ${p.poNo}`);
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
      <CancelModal
        open={cancelOpen}
        onClose={() => setCancelOpen(false)}
        onConfirm={async (reason) => {
          try {
            const p = await poApi.cancel(poId, reason);
            apply(p);
            setCancelOpen(false);
            message.success('采购单已取消，各型号数量回到需求池');
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
      <MoveModal
        open={moveOpen}
        count={picked.length}
        current={po.supplierId}
        onClose={() => setMoveOpen(false)}
        onConfirm={async (supplierId) => {
          try {
            const target = await poApi.move(poId, picked, supplierId);
            setMoveOpen(false);
            message.success('已改到其他供应商的草稿');
            if (po.items.length === picked.length) {
              history.push(PATHS.order(target));
            } else {
              load();
            }
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

// ---------------------------------------------------------------- 合同

const ContractCard: React.FC<{
  po: PurchaseOrder;
  draft: Draft;
  editable: boolean;
  fieldsEditable: boolean;
  contractDiff: number | null;
  onDraft: (patch: Partial<Draft>) => void;
  onChanged: (po: PurchaseOrder) => void;
}> = ({
  po,
  draft,
  editable,
  fieldsEditable,
  contractDiff,
  onDraft,
  onChanged,
}) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [uploading, setUploading] = useState(false);
  const open = (attId: number, name: string, inline: boolean) =>
    poApi
      .openAttachment(po.id, attId, name, inline)
      .catch((e) => message.error(readBizError(e).message));
  return (
    <Card style={{ padding: 20 }}>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12 }}>
        <b style={{ color: palette.ink }}>
          <FileProtectOutlined /> 供应商合同
        </b>
        <span style={{ flex: 1 }} />
        <span style={{ fontSize: 12, color: palette.mute }}>选填</span>
      </div>
      <div style={{ display: 'grid', gap: 8 }}>
        {po.attachments.map((a) => (
          <div
            key={a.id}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 12px',
              borderRadius: 10,
              background: palette.inset,
            }}
          >
            <FilePdfOutlined style={{ color: palette.link }} />
            <span
              style={{
                color: palette.ink,
                minWidth: 0,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {a.fileName}
            </span>
            <span
              style={{
                fontSize: 12,
                color: palette.mute,
                whiteSpace: 'nowrap',
              }}
            >
              {(a.fileSize / 1024 / 1024).toFixed(1)} MB · {a.uploadedByName}
            </span>
            <span style={{ flex: 1 }} />
            <a onClick={() => open(a.id, a.fileName, true)}>预览</a>
            <a onClick={() => open(a.id, a.fileName, false)}>下载</a>
            {editable && (
              <Button
                type="text"
                size="small"
                icon={<DeleteOutlined />}
                aria-label={`删除 ${a.fileName}`}
                onClick={() =>
                  poApi
                    .removeAttachment(po.id, a.id)
                    .then(onChanged)
                    .catch((e) => message.error(readBizError(e).message))
                }
              />
            )}
          </div>
        ))}
        {editable && po.attachments.length < 10 && (
          <Upload
            accept=".pdf,.jpg,.jpeg,.png"
            showUploadList={false}
            beforeUpload={(file) => {
              if (file.size > 10 * 1024 * 1024) {
                message.error('单个文件不能超过 10MB');
                return Upload.LIST_IGNORE;
              }
              setUploading(true);
              poApi
                .upload(po.id, file)
                .then((p) => {
                  onChanged(p);
                  message.success('合同已上传');
                })
                .catch((e) => message.error(readBizError(e).message))
                .finally(() => setUploading(false));
              return false;
            }}
          >
            <Button icon={<UploadOutlined />} loading={uploading}>
              上传合同（PDF、JPG、PNG，单个 10MB 内）
            </Button>
          </Upload>
        )}
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}
        >
          <div>
            <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
              合同编号
            </div>
            {fieldsEditable ? (
              <Input
                value={draft.contractNo}
                maxLength={64}
                onChange={(e) => onDraft({ contractNo: e.target.value })}
                aria-label="合同编号"
              />
            ) : (
              <span style={{ color: palette.ink }}>{po.contractNo || '—'}</span>
            )}
          </div>
          <div>
            <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
              合同金额
            </div>
            {fieldsEditable ? (
              <InputNumber
                min={0}
                precision={2}
                value={draft.contractAmount}
                prefix={draft.currencyCode}
                style={{ width: '100%' }}
                onChange={(v) => onDraft({ contractAmount: v })}
                aria-label="合同金额"
              />
            ) : (
              <span style={{ color: palette.ink }}>
                {po.contractAmount == null
                  ? '—'
                  : formatAmount(po.contractAmount, po.currencyCode)}
              </span>
            )}
          </div>
        </div>
        {contractDiff ? (
          <Alert
            type="warning"
            showIcon
            title={`合同金额与采购单合计相差 ${formatAmount(Math.abs(contractDiff), draft.currencyCode)}`}
          />
        ) : null}
        {!editable &&
          po.status !== PO_STATUS.CANCELLED &&
          po.attachments.length === 0 && (
            <span style={{ fontSize: 12, color: palette.mute }}>
              没有上传合同
            </span>
          )}
      </div>
    </Card>
  );
};

// ---------------------------------------------------------------- 弹窗

const ConfirmModal: React.FC<{
  open: boolean;
  po: PurchaseOrder;
  total: string;
  missing: number;
  onClose: () => void;
  onConfirm: (date: Dayjs) => Promise<void>;
}> = ({ open, po, total, missing, onClose, onConfirm }) => {
  const { palette } = useAppTheme();
  const [date, setDate] = useState<Dayjs | null>(dayjs());
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) setDate(dayjs());
  }, [open]);
  return (
    <Modal
      open={open}
      title={`确认向「${po.supplierName}」下单`}
      width={520}
      onCancel={onClose}
      okText="确认下单"
      okButtonProps={{ disabled: !date || missing > 0, loading: busy }}
      onOk={async () => {
        if (!date) return;
        setBusy(true);
        try {
          await onConfirm(date);
        } finally {
          setBusy(false);
        }
      }}
    >
      <div style={{ display: 'grid', gap: 12 }}>
        {missing > 0 && (
          <Alert type="warning" showIcon title={`还有 ${missing} 行没填单价`} />
        )}
        <div style={{ color: palette.sub }}>
          {po.items.length} 个型号，合计{' '}
          <b style={{ color: palette.ink }}>{total}</b>
        </div>
        <div>
          <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
            下单日期
          </div>
          <DatePicker
            value={date}
            onChange={setDate}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            style={{ width: '100%' }}
            aria-label="下单日期"
          />
        </div>
        <div style={{ fontSize: 12, color: palette.mute }}>
          确认后分配采购单编号；全部数量都在已下单采购单上的订单型号会自动变为「已下单」。下单后仍可改价格、数量与合同，修改会记录在操作日志里。
        </div>
      </div>
    </Modal>
  );
};

const CancelModal: React.FC<{
  open: boolean;
  onClose: () => void;
  onConfirm: (reason: string) => Promise<void>;
}> = ({ open, onClose, onConfirm }) => {
  const { palette } = useAppTheme();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) setReason('');
  }, [open]);
  return (
    <Modal
      open={open}
      title="取消采购单"
      width={480}
      onCancel={onClose}
      okText="取消采购单"
      okButtonProps={{ danger: true, disabled: !reason.trim(), loading: busy }}
      cancelText="不取消"
      onOk={async () => {
        setBusy(true);
        try {
          await onConfirm(reason.trim());
        } finally {
          setBusy(false);
        }
      }}
    >
      <div style={{ display: 'grid', gap: 10 }}>
        <Input.TextArea
          value={reason}
          maxLength={200}
          placeholder="如供应商缺货、价格谈不拢"
          onChange={(e) => setReason(e.target.value)}
          aria-label="取消原因"
        />
        <div style={{ fontSize: 12, color: palette.mute }}>
          取消后各型号数量回到需求池，订单型号的进度随之回到「待采购」。
        </div>
      </div>
    </Modal>
  );
};

const MoveModal: React.FC<{
  open: boolean;
  count: number;
  current: number;
  onClose: () => void;
  onConfirm: (supplierId: number) => Promise<void>;
}> = ({ open, count, current, onClose, onConfirm }) => {
  const { palette } = useAppTheme();
  const [supplierId, setSupplierId] = useState<number>();
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) {
      setSupplierId(undefined);
      setName('');
    }
  }, [open]);
  return (
    <Modal
      open={open}
      title="改到其他供应商"
      width={520}
      onCancel={onClose}
      okText="改到这家"
      okButtonProps={{
        disabled: !supplierId || supplierId === current,
        loading: busy,
      }}
      onOk={async () => {
        if (!supplierId) return;
        setBusy(true);
        try {
          await onConfirm(supplierId);
        } finally {
          setBusy(false);
        }
      }}
    >
      <div style={{ display: 'grid', gap: 10 }}>
        <div style={{ color: palette.sub }}>已选 {count} 个型号</div>
        <SupplierPicker
          style={{ width: '100%' }}
          placeholder="选择新供应商"
          value={supplierId}
          label={name}
          onChange={(v, n) => {
            setSupplierId(v);
            setName(n);
          }}
        />
        <div style={{ fontSize: 12, color: palette.mute }}>
          这些型号会移出当前草稿，追加到你对新供应商的草稿采购单（没有时新建），已填的单价保留；当前草稿没有型号时自动删除。
        </div>
      </div>
    </Modal>
  );
};

export default PurchaseOrderDetail;
