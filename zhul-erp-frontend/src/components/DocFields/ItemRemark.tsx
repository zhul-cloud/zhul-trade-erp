import { App, Button, Input } from 'antd';
import { MessageSquare, Pencil, Plus, TriangleAlert } from 'lucide-react';
import React, { useState } from 'react';
import EllipsisText from '@/components/EllipsisText';
import { useAppTheme } from '@/theme/AppTheme';
import { ChangeStamp } from './ActualModel';

/** 型号备注最多 200 字（与后端一致） */
export const ITEM_REMARK_MAX = 200;

/** 保存型号备注后后端返回的内容（openspec item-remark） */
export interface ItemRemarkResult {
  remark: string;
  remarkBy: string;
  remarkAt?: string | null;
}

const readError = (e: unknown) =>
  (e as { info?: { errorMessage?: string }; message?: string })?.info
    ?.errorMessage ||
  (e as { message?: string })?.message ||
  '保存失败，请稍后重试';

/**
 * 型号下面的业务员备注（只读）：只在系统内显示，不进发给客户、供应商的文件。
 * alert 为采购单「下单后备注有更新」：标红提醒与供应商核对。
 */
export const ItemRemarkText: React.FC<{ text?: string; alert?: boolean }> = ({
  text,
  alert,
}) => {
  const { palette } = useAppTheme();
  if (!text && !alert) return null;
  const color = alert ? palette.red : palette.orange;
  return (
    <div
      style={{
        color,
        fontSize: 12,
        fontWeight: 400,
        lineHeight: 1.5,
        display: 'flex',
        gap: 6,
        alignItems: 'flex-start',
        marginTop: 2,
      }}
    >
      {alert ? (
        <TriangleAlert size="1em" style={{ marginTop: 3 }} />
      ) : (
        <MessageSquare size="1em" style={{ marginTop: 3 }} />
      )}
      <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
        {alert ? '备注已更新：' : ''}
        {text || '（已清空）'}
        {alert ? '（请与供应商核对）' : ''}
      </span>
    </div>
  );
};

/**
 * 列表的「描述」格（采购需求，2026-11-24 用户选定）：描述一行，有备注时下面一行橙色「备注：…」。
 * 两行都单行省略、悬停看全文，再长也不折行撑高（D21）；详情页的明细仍用 DocDescription、ItemRemarkText 完整显示。
 */
export const DescRemarkCell: React.FC<{
  description?: string;
  remark?: string;
}> = ({ description, remark }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ minWidth: 0 }}>
      {description ? (
        <EllipsisText text={description} style={{ color: palette.sub }} />
      ) : (
        <span style={{ color: palette.faint }}>—</span>
      )}
      {remark && (
        <EllipsisText
          text={`备注：${remark}`}
          tip={<span style={{ whiteSpace: 'pre-wrap' }}>{remark}</span>}
          style={{ fontSize: 12, lineHeight: '18px', color: palette.orange }}
        />
      )}
    </div>
  );
};

/**
 * 可编辑的型号备注（询盘详情、销售订单详情）：有备注时显示内容、修改记录与铅笔，没有时显示「+ 备注」。
 * 点「保存」才保存，「取消」或 Esc 恢复原值。
 */
export const ItemRemarkLine: React.FC<{
  text?: string;
  by?: string;
  at?: string | null;
  editable: boolean;
  onSave: (value: string) => Promise<void>;
}> = ({ text, by, at, editable, onSave }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState('');
  const [saving, setSaving] = useState(false);

  if (editing) {
    const save = async () => {
      if (value.trim() === (text ?? '').trim()) {
        setEditing(false);
        return;
      }
      setSaving(true);
      try {
        await onSave(value.trim());
        setEditing(false);
      } catch (e) {
        message.error(readError(e));
      } finally {
        setSaving(false);
      }
    };
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 6,
          marginTop: 6,
          maxWidth: 420,
        }}
      >
        <Input.TextArea
          autoFocus
          value={value}
          maxLength={ITEM_REMARK_MAX}
          autoSize={{ minRows: 2, maxRows: 5 }}
          placeholder="如：线圈 DC24V、要带 2 个辅助触点"
          aria-label="型号备注"
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setEditing(false);
          }}
        />
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 8,
            alignItems: 'center',
          }}
        >
          <span style={{ flex: 1, color: palette.mute, fontSize: 12 }}>
            只在系统内显示，不进发给客户、供应商的文件 · {value.length}/
            {ITEM_REMARK_MAX}
          </span>
          <Button
            size="small"
            onClick={() => setEditing(false)}
            disabled={saving}
          >
            取消
          </Button>
          <Button size="small" type="primary" loading={saving} onClick={save}>
            保存
          </Button>
        </div>
      </div>
    );
  }

  const open = () => {
    setValue(text ?? '');
    setEditing(true);
  };
  if (text) {
    return (
      <div
        style={{
          color: palette.orange,
          fontSize: 12,
          fontWeight: 400,
          lineHeight: 1.5,
          display: 'flex',
          gap: 6,
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          marginTop: 2,
        }}
      >
        <MessageSquare size="1em" style={{ marginTop: 3 }} />
        <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
          {text}
        </span>
        {editable && (
          <Pencil
            size="1em"
            aria-label="修改型号备注"
            style={{ color: palette.faint, cursor: 'pointer', marginTop: 3 }}
            onClick={open}
          />
        )}
        <ChangeStamp by={by} at={at} />
      </div>
    );
  }
  if (!editable) return null;
  return (
    <Button
      type="link"
      size="small"
      icon={<Plus size="1em" />}
      onClick={open}
      style={{ padding: 0, height: 'auto', alignSelf: 'flex-start' }}
    >
      备注
    </Button>
  );
};
