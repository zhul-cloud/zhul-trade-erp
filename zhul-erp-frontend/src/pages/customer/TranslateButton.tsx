import type { FormInstance } from 'antd';
import { App, Button } from 'antd';
import { Languages } from 'lucide-react';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { CJK_PATTERN } from './constants';
import { useNameTranslation } from './nameTranslation';

/** 输入框右侧的「AI 翻译」按钮（客户名称、主联系人中文名、联系人中文名共用） */
export const TranslateButton: React.FC<{
  loading: boolean;
  onClick: () => void;
}> = ({ loading, onClick }) => {
  const { palette } = useAppTheme();
  return (
    <Button
      type="text"
      size="small"
      icon={<Languages size="1em" />}
      loading={loading}
      onClick={onClick}
      style={{ color: palette.link, background: palette.neutralSoft }}
    >
      {loading ? '翻译中' : 'AI 翻译'}
    </Button>
  );
};

/**
 * 联系人中文名的「AI 翻译」（spec master-data/customer-profile「客户联系人与决策人画像」）：
 * 读表单里的英文姓名，当场翻译后把译名填进中文名，由人确认后随表单保存
 */
export const useContactNameTranslate = <T,>(
  form: FormInstance<T>,
  nameField: string,
  nameCnField: string,
) => {
  const { message } = App.useApp();
  const { running, translate } = useNameTranslation('contact');
  const run = async () => {
    const name = String(
      (form.getFieldValue(nameField as never) as unknown) ?? '',
    ).trim();
    if (!name || CJK_PATTERN.test(name)) {
      message.warning('请先填写英文姓名');
      return;
    }
    const r = await translate(name);
    if ('error' in r) {
      message.error(r.error);
      return;
    }
    form.setFieldValue(nameCnField as never, r.text as never);
  };
  return { running, run };
};
