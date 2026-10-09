import { InfoCircleOutlined, SendOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import {
  App,
  Button,
  Checkbox,
  Input,
  InputNumber,
  Modal,
  Select,
  Skeleton,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { ObStatusPill } from '@/pages/logistics/components';
import {
  type Forwarder,
  forwarderApi,
  type NoticeForm,
  type Outbound,
  outboundApi,
  readBizError,
} from '@/pages/logistics/service';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { ReasonModal } from '@/pages/warehouse/components';
import { OutboundDetail } from '@/pages/warehouse/outbounds/dialogs';
import { useAppTheme } from '@/theme/AppTheme';
import { Card } from '../components';

interface Line {
  checked: boolean;
  quantity: number | null;
}

/** 发货通知：勾选可出库的型号与数量、选货代；outbound 传入时为修改待打包的通知 */
export const ShipNoticeModal: React.FC<{
  open: boolean;
  soId: number;
  outbound?: Outbound;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, soId, outbound, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [form, setForm] = useState<NoticeForm>();
  const [forwarders, setForwarders] = useState<Forwarder[]>([]);
  const [error, setError] = useState<string>();
  const [forwarderId, setForwarderId] = useState<number>();
  const [note, setNote] = useState('');
  const [lines, setLines] = useState<Record<number, Line>>({});
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setForm(undefined);
    setError(undefined);
    try {
      const [f, fw] = await Promise.all([
        outboundApi.noticeForm(
          outbound ? { outboundId: outbound.id } : { soId },
        ),
        forwarderApi.list(),
      ]);
      setForm(f);
      setForwarders(fw);
      setForwarderId(
        outbound?.forwarderId ?? (fw.length === 1 ? fw[0].id : undefined),
      );
      setNote(outbound?.note ?? '');
      const init: Record<number, Line> = {};
      for (const l of f.lines) {
        init[l.soItemId] = outbound
          ? { checked: l.current > 0, quantity: l.current || null }
          : { checked: l.available > 0, quantity: l.available || null };
      }
      setLines(init);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [soId, outbound]);

  useEffect(() => {
    if (open) load();
  }, [open, load]);

  const chosen = (form?.lines ?? []).filter(
    (l) => lines[l.soItemId]?.checked && (lines[l.soItemId]?.quantity ?? 0) > 0,
  );
  const over = chosen.find(
    (l) => (lines[l.soItemId]?.quantity ?? 0) > l.available,
  );

  const submit = async () => {
    if (!forwarderId) return;
    setBusy(true);
    const body = {
      forwarderId,
      note: note.trim() || undefined,
      items: chosen.map((l) => ({
        soItemId: l.soItemId,
        quantity: lines[l.soItemId].quantity as number,
      })),
    };
    try {
      const ob = outbound
        ? await outboundApi.updateNotice(outbound.id, body)
        : await outboundApi.createNotice({ soId, ...body });
      message.success(
        outbound ? `${ob.obNo} 已修改` : `已通知仓库打包：${ob.obNo}`,
      );
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const setLine = (id: number, patch: Partial<Line>) =>
    setLines((m) => ({ ...m, [id]: { ...m[id], ...patch } }));

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={720}
      destroyOnHidden
      title={
        <span>
          <SendOutlined />{' '}
          {outbound ? `修改发货通知 · ${outbound.obNo}` : '发货通知'}
          {form ? ` · ${form.soNo}` : ''}
        </span>
      }
      footer={
        <>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={busy}
            disabled={!form || !forwarderId || chosen.length === 0 || !!over}
            onClick={submit}
          >
            {outbound ? '保存' : '发通知'}
          </Button>
        </>
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !form ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ color: palette.sub }}>
            {form.customerName} · 勾选已入库的型号，仓库收到后打包发往货代
          </div>
          <div>
            <div style={{ color: palette.sub, marginBottom: 6 }}>
              货代 <span style={{ color: palette.red }}>*</span>
            </div>
            <Select
              value={forwarderId}
              onChange={setForwarderId}
              placeholder={
                forwarders.length
                  ? '选择货代'
                  : '还没有启用的服务商，请先在供应商里添加'
              }
              options={forwarders.map((f) => ({ value: f.id, label: f.name }))}
              style={{ width: '100%' }}
              aria-label="货代"
            />
          </div>
          {form.lines.map((l) => {
            const line = lines[l.soItemId] ?? {
              checked: false,
              quantity: null,
            };
            const waiting = [
              l.inTransit
                ? `在途 ${l.inTransit}${l.earliestArrival ? `（预计 ${dayjs(l.earliestArrival).format('MM-DD')} 到）` : ''}`
                : '',
              l.pendingShip ? `待发货 ${l.pendingShip}` : '',
            ]
              .filter(Boolean)
              .join(' · ');
            const tooMany = (line.quantity ?? 0) > l.available;
            return (
              <div
                key={l.soItemId}
                style={{
                  padding: '12px 14px',
                  borderRadius: 10,
                  background: palette.inset,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <Checkbox
                    checked={line.checked}
                    disabled={l.available <= 0}
                    onChange={(e) =>
                      setLine(l.soItemId, { checked: e.target.checked })
                    }
                    aria-label={`选择 ${l.model}`}
                  />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <b style={{ color: palette.ink }}>{l.model}</b>
                    <div style={{ fontSize: 12, color: palette.mute }}>
                      {[l.brand, l.tracked ? undefined : '系统外采购']
                        .filter(Boolean)
                        .join(' · ') || `订单数量 ${l.quantity}`}
                    </div>
                  </div>
                  <span style={{ fontSize: 12, color: palette.mute }}>
                    {l.tracked ? '可出库' : '最多'} {l.available}
                  </span>
                  <InputNumber
                    value={line.quantity}
                    min={0}
                    max={l.available}
                    precision={0}
                    disabled={!line.checked}
                    status={tooMany ? 'error' : undefined}
                    onChange={(v) =>
                      setLine(l.soItemId, {
                        quantity: v === null ? null : Number(v),
                      })
                    }
                    style={{ width: 90 }}
                    aria-label={`${l.model} 出库数量`}
                  />
                </div>
                {l.available <= 0 && (
                  <div
                    style={{
                      fontSize: 12,
                      color: palette.orange,
                      marginTop: 6,
                    }}
                  >
                    {l.occupied >= l.quantity
                      ? '已全部发过通知'
                      : waiting
                        ? `${waiting}，到了再发`
                        : '还没有入库的货'}
                  </div>
                )}
              </div>
            );
          })}
          <div>
            <div style={{ color: palette.sub, marginBottom: 6 }}>备注</div>
            <Input
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={200}
              placeholder="给仓库的话，如 客户要求 DHL，周五前发出"
              aria-label="备注"
            />
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            <InfoCircleOutlined />{' '}
            确认后生成出库单（待打包），仓库在「出库打包」里看到；可以分批发，没到的型号以后再发。
          </div>
        </div>
      )}
    </Modal>
  );
};

/** 订单上的出库单：待打包的可以修改、撤回 */
export const OrderOutbounds: React.FC<{
  soId: number;
  version: number;
  onChanged: () => void;
}> = ({ soId, version, onChanged }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess();
  const canNotice = !!access['sales:order:ship-notice'];
  const [rows, setRows] = useState<Outbound[]>([]);
  const [editing, setEditing] = useState<Outbound>();
  const [withdrawing, setWithdrawing] = useState<Outbound>();
  const [viewing, setViewing] = useState<Outbound>();

  const load = useCallback(() => {
    outboundApi
      .byOrder(soId)
      .then(setRows)
      .catch(() => setRows([]));
  }, [soId]);
  useEffect(load, [load, version]);

  if (rows.length === 0) return null;
  return (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      <b style={{ color: palette.ink }}>出库单</b>
      <div style={{ display: 'grid', gap: 10, marginTop: 12 }}>
        {rows.map((r) => (
          <div
            key={r.id}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              flexWrap: 'wrap',
              padding: '10px 14px',
              borderRadius: 10,
              background: palette.inset,
            }}
          >
            <a onClick={() => setViewing(r)}>{r.obNo}</a>
            <ObStatusPill value={r.status}>{r.statusName}</ObStatusPill>
            {r.source === 2 && (
              <span style={{ fontSize: 12, color: palette.orange }}>
                {r.sourceName}
              </span>
            )}
            <span style={{ color: palette.sub, fontSize: 13 }}>
              {r.items.map((i) => `${i.model} × ${i.quantity}`).join('、')}
            </span>
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: palette.mute }}>
              {r.forwarderName}
              {r.courier
                ? ` · ${r.courier.carrier} ${r.courier.trackingNo ?? ''}`
                : ''}
              {r.shNo ? ` · ${r.shNo}` : ''}
            </span>
            {canNotice && r.status === 1 && (
              <>
                <a onClick={() => setEditing(r)}>修改</a>
                <a
                  style={{ color: palette.red }}
                  onClick={() => setWithdrawing(r)}
                >
                  撤回
                </a>
              </>
            )}
          </div>
        ))}
      </div>
      <ShipNoticeModal
        open={!!editing}
        soId={soId}
        outbound={editing}
        onClose={() => setEditing(undefined)}
        onDone={() => {
          setEditing(undefined);
          load();
          onChanged();
        }}
      />
      <ReasonModal
        open={!!withdrawing}
        title={`撤回发货通知 · ${withdrawing?.obNo ?? ''}`}
        description="撤回后仓库不再打包这张出库单，数量回到可出库。"
        okText="撤回"
        danger
        onCancel={() => setWithdrawing(undefined)}
        onOk={async (reason) => {
          if (!withdrawing) return;
          try {
            await outboundApi.withdraw(withdrawing.id, reason);
            message.success('已撤回');
            setWithdrawing(undefined);
            load();
            onChanged();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
      <Modal
        open={!!viewing}
        title={viewing ? `出库单 ${viewing.obNo}` : ''}
        footer={null}
        width={760}
        onCancel={() => setViewing(undefined)}
        destroyOnHidden
      >
        {viewing && <OutboundDetail ob={viewing} palette={palette} />}
      </Modal>
    </Card>
  );
};
