import { Segmented } from 'antd';
import React from 'react';
import type { ContentLang } from '../service';

export const LANG_OPTIONS: { label: string; value: ContentLang }[] = [
  { label: '中文', value: 'zh' },
  { label: 'English', value: 'en' },
  { label: 'Русский', value: 'ru' },
];

/** 内容语言切换：中文对内，英文、俄文对外 */
export const LangSwitch: React.FC<{
  value: ContentLang;
  onChange: (v: ContentLang) => void;
  disabled?: boolean;
}> = ({ value, onChange, disabled }) => (
  <Segmented<ContentLang>
    size="small"
    options={LANG_OPTIONS}
    value={value}
    onChange={onChange}
    disabled={disabled}
  />
);
