import {
  CheckCircleOutlined,
  ExportOutlined,
  PictureOutlined,
  PlayCircleFilled,
} from '@ant-design/icons';
import { Link } from '@umijs/max';
import { App, Button, Image, Segmented } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { type FileLink, mediaApi } from '@/pages/operations/media/service';
import {
  type CompanyMedia,
  companyMediaApi,
  readBizError,
} from '@/pages/operations/videos/service';
import { isPlatformAccount } from '@/utils/platform';
import { EmptyHint } from '../../components/EmptyHint';
import { Pill } from '../../components/Pills';
import { useProductTheme } from '../../theme';
import { CardShell } from './CardShell';

/** 本公司素材的三个类别（与后端 MediaCategory 一致） */
const GROUPS = [
  { code: 12, label: '图片素材' },
  { code: 13, label: '视频素材' },
  { code: 9, label: '营销实拍' },
];

/**
 * 商品档案「本公司素材」：只显示本公司素材库里这个型号的图片素材、视频素材和营销实拍，引用素材库的文件，不另存一份。
 * 平台账号可以把自己的素材「设为官方素材」，复制一份到共享商品库。
 */
export const CompanyMediaCard: React.FC<{
  productId: number;
  onOfficial: () => void;
}> = ({ productId, onOfficial }) => {
  const { message, modal } = App.useApp();
  const { palette } = useProductTheme();
  const platform = isPlatformAccount();
  const [data, setData] = useState<CompanyMedia>();
  const [links, setLinks] = useState<Record<number, FileLink>>({});
  const [group, setGroup] = useState(12);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState<number>();

  const load = useCallback(async () => {
    setError(undefined);
    try {
      const d = await companyMediaApi.list(productId);
      setData(d);
      const ids = d.items
        .map((i) => i.media.itemId)
        .filter((x): x is number => !!x)
        .slice(0, 50);
      if (ids.length) {
        const ls = await mediaApi.urls(
          ids.map((itemId) => ({ itemId })),
          false,
        );
        const m: Record<number, FileLink> = {};
        ids.forEach((id, i) => {
          if (ls[i]) m[id] = ls[i];
        });
        setLinks(m);
      }
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [productId]);

  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(load);

  const makeOfficial = (itemId: number, video: boolean) =>
    modal.confirm({
      title: '设为官方素材？',
      content: `复制一份到共享商品库的「图片与视频」，所有公司都能看到${video ? '（连同封面）' : ''}；之后两边互不影响。`,
      okText: '设为官方素材',
      onOk: async () => {
        setBusy(itemId);
        try {
          await companyMediaApi.makeOfficial(productId, itemId);
          message.success('已设为官方素材');
          load();
          onOfficial();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(undefined);
        }
      },
    });

  const items = data?.items.filter((i) => i.media.category === group) ?? [];
  const count = (code: number) =>
    data?.items.filter((i) => i.media.category === code).length ?? 0;

  return (
    <CardShell
      id="card-company-media"
      title="本公司素材"
      actions={
        data && (
          <Link
            to={`/operations/media?keyword=${encodeURIComponent(data.model)}`}
          >
            <Button type="link" icon={<ExportOutlined />}>
              去素材库
            </Button>
          </Link>
        )
      }
    >
      <div
        style={{
          marginTop: -8,
          marginBottom: 12,
          fontSize: 12,
          color: palette.sub,
        }}
      >
        <Pill tone="accent">仅本公司可见</Pill> 引用素材库里的文件，不另存一份
      </div>
      {error ? (
        <EmptyHint
          title="加载失败"
          description={error}
          actionText="重试"
          onAction={load}
        />
      ) : !data ? (
        <div style={{ color: palette.sub }}>正在加载…</div>
      ) : (
        <>
          <Segmented
            value={group}
            onChange={(v) => setGroup(v as number)}
            options={GROUPS.map((g) => ({
              value: g.code,
              label: `${g.label} ${count(g.code)}`,
            }))}
          />
          {items.length === 0 ? (
            <div style={{ marginTop: 16, fontSize: 13, color: palette.sub }}>
              {group === 13
                ? '还没有审核通过的视频；在运营管理 → 视频任务里上传素材生成'
                : group === 12
                  ? '还没有图片素材；在视频任务里上传的图片会出现在这里'
                  : '还没有营销实拍'}
            </div>
          ) : (
            <Image.PreviewGroup>
              <ul
                style={{
                  listStyle: 'none',
                  margin: '16px 0 0',
                  padding: 0,
                  display: 'flex',
                  gap: 12,
                  flexWrap: 'wrap',
                }}
              >
                {items.map(({ media: m, official }) => {
                  const itemId = m.itemId;
                  const video = m.kind === 2;
                  const url = m.itemId ? links[m.itemId]?.url : undefined;
                  return (
                    <li key={m.itemId} style={{ width: 160 }}>
                      <div
                        style={{
                          position: 'relative',
                          height: 120,
                          borderRadius: 10,
                          overflow: 'hidden',
                          background: palette.inset,
                          border: `1px solid ${palette.hairline}`,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        {video ? (
                          <a
                            href={url}
                            target="_blank"
                            rel="noreferrer"
                            aria-label={`播放视频 ${m.fileName ?? ''}`}
                            style={{ color: palette.sub, fontSize: 36 }}
                          >
                            <PlayCircleFilled />
                          </a>
                        ) : url ? (
                          <Image
                            src={url}
                            alt={m.fileName ?? ''}
                            width="100%"
                            height="100%"
                            styles={{ image: { objectFit: 'cover' } }}
                          />
                        ) : (
                          <PictureOutlined style={{ color: palette.sub }} />
                        )}
                        <span
                          style={{
                            position: 'absolute',
                            bottom: 6,
                            left: 6,
                            display: 'inline-flex',
                            gap: 4,
                          }}
                        >
                          {m.forListing && <Pill tone="green">用于发品</Pill>}
                          {official && <Pill tone="accent">已是官方素材</Pill>}
                        </span>
                      </div>
                      <div
                        title={m.fileName}
                        style={{
                          marginTop: 4,
                          fontSize: 12,
                          color: palette.sub,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {m.fileName}
                      </div>
                      {platform && !official && itemId && (
                        <Button
                          size="small"
                          type="link"
                          icon={<CheckCircleOutlined />}
                          style={{ padding: 0 }}
                          loading={busy === itemId}
                          onClick={() => makeOfficial(itemId, video)}
                        >
                          设为官方素材
                        </Button>
                      )}
                    </li>
                  );
                })}
              </ul>
            </Image.PreviewGroup>
          )}
        </>
      )}
    </CardShell>
  );
};
