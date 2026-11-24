import { App, Button, Input } from 'antd';
import dayjs from 'dayjs';
import { Pencil, Plus } from 'lucide-react';
import React, { useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/** 真实型号最多 128 字（与后端一致） */
const MAX = 128;

/** 保存真实型号后后端返回的询盘型号（openspec actual-model-and-description） */
export interface ActualModelResult {
  inquiryItemId: number;
  actualModel: string;
  actualModelBy: string;
  actualModelAt?: string | null;
  archiveStatus?: number;
  archiveStatusName?: string;
  productId?: number | null;
  /** 单据上用的型号：有真实型号取真实型号，否则取确认型号 */
  docModel: string;
  /** 客户型号：与单据型号相同时为空 */
  customerModel: string;
}

const readError = (e: unknown) =>
  (e as { info?: { errorMessage?: string }; message?: string })?.info
    ?.errorMessage ||
  (e as { message?: string })?.message ||
  '保存失败，请稍后重试';

/**
 * 行内编辑真实型号：点「保存」或按 Enter 才保存，「取消」或 Esc 恢复原值。
 * 改真实型号会连带改还没发出的单据，所以不在离开输入框时自动保存。
 */
export const ActualModelEditor: React.FC<{
  initial?: string;
  hint: string;
  onSave: (value: string) => Promise<void>;
  onCancel: () => void;
}> = ({ initial = '', hint, onSave, onCancel }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [value, setValue] = useState(initial);
  const [saving, setSaving] = useState(false);
  const save = async () => {
    if (value.trim() === initial.trim()) {
      onCancel();
      return;
    }
    setSaving(true);
    try {
      await onSave(value.trim());
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
        maxWidth: 360,
      }}
    >
      {/* 型号列窄时（报价单编辑）按钮换到下一行，输入框至少能放下一个完整型号 */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 8,
          alignItems: 'center',
        }}
      >
        <Input
          autoFocus
          size="small"
          value={value}
          maxLength={MAX}
          placeholder="确认后的准确型号"
          onChange={(e) => setValue(e.target.value)}
          onPressEnter={save}
          onKeyDown={(e) => {
            if (e.key === 'Escape') onCancel();
          }}
          style={{
            flex: '1 1 180px',
            minWidth: 180,
            fontFamily: 'JetBrains Mono, monospace',
          }}
        />
        <span style={{ display: 'inline-flex', gap: 8 }}>
          <Button size="small" onClick={onCancel} disabled={saving}>
            取消
          </Button>
          <Button size="small" type="primary" loading={saving} onClick={save}>
            保存
          </Button>
        </span>
      </div>
      <span style={{ color: palette.mute, fontSize: 12, lineHeight: 1.5 }}>
        {hint}
      </span>
    </div>
  );
};

/** 「采购 王磊 · 11-18 10:20」这样的修改记录 */
export const actualModelStamp = (by?: string, at?: string | null) =>
  [by, at ? dayjs(at).format('MM-DD HH:mm') : ''].filter(Boolean).join(' · ');

/**
 * 询盘详情型号下面的真实型号一行：有真实型号时显示「真实型号：…」与修改记录、可改；
 * 没有时显示「+ 真实型号」（editable 时）。
 */
export const ActualModelLine: React.FC<{
  actualModel?: string;
  by?: string;
  at?: string | null;
  editable: boolean;
  onSave: (value: string) => Promise<void>;
}> = ({ actualModel, by, at, editable, onSave }) => {
  const { palette } = useAppTheme();
  const [editing, setEditing] = useState(false);
  if (editing) {
    return (
      <>
        <span style={{ color: palette.info, fontSize: 12, fontWeight: 400 }}>
          真实型号
        </span>
        <ActualModelEditor
          initial={actualModel}
          hint="还没发出的报价单、PI、订单和采购单草稿会一起改；已发给客户或供应商的只提示。"
          onSave={async (v) => {
            await onSave(v);
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
        />
      </>
    );
  }
  if (actualModel) {
    const stamp = actualModelStamp(by, at);
    return (
      <span
        style={{
          color: palette.info,
          fontSize: 12,
          fontWeight: 400,
          display: 'inline-flex',
          gap: 6,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        真实型号：{actualModel}
        {editable && (
          <Pencil
            size="1em"
            aria-label="修改真实型号"
            style={{ color: palette.faint, cursor: 'pointer' }}
            onClick={() => setEditing(true)}
          />
        )}
        {stamp && (
          <span style={{ color: palette.faint, fontWeight: 400 }}>{stamp}</span>
        )}
      </span>
    );
  }
  if (!editable) return null;
  return (
    <Button
      type="link"
      size="small"
      icon={<Plus size="1em" />}
      onClick={() => setEditing(true)}
      style={{ padding: 0, height: 'auto', alignSelf: 'flex-start' }}
    >
      真实型号
    </Button>
  );
};

/** 报价单草稿型号旁的铅笔：点开在型号下面行内编辑真实型号 */
export const ActualModelPencil: React.FC<{ onClick: () => void }> = ({
  onClick,
}) => {
  const { palette } = useAppTheme();
  return (
    // 原生按钮：键盘能操作，悬停显示名称
    <button
      type="button"
      aria-label="填写真实型号"
      title="填写真实型号"
      onClick={onClick}
      style={{
        marginLeft: 6,
        padding: 0,
        border: 'none',
        background: 'none',
        color: palette.faint,
        fontSize: 12,
        cursor: 'pointer',
        lineHeight: 1,
      }}
    >
      <Pencil size="1em" />
    </button>
  );
};
