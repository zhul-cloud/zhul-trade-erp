import { useAccess } from '@umijs/max';
import { App, Checkbox, Modal } from 'antd';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { isPlatformAccount } from '@/utils/platform';
import {
  contentApi,
  type MarketingPlatform,
  type MarketingPlatformOption,
  readBizError,
} from '../service';

/** 各平台要生成的内容，帮运营判断要不要启用 */
const PLATFORM_DESC: Record<MarketingPlatform, string> = {
  alibaba: '标题、关键词库、卖点 · 英文',
  mic: '标题、关键词库、商品详情 · 英文',
  site: 'SEO、首屏定义块、长描述、营销问答 · 英文、俄文',
  social: 'LinkedIn、Facebook、YouTube 描述 · 英文',
};

/** 平台设置：本公司做哪些平台的营销内容，至少一个；没有维护权限只能查看 */
export const PlatformSettings: React.FC<{
  open: boolean;
  options: MarketingPlatformOption[];
  onClose: () => void;
  onSaved: (options: MarketingPlatformOption[]) => void;
}> = ({ open, options, onClose, onSaved }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['product:content:edit'];
  const [checked, setChecked] = useState<MarketingPlatform[]>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) setChecked(options.filter((o) => o.enabled).map((o) => o.code));
  }, [open, options]);

  const toggle = (code: MarketingPlatform, on: boolean) =>
    setChecked((prev) =>
      on ? [...prev, code] : prev.filter((c) => c !== code),
    );

  const save = async () => {
    setSaving(true);
    try {
      onSaved(await contentApi.savePlatforms(checked));
      message.success('平台设置已保存');
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title="平台设置"
      okText="保存"
      cancelText="取消"
      onOk={save}
      onCancel={onClose}
      confirmLoading={saving}
      okButtonProps={{ disabled: !canEdit || checked.length === 0 }}
      width={480}
    >
      <div style={{ fontSize: 13, color: palette.mute, marginBottom: 14 }}>
        本公司做哪些平台的营销内容。没启用的平台不显示、不计进度，已写入的内容保留。
        {isPlatformAccount() &&
          '平台账号的设置是各公司的默认值，公司自己设置过的以公司为准。'}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {options.map((o) => (
          <div
            key={o.code}
            style={{
              padding: '10px 12px',
              borderRadius: 10,
              background: palette.inset,
            }}
          >
            <Checkbox
              checked={checked.includes(o.code)}
              disabled={!canEdit}
              onChange={(e) => toggle(o.code, e.target.checked)}
            >
              <span style={{ fontWeight: 600, color: palette.ink }}>
                {o.label}
              </span>
              <div style={{ fontSize: 12, color: palette.mute }}>
                {PLATFORM_DESC[o.code]}
              </div>
            </Checkbox>
          </div>
        ))}
      </div>
      {checked.length === 0 && (
        <div style={{ marginTop: 10, fontSize: 12, color: palette.orange }}>
          至少启用一个平台
        </div>
      )}
      {!canEdit && (
        <div style={{ marginTop: 10, fontSize: 12, color: palette.mute }}>
          没有「商品内容维护」权限，只能查看
        </div>
      )}
    </Modal>
  );
};
