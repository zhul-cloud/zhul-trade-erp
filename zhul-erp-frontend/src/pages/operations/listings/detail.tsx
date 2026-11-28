import { Link, useParams, useSearchParams } from '@umijs/max';
import React, { useCallback, useEffect, useState } from 'react';
import { PageHeader, ViewTabs } from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { PlatformTaskChip, TABS, taskState } from './components';
import ListingPanel from './ListingPanel';
import { type ListingProduct, listingApi, readBizError } from './service';

/** 运营管理 → 发品任务 → 某商品：页头显示各平台状态，按平台切换处理 */
const ListingProductDetail: React.FC = () => {
  const { palette } = useAppTheme();
  const params = useParams<{ productId: string }>();
  const productId = Number(params.productId);
  const [search, setSearch] = useSearchParams();
  const [data, setData] = useState<ListingProduct>();
  usePageTitle(data?.model);
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setData(await listingApi.product(productId));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [productId]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  if (error) return <ErrorHint page message={error} onRetry={load} />;
  if (!data) return <div style={{ color: palette.sub }}>正在加载…</div>;

  // 默认打开第一个待发布的平台；地址里带 task（独立站每个站点一个任务）或 platform 时打开那个
  const wantedTask = Number(search.get('task'));
  const wanted = search.get('platform');
  const current =
    data.tasks.find((t) => t.id === wantedTask) ??
    data.tasks.find((t) => t.platform === wanted) ??
    data.tasks.find((t) => t.status === 1) ??
    data.tasks[0];
  const label = `${data.brand ?? ''} · ${data.model}`;
  const status = TABS.find((t) => t.key === data.status);

  return (
    <div>
      <PageHeader
        title={label}
        help="本公司 · 每个平台一个店铺，逐个平台复制内容、下载素材，发布后回填链接"
        tags={
          <>
            <Pill tone={status?.tone ?? 'gray'}>{data.statusName}</Pill>
            {data.needMedia && <Pill tone="orange">素材待补</Pill>}
          </>
        }
        sub={
          <span
            style={{
              display: 'inline-flex',
              gap: 10,
              alignItems: 'center',
              flexWrap: 'wrap',
            }}
          >
            {[data.productName, data.category].filter(Boolean).join(' · ')}
            {data.tasks.map((t) => (
              <PlatformTaskChip key={t.id} task={t} />
            ))}
          </span>
        }
        extra={<Link to={`/operations/products/${productId}`}>去营销任务</Link>}
      />
      {/* 每个平台一份内容，用页签切换；独立站每个站点一个任务 */}
      <ViewTabs
        activeKey={current ? String(current.id) : ''}
        onChange={(id) => setSearch({ task: id }, { replace: true })}
        items={data.tasks.map((t) => ({
          key: String(t.id),
          label: `${t.platformLabel} · ${taskState(t)[1]}`,
        }))}
      />
      {current && (
        <Card>
          <ListingPanel key={current.id} id={current.id} onChanged={load} />
        </Card>
      )}
    </div>
  );
};

export default ListingProductDetail;
