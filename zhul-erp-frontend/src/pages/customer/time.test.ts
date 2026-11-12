import { localTimeInfo } from './time';

/** UI-016：时差用同一时刻两地的 UTC 偏移相减，跨日的也不折回 */
it.each([
  // 北京 20:00 时洛杉矶是前一天 04:00，比北京晚 16 小时
  ['America/Los_Angeles', '04:00', -16],
  ['Europe/Berlin', '13:00', -7],
  ['Asia/Kolkata', '17:30', -2.5],
  ['Asia/Tokyo', '21:00', 1],
  ['Pacific/Auckland', '01:00', 5],
  ['Asia/Shanghai', '20:00', 0],
])('%s：当地 %s，比北京差 %s 小时', (zone, time, diff) => {
  const info = localTimeInfo(zone, new Date('2026-01-15T12:00:00Z'));
  expect(info.time).toBe(time);
  expect(info.diffHours).toBe(diff);
});
