import dayjs from 'dayjs';
import { Image as ImageIcon, Play } from 'lucide-react';
import React, { useEffect, useState } from 'react';
import { AlertLabel } from '@/components/MetricCard';
import { Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';
import type { FileLink } from '@/pages/operations/media/service';
import { useAppTheme } from '@/theme/AppTheme';
import {
  type VideoStatus,
  type VideoTab,
  type VideoTask,
  videoApi,
} from './service';

export const TABS: { key: VideoTab; label: string; empty: string }[] = [
  {
    key: 'all',
    label: '全部',
    empty: '还没有视频任务，点右上角「上传素材」开始',
  },
  { key: 'pending', label: '待生成', empty: '没有待生成的任务' },
  { key: 'queued', label: '排队中', empty: '队列是空的' },
  { key: 'review', label: '待审核', empty: '没有待审核的视频' },
  { key: 'approved', label: '已通过', empty: '还没有审核通过的视频' },
  { key: 'rejected', label: '已退回', empty: '没有退回的任务' },
  { key: 'failed', label: '生成失败', empty: '没有生成失败的任务' },
];

export const STATUS_TONE: Record<VideoStatus, Tone> = {
  1: 'gray',
  2: 'info',
  3: 'accent',
  4: 'orange',
  5: 'green',
  6: 'red',
  7: 'red',
};

/** 状态与下面一行说明（排队位置、失败或退回原因、选用不够） */
export const StatusCell: React.FC<{ task: VideoTask }> = ({ task: t }) => {
  const { palette } = useAppTheme();
  let note: React.ReactNode = null;
  let warn = false;
  if (t.status === 2 && t.expectedAt) {
    note = `预计 ${dayjs(t.expectedAt).format('HH:mm')} 开始`;
  } else if (t.status === 3) {
    note = '生成中，约几分钟';
  } else if (t.status === 6 && t.rejectReason) {
    // 退回、失败的原因：状态标签已是错误色，原因用次要色文字，不再一层彩字
    note = t.rejectReason;
  } else if (t.status === 7 && t.failReason) {
    note = t.failReason;
  } else if (t.status === 1 && t.usedCount < 4) {
    // 要处理的提醒做成带图标的浅底标签（规范「警告不用纯文字」）
    note = `选用 ${t.usedCount} 张，至少要 4 张`;
    warn = true;
  }
  return (
    <div>
      <Pill tone={STATUS_TONE[t.status]}>
        {t.statusName}
        {t.status === 2 && t.queuePosition ? ` · 第 ${t.queuePosition} 位` : ''}
      </Pill>
      {note &&
        (warn ? (
          <div style={{ marginTop: 4 }}>
            <AlertLabel tone="warning">{note}</AlertLabel>
          </div>
        ) : (
          <div
            style={{
              fontSize: 12,
              color: palette.sub,
              marginTop: 4,
              maxWidth: 220,
            }}
          >
            {note}
          </div>
        ))}
    </div>
  );
};

/** 一次取一个任务里若干附件的预览链接；ids 变化时重新取 */
export const useTaskLinks = (taskId: number | undefined, ids: number[]) => {
  const key = ids.join(',');
  const [links, setLinks] = useState<Record<number, FileLink>>({});
  useEffect(() => {
    if (!taskId || !key) {
      setLinks({});
      return;
    }
    let alive = true;
    const list = key.split(',').map(Number);
    videoApi
      .links(taskId, list)
      .then((r) => {
        if (!alive) return;
        const m: Record<number, FileLink> = {};
        list.forEach((id, i) => {
          if (r[i]) m[id] = r[i];
        });
        setLinks(m);
      })
      .catch(() => alive && setLinks({}));
    return () => {
      alive = false;
    };
  }, [taskId, key]);
  return links;
};

/** 缩略图：有链接显示图片，没有显示占位图标 */
export const Thumb: React.FC<{
  url?: string;
  size?: number;
  width?: number;
  height?: number;
  video?: boolean;
  onClick?: () => void;
  title?: string;
}> = ({ url, size = 32, width, height, video, onClick, title }) => {
  const { palette } = useAppTheme();
  const w = width ?? size;
  const h = height ?? size;
  return (
    <span
      title={title}
      onClick={onClick}
      style={{
        position: 'relative',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: w,
        height: h,
        borderRadius: 6,
        overflow: 'hidden',
        background: palette.inset,
        border: `1px solid ${palette.hairline}`,
        cursor: onClick ? 'pointer' : undefined,
        flex: 'none',
      }}
    >
      {url ? (
        <img
          src={url}
          alt={title ?? ''}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      ) : (
        <ImageIcon size={Math.min(w, h) / 2} style={{ color: palette.mute }} />
      )}
      {video && (
        // 播放标记：卡片底色小圆配正文色图标，照片深浅都看得清，不用写死白色和阴影
        <span
          aria-hidden
          style={{
            position: 'absolute',
            width: Math.min(w, h) * 0.6,
            height: Math.min(w, h) * 0.6,
            borderRadius: '50%',
            display: 'grid',
            placeItems: 'center',
            background: palette.card,
            color: palette.ink,
          }}
        >
          <Play size={Math.min(w, h) * 0.32} fill="currentColor" />
        </span>
      )}
    </span>
  );
};

/** 时段是否包含当前时间（与后端 VideoWindow 一致：含开始、不含结束，可跨零点，开始等于结束表示全天） */
export const windowOpen = (window: string, now = dayjs()) => {
  const m = /^(\d{2}):(\d{2})-(\d{2}):(\d{2})$/.exec(window);
  if (!m) return false;
  const start = Number(m[1]) * 60 + Number(m[2]);
  const end = Number(m[3]) * 60 + Number(m[4]);
  const t = now.hour() * 60 + now.minute();
  if (start === end) return true;
  return start < end ? t >= start && t < end : t >= start || t < end;
};
