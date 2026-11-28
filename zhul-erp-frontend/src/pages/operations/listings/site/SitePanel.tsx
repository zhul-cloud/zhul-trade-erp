import { App, Button, Skeleton, Table, Tooltip } from 'antd';
import {
  Check,
  CloudUpload,
  ExternalLink,
  Eye,
  LoaderCircle,
  RefreshCw,
  Rocket,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { usePermission } from '@/utils/permission';
import { type ListingDetail, type ListingSite, readBizError } from '../service';
import { PublishNotes } from './BatchPreviewModal';
import { ConfirmLiveModal } from './ConfirmLiveModal';
import {
  ChangesSummary,
  ChangeTable,
  Checks,
  LangSwitch,
  PageContent,
  SiteDataCard,
} from './PreviewParts';
import { MONO, RELEASE_TONE, ReleaseDrawer, seconds } from './ReleaseDrawer';
import {
  LANG_LABEL,
  PERM_PUBLISH,
  type Preview,
  RELEASE_DEPLOY_FAILED,
  RELEASE_DONE,
  RELEASE_FAILED,
  type Release,
  sitePublishApi,
  stepName,
  TASK_LIVE_UNCONFIRMED,
  TASK_PUBLISHING,
} from './service';

/** 圆角卡片：标题 + 右侧操作 */
const Section: React.FC<{
  title: React.ReactNode;
  extra?: React.ReactNode;
  children: React.ReactNode;
}> = ({ title, extra, children }) => {
  const { palette } = useAppTheme();
  return (
    <section
      style={{
        padding: 16,
        borderRadius: 14,
        border: `1px solid ${palette.hairline}`,
        display: 'grid',
        gap: 12,
        alignContent: 'start',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span
          style={{ flex: 1, fontSize: 16, fontWeight: 600, color: palette.ink }}
        >
          {title}
        </span>
        {extra}
      </div>
      {children}
    </section>
  );
};

/** 单个商品的发布预览：左边页面内容，右边检查、站点数据、与上次发布的差异 */
const PublishPreview: React.FC<{
  p: Preview;
  category?: string;
  siteName: string;
  canPublish: boolean;
  publishing: boolean;
  onPublish: () => void;
  onChanged: () => void;
  publishLabel: string;
}> = ({
  p,
  category,
  siteName,
  canPublish,
  publishing,
  onPublish,
  onChanged,
  publishLabel,
}) => {
  const { palette } = useAppTheme();
  const [lang, setLang] = useState('en');
  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 1.6fr) minmax(320px, 1fr)',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <Section
          title="页面内容"
          extra={
            <LangSwitch
              langs={Object.keys(p.liveUrls)}
              value={lang}
              onChange={setLang}
            />
          }
        >
          <PageContent p={p} lang={lang} />
        </Section>
        <div style={{ display: 'grid', gap: 16 }}>
          <Section title="检查">
            <Checks p={p} />
          </Section>
          <Section title="站点数据">
            <SiteDataCard
              p={p}
              category={category}
              canEdit={canPublish}
              onChanged={onChanged}
            />
          </Section>
          <Section title="与上次发布的差异">
            <ChangesSummary p={p} siteName={siteName} />
          </Section>
        </div>
      </div>
      <PublishNotes />
      {canPublish && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span style={{ flex: 1, fontSize: 13, color: palette.red }}>
            {p.blockers.length > 0 &&
              `有 ${p.blockers.length} 个阻断项，处理后才能发布`}
          </span>
          <Button
            type="primary"
            icon={<Rocket size="1em" />}
            disabled={!p.publishable}
            loading={publishing}
            onClick={onPublish}
          >
            {publishLabel}
          </Button>
        </div>
      )}
    </div>
  );
};

/** 自动发布站点的独立站任务：发布预览、发布中、确认上线、内容有更新时同步到站点、发布记录 */
const SitePanel: React.FC<{
  detail: ListingDetail;
  site: ListingSite;
  header: React.ReactNode;
  /** 重新取任务详情 */
  reload: () => void;
  /** 用返回的详情刷新，并通知列表 */
  onDetail: (d: ListingDetail) => void;
  onChanged: () => void;
}> = ({ detail, site, header, reload, onDetail, onChanged }) => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const { hasPerm } = usePermission();
  const canPublish = hasPerm(PERM_PUBLISH);
  const t = detail.task;
  const [preview, setPreview] = useState<Preview>();
  const [previewError, setPreviewError] = useState<string>();
  const [showAll, setShowAll] = useState(false);
  const [releases, setReleases] = useState<Release[]>();
  const [drawer, setDrawer] = useState<number>();
  const [confirming, setConfirming] = useState(false);
  const [publishing, setPublishing] = useState(false);

  const wantPreview = t.status === 1 || (t.status === 2 && t.contentUpdated);

  const loadPreview = useCallback(async () => {
    setPreviewError(undefined);
    try {
      const [p] = await sitePublishApi.preview(site.siteId, [t.id]);
      setPreview(p);
    } catch (e) {
      setPreviewError(readBizError(e).message);
    }
  }, [site.siteId, t.id]);

  const loadReleases = useCallback(() => {
    sitePublishApi
      .releasesOfTask(t.id)
      .then(setReleases)
      .catch(() => setReleases([]));
  }, [t.id]);

  useEffect(() => {
    if (wantPreview) loadPreview();
    else setPreview(undefined);
  }, [wantPreview, loadPreview]);

  useEffect(() => {
    loadReleases();
  }, [loadReleases, t.status]);

  const refresh = () => {
    reload();
    onChanged();
    loadReleases();
  };

  const publish = async () => {
    setPublishing(true);
    try {
      const r = await sitePublishApi.release(site.siteId, [t.id]);
      message.success(`已生成发布单 ${r.releaseNo}`);
      setShowAll(false);
      setDrawer(r.id);
      refresh();
    } catch (e) {
      message.error(readBizError(e).message);
      loadPreview();
    } finally {
      setPublishing(false);
    }
  };

  const sync = () =>
    modal.confirm({
      title: `同步到 ${site.name}？`,
      content:
        '生成新的发布单，把更新后的内容发布到站点；完成并确认上线后「内容有更新」消失。',
      okText: '同步到站点',
      onOk: publish,
    });

  const current = releases?.find((r) => r.id === t.releaseId);
  const last = releases?.find((r) => r.status === RELEASE_DONE);
  const liveEntries = Object.entries(site.liveUrls ?? {});

  const records = (
    <Section title="发布记录">
      {!releases ? (
        <Skeleton active paragraph={{ rows: 2 }} />
      ) : (
        <Table<Release>
          rowKey="id"
          size="small"
          showHeader={false}
          pagination={false}
          dataSource={releases}
          locale={{ emptyText: '还没有发布过' }}
          columns={[
            {
              key: 'no',
              width: 170,
              render: (_, r) => (
                <b style={{ fontFamily: MONO }}>{r.releaseNo}</b>
              ),
            },
            {
              key: 'status',
              width: 100,
              render: (_, r) => (
                <Pill tone={RELEASE_TONE[r.status] ?? 'gray'}>
                  {r.statusName}
                </Pill>
              ),
            },
            { key: 'count', width: 70, render: (_, r) => `${r.itemCount} 个` },
            {
              key: 'who',
              render: (_, r) => (
                <span style={{ color: palette.sub }}>
                  {[
                    r.createBy,
                    formatDateTime(r.createTime),
                    r.status === RELEASE_FAILED ||
                    r.status === RELEASE_DEPLOY_FAILED
                      ? `${stepName(r.failedStep)}失败`
                      : r.endedAt
                        ? seconds(r.startedAt, r.endedAt)
                        : '',
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </span>
              ),
            },
            {
              key: 'op',
              width: 60,
              align: 'right',
              render: (_, r) => (
                <Button
                  type="link"
                  size="small"
                  onClick={() => setDrawer(r.id)}
                >
                  查看
                </Button>
              ),
            },
          ]}
        />
      )}
    </Section>
  );

  const banner = (
    tone: 'mute' | 'cyan' | 'orange',
    text: React.ReactNode,
    action?: React.ReactNode,
  ) => (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '12px 14px',
        borderRadius: 12,
        background:
          tone === 'mute'
            ? palette.inset
            : tone === 'orange'
              ? palette.orangeSoft
              : palette.infoSoft,
        fontSize: 13,
        color:
          tone === 'mute'
            ? palette.sub
            : tone === 'orange'
              ? palette.orange
              : palette.ink,
      }}
    >
      <span style={{ flex: 1 }}>{text}</span>
      {action}
    </div>
  );

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'wrap',
        }}
      >
        {header}
        <span style={{ flex: 1 }} />
        {t.status === TASK_LIVE_UNCONFIRMED &&
          hasPerm('operations:listing:edit') && (
            <Button
              type="primary"
              icon={<Check size="1em" />}
              onClick={() => setConfirming(true)}
            >
              确认上线
            </Button>
          )}
        {t.status === TASK_PUBLISHING && t.releaseId && (
          <Button
            icon={<LoaderCircle size="1em" className="zhul-spin" />}
            onClick={() => setDrawer(t.releaseId)}
          >
            查看进度
          </Button>
        )}
      </div>

      {t.status === 3 &&
        banner(
          'mute',
          `已取消：${t.cancelReason || '—'}。站点重新启用或商品回到站点范围后，任务自动回到待发布。`,
        )}

      {t.status === TASK_PUBLISHING &&
        (current?.status === RELEASE_DEPLOY_FAILED
          ? banner(
              'orange',
              <>
                发布单 {current.releaseNo}{' '}
                部署失败，线上可能只更新了一部分；打开发布单从部署一步重试
              </>,
            )
          : banner(
              'cyan',
              <>
                <LoaderCircle size="1em" className="zhul-spin" /> 发布单{' '}
                {current?.releaseNo ?? ''} 正在发布到 {site.name}
                ，完成后到这里确认上线
              </>,
            ))}

      {t.status === TASK_LIVE_UNCONFIRMED &&
        banner(
          'cyan',
          `已发布到 ${site.name}，请打开上线链接核对页面${site.needPurge ? '，并按说明清理站点缓存' : ''}后确认上线`,
        )}

      {wantPreview && previewError && (
        <ErrorHint message={previewError} onRetry={loadPreview} />
      )}
      {wantPreview && !preview && !previewError && <Skeleton active />}

      {t.status === 1 && preview && (
        <PublishPreview
          p={preview}
          category={t.category}
          siteName={site.name}
          canPublish={canPublish}
          publishing={publishing}
          onPublish={publish}
          onChanged={loadPreview}
          publishLabel="发布"
        />
      )}

      {t.status === 2 && t.contentUpdated && preview && (
        <Section
          title={
            <>
              <RefreshCw size="1em" style={{ color: palette.info }} />{' '}
              内容在发布后更新过
            </>
          }
          extra={
            <>
              <Button
                icon={<Eye size="1em" />}
                onClick={() => setShowAll((v) => !v)}
              >
                {showAll ? '收起预览' : '预览全部'}
              </Button>
              {canPublish && (
                <Tooltip
                  title={
                    preview.publishable
                      ? undefined
                      : preview.blockers.join('；')
                  }
                >
                  <Button
                    type="primary"
                    icon={<CloudUpload size="1em" />}
                    disabled={!preview.publishable}
                    loading={publishing}
                    onClick={sync}
                  >
                    同步到站点
                  </Button>
                </Tooltip>
              )}
            </>
          }
        >
          {preview.changes.length ? (
            <ChangeTable changes={preview.changes} />
          ) : (
            <div style={{ fontSize: 13, color: palette.mute }}>
              营销内容的写入时间变了，写入站点的内容和上次发布一样；可以照样同步
            </div>
          )}
          <div style={{ fontSize: 12, color: palette.mute }}>
            {last &&
              `上次发布 ${last.releaseNo} · ${formatDateTime(last.createTime)} · `}
            同步后生成新的发布单，完成并确认上线后提示消失
          </div>
        </Section>
      )}

      {showAll && t.status === 2 && preview && (
        <PublishPreview
          p={preview}
          category={t.category}
          siteName={site.name}
          canPublish={canPublish}
          publishing={publishing}
          onPublish={sync}
          onChanged={loadPreview}
          publishLabel="同步到站点"
        />
      )}

      {(t.status === 2 || t.status === TASK_LIVE_UNCONFIRMED) &&
        liveEntries.length > 0 && (
          <Section title="上线链接">
            <div style={{ display: 'grid', gap: 8 }}>
              {liveEntries.map(([l, u]) => (
                <div
                  key={l}
                  style={{ display: 'flex', gap: 10, alignItems: 'center' }}
                >
                  <Pill tone="gray">{LANG_LABEL[l] ?? l}</Pill>
                  <span
                    style={{
                      flex: 1,
                      fontFamily: MONO,
                      fontSize: 12,
                      wordBreak: 'break-all',
                    }}
                  >
                    {u}
                  </span>
                  <a
                    href={u}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label="打开页面"
                  >
                    <ExternalLink size="1em" />
                  </a>
                </div>
              ))}
            </div>
            {t.confirmedAt && (
              <div style={{ fontSize: 12, color: palette.mute }}>
                确认上线：{t.confirmedBy} · {formatDateTime(t.confirmedAt)}
                {site.needPurge && ' · 已清缓存'}
              </div>
            )}
          </Section>
        )}

      {records}

      <ReleaseDrawer
        releaseId={drawer}
        siteName={site.name}
        onClose={() => setDrawer(undefined)}
        onChanged={refresh}
      />
      <ConfirmLiveModal
        open={confirming}
        detail={detail}
        onClose={() => setConfirming(false)}
        onConfirmed={(d) => {
          setConfirming(false);
          onDetail(d);
          loadReleases();
        }}
      />
    </div>
  );
};

export default SitePanel;
