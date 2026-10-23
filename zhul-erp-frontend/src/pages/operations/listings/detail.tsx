import { Link, useParams, useSearchParams } from '@umijs/max';
import { Segmented } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { MenuLink, MenuName } from '@/utils/menuName';
import { PlatformTaskChip, TABS } from './components';
import ListingPanel from './ListingPanel';
import { type ListingProduct, listingApi, readBizError } from './service';

/** 运营管理 → 发品任务 → 某商品：页头显示各平台状态，按平台切换处理 */
const ListingProductDetail: React.FC = () => {
  const { palette } = useAppTheme();
  const params = useParams<{ productId: string }>();
  const productId = Number(params.productId);
  const [search, setSearch] = useSearchParams();
  const [data, setData] = useState<ListingProduct>();
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

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!data) return <div style={{ color: palette.sub }}>正在加载…</div>;

  // 默认打开第一个待发布的平台；地址里带 platform 时打开那个平台
  const wanted = search.get('platform');
  const current =
    data.tasks.find((t) => t.platform === wanted) ??
    data.tasks.find((t) => t.status === 1) ??
    data.tasks[0];
  const label = `${data.brand ?? ''} · ${data.model}`;
  const status = TABS.find((t) => t.key === data.status);

  return (
    <div>
      <PageTitle
        crumbs={[<MenuLink key="list" to="/operations/listings" />, data.model]}
        title={
          <>
            <MenuName /> · {label}
          </>
        }
        description="本公司 · 每个平台一个店铺，逐个平台复制内容、下载素材，发布后回填链接"
      />
      <Card style={{ marginBottom: 16 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <div>
            <div style={{ fontSize: 18, fontWeight: 700, color: palette.ink }}>
              {label}
            </div>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {[data.productName, data.category].filter(Boolean).join(' · ')}
            </div>
          </div>
          <span style={{ flex: 1 }} />
          <Pill tone={status?.tone ?? 'gray'}>{data.statusName}</Pill>
          {data.needMedia && <Pill tone="orange">素材待补</Pill>}
          <Link to={`/operations/products/${productId}`}>去营销任务 →</Link>
        </div>
        <div
          style={{
            marginTop: 10,
            display: 'flex',
            gap: 10,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <span style={{ fontSize: 12, color: palette.mute }}>各平台</span>
          {data.tasks.map((t) => (
            <PlatformTaskChip key={t.id} task={t} />
          ))}
        </div>
      </Card>
      <Segmented<string>
        value={current?.platform}
        onChange={(p) => setSearch({ platform: p }, { replace: true })}
        style={{ marginBottom: 16 }}
        options={data.tasks.map((t) => ({
          value: t.platform,
          label: t.platformLabel,
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
