import { WarningOutlined } from '@ant-design/icons';
import { Link, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Segmented, Table } from 'antd';
import React, { useEffect, useState } from 'react';
import { Card, PageTitle } from '@/pages/inquiry/shared/components';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { Pill } from '@/pages/product/components/Pills';
import { UploadDrawer } from '@/pages/product/fact-tasks/UploadDrawer';
import { ImportRecordList } from '@/pages/product/products/cards/ImportRecords';
import {
  factsSummary,
  factsVerified,
  PLATFORM_META,
  PlatformChips,
  PlatformPanel,
  STAGES,
  useProductContent,
} from '@/pages/product/products/cards/SeoCard';
import {
  type MarketingPlatform,
  type Product,
  type ProductContent,
  productApi,
  readBizError,
} from '@/pages/product/service';
import { useAppTheme } from '@/theme/AppTheme';
import { MenuLink, MenuName } from '@/utils/menuName';

type Tab = 'keywords' | MarketingPlatform | 'records';
type LibraryKeyword = NonNullable<ProductContent['keywordLibrary']>[number];

const TIERS: Record<
  number,
  { label: string; tone: 'accent' | 'violet' | 'gray' }
> = {
  1: { label: '核心', tone: 'accent' },
  2: { label: '主要', tone: 'violet' },
  3: { label: '长尾', tone: 'gray' },
};

/** 关键词库：本公司这个型号的关键词总库，按档位、库内顺序，标出选用平台与没被选用的词 */
const KeywordLibrary: React.FC<{ data: ProductContent }> = ({ data }) => {
  const { palette } = useAppTheme();
  const rows = data.keywordLibrary ?? [];
  const count = (tier: number) => rows.filter((k) => k.tier === tier).length;
  const unused = rows.filter((k) => k.platforms.length === 0).length;
  const columns: TableColumnsType<LibraryKeyword> = [
    {
      title: '档位',
      dataIndex: 'tier',
      width: 90,
      render: (t: number) => <Pill tone={TIERS[t].tone}>{TIERS[t].label}</Pill>,
    },
    {
      title: '关键词',
      dataIndex: 'keyword',
      render: (v: string, k) => (
        <span>
          <b>{v}</b>
          {k.basis && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              依据：{k.basis}
            </div>
          )}
        </span>
      ),
    },
    {
      title: '分类',
      dataIndex: 'category',
      width: 140,
      render: (v?: string) => v || '—',
    },
    {
      title: '搜索意图',
      dataIndex: 'intent',
      width: 180,
      render: (v?: string) => (
        <span style={{ color: palette.sub }}>{v || '—'}</span>
      ),
    },
    {
      title: '选用平台',
      key: 'platforms',
      width: 220,
      render: (_, k) =>
        k.platforms.length ? (
          <span style={{ display: 'inline-flex', gap: 6, flexWrap: 'wrap' }}>
            {k.platforms.map((p) => (
              <Pill key={p} tone="gray">
                {PLATFORM_META[p].short}
              </Pill>
            ))}
          </span>
        ) : (
          <span style={{ color: palette.orange }}>未被任何平台选用</span>
        ),
    },
  ];
  if (rows.length === 0) {
    return (
      <EmptyHint
        title="还没有关键词"
        description="上传含「关键词库」的营销包后，这里列出本公司这个型号的全部关键词；各平台从中选用。"
      />
    );
  }
  return (
    <>
      <div
        style={{
          display: 'flex',
          gap: 8,
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: 12,
        }}
      >
        <b style={{ color: palette.ink }}>关键词库 · {rows.length} 个</b>
        <Pill tone="accent">核心 {count(1)}</Pill>
        <Pill tone="violet">主要 {count(2)}</Pill>
        <Pill tone="gray">长尾 {count(3)}</Pill>
        <span style={{ flex: 1 }} />
        <span style={{ fontSize: 12, color: palette.mute }}>
          已被平台选用 {rows.length - unused} · 未使用 {unused}
        </span>
      </div>
      <Table<LibraryKeyword>
        rowKey="keyword"
        size="small"
        columns={columns}
        dataSource={rows}
        pagination={rows.length > 50 ? { pageSize: 50 } : false}
        scroll={{ x: 900 }}
      />
      <div style={{ marginTop: 8, fontSize: 12, color: palette.mute }}>
        总库按公司、按型号一份，导入含关键词库的营销包时整份替换；选用平台只算本公司启用的平台。搜索量未实测，档位是语义建议。
      </div>
    </>
  );
};

/** 运营管理 → 营销任务 → 某商品：本公司的关键词库、各平台营销内容、营销包上传记录 */
const OperationsProductDetail: React.FC = () => {
  const { palette } = useAppTheme();
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const [product, setProduct] = useState<Product>();
  const [error, setError] = useState<string>();
  const content = useProductContent(id);
  const [tab, setTab] = useState<Tab>('keywords');
  const [uploadOpen, setUploadOpen] = useState(false);
  const data = content.data;

  useEffect(() => {
    productApi
      .get(id)
      .then(setProduct)
      .catch((e) => setError(readBizError(e).message));
  }, [id]);

  if (error || content.error) {
    return (
      <ErrorHint
        message={error ?? content.error ?? ''}
        onRetry={content.load}
      />
    );
  }
  if (!product || !data) {
    return <div style={{ color: palette.sub }}>正在加载…</div>;
  }

  const label = `${product.brandName ?? ''} · ${product.mpnDisplay || product.mpnRaw}`;
  const verified = factsVerified(data.status);
  const platformTab = data.platforms.includes(tab as MarketingPlatform)
    ? (tab as MarketingPlatform)
    : undefined;
  const current: Tab =
    tab === 'keywords' || tab === 'records' || platformTab ? tab : 'keywords';

  return (
    <div>
      <PageTitle
        crumbs={[
          <MenuLink key="list" to="/operations/products" />,
          product.mpnDisplay || product.mpnRaw,
        ]}
        title={
          <>
            <MenuName /> · {label}
          </>
        }
        description="本公司 · 只列本公司启用的平台"
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
              {[
                product.productName,
                product.categoryNameZh || product.categoryName,
              ]
                .filter(Boolean)
                .join(' · ')}
            </div>
          </div>
          <span style={{ flex: 1 }} />
          <span style={{ fontSize: 12, color: palette.mute }}>资料</span>
          <Pill tone={verified ? 'green' : 'orange'}>
            {verified ? '资料已核对' : STAGES[data.status]?.text}
          </Pill>
          <Link to={`/product/products/${id}`}>去商品资料 →</Link>
        </div>
        {!verified && (
          <div
            style={{
              marginTop: 10,
              fontSize: 12,
              color: palette.orange,
              display: 'flex',
              gap: 6,
              alignItems: 'center',
            }}
          >
            <WarningOutlined />
            商品资料{data.status === 1 ? '尚未补全' : '尚未核对'}（
            {factsSummary(data)}）：营销内容可以先做，资料核对后才算营销完成。
          </div>
        )}
        <div
          style={{
            marginTop: 10,
            display: 'flex',
            gap: 10,
            alignItems: 'center',
          }}
        >
          <span style={{ fontSize: 12, color: palette.mute }}>营销进度</span>
          <PlatformChips progress={data} platforms={data.platforms} />
        </div>
      </Card>
      <Segmented<Tab>
        value={current}
        onChange={setTab}
        style={{ marginBottom: 16 }}
        options={[
          { value: 'keywords', label: '关键词库' },
          ...data.platforms.map((p) => ({
            value: p as Tab,
            label: PLATFORM_META[p].label,
          })),
          { value: 'records', label: '上传记录' },
        ]}
      />
      <Card>
        {current === 'keywords' ? (
          <KeywordLibrary data={data} />
        ) : current === 'records' ? (
          <ImportRecordList productId={id} type="marketing" />
        ) : (
          <PlatformPanel
            productId={id}
            platform={current}
            data={data}
            onChanged={content.load}
            onUpload={() => setUploadOpen(true)}
          />
        )}
      </Card>
      <UploadDrawer
        entry="marketing"
        open={uploadOpen}
        product={{ id, label }}
        onClose={() => setUploadOpen(false)}
        onDone={content.load}
      />
    </div>
  );
};

export default OperationsProductDetail;
