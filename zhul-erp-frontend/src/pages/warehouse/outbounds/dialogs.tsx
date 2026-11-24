import { useModel } from '@umijs/max';
import {
  App,
  AutoComplete,
  Button,
  DatePicker,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Skeleton,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Car, Check, Info } from 'lucide-react';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  BoxEditor,
  boxesProblem,
  emptyBox,
  kg,
  type PackLine,
} from '@/pages/logistics/components';
import {
  type BoxInput,
  type Outbound,
  outboundApi,
  readBizError,
} from '@/pages/logistics/service';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { CARRIERS } from '@/pages/purchase/shipments/ShipmentDrawer';
import {
  ACCEPT_IMAGE,
  AttachmentWall,
  attachmentModels,
  type ModelTagMap,
  ModelTags,
} from '@/pages/warehouse/components';
import { showStockShortage } from '@/pages/warehouse/inventoryParts';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';

/** 已有装箱记录转成可编辑的箱子 */
export const toInputs = (ob: Outbound): BoxInput[] =>
  ob.boxes.map((b) => ({
    length: b.length,
    width: b.width,
    height: b.height,
    grossWeight: b.grossWeight,
    netWeight: b.netWeight ?? null,
    items: b.items.map((i) => ({
      key: i.outboundItemId,
      quantity: i.quantity,
    })),
  }));

// ---------------------------------------------------------------- 打包

/** 装箱的型号行；几张单一起装（合并打包或修改整组装箱）时每行带所属出库单 */
export const packLines = (obs: Outbound[]): PackLine[] =>
  obs.flatMap((o) =>
    o.items.map((i) => ({
      key: i.id,
      model: i.model,
      quantity: i.quantity,
      group: obs.length > 1 ? { no: o.obNo, sub: o.soNo } : undefined,
    })),
  );

export const PackDrawer: React.FC<{
  /** 打包或修改装箱的单；合并打包了的单打开整组 */
  id?: number;
  /** 合并打包：同一客户、同一货代的几张待打包销售出库单 */
  mergeIds?: number[];
  onClose: () => void;
  /** 打包完成；packedId 是完成的那张单（可能已不是抽屉当前的单） */
  onDone: (packedId: number) => void;
}> = ({ id, mergeIds, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const key = mergeIds?.join(',') ?? (id === undefined ? undefined : `${id}`);
  /** 抽屉当前打开的单：保存进行中换了单，旧单的结果不能动新单的按钮状态 */
  const current = useRef(key);
  current.current = key;
  const [loaded, setLoaded] = useState<{ key: string; obs: Outbound[] }>();
  const [error, setError] = useState<string>();
  const [boxes, setBoxes] = useState<BoxInput[]>([]);
  const [busy, setBusy] = useState(false);
  const [photos, setPhotos] = useState<Attachment[]>([]);
  const [tags, setTags] = useState<ModelTagMap>({});

  useEffect(() => {
    if (key === undefined) return;
    // 关闭或换单后，上一单迟到的回包作废，不能改写当前抽屉（UI-017）
    let alive = true;
    setLoaded(undefined);
    setTags({});
    setError(undefined);
    setBusy(false);
    const ids = key.split(',').map(Number);
    (async () => {
      const opened = await Promise.all(ids.map((x) => outboundApi.detail(x)));
      // 合并打包了的单：同组的单一起装箱
      const mates = opened.length === 1 ? (opened[0].packMates ?? []) : [];
      const rest = await Promise.all(
        mates.map((m) => outboundApi.detail(m.id)),
      );
      return [...opened, ...rest].sort((a, b) => a.id - b.id);
    })()
      .then((obs) => {
        if (!alive) return;
        const head = obs.find((o) => o.id === ids[0]) ?? obs[0];
        setLoaded({ key, obs });
        setPhotos(head.packingAttachments ?? []);
        setBoxes(
          head.boxes.length
            ? toInputs(head)
            : [emptyBox(packLines(obs), new Map())],
        );
      })
      .catch((e) => {
        if (alive) setError(readBizError(e).message);
      });
    return () => {
      alive = false;
    };
  }, [key]);

  const obs = loaded && loaded.key === key ? loaded.obs : undefined;
  const head = obs?.find((o) => o.id === (mergeIds?.[0] ?? id)) ?? obs?.[0];
  const many = (obs?.length ?? 0) > 1;
  const lines: PackLine[] = useMemo(() => (obs ? packLines(obs) : []), [obs]);
  const problem = obs ? boxesProblem(lines, boxes) : '加载中';
  const missing = [
    ...new Set(obs?.flatMap((o) => o.missingMarketing ?? []) ?? []),
  ];

  const submit = async () => {
    // 只提交本次打开的单据
    if (!obs || !head || key === undefined) return;
    const target = key;
    const files = {
      attachmentIds: photos.map((p) => p.id),
      attachmentModels: attachmentModels(photos, tags),
    };
    setBusy(true);
    try {
      if (mergeIds) {
        await outboundApi.mergePack(mergeIds, boxes, files);
        message.success(`${mergeIds.length} 张销售出库单已合并打包`);
      } else {
        await outboundApi.pack(head.id, boxes, files);
        message.success(many ? '已修改整组装箱' : `${head.obNo} 已打包`);
      }
      onDone(head.id);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      if (current.current === target) setBusy(false);
    }
  };

  const title = mergeIds
    ? `合并打包 · ${mergeIds.length} 张销售出库单`
    : !head
      ? '打包'
      : many
        ? `修改装箱 · 合并打包 ${obs?.length} 张`
        : `打包 · ${head.obNo}`;

  return (
    <Drawer
      open={key !== undefined}
      onClose={onClose}
      size="min(860px, 96vw)"
      destroyOnHidden
      title={
        <span>
          {title}
          {head && (
            <span
              style={{
                marginLeft: 10,
                fontSize: 13,
                fontWeight: 400,
                color: palette.mute,
              }}
            >
              {many ? '' : `${head.soNo} · `}
              {head.customerName} · 货代 {head.forwarderName}
            </span>
          )}
        </span>
      }
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{
              fontSize: 12,
              color: problem && obs ? palette.orange : palette.mute,
            }}
          >
            <Info size="1em" />{' '}
            {problem && obs
              ? problem
              : '体积重 = 长 × 宽 × 高 ÷ 5000；计费重取毛重和体积重的大者'}
          </span>
          <span style={{ flex: 1 }} />
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            icon={<Check size="1em" />}
            loading={busy}
            disabled={!!problem}
            onClick={submit}
          >
            {mergeIds ? '完成合并打包' : '完成打包'}
          </Button>
        </div>
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={onClose} />
      ) : !obs ? (
        <Skeleton active />
      ) : (
        <>
          {obs
            .filter((o) => o.note)
            .map((o) => (
              <div key={o.id} style={{ marginBottom: 12, color: palette.sub }}>
                {many ? `${o.obNo} ` : ''}业务员备注：{o.note}
              </div>
            ))}
          {missing.length > 0 && (
            <div
              style={{
                marginBottom: 12,
                padding: '8px 12px',
                borderRadius: 10,
                background: palette.orangeSoft,
                color: palette.orange,
                fontSize: 13,
              }}
            >
              <Info size="1em" /> 以下型号还没有营销实拍：
              {missing.join('、')}
              。货发走前可以请拍摄人员补拍，不影响打包。
            </div>
          )}
          <BoxEditor lines={lines} boxes={boxes} onChange={setBoxes} />
          {many && (
            <div
              style={{
                marginTop: 12,
                padding: '8px 12px',
                borderRadius: 10,
                background: palette.orangeSoft,
                color: palette.orange,
                fontSize: 13,
              }}
            >
              <Info size="1em" /> 这 {obs.length}{' '}
              张单共用这些箱子，要一起交快递、一起放进出运单；需要分开时在销售出库单详情里「拆开」。
            </div>
          )}
          <div
            style={{
              margin: '18px 0 8px',
              color: palette.ink,
              fontWeight: 600,
            }}
          >
            打包发货照片
            <span
              style={{
                marginLeft: 8,
                fontSize: 12,
                fontWeight: 400,
                color: palette.mute,
              }}
            >
              选填；发给客户前的打包照片，进素材库「打包发货」，可以发给客户
            </span>
          </div>
          <AttachmentWall
            ownerType="PACKING"
            value={photos}
            onChange={setPhotos}
            accept={ACCEPT_IMAGE}
          />
          <ModelTags
            files={photos}
            models={obs.flatMap((o) => o.items)}
            value={tags}
            onChange={setTags}
          />
        </>
      )}
    </Drawer>
  );
};

// ---------------------------------------------------------------- 交国内快递

/** 按计费重分摊，两位小数，尾差给计费重最大的一张（与后端一致） */
const splitFreight = (freight: number, weights: number[]) => {
  const total = weights.reduce((a, b) => a + b, 0);
  if (!total || !freight) return weights.map(() => 0);
  const shares = weights.map(
    (w) => Math.round((freight * w * 100) / total) / 100,
  );
  const diff =
    Math.round((freight - shares.reduce((a, b) => a + b, 0)) * 100) / 100;
  const max = weights.indexOf(Math.max(...weights));
  shares[max] = Math.round((shares[max] + diff) * 100) / 100;
  return shares;
};

export const HandOverModal: React.FC<{
  open: boolean;
  outbounds: Outbound[];
  onClose: () => void;
  onDone: () => void;
}> = ({ open, outbounds, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const { initialState } = useModel('@@initialState');
  const [carrier, setCarrier] = useState('顺丰');
  const [trackingNo, setTrackingNo] = useState('');
  const [sentDate, setSentDate] = useState<Dayjs>(dayjs());
  const [freight, setFreight] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) {
      setTrackingNo('');
      setSentDate(dayjs());
      setFreight(null);
    }
  }, [open]);

  // 合并打包的同组单共用箱子，箱数和计费重按组只算一次（合并单元格）
  const unitOf = (o: Outbound) => (o.packId ? `p${o.packId}` : `o${o.id}`);
  const units = [...new Set(outbounds.map(unitOf))];
  const rows = units.flatMap((u) => outbounds.filter((o) => unitOf(o) === u));
  const weights = units.map(
    (u) => rows.find((o) => unitOf(o) === u)?.chargeableWeight ?? 0,
  );
  const shares = splitFreight(freight ?? 0, weights);
  const span = (r: Outbound, i = 0) => ({
    rowSpan:
      rows.findIndex((o) => unitOf(o) === unitOf(r)) === i
        ? rows.filter((o) => unitOf(o) === unitOf(r)).length
        : 0,
  });
  const forwarder = outbounds[0]?.forwarderName;

  const submit = async () => {
    setBusy(true);
    try {
      await outboundApi.handOver({
        outboundIds: outbounds.map((o) => o.id),
        carrier: carrier.trim(),
        trackingNo: trackingNo.trim() || undefined,
        sentDate: sentDate.format('YYYY-MM-DD'),
        freight: freight ?? 0,
      });
      message.success('已交货代');
      onDone();
    } catch (e) {
      const err = readBizError(e);
      if (!showStockShortage(modal, err, '交货代')) message.error(err.message);
    } finally {
      setBusy(false);
    }
  };

  const field = (label: React.ReactNode, node: React.ReactNode) => (
    <div>
      <div style={{ color: palette.sub, marginBottom: 6 }}>{label}</div>
      {node}
    </div>
  );

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={720}
      destroyOnHidden
      title={
        <span>
          <Car size="1em" /> 交国内快递{forwarder ? ` · ${forwarder}` : ''}
        </span>
      }
      footer={
        <>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            icon={<Car size="1em" />}
            loading={busy}
            disabled={!carrier.trim() || freight === null}
            onClick={submit}
          >
            确认交快递
          </Button>
        </>
      }
    >
      <div style={{ color: palette.sub, marginBottom: 16 }}>
        {outbounds.length > 1
          ? `同一家货代的 ${outbounds.length} 张销售出库单合成一票寄出，运费按计费重分摊`
          : '一张销售出库单单独寄出'}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: 12,
          marginBottom: 12,
        }}
      >
        {field(
          '快递公司',
          <AutoComplete
            value={carrier}
            options={CARRIERS.filter((c) => c.value !== '供应商送货')}
            onChange={setCarrier}
            style={{ width: '100%' }}
            aria-label="快递公司"
          />,
        )}
        {field(
          '快递单号',
          <Input
            value={trackingNo}
            onChange={(e) => setTrackingNo(e.target.value)}
            maxLength={64}
            aria-label="快递单号"
          />,
        )}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr 1fr',
          gap: 12,
          marginBottom: 16,
        }}
      >
        {field(
          '发出日期',
          <DatePicker
            value={sentDate}
            allowClear={false}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(d) => d && setSentDate(d)}
            style={{ width: '100%' }}
            aria-label="发出日期"
          />,
        )}
        {field(
          <span>
            运费 <span style={{ color: palette.red }}>*</span>
          </span>,
          <InputNumber
            value={freight}
            min={0}
            precision={2}
            prefix="CNY"
            onChange={(v) => setFreight(v === null ? null : Number(v))}
            style={{ width: '100%' }}
            aria-label="运费"
          />,
        )}
        {field(
          '垫付人',
          <Input
            value={initialState?.currentUser?.name ?? '我'}
            disabled
            aria-label="垫付人"
          />,
        )}
      </div>
      <Table<Outbound>
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={rows}
        columns={[
          { title: '销售出库单', dataIndex: 'obNo' },
          { title: '订单', dataIndex: 'soNo' },
          { title: '箱数', dataIndex: 'boxCount', width: 70, onCell: span },
          {
            title: '计费重',
            dataIndex: 'chargeableWeight',
            render: (v: number) => kg(v),
            onCell: span,
          },
          {
            title: '分到运费',
            align: 'right',
            key: 'share',
            onCell: span,
            render: (_, r) =>
              freight === null
                ? '—'
                : formatAmount(shares[units.indexOf(unitOf(r))], 'CNY'),
          },
        ]}
      />
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 12 }}>
        <Info size="1em" />{' '}
        登记后销售出库单为「已交货代」，订单型号自动推进；运费由垫付人以后走报销。
      </div>
    </Modal>
  );
};
