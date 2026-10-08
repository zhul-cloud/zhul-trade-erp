import { TranslationOutlined } from '@ant-design/icons';
import { request } from '@umijs/max';
import { Alert, App, Button, Input } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { readBizError } from '@/pages/crm/opportunity/service';
import { useAppTheme } from '@/theme/AppTheme';

/**
 * 型号描述中英文两份（spec add-bilingual-item-description）：系统内显示中文，发给客户的单据用英文。
 * 报价单、PI 编辑页共用：两个输入框、缺英文提示条、「生成英文描述」的提交与轮询。
 */

const CJK = /[⺀-鿿]/;

/** 英文描述为空或含中文：导出会退回中文，需要提示 */
export const needsEnglish = (en?: string | null) => !en?.trim() || CJK.test(en);

const label = (color: string, tag: string, text: string) => (
  <div style={{ fontSize: 12, color, marginBottom: 4 }}>
    <b style={{ marginRight: 6 }}>{tag}</b>
    {text}
  </div>
);

export const DescriptionInputs: React.FC<{
  zh?: string;
  en?: string;
  onZh: (v: string) => void;
  onEn: (v: string) => void;
  /** 刚由「生成英文描述」填入，提示检查 */
  generated?: boolean;
}> = ({ zh, en, onZh, onEn, generated }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ display: 'grid', gap: 8 }}>
      <div>
        {label(palette.mute, '中', '描述（系统内显示）')}
        <Input.TextArea
          value={zh}
          maxLength={300}
          autoSize={{ minRows: 1, maxRows: 4 }}
          onChange={(e) => onZh(e.target.value)}
          aria-label="中文描述"
        />
      </div>
      <div>
        {label(
          palette.link,
          'EN',
          generated
            ? '英文描述（给客户看，导出用）· 刚生成，请检查'
            : '英文描述（给客户看，导出用）',
        )}
        <Input.TextArea
          value={en}
          maxLength={300}
          autoSize={{ minRows: 1, maxRows: 4 }}
          status={generated ? 'warning' : undefined}
          placeholder="还没有英文描述，导出会显示中文"
          onChange={(e) => onEn(e.target.value)}
          aria-label="英文描述"
        />
      </div>
    </div>
  );
};

// ---------------------------------------------------------------- 生成英文描述

interface TranslationResult {
  taskId: number;
  /** 1-排队中、2-处理中、3-已完成、4-失败 */
  status: number;
  errorMessage?: string;
  results?: { key: string; text: string }[];
}

const API = '/api/v1/translations/item-descriptions';
const quiet = { skipErrorHandler: true } as const;
const POLL_MS = 2000;
const MAX_POLLS = 180;

export interface TranslateItem {
  key: string;
  text: string;
  inquiryItemId?: number | null;
}

/**
 * 提交翻译任务并轮询；完成后把结果交给 onDone（key → 英文）。
 * 页面离开时停止轮询。
 */
export const useDescriptionTranslation = () => {
  const { message } = App.useApp();
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

  const run = useCallback(
    async (
      items: TranslateItem[],
      onDone: (texts: Map<string, string>) => void,
    ) => {
      if (items.length === 0) return;
      setRunning(true);
      try {
        const started = await request<{ data: TranslationResult }>(API, {
          method: 'POST',
          data: { items },
          ...quiet,
        }).then((r) => r.data);
        let polls = 0;
        const poll = async () => {
          if (!alive.current) return;
          try {
            const r = await request<{ data: TranslationResult }>(
              `${API}/${started.taskId}`,
              { method: 'GET', ...quiet },
            ).then((x) => x.data);
            if (r.status === 3) {
              setRunning(false);
              onDone(new Map((r.results ?? []).map((x) => [x.key, x.text])));
              return;
            }
            if (r.status === 4) {
              setRunning(false);
              message.error(
                `生成英文描述失败：${r.errorMessage || '原因未知'}，可以重试`,
              );
              return;
            }
            polls += 1;
            if (polls >= MAX_POLLS) {
              setRunning(false);
              message.error('生成英文描述超时，可以重试');
              return;
            }
            timer.current = window.setTimeout(poll, POLL_MS);
          } catch (e) {
            setRunning(false);
            message.error(readBizError(e).message);
          }
        };
        timer.current = window.setTimeout(poll, POLL_MS);
      } catch (e) {
        setRunning(false);
        message.error(readBizError(e).message);
      }
    },
    [message],
  );

  return { running, run };
};

/** 缺英文描述的提示条与生成按钮 */
export const MissingEnglishBanner: React.FC<{
  count: number;
  running: boolean;
  onGenerate: () => void;
}> = ({ count, running, onGenerate }) =>
  count > 0 || running ? (
    <Alert
      type={running ? 'info' : 'warning'}
      showIcon
      style={{ marginBottom: 10 }}
      title={
        running
          ? `正在生成 ${count} 个型号的英文描述，大约需要半分钟，可以继续编辑其他内容`
          : `${count} 个型号还没有英文描述，导出会显示中文`
      }
      description={
        running
          ? undefined
          : '可以让 AI 把中文描述翻译成英文（品牌用英文名，型号与参数原样保留），生成后请检查再保存'
      }
      action={
        <Button
          type="primary"
          size="small"
          icon={<TranslationOutlined />}
          loading={running}
          disabled={count === 0}
          onClick={onGenerate}
        >
          {running ? '生成中' : '生成英文描述'}
        </Button>
      }
    />
  ) : null;
