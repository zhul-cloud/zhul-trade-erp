import { Segmented } from 'antd';
import React from 'react';
import type { ContentLang } from '../service';

export const LANG_OPTIONS: { label: string; value: ContentLang }[] = [
  { label: 'English', value: 'en' },
  { label: 'Русский', value: 'ru' },
];

/** 营销内容语言切换：英文、俄文（商品资料只存中文） */
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
