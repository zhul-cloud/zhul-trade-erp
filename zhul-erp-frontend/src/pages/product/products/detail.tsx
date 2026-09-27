import { ArrowLeftOutlined, LockOutlined } from '@ant-design/icons';
import { history, useAccess, useParams, useSearchParams } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Dropdown,
  Form,
  Input,
  Modal,
  Select,
  Skeleton,
  Space,
  Switch,
} from 'antd';
import dayjs from 'dayjs';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { CompletenessBar } from '../components/CompletenessBar';
import { ErrorHint } from '../components/EmptyHint';
import { LifecyclePill, Pill } from '../components/Pills';
import { COMPLETENESS_MODULES, LIFECYCLE_OPTIONS } from '../constants';
import { type Product, productApi, readBizError } from '../service';
import { ProductThemeProvider, useProductTheme } from '../theme';
import { BasicCard } from './cards/BasicCard';
import { UnsavedContext, type UnsavedEntry } from './cards/CardShell';
import { ApplicationCard, DocumentCard, FaqCard } from './cards/ContentCards';
import { LogisticsCustomsCard } from './cards/LogisticsCustomsCard';
import { MediaCard } from './cards/MediaCard';
import { PriceCard } from './cards/PriceCard';
import { RelationCard } from './cards/RelationCard';
import { SpecCard } from './cards/SpecCard';

const scrollToCard = (anchor: string) => {
  const el = document.getElementById(anchor);
  if (!el) return;
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  el.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
  el.querySelector<HTMLElement>('h2')?.focus?.();
};

const panel = (palette: {
  card: string;
  hairline: string;
}): React.CSSProperties => ({
  background: palette.card,
  border: `1px solid ${palette.hairline}`,
  borderRadius: 16,
  padding: 24,
  marginBottom: 20,
});

/** 侧栏：状态、生命周期、被引用、信息 */
const Sidebar: React.FC<{
  product: Product;
  platform: boolean;
  canEdit: boolean;
  onChanged: () => void;
  onToggleStatus: () => void;
}> = ({ product, platform, canEdit, onChanged, onToggleStatus }) => {
  const { message } = App.useApp();
  const { palette } = useProductTheme();
  const [form] = Form.useForm<{
    lifecycleStatus: number;
    lifecycleSource: string;
  }>();
  const [saving, setSaving] = useState(false);
  const lifecycle = Form.useWatch('lifecycleStatus', form);

  useEffect(() => {
    form.setFieldsValue({
      lifecycleStatus: product.lifecycleStatus,
      lifecycleSource: product.lifecycleSource,
    });
  }, [product, form]);

  const saveLifecycle = async () => {
    const v = await form.validateFields();
    setSaving(true);
    try {
      const saved = await productApi.update(product.id, {
        brandId: product.brandId,
        categoryId: product.categoryId,
        seriesId: product.seriesId ?? null,
        mpnRaw: product.mpnRaw,
        mpnDisplay: product.mpnDisplay,
        productName: product.productName,
        specSummary: product.specSummary,
        shortDescription: product.shortDescription,
        ...v,
      });
      message.success('生命周期已保存');
      for (const w of saved.warnings ?? []) message.warning(w);
      onChanged();
    } finally {
      setSaving(false);
    }
  };

  const row = (label: string, value: React.ReactNode) => (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        gap: 12,
        padding: '6px 0',
        fontSize: 13,
      }}
    >
      <span style={{ color: palette.mute }}>{label}</span>
      <span style={{ color: palette.ink, textAlign: 'right' }}>{value}</span>
    </div>
  );

  return (
    <aside aria-label="状态与信息">
      {platform && (
        <div style={panel(palette)}>
          <h2 style={{ margin: '0 0 12px', fontSize: 16 }}>状态</h2>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 16,
            }}
          >
            <span>启用</span>
            {canEdit ? (
              <Switch
                checked={product.status === 1}
                onClick={onToggleStatus}
                aria-label={
                  product.status === 1 ? '已启用，点击停用' : '已停用，点击启用'
                }
              />
            ) : (
              <Pill tone={product.status === 1 ? 'green' : 'gray'}>
                {product.status === 1 ? '启用' : '已停用'}
              </Pill>
            )}
          </div>
          <Form form={form} layout="vertical" requiredMark={false}>
            <Form.Item name="lifecycleStatus" label="生命周期">
              <Select disabled={!canEdit} options={LIFECYCLE_OPTIONS} />
            </Form.Item>
            <Form.Item
              name="lifecycleSource"
              label="生命周期依据"
              extra={
                lifecycle === 4 || lifecycle === 5
                  ? '已停产或停产无替代必须写明依据，如厂商停产通知'
                  : undefined
              }
              rules={[
                {
                  validator: (_, v) =>
                    (lifecycle === 4 || lifecycle === 5) &&
                    !String(v ?? '').trim()
                      ? Promise.reject(new Error('停产类状态必须填写依据'))
                      : Promise.resolve(),
                },
              ]}
            >
              <Input disabled={!canEdit} maxLength={128} />
            </Form.Item>
            {canEdit && (
              <Button loading={saving} onClick={saveLifecycle}>
                保存生命周期
              </Button>
            )}
          </Form>
        </div>
      )}
      {!platform && (
        <div style={panel(palette)}>
          <h2 style={{ margin: '0 0 12px', fontSize: 16 }}>生命周期</h2>
          <LifecyclePill value={product.lifecycleStatus} />
          {product.lifecycleSource && (
            <p style={{ margin: '8px 0 0', fontSize: 13, color: palette.sub }}>
              依据：{product.lifecycleSource}
            </p>
          )}
        </div>
      )}
      {platform && (
        <div style={panel(palette)}>
          <h2 style={{ margin: '0 0 8px', fontSize: 16 }}>被引用</h2>
          <div className="num" style={{ fontSize: 24, fontWeight: 700 }}>
            {product.usageCount ?? 0} 处
          </div>
          <p style={{ margin: '4px 0 0', fontSize: 13, color: palette.sub }}>
            {(product.usageCount ?? 0) > 0
              ? '被引用后，品牌和型号已锁定，商品不能删除，只能停用。'
              : '还没有被询盘、报价等单据引用。'}
          </p>
        </div>
      )}
      <div style={panel(palette)}>
        <h2 style={{ margin: '0 0 8px', fontSize: 16 }}>信息</h2>
        {row('可见范围', '所有租户共用')}
        {row(
          '创建',
          <span className="num">
            {dayjs(product.createTime).format('YYYY-MM-DD HH:mm')}
          </span>,
        )}
        {row(
          '最近更新',
          <span className="num">
            {dayjs(product.updateTime).format('YYYY-MM-DD HH:mm')}
          </span>,
        )}
        {row(
          '归一化型号',
          <span style={{ fontFamily: "'IBM Plex Mono', monospace" }}>
            {product.mpnNormalized}
          </span>,
        )}
      </div>
    </aside>
  );
};

const DetailInner: React.FC = () => {
  const { message, modal } = App.useApp();
  const { palette } = useProductTheme();
  const access = useAccess();
  const params = useParams<{ id: string }>();
  const [search] = useSearchParams();
  const id = Number(params.id);
  const platform = !!access.productPlatform;
  const canEdit = platform && !!access['product:product:edit'];
  const readOnly = !canEdit;

  const [product, setProduct] = useState<Product>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [unsavedCount, setUnsavedCount] = useState(0);
  const unsaved = useRef(new Map<string, UnsavedEntry>());
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setProduct(await productApi.get(id));
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    setLoading(true);
    load();
  }, [load]);

  // ?focus=faq 之类：打开时直接滚动到对应卡片
  useEffect(() => {
    const focus = search.get('focus');
    if (!product || !focus) return;
    const m = COMPLETENESS_MODULES.find((x) => x.key === focus);
    if (m) setTimeout(() => scrollToCard(m.anchor), 100);
  }, [product, search]);

  const registry = useMemo(
    () => ({
      set: (key: string, entry: UnsavedEntry | null) => {
        if (entry) unsaved.current.set(key, entry);
        else unsaved.current.delete(key);
        setUnsavedCount(unsaved.current.size);
      },
    }),
    [],
  );

  // 有未保存修改时，关闭标签页和站内跳转都要提示
  useEffect(() => {
    if (unsavedCount === 0) return;
    const onBefore = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener('beforeunload', onBefore);
    const unblock = history.block((tx) => {
      modal.confirm({
        title: '有未保存的修改',
        content: '现在离开，这些修改会丢失。',
        okText: '离开',
        cancelText: '留下',
        onOk: () => {
          unblock();
          tx.retry();
        },
      });
    });
    return () => {
      window.removeEventListener('beforeunload', onBefore);
      unblock();
    };
  }, [unsavedCount, modal]);

  const saveAll = async () => {
    setSaving(true);
    try {
      for (const entry of [...unsaved.current.values()]) await entry.save();
    } catch {
      /* 校验失败的卡片会在卡片内显示原因，其余卡片保持编辑态 */
    } finally {
      setSaving(false);
    }
  };
  const discardAll = () => {
    for (const entry of unsaved.current.values()) entry.discard();
  };

  const toggleStatus = () => {
    if (!product) return;
    const disabling = product.status === 1;
    modal.confirm({
      title: disabling
        ? `停用「${product.mpnDisplay}」？`
        : `启用「${product.mpnDisplay}」？`,
      content: disabling
        ? '停用后商品不再出现在选择器和匹配结果中，已引用它的单据不受影响。'
        : '启用后商品可以被单据选用。',
      okText: disabling ? '停用' : '启用',
      cancelText: '取消',
      onOk: async () => {
        await productApi.setStatus(product.id, disabling ? 0 : 1);
        message.success(disabling ? '已停用' : '已启用');
        load();
      },
    });
  };

  if (loading) {
    return (
      <div style={{ padding: 8 }}>
        <Skeleton active paragraph={{ rows: 8 }} />
      </div>
    );
  }
  if (error || !product) {
    return (
      <ErrorHint message={error ?? '商品不存在或已被删除'} onRetry={load} />
    );
  }

  const c = product.completeness;
  const doneOf = (key: string): boolean | undefined =>
    platform ? c?.modules.find((m) => m.key === key)?.done : undefined;
  const usage = product.usageCount ?? 0;
  const common = { productId: product.id, readOnly, onChanged: load };

  return (
    <UnsavedContext.Provider value={registry}>
      <Button
        type="link"
        icon={<ArrowLeftOutlined />}
        onClick={() => history.push('/product/products')}
        style={{ paddingLeft: 0, marginBottom: 8 }}
      >
        返回商品列表
      </Button>

      <div
        style={{
          ...panel(palette),
          display: 'flex',
          gap: 20,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <div style={{ flex: 1, minWidth: 260 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              flexWrap: 'wrap',
            }}
          >
            <h1
              id="product-main"
              tabIndex={-1}
              style={{ margin: 0, fontSize: 28, fontWeight: 700 }}
            >
              {product.mpnDisplay}
            </h1>
            <LifecyclePill value={product.lifecycleStatus} />
            {platform && (
              <Pill tone={product.status === 1 ? 'green' : 'gray'}>
                {product.status === 1 ? '启用' : '已停用'}
              </Pill>
            )}
            {!canEdit && (
              <Pill tone="gray">
                <LockOutlined style={{ marginRight: 4 }} />
                只读
              </Pill>
            )}
          </div>
          <div style={{ color: palette.sub, marginTop: 6 }}>
            {[product.brandName, product.categoryName, product.seriesName]
              .filter(Boolean)
              .join(' · ')}
            {product.productName ? ` · ${product.productName}` : ''}
          </div>
        </div>
        <Space>
          {canEdit && (
            <>
              <Button onClick={toggleStatus}>
                {product.status === 1 ? '停用' : '启用'}
              </Button>
              <Dropdown
                menu={{
                  items: [
                    {
                      key: 'delete',
                      danger: true,
                      label: '删除商品…',
                      onClick: () => setDeleteOpen(true),
                    },
                  ],
                }}
                trigger={['click']}
              >
                <Button aria-label="更多操作">更多</Button>
              </Dropdown>
            </>
          )}
        </Space>
      </div>

      {platform && usage > 0 && (
        <Alert
          style={{ marginBottom: 20, borderRadius: 12 }}
          type="info"
          showIcon
          title={`已被 ${usage} 处引用：品牌和型号已锁定，其余信息都可以改`}
        />
      )}
      {(product.warnings ?? []).map((w) => (
        <Alert
          key={w}
          style={{ marginBottom: 20, borderRadius: 12 }}
          type="warning"
          showIcon
          title={w}
        />
      ))}

      <div
        style={{
          display: 'flex',
          gap: 20,
          alignItems: 'flex-start',
          flexWrap: 'wrap',
        }}
      >
        <div style={{ flex: '1 1 560px', minWidth: 0 }}>
          {platform && c && (
            <section
              aria-labelledby="completeness-title"
              style={panel(palette)}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'baseline',
                  gap: 12,
                  marginBottom: 12,
                }}
              >
                <h2 id="completeness-title" style={{ margin: 0, fontSize: 16 }}>
                  档案完整度
                </h2>
                <span
                  className="num"
                  style={{
                    fontSize: 32,
                    fontWeight: 800,
                    background:
                      'linear-gradient(90deg,#BFDBFE,#60A5FA,#22D3EE)',
                    WebkitBackgroundClip: 'text',
                    color: 'transparent',
                  }}
                >
                  {c.done} / {c.total} 项
                </span>
                <span style={{ color: palette.sub }}>
                  {c.done === c.total
                    ? '档案很完整了'
                    : `还差 ${c.total - c.done} 项就完整了`}
                </span>
              </div>
              <CompletenessBar value={c} size="full" />
              {c.missing.length > 0 && (
                <ul
                  style={{
                    listStyle: 'none',
                    margin: '16px 0 0',
                    padding: 0,
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 8,
                  }}
                >
                  {c.missing.map((key) => {
                    const m = COMPLETENESS_MODULES.find((x) => x.key === key);
                    if (!m) return null;
                    return (
                      <li key={key}>
                        <button
                          type="button"
                          onClick={() => scrollToCard(m.anchor)}
                          style={{
                            height: 36,
                            padding: '0 14px',
                            borderRadius: 18,
                            border: `1px solid ${palette.accentLine}`,
                            background: palette.accentSoft,
                            color: palette.link,
                            cursor: 'pointer',
                            fontSize: 13,
                            fontWeight: 600,
                          }}
                        >
                          补 {m.label} · 约{' '}
                          <span className="num">{m.minutes}</span> 分钟
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>
          )}

          <BasicCard
            product={product}
            readOnly={readOnly}
            done={doneOf('basic')}
            onChanged={load}
          />
          <MediaCard {...common} done={doneOf('media')} />
          <SpecCard {...common} done={doneOf('specifications')} />
          <LogisticsCustomsCard
            productId={product.id}
            readOnly={readOnly}
            logisticsDone={doneOf('logistics')}
            customsDone={doneOf('customs')}
            onChanged={load}
          />
          <PriceCard {...common} done={doneOf('referencePrice')} />
          <RelationCard {...common} done={doneOf('relationships')} />
          <DocumentCard {...common} done={doneOf('documents')} />
          <ApplicationCard {...common} done={doneOf('applications')} />
          <FaqCard {...common} done={doneOf('faq')} platform={platform} />
        </div>
        <div style={{ flex: '0 0 340px', maxWidth: '100%' }}>
          <Sidebar
            product={product}
            platform={platform}
            canEdit={canEdit}
            onChanged={load}
            onToggleStatus={toggleStatus}
          />
        </div>
      </div>

      {unsavedCount > 0 && (
        <section
          aria-label="未保存的修改"
          style={{
            position: 'fixed',
            left: '50%',
            bottom: 24,
            transform: 'translateX(-50%)',
            zIndex: 20,
            display: 'flex',
            alignItems: 'center',
            gap: 16,
            padding: '12px 20px',
            borderRadius: 16,
            background: '#0A101D',
            color: '#F8FAFC',
            boxShadow: '0 24px 60px -12px rgba(0,0,0,0.6)',
            border: '1px solid #234681',
          }}
        >
          <span>有 {unsavedCount} 张卡片的修改还没保存</span>
          <Button onClick={discardAll}>放弃</Button>
          <Button type="primary" loading={saving} onClick={saveAll}>
            全部保存
          </Button>
        </section>
      )}

      <Modal
        open={deleteOpen}
        title="删除这个商品？"
        onCancel={() => setDeleteOpen(false)}
        footer={[
          <Button key="cancel" onClick={() => setDeleteOpen(false)}>
            取消
          </Button>,
          <Button
            key="disable"
            onClick={async () => {
              await productApi.setStatus(product.id, 0);
              message.success('已改为停用');
              setDeleteOpen(false);
              load();
            }}
          >
            改为停用
          </Button>,
          <Button
            key="delete"
            danger
            type="primary"
            disabled={usage > 0}
            onClick={async () => {
              await productApi.remove(product.id);
              message.success('已删除');
              history.push('/product/products');
            }}
          >
            删除
          </Button>,
        ]}
      >
        {usage > 0 ? (
          <Alert
            type="warning"
            showIcon
            title={`已被 ${usage} 处引用，请改为停用`}
          />
        ) : (
          <Alert
            type="error"
            showIcon
            title="删除是软删除"
            description="商品会从列表和选择器中消失，规格、图片等资料原样保留。之后再新建同一个型号会提示「曾被删除，可恢复」。如果只是暂时不用，改为停用更稳妥。"
          />
        )}
      </Modal>
    </UnsavedContext.Provider>
  );
};

const ProductDetailPage: React.FC = () => (
  <ProductThemeProvider>
    <DetailInner />
  </ProductThemeProvider>
);

export default ProductDetailPage;
