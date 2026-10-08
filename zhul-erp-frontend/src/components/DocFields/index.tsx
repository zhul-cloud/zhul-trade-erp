import { ExpandOutlined } from '@ant-design/icons';
import { AutoComplete, Button, Drawer, Select, Space } from 'antd';
import React, { useState } from 'react';
import { INCOTERMS } from '@/pages/customer/constants';
import { CustomerTypePill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_TRADE_TERM_PLACE, useDictTexts } from '@/utils/dict';

/** 改选贸易术语时带出的默认地点：DAP 为客户国家，FOB 为香港，EXW 为福州；其他术语保留原地点 */
export const defaultPlaceOf = (
  incoterm: string,
  customerCountry: string | undefined,
  current: string,
) => {
  if (incoterm === 'DAP') return customerCountry || current;
  if (incoterm === 'FOB') return 'Hong Kong';
  if (incoterm === 'EXW') return 'Fuzhou';
  return current;
};

/** 下拉按已输入的文字过滤（不区分大小写） */
const matchText = (input: string, o?: { value?: unknown }) =>
  String(o?.value ?? '')
    .toLowerCase()
    .includes(input.toLowerCase());

/**
 * 可选可填的下拉：打开时列出全部选项，开始打字后才按输入过滤。
 * AutoComplete 默认拿输入框里已有的值过滤，已有内容时只剩完全匹配的一项，看起来像下拉不出来。
 */
const useOpenFilter = <T extends { value?: unknown }>(options: T[]) => {
  const [typed, setTyped] = useState<string | null>(null);
  return {
    options: typed ? options.filter((o) => matchText(typed, o)) : options,
    showSearch: { filterOption: false, onSearch: (v: string) => setTyped(v) },
    onOpenChange: (open: boolean) => {
      if (open) setTyped(null);
    },
  };
};

/** 下拉选项：英文（单据上显示的文字）+ 中文说明；可直接填写字典以外的内容 */
const textOptions = (items: { text: string; name: string }[]) =>
  items.map((d) => ({
    value: d.text,
    label: (
      <span>
        {d.text}
        {d.name && d.name !== d.text && (
          <span style={{ marginLeft: 8, opacity: 0.6, fontSize: 12 }}>
            {d.name}
          </span>
        )}
      </span>
    ),
  }));

/** 贸易术语 + 地点（报价单、PI 共用） */
export const IncotermInput: React.FC<{
  incoterm: string;
  place: string;
  customerCountry?: string;
  onChange: (incoterm: string, place: string) => void;
}> = ({ incoterm, place, customerCountry, onChange }) => {
  const places = useDictTexts(DICT_TRADE_TERM_PLACE);
  const placeFilter = useOpenFilter(
    textOptions(
      customerCountry && !places.some((p) => p.text === customerCountry)
        ? [{ text: customerCountry, name: '客户国家' }, ...places]
        : places,
    ),
  );
  return (
    <Space.Compact style={{ width: '100%' }}>
      <Select
        style={{ width: 96 }}
        value={incoterm || undefined}
        placeholder="术语"
        allowClear
        options={INCOTERMS.map((c) => ({ value: c, label: c }))}
        onChange={(v) => {
          const next = v ?? '';
          onChange(next, defaultPlaceOf(next, customerCountry, place));
        }}
        aria-label="贸易术语"
      />
      <AutoComplete
        style={{ flex: 1 }}
        value={place}
        options={placeFilter.options}
        placeholder="地点"
        onChange={(v) => onChange(incoterm, v ?? '')}
        showSearch={placeFilter.showSearch}
        onOpenChange={placeFilter.onOpenChange}
        aria-label="术语地点"
      />
    </Space.Compact>
  );
};

/** 文字字典下拉（可选可填）：质保、交期、付款条件、起运港等 */
export const DictTextInput: React.FC<{
  dictType: string;
  value?: string;
  onChange: (v: string) => void;
  placeholder?: string;
  size?: 'small' | 'middle';
  ariaLabel?: string;
  style?: React.CSSProperties;
}> = ({ dictType, value, onChange, placeholder, size, ariaLabel, style }) => {
  const items = useDictTexts(dictType);
  const filter = useOpenFilter(textOptions(items));
  return (
    <AutoComplete
      size={size}
      style={{ width: '100%', ...style }}
      value={value}
      options={filter.options}
      placeholder={placeholder}
      onChange={(v) => onChange(v ?? '')}
      showSearch={filter.showSearch}
      onOpenChange={filter.onOpenChange}
      aria-label={ariaLabel}
    />
  );
};

/** 列表的客户列：名称、国家与新 / 老客户（与客户询盘列表一致） */
export const CustomerCell: React.FC<{
  name?: string;
  country?: string;
  type?: number;
}> = ({ name, country, type }) => {
  const { palette } = useAppTheme();
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      <span
        style={{ display: 'inline-flex', flexDirection: 'column', minWidth: 0 }}
      >
        <span style={{ color: palette.ink, fontWeight: 600 }}>
          {name || '—'}
        </span>
        <span style={{ color: palette.mute, fontSize: 12 }}>
          {country || '—'}
        </span>
      </span>
      {type ? <CustomerTypePill type={type} /> : null}
    </span>
  );
};

/** 单据预览：侧栏显示第一页缩略图与总页数，「放大查看」在大抽屉里逐页按可读尺寸显示 */
export const PreviewPages: React.FC<{ pages: string[]; title: string }> = ({
  pages,
  title,
}) => {
  const { palette } = useAppTheme();
  const [open, setOpen] = useState(false);
  const page = (src: string, i: number) => (
    <img
      src={src}
      alt={`第 ${i + 1} 页`}
      style={{
        display: 'block',
        width: '100%',
        borderRadius: 8,
        border: `1px solid ${palette.hairline}`,
        background: '#fff',
      }}
    />
  );
  if (!pages.length) return null;
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        style={{ all: 'unset', cursor: 'zoom-in', display: 'block' }}
        aria-label="放大查看预览"
      >
        {page(pages[0], 0)}
      </button>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          marginTop: 8,
          fontSize: 12,
          color: palette.mute,
        }}
      >
        共 {pages.length} 页
        <Button
          size="small"
          type="link"
          icon={<ExpandOutlined />}
          style={{ marginLeft: 'auto' }}
          onClick={() => setOpen(true)}
        >
          放大查看
        </Button>
      </div>
      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        title={title}
        size="min(1080px, 94vw)"
        styles={{ body: { background: palette.inset } }}
      >
        <div
          style={{ display: 'grid', gap: 20, maxWidth: 960, margin: '0 auto' }}
        >
          {pages.map((src, i) => (
            <figure key={src.slice(-32) + String(i)} style={{ margin: 0 }}>
              {page(src, i)}
              <figcaption
                style={{
                  textAlign: 'center',
                  fontSize: 12,
                  color: palette.mute,
                  marginTop: 6,
                }}
              >
                第 {i + 1} / {pages.length} 页
              </figcaption>
            </figure>
          ))}
        </div>
      </Drawer>
    </>
  );
};
