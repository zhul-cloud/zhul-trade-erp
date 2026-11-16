import { request } from '@umijs/max';
import { useCallback, useEffect, useRef, useState } from 'react';
import { readBizError } from './service';

interface TranslationResult {
  taskId: number;
  /** 1-排队中、2-处理中、3-已完成、4-失败 */
  status: number;
  errorMessage?: string;
  results?: { key: string; text: string }[];
}

const API = '/api/v1/masterdata/customers/name-translation';
const quiet = { skipErrorHandler: true } as const;
const POLL_MS = 1500;
/** 最长等 20 秒 */
const MAX_POLLS = 14;

/** 本次翻译的结果：成功给译名，失败给原因 */
export type NameTranslation = { text: string } | { error: string };

/**
 * 客户编辑页「AI 翻译」（spec master-data/customer「客户英文名称自动翻译中文」）：
 * 提交翻译后轮询结果，最长 20 秒；译名只交给调用方填进输入框，不写库。页面离开时停止轮询。
 */
export const useNameTranslation = () => {
  const [running, setRunning] = useState(false);
  const timer = useRef<number | undefined>(undefined);
  const alive = useRef(true);

  useEffect(
    () => () => {
      alive.current = false;
      window.clearTimeout(timer.current);
    },
    [],
  );

  const translate = useCallback(
    (name: string): Promise<NameTranslation> =>
      new Promise((resolve) => {
        const finish = (r: NameTranslation) => {
          setRunning(false);
          resolve(r);
        };
        setRunning(true);
        request<{ data: TranslationResult }>(API, {
          method: 'POST',
          data: { name },
          ...quiet,
        })
          .then(({ data: started }) => {
            let polls = 0;
            const poll = async () => {
              if (!alive.current) return;
              try {
                const { data: r } = await request<{
                  data: TranslationResult;
                }>(`${API}/${started.taskId}`, { method: 'GET', ...quiet });
                const text = r.results?.[0]?.text;
                if (r.status === 3) {
                  finish(
                    text ? { text } : { error: '没有得到译名，请稍后再试' },
                  );
                  return;
                }
                if (r.status === 4) {
                  finish({
                    error: `翻译失败：${r.errorMessage || '原因未知'}，请稍后再试`,
                  });
                  return;
                }
                polls += 1;
                if (polls >= MAX_POLLS) {
                  finish({ error: '翻译超时，请稍后再试' });
                  return;
                }
                timer.current = window.setTimeout(poll, POLL_MS);
              } catch (e) {
                finish({ error: readBizError(e).message });
              }
            };
            timer.current = window.setTimeout(poll, POLL_MS);
          })
          .catch((e) => finish({ error: readBizError(e).message }));
      }),
    [],
  );

  return { running, translate };
};
