import { Alert, App, Checkbox, Input, Modal, Radio, Skeleton } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { Pill } from '../shared/components';
import {
  ITEM_NO_STOCK,
  ITEM_PENDING,
  ITEM_PRICED,
  RETURN_REASONS,
} from '../shared/constants';
import { myTaskApi, type ReturnPreview } from '../shared/service';

/** 按型号退回：待询价的一定交出，无货默认选中，有价可选，已报给客户的不能交出 */
const ReturnModal: React.FC<{
  taskId?: number;
  title: string;
  /** 业务员转给采购继续找；否则是采购、兼职退回 */
  selfSourcing: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ taskId, title, selfSourcing, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [preview, setPreview] = useState<ReturnPreview>();
  const [selected, setSelected] = useState<number[]>([]);
  const [reason, setReason] = useState(selfSourcing ? 2 : 1);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const verb = selfSourcing ? '转给采购' : '退回';

  useEffect(() => {
    if (!taskId) return;
    setPreview(undefined);
    setNote('');
    setReason(selfSourcing ? 2 : 1);
    myTaskApi
      .returnPreview(taskId)
      .then((p) => {
        setPreview(p);
        setSelected(
          p.rows
            .filter((r) => !r.locked && r.quoteStatus === ITEM_NO_STOCK)
            .map((r) => r.itemId),
        );
      })
      .catch((e) => {
        message.error(readBizError(e).message);
        onClose();
      });
  }, [taskId, selfSourcing, message, onClose]);

  const handed = useMemo(
    () =>
      preview?.rows.filter((r) => r.must || selected.includes(r.itemId)) ?? [],
    [preview, selected],
  );
  const drafts = useMemo(() => {
    const models = new Set(handed.map((r) => r.model));
    return (preview?.drafts ?? []).filter((d) =>
      d.models.some((m) => models.has(m)),
    );
  }, [preview, handed]);

  const submit = async () => {
    if (!taskId) return;
    setBusy(true);
    try {
      await myTaskApi.returnTask(taskId, reason, note, selected);
      message.success(
        reason === 1 ? '已退回，并提醒业务员核实型号' : `已${verb}`,
      );
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  if (preview?.pendingReview)
    return (
      <Modal
        open={!!taskId}
        title="暂时不能退回"
        okText="知道了"
        cancelButtonProps={{ style: { display: 'none' } }}
        onOk={onClose}
        onCancel={onClose}
      >
        <Alert
          type="warning"
          showIcon
          title="这个任务还有回价在等审核。审核完成后才能退回，这样接手的人能看到准确的结果。"
        />
        <div style={{ color: palette.mute, marginTop: 12 }}>
          如果急着交出，可以联系采购负责人尽快审核。
        </div>
      </Modal>
    );

  return (
    <Modal
      open={!!taskId}
      width={760}
      title={selfSourcing ? '转给采购继续找' : '退回任务'}
      okText={verb}
      cancelText="再想想"
      confirmLoading={busy}
      okButtonProps={{ disabled: !preview || handed.length === 0 }}
      onOk={submit}
      onCancel={onClose}
    >
      {!preview ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'grid', gap: 12 }}>
          <b style={{ color: palette.ink }}>{title}</b>
          <div style={{ color: palette.sub }}>
            选出需要继续找的型号，其他型号保持现在的结果。
          </div>
          <div style={{ display: 'grid', gap: 4 }}>
            {preview.rows.map((r) => {
              const on = r.must || selected.includes(r.itemId);
              return (
                <div
                  key={r.itemId}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '8px 12px',
                    borderRadius: 10,
                    background: on ? palette.accentSoft : undefined,
                  }}
                >
                  <Checkbox
                    aria-label={`选择 ${r.model}`}
                    checked={on}
                    disabled={r.must || r.locked}
                    onChange={(e) =>
                      setSelected((s) =>
                        e.target.checked
                          ? [...s, r.itemId]
                          : s.filter((id) => id !== r.itemId),
                      )
                    }
                  />
                  <b style={{ flex: '0 0 190px', color: palette.ink }}>
                    {r.model}
                  </b>
                  <span style={{ flex: '0 0 36px', color: palette.sub }}>
                    {r.quantity}
                  </span>
                  <span style={{ flex: '0 0 170px' }}>
                    {r.locked ? (
                      <Pill tone="violet">已报给客户</Pill>
                    ) : r.quoteStatus === ITEM_PENDING ? (
                      <Pill tone="orange">待询价</Pill>
                    ) : r.quoteStatus === ITEM_NO_STOCK ? (
                      <Pill tone="red">无货</Pill>
                    ) : r.quoteStatus === ITEM_PRICED ? (
                      <Pill tone="green">
                        有价
                        {r.unitPriceCny != null
                          ? ` ${formatAmount(r.unitPriceCny, r.currencyCode ?? 'CNY')}`
                          : ''}
                      </Pill>
                    ) : null}
                  </span>
                  <span
                    style={{
                      color: r.locked ? palette.orange : palette.mute,
                      fontSize: 12,
                    }}
                  >
                    {r.locked
                      ? `已在报价单 ${r.lockedQuotationNo ?? ''} 里，不能交出`
                      : r.must
                        ? '还没有结果，一定会交出去'
                        : r.quoteStatus === ITEM_PRICED
                          ? '想让别人再比一比可以勾选'
                          : r.checkedChannels
                            ? `你的记录：${r.checkedChannels}`
                            : ''}
                  </span>
                </div>
              );
            })}
          </div>
          <Radio.Group
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            options={RETURN_REASONS}
            optionType="button"
          />
          <Input.TextArea
            rows={2}
            maxLength={300}
            placeholder="给接手人的备注（选填），如：淘宝只有拆机件，德国现货渠道可能有"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
          {reason === 1 && (
            <div style={{ color: palette.mute, fontSize: 12 }}>
              选「型号存疑」会提醒业务员核实。
            </div>
          )}
          {drafts.length > 0 && (
            <Alert
              type="warning"
              showIcon
              title={`草稿报价单 ${drafts.map((d) => d.quotationNo).join('、')} 用了其中型号的价格，回价后请重新取价。`}
            />
          )}
          <div style={{ color: palette.sub, fontSize: 13 }}>
            转交后 {handed.length}{' '}
            个型号回到待询价，询盘回到「询价中」；你不再看到这个任务，查过的记录会留给接手的人。
          </div>
        </div>
      )}
    </Modal>
  );
};

export default ReturnModal;
