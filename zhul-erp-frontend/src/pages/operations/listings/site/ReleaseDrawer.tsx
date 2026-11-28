import {
  CheckCircleFilled,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleFilled,
  CopyOutlined,
  ExclamationCircleFilled,
  ExclamationCircleOutlined,
  LoadingOutlined,
  OrderedListOutlined,
  RedoOutlined,
} from '@ant-design/icons';
import { App, Button, Drawer, Skeleton } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useTabActive } from '@/components/PageTabs/hooks';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { usePermission } from '@/utils/permission';
import { readBizError } from '../service';
import {
  PERM_PUBLISH,
  RELEASE_DEPLOY_FAILED,
  RELEASE_DONE,
  RELEASE_FAILED,
  RELEASE_QUEUED,
  RELEASE_RUNNING,
  type Release,
  type ReleaseStep,
  STEPS,
  sitePublishApi,
  stepName,
} from './service';

export const MONO = "'SFMono-Regular', Menlo, Consolas, monospace";

type Tone = React.ComponentProps<typeof Pill>['tone'];
export const RELEASE_TONE: Record<number, Tone> = {
  1: 'gray',
  2: 'cyan',
  3: 'green',
  4: 'red',
  5: 'orange',
};

export const seconds = (from?: string, to?: string) => {
  if (!from) return '';
  const end = to ? new Date(to).getTime() : Date.now();
  const s = Math.max(0, Math.round((end - new Date(from).getTime()) / 1000));
  if (to) return s >= 60 ? `${Math.floor(s / 60)} 分 ${s % 60} 秒` : `${s} 秒`;
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
};

/** 链接只显示路径部分 */
const pathOf = (url: string) => {
  try {
    return new URL(url).pathname;
  } catch {
    return url;
  }
};

const StepIcon: React.FC<{ status?: number; index: number }> = ({
  status,
  index,
}) => {
  const { palette } = useAppTheme();
  const size = { fontSize: 22 };
  if (status === 2)
    return <CheckCircleFilled style={{ ...size, color: palette.green }} />;
  if (status === 3)
    return <CloseCircleFilled style={{ ...size, color: palette.red }} />;
  if (status === 4)
    return (
      <ExclamationCircleFilled style={{ ...size, color: palette.orange }} />
    );
  if (status === 1)
    return <LoadingOutlined style={{ ...size, color: palette.link }} />;
  return (
    <span
      style={{
        width: 22,
        height: 22,
        borderRadius: 11,
        border: `1px solid ${palette.hairline}`,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 11,
        color: palette.mute,
      }}
    >
      {index}
    </span>
  );
};

/** 发布单进度：步骤、商品与上线链接、失败原因与构建输出、部署失败重试 */
export const ReleaseDrawer: React.FC<{
  releaseId?: number;
  siteName?: string;
  onClose: () => void;
  /** 发布单状态变化（结束、重试）后通知页面刷新 */
  onChanged?: () => void;
}> = ({ releaseId, siteName, onClose, onChanged }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const { hasPerm } = usePermission();
  const canPublish = hasPerm(PERM_PUBLISH);
  const changed = useRef(onChanged);
  changed.current = onChanged;
  const prevStatus = useRef<number | undefined>(undefined);
  const active = useTabActive();
  const [r, setR] = useState<Release>();
  const [queue, setQueue] = useState<Release[]>([]);
  const [error, setError] = useState<string>();
  const [retrying, setRetrying] = useState(false);
  const [, tick] = useState(0);

  const load = useCallback(async () => {
    if (!releaseId) return;
    try {
      const d = await sitePublishApi.detail(releaseId);
      if (prevStatus.current !== undefined && prevStatus.current !== d.status)
        changed.current?.();
      prevStatus.current = d.status;
      setR(d);
      setError(undefined);
      if (d.status === RELEASE_QUEUED || d.status === RELEASE_RUNNING) {
        const all = await sitePublishApi.releases(d.siteId);
        setQueue(
          all
            .filter((x) => x.status === RELEASE_QUEUED && x.id > d.id)
            .reverse(),
        );
      } else setQueue([]);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [releaseId]);

  useEffect(() => {
    setR(undefined);
    setError(undefined);
    prevStatus.current = undefined;
    load();
  }, [load]);

  const running = r?.status === RELEASE_QUEUED || r?.status === RELEASE_RUNNING;
  useEffect(() => {
    if (!running || !active || !releaseId) return undefined;
    const t = window.setInterval(() => {
      load();
    }, 3000);
    const clock = window.setInterval(() => tick((n) => n + 1), 1000);
    return () => {
      window.clearInterval(t);
      window.clearInterval(clock);
    };
  }, [running, active, releaseId, load]);

  const retry = async () => {
    if (!r) return;
    setRetrying(true);
    try {
      setR(await sitePublishApi.retry(r.id));
      message.success('已从部署一步重新开始');
      changed.current?.();
      load();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setRetrying(false);
    }
  };

  // 每一步取最后一次执行的记录
  const latest = new Map<string, ReleaseStep>();
  for (const s of r?.steps ?? []) latest.set(s.step, s);
  const failedStep = r?.steps.find(
    (s) => s.status === 3 && latest.get(s.step) === s,
  );
  const terminal =
    r?.status === RELEASE_DONE ||
    r?.status === RELEASE_FAILED ||
    r?.status === RELEASE_DEPLOY_FAILED;
  const shown = STEPS.filter(
    (d) =>
      latest.has(d.key) || (d.key !== 'deploy_prepare' && d.key !== 'indexnow'),
  );
  const failedIndex = r?.failedStep
    ? shown.findIndex((d) => d.key === r.failedStep) + 1
    : 0;

  const copy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      message.success('已复制');
    } catch {
      message.error('复制失败，请手动选择复制');
    }
  };

  return (
    <Drawer
      open={!!releaseId}
      onClose={onClose}
      size={760}
      destroyOnHidden
      title={
        <div>
          <div style={{ fontFamily: MONO }}>发布单 {r?.releaseNo ?? ''}</div>
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            每一步都有超时：构建、部署各 10 分钟，其他 3 分钟
          </div>
        </div>
      }
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button onClick={onClose}>关闭</Button>
        </div>
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !r ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              flexWrap: 'wrap',
              fontSize: 13,
              color: palette.sub,
            }}
          >
            <Pill tone={RELEASE_TONE[r.status] ?? 'gray'}>
              {running && r.status === RELEASE_RUNNING && <LoadingOutlined />}{' '}
              {r.statusName}
            </Pill>
            {[
              siteName,
              `${r.itemCount} 个商品`,
              r.createBy,
              r.startedAt
                ? `${formatDateTime(r.startedAt)} 开始`
                : `${formatDateTime(r.createTime)} 生成`,
              r.endedAt ? `用时 ${seconds(r.startedAt, r.endedAt)}` : '',
            ]
              .filter(Boolean)
              .join(' · ')}
          </div>

          {r.status === RELEASE_FAILED && (
            <div
              style={{
                padding: '12px 14px',
                borderRadius: 12,
                background: palette.redSoft,
                color: palette.red,
                fontSize: 13,
              }}
            >
              <CloseCircleFilled />{' '}
              {failedIndex > 0
                ? `第 ${failedIndex} 步「${stepName(r.failedStep)}」失败：`
                : '失败：'}
              {r.error || '发布服务没有返回原因'}
              。线上没有变化，任务已回到原来的状态
            </div>
          )}

          {r.status === RELEASE_DEPLOY_FAILED && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 16,
                padding: '12px 14px',
                borderRadius: 12,
                border: `1px solid ${palette.orange}`,
                background: palette.orangeSoft,
              }}
            >
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, color: palette.orange }}>
                  <ExclamationCircleOutlined />{' '}
                  部署失败：线上可能只更新了一部分，请重试
                </div>
                <div
                  style={{
                    fontFamily: MONO,
                    fontSize: 12,
                    color: palette.sub,
                    marginTop: 4,
                    wordBreak: 'break-all',
                  }}
                >
                  {r.error}
                </div>
              </div>
              {canPublish && (
                <Button
                  type="primary"
                  icon={<RedoOutlined />}
                  loading={retrying}
                  onClick={retry}
                >
                  从部署一步重试
                </Button>
              )}
            </div>
          )}

          {r.status === RELEASE_QUEUED && (
            <div
              style={{
                padding: '12px 14px',
                borderRadius: 12,
                background: palette.inset,
                fontSize: 13,
                color: palette.sub,
              }}
            >
              <ClockCircleOutlined />{' '}
              排队中：同一站点同一时间只执行一张发布单，前面的结束后自动开始
            </div>
          )}

          <div
            style={{
              padding: '6px 16px',
              borderRadius: 14,
              background: palette.inset,
            }}
          >
            {shown.map((d, i) => {
              const s = latest.get(d.key);
              const failed = s?.status === 3;
              const notRun = !s && terminal;
              return (
                <div
                  key={d.key}
                  style={{ display: 'flex', gap: 12, padding: '10px 0' }}
                >
                  <StepIcon status={s?.status} index={i + 1} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div
                      style={{
                        fontWeight: 600,
                        color: failed
                          ? palette.red
                          : s?.status === 1
                            ? palette.link
                            : s
                              ? palette.ink
                              : palette.mute,
                      }}
                    >
                      {i + 1}. {d.name}
                      {s && s.attempt > 1 && (
                        <span
                          style={{
                            marginLeft: 8,
                            fontSize: 12,
                            fontWeight: 400,
                            color: palette.mute,
                          }}
                        >
                          第 {s.attempt} 次
                        </span>
                      )}
                    </div>
                    <div
                      style={{
                        fontSize: 12,
                        color: failed ? palette.red : palette.mute,
                        marginTop: 2,
                        wordBreak: 'break-all',
                      }}
                    >
                      {s?.message || (notRun ? '没有执行' : d.hint)}
                    </div>
                  </div>
                  {s?.startedAt && (
                    <span
                      style={{
                        fontSize: 12,
                        color: palette.mute,
                        fontVariantNumeric: 'tabular-nums',
                      }}
                    >
                      {seconds(s.startedAt, s.endedAt)}
                    </span>
                  )}
                </div>
              );
            })}
          </div>

          {failedStep?.output && (
            <div>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  marginBottom: 8,
                }}
              >
                <b style={{ flex: 1, fontSize: 13 }}>
                  「{stepName(failedStep.step)}」输出的最后部分（凭据已去掉）
                </b>
                <Button
                  type="text"
                  size="small"
                  icon={<CopyOutlined />}
                  onClick={() => copy(failedStep.output ?? '')}
                >
                  复制
                </Button>
              </div>
              <pre
                style={{
                  margin: 0,
                  padding: 14,
                  borderRadius: 12,
                  background: palette.inset,
                  border: `1px solid ${palette.hairline}`,
                  fontFamily: MONO,
                  fontSize: 12,
                  lineHeight: 1.6,
                  color: palette.sub,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                  maxHeight: 320,
                  overflow: 'auto',
                }}
              >
                {failedStep.output}
              </pre>
            </div>
          )}

          {!!r.warnings?.length && (
            <div
              style={{
                display: 'grid',
                gap: 4,
                fontSize: 13,
                color: palette.orange,
              }}
            >
              {r.warnings.map((w) => (
                <div key={w}>
                  <ExclamationCircleOutlined /> {w}
                </div>
              ))}
            </div>
          )}

          <div>
            <b style={{ fontSize: 13 }}>商品与上线链接</b>
            <div
              style={{
                marginTop: 8,
                border: `1px solid ${palette.hairline}`,
                borderRadius: 12,
                overflow: 'hidden',
              }}
            >
              {r.items.map((it, idx) => {
                const urls = Object.values(it.liveUrls ?? {});
                const checks = it.checkResult ?? [];
                const ok = checks.length > 0 && checks.every((c) => c.ok);
                const bad = checks.some((c) => !c.ok);
                return (
                  <div
                    key={it.listingTaskId}
                    style={{
                      display: 'flex',
                      gap: 12,
                      alignItems: 'center',
                      padding: '10px 14px',
                      borderTop: idx
                        ? `1px solid ${palette.hairline}`
                        : undefined,
                      fontSize: 13,
                    }}
                  >
                    <b style={{ fontFamily: MONO, width: 180, flexShrink: 0 }}>
                      {it.model}
                    </b>
                    <span
                      style={{
                        flex: 1,
                        minWidth: 0,
                        fontFamily: MONO,
                        fontSize: 12,
                        color: palette.sub,
                        display: 'grid',
                      }}
                    >
                      {urls.length
                        ? urls.map((u) => <span key={u}>{pathOf(u)}</span>)
                        : '—'}
                    </span>
                    <span
                      style={{
                        flexShrink: 0,
                        fontSize: 12,
                        color: ok
                          ? palette.green
                          : bad
                            ? palette.orange
                            : palette.mute,
                      }}
                    >
                      {ok ? (
                        <>
                          <CheckCircleOutlined /> 线上检查通过
                        </>
                      ) : bad ? (
                        <>
                          <ExclamationCircleOutlined /> 没通过
                        </>
                      ) : r.status === RELEASE_FAILED ? (
                        '没有上线'
                      ) : (
                        <>
                          <ClockCircleOutlined /> 待检查
                        </>
                      )}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          {queue.length > 0 && (
            <div
              style={{
                padding: '10px 14px',
                borderRadius: 12,
                background: palette.inset,
                fontSize: 13,
                color: palette.sub,
              }}
            >
              <OrderedListOutlined /> 同一站点后面还有 {queue.length}{' '}
              张发布单排队（
              {queue
                .slice(0, 3)
                .map(
                  (q) =>
                    `${q.releaseNo} · ${q.createBy ?? ''} · ${q.itemCount} 个商品`,
                )
                .join('；')}
              ），这张结束后自动开始
            </div>
          )}
        </div>
      )}
    </Drawer>
  );
};
