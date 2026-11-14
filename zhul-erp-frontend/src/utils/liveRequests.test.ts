import { liveRequests } from './liveRequests';

const flush = () => new Promise((r) => setTimeout(r, 0));

/** audit UI-054 / UI-056：作废后迟到的成功与失败都不再回调；未作废时照常 */
describe('liveRequests', () => {
  it('作废前完成的请求照常回调', async () => {
    const { live } = liveRequests();
    await expect(live(Promise.resolve(1))).resolves.toBe(1);
    await expect(live(Promise.reject(new Error('x')))).rejects.toThrow('x');
  });

  it('作废后迟到的成功与失败都丢弃', async () => {
    const { live, cancel } = liveRequests();
    let ok!: (v: number) => void;
    let fail!: (e: Error) => void;
    const seen: string[] = [];
    live(new Promise<number>((r) => (ok = r))).then(() => seen.push('ok'));
    live(new Promise<number>((_, r) => (fail = r))).catch(() =>
      seen.push('error'),
    );
    cancel();
    ok(1);
    fail(new Error('late'));
    await flush();
    expect(seen).toEqual([]);
  });
});
