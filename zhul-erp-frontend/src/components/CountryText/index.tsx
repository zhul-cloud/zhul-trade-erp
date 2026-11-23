import { Tooltip } from 'antd';
import { Globe } from 'lucide-react';
import React from 'react';
import { useCountries } from '@/pages/product/components/useCountries';
import { useAppTheme } from '@/theme/AppTheme';

/**
 * 国旗（公共组件，规范「组件标准 · 国家」，ui-redesign C2、D14）：
 * 按 ISO 两位代码取 `flag-icons` 的 SVG（Windows 不显示国旗 emoji），16×12、圆角 2、内描边；没有代码时显示 globe。
 * 只认清单里的代码（见 CountryText），不把任意文字拼进地址。
 */
export const CountryFlag: React.FC<{ code?: string }> = ({ code }) => {
  const { palette } = useAppTheme();
  if (!code) {
    // 放进和国旗同样大的 16×12 格子里，同一列的国名对齐
    return (
      <span
        aria-hidden
        style={{
          width: 16,
          height: 12,
          flex: 'none',
          display: 'grid',
          placeItems: 'center',
          color: palette.mute,
        }}
      >
        <Globe size={14} />
      </span>
    );
  }
  // 产物里的 flags/ 由 config.ts 的 copy 从 flag-icons 复制；浏览器只下载用到的那几面
  return (
    <img
      alt=""
      aria-hidden
      src={`/flags/${code.toLowerCase()}.svg`}
      width={16}
      height={12}
      loading="lazy"
      style={{
        flex: 'none',
        display: 'block',
        borderRadius: 2,
        objectFit: 'cover',
        // 白底国旗（日本、新加坡等）在白卡上要有边
        outline: `1px solid ${palette.hairline}`,
        outlineOffset: -1,
      }}
    />
  );
};

/**
 * 国家：国旗 + 中文国名，悬停显示英文名。value 为保存的国家（英文名，也认两位代码和中文名）；
 * 清单里找不到时原样显示，空值显示「—」。
 */
export const CountryText: React.FC<{ value?: string | null }> = ({ value }) => {
  const { find } = useCountries();
  if (!value) return <>—</>;
  const c = find(value);
  const body = (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        maxWidth: '100%',
        verticalAlign: 'middle',
      }}
    >
      <CountryFlag code={c?.code} />
      <span
        style={{
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {c?.nameZh ?? value}
      </span>
    </span>
  );
  return c && c.nameEn !== c.nameZh ? (
    <Tooltip title={c.nameEn}>{body}</Tooltip>
  ) : (
    body
  );
};
