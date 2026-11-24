import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { history, Link, useAccess } from '@umijs/max';
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
import { Eye, Search } from 'lucide-react';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ellipsisRender } from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { Pill } from '@/pages/inquiry/shared/components';
import {
  auditColumns,
  SHOP_CHANNELS,
  SupplierPicker,
} from '@/pages/purchase/components';
import { CURRENCIES } from '@/pages/quotation/components';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import {
  type BankOption,
  bankOptionsApi,
  readBizError,
} from '@/pages/sales/service';
import { AttachmentWall, DOC_PATHS } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import {
  PUBLIC_UNIT,
  UnitPill,
  UnitSelect,
  unitFilterOptions,
  unitOrPublic,
  useBusinessUnits,
} from '@/utils/businessUnit';
import { recentRange } from '@/utils/dateRange';
import { useDictTexts } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { liveRequests } from '@/utils/liveRequests';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  ACCEPT_PROOF,
  PayStatusPill,
  ProofLinks,
} from '../payables/components';
import { METHOD, PAYEE } from '../payables/service';
import { afterSalesOptions } from '../refunds/service';
import {
  type Expense,
  type ExpenseQuery,
  expenseApi,
  type SaveExpenseBody,
  type SoItemOption,
} from '../reimbursements/service';

// 金额不折行（audit UI-041）
const num: React.CSSProperties = {
  fontVariantNumeric: 'tabular-nums',
  whiteSpace: 'nowrap',
};
const round2 = (n: number) => Math.round(n * 100) / 100;

interface AllocRow {
  soId?: number;
  soItemId: number;
  soNo: string;
  model: string;
  amount: number | null;
}

/** 新建与修改费用单 */
export const ExpenseDrawer: React.FC<{
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
  // 私对私：我方对私账户、对方收款方式与账号（spec finance/expense「录入费用单」）
  const [privateAccounts, setPrivateAccounts] = useState<BankOption[]>([]);
  const [bankAccountId, setBankAccountId] = useState<number>();
  const [payeeChannel, setPayeeChannel] = useState(1);
  const [payeeAccountName, setPayeeAccountName] = useState('');
  const [payeeAccountNo, setPayeeAccountNo] = useState('');
  const [remark, setRemark] = useState('');
  const [files, setFiles] = useState<Attachment[]>([]);
  const [allocs, setAllocs] = useState<AllocRow[]>([]);
  const [bu] = useBusinessUnits();
  const [unit, setUnit] = useState<number>(PUBLIC_UNIT);
  /** 用户这次打开后自己选过事业部：之后到达的默认事业部不再覆盖 */
  const unitTouched = useRef(false);
  const [options, setOptions] = useState<SoItemOption[]>([]);
  const [saving, setSaving] = useState(false);
  // 生效后金额、币种、收款方、付款方式与发生日期不能改，录错作废重开（change finance-expense-docs）
  const locked = !!editing;
  const [afterSalesId, setAfterSalesId] = useState<number>();
  const [asOptions, setAsOptions] = useState<
    { value: number; label: string }[]
  >([]);
  const allocSoIds = [
    ...new Set(allocs.map((a) => a.soId).filter((x): x is number => !!x)),
  ].join(',');
  // 关联售后单：分摊订单的售后单（spec finance/expense「费用单关联售后单」）
  // 分摊订单变了：不再属于这些订单的售后单一并清掉，迟到的旧结果丢弃（audit BUS-056）
  useEffect(() => {
    const ids = allocSoIds ? allocSoIds.split(',').map(Number) : [];
    if (ids.length === 0) {
      setAsOptions([]);
      setAfterSalesId(undefined);
      return undefined;
    }
    const { live, cancel } = liveRequests();
    live(Promise.all(ids.map((id) => afterSalesOptions(id).catch(() => null))))
      .then((rows) => {
        const list = rows.flatMap((r) => r ?? []);
        setAsOptions(
          list.map((x) => ({
            value: x.id,
            label: `${x.asNo} · ${x.statusName} · ${x.symptom}`,
          })),
        );
        // 有订单没取到时不清，免得把仍有效的关联误清掉
        if (rows.some((r) => r === null)) return;
        setAfterSalesId((cur) =>
          cur !== undefined && list.some((x) => x.id === cur) ? cur : undefined,
        );
      })
      .catch(() => setAsOptions([]));
    return cancel;
  }, [allocSoIds]);

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
    setBankAccountId(undefined);
    setPayeeChannel(1);
    setPayeeAccountName('');
    setPayeeAccountNo('');
    setRemark(e?.remark ?? '');
    unitTouched.current = false;
    setUnit(e ? (e.businessUnitId ?? PUBLIC_UNIT) : PUBLIC_UNIT);
    setFiles((e?.attachments ?? []) as unknown as Attachment[]);
    setAfterSalesId(e?.afterSalesId ?? undefined);
    setAllocs(
      (e?.allocations ?? []).map((a) => ({
        soId: a.soId,
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
    if (!e)
      bankOptionsApi
        .options()
        .then((list) =>
          setPrivateAccounts(
            list.filter((a) => a.enabled && a.accountType === 2),
          ),
        )
        .catch(() => setPrivateAccounts([]));
  }, [open, editing]);

  // 私对私的付款账户按币种列出，默认选默认账户
  const currencyPrivate = privateAccounts.filter(
    (a) => a.currencyCode === currency,
  );
  useEffect(() => {
    if (method !== METHOD.PRIVATE || editing) return;
    setBankAccountId((cur) =>
      cur && currencyPrivate.some((a) => a.id === cur)
        ? cur
        : (currencyPrivate.find((a) => a.isDefault) ?? currencyPrivate[0])?.id,
    );
  }, [method, editing, currencyPrivate]);

  // 默认事业部单独补：新建时信息晚到也只填事业部，不重置已填写的内容（UI-018）
  useEffect(() => {
    if (!open || editing || unitTouched.current) return;
    setUnit(bu.myUnitId ?? PUBLIC_UNIT);
  }, [open, editing, bu.myUnitId]);

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
    const isPrivate = !editing && method === METHOD.PRIVATE;
    if (isPrivate) {
      if (!bankAccountId) {
        message.error('请选择付款账户');
        return;
      }
      if (!payeeAccountName.trim() || !payeeAccountNo.trim()) {
        message.error('请填写对方户名和账号');
        return;
      }
      if (files.length === 0) {
        message.error('私对私请上传付款凭证');
        return;
      }
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
      ...(isPrivate
        ? {
            bankAccountId,
            payeeChannel,
            payeeAccountName: payeeAccountName.trim(),
            payeeAccountNo: payeeAccountNo.trim(),
          }
        : {}),
      remark: remark.trim() || undefined,
      attachmentIds: files.map((f) => f.id),
      businessUnitId: unitOrPublic(unit),
      allocations: allocs
        .filter((a) => a.amount)
        .map((a) => ({ soItemId: a.soItemId, amount: a.amount as number })),
      afterSalesId,
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
            title="金额、币种、收款方、付款方式与发生日期不能修改，录错请作废后重新登记"
          />
        )}
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: 220 }}>
            {label('名称')}
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              aria-label="名称"
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
              aria-label="费用类别"
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
              aria-label="发生日期"
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
              aria-label="收款方类型"
              options={[
                { value: PAYEE.OTHER, label: '其他' },
                { value: PAYEE.SUPPLIER, label: '供应商' },
                { value: PAYEE.FORWARDER, label: '货代' },
                { value: PAYEE.SHOP, label: '线上店铺' },
              ]}
            />
            {locked &&
              (payeeType === PAYEE.SUPPLIER ||
                payeeType === PAYEE.FORWARDER) && (
                <Input
                  style={{ width: 280 }}
                  value={supplierName}
                  disabled
                  aria-label={payeeType === PAYEE.FORWARDER ? '货代' : '供应商'}
                />
              )}
            {!locked &&
              (payeeType === PAYEE.SUPPLIER ||
                payeeType === PAYEE.FORWARDER) && (
                <SupplierPicker
                  key={payeeType}
                  forwarder={payeeType === PAYEE.FORWARDER}
                  placeholder={
                    payeeType === PAYEE.FORWARDER ? '搜索货代' : '搜索供应商'
                  }
                  value={supplierId}
                  label={supplierName}
                  aria-label={payeeType === PAYEE.FORWARDER ? '货代' : '供应商'}
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
                  aria-label="店铺平台"
                />
                <Input
                  style={{ width: 220 }}
                  value={shopName}
                  disabled={locked}
                  onChange={(e) => setShopName(e.target.value)}
                  placeholder="店铺名称"
                  aria-label="店铺名称"
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
                aria-label="收款方名称"
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
              aria-label="币种"
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
              aria-label="金额"
            />
          </div>
        </div>
        {bu.enabled && (
          <div style={{ maxWidth: 420 }}>
            {label('所属事业部')}
            <UnitSelect
              info={bu}
              allowPublic
              aria-label="所属事业部"
              value={unit}
              onChange={(v) => {
                unitTouched.current = true;
                setUnit(v);
              }}
              style={{ width: '100%' }}
            />
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
              {allocs.length
                ? '已分摊到订单型号：归属订单的事业部，这里的选择不生效'
                : unit === PUBLIC_UNIT
                  ? '公共费用每月按销售额比例分给各事业部'
                  : '计入该事业部的直接费用，不分给其他事业部'}
            </div>
          </div>
        )}
        <div>
          {label('付款方式')}
          <Segmented<number>
            value={method}
            disabled={locked}
            onChange={setMethod}
            aria-label="付款方式"
            options={[
              { label: '公司对公', value: METHOD.COMPANY },
              { label: '员工垫付', value: METHOD.ADVANCE },
              { label: '私对私', value: METHOD.PRIVATE },
            ]}
          />
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
            {method === METHOD.PRIVATE
              ? '私对私保存后视为已付：同时生成应付单和私对私付款单，不进报销'
              : method === METHOD.ADVANCE
                ? '进入垫付人的待报销，凭证必传'
                : '保存后生成应付，在应付单付款'}
          </div>
          {method === METHOD.PRIVATE && !editing && (
            <div
              style={{
                display: 'flex',
                gap: 14,
                flexWrap: 'wrap',
                marginTop: 12,
              }}
            >
              <div style={{ flex: '1 1 100%' }}>
                {label('付款账户')}
                <Select
                  style={{ width: '100%' }}
                  value={bankAccountId}
                  onChange={setBankAccountId}
                  aria-label="付款账户"
                  placeholder="选择对私账户"
                  options={currencyPrivate.map((a) => ({
                    value: a.id,
                    label: `${a.bankName} · ${a.accountName} ${a.accountNoMasked}（对私）`,
                  }))}
                  notFoundContent={`没有对私的 ${currency} 账户，请先在「收款账户」里添加`}
                />
              </div>
              <div>
                {label('对方收款方式')}
                <Segmented<number>
                  value={payeeChannel}
                  onChange={setPayeeChannel}
                  aria-label="对方收款方式"
                  options={[
                    { label: '支付宝', value: 1 },
                    { label: '微信', value: 2 },
                    { label: '银行卡', value: 3 },
                  ]}
                />
              </div>
              <div style={{ flex: 1, minWidth: 160 }}>
                {label('户名')}
                <Input
                  value={payeeAccountName}
                  onChange={(e) => setPayeeAccountName(e.target.value)}
                  aria-label="对方户名"
                  maxLength={50}
                />
              </div>
              <div style={{ flex: 1, minWidth: 180 }}>
                {label('账号')}
                <Input
                  value={payeeAccountNo}
                  onChange={(e) => setPayeeAccountNo(e.target.value)}
                  aria-label="对方账号"
                  maxLength={50}
                  placeholder={payeeChannel === 3 ? '银行卡号' : '手机号或账号'}
                />
              </div>
            </div>
          )}
          {method === METHOD.ADVANCE && (
            <Select
              style={{ width: 200, marginTop: 8 }}
              placeholder="垫付人（默认自己）"
              aria-label="垫付人"
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
              aria-label="按订单号搜索并添加型号"
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
                      soId: o.soId,
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
            locale={{ emptyText: tableEmpty('没有关联订单型号') }}
            columns={[
              { title: '销售订单', width: 160, dataIndex: 'soNo' },
              { title: '型号', width: 160, dataIndex: 'model' },
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
          {asOptions.length > 0 && (
            <Select
              allowClear
              style={{ width: '100%', marginTop: 10 }}
              placeholder="关联售后单（选填）：补寄运费、维修费等售后支出"
              value={afterSalesId}
              onChange={setAfterSalesId}
              options={asOptions}
              aria-label="关联售后单"
            />
          )}
        </div>
        {!editing && (
          <>
            <div>
              {label('凭证', method !== METHOD.COMPANY)}
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
                aria-label="说明"
              />
            </div>
          </>
        )}
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
  unit?: number;
}

/** 帮助文字：help-texts.md「费用管理」 */
export const HELP = {
  page: '补发运费这类偶发成本，以及房租、工资、平台费等运营成本；公司对公生成应付，员工垫付进入垫付人的待报销，私对私保存即已付。关联订单型号的计入订单成本，不关联的按类别与分摊月数计入经营统计',
  total:
    '按当前筛选条件统计的有效费用单（不含已作废），折人民币；没选日期时统计本月',
} as const;

/** 初始筛选：日期默认最近 3 个月（清空看全部）；经营统计跳转时带 dateFrom、dateTo、category、linked */
const initial = (): Filters => {
  const p = new URLSearchParams(window.location.search);
  const from = p.get('dateFrom');
  const to = p.get('dateTo');
  const linked = p.get('linked');
  return {
    keyword: '',
    soNo: '',
    range: from && to ? [dayjs(from), dayjs(to)] : recentRange(),
    category: p.get('category') ?? undefined,
    linked: linked == null ? undefined : linked === 'true',
    showVoid: false,
    unit:
      p.get('businessUnitId') == null
        ? undefined
        : Number(p.get('businessUnitId')),
  };
};

/** 财务管理 → 费用单：补发运费这类偶发成本，可以分摊到订单型号 */
const ExpensePage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['finance:expense:edit'];
  const categories = useDictTexts('expense_category');
  const [bu] = useBusinessUnits();
  const [draft, setDraft] = useState<Filters>(initial);
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<Expense[]>([]);
  const [total, setTotal] = useState(0);
  const [monthTotal, setMonthTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [drawer, setDrawer] = useState<{ editing?: Expense }>();

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
      businessUnitId: filters.unit,
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

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

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
        <Link
          to={DOC_PATHS.expense(r.id)}
          style={{
            textDecoration: r.status === 2 ? 'line-through' : undefined,
          }}
        >
          {v}
        </Link>
      ),
    },
    { title: '发生日期', dataIndex: 'occurDate', width: 110 },
    { title: '名称', dataIndex: 'name', width: 160, render: ellipsisRender },
    ...(bu.enabled
      ? [
          {
            title: '所属事业部',
            key: 'unit',
            width: 180,
            render: (_: unknown, r: Expense) =>
              r.allocations.length ? (
                <span style={{ color: palette.mute }}>跟随订单</span>
              ) : (
                <UnitPill info={bu} id={r.businessUnitId} />
              ),
          },
        ]
      : []),
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
        r.method === METHOD.COMPANY || r.method === METHOD.PRIVATE ? (
          <Space size={6}>
            <Pill tone={r.method === METHOD.PRIVATE ? 'orange' : 'accent'}>
              {r.method === METHOD.PRIVATE ? '私对私' : '公司对公'}
            </Pill>
            <PayStatusPill status={r.payStatus} />
          </Space>
        ) : (
          <div>
            <Pill tone="gray">员工垫付</Pill> {r.advanceUserName}
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
      width: rowActionsWidth(1),
      fixed: 'right',
      render: (_, r) => (
        <RowActions
          inline={[
            {
              key: 'view',
              label: '查看',
              icon: <Eye size={18} />,
              onClick: () => history.push(DOC_PATHS.expense(r.id)),
            },
          ]}
        />
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        help={HELP.page}
        extra={
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
      <SummaryBar
        items={[
          {
            key: 'total',
            label: filters.range ? '所选期间费用' : '本月费用',
            help: HELP.total,
            value: monthTotal,
            currency: 'CNY',
          },
        ]}
      />
      <FilterCard
        onSearch={() => apply({})}
        onReset={() => {
          const f: Filters = {
            keyword: '',
            soNo: '',
            range: recentRange(),
            showVoid: false,
          };
          setDraft(f);
          setFilters(f);
          setPage(1);
        }}
        moreCount={
          [
            filters.soNo.trim() || undefined,
            filters.method,
            bu.enabled ? filters.unit : undefined,
            filters.linked,
            filters.showVoid || undefined,
          ].filter((v) => v != null).length
        }
        more={
          <>
            <FilterField label="关联订单号">
              <Input
                allowClear
                placeholder="订单编号"
                aria-label="关联订单号"
                value={draft.soNo}
                onChange={(e) => setDraft({ ...draft, soNo: e.target.value })}
                onPressEnter={() => apply({})}
              />
            </FilterField>
            <FilterField label="付款方式">
              <Select
                allowClear
                placeholder="全部"
                aria-label="付款方式"
                value={draft.method}
                onChange={(v) => setDraft({ ...draft, method: v })}
                options={[
                  { value: METHOD.COMPANY, label: '公司对公' },
                  { value: METHOD.ADVANCE, label: '员工垫付' },
                  { value: METHOD.PRIVATE, label: '私对私' },
                ]}
              />
            </FilterField>
            {bu.enabled && (
              <FilterField label="所属事业部">
                <Select
                  allowClear
                  placeholder="全部"
                  aria-label="所属事业部"
                  value={draft.unit}
                  onChange={(v) => setDraft({ ...draft, unit: v })}
                  options={unitFilterOptions(bu)}
                />
              </FilterField>
            )}
            <FilterField label="是否关联订单">
              <Select
                allowClear
                placeholder="全部"
                aria-label="是否关联订单"
                value={draft.linked == null ? undefined : String(draft.linked)}
                onChange={(v) =>
                  setDraft({
                    ...draft,
                    linked: v == null ? undefined : v === 'true',
                  })
                }
                options={[
                  { value: 'false', label: '运营成本（不关联订单）' },
                  { value: 'true', label: '关联订单' },
                ]}
              />
            </FilterField>
            <FilterField label="已作废">
              <Checkbox
                checked={draft.showVoid}
                onChange={(e) =>
                  setDraft({ ...draft, showVoid: e.target.checked })
                }
              >
                一并显示
              </Checkbox>
            </FilterField>
          </>
        }
      >
        <FilterField label="关键词" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="费用编号、名称、收款方"
            aria-label="关键词"
            value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
            onPressEnter={() => apply({})}
          />
        </FilterField>
        <FilterField label="费用日期" span={2}>
          <DatePicker.RangePicker
            style={{ width: '100%' }}
            aria-label="费用日期"
            value={draft.range}
            onChange={(v) =>
              setDraft({
                ...draft,
                range: v?.[0] && v[1] ? [v[0], v[1]] : null,
              })
            }
          />
        </FilterField>
        <FilterField label="费用类别">
          <Select
            allowClear
            placeholder="全部"
            aria-label="费用类别"
            value={draft.category}
            onChange={(v) => setDraft({ ...draft, category: v })}
            options={categories.map((c) => ({ value: c.code, label: c.name }))}
          />
        </FilterField>
      </FilterCard>
      <MainCard count={total} loading={loading}>
        <Table<Expense>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: tableScrollX(columns) }}
          pagination={{
            ...PAGER_STYLE,
            current: page,
            pageSize,
            total,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      </MainCard>
      <ExpenseDrawer
        open={!!drawer}
        editing={drawer?.editing}
        onClose={() => setDrawer(undefined)}
        onDone={load}
      />
    </div>
  );
};

export default ExpensePage;
