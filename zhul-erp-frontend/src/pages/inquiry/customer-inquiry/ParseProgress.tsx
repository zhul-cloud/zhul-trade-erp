import { WarningOutlined } from '@ant-design/icons';
import { Progress } from 'antd';
import React from 'react';
import { Pill } from '@/pages/crm/opportunity/components';
import { useAppTheme } from '@/theme/AppTheme';
import type { UnparsedPart } from '../shared/service';

/** 大附件分批解析中的进度（原型 11-10询盘大附件 S3）：已解析批数 / 总批数，详情页每隔几秒刷新 */
export const ParseProgressCard: React.FC<{ done: number; total: number }> = ({
  done,
  total,
}) => {
  const { palette } = useAppTheme();
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
          已解析 {done} / {total} 批
        </span>
      </div>
      <Progress
        percent={Math.floor((done / total) * 100)}
        showInfo={false}
        strokeColor={{ from: '#3B82F6', to: '#22D3EE' }}
        style={{ margin: '10px 0 4px' }}
      />
      <div style={{ color: palette.sub, fontSize: 13 }}>
        附件较大，正在分批解析。可以先离开页面，完成后变为「待确认」。
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
