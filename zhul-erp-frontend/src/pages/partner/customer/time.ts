/** IANA 时区的当前 UTC 偏移，如 Europe/Berlin → UTC+01:00；无法识别时返回空串 */
export function utcOffsetLabel(zone: string, at: Date = new Date()): string {
  try {
    const part = new Intl.DateTimeFormat('en-US', {
      timeZone: zone,
      timeZoneName: 'shortOffset',
    })
      .formatToParts(at)
      .find((p) => p.type === 'timeZoneName')?.value;
    if (!part) return '';
    const m = /GMT([+-])(\d{1,2})(?::(\d{2}))?/.exec(part);
    if (!m) return 'UTC+00:00';
    return `UTC${m[1]}${m[2].padStart(2, '0')}:${m[3] ?? '00'}`;
  } catch {
    return '';
  }
}

/** 下拉里显示的时区名：Europe/Berlin（UTC+01:00） */
export function zoneLabel(zone: string): string {
  const offset = utcOffsetLabel(zone);
  return offset ? `${zone}（${offset}）` : zone;
}

/** 当地时间（HH:mm）、与北京时间的时差（小时，负数表示比北京晚）、是否在当地 9:00–18:00 */
export function localTimeInfo(zone: string, now: Date = new Date()) {
  const fmt = (tz: string) =>
    new Intl.DateTimeFormat('en-GB', {
      timeZone: tz,
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(now);
  const minutesOf = (hhmm: string) => {
    const [h, m] = hhmm.split(':').map(Number);
    return h * 60 + m;
  };
  const local = fmt(zone);
  let diff = minutesOf(local) - minutesOf(fmt('Asia/Shanghai'));
  if (diff > 12 * 60) diff -= 24 * 60;
  if (diff < -12 * 60) diff += 24 * 60;
  const localMinutes = minutesOf(local);
  return {
    time: local,
    diffHours: Math.round((diff / 60) * 10) / 10,
    working: localMinutes >= 9 * 60 && localMinutes < 18 * 60,
  };
}

/** 浏览器支持的全部 IANA 时区（国家时区数据缺失时的兜底选项） */
export function allZones(): string[] {
  const intl = Intl as unknown as {
    supportedValuesOf?: (key: string) => string[];
  };
  return intl.supportedValuesOf ? intl.supportedValuesOf('timeZone') : [];
}
