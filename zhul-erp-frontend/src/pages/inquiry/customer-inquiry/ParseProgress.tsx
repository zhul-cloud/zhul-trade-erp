import {
  CheckCircleOutlined,
  LoadingOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Progress } from 'antd';
import React from 'react';
import { Pill } from '@/pages/crm/opportunity/components';
import { useAppTheme } from '@/theme/AppTheme';
import type { UnparsedPart } from '../shared/service';

/** 分批解析的四个阶段，顺序即编排服务的执行顺序 */
const STAGES = [
  { key: 'read', label: '读取附件' },
  { key: 'extract', label: '提取型号' },
  { key: 'merge', label: '合并去重' },
  { key: 'verify', label: '联网核验' },
] as const;

/**
 * 当前阶段的序号；没有阶段（旧版编排服务只报批数）时按「提取型号」处理。
 * 进度条的总批数在开始联网核验时才加上核验批数，阶段卡片说明为什么进度会回落
 */
const stageIndex = (stage?: string) => {
  const i = STAGES.findIndex((s) => s.key === stage);
  return i < 0 ? 1 : i;
};

/** 大附件分批解析中的进度（原型 11-10询盘大附件 S3）：已解析批数 / 总批数与四个阶段，详情页每隔几秒刷新 */
export const ParseProgressCard: React.FC<{
  done: number;
  total: number;
  stage?: string;
  /** 询盘附件个数，「读取附件」完成后显示 */
  files: number;
}> = ({ done, total, stage, files }) => {
  const { palette } = useAppTheme();
  const current = stageIndex(stage);
  const reading = current === 0;
  return (
    <div
      style={{
        marginBottom: 16,
        padding: '16px 20px',
        borderRadius: 12,
        border: `1px solid ${palette.hairline}`,
        background: palette.card,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <Pill tone="accent">解析中</Pill>
        <span style={{ color: palette.ink, fontSize: 16, fontWeight: 600 }}>
          {reading ? '正在读取附件' : `已解析 ${done} / ${total} 批`}
        </span>
      </div>
      <Progress
        percent={reading || !total ? 0 : Math.floor((done / total) * 100)}
        showInfo={false}
        strokeColor={{ from: '#3B82F6', to: '#22D3EE' }}
        style={{ margin: '10px 0 4px' }}
      />
      <div style={{ color: palette.sub, fontSize: 13 }}>
        附件较大，正在分批解析。可以先离开页面，完成后变为「待确认」。
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
          gap: 10,
          marginTop: 14,
        }}
      >
        {STAGES.map((s, i) => {
          const state =
            i < current ? 'done' : i === current ? 'active' : 'wait';
          const detail =
            state === 'wait'
              ? '等待中'
              : state === 'active'
                ? '进行中'
                : i === 0
                  ? `${files} 个文件`
                  : '已完成';
          return (
            <div
              key={s.key}
              data-stage={s.key}
              data-state={state}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '10px 14px',
                borderRadius: 10,
                background: palette.inset,
              }}
            >
              {state === 'done' && (
                <CheckCircleOutlined
                  style={{ color: palette.green, fontSize: 18 }}
                />
              )}
              {state === 'active' && (
                <LoadingOutlined
                  style={{ color: palette.link, fontSize: 18 }}
                />
              )}
              {state === 'wait' && (
                <span
                  style={{
                    width: 16,
                    height: 16,
                    margin: 1,
                    borderRadius: '50%',
                    border: `1.5px solid ${palette.faint}`,
                    flex: 'none',
                  }}
                />
              )}
              <div>
                <div
                  style={{ color: palette.ink, fontSize: 14, fontWeight: 600 }}
                >
                  {s.label}
                </div>
                <div style={{ color: palette.mute, fontSize: 12 }}>
                  {detail}
                </div>
              </div>
            </div>
          );
        })}
      </div>
      <div style={{ color: palette.mute, fontSize: 12, marginTop: 12 }}>
        长时间没有进展或总时长超限时，会判为解析失败，可以重新解析。
      </div>
    </div>
  );
};

/** 「《清单.pdf》第 15 页：页面损坏，无法读取」；没有文件名时（某一批）直接用范围 */
export const unparsedText = (u: UnparsedPart) =>
  `${u.file ? `《${u.file}》` : ''}${u.range ?? ''}：${u.reason}`;

/** 确认页顶部：AI 没能解析的部分，请业务员自行核对补充（原型 11-10询盘大附件 S4） */
export const UnparsedAlert: React.FC<{ parts?: UnparsedPart[] }> = ({
  parts,
}) => {
  const { palette } = useAppTheme();
  if (!parts?.length) return null;
  return (
    <div
      style={{
        marginBottom: 16,
        padding: '14px 18px',
        borderRadius: 12,
        background: palette.orangeSoft,
      }}
    >
      <div style={{ color: palette.ink, fontWeight: 600 }}>
        <WarningOutlined style={{ color: palette.orange, marginRight: 8 }} />
        {parts.length} 处内容未能解析，请手动补充
      </div>
      <ul style={{ margin: '8px 0 0', paddingLeft: 22, color: palette.sub }}>
        {parts.map((u) => (
          <li key={unparsedText(u)}>{unparsedText(u)}</li>
        ))}
      </ul>
    </div>
  );
};
