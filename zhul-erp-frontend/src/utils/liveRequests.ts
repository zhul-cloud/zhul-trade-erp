/**
 * 弹窗、抽屉打开时按对象取数用（audit UI-054 / UI-056）：effect 清理时 cancel()，
 * 之后用 live() 包着的请求无论成功还是失败都不再回调，上一个对象迟到的响应不会覆盖当前对象。
 *
 * useEffect(() => {
 *   if (!open) return undefined;
 *   const { live, cancel } = liveRequests();
 *   live(api.detail(id)).then(setData);
 *   return cancel;
 * }, [open, id]);
 */
export const liveRequests = () => {
  let alive = true;
  return {
    live: <T>(request: Promise<T>) =>
      new Promise<T>((resolve, reject) => {
        request.then(
          (value) => {
            if (alive) resolve(value);
          },
          (error) => {
            if (alive) reject(error);
          },
        );
      }),
    cancel: () => {
      alive = false;
    },
  };
};
