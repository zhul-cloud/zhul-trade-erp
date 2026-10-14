import { PlusOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Checkbox,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import { readBizError } from '@/pages/sales/service';
import { AttachmentWall, ReasonModal } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { useDictTexts } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { ACCEPT_PROOF, ProofLinks } from '../payables/components';
import { DICT_INCOME_CATEGORY, incomeApi, type OtherIncome } from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 经营统计跳转时 URL 带的日期范围 */
const rangeFromUrl = (): [Dayjs, Dayjs] | null => {
  const p = new URLSearchParams(window.location.search);
  const from = p.get('dateFrom');
  const to = p.get('dateTo');
  return from && to ? [dayjs(from), dayjs(to)] : null;
};

const IncomeModal: React.FC<{
  open: boolean;
  editing?: OtherIncome;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, editing, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const categories = useDictTexts(DICT_INCOME_CATEGORY);
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [category, setCategory] = useState<string>();
  const [amount, setAmount] = useState<number | null>(null);
  const [remark, setRemark] = useState('');
  const [files, setFiles] = useState<Attachment[]>([]);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (!open) return;
    setDate(editing ? dayjs(editing.incomeDate) : dayjs());
    setCategory(editing?.category);
    setAmount(editing?.amount ?? null);
    setRemark(editing?.remark ?? '');
    setFiles((editing?.attachments ?? []) as unknown as Attachment[]);
  }, [open, editing]);
  const submit = async () => {
    if (!category || !amount || !remark.trim()) {
      message.error('请填写类别、金额与说明');
      return;
    }
    const body = {
      incomeDate: date.format('YYYY-MM-DD'),
      category,
      amount,
      remark: remark.trim(),
      attachmentIds: files.map((f) => f.id),
    };
    setSaving(true);
    try {
      if (editing) await incomeApi.update(editing.id, body);
      else await incomeApi.create(body);
      message.success('已保存');
      onDone();
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  const label = (t: string, req = true) => (
    <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
      {req && <span style={{ color: palette.red }}>* </span>}
      {t}
    </div>
  );
  return (
    <Modal
      open={open}
      title={editing ? `修改 ${editing.incomeNo}` : '登记其他收入'}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ flex: '1 1 150px' }}>
            {label('日期')}
            <DatePicker
              value={date}
              allowClear={false}
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
              onChange={(d) => d && setDate(d)}
              style={{ width: '100%' }}
            />
          </div>
          <div style={{ flex: '1 1 150px' }}>
            {label('类别')}
            <Select
              value={category}
              onChange={setCategory}
              style={{ width: '100%' }}
              options={categories.map((c) => ({
                value: c.code,
                label: c.name,
              }))}
            />
          </div>
          <div style={{ flex: '1 1 150px' }}>
            {label('金额')}
            <InputNumber<number>
              min={0.01}
              precision={2}
              prefix="CNY"
              value={amount}
              onChange={setAmount}
              style={{ width: '100%' }}
              aria-label="金额"
            />
          </div>
        </div>
        <div>
          {label('说明')}
          <Input
            maxLength={500}
            placeholder="如 信保 9 月返点"
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
          />
        </div>
        <div>
          {label('凭证', false)}
          <AttachmentWall
            ownerType="OTHER_INCOME"
            value={files}
            onChange={setFiles}
            max={10}
            accept={ACCEPT_PROOF}
          />
        </div>
      </Space>
    </Modal>
  );
};

/** 财务管理 → 其他收入：平台返点、利息、补贴等，按月计入经营统计 */
const OtherIncomePage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['finance:income:edit'];
  const categories = useDictTexts(DICT_INCOME_CATEGORY);
  const [category, setCategory] = useState<string>();
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(rangeFromUrl);
  const [showVoid, setShowVoid] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<OtherIncome[]>([]);
  const [total, setTotal] = useState(0);
  const [sum, setSum] = useState(0);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState<{ editing?: OtherIncome }>();
  const [voiding, setVoiding] = useState<OtherIncome>();

  const load = useCallback(async () => {
    setLoading(true);
    const q = {
      category,
      dateFrom: range?.[0].format('YYYY-MM-DD'),
      dateTo: range?.[1].format('YYYY-MM-DD'),
      showVoid: showVoid || undefined,
    };
    try {
      const [res, t] = await Promise.all([
        incomeApi.page({ ...q, page, pageSize }),
        incomeApi.total(q),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setSum(t);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [category, range, showVoid, page, pageSize, message]);

  useEffect(() => {
    load();
  }, [load]);

  const columns: TableColumnsType<OtherIncome> = [
    {
      title: '编号',
      dataIndex: 'incomeNo',
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
    { title: '日期', dataIndex: 'incomeDate', width: 110 },
    { title: '类别', dataIndex: 'categoryName', width: 110 },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number) => <b style={num}>{formatAmount(v, 'CNY')}</b>,
    },
    { title: '说明', dataIndex: 'remark', width: 240, ellipsis: true },
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
    ...auditColumns<OtherIncome>(),
    {
      title: '操作',
      key: 'op',
      width: 110,
      fixed: 'right',
      render: (_, r) =>
        canEdit && r.status === 1 ? (
          <Space size={10}>
            <a onClick={() => setModal({ editing: r })}>修改</a>
            <a onClick={() => setVoiding(r)}>作废</a>
          </Space>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['其他收入']}
        title="其他收入"
        description="不属于订单的收入（平台返点、利息、政府补贴等），按日期所在月份计入经营统计。"
        actions={
          canEdit && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setModal({})}
            >
              登记其他收入
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
        <div style={{ fontSize: 13, color: palette.sub }}>合计（人民币）</div>
        <div
          style={{
            ...num,
            fontSize: 22,
            fontWeight: 800,
            color: palette.green,
            marginTop: 4,
          }}
        >
          {formatAmount(sum, 'CNY')}
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
          <DatePicker.RangePicker
            value={range}
            onChange={(v) => {
              setRange(v?.[0] && v[1] ? [v[0], v[1]] : null);
              setPage(1);
            }}
          />
          <Select
            allowClear
            placeholder="类别"
            style={{ width: 140 }}
            value={category}
            onChange={(v) => {
              setCategory(v);
              setPage(1);
            }}
            options={categories.map((c) => ({ value: c.code, label: c.name }))}
          />
          <Checkbox
            checked={showVoid}
            onChange={(e) => setShowVoid(e.target.checked)}
          >
            显示已作废
          </Checkbox>
        </div>
        <Table<OtherIncome>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1500 }}
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
      <IncomeModal
        open={!!modal}
        editing={modal?.editing}
        onClose={() => setModal(undefined)}
        onDone={load}
      />
      <ReasonModal
        open={!!voiding}
        title={`作废 ${voiding?.incomeNo ?? ''}`}
        description="作废后不再计入经营统计。"
        okText="作废"
        danger
        onCancel={() => setVoiding(undefined)}
        onOk={async (why) => {
          if (!voiding) return;
          await incomeApi.voidIncome(voiding.id, why);
          message.success('已作废');
          setVoiding(undefined);
          load();
        }}
      />
    </div>
  );
};

export default OtherIncomePage;
