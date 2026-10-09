import {
  CheckOutlined,
  FileTextOutlined,
  InfoCircleOutlined,
  PaperClipOutlined,
  RocketOutlined,
} from '@ant-design/icons';
import {
  App,
  AutoComplete,
  Button,
  Checkbox,
  DatePicker,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Radio,
  Upload,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import { type Attachment, attachmentApi } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import {
  BoxEditor,
  boxesProblem,
  emptyBox,
  type PackLine,
} from '../components';
import {
  type BoxInput,
  type DirectRef,
  type Logistics,
  logisticsApi,
  type PendingGroup,
  readBizError,
} from '../service';

const Label: React.FC<{ children: React.ReactNode; required?: boolean }> = ({
  children,
  required,
}) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ color: palette.sub, marginBottom: 6 }}>
      {children}
      {required && <span style={{ color: palette.red }}> *</span>}
    </div>
  );
};

// ---------------------------------------------------------------- 直发确认实收

export const ConfirmDirectDrawer: React.FC<{
  sh: Logistics;
  direct?: DirectRef;
  onClose: () => void;
  onDone: (sh: Logistics) => void;
}> = ({ sh, direct, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [received, setReceived] = useState<Record<number, number | null>>({});
  const [boxes, setBoxes] = useState<BoxInput[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!direct) return;
    setReceived(
      Object.fromEntries(
        direct.items.map((i) => [i.shipmentItemId, i.quantity]),
      ),
    );
    const lines = direct.items.map((i) => ({
      key: i.shipmentItemId,
      model: i.model,
      quantity: i.quantity,
    }));
    setBoxes([emptyBox(lines, new Map())]);
  }, [direct]);

  const lines: PackLine[] = useMemo(
    () =>
      (direct?.items ?? [])
        .map((i) => ({
          key: i.shipmentItemId,
          model: i.model,
          quantity: received[i.shipmentItemId] ?? 0,
        }))
        .filter((l) => l.quantity > 0),
    [direct, received],
  );
  const short = (direct?.items ?? []).filter(
    (i) => (received[i.shipmentItemId] ?? 0) < i.quantity,
  );
  const problem = !direct
    ? '加载中'
    : direct.items.some((i) => (received[i.shipmentItemId] ?? 0) > i.quantity)
      ? '实收不能多于发货数量'
      : lines.length === 0
        ? '至少要实收一个型号'
        : boxesProblem(lines, boxes);

  const submit = async () => {
    if (!direct) return;
    setBusy(true);
    try {
      const next = await logisticsApi.confirmDirect(
        sh.id,
        direct.id,
        direct.items.map((i) => ({
          shipmentItemId: i.shipmentItemId,
          quantity: received[i.shipmentItemId] ?? 0,
        })),
        boxes.map((b) => ({
          ...b,
          items: b.items.filter((i) => lines.some((l) => l.key === i.key)),
        })),
      );
      message.success(`${direct.sdNo} 已确认实收`);
      onDone(next);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Drawer
      open={!!direct}
      onClose={onClose}
      size="min(860px, 96vw)"
      destroyOnHidden
      title={
        <span>
          确认实收{direct ? ` · ${direct.sdNo}` : ''}
          {direct && (
            <span
              style={{
                marginLeft: 10,
                fontSize: 13,
                fontWeight: 400,
                color: palette.mute,
              }}
            >
              {direct.poNo} · {direct.supplierName} ·{' '}
              {[direct.carrier, direct.trackingNo].filter(Boolean).join(' ')}
            </span>
          )}
        </span>
      }
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{
              fontSize: 12,
              color: problem ? palette.orange : palette.mute,
            }}
          >
            <InfoCircleOutlined />{' '}
            {problem ||
              `按货代实收补入库单与出库单；体积重按货代系数 ${sh.volumeDivisor} 算`}
          </span>
          <span style={{ flex: 1 }} />
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            icon={<CheckOutlined />}
            loading={busy}
            disabled={!!problem}
            onClick={submit}
          >
            确认实收
          </Button>
        </div>
      }
    >
      {direct && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div>
            <b style={{ color: palette.ink }}>货代实收</b>
            {direct.items.map((i) => (
              <div
                key={i.shipmentItemId}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '8px 0',
                }}
              >
                <span style={{ flex: 1, color: palette.ink }}>
                  {i.model}
                  {i.brand && (
                    <span style={{ color: palette.mute }}> · {i.brand}</span>
                  )}
                </span>
                <span style={{ fontSize: 12, color: palette.mute }}>
                  发货 {i.quantity} · 实收
                </span>
                <InputNumber
                  value={received[i.shipmentItemId]}
                  min={0}
                  max={i.quantity}
                  precision={0}
                  onChange={(v) =>
                    setReceived((m) => ({
                      ...m,
                      [i.shipmentItemId]: v === null ? null : Number(v),
                    }))
                  }
                  style={{ width: 90 }}
                  aria-label={`${i.model} 实收`}
                />
              </div>
            ))}
            {short.length > 0 && (
              <div style={{ fontSize: 12, color: palette.orange }}>
                {short
                  .map(
                    (i) =>
                      `${i.model} 少收 ${i.quantity - (received[i.shipmentItemId] ?? 0)}`,
                  )
                  .join('、')}
                ，确认后生成少发差异，由采购员处理
              </div>
            )}
          </div>
          {lines.length > 0 && (
            <BoxEditor
              lines={lines}
              boxes={boxes}
              onChange={setBoxes}
              divisor={sh.volumeDivisor}
            />
          )}
        </div>
      )}
    </Drawer>
  );
};

// ---------------------------------------------------------------- 生成 CI / PL

export const DocsModal: React.FC<{
  sh: Logistics;
  open: boolean;
  onClose: () => void;
  onDone: (sh: Logistics) => void;
}> = ({ sh, open, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const multi = sh.orders.length > 1;
  const [mode, setMode] = useState<'MERGED' | 'PER_ORDER'>('MERGED');
  const [paymentRef, setPaymentRef] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    const groups = sh.docGroups ?? [];
    setMode(groups.length > 1 ? 'PER_ORDER' : 'MERGED');
    setPaymentRef(groups[0]?.paymentRef ?? '');
  }, [open, sh.docGroups]);

  const submit = async () => {
    setBusy(true);
    try {
      const next = await logisticsApi.generateDocs(
        sh.id,
        multi ? mode : 'MERGED',
        paymentRef.trim() || undefined,
      );
      message.success('CI / PL 已生成，可以下载');
      onDone(next);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const option = (
    value: 'MERGED' | 'PER_ORDER',
    title: string,
    desc: string,
  ) => (
    <Radio
      value={value}
      style={{
        display: 'flex',
        padding: '12px 14px',
        borderRadius: 10,
        border: `1px solid ${mode === value ? palette.accentLine : palette.hairline}`,
        background: mode === value ? palette.accentSoft : undefined,
        marginBottom: 10,
      }}
    >
      <b style={{ color: palette.ink }}>{title}</b>
      <div style={{ fontSize: 12, color: palette.mute }}>{desc}</div>
    </Radio>
  );

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={620}
      destroyOnHidden
      title={
        <span>
          <FileTextOutlined /> 生成 CI / PL
        </span>
      }
      footer={
        <>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            icon={<FileTextOutlined />}
            loading={busy}
            onClick={submit}
          >
            生成
          </Button>
        </>
      }
    >
      <div style={{ color: palette.sub, marginBottom: 14 }}>
        {sh.shNo} 含 {sh.orders.length} 张订单的货
      </div>
      {multi && (
        <Radio.Group
          value={mode}
          onChange={(e) => setMode(e.target.value)}
          style={{ width: '100%' }}
        >
          {option(
            'MERGED',
            '合成一组',
            '一张 CI、一张 PL 列出几张订单的型号与全部箱子（币种需相同）',
          )}
          {option(
            'PER_ORDER',
            '每张订单各一组',
            `${sh.orders.map((o) => o.soNo).join('、')} 各一组，编号各不相同`,
          )}
        </Radio.Group>
      )}
      <Label>付款参考（选填，写进 CI）</Label>
      <Input
        value={paymentRef}
        onChange={(e) => setPaymentRef(e.target.value)}
        maxLength={200}
        placeholder="如 TT No.000126150063 dated 10.10.2026"
        aria-label="付款参考"
      />
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 14 }}>
        <InfoCircleOutlined /> 用「业务设置 → 单据模版」里的默认 CI、PL 模版。CI
        取订单的型号、英文描述、HS 编码、单价与费用行；PL
        取装箱记录，箱的尺寸重量写在箱内第一行并合并单元格。重新生成时同一组订单编号不变。
      </div>
    </Modal>
  );
};

// ---------------------------------------------------------------- 面单

const ACCEPT_FACE = 'application/pdf,image/jpeg,image/png,image/webp';

export const FaceSheets: React.FC<{
  value: Attachment[];
  onChange?: (list: Attachment[]) => void;
}> = ({ value, onChange }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [busy, setBusy] = useState(0);
  const open = async (a: Attachment) => {
    try {
      const url = await attachmentApi.blobUrl(a.id);
      window.open(url, '_blank', 'noopener');
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      {value.map((a) => (
        <div
          key={a.id}
          style={{ display: 'flex', alignItems: 'center', gap: 8 }}
        >
          <PaperClipOutlined style={{ color: palette.mute }} />
          <a onClick={() => open(a)}>{a.fileName}</a>
          {onChange && (
            <a
              style={{ color: palette.red, fontSize: 12 }}
              onClick={() => onChange(value.filter((x) => x.id !== a.id))}
            >
              移除
            </a>
          )}
        </div>
      ))}
      {onChange && value.length < 10 && (
        <Upload
          accept={ACCEPT_FACE}
          multiple
          showUploadList={false}
          beforeUpload={(file) => {
            if (file.size > 10 * 1024 * 1024) {
              message.error(`${file.name}：面单不能超过 10MB`);
              return Upload.LIST_IGNORE;
            }
            setBusy((n) => n + 1);
            attachmentApi
              .upload('LOGISTICS', file)
              .then((a) => onChange([...value, a]))
              .catch((e) =>
                message.error(`${file.name}：${readBizError(e).message}`),
              )
              .finally(() => setBusy((n) => n - 1));
            return false;
          }}
        >
          <Button size="small" icon={<PaperClipOutlined />} loading={busy > 0}>
            上传面单（PDF / 图片）
          </Button>
        </Upload>
      )}
      {!onChange && value.length === 0 && (
        <span style={{ color: palette.mute }}>—</span>
      )}
    </div>
  );
};

// ---------------------------------------------------------------- 登记出运 / 修改运单

export const ShipModal: React.FC<{
  sh: Logistics;
  open: boolean;
  onClose: () => void;
  onDone: (sh: Logistics) => void;
}> = ({ sh, open, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const update = sh.status === 2;
  const [carrier, setCarrier] = useState('');
  const [waybillNo, setWaybillNo] = useState('');
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [freight, setFreight] = useState<number | null>(null);
  const [files, setFiles] = useState<Attachment[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setCarrier(sh.carrier || 'DHL');
    setWaybillNo(sh.waybillNo ?? '');
    setDate(sh.shippedDate ? dayjs(sh.shippedDate) : dayjs());
    setFreight(sh.freight ?? null);
    setFiles(sh.faceSheets ?? []);
  }, [open, sh]);

  const submit = async () => {
    setBusy(true);
    try {
      const next = await logisticsApi.ship(
        sh.id,
        {
          carrier: carrier.trim(),
          waybillNo: waybillNo.trim(),
          shippedDate: date.format('YYYY-MM-DD'),
          freight: freight ?? 0,
          attachmentIds: files.map((f) => f.id),
        },
        update,
      );
      message.success(update ? '已保存' : `${sh.shNo} 已出运`);
      onDone(next);
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
      width={640}
      destroyOnHidden
      title={
        <span>
          <RocketOutlined /> {update ? '修改运单与运费' : '登记出运'} ·{' '}
          {sh.shNo}
        </span>
      }
      footer={
        <>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            icon={<RocketOutlined />}
            loading={busy}
            disabled={!carrier.trim() || !waybillNo.trim() || freight === null}
            onClick={submit}
          >
            {update ? '保存' : '确认出运'}
          </Button>
        </>
      }
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: 12,
          marginBottom: 12,
        }}
      >
        <div>
          <Label required>承运商</Label>
          <AutoComplete
            value={carrier}
            onChange={setCarrier}
            options={['DHL', 'FedEx', 'UPS', 'TNT', 'EMS', '海运', '空运'].map(
              (value) => ({ value }),
            )}
            style={{ width: '100%' }}
            aria-label="承运商"
          />
        </div>
        <div>
          <Label required>运单号</Label>
          <Input
            value={waybillNo}
            onChange={(e) => setWaybillNo(e.target.value)}
            maxLength={64}
            aria-label="运单号"
          />
        </div>
        <div>
          <Label required>出运日期</Label>
          <DatePicker
            value={date}
            allowClear={false}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(d) => d && setDate(d)}
            style={{ width: '100%' }}
            aria-label="出运日期"
          />
        </div>
        <div>
          <Label required>实际运费（CNY）</Label>
          <InputNumber
            value={freight}
            min={0}
            precision={2}
            prefix="¥"
            disabled={sh.reconciled}
            onChange={(v) => setFreight(v === null ? null : Number(v))}
            style={{ width: '100%' }}
            aria-label="实际运费"
          />
        </div>
      </div>
      <Label>面单</Label>
      <FaceSheets value={files} onChange={setFiles} />
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 14 }}>
        <InfoCircleOutlined />{' '}
        {sh.reconciled
          ? '已对账，运费不能再改。'
          : `运费按计费重分到箱（体积系数 ${sh.volumeDivisor}），再按货值分到型号行；确认后订单型号变「已出运」。月底与货代对账时可以按账单调整。`}
      </div>
    </Modal>
  );
};

// ---------------------------------------------------------------- 调整货物

export const ItemsModal: React.FC<{
  sh: Logistics;
  open: boolean;
  onClose: () => void;
  onDone: (sh: Logistics) => void;
}> = ({ sh, open, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [extra, setExtra] = useState<PendingGroup>();
  const [obs, setObs] = useState<Set<number>>(new Set());
  const [ds, setDs] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setObs(
      new Set(
        (sh.outbounds ?? []).filter((o) => o.source === 1).map((o) => o.id),
      ),
    );
    setDs(new Set((sh.directs ?? []).map((d) => d.id)));
    logisticsApi
      .pending()
      .then((g) =>
        setExtra(
          g.find(
            (x) =>
              x.customerId === sh.customerId &&
              x.forwarderId === sh.forwarderId,
          ),
        ),
      )
      .catch(() => setExtra(undefined));
  }, [open, sh]);

  const ownObs = (sh.outbounds ?? []).filter((o) => o.source === 1);
  const allObs = [...ownObs, ...(extra?.outbounds ?? [])];
  const allDs = [...(sh.directs ?? []), ...(extra?.directs ?? [])];
  const flip = (set: Set<number>, id: number, on: boolean) => {
    const n = new Set(set);
    if (on) n.add(id);
    else n.delete(id);
    return n;
  };

  const submit = async () => {
    setBusy(true);
    try {
      const directObs = (sh.outbounds ?? [])
        .filter((o) => o.source === 2)
        .map((o) => o.id);
      const next = await logisticsApi.updateItems(sh.id, {
        outboundIds: [...obs, ...directObs],
        directShipmentIds: [...ds],
      });
      message.success('已调整');
      onDone(next);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const row = (
    key: string,
    checked: boolean,
    onChange: (on: boolean) => void,
    title: string,
    desc: string,
  ) => (
    <div
      key={key}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '6px 0',
      }}
    >
      <Checkbox
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        aria-label={`选择 ${title}`}
      />
      <b style={{ color: palette.ink }}>{title}</b>
      <span style={{ fontSize: 12, color: palette.mute }}>{desc}</span>
    </div>
  );

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={620}
      destroyOnHidden
      title={`调整货物 · ${sh.shNo}`}
      footer={
        <>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            loading={busy}
            disabled={obs.size + ds.size === 0}
            onClick={submit}
          >
            保存
          </Button>
        </>
      }
    >
      <div style={{ color: palette.sub, marginBottom: 10 }}>
        只能放 {sh.customerName}、交给 {sh.forwarderName}{' '}
        的货；去掉勾选的回到「待出运的货」。
      </div>
      {allObs.map((o) =>
        row(
          `ob-${o.id}`,
          obs.has(o.id),
          (on) => setObs((s) => flip(s, o.id, on)),
          o.obNo,
          `${o.soNo} · ${o.boxCount} 箱 · ${o.items.map((i) => `${i.model} × ${i.quantity}`).join('、')}`,
        ),
      )}
      {allDs.map((d) =>
        row(
          `sd-${d.id}`,
          ds.has(d.id),
          (on) => setDs((s) => flip(s, d.id, on)),
          d.sdNo,
          `直发货代 · ${d.confirmed ? '已确认实收' : '待确认实收'} · ${d.items.map((i) => `${i.model} × ${i.quantity}`).join('、')}`,
        ),
      )}
    </Modal>
  );
};
