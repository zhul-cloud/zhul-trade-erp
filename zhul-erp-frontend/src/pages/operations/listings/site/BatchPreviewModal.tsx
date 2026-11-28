import { App, Button, Checkbox, Modal, Skeleton } from 'antd';
import { CircleAlert, CircleCheck, CircleX, Info, Rocket } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { readBizError } from '../service';
import { Checks, LangSwitch, MediaStrip } from './PreviewParts';
import { MONO } from './ReleaseDrawer';
import { type Preview, type PublishTarget, sitePublishApi } from './service';

/** 发布说明：发布页和批量发布预览共用 */
export const PublishNotes: React.FC = () => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'grid',
        gap: 4,
        padding: '10px 14px',
        borderRadius: 10,
        background: palette.inset,
        fontSize: 12,
        color: palette.sub,
      }}
    >
      <span>
        <Info size="1em" />{' '}
        同一站点同一时间只执行一张发布单，后来的排队；不受夜间时段限制
      </span>
      <span>
        <Info size="1em" /> 发布单依次：拉取仓库 → 写入数据 → 构建 → 本地检查 →
        提交 → 部署 → 线上检查 → 推送仓库 → 推送收录
      </span>
    </div>
  );
};

/** 批量发布预览：左边勾选商品（有阻断项的不能勾），右边看选中商品的页面要点 */
export const BatchPreviewModal: React.FC<{
  site?: PublishTarget;
  taskIds: number[];
  onClose: () => void;
  /** 已生成发布单 */
  onReleased: (releaseId: number) => void;
}> = ({ site, taskIds, onClose, onReleased }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [items, setItems] = useState<Preview[]>();
  const [error, setError] = useState<string>();
  const [checked, setChecked] = useState<number[]>([]);
  const [current, setCurrent] = useState<number>();
  const [lang, setLang] = useState('en');
  const [sending, setSending] = useState(false);

  // 列表每次渲染都会传新数组，按内容比较
  const ids = taskIds.join(',');
  const load = useCallback(async () => {
    if (!site) return;
    setItems(undefined);
    setError(undefined);
    try {
      const list = await sitePublishApi.preview(
        site.id,
        ids.split(',').map(Number),
      );
      setItems(list);
      setChecked(list.filter((p) => p.publishable).map((p) => p.taskId));
      setCurrent(list[0]?.taskId);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [site, ids]);

  useEffect(() => {
    load();
  }, [load]);

  const ok = (items ?? []).filter((p) => p.publishable);
  const blocked = (items ?? []).length - ok.length;
  const p = items?.find((x) => x.taskId === current);

  const release = async () => {
    if (!site) return;
    setSending(true);
    try {
      const r = await sitePublishApi.release(site.id, checked);
      message.success(`已生成发布单 ${r.releaseNo}`);
      onReleased(r.id);
    } catch (e) {
      message.error(readBizError(e).message);
      load();
    } finally {
      setSending(false);
    }
  };

  return (
    <Modal
      open={!!site}
      onCancel={onClose}
      width={1080}
      destroyOnHidden
      title={
        <div>
          发布预览 · {site?.name} · {taskIds.length} 个商品
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            勾选的商品生成一张发布单；有阻断项的不能勾选
          </div>
        </div>
      }
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{
              flex: 1,
              textAlign: 'right',
              fontSize: 12,
              color: palette.mute,
            }}
          >
            {blocked > 0 && `已取消勾选 ${blocked} 个有阻断项的商品`}
          </span>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            icon={<Rocket size="1em" />}
            disabled={!checked.length}
            loading={sending}
            onClick={release}
          >
            发布 {checked.length} 个
          </Button>
        </div>
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !items ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '340px 1fr',
              gap: 16,
              alignItems: 'start',
            }}
          >
            <div>
              <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>
                {ok.length} 个可以发布
                {blocked > 0 && ` · ${blocked} 个有阻断项`}
              </div>
              <div
                style={{
                  display: 'grid',
                  gap: 8,
                  maxHeight: 480,
                  overflow: 'auto',
                  paddingRight: 4,
                }}
              >
                {items.map((x) => {
                  const active = x.taskId === current;
                  const note = x.blockers[0] ?? x.warnings[0];
                  return (
                    // biome-ignore lint/a11y/useSemanticElements: 整行可点，内含勾选框
                    <div
                      key={x.taskId}
                      role="button"
                      tabIndex={0}
                      onClick={() => setCurrent(x.taskId)}
                      onKeyDown={(e) =>
                        e.key === 'Enter' && setCurrent(x.taskId)
                      }
                      style={{
                        display: 'flex',
                        gap: 10,
                        alignItems: 'flex-start',
                        padding: '10px 12px',
                        borderRadius: 10,
                        cursor: 'pointer',
                        border: `1px solid ${active ? palette.link : palette.hairline}`,
                        background: active ? palette.primarySoft : palette.card,
                        opacity: x.publishable ? 1 : 0.7,
                      }}
                    >
                      <Checkbox
                        disabled={!x.publishable}
                        checked={checked.includes(x.taskId)}
                        onClick={(e) => e.stopPropagation()}
                        onChange={(e) =>
                          setChecked((c) =>
                            e.target.checked
                              ? [...c, x.taskId]
                              : c.filter((id) => id !== x.taskId),
                          )
                        }
                      />
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 12, color: palette.mute }}>
                          {x.brand}
                        </div>
                        <div style={{ fontFamily: MONO, fontWeight: 600 }}>
                          {x.model}
                        </div>
                        {note && (
                          <div
                            style={{
                              fontSize: 12,
                              color: x.blockers.length
                                ? palette.red
                                : palette.orange,
                            }}
                          >
                            {note}
                            {x.blockers.length + x.warnings.length > 1 &&
                              ` 等 ${x.blockers.length + x.warnings.length} 项`}
                          </div>
                        )}
                      </div>
                      {x.blockers.length ? (
                        <CircleX size="1em" style={{ color: palette.red }} />
                      ) : x.warnings.length ? (
                        <CircleAlert
                          size="1em"
                          style={{ color: palette.orange }}
                        />
                      ) : (
                        <CircleCheck
                          size="1em"
                          style={{ color: palette.green }}
                        />
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
            {p && (
              <div
                style={{
                  display: 'grid',
                  gap: 12,
                  padding: 16,
                  borderRadius: 12,
                  background: palette.inset,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center' }}>
                  <b style={{ flex: 1, fontFamily: MONO, fontSize: 16 }}>
                    {p.model}
                  </b>
                  <LangSwitch
                    langs={Object.keys(p.liveUrls)}
                    value={lang}
                    onChange={setLang}
                  />
                </div>
                <div>
                  <div style={{ fontSize: 12, color: palette.mute }}>
                    SEO 标题
                  </div>
                  <div
                    style={{
                      color: p.seoTitles[lang] ? palette.ink : palette.red,
                    }}
                  >
                    {p.seoTitles[lang] || '缺少'}
                  </div>
                </div>
                <MediaStrip p={p} size={64} />
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '72px 1fr',
                    rowGap: 6,
                    fontSize: 13,
                  }}
                >
                  <span style={{ color: palette.sub }}>售价</span>
                  <span>
                    {p.price?.amount != null
                      ? `${p.price.currency ?? ''} ${p.price.amount} · ${p.price.sourceName}`
                      : '没有售价，站点显示询价'}
                  </span>
                  <span style={{ color: palette.sub }}>站点品类</span>
                  <span style={{ fontFamily: MONO }}>
                    {p.siteCategory || '—'}
                  </span>
                  <span style={{ color: palette.sub }}>链接</span>
                  <span style={{ fontFamily: MONO, wordBreak: 'break-all' }}>
                    {p.liveUrls[lang] ?? Object.values(p.liveUrls)[0]}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <Pill tone={p.firstPublish ? 'accent' : 'gray'}>
                    {p.firstPublish
                      ? '首次发布'
                      : `改动 ${p.changes.length} 项`}
                  </Pill>
                </div>
                <Checks p={p} />
              </div>
            )}
          </div>
          <PublishNotes />
        </div>
      )}
    </Modal>
  );
};
