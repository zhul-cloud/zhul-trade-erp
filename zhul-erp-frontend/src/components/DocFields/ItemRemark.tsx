import {
  EditOutlined,
  MessageOutlined,
  PlusOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { App, Button, Input } from 'antd';
import React, { useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { actualModelStamp } from './ActualModel';

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
        fontWeight: 500,
        lineHeight: 1.5,
        display: 'flex',
        gap: 6,
        alignItems: 'flex-start',
        marginTop: 2,
      }}
    >
      {alert ? (
        <WarningOutlined style={{ marginTop: 3 }} />
      ) : (
        <MessageOutlined style={{ marginTop: 3 }} />
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
    const stamp = actualModelStamp(by, at);
    return (
      <div
        style={{
          color: palette.orange,
          fontSize: 12,
          fontWeight: 500,
          lineHeight: 1.5,
          display: 'flex',
          gap: 6,
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          marginTop: 2,
        }}
      >
        <MessageOutlined style={{ marginTop: 3 }} />
        <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
          {text}
        </span>
        {editable && (
          <EditOutlined
            aria-label="修改型号备注"
            style={{ color: palette.faint, cursor: 'pointer', marginTop: 3 }}
            onClick={open}
          />
        )}
        {stamp && (
          <span style={{ color: palette.faint, fontWeight: 400 }}>{stamp}</span>
        )}
      </div>
    );
  }
  if (!editable) return null;
  return (
    <Button
      type="link"
      size="small"
      icon={<PlusOutlined />}
      onClick={open}
      style={{ padding: 0, height: 'auto', alignSelf: 'flex-start' }}
    >
      备注
    </Button>
  );
};
