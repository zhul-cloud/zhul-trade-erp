import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Checkbox,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Space,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import {
  DICT_REFUND_REASON,
  REFUND_METHODS,
  type RequestRefundable,
  refundRequestApi,
} from '@/pages/finance/refunds/service';
import { AttachmentWall } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { searchCustomers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { useDictTexts } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { type Order, orderApi, readBizError } from '../service';
import {
  type AfterSales,
  afterSalesApi,
  DICT_LEVEL,
  DICT_REASON,
  DICT_SOLUTION,
  returnApi,
} from './service';

const fmt = (d?: Dayjs | null) =>
  d ? d.second(0).format('YYYY-MM-DDTHH:mm:ss') : undefined;

const Label: React.FC<{ text: string; required?: boolean }> = ({
  text,
  required,
}) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
      {required && <span style={{ color: palette.red }}>* </span>}
      {text}
    </div>
  );
};

/** 负责人候选：本租户启用的用户 */
export const useOwnerOptions = () => {
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  useEffect(() => {
    afterSalesApi
      .owners()
      .then((r) => setUsers(r.map((u) => ({ value: u.id, label: u.name }))))
      .catch(() => setUsers([]));
  }, []);
  return users;
};

/** 等级按钮组 */
const LevelPicker: React.FC<{
  value?: string;
  onChange: (v: string) => void;
}> = ({ value, onChange }) => {
  const levels = useDictTexts(DICT_LEVEL);
  return (
    <Segmented
      value={value}
      onChange={(v) => onChange(String(v))}
      options={levels.map((l) => ({ value: l.code, label: l.name }))}
    />
  );
};

// ---------------------------------------------------------------- 新建售后单

type Line = {
  key: number;
  soItemId?: number;
  model?: string;
  brand?: string;
  quantity: number | null;
  max?: number;
  picked?: boolean;
};

/** 新建售后单（spec sales/after-sales）：从订单发起时带出客户、订单与型号；也可以不选订单按型号追溯 */
export const CreateAfterSalesModal: React.FC<{
  open: boolean;
  order?: Order;
  onClose: () => void;
  onDone: (a: AfterSales) => void;
}> = ({ open, order, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const owners = useOwnerOptions();
  const [mode, setMode] = useState<'order' | 'none'>('order');
  const [picked, setPicked] = useState<Order>();
  const [orderOptions, setOrderOptions] = useState<
    { value: number; label: string }[]
  >([]);
  const [customerId, setCustomerId] = useState<number>();
  const [customerOptions, setCustomerOptions] = useState<
    { value: number; label: string }[]
  >([]);
  const [lines, setLines] = useState<Line[]>([]);
  const [level, setLevel] = useState('PENDING');
  const [symptom, setSymptom] = useState('');
  const [request, setRequest] = useState('');
  const [receivedAt, setReceivedAt] = useState<Dayjs | null>(dayjs());
  const [ownerId, setOwnerId] = useState<number>();
  const [nextAction, setNextAction] = useState('');
  const [nextUpdate, setNextUpdate] = useState<Dayjs | null>(null);
  const [files, setFiles] = useState<Attachment[]>([]);
  const [busy, setBusy] = useState(false);

  const applyOrder = (o?: Order) => {
    setPicked(o);
    setLines(
      (o?.items ?? [])
        .filter((i) => i.quantity > 0)
        .map((i, n) => ({
          key: n,
          soItemId: i.id,
          model: i.model,
          brand: i.brand,
          quantity: null,
          max: i.quantity,
          picked: false,
        })),
    );
    setOwnerId(o?.ownerId);
  };

  useEffect(() => {
    if (!open) return;
    setMode('order');
    setCustomerId(undefined);
    setLevel('PENDING');
    setSymptom('');
    setRequest('');
    setReceivedAt(dayjs());
    setNextAction('');
    setNextUpdate(null);
    setFiles([]);
    applyOrder(order);
  }, [open, order]);

  const searchOrders = async (kw: string) => {
    try {
      const res = await orderApi.page({
        keyword: kw,
        page: 1,
        pageSize: 20,
      } as never);
      setOrderOptions(
        res.records
          .filter((r) => r.progressCode !== 'CANCELLED')
          .map((r) => ({
            value: r.id,
            label: `${r.soNo} · ${r.customerName}`,
          })),
      );
    } catch {
      setOrderOptions([]);
    }
  };
  const searchCust = async (kw: string) => {
    try {
      const res = await searchCustomers(kw);
      setCustomerOptions(
        res.map((c: { id: number; name?: string; companyName?: string }) => ({
          value: c.id,
          label: c.name ?? c.companyName ?? String(c.id),
        })),
      );
    } catch {
      setCustomerOptions([]);
    }
  };

  const submit = async () => {
    const withOrder = mode === 'order';
    if (withOrder && !picked) {
      message.error('请选择订单，或切换为「没有订单」');
      return;
    }
    if (!withOrder && !customerId) {
      message.error('请选择客户');
      return;
    }
    if (!symptom.trim()) {
      message.error('请填写问题现象');
      return;
    }
    const items = withOrder
      ? lines
          .filter((l) => l.picked && (l.quantity ?? 0) > 0)
          .map((l) => ({
            soItemId: l.soItemId,
            quantity: l.quantity as number,
          }))
      : lines
          .filter((l) => l.model?.trim() && (l.quantity ?? 0) > 0)
          .map((l) => ({
            model: l.model?.trim(),
            brand: l.brand?.trim(),
            quantity: l.quantity as number,
          }));
    setBusy(true);
    try {
      const a = await afterSalesApi.create({
        customerId: withOrder
          ? (picked?.customerId as number)
          : (customerId as number),
        soId: withOrder ? picked?.id : undefined,
        items,
        levelCode: level,
        symptom: symptom.trim(),
        customerRequest: request.trim() || undefined,
        receivedAt: fmt(receivedAt),
        ownerId,
        nextAction: nextAction.trim() || undefined,
        nextUpdateAt: fmt(nextUpdate),
        attachmentIds: files.map((f) => f.id),
      });
      message.success(`已创建售后单 ${a.asNo}`);
      onDone(a);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const grid2: React.CSSProperties = {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: 16,
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={760}
      title={
        <div>
          <div>新建售后单</div>
          <div style={{ fontSize: 12, color: palette.mute, fontWeight: 400 }}>
            从订单详情点「售后」时，客户、订单与型号自动带出
          </div>
        </div>
      }
      okText="创建售后单"
      okButtonProps={{ loading: busy }}
      onOk={submit}
      destroyOnHidden
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        {!order && (
          <Segmented<'order' | 'none'>
            value={mode}
            onChange={(v) => {
              setMode(v);
              applyOrder(undefined);
              if (v === 'none') {
                setLines([{ key: 0, quantity: 1 }]);
              }
            }}
            options={[
              { value: 'order', label: '有订单' },
              { value: 'none', label: '没有订单（按型号追溯）' },
            ]}
          />
        )}
        {mode === 'order' ? (
          <div>
            <Label text="订单" required />
            {order ? (
              <b style={{ color: palette.ink }}>
                {order.soNo} · {order.customerName}
              </b>
            ) : (
              <Select
                showSearch={{ filterOption: false, onSearch: searchOrders }}
                style={{ width: '100%' }}
                placeholder="按订单号或客户搜索"
                value={picked?.id}
                onChange={async (id) => {
                  try {
                    applyOrder(await orderApi.detail(id));
                  } catch (e) {
                    message.error(readBizError(e).message);
                  }
                }}
                options={orderOptions}
                aria-label="订单"
              />
            )}
          </div>
        ) : (
          <div>
            <Label text="客户" required />
            <Select
              showSearch={{ filterOption: false, onSearch: searchCust }}
              style={{ width: '100%' }}
              placeholder="搜索客户"
              value={customerId}
              onChange={setCustomerId}
              options={customerOptions}
              aria-label="客户"
            />
          </div>
        )}
        <div>
          <Label text="有问题的型号" />
          <div
            style={{
              border: `1px solid ${palette.hairline}`,
              borderRadius: 12,
              overflow: 'hidden',
            }}
          >
            {lines.length === 0 && (
              <div style={{ padding: 14, fontSize: 12, color: palette.mute }}>
                先选订单
              </div>
            )}
            {lines.map((l, n) => (
              <div
                key={l.key}
                style={{
                  display: 'flex',
                  gap: 12,
                  alignItems: 'center',
                  padding: '10px 14px',
                  borderTop: n ? `1px solid ${palette.hairline}` : undefined,
                }}
              >
                {mode === 'order' ? (
                  <>
                    <Checkbox
                      checked={l.picked}
                      onChange={(e) =>
                        setLines((s) =>
                          s.map((x) =>
                            x.key === l.key
                              ? {
                                  ...x,
                                  picked: e.target.checked,
                                  quantity: e.target.checked
                                    ? (x.quantity ?? 1)
                                    : x.quantity,
                                }
                              : x,
                          ),
                        )
                      }
                      aria-label={`选择 ${l.model}`}
                    />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 600, color: palette.ink }}>
                        {l.model}
                      </div>
                      <div style={{ fontSize: 12, color: palette.mute }}>
                        {[l.brand, `订单 ${l.max} 个`]
                          .filter(Boolean)
                          .join(' · ')}
                      </div>
                    </div>
                  </>
                ) : (
                  <>
                    <Input
                      placeholder="型号"
                      value={l.model}
                      onChange={(e) =>
                        setLines((s) =>
                          s.map((x) =>
                            x.key === l.key
                              ? { ...x, model: e.target.value }
                              : x,
                          ),
                        )
                      }
                      style={{ flex: 2 }}
                      aria-label="型号"
                    />
                    <Input
                      placeholder="品牌"
                      value={l.brand}
                      onChange={(e) =>
                        setLines((s) =>
                          s.map((x) =>
                            x.key === l.key
                              ? { ...x, brand: e.target.value }
                              : x,
                          ),
                        )
                      }
                      style={{ flex: 1 }}
                      aria-label="品牌"
                    />
                  </>
                )}
                <InputNumber<number>
                  min={1}
                  max={l.max}
                  precision={0}
                  value={l.quantity}
                  disabled={mode === 'order' && !l.picked}
                  onChange={(v) =>
                    setLines((s) =>
                      s.map((x) =>
                        x.key === l.key ? { ...x, quantity: v } : x,
                      ),
                    )
                  }
                  style={{ width: 100 }}
                  aria-label={`${l.model ?? '型号'} 数量`}
                />
                {mode === 'none' && lines.length > 1 && (
                  <Button
                    type="text"
                    icon={<DeleteOutlined />}
                    aria-label="删除这一行"
                    onClick={() =>
                      setLines((s) => s.filter((x) => x.key !== l.key))
                    }
                  />
                )}
              </div>
            ))}
          </div>
          {mode === 'none' && (
            <Button
              type="dashed"
              icon={<PlusOutlined />}
              style={{ marginTop: 8 }}
              onClick={() =>
                setLines((s) => [...s, { key: Date.now(), quantity: 1 }])
              }
            >
              加一个型号
            </Button>
          )}
        </div>
        <div>
          <Label text="问题等级" />
          <LevelPicker value={level} onChange={setLevel} />
        </div>
        <div>
          <Label text="问题现象" required />
          <Input.TextArea
            rows={2}
            maxLength={1000}
            value={symptom}
            onChange={(e) => setSymptom(e.target.value)}
            placeholder="如 伺服报警 AL.16，轴无法运行；客户已断电"
            aria-label="问题现象"
          />
        </div>
        <div style={grid2}>
          <div>
            <Label text="客户诉求" />
            <Input
              maxLength={500}
              value={request}
              onChange={(e) => setRequest(e.target.value)}
              placeholder="如 尽快换新，产线停机中"
              aria-label="客户诉求"
            />
          </div>
          <div>
            <Label text="收到时间" />
            <DatePicker
              showTime={{ format: 'HH:mm' }}
              format="YYYY-MM-DD HH:mm"
              value={receivedAt}
              onChange={setReceivedAt}
              style={{ width: '100%' }}
              aria-label="收到时间"
            />
          </div>
        </div>
        <div>
          <Label text="证据附件（图片、视频、PDF）" />
          <AttachmentWall
            ownerType="AFTER_SALES"
            value={files}
            onChange={setFiles}
            max={20}
            accept="image/*,video/mp4,video/quicktime,application/pdf"
          />
        </div>
        <div style={grid2}>
          <div>
            <Label text="负责人" />
            <Select
              showSearch={{ optionFilterProp: 'label' }}
              style={{ width: '100%' }}
              placeholder="默认订单业务员"
              value={ownerId}
              onChange={setOwnerId}
              options={owners}
              aria-label="负责人"
            />
          </div>
          <div>
            <Label text="下次更新客户时间" />
            <DatePicker
              showTime={{ format: 'HH:mm' }}
              format="YYYY-MM-DD HH:mm"
              value={nextUpdate}
              onChange={setNextUpdate}
              style={{ width: '100%' }}
              aria-label="下次更新客户时间"
            />
          </div>
        </div>
        <Input
          maxLength={300}
          value={nextAction}
          onChange={(e) => setNextAction(e.target.value)}
          placeholder="下一步行动（选填），如 等供应商检测报告"
          aria-label="下一步行动"
        />
        <div style={{ fontSize: 12, color: palette.mute }}>
          S1
          安全或重大风险：先提醒客户停机断电，不等资料齐全。等级与责任分开记录，之后可以调整并留痕。
        </div>
      </Space>
    </Modal>
  );
};

// ---------------------------------------------------------------- 编辑处理

export const PlanModal: React.FC<{
  open: boolean;
  data: AfterSales;
  onClose: () => void;
  onDone: (a: AfterSales) => void;
}> = ({ open, data, onClose, onDone }) => {
  const { message } = App.useApp();
  const reasons = useDictTexts(DICT_REASON);
  const solutions = useDictTexts(DICT_SOLUTION);
  const owners = useOwnerOptions();
  const [level, setLevel] = useState(data.levelCode);
  const [levelNote, setLevelNote] = useState('');
  const [reason, setReason] = useState(data.reasonCode);
  const [sol, setSol] = useState<string[]>(data.solutions);
  const [ownerId, setOwnerId] = useState(data.ownerId);
  const [nextAction, setNextAction] = useState(data.nextAction ?? '');
  const [nextUpdate, setNextUpdate] = useState<Dayjs | null>(
    data.nextUpdateAt ? dayjs(data.nextUpdateAt) : null,
  );
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setLevel(data.levelCode);
    setLevelNote('');
    setReason(data.reasonCode);
    setSol(data.solutions);
    setOwnerId(data.ownerId);
    setNextAction(data.nextAction ?? '');
    setNextUpdate(data.nextUpdateAt ? dayjs(data.nextUpdateAt) : null);
  }, [open, data]);

  const levelChanged = level !== data.levelCode;
  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={620}
      title={`处理 ${data.asNo}`}
      okText="保存"
      okButtonProps={{
        loading: busy,
        disabled: levelChanged && !levelNote.trim(),
      }}
      onOk={async () => {
        setBusy(true);
        try {
          onDone(
            await afterSalesApi.plan(data.id, {
              levelCode: level,
              levelNote: levelNote.trim() || undefined,
              reasonCode: reason,
              solutions: sol,
              ownerId,
              nextAction: nextAction.trim(),
              nextUpdateAt: fmt(nextUpdate) ?? null,
            }),
          );
          message.success('已保存');
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Label text="问题等级" />
          <LevelPicker value={level} onChange={setLevel} />
          {levelChanged && (
            <Input
              style={{ marginTop: 8 }}
              maxLength={200}
              value={levelNote}
              onChange={(e) => setLevelNote(e.target.value)}
              placeholder="调整等级的原因（必填，写入跟进记录）"
              aria-label="调整等级的原因"
            />
          )}
        </div>
        <div>
          <Label text="原因与责任" />
          <Select
            style={{ width: '100%' }}
            value={reason}
            onChange={setReason}
            options={reasons.map((r) => ({ value: r.code, label: r.name }))}
            aria-label="原因与责任"
          />
        </div>
        <div>
          <Label text="处理方案（可多选）" />
          <Checkbox.Group
            value={sol}
            onChange={(v) => setSol(v as string[])}
            options={solutions.map((s) => ({ value: s.code, label: s.name }))}
          />
        </div>
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}
        >
          <div>
            <Label text="负责人" />
            <Select
              showSearch={{ optionFilterProp: 'label' }}
              style={{ width: '100%' }}
              value={ownerId}
              onChange={setOwnerId}
              options={owners}
              aria-label="负责人"
            />
          </div>
          <div>
            <Label text="下次更新客户时间" />
            <DatePicker
              showTime={{ format: 'HH:mm' }}
              format="YYYY-MM-DD HH:mm"
              value={nextUpdate}
              onChange={setNextUpdate}
              style={{ width: '100%' }}
              aria-label="下次更新客户时间"
            />
          </div>
        </div>
        <div>
          <Label text="下一步行动" />
          <Input
            maxLength={300}
            value={nextAction}
            onChange={(e) => setNextAction(e.target.value)}
            aria-label="下一步行动"
          />
        </div>
      </Space>
    </Modal>
  );
};

// ---------------------------------------------------------------- 结案

export const CloseModal: React.FC<{
  open: boolean;
  data: AfterSales;
  onClose: () => void;
  onDone: (a: AfterSales) => void;
}> = ({ open, data, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const reasons = useDictTexts(DICT_REASON);
  const [reason, setReason] = useState<string>();
  const [result, setResult] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) {
      setReason(
        data.reasonCode === 'INVESTIGATING' ? undefined : data.reasonCode,
      );
      setResult('');
    }
  }, [open, data]);
  const total =
    Number(data.refundCny ?? 0) +
    Number(data.expenseCny ?? 0) +
    Number(data.returnFreightCny ?? 0);
  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={560}
      title={`结案 ${data.asNo}`}
      okText="确认结案"
      okButtonProps={{ loading: busy, disabled: !reason || !result.trim() }}
      onOk={async () => {
        if (!reason) return;
        setBusy(true);
        try {
          onDone(await afterSalesApi.close(data.id, reason, result.trim()));
          message.success('已结案');
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Label text="最终原因与责任" required />
          <Select
            style={{ width: '100%' }}
            value={reason}
            onChange={setReason}
            options={reasons
              .filter((r) => r.code !== 'INVESTIGATING')
              .map((r) => ({ value: r.code, label: r.name }))}
            aria-label="最终原因与责任"
          />
        </div>
        <div>
          <Label text="处理结果" required />
          <Input.TextArea
            rows={3}
            maxLength={1000}
            value={result}
            onChange={(e) => setResult(e.target.value)}
            placeholder="如 供应商检测确认编码器故障，换新发出；客户确认运行正常"
            aria-label="处理结果"
          />
        </div>
        <div
          style={{
            padding: '12px 14px',
            borderRadius: 12,
            background: palette.inset,
            fontSize: 13,
            display: 'grid',
            gap: 4,
          }}
        >
          <div>退款 {formatAmount(data.refundCny ?? 0, 'CNY')}</div>
          <div>费用 {formatAmount(data.expenseCny ?? 0, 'CNY')}</div>
          {Number(data.returnCreditCny ?? 0) > 0 && (
            <div style={{ color: palette.green }}>
              退货冲减订单成本 −{formatAmount(data.returnCreditCny ?? 0, 'CNY')}
            </div>
          )}
          <b>已发生 {formatAmount(total, 'CNY')}</b>
        </div>
        <div style={{ fontSize: 12, color: palette.mute }}>
          结案后只能添加跟进记录。有没处理完的退货单或待处理的退款申请时不能结案。
        </div>
      </Space>
    </Modal>
  );
};

// ---------------------------------------------------------------- 发起退货

export const ReturnModal: React.FC<{
  open: boolean;
  data: AfterSales;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, data, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const items = useMemo(
    () => (data.items ?? []).filter((i) => i.soItemId),
    [data],
  );
  const [qty, setQty] = useState<Record<number, number | null>>({});
  const [carrier, setCarrier] = useState('');
  const [trackingNo, setTrackingNo] = useState('');
  const [remark, setRemark] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!open) return;
    const q: Record<number, number | null> = {};
    items.forEach((i) => {
      q[i.soItemId as number] = Math.min(i.quantity, i.shippedQty ?? 0) || null;
    });
    setQty(q);
    setCarrier('');
    setTrackingNo('');
    setRemark('');
  }, [open, items]);
  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={680}
      title={
        <div>
          <div>发起退货</div>
          <div style={{ fontSize: 12, color: palette.mute, fontWeight: 400 }}>
            {data.asNo} · {data.soNo}
          </div>
        </div>
      }
      okText="发起退货"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        const lines = Object.entries(qty)
          .filter(([, v]) => (v ?? 0) > 0)
          .map(([k, v]) => ({ soItemId: Number(k), quantity: v as number }));
        if (lines.length === 0) {
          message.error('请填写退回数量');
          return;
        }
        setBusy(true);
        try {
          const r = await returnApi.create({
            afterSalesId: data.id,
            items: lines,
            carrier: carrier.trim() || undefined,
            trackingNo: trackingNo.trim() || undefined,
            remark: remark.trim() || undefined,
          });
          message.success(`已发起退货 ${r.returnNo}`);
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        <div
          style={{
            padding: '10px 14px',
            borderRadius: 12,
            background: palette.accentSoft,
            fontSize: 13,
            color: palette.sub,
          }}
        >
          客户寄回后，仓库在「退货入库」验收；可退 = 已出运 −
          其他退货单的数量。去向（退回供应商 / 转闲置 /
          报废）由采购员在验收后处理。
        </div>
        {items.length === 0 && (
          <div style={{ color: palette.mute }}>
            售后单没有选订单型号，不能退货
          </div>
        )}
        {items.map((i) => (
          <div
            key={i.id}
            style={{ display: 'flex', alignItems: 'center', gap: 12 }}
          >
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 600, color: palette.ink }}>
                {i.model}
              </div>
              <div style={{ fontSize: 12, color: palette.mute }}>
                已出运 {i.shippedQty ?? 0} 个 · 售后 {i.quantity} 个
              </div>
            </div>
            <InputNumber<number>
              min={0}
              precision={0}
              value={qty[i.soItemId as number] ?? null}
              onChange={(v) =>
                setQty((s) => ({ ...s, [i.soItemId as number]: v }))
              }
              style={{ width: 120 }}
              aria-label={`${i.model} 退回数量`}
            />
          </div>
        ))}
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}
        >
          <Input
            value={carrier}
            maxLength={32}
            onChange={(e) => setCarrier(e.target.value)}
            placeholder="客户寄回快递，如 DHL"
            aria-label="快递公司"
          />
          <Input
            value={trackingNo}
            maxLength={64}
            onChange={(e) => setTrackingNo(e.target.value)}
            placeholder="快递单号（可后补）"
            aria-label="快递单号"
          />
        </div>
        <Input
          value={remark}
          maxLength={300}
          onChange={(e) => setRemark(e.target.value)}
          placeholder="说明（选填），如 供应商 RMA 号"
          aria-label="说明"
        />
      </Space>
    </Modal>
  );
};

// ---------------------------------------------------------------- 申请退款

const METHOD_HINTS: Record<number, string> = {
  1: '退回客户付款的平台',
  2: '财务按银行汇款退回',
  3: '不退现金，留作下一单抵扣',
};

export const RefundRequestModal: React.FC<{
  open: boolean;
  soId: number;
  afterSalesId?: number;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, soId, afterSalesId, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const reasons = useDictTexts(DICT_REFUND_REASON);
  const [info, setInfo] = useState<RequestRefundable>();
  const [amount, setAmount] = useState<number | null>(null);
  const [reason, setReason] = useState<string>();
  const [method, setMethod] = useState(3);
  const [payee, setPayee] = useState('');
  const [remark, setRemark] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!open) return;
    setAmount(null);
    setReason(undefined);
    setPayee('');
    setRemark('');
    setInfo(undefined);
    refundRequestApi
      .refundable(soId)
      .then((r) => {
        setInfo(r);
        setMethod(r.platformOnly ? 1 : 3);
      })
      .catch((e) => message.error(readBizError(e).message));
  }, [open, soId, message]);
  const cur = info?.currencyCode ?? 'USD';
  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={600}
      title="申请退款"
      okText="提交申请"
      okButtonProps={{ loading: busy, disabled: !amount || !reason }}
      onOk={async () => {
        if (!amount || !reason) return;
        setBusy(true);
        try {
          const q = await refundRequestApi.apply({
            soId,
            afterSalesId,
            amount,
            reason,
            method,
            payeeInfo: payee.trim() || undefined,
            remark: remark.trim() || undefined,
          });
          message.success(`已提交 ${q.requestNo}（${q.statusName}）`);
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        {info && (
          <div
            style={{
              padding: '12px 14px',
              borderRadius: 12,
              background: palette.inset,
              fontSize: 13,
              display: 'grid',
              gap: 4,
            }}
          >
            <div>
              {info.soNo} · 已到账 {formatAmount(info.received, cur)}
            </div>
            <div style={{ color: palette.sub }}>
              已退款 {formatAmount(info.refunded, cur)} · 待处理申请{' '}
              {formatAmount(info.pending, cur)}
            </div>
            <b style={{ color: palette.green }}>
              最多还能申请 {formatAmount(info.available, cur)}
            </b>
          </div>
        )}
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}
        >
          <div>
            <Label text={`金额（${cur}）`} required />
            <InputNumber<number>
              min={0.01}
              max={info?.available}
              precision={2}
              value={amount}
              onChange={setAmount}
              style={{ width: '100%' }}
              aria-label="申请金额"
            />
          </div>
          <div>
            <Label text="原因" required />
            <Select
              style={{ width: '100%' }}
              value={reason}
              onChange={setReason}
              options={reasons.map((r) => ({ value: r.code, label: r.name }))}
              aria-label="原因"
            />
          </div>
        </div>
        <div>
          <Label text="建议去向" />
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              gap: 10,
            }}
          >
            {REFUND_METHODS.map((m) => {
              const disabled = !!info?.platformOnly && m.value !== 1;
              const on = method === m.value;
              return (
                <button
                  key={m.value}
                  type="button"
                  aria-pressed={on}
                  disabled={disabled}
                  onClick={() => setMethod(m.value)}
                  style={{
                    textAlign: 'left',
                    padding: '10px 12px',
                    borderRadius: 12,
                    cursor: disabled ? 'not-allowed' : 'pointer',
                    opacity: disabled ? 0.45 : 1,
                    background: on ? palette.accentSoft : palette.inset,
                    border: `${on ? 1.5 : 1}px solid ${on ? palette.link : palette.hairline}`,
                    color: palette.ink,
                    font: 'inherit',
                  }}
                >
                  <div style={{ fontWeight: 600 }}>{m.label}</div>
                  <div style={{ fontSize: 12, color: palette.mute }}>
                    {disabled ? '平台收款的订单不可用' : METHOD_HINTS[m.value]}
                  </div>
                </button>
              );
            })}
          </div>
        </div>
        <Input
          value={payee}
          maxLength={300}
          onChange={(e) => setPayee(e.target.value)}
          placeholder="客户收款信息（银行汇款时填写）"
          aria-label="客户收款信息"
        />
        <Input
          value={remark}
          maxLength={500}
          onChange={(e) => setRemark(e.target.value)}
          placeholder="说明（选填），如 客户同意下一单抵扣"
          aria-label="说明"
        />
        <div style={{ fontSize: 12, color: palette.mute }}>
          {info?.approvalRequired
            ? '系统设置打开了审批：提交后先由审批人通过，再交财务处理。'
            : '提交后交财务在「退款管理 → 退款申请」执行。'}
        </div>
      </Space>
    </Modal>
  );
};
