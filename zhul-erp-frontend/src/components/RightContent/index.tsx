import { getAllLocales, getLocale, setLocale } from '@umijs/max';
import type { MenuProps } from 'antd';
import { Dropdown } from 'antd';
import { Check, Languages } from 'lucide-react';
import React, { useMemo, useState } from 'react';
import { IconButton } from '@/components/Shell/IconButton';
import { useAppTheme } from '@/theme/AppTheme';

/** 各语言用自己的文字显示，不用国旗 */
export const localeLabelMap: Record<string, string> = {
  'zh-CN': '简体中文',
  'zh-TW': '繁體中文',
  'en-US': 'English',
  'ja-JP': '日本語',
  'pt-BR': 'Português',
  'id-ID': 'Bahasa Indonesia',
  'fa-IR': 'فارسی',
  'bn-BD': 'বাংলা',
};

/** 顶栏的语言切换：下拉列出各语言，当前语言为主题色浅底加对勾（外壳原型 F1d） */
export const LangDropdown: React.FC = () => {
  const { palette } = useAppTheme();
  const [open, setOpen] = useState(false);
  const allLocales = useMemo(() => getAllLocales(), []);
  const currentLocale = getLocale();
  const supportLocales = allLocales.filter((l) => l in localeLabelMap);

  if (supportLocales.length <= 1) {
    return null;
  }

  const items: MenuProps['items'] = supportLocales.map((locale) => ({
    key: locale,
    label: localeLabelMap[locale] ?? locale,
    extra:
      locale === currentLocale ? (
        <Check size={16} color={palette.link} aria-hidden />
      ) : undefined,
  }));

  return (
    <Dropdown
      trigger={['click']}
      placement="bottomRight"
      open={open}
      onOpenChange={setOpen}
      menu={{
        selectedKeys: [currentLocale],
        onClick: ({ key }) => setLocale(key, false),
        items,
        style: { minWidth: 200 },
      }}
    >
      <IconButton
        label="语言"
        icon={<Languages size={20} />}
        pressed={open}
        tip={!open}
      />
    </Dropdown>
  );
};
