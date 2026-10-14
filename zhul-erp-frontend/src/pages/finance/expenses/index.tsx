import {
  DeleteOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  Checkbox,
  DatePicker,
  Drawer,
  Input,
  InputNumber,
  Segmented,
  Select,
  Space,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import {
  auditColumns,
  SHOP_CHANNELS,
  SupplierPicker,
} from '@/pages/purchase/components';
import { CURRENCIES } from '@/pages/quotation/components';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { readBizError } from '@/pages/sales/service';
import { AttachmentWall, ReasonModal } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { useDictTexts } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import {
  ACCEPT_PROOF,
  PayStatusPill,
  ProofLinks,
} from '../payables/components';
import { METHOD, PAYEE } from '../payables/service';
import {
  type Expense,
  type ExpenseQuery,
  expenseApi,
  type SaveExpenseBody,
  type SoItemOption,
} from '../reimbursements/service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const round2 = (n: number) => Math.round(n * 100) / 100;

interface AllocRow {
  soItemId: number;
  soNo: string;
  model: string;
  amount: number | null;
}

/** 新建与修改费用单 */
const ExpenseDrawer: React.FC<{
  open: boolean;
  editing?: Expense;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, editing, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const users = useUserOptions();
  const [name, setName] = useState('');
  const [category, setCategory] = useState<string>();
  const categories = useDictTexts('expense_category');
  const [months, setMonths] = useState<number | null>(1);
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [payeeType, setPayeeType] = useState<number>(PAYEE.OTHER);
  const [supplierId, setSupplierId] = useState<number>();
  const [supplierName, setSupplierName] = useState<string>();
  const [shopChannel, setShopChannel] = useState<number>(1);
  const [shopName, setShopName] = useState('');
  const [payeeName, setPayeeName] = useState('');
  const [currency, setCurrency] = useState('CNY');
  const [amount, setAmount] = useState<number | null>(null);
  const [method, setMethod] = useState<number>(METHOD.COMPANY);
  const [advanceUserId, setAdvanceUserId] = useState<number>();
  const [remark, setRemark] = useState('');
  const [files, setFiles] = useState<Attachment[]>([]);
  const [allocs, setAllocs] = useState<AllocRow[]>([]);
  const [options, setOptions] = useState<SoItemOption[]>([]);
  const [saving, setSaving] = useState(false);
  const locked = !!editing?.locked;

  useEffect(() => {
    if (!open) return;
    const e = editing;
    setName(e?.name ?? '');
    setCategory(e?.category);
    setMonths(e?.amortizeMonths ?? 1);
    setDate(e ? dayjs(e.occurDate) : dayjs());
    setPayeeType(e?.payeeType ?? PAYEE.OTHER);
    setSupplierId(e?.supplierId);
    setSupplierName(e?.payeeName);
    setShopChannel(e?.shopPlatform ? Number(e.shopPlatform) : 1);
    setShopName(e?.shopName ?? '');
    setPayeeName(e?.payeeType === PAYEE.OTHER ? e.payeeName : '');
    setCurrency(e?.currencyCode ?? 'CNY');
    setAmount(e?.amount ?? null);
    setMethod(e?.method ?? METHOD.COMPANY);
    setAdvanceUserId(e?.advanceUserId);
    setRemark(e?.remark ?? '');
    setFiles((e?.attachments ?? []) as unknown as Attachment[]);
    setAllocs(
      (e?.allocations ?? []).map((a) => ({
        soItemId: a.soItemId,
        soNo: a.soNo ?? '',
        model: a.model ?? '',
        amount: a.amount,
      })),
    );
    expenseApi
      .soItems()
      .then(setOptions)
      .catch(() => setOptions([]));
  }, [open, editing]);

  const allocTotal = round2(allocs.reduce((s, a) => s + (a.amount ?? 0), 0));
  const allocOk = allocs.length === 0 || allocTotal === round2(amount ?? 0);

  const save = async () => {
    if (!name.trim() || !amount) {
      message.error('请填写名称和金额');
      return;
    }
    if (!allocOk) {
      message.error('分摊合计须等于费用金额');
      return;
    }
    const body: SaveExpenseBody = {
      name: name.trim(),
      category: category ?? (allocs.length ? 'ORDER' : 'OTHER'),
      amortizeMonths: months ?? 1,
      occurDate: date.format('YYYY-MM-DD'),
      payeeType,
      supplierId:
        payeeType === PAYEE.SUPPLIER || payeeType === PAYEE.FORWARDER
          ? supplierId
          : undefined,
      shopChannel: payeeType === PAYEE.SHOP ? shopChannel : undefined,
      shopName: payeeType === PAYEE.SHOP ? shopName : undefined,
      payeeName: payeeType === PAYEE.OTHER ? payeeName : undefined,
      currencyCode: currency,
      amount,
      method,
      advanceUserId: method === METHOD.ADVANCE ? advanceUserId : undefined,
      remark: remark.trim() || undefined,
      attachmentIds: files.map((f) => f.id),
      allocations: allocs
        .filter((a) => a.amount)
        .map((a) => ({ soItemId: a.soItemId, amount: a.amount as number })),
    };
    setSaving(true);
    try {
      if (editing) await expenseApi.update(editing.id, body);
      else await expenseApi.create(body);
      message.success('费用单已保存');
      onDone();
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const label = (text: string, required = true) => (
    <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
      {required && <span style={{ color: palette.red }}>* </span>}
      {text}
    </div>
  );

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size="min(780px, 96vw)"
      destroyOnHidden
      title={editing ? `修改费用单 ${editing.expenseNo}` : '新建费用单'}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={saving} onClick={save}>
            保存
          </Button>
        </div>
      }
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        {locked && (
          <Alert
            type="info"
            showIcon
            title={
              editing?.method === METHOD.COMPANY
                ? '已经付款：只能改名称、说明与分摊'
                : '已在报销单里：只能改名称、说明与分摊'
            }
          />
        )}
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: 220 }}>
            {label('名称')}
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={64}
              placeholder="如 补发运费"
            />
          </div>
          <div style={{ width: 160 }}>
            {label('费用类别')}
            <Select
              style={{ width: '100%' }}
              value={category ?? (allocs.length ? 'ORDER' : 'OTHER')}
              onChange={setCategory}
              options={categories.map((c) => ({
                value: c.code,
                label: c.name,
              }))}
            />
          </div>
          <div style={{ width: 120 }}>
            {label('分摊月数', false)}
            <InputNumber<number>
              min={1}
              max={36}
              precision={0}
              value={months}
              onChange={setMonths}
              suffix="个月"
              style={{ width: '100%' }}
              aria-label="分摊月数"
            />
          </div>
          <div style={{ width: 180 }}>
            {label('发生日期')}
            <DatePicker
              value={date}
              disabled={locked}
              onChange={(d) => d && setDate(d)}
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
              allowClear={false}
              style={{ width: '100%' }}
            />
          </div>
        </div>
        <div>
          {label('收款方')}
          <Space wrap>
            <Select
              style={{ width: 120 }}
              value={payeeType}
              disabled={locked}
              onChange={setPayeeType}
              options={[
                { value: PAYEE.OTHER, label: '其他' },
                { value: PAYEE.SUPPLIER, label: '供应商' },
                { value: PAYEE.FORWARDER, label: '货代' },
                { value: PAYEE.SHOP, label: '线上店铺' },
              ]}
            />
            {(payeeType === PAYEE.SUPPLIER ||
              payeeType === PAYEE.FORWARDER) && (
              <SupplierPicker
                value={supplierId}
                label={supplierName}
                style={{ width: 280 }}
                onChange={(id, n) => {
                  setSupplierId(id);
                  setSupplierName(n);
                }}
              />
            )}
            {payeeType === PAYEE.SHOP && (
              <>
                <Select
                  style={{ width: 100 }}
                  value={shopChannel}
                  disabled={locked}
                  onChange={setShopChannel}
                  options={SHOP_CHANNELS}
                />
                <Input
                  style={{ width: 220 }}
                  value={shopName}
                  disabled={locked}
                  onChange={(e) => setShopName(e.target.value)}
                  placeholder="店铺名称"
                />
              </>
            )}
            {payeeType === PAYEE.OTHER && (
              <Input
                style={{ width: 260 }}
                value={payeeName}
                disabled={locked}
                onChange={(e) => setPayeeName(e.target.value)}
                placeholder="如 顺丰"
                maxLength={128}
              />
            )}
          </Space>
        </div>
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
          <div style={{ width: 120 }}>
            {label('币种')}
            <Select
              style={{ width: '100%' }}
              value={currency}
              disabled={locked}
              onChange={setCurrency}
              options={CURRENCIES.map((c) => ({ value: c, label: c }))}
            />
          </div>
          <div style={{ width: 200 }}>
            {label('金额')}
            <InputNumber<number>
              min={0.01}
              precision={2}
              disabled={locked}
              style={{ width: '100%' }}
              value={amount}
              onChange={setAmount}
            />
          </div>
        </div>
        <div>
          {label('付款方式')}
          <Segmented<number>
            value={method}
            disabled={locked}
            onChange={setMethod}
            options={[
              { label: '公司对公', value: METHOD.COMPANY },
              { label: '员工垫付', value: METHOD.ADVANCE },
            ]}
          />
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
            公司对公：保存后生成应付，在应付单付款；员工垫付：进入垫付人的待报销，凭证必传
          </div>
          {method === METHOD.ADVANCE && (
            <Select
              style={{ width: 200, marginTop: 8 }}
              placeholder="垫付人（默认自己）"
              allowClear
              disabled={locked}
              value={advanceUserId}
              onChange={setAdvanceUserId}
              options={users}
              showSearch={{ optionFilterProp: 'label' }}
            />
          )}
        </div>
        <div>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              marginBottom: 8,
            }}
          >
            <b>分摊到订单型号</b>
            <span style={{ fontSize: 12, color: palette.mute }}>
              可选；不关联的视为公司费用
            </span>
            <span style={{ flex: 1 }} />
            <Select
              showSearch={{
                filterOption: false,
                onSearch: (kw) => {
                  expenseApi
                    .soItems(kw)
                    .then(setOptions)
                    .catch(() => setOptions([]));
                },
              }}
              placeholder={
                <span>
                  <PlusOutlined /> 按订单号搜索并添加型号
                </span>
              }
              style={{ width: 300 }}
              value={null}
              options={options
                .filter((o) => !allocs.some((a) => a.soItemId === o.soItemId))
                .map((o) => ({
                  value: o.soItemId,
                  label: `${o.soNo} · ${o.model}（${o.quantity}）`,
                }))}
              onChange={(id: number) => {
                const o = options.find((x) => x.soItemId === id);
                if (o)
                  setAllocs((l) => [
                    ...l,
                    {
                      soItemId: o.soItemId,
                      soNo: o.soNo,
                      model: o.model,
                      amount: null,
                    },
                  ]);
              }}
            />
          </div>
          <Table<AllocRow>
            rowKey="soItemId"
            size="small"
            pagination={false}
            dataSource={allocs}
            locale={{ emptyText: '没有关联订单型号' }}
            columns={[
              { title: '销售订单', dataIndex: 'soNo' },
              { title: '型号', dataIndex: 'model' },
              {
                title: '分摊金额',
                key: 'amount',
                width: 160,
                render: (_, a) => (
                  <InputNumber<number>
                    aria-label={`分摊 ${a.model}`}
                    min={0.01}
                    precision={2}
                    style={{ width: '100%' }}
                    value={a.amount}
                    onChange={(v) =>
                      setAllocs((l) =>
                        l.map((x) =>
                          x.soItemId === a.soItemId ? { ...x, amount: v } : x,
                        ),
                      )
                    }
                  />
                ),
              },
              {
                title: '',
                key: 'del',
                width: 48,
                render: (_, a) => (
                  <Button
                    type="text"
                    danger
                    aria-label="删除这一行"
                    icon={<DeleteOutlined />}
                    onClick={() =>
                      setAllocs((l) =>
                        l.filter((x) => x.soItemId !== a.soItemId),
                      )
                    }
                  />
                ),
              },
            ]}
          />
          {allocs.length > 0 && (
            <div
              style={{
                marginTop: 8,
                color: allocOk ? palette.green : palette.orange,
              }}
            >
              分摊合计 {formatAmount(allocTotal, currency)}{' '}
              {allocOk
                ? '= 费用金额'
                : `≠ 费用金额 ${formatAmount(amount ?? 0, currency)}`}
            </div>
          )}
        </div>
        <div>
          {label('凭证', method === METHOD.ADVANCE)}
          <AttachmentWall
            ownerType="EXPENSE"
            value={files}
            onChange={setFiles}
            max={10}
            accept={ACCEPT_PROOF}
          />
        </div>
        <div>
          {label('说明', false)}
          <Input.TextArea
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
            maxLength={500}
            autoSize={{ minRows: 2 }}
          />
        </div>
      </Space>
    </Drawer>
  );
};

interface Filters {
  keyword: string;
  soNo: string;
  range: [Dayjs, Dayjs] | null;
  method?: number;
  category?: string;
  linked?: boolean;
  showVoid: boolean;
}

/** 初始筛选：经营统计跳转时带 dateFrom、dateTo、category、linked */
const initial = (): Filters => {
  const p = new URLSearchParams(window.location.search);
  const from = p.get('dateFrom');
  const to = p.get('dateTo');
  const linked = p.get('linked');
  return {
    keyword: '',
    soNo: '',
    range: from && to ? [dayjs(from), dayjs(to)] : null,
    category: p.get('category') ?? undefined,
    linked: linked == null ? undefined : linked === 'true',
    showVoid: false,
  };
};

/** 财务管理 → 费用单：补发运费这类偶发成本，可以分摊到订单型号 */
const ExpensePage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['finance:expense:edit'];
  const categories = useDictTexts('expense_category');
  const [draft, setDraft] = useState<Filters>(initial);
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Expense[]>([]);
  const [total, setTotal] = useState(0);
  const [monthTotal, setMonthTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [drawer, setDrawer] = useState<{ editing?: Expense }>();
  const [voiding, setVoiding] = useState<Expense>();

  const load = useCallback(async () => {
    setLoading(true);
    const q: Omit<ExpenseQuery, 'page' | 'pageSize'> = {
      keyword: filters.keyword.trim() || undefined,
      soNo: filters.soNo.trim() || undefined,
      dateFrom: filters.range?.[0].format('YYYY-MM-DD'),
      dateTo: filters.range?.[1].format('YYYY-MM-DD'),
      method: filters.method,
      category: filters.category,
      linked: filters.linked,
      showVoid: filters.showVoid || undefined,
    };
    try {
      const [res, t] = await Promise.all([
        expenseApi.page({ ...q, page, pageSize }),
        expenseApi.monthTotal(q),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setMonthTotal(t);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [filters, page, pageSize, message]);

  useEffect(() => {
    load();
  }, [load]);

  const apply = (next: Partial<Filters>) => {
    const f = { ...draft, ...next };
    setDraft(f);
    setFilters(f);
    setPage(1);
  };

  const columns: TableColumnsType<Expense> = [
    {
      title: '编号',
      dataIndex: 'expenseNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => (
        <span
          style={{
            textDecoration: r.status === 2 ? 'line-through' : undefined,
          }}
        >
          {v}
        </span>
      ),
    },
    { title: '发生日期', dataIndex: 'occurDate', width: 110 },
    { title: '名称', dataIndex: 'name', width: 160 },
    {
      title: '类别',
      key: 'category',
      width: 120,
      render: (_, r) => (
        <div>
          {r.categoryName ?? r.category}
          {r.amortizeMonths > 1 && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              分摊 {r.amortizeMonths} 个月
            </div>
          )}
        </div>
      ),
    },
    { title: '收款方', dataIndex: 'payeeName', width: 160, ellipsis: true },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number, r) => (
        <span style={num}>{formatAmount(v, r.currencyCode)}</span>
      ),
    },
    {
      title: '人民币',
      dataIndex: 'amountCny',
      width: 120,
      align: 'right',
      render: (v: number) => <span style={num}>{formatAmount(v, 'CNY')}</span>,
    },
    {
      title: '付款',
      key: 'pay',
      width: 170,
      render: (_, r) =>
        r.method === METHOD.COMPANY ? (
          <Space size={6}>
            <Pill tone="accent">公司对公</Pill>
            <PayStatusPill status={r.payStatus} />
          </Space>
        ) : (
          <div>
            <Pill tone="violet">员工垫付</Pill> {r.advanceUserName}
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.reimburseNo
                ? `${r.reimburseNo} · ${r.reimburseStatus === 2 ? '已报销' : '待付款'}`
                : '待报销'}
            </div>
          </div>
        ),
    },
    {
      title: '关联订单',
      key: 'so',
      width: 200,
      render: (_, r) =>
        r.allocations.length ? (
          <div style={{ fontSize: 12 }}>
            {r.allocations.map((a) => (
              <div key={a.soItemId}>
                {a.soNo} · {a.model} {formatAmount(a.amount, r.currencyCode)}
              </div>
            ))}
          </div>
        ) : (
          <span style={{ color: palette.mute }}>公司费用</span>
        ),
    },
    {
      title: '凭证',
      key: 'files',
      width: 90,
      render: (_, r) => <ProofLinks files={r.attachments} />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (v: number, r) =>
        v === 2 ? (
          <div>
            <Pill tone="mute">已作废</Pill>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.voidReason}
            </div>
          </div>
        ) : (
          <Pill tone="green">有效</Pill>
        ),
    },
    ...auditColumns<Expense>(),
    {
      title: '操作',
      key: 'op',
      width: 110,
      fixed: 'right',
      render: (_, r) =>
        canEdit && r.status === 1 ? (
          <Space size={10}>
            <a onClick={() => setDrawer({ editing: r })}>修改</a>
            {!r.locked && <a onClick={() => setVoiding(r)}>作废</a>}
          </Space>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['费用单']}
        title="费用单"
        description="补发运费这类偶发成本，以及房租、工资、平台费等运营成本；公司对公生成应付，员工垫付进入垫付人的待报销。关联订单型号的计入订单成本，不关联的按类别与分摊月数计入经营统计。"
        actions={
          canEdit && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setDrawer({})}
            >
              新建费用单
            </Button>
          )
        }
      />
      <div
        style={{
          display: 'inline-block',
          padding: '16px 18px',
          borderRadius: 14,
          background: palette.card,
          border: `1px solid ${palette.hairline}`,
          marginBottom: 16,
          minWidth: 240,
        }}
      >
        <div style={{ fontSize: 13, color: palette.sub }}>
          {filters.range ? '所选期间费用' : '本月费用'}（人民币）
        </div>
        <div
          style={{
            ...num,
            fontSize: 22,
            fontWeight: 800,
            color: palette.ink,
            marginTop: 4,
          }}
        >
          {formatAmount(monthTotal, 'CNY')}
        </div>
      </div>
      <Card>
        <div
          style={{
            display: 'flex',
            gap: 10,
            flexWrap: 'wrap',
            marginBottom: 14,
            alignItems: 'center',
          }}
        >
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="编号、名称、收款方"
            style={{ width: 200 }}
            value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
            onPressEnter={() => apply({})}
          />
          <Input
            allowClear
            placeholder="关联订单号"
            style={{ width: 180 }}
            value={draft.soNo}
            onChange={(e) => setDraft({ ...draft, soNo: e.target.value })}
            onPressEnter={() => apply({})}
          />
          <DatePicker.RangePicker
            value={draft.range}
            onChange={(v) =>
              apply({ range: v?.[0] && v[1] ? [v[0], v[1]] : null })
            }
          />
          <Select
            allowClear
            placeholder="付款方式"
            style={{ width: 120 }}
            value={draft.method}
            onChange={(v) => apply({ method: v })}
            options={[
              { value: METHOD.COMPANY, label: '公司对公' },
              { value: METHOD.ADVANCE, label: '员工垫付' },
            ]}
          />
          <Select
            allowClear
            placeholder="费用类别"
            style={{ width: 130 }}
            value={draft.category}
            onChange={(v) => apply({ category: v })}
            options={categories.map((c) => ({ value: c.code, label: c.name }))}
          />
          <Select
            allowClear
            placeholder="是否关联订单"
            style={{ width: 150 }}
            value={draft.linked == null ? undefined : String(draft.linked)}
            onChange={(v) =>
              apply({ linked: v == null ? undefined : v === 'true' })
            }
            options={[
              { value: 'false', label: '运营成本（不关联订单）' },
              { value: 'true', label: '关联订单' },
            ]}
          />
          <Checkbox
            checked={draft.showVoid}
            onChange={(e) => apply({ showVoid: e.target.checked })}
          >
            显示已作废
          </Checkbox>
          <Button onClick={() => apply({})}>查询</Button>
        </div>
        <Table<Expense>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 2220 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      </Card>
      <ExpenseDrawer
        open={!!drawer}
        editing={drawer?.editing}
        onClose={() => setDrawer(undefined)}
        onDone={load}
      />
      <ReasonModal
        open={!!voiding}
        title={`作废 ${voiding?.expenseNo ?? ''}`}
        description="公司对公的应付单随之关闭，员工垫付的从待报销里去掉。"
        okText="作废"
        danger
        onCancel={() => setVoiding(undefined)}
        onOk={async (reason) => {
          if (!voiding) return;
          await expenseApi.voidExpense(voiding.id, reason);
          message.success('已作废');
          setVoiding(undefined);
          load();
        }}
      />
    </div>
  );
};

export default ExpensePage;
