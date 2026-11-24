import { history } from '@umijs/max';
import type { TableColumnsType, UploadFile } from 'antd';
import {
  App,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Table,
  Upload,
} from 'antd';
import dayjs from 'dayjs';
import {
  Ban,
  FileImage,
  FileText,
  Paperclip,
  Search,
  Undo2,
  UploadIcon,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { tableEmpty } from '@/components/EmptyState';
import {
  CardTitle,
  FilterCard,
  FilterField,
  MainCard,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { CURRENCIES } from '@/pages/quotation/components';
import { PATHS, Pill } from '@/pages/sales/components';
import {
  ReasonModal,
  SLIP_TYPES,
  useBankOptions,
} from '@/pages/sales/pi/dialogs';
import {
  FxEstimateHint,
  PaymentMethodSelect,
} from '@/pages/sales/pi/receiptDialogs';
import { type ReceiptRow, readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import {
  UnitPill,
  unitFilterOptions,
  useBusinessUnits,
} from '@/utils/businessUnit';
import { formatAmount } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import { ledgerApi } from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 帮助文字：help-texts.md「收款管理」，关键词范围以 search-scope.md 为准 */
const UNCLAIMED_HELP = {
  keyword: '可搜：付款人、说明、金额（去掉千分位逗号后匹配）',
} as const;
const label: React.CSSProperties = { fontSize: 13, marginBottom: 6 };

/** 付款方式与线上 / 线下 */
export const MethodCell: React.FC<{ r: ReceiptRow }> = ({ r }) => (
  <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
    {r.paymentMethodName}
    <Pill tone={r.channel === 2 ? 'violet' : 'gray'}>
      {r.channel === 2 ? '线上' : '线下'}
    </Pill>
  </span>
);

export const SETTLE_NAMES: Record<number, string> = {
  1: '未结汇 · 估算',
  2: '部分结汇',
  3: '已结汇',
};

/** 实收人民币、到账汇率与结汇状态 */
export const NetCnyCell: React.FC<{ r: ReceiptRow }> = ({ r }) => {
  const { palette } = useAppTheme();
  const settled = r.settleStatus === 3;
  return (
    <div>
      <b
        style={{
          ...num,
          color: settled || !r.settleStatus ? palette.ink : palette.sub,
        }}
      >
        {formatAmount(r.netAmountCny, 'CNY')}
      </b>
      <div
        style={{
          fontSize: 12,
          color: settled
            ? palette.green
            : r.settleStatus
              ? palette.orange
              : palette.mute,
        }}
      >
        {r.settleStatus ? SETTLE_NAMES[r.settleStatus] : '人民币'}{' '}
        {Number(r.exchangeRate).toFixed(6)}
      </div>
      {!!r.settleNos?.length && (
        <div style={{ fontSize: 12, color: palette.mute }}>
          {r.settleNos.join('、')}
        </div>
      )}
    </div>
  );
};

const RegisterModal: React.FC<{
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, onClose, onDone }) => {
  const { message } = App.useApp();
  const [bu] = useBusinessUnits();
  const { palette } = useAppTheme();
  const [currency, setCurrency] = useState('USD');
  const [amount, setAmount] = useState<number | null>(null);
  const [date, setDate] = useState<dayjs.Dayjs | null>(dayjs());
  const [bankId, setBankId] = useState<number>();
  const [method, setMethod] = useState<string>();
  const [payer, setPayer] = useState('');
  const [note, setNote] = useState('');
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [busy, setBusy] = useState(false);
  const banks = useBankOptions(currency);

  useEffect(() => {
    if (!open) return;
    setAmount(null);
    setDate(dayjs());
    setMethod(undefined);
    setPayer('');
    setNote('');
    setFiles([]);
  }, [open]);

  useEffect(() => {
    if (!bankId || !banks.some((b) => b.id === bankId)) {
      setBankId(banks.find((b) => b.isDefault)?.id ?? banks[0]?.id);
    }
  }, [banks, bankId]);

  const submit = async () => {
    if (!amount || !date || !bankId) return;
    setBusy(true);
    try {
      const created = await ledgerApi.registerUnclaimed({
        currencyCode: currency,
        amount,
        receiptDate: date.format('YYYY-MM-DD'),
        bankAccountId: bankId,
        paymentMethod: method,
        payer: payer.trim() || undefined,
        note: note.trim() || undefined,
      });
      if (files.length) {
        try {
          await ledgerApi.addUnclaimedFiles(
            created.id,
            files.map((f) => f.originFileObj as File),
          );
        } catch (e) {
          // 到账已登记，附件没传上：提示后仍关闭，可在列表里看到没有附件
          message.warning(`已登记，附件上传失败：${readBizError(e).message}`);
          onDone();
          return;
        }
      }
      message.success('已登记，业务员可以在自己的 PI 上认领');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={600}
      title="登记未认领到账"
      okText="登记"
      confirmLoading={busy}
      okButtonProps={{ disabled: !amount || amount <= 0 || !date || !bankId }}
      onOk={submit}
    >
      <div style={{ fontSize: 13, color: palette.sub, marginBottom: 12 }}>
        钱到了公司账户，但还不知道是哪张 PI 的：先登记，业务员在自己的 PI
        上认领。
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gap: 12,
        }}
      >
        <div>
          <div style={{ ...label, color: palette.sub }}>币种</div>
          <Select
            style={{ width: '100%' }}
            value={currency}
            onChange={setCurrency}
            options={CURRENCIES.map((c) => ({ value: c, label: c }))}
            aria-label="币种"
          />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>到账金额</div>
          <InputNumber
            style={{ width: '100%' }}
            prefix={currency}
            min={0.01}
            precision={2}
            value={amount}
            onChange={setAmount}
            aria-label="到账金额"
          />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>到账日期</div>
          <DatePicker
            style={{ width: '100%' }}
            value={date}
            onChange={setDate}
            aria-label="到账日期"
          />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>收款账户</div>
          <Select
            style={{ width: '100%' }}
            value={bankId}
            onChange={setBankId}
            placeholder={
              banks.length === 0
                ? `还没有 ${currency} 收款账户`
                : '选择收款账户'
            }
            options={banks.map((b) => ({
              value: b.id,
              label: `${b.bankName} ${b.accountNoMasked}${b.accountType === 2 ? '（对私）' : ''}${bu.enabled ? ` · ${bu.units.find((u) => u.id === b.businessUnitId)?.name ?? ''}` : ''}`,
            }))}
            aria-label="收款账户"
          />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>付款方式</div>
          <PaymentMethodSelect
            online={false}
            value={method}
            onChange={setMethod}
          />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>
            付款人（银行流水上的名称）
          </div>
          <Input
            value={payer}
            maxLength={128}
            onChange={(e) => setPayer(e.target.value)}
            aria-label="付款人"
          />
        </div>
      </div>
      <FxEstimateHint currency={currency} />
      <div style={{ marginTop: 12 }}>
        <div style={{ ...label, color: palette.sub }}>说明（选填）</div>
        <Input
          value={note}
          maxLength={300}
          placeholder="如 群里已通知"
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
      <div style={{ marginTop: 12 }}>
        <div style={{ ...label, color: palette.sub }}>
          附件{' '}
          <span style={{ fontSize: 12, color: palette.mute }}>最多 5 个</span>
        </div>
        <Upload.Dragger
          multiple
          fileList={files}
          accept=".jpg,.jpeg,.png,.pdf"
          beforeUpload={(f) => {
            if (!SLIP_TYPES.includes(f.type)) {
              message.error('只支持 JPG、PNG、PDF');
              return Upload.LIST_IGNORE;
            }
            if (f.size > 10 * 1024 * 1024) {
              message.error('单个文件不能超过 10MB');
              return Upload.LIST_IGNORE;
            }
            return false;
          }}
          onChange={({ fileList }) => setFiles(fileList.slice(0, 5))}
          style={{ padding: '4px 0' }}
        >
          <span style={{ color: palette.sub }}>
            <UploadIcon size="1em" /> 拖入或点击上传银行回单
          </span>
        </Upload.Dragger>
      </div>
    </Modal>
  );
};

/** 未认领到账的附件：点开在新窗口预览 */
const FilesModal: React.FC<{ row?: ReceiptRow; onClose: () => void }> = ({
  row,
  onClose,
}) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  return (
    <Modal
      open={!!row}
      onCancel={onClose}
      footer={null}
      width={560}
      title={row ? `附件 · ${formatAmount(row.amount, row.currencyCode)}` : ''}
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
          gap: 10,
        }}
      >
        {row?.fileNames?.map((name, i) => (
          <button
            type="button"
            key={name}
            onClick={() =>
              ledgerApi
                .openFile(row.id, i)
                .catch((e) => message.error(readBizError(e).message))
            }
            style={{
              display: 'grid',
              justifyItems: 'center',
              gap: 8,
              padding: '24px 10px',
              borderRadius: 12,
              border: `1px solid ${palette.hairline}`,
              background: palette.inset,
              color: palette.ink,
              cursor: 'pointer',
              fontSize: 12,
            }}
          >
            {/\.pdf$/i.test(name) ? (
              <FileText
                size="1em"
                style={{ fontSize: 26, color: palette.sub }}
              />
            ) : (
              <FileImage
                size="1em"
                style={{ fontSize: 26, color: palette.sub }}
              />
            )}
            <span style={{ wordBreak: 'break-all' }}>{name}</span>
          </button>
        ))}
      </div>
    </Modal>
  );
};

/** 收款单 → 未认领到账：财务先登记、业务员在 PI 上认领；最近认领的可以取消认领 */
const UnclaimedTab: React.FC<{
  registerSignal: number;
  onCount?: (n: number) => void;
}> = ({ registerSignal, onCount }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [form] = Form.useForm<{
    keyword?: string;
    currency?: string;
    unitFilter?: number;
  }>();
  const [keyword, setKeyword] = useState('');
  const [currency, setCurrency] = useState<string>();
  const [data, setData] = useState<{
    unclaimed: ReceiptRow[];
    recentClaimed: ReceiptRow[];
  }>();
  const [loading, setLoading] = useState(true);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [viewing, setViewing] = useState<ReceiptRow>();
  const [bu] = useBusinessUnits();
  const [unitFilter, setUnitFilter] = useState<number>();
  const [reason, setReason] = useState<{
    id: number;
    kind: 'void' | 'unclaim';
  }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await ledgerApi.unclaimed(
        keyword.trim() || undefined,
        currency,
        unitFilter,
      );
      setData(res);
      if (!keyword.trim() && !currency && unitFilter == null) {
        onCount?.(res.unclaimed.length);
      }
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [keyword, currency, unitFilter, message, onCount]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  useEffect(() => {
    if (registerSignal > 0) setRegisterOpen(true);
  }, [registerSignal]);

  const search = () => {
    const v = form.getFieldsValue();
    setKeyword(v.keyword ?? '');
    setCurrency(v.currency);
    setUnitFilter(v.unitFilter);
  };

  const columns: TableColumnsType<ReceiptRow> = [
    {
      title: '收款单号',
      dataIndex: 'receiptNo',
      width: 150,
      render: (v: string | undefined, r) =>
        v ? (
          <button
            type="button"
            className="zhul-link"
            style={{ whiteSpace: 'nowrap' }}
            onClick={() => history.push(`/finance/receipts/${r.id}`)}
          >
            {v}
          </button>
        ) : (
          '—'
        ),
    },
    { title: '到账日期', dataIndex: 'receiptDate', width: 110 },
    {
      title: '金额',
      key: 'amount',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <b style={{ ...num, color: palette.ink }}>
          {formatAmount(r.amount, r.currencyCode)}
        </b>
      ),
    },
    {
      title: '付款人',
      width: 180,
      dataIndex: 'payer',
      render: (v?: string) =>
        v || <span style={{ color: palette.mute }}>未填</span>,
    },
    {
      title: '付款方式',
      key: 'method',
      width: 150,
      render: (_, r) => <MethodCell r={r} />,
    },
    { title: '收款账户', dataIndex: 'bankAccountName', width: 200 },
    ...(bu.enabled
      ? [
          {
            title: '所属事业部',
            key: 'unit',
            width: 180,
            render: (_: unknown, r: ReceiptRow) => (
              <UnitPill info={bu} id={r.businessUnitId} />
            ),
          },
        ]
      : []),
    {
      title: '实收人民币',
      key: 'cny',
      width: 160,
      render: (_, r) => <NetCnyCell r={r} />,
    },
    {
      title: '附件',
      key: 'files',
      width: 80,
      render: (_, r) =>
        r.fileNames?.length ? (
          <button
            type="button"
            className="zhul-link"
            onClick={() => setViewing(r)}
          >
            <Paperclip size="1em" /> {r.fileNames.length} 个
          </button>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '说明',
      dataIndex: 'note',
      width: 160,
      ellipsis: true,
      render: (v?: string) => v || '—',
    },
    {
      title: '操作',
      key: 'op',
      width: rowActionsWidth(1),
      render: (_, r) => (
        <RowActions
          more={[
            {
              key: 'void',
              label: '作废',
              icon: <Ban size={16} />,
              danger: true,
            },
          ]}
          onMore={() => setReason({ id: r.id, kind: 'void' })}
        />
      ),
    },
  ];

  const claimedColumns: TableColumnsType<ReceiptRow> = [
    {
      title: '收款单号',
      dataIndex: 'receiptNo',
      width: 150,
      render: (v: string | undefined, r) =>
        v ? (
          <button
            type="button"
            className="zhul-link"
            style={{ whiteSpace: 'nowrap' }}
            onClick={() => history.push(`/finance/receipts/${r.id}`)}
          >
            {v}
          </button>
        ) : (
          '—'
        ),
    },
    { title: '到账日期', dataIndex: 'receiptDate', width: 110 },
    {
      title: '金额',
      key: 'amount',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.amount, r.currencyCode)}</span>
      ),
    },
    {
      title: '付款人',
      width: 180,
      dataIndex: 'payer',
      render: (v?: string) => v || '—',
    },
    {
      title: '认领到',
      key: 'pi',
      width: 220,
      render: (_, r) =>
        r.piId || r.soId ? (
          <span>
            <button
              type="button"
              className="zhul-link"
              onClick={() =>
                history.push(
                  r.piId ? PATHS.pi(r.piId) : PATHS.order(r.soId as number),
                )
              }
            >
              {r.piNo ?? r.soNo}
            </button>
            <span style={{ marginLeft: 6, fontSize: 12, color: palette.mute }}>
              {r.customerName}
            </span>
          </span>
        ) : (
          '—'
        ),
    },
    {
      title: '认领人',
      key: 'by',
      width: 160,
      render: (_, r) =>
        `${r.claimedByName ?? ''} ${r.claimedAt ? dayjs(r.claimedAt).format('MM-DD HH:mm') : ''}`,
    },
    {
      title: '操作',
      key: 'op',
      width: rowActionsWidth(1),
      render: (_, r) => (
        <RowActions
          more={[
            {
              key: 'unclaim',
              label: '取消认领',
              icon: <Undo2 size={16} />,
              danger: true,
            },
          ]}
          onMore={() => setReason({ id: r.id, kind: 'unclaim' })}
        />
      ),
    },
  ];

  return (
    <div>
      <Form form={form} component={false}>
        <FilterCard
          onSearch={search}
          onReset={() => {
            form.resetFields();
            setKeyword('');
            setCurrency(undefined);
            setUnitFilter(undefined);
          }}
        >
          <FilterField label="关键词" span={2} help={UNCLAIMED_HELP.keyword}>
            <Form.Item name="keyword" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="付款人、金额、说明"
                aria-label="关键词"
                onPressEnter={search}
              />
            </Form.Item>
          </FilterField>
          <FilterField label="币种">
            <Form.Item name="currency" noStyle>
              <Select
                allowClear
                placeholder="全部"
                aria-label="币种"
                options={CURRENCIES.map((c) => ({ value: c, label: c }))}
              />
            </Form.Item>
          </FilterField>
          {bu.enabled && (
            <FilterField label="所属事业部">
              <Form.Item name="unitFilter" noStyle>
                <Select
                  allowClear
                  placeholder="全部"
                  aria-label="所属事业部"
                  options={unitFilterOptions(bu, false)}
                />
              </Form.Item>
            </FilterField>
          )}
        </FilterCard>
      </Form>
      <MainCard
        left={
          loading && !(data?.unclaimed?.length ?? 0)
            ? undefined
            : `共 ${data?.unclaimed?.length ?? 0} 条`
        }
      >
        <Table<ReceiptRow>
          rowKey="id"
          columns={columns}
          dataSource={data?.unclaimed}
          loading={loading}
          pagination={false}
          scroll={{ x: tableScrollX(columns) }}
          locale={{ emptyText: tableEmpty('没有未认领的到账', loading) }}
        />
      </MainCard>
      <div style={{ height: 16 }} />
      <MainCard left={<CardTitle>最近认领</CardTitle>}>
        <Table<ReceiptRow>
          rowKey="id"
          columns={claimedColumns}
          dataSource={data?.recentClaimed}
          loading={loading}
          pagination={false}
          scroll={{ x: tableScrollX(claimedColumns) }}
          locale={{ emptyText: tableEmpty('还没有认领记录', loading) }}
        />
      </MainCard>
      <FilesModal row={viewing} onClose={() => setViewing(undefined)} />
      <RegisterModal
        open={registerOpen}
        onClose={() => setRegisterOpen(false)}
        onDone={() => {
          setRegisterOpen(false);
          load();
        }}
      />
      <ReasonModal
        open={!!reason}
        title={reason?.kind === 'unclaim' ? '取消认领' : '作废未认领到账'}
        description={
          reason?.kind === 'unclaim'
            ? '到账回到未认领，原 PI 的已到账与收款状态随之重算。'
            : '作废后不再出现在未认领列表，记录保留。'
        }
        okText={reason?.kind === 'unclaim' ? '取消认领' : '作废'}
        onClose={() => setReason(undefined)}
        onSubmit={async (why) => {
          if (!reason) return;
          try {
            if (reason.kind === 'unclaim')
              await ledgerApi.unclaim(reason.id, why);
            else await ledgerApi.voidUnclaimed(reason.id, why);
            message.success(
              reason.kind === 'unclaim' ? '已取消认领' : '已作废',
            );
            setReason(undefined);
            load();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default UnclaimedTab;
