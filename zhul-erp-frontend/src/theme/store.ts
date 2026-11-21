import { useSyncExternalStore } from 'react';
import {
  DEFAULT_PRESET,
  isPresetKey,
  type PresetKey,
  type ThemeMode,
} from './palette';

// 主题设置（模式、主题色、涨跌颜色）每人自选，存在浏览器里，全站共用：外壳、所有页面读同一份，切换时同时刷新。
// 读不到、值不认识或存储不可用时用默认值，存不了就只在当前页面生效。

const listeners = new Set<() => void>();
const notify = () => {
  for (const l of listeners) l();
};
const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => listeners.delete(l);
};

const load = <T>(keys: string[], ok: (v: unknown) => v is T, fallback: T) => {
  try {
    for (const k of keys) {
      const v = localStorage.getItem(k);
      if (ok(v)) return v;
    }
  } catch {
    /* 存储不可用 */
  }
  return fallback;
};

const save = (key: string, value: string) => {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* 存不了就只在当前页面生效 */
  }
};

// ———— 模式：跟随系统、浅色、深色。没有选过时按浅色显示。
// 之前只有商品页用过 zhul_product_theme，读取时兼容它，用户已选的主题不丢。

export type ModePref = 'system' | 'light' | 'dark';

const MODE_KEY = 'zhul_theme';
const LEGACY_MODE_KEY = 'zhul_product_theme';
const isModePref = (v: unknown): v is ModePref =>
  v === 'system' || v === 'light' || v === 'dark';

const darkQuery = (): MediaQueryList | undefined => {
  try {
    return window.matchMedia?.('(prefers-color-scheme: dark)') ?? undefined;
  } catch {
    return undefined;
  }
};

let modePref: ModePref = load([MODE_KEY, LEGACY_MODE_KEY], isModePref, 'light');
let systemDark = !!darkQuery()?.matches;
darkQuery()?.addEventListener?.('change', (e) => {
  systemDark = e.matches;
  if (modePref === 'system') notify();
});

/** 实际显示的模式：跟随系统时按操作系统的深浅色 */
export const getThemeMode = (): ThemeMode =>
  modePref === 'system' ? (systemDark ? 'dark' : 'light') : modePref;

export const getModePref = () => modePref;

export function setModePref(next: ModePref) {
  if (next === modePref) return;
  modePref = next;
  save(MODE_KEY, next);
  notify();
}

/** 直接指定浅色或深色（登录页的切换按钮用） */
export const setThemeMode = (next: ThemeMode) => setModePref(next);

export const toggleThemeMode = () =>
  setModePref(getThemeMode() === 'dark' ? 'light' : 'dark');

export const useThemeMode = (): ThemeMode =>
  useSyncExternalStore(subscribe, getThemeMode);

export const useModePref = (): ModePref =>
  useSyncExternalStore(subscribe, getModePref);

// ———— 主题色：默认琥珀

const PRESET_KEY = 'zhul_theme_preset';

let preset: PresetKey = load([PRESET_KEY], isPresetKey, DEFAULT_PRESET);

export const getThemePreset = () => preset;

export function setThemePreset(next: PresetKey) {
  if (next === preset) return;
  preset = next;
  save(PRESET_KEY, next);
  notify();
}

export const useThemePreset = (): PresetKey =>
  useSyncExternalStore(subscribe, getThemePreset);

// ———— 涨跌颜色：涨红跌绿（默认）或涨绿跌红，只按方向着色，告警色不受影响

export type DeltaPref = 'redUp' | 'greenUp';

const DELTA_KEY = 'zhul_delta_color';
const isDeltaPref = (v: unknown): v is DeltaPref =>
  v === 'redUp' || v === 'greenUp';

let delta: DeltaPref = load([DELTA_KEY], isDeltaPref, 'redUp');

export const getDeltaPref = () => delta;

export function setDeltaPref(next: DeltaPref) {
  if (next === delta) return;
  delta = next;
  save(DELTA_KEY, next);
  notify();
}

export const useDeltaPref = (): DeltaPref =>
  useSyncExternalStore(subscribe, getDeltaPref);
