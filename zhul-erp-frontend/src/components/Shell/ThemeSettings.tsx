import { Popover } from 'antd';
import {
  Check,
  Monitor,
  Moon,
  Sun,
  SwatchBook,
  TrendingDown,
  TrendingUp,
} from 'lucide-react';
import React, { useEffect, useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { PRESET_ORDER, PRESETS, type PresetKey } from '@/theme/palette';
import {
  type DeltaPref,
  type ModePref,
  setDeltaPref,
  setModePref,
  setThemePreset,
  useDeltaPref,
  useModePref,
} from '@/theme/store';
import { IconButton } from './IconButton';

/**
 * 单选组：用原生单选，方向键切换、只有选中项在 Tab 顺序里都由浏览器处理；
 * 单选框本身隐藏，焦点框画在后面的可见内容上（全局样式 .zhul-radio）。
 */
function RadioGroup<T extends string>({
  label,
  name,
  value,
  options,
  onChange,
  render,
  style,
}: {
  label: string;
  name: string;
  value: T;
  options: readonly T[];
  onChange: (v: T) => void;
  render: (v: T, checked: boolean) => React.ReactNode;
  style?: React.CSSProperties;
}) {
  return (
    <fieldset
      style={{ border: 0, margin: 0, padding: 0, minWidth: 0, ...style }}
    >
      <legend className="zhul-sr-only">{label}</legend>
      {options.map((o) => {
        const checked = o === value;
        return (
          <label
            key={o}
            style={{
              flex: 1,
              minWidth: 0,
              display: 'block',
              cursor: 'pointer',
            }}
          >
            <input
              type="radio"
              className="zhul-radio"
              name={name}
              value={o}
              checked={checked}
              onChange={() => onChange(o)}
            />
            {render(o, checked)}
          </label>
        );
      })}
    </fieldset>
  );
}

const MODES: readonly ModePref[] = ['system', 'light', 'dark'];
const MODE_LABEL: Record<ModePref, [string, React.ReactNode]> = {
  system: ['跟随系统', <Monitor key="i" size={16} />],
  light: ['浅色', <Sun key="i" size={16} />],
  dark: ['深色', <Moon key="i" size={16} />],
};
const DELTAS: readonly DeltaPref[] = ['redUp', 'greenUp'];

/** 主题设置的内容：模式三选一、6 种主题色、涨跌颜色；都只保存在本机浏览器，选中统一用主题色表示 */
export const ThemeSettingsPanel: React.FC<{ onClose?: () => void }> = ({
  onClose,
}) => {
  const { palette: p, preset } = useAppTheme();
  const modePref = useModePref();
  const delta = useDeltaPref();
  const box = useRef<HTMLDivElement>(null);
  // 打开后焦点落到第一组的选中项
  useEffect(() => {
    box.current
      ?.querySelector<HTMLElement>('input[type="radio"]:checked')
      ?.focus();
  }, []);
  const section = (title: string, body: React.ReactNode) => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <div style={{ fontSize: 13, fontWeight: 500, color: p.mute }}>
        {title}
      </div>
      {body}
    </div>
  );
  return (
    <div
      ref={box}
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          e.stopPropagation();
          onClose?.();
        }
      }}
      style={{ width: 308, display: 'flex', flexDirection: 'column', gap: 16 }}
    >
      <div style={{ fontSize: 16, fontWeight: 500, color: p.ink }}>
        主题设置
      </div>
      {section(
        '模式',
        <RadioGroup
          label="模式"
          name="theme-mode"
          value={modePref}
          options={MODES}
          onChange={setModePref}
          style={{
            display: 'flex',
            gap: 2,
            padding: 3,
            borderRadius: 8,
            background: p.inset,
          }}
          render={(m, on) => (
            <span
              style={{
                height: 32,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6,
                borderRadius: 6,
                fontSize: 13,
                fontWeight: on ? 500 : 400,
                color: on ? p.link : p.mute,
                background: on ? p.primarySoft : 'transparent',
              }}
            >
              {MODE_LABEL[m][1]}
              {MODE_LABEL[m][0]}
            </span>
          )}
        />,
      )}
      {section(
        '主题色',
        <RadioGroup<PresetKey>
          label="主题色"
          name="theme-preset"
          value={preset}
          options={PRESET_ORDER}
          onChange={setThemePreset}
          style={{ display: 'flex', gap: 4 }}
          render={(k, on) => (
            <span
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 6,
              }}
            >
              <span
                style={{
                  width: 36,
                  height: 36,
                  display: 'grid',
                  placeItems: 'center',
                  borderRadius: '50%',
                  border: `2px solid ${on ? p.link : 'transparent'}`,
                }}
              >
                <span
                  style={{
                    width: 28,
                    height: 28,
                    display: 'grid',
                    placeItems: 'center',
                    borderRadius: '50%',
                    background: PRESETS[k].primary,
                    color: '#FFFFFF',
                  }}
                >
                  {on && <Check size={16} />}
                </span>
              </span>
              <span style={{ fontSize: 12, color: on ? p.ink : p.mute }}>
                {PRESETS[k].name}
              </span>
            </span>
          )}
        />,
      )}
      {section(
        '涨跌颜色',
        <RadioGroup
          label="涨跌颜色"
          name="theme-delta"
          value={delta}
          options={DELTAS}
          onChange={setDeltaPref}
          style={{ display: 'flex', gap: 8 }}
          render={(d, on) => {
            const up = d === 'redUp' ? p.red : p.green;
            const down = d === 'redUp' ? p.green : p.red;
            return (
              <span
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 8,
                  padding: '10px 12px',
                  borderRadius: 8,
                  textAlign: 'left',
                  background: on ? p.inset : 'transparent',
                  border: `1px solid ${on ? p.control : p.hairline}`,
                }}
              >
                <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span
                    style={{
                      width: 16,
                      height: 16,
                      flex: 'none',
                      display: 'grid',
                      placeItems: 'center',
                      borderRadius: '50%',
                      border: `1.5px solid ${on ? p.link : p.faint}`,
                    }}
                  >
                    {on && (
                      <span
                        style={{
                          width: 8,
                          height: 8,
                          borderRadius: '50%',
                          background: p.link,
                        }}
                      />
                    )}
                  </span>
                  <span style={{ fontSize: 13, fontWeight: 500, color: p.ink }}>
                    {d === 'redUp' ? '涨红跌绿' : '涨绿跌红'}
                  </span>
                </span>
                <span
                  className="num"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    fontSize: 12,
                  }}
                >
                  <span
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 3,
                      color: up,
                    }}
                  >
                    <TrendingUp size={14} />
                    +12.5%
                  </span>
                  <span
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 3,
                      color: down,
                    }}
                  >
                    <TrendingDown size={14} />
                    -3.7%
                  </span>
                </span>
              </span>
            );
          }}
        />,
      )}
    </div>
  );
};

/** 顶栏的主题设置按钮（色卡图标，不用明暗图标），点开弹出层 */
const TRIGGER_ID = 'zhul-theme-settings';

export const ThemeSettings: React.FC = () => {
  const [open, setOpen] = useState(false);
  // 关闭后焦点回到按钮
  const close = () => {
    setOpen(false);
    // 等弹出层关掉（面板卸载）后再移焦点，否则焦点会随面板一起落回 body
    window.setTimeout(() => document.getElementById(TRIGGER_ID)?.focus());
  };
  return (
    <Popover
      trigger="click"
      placement="bottomRight"
      open={open}
      onOpenChange={setOpen}
      arrow={false}
      destroyOnHidden
      content={<ThemeSettingsPanel onClose={close} />}
    >
      <IconButton
        id={TRIGGER_ID}
        label="主题设置"
        icon={<SwatchBook size={20} />}
        pressed={open}
        tip={!open}
      />
    </Popover>
  );
};
