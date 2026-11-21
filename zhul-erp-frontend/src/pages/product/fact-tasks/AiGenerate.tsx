import { CodeSandboxOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { App, Button, Modal } from 'antd';
import dayjs from 'dayjs';
import React, { useState } from 'react';
import { Pill } from '@/pages/product/components/Pills';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  AI_STATUS,
  type ContentAiJob,
  type ContentTask,
  contentApi,
  readBizError,
} from '../service';

/** 资料任务、营销任务的 AI 生成（spec product/product-content「AI 生成资料与营销内容」） */

/** 一次最多发起的商品数，与后端一致 */
export const AI_MAX_BATCH = 20;

/** 排队中、生成中、待确认：不能再次发起 */
export const aiBusy = (job?: ContentAiJob) =>
  !!job &&
  (job.status === AI_STATUS.QUEUED ||
    job.status === AI_STATUS.RUNNING ||
    job.status === AI_STATUS.PENDING);

/** 列表要不要定时刷新：有排队中或生成中的 */
export const aiWorking = (rows: ContentTask[]) =>
  rows.some(
    (r) =>
      r.ai?.status === AI_STATUS.QUEUED || r.ai?.status === AI_STATUS.RUNNING,
  );

/** 已用时长：2 分 14 秒、1 小时 3 分 */
export const elapsed = (from?: string, now = dayjs()) => {
  if (!from) return '';
  const s = Math.max(0, now.diff(dayjs(from), 'second'));
  if (s < 60) return `${s} 秒`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m} 分 ${s % 60} 秒`;
  return `${Math.floor(m / 60)} 小时 ${m % 60} 分`;
};

type Tone = 'gray' | 'accent' | 'green' | 'red' | 'violet';
const META: Record<number, { label: string; tone: Tone }> = {
  [AI_STATUS.QUEUED]: { label: '排队中', tone: 'gray' },
  [AI_STATUS.RUNNING]: { label: '生成中', tone: 'violet' },
  [AI_STATUS.PENDING]: { label: '待确认', tone: 'accent' },
  [AI_STATUS.IMPORTED]: { label: '已导入', tone: 'green' },
  [AI_STATUS.FAILED]: { label: '失败', tone: 'red' },
  [AI_STATUS.CANCELLED]: { label: '已取消', tone: 'gray' },
};

/** 列表「AI 生成」列：状态 + 一行说明 + 操作（查看结果、取消、重新生成） */
export const AiCell: React.FC<{
  job?: ContentAiJob;
  canEdit: boolean;
  onView: (job: ContentAiJob) => void;
  onCancel: (job: ContentAiJob) => void;
  onRetry: () => void;
}> = ({ job, canEdit, onView, onCancel, onRetry }) => {
  const { palette } = useAppTheme();
  if (!job) return <span style={{ color: palette.mute }}>—</span>;
  const meta = META[job.status] ?? META[AI_STATUS.CANCELLED];
  const link = (text: string, onClick: () => void) => (
    <button type="button" className="zhul-link" onClick={onClick}>
      {text}
    </button>
  );
  let line: React.ReactNode = null;
  if (job.status === AI_STATUS.QUEUED) {
    line = (
      <>
        {job.queueAhead ? `前面还有 ${job.queueAhead} 个` : '马上开始'}
        {canEdit && <> · {link('取消', () => onCancel(job))}</>}
      </>
    );
  } else if (job.status === AI_STATUS.RUNNING) {
    line = (
      <>
        已用 {elapsed(job.startedAt)}
        {canEdit && <> · {link('取消', () => onCancel(job))}</>}
      </>
    );
  } else if (job.status === AI_STATUS.PENDING) {
    line = (
      <>
        {formatDateTime(job.completedAt)} 完成 ·{' '}
        {link('查看结果', () => onView(job))}
      </>
    );
  } else if (job.status === AI_STATUS.IMPORTED) {
    line = `${formatDateTime(job.confirmedAt)} · ${job.confirmedBy ?? ''}确认`;
  } else if (job.status === AI_STATUS.FAILED) {
    line = (
      <>
        <span title={job.failReason}>{job.failReason || 'AI 处理失败'}</span>
        {canEdit && <> · {link('重新生成', onRetry)}</>}
      </>
    );
  } else {
    line = job.failReason || '已取消';
  }
  return (
    <div style={{ display: 'grid', gap: 4, justifyItems: 'start' }}>
      <Pill tone={meta.tone}>{meta.label}</Pill>
      <div
        style={{
          fontSize: 12,
          color: palette.mute,
          maxWidth: 220,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {line}
      </div>
    </div>
  );
};

/** 发起 AI 生成前的确认：列出会生成的商品、跳过的商品和计费说明 */
export const AiStartModal: React.FC<{
  rows?: ContentTask[];
  onClose: () => void;
  onStarted: () => void;
}> = ({ rows, onClose, onStarted }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [starting, setStarting] = useState(false);
  const list = rows ?? [];
  const todo = list.filter((r) => !aiBusy(r.ai));
  const skipped = list.filter((r) => aiBusy(r.ai));
  const label = (t: ContentTask) => `${t.brandName ?? ''} · ${t.mpn}`;

  const start = async () => {
    setStarting(true);
    try {
      const res = await contentApi.aiStart(todo.map((r) => r.productId));
      message.success(
        `已开始 AI 生成 ${res.created} 个商品${res.skipped.length ? `，跳过 ${res.skipped.length} 个` : ''}`,
      );
      onStarted();
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setStarting(false);
    }
  };

  return (
    <Modal
      open={!!rows}
      onCancel={onClose}
      width={520}
      destroyOnHidden
      title={
        <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <ThunderboltOutlined style={{ color: palette.info }} />
          AI 生成 {todo.length} 个商品
        </span>
      }
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="ok"
          type="primary"
          icon={<ThunderboltOutlined />}
          loading={starting}
          disabled={todo.length === 0}
          onClick={start}
        >
          开始生成（{todo.length}）
        </Button>,
      ]}
    >
      <p style={{ color: palette.sub }}>
        每个商品生成一份中文资料包，以及本公司启用平台的营销包。
      </p>
      <div
        style={{
          display: 'grid',
          borderRadius: 12,
          background: palette.inset,
          maxHeight: 280,
          overflow: 'auto',
        }}
      >
        {todo.map((r) => (
          <div
            key={r.productId}
            style={{
              display: 'flex',
              gap: 10,
              alignItems: 'center',
              padding: '10px 12px',
            }}
          >
            <CodeSandboxOutlined style={{ color: palette.mute }} />
            <div style={{ minWidth: 0 }}>
              <div style={{ fontWeight: 600, color: palette.ink }}>
                {label(r)}
              </div>
              <div style={{ fontSize: 12, color: palette.mute }}>
                {r.ai?.status === AI_STATUS.FAILED
                  ? '上次失败，重新生成'
                  : r.ai?.status === AI_STATUS.IMPORTED
                    ? '已导入过，生成新结果后再确认'
                    : r.productName || '—'}
              </div>
            </div>
          </div>
        ))}
      </div>
      {skipped.length > 0 && (
        <div style={{ marginTop: 10, fontSize: 12, color: palette.sub }}>
          {skipped.map(label).join('、')} 正在生成或有待确认的结果，已跳过。
        </div>
      )}
      <div style={{ marginTop: 10, fontSize: 12, color: palette.mute }}>
        按本公司 AI
        设置的连接方式顺序使用，会产生相应费用。每个商品需要几分钟，生成在后台进行，可以离开页面。
      </div>
    </Modal>
  );
};
