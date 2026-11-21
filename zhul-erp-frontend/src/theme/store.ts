import { useSyncExternalStore } from 'react';
import {
  DEFAULT_PRESET,
  isPresetKey,
  type PresetKey,
  type ThemeMode,
} from './palette';

// 主题选择存在浏览器里，全站共用：外壳、所有页面读同一份，切换时同时刷新。
// 之前只有商品页用过 zhul_product_theme，读取时兼容它，用户已选的主题不丢。
const KEY = 'zhul_theme';
const LEGACY_KEY = 'zhul_product_theme';

// 默认浅色；用户自己切到深色的保留深色
const read = (): ThemeMode => {
  try {
    const v = localStorage.getItem(KEY) ?? localStorage.getItem(LEGACY_KEY);
    return v === 'dark' ? 'dark' : 'light';
  } catch {
    return 'light';
  }
};

let mode: ThemeMode = read();
const listeners = new Set<() => void>();

export const getThemeMode = () => mode;

export function setThemeMode(next: ThemeMode) {
  if (next === mode) return;
  mode = next;
  try {
    localStorage.setItem(KEY, next);
  } catch {
    /* 存不了就只在当前页面生效 */
  }
  for (const l of listeners) l();
}

export const toggleThemeMode = () =>
  setThemeMode(mode === 'dark' ? 'light' : 'dark');

const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => listeners.delete(l);
};

export const useThemeMode = (): ThemeMode =>
  useSyncExternalStore(subscribe, getThemeMode);

// 主题色：每人自选，存在浏览器里；读不到或值不认识时用默认（琥珀）
const PRESET_KEY = 'zhul_theme_preset';

const readPreset = (): PresetKey => {
  try {
    const v = localStorage.getItem(PRESET_KEY);
    return isPresetKey(v) ? v : DEFAULT_PRESET;
  } catch {
    return DEFAULT_PRESET;
  }
};

let preset: PresetKey = readPreset();

export const getThemePreset = () => preset;

export function setThemePreset(next: PresetKey) {
  if (next === preset) return;
  preset = next;
  try {
    localStorage.setItem(PRESET_KEY, next);
  } catch {
    /* 存不了就只在当前页面生效 */
  }
  for (const l of listeners) l();
}

export const useThemePreset = (): PresetKey =>
  useSyncExternalStore(subscribe, getThemePreset);
