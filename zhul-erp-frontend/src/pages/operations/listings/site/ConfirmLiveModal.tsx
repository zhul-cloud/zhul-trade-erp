import { App, Button, Checkbox, Modal } from 'antd';
import {
  Check,
  CircleAlert,
  CircleCheck,
  Clock,
  Eraser,
  ExternalLink,
} from 'lucide-react';
import React, { useEffect, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { type ListingDetail, readBizError } from '../service';
import { MONO } from './ReleaseDrawer';
import { LANG_LABEL, type Release, sitePublishApi } from './service';

/** 确认上线：逐条打开核对页面；站点要人工清缓存时须勾选「已清缓存」 */
export const ConfirmLiveModal: React.FC<{
  detail?: ListingDetail;
  open: boolean;
  onClose: () => void;
  onConfirmed: (d: ListingDetail) => void;
}> = ({ detail, open, onClose, onConfirmed }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [release, setRelease] = useState<Release>();
  const [purged, setPurged] = useState(false);
  const [tried, setTried] = useState(false);
  const [saving, setSaving] = useState(false);
  const t = detail?.task;
  const site = detail?.site;

  useEffect(() => {
    setPurged(false);
    setTried(false);
    setRelease(undefined);
    if (open && t?.releaseId)
      sitePublishApi
        .detail(t.releaseId)
        .then(setRelease)
        .catch(() => setRelease(undefined));
  }, [open, t?.releaseId]);

  const checks =
    release?.items.find((i) => i.listingTaskId === t?.id)?.checkResult ?? [];
  const needPurge = !!site?.needPurge;

  const confirm = async () => {
    if (!t) return;
    if (needPurge && !purged) {
      setTried(true);
      return;
    }
    setSaving(true);
    try {
      const d = await sitePublishApi.confirmLive(t.id, purged);
      message.success('已确认上线');
      onConfirmed(d);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={640}
      destroyOnHidden
      title={
        <div>
          确认上线
          {release && (
            <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
              发布单 {release.releaseNo} · {formatDateTime(release.endedAt)}{' '}
              完成；线上检查没通过仍可以确认
            </div>
          )}
        </div>
      }
      footer={
        <>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            icon={<Check size="1em" />}
            loading={saving}
            onClick={confirm}
          >
            确认上线
          </Button>
        </>
      }
    >
      {t && site && (
        <div style={{ display: 'grid', gap: 14 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ color: palette.sub }}>{t.brand}</span>
            <b style={{ fontFamily: MONO, fontSize: 16 }}>{t.model}</b>
            <span style={{ color: palette.mute }}>· {site.name}</span>
            <Pill tone="cyan">{t.statusName}</Pill>
          </div>
          <div style={{ fontSize: 13, color: palette.sub }}>
            逐条打开核对页面
          </div>
          {Object.entries(site.liveUrls).map(([lang, url]) => {
            const c = checks.find((x) => x.url === url);
            return (
              <div
                key={lang}
                style={{
                  display: 'flex',
                  gap: 10,
                  alignItems: 'center',
                  padding: '10px 12px',
                  borderRadius: 10,
                  background: palette.inset,
                }}
              >
                <Pill tone="gray">{LANG_LABEL[lang] ?? lang}</Pill>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div
                    style={{
                      fontFamily: MONO,
                      fontSize: 12,
                      wordBreak: 'break-all',
                    }}
                  >
                    {url}
                  </div>
                  {c && !c.ok && (
                    <div style={{ fontSize: 12, color: palette.orange }}>
                      {c.errors?.join('；') ||
                        '页面还是旧内容，可能是站点缓存，清缓存后再看'}
                    </div>
                  )}
                </div>
                <span
                  style={{
                    fontSize: 12,
                    whiteSpace: 'nowrap',
                    color: !c
                      ? palette.mute
                      : c.ok
                        ? palette.green
                        : palette.orange,
                  }}
                >
                  {!c ? (
                    <>
                      <Clock size="1em" /> 没有检查结果
                    </>
                  ) : c.ok ? (
                    <>
                      <CircleCheck size="1em" /> 线上检查通过
                    </>
                  ) : (
                    <>
                      <CircleAlert size="1em" /> 没通过
                    </>
                  )}
                </span>
                <a
                  href={url}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label="打开页面"
                >
                  <ExternalLink size="1em" />
                </a>
              </div>
            );
          })}
          {needPurge && (
            <>
              <div
                style={{
                  padding: '12px 14px',
                  borderRadius: 10,
                  border: `1px solid ${palette.hairline}`,
                  background: palette.inset,
                  fontSize: 13,
                }}
              >
                <div style={{ fontWeight: 600, marginBottom: 4 }}>
                  <Eraser size="1em" /> 这个站点发布后要人工清缓存
                </div>
                <div style={{ color: palette.sub, whiteSpace: 'pre-wrap' }}>
                  {site.purgeNote || '按站点的缓存设置清理页面缓存'}
                </div>
              </div>
              <Checkbox
                checked={purged}
                onChange={(e) => setPurged(e.target.checked)}
              >
                <b>已清缓存</b>
              </Checkbox>
              {tried && !purged && (
                <div style={{ fontSize: 12, color: palette.red }}>
                  <CircleAlert size="1em" />{' '}
                  请先按说明清理站点缓存，再勾选「已清缓存」
                </div>
              )}
            </>
          )}
        </div>
      )}
    </Modal>
  );
};
