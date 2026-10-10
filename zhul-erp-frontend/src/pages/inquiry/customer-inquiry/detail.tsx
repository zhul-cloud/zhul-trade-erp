import {
  ExclamationCircleOutlined,
  FileTextOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history, Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Alert, App, Button, Select, Skeleton, Table, Tooltip } from 'antd';
import React, { useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  PATHS as QUOTATION_PATHS,
  QuotationStatusPill,
} from '@/pages/quotation/components';
import {
  type QuotationListItem,
  quotationApi,
} from '@/pages/quotation/service';
import { ChainCard } from '@/pages/sales/components';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { formatAmount, formatDateTime } from '@/utils/format';
import {
  Card,
  CustomerTypePill,
  DeadlineText,
  LevelPill,
  PageTitle,
  Pill,
  PriceSummary,
  Progress,
  StatusPill,
  TaskStatusPill,
  useLevels,
  useLifecycles,
  useWide,
} from '../shared/components';
import {
  ITEM_NO_STOCK,
  ITEM_PENDING,
  LIFECYCLE_DISCONTINUED,
  PATHS,
  PRICE_SOURCE_HISTORY,
  STATUS,
} from '../shared/constants';
import {
  type InquiryDetail,
  type InquiryItem,
  inquiryApi,
  readBizError,
} from '../shared/service';

const Meta: React.FC<{ label: string; children: React.ReactNode }> = ({
  label,
  children,
}) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ display: 'grid', gap: 2 }}>
      <span style={{ color: palette.ink, fontWeight: 600 }}>{children}</span>
      <span style={{ color: palette.mute, fontSize: 12 }}>{label}</span>
    </div>
  );
};

const CustomerInquiryDetailPage: React.FC = () => {
  const { levelOptions } = useLevels();
  const { lifecycleLabel } = useLifecycles();
  const { labelOf: sourceLabel } = useDictOptions(DICT_SOURCE_CHANNEL);
  const wide = useWide();
  const { id } = useParams<{ id: string }>();
  const inquiryId = Number(id);
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const [detail, setDetail] = useState<InquiryDetail | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [quotations, setQuotations] = useState<QuotationListItem[]>();
  const access = useAccess() as Record<string, boolean>;
  const canQuote = !!access.quotationList;

  const load = async () => {
    setError('');
    try {
      setDetail(await inquiryApi.detail(inquiryId));
    } catch (e) {
      setError(readBizError(e).message);
    }
    if (canQuote) {
      quotationApi
        .byInquiry(inquiryId)
        .then(setQuotations)
        .catch(() => setQuotations([]));
    }
  };

  /** 去报价：直接按询盘报价，带入这个询盘全部已回价的型号 */
  const goQuote = async () => {
    setBusy(true);
    try {
      const q = await quotationApi.create({ inquiryIds: [inquiryId] });
      if (q.pendingItemCount) {
        message.info(`还有 ${q.pendingItemCount} 个型号在询价，回价后可以再报`);
      }
      history.push(QUOTATION_PATHS.detail(q.id));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    load();
  }, [inquiryId]);

  // 解析中时每 5 秒刷新一次，解析完自动显示结果
  useEffect(() => {
    if (detail?.inquiry.status !== STATUS.PARSING) return undefined;
    const t = window.setInterval(load, 5000);
    return () => window.clearInterval(t);
  }, [detail?.inquiry.status]);

  const run = async (
    fn: () => Promise<unknown>,
    ok: string,
    then?: () => void,
  ) => {
    setBusy(true);
    try {
      await fn();
      message.success(ok);
      if (then) then();
      else load();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!detail) return <Skeleton active paragraph={{ rows: 12 }} />;

  const inq = detail.inquiry;
  const pricedCount = detail.items.filter((i) => i.selectedQuote).length;
  const quotedCount = detail.items.filter((i) => i.quoted).length;
  const cancellable =
    inq.status <= STATUS.READY && inq.status !== STATUS.CANCELLED;

  // 同品牌同品类的第一行才显示品牌与品类
  const firstOfGroup = new Set<number>();
  detail.items.forEach((it, i) => {
    const prev = detail.items[i - 1];
    if (!prev || prev.brandKey !== it.brandKey || prev.category !== it.category)
      firstOfGroup.add(it.id);
  });

  const columns: TableColumnsType<InquiryItem> = [
    {
      title: '品牌',
      dataIndex: 'brand',
      width: 120,
      render: (v, r) =>
        firstOfGroup.has(r.id) ? (
          <span style={{ color: palette.ink, fontWeight: 600 }}>{v}</span>
        ) : null,
    },
    {
      title: '品类',
      dataIndex: 'category',
      width: 100,
      render: (v, r) => (firstOfGroup.has(r.id) ? v || '—' : null),
    },
    {
      title: '型号',
      width: 180,
      render: (_, r) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
          <span style={{ color: palette.ink, fontWeight: 600 }}>
            {r.confirmedModel}
            {r.lifecycle === LIFECYCLE_DISCONTINUED && r.replacementModel
              ? ` → ${r.replacementModel}`
              : ''}
          </span>
          {r.lifecycle === LIFECYCLE_DISCONTINUED && (
            <span style={{ color: palette.red, fontSize: 12 }}>
              {lifecycleLabel(r.lifecycle)}
            </span>
          )}
          {r.actualModel && (
            <span style={{ color: palette.violet, fontSize: 12 }}>
              真实型号：{r.actualModel}
            </span>
          )}
        </span>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 320,
      // 描述可能很长（AI 解析出的规格说明）：自动换行，最多显示 4 行，超出部分悬停看全文
      render: (v?: string) =>
        v ? (
          <Tooltip title={v} placement="topLeft">
            <span
              style={{
                color: palette.sub,
                lineHeight: 1.6,
                whiteSpace: 'normal',
                overflowWrap: 'anywhere',
                display: '-webkit-box',
                WebkitLineClamp: 4,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
            >
              {v}
            </span>
          </Tooltip>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '数量',
      width: 80,
      render: (_, r) => `${r.quantity} ${r.unit || ''}`,
    },
    {
      title: '任务',
      dataIndex: 'taskCode',
      width: 64,
      render: (v?: string) => (v ? v.slice(13) : '—'),
    },
    {
      title: '回价',
      width: 110,
      render: (_, r) =>
        r.quoted ? (
          <Pill tone="violet" dot>
            已报出
          </Pill>
        ) : r.priceSource === PRICE_SOURCE_HISTORY ? (
          <Pill tone="cyan" dot>
            复用历史价
          </Pill>
        ) : r.quoteStatus === ITEM_PENDING ? (
          <Pill tone="orange" dot>
            待询价
          </Pill>
        ) : r.quoteStatus === ITEM_NO_STOCK ? (
          <Pill tone="mute" dot>
            无货
          </Pill>
        ) : (
          <Pill tone="green" dot>
            已回价
          </Pill>
        ),
    },
    {
      title: (
        <Tooltip title="由采购定的采购成本价：默认取采购标的推荐报价，多人比价时由采购负责人决定；业务员不能修改">
          <span>采购成本价</span>
        </Tooltip>
      ),
      width: 300,
      render: (_, r) => {
        if (r.quoteStatus === ITEM_NO_STOCK && !r.selectedQuote) {
          return (
            <span style={{ color: palette.mute, fontSize: 12 }}>
              无货{r.noStockNote ? ` · ${r.noStockNote}` : ''}
            </span>
          );
        }
        if (!r.selectedQuote)
          return (
            <span style={{ color: palette.mute, fontSize: 12 }}>
              等待采购回价
            </span>
          );
        const q = r.selectedQuote;
        return (
          <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
            <span style={{ color: palette.ink, fontWeight: 600 }}>
              <PriceSummary value={q} />
            </span>
            {q.note && (
              <span
                style={{
                  color: palette.sub,
                  fontSize: 12,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                }}
              >
                备注：{q.note}
              </span>
            )}
            <span style={{ color: palette.mute, fontSize: 12 }}>
              {q.daysAgo != null
                ? q.daysAgo === 0
                  ? '今天'
                  : `${q.daysAgo} 天前`
                : ''}
            </span>
          </span>
        );
      },
    },
    {
      title: (
        <Tooltip title="确认询盘时自动建档：商品库已有的直接关联；没有的进商品候选；写的不是型号的等采购回填真实型号">
          <span>建档</span>
        </Tooltip>
      ),
      key: 'archive',
      width: 150,
      render: (_, r) =>
        r.archiveStatus === 1 ? (
          <span
            style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}
          >
            <Pill tone="green">已建档</Pill>
            {r.productId && (
              <Link to={`/product/products/${r.productId}`}>查看商品</Link>
            )}
          </span>
        ) : r.archiveStatus === 2 ? (
          <Pill tone="orange">候选中</Pill>
        ) : r.archiveStatus === 3 ? (
          <Pill tone="gray">待回填真实型号</Pill>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={[
          <Link key="l" to={PATHS.inquiries}>
            客户询盘
          </Link>,
          inq.inquiryCode,
        ]}
        title={inq.customerName}
        actions={
          <>
            {cancellable && (
              <Button
                disabled={busy}
                onClick={() =>
                  modal.confirm({
                    title: '取消这条询盘？',
                    content:
                      '还没回价的询价任务会一并取消，从采购的待办里消失；已问到的价格仍保留在历史询价里。',
                    okText: '取消询盘',
                    okButtonProps: { danger: true },
                    cancelText: '再想想',
                    onOk: () =>
                      run(() => inquiryApi.cancel(inquiryId), '询盘已取消'),
                  })
                }
              >
                取消询盘
              </Button>
            )}
            {inq.status === STATUS.PENDING_PARSE && (
              <>
                <Button
                  disabled={busy}
                  onClick={() =>
                    history.push(`${PATHS.inquiries}/${inquiryId}/confirm`)
                  }
                >
                  手动录入
                </Button>
                <Button
                  type="primary"
                  disabled={busy}
                  onClick={() =>
                    run(
                      () => inquiryApi.startParse(inquiryId),
                      '已开始 AI 解析',
                    )
                  }
                >
                  AI 解析
                </Button>
              </>
            )}
            {inq.status === STATUS.PARSE_FAILED && (
              <>
                <Button
                  disabled={busy}
                  onClick={() =>
                    history.push(`${PATHS.inquiries}/${inquiryId}/confirm`)
                  }
                >
                  改为手动录入
                </Button>
                <Button
                  type="primary"
                  disabled={busy}
                  onClick={() =>
                    run(
                      () => inquiryApi.retryParse(inquiryId),
                      '已重新发起 AI 解析',
                    )
                  }
                >
                  重新解析
                </Button>
              </>
            )}
            {inq.status === STATUS.PENDING_CONFIRM && (
              <Button
                type="primary"
                onClick={() =>
                  history.push(`${PATHS.inquiries}/${inquiryId}/confirm`)
                }
              >
                去确认型号
              </Button>
            )}
            {canQuote &&
              inq.status >= STATUS.SOURCING &&
              inq.status !== STATUS.CANCELLED && (
                <Tooltip
                  title={
                    pricedCount === 0
                      ? '还没有回价的型号，回价后才能报价'
                      : inq.status === STATUS.SOURCING
                        ? '先报已回价的型号，其余回价后可以再报'
                        : undefined
                  }
                >
                  <Button
                    type="primary"
                    icon={<FileTextOutlined />}
                    disabled={busy || pricedCount === 0}
                    onClick={goQuote}
                  >
                    {inq.status >= STATUS.QUOTED ? '再报一张' : '去报价'}
                  </Button>
                </Tooltip>
              )}
          </>
        }
      />

      <Card style={{ marginBottom: 16, padding: 20 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 28,
            flexWrap: 'wrap',
          }}
        >
          <span style={{ display: 'inline-flex', gap: 8 }}>
            <StatusPill status={inq.status} />
            {inq.urgent && <Pill tone="red">紧急</Pill>}
            <CustomerTypePill type={inq.customerType} />
          </span>
          <Meta
            label={`${new Set(detail.items.map((i) => i.brandKey)).size} 个品牌`}
          >
            {inq.totalItemCount} 个型号
          </Meta>
          <Meta label="询盘等级">
            {inq.status === STATUS.CANCELLED ? (
              <LevelPill value={inq.level} />
            ) : (
              <Select
                size="small"
                aria-label="询盘等级"
                style={{ width: 84 }}
                value={inq.level}
                options={levelOptions}
                disabled={busy}
                onChange={(v: number) =>
                  run(
                    () => inquiryApi.updateLevel(inquiryId, v),
                    '询盘等级已调整，采购任务会按新等级排序',
                  )
                }
              />
            )}
          </Meta>
          <Meta label="负责人">{inq.ownerName || '—'}</Meta>
          <Meta label="来源">{sourceLabel(inq.source)}</Meta>
          <Meta label="询盘日期">{inq.inquiryDate}</Meta>
          <Meta label="报价截止">
            <DeadlineText date={inq.quoteDeadline} status={inq.status} />
          </Meta>
          <Meta label="创建时间">{formatDateTime(inq.createTime)}</Meta>
          {detail.opportunityCode && (
            <Meta label={`来源商机 · ${detail.opportunityStageName ?? ''}`}>
              <Link to={`/crm/opportunities/${inq.opportunityId}`}>
                {detail.opportunityCode}
              </Link>
            </Meta>
          )}
          {inq.status >= STATUS.SOURCING && (
            <span style={{ marginLeft: 'auto', display: 'grid', gap: 6 }}>
              <span style={{ color: palette.mute, fontSize: 12 }}>
                回价进度
              </span>
              <Progress
                done={inq.pricedItemCount}
                total={inq.totalItemCount}
                width={200}
              />
              {quotedCount > 0 && (
                <span style={{ color: palette.violet, fontSize: 12 }}>
                  已报出 {quotedCount}/{inq.totalItemCount}
                </span>
              )}
            </span>
          )}
        </div>
      </Card>

      {inq.needsReview && (
        <Alert
          type="warning"
          showIcon
          icon={<ExclamationCircleOutlined />}
          style={{ marginBottom: 16 }}
          title="采购以「型号存疑」退回了询价任务，请核实型号后告知采购负责人重新分配"
          description={detail.tasks
            .filter((t) => t.returnReason === 1)
            .map((t) => `${t.taskCode}：${t.returnNote || '没有填写说明'}`)
            .join('；')}
          action={
            <Button
              size="small"
              onClick={() =>
                run(() => inquiryApi.reviewed(inquiryId), '已标记核实完成')
              }
            >
              核实完成
            </Button>
          }
        />
      )}
      {inq.status === STATUS.PARSE_FAILED && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          title="AI 解析失败"
          description={`${detail.parseError || '原因未知'}。可以重新解析，或改为手动录入型号。`}
        />
      )}
      {inq.status === STATUS.PARSING && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          title="AI 正在解析，通常需要几十秒到几分钟，完成后页面会自动刷新"
        />
      )}

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide ? 'minmax(0, 1fr) 380px' : 'minmax(0, 1fr)',
          gap: 20,
          alignItems: 'start',
        }}
      >
        <div
          style={{
            display: 'grid',
            // 列宽固定为容器宽度，表格超宽时在表格内横向滚动，不撑开整页
            gridTemplateColumns: 'minmax(0, 1fr)',
            gap: 16,
            minWidth: 0,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
            <h2 style={{ margin: 0, fontSize: 16, color: palette.ink }}>
              型号明细
            </h2>
            <span
              style={{ marginLeft: 'auto', color: palette.mute, fontSize: 12 }}
            >
              同品牌、同品类的型号排在一起
            </span>
          </div>
          <Table<InquiryItem>
            rowKey="id"
            size="middle"
            scroll={{ x: 1340 }}
            columns={columns}
            dataSource={detail.items}
            pagination={false}
            locale={{
              emptyText:
                inq.status <= STATUS.PARSE_FAILED
                  ? '确认型号后显示'
                  : '没有型号',
            }}
          />
          <Card title="原始内容">
            <div
              style={{
                whiteSpace: 'pre-wrap',
                overflowWrap: 'anywhere',
                color: palette.sub,
                lineHeight: 1.7,
              }}
            >
              {detail.rawContent || (
                <span style={{ color: palette.mute }}>没有正文</span>
              )}
            </div>
            {detail.attachments.length > 0 && (
              <div
                style={{
                  display: 'flex',
                  gap: 12,
                  flexWrap: 'wrap',
                  marginTop: 12,
                }}
              >
                {detail.attachments.map((a) => (
                  <a
                    key={a.id}
                    onClick={async () => {
                      const url = await inquiryApi
                        .attachmentBlobUrl(inquiryId, a.id)
                        .catch(() => '');
                      if (url) window.open(url, '_blank', 'noopener');
                    }}
                  >
                    {a.fileName}
                  </a>
                ))}
              </div>
            )}
          </Card>
        </div>
        <div style={{ display: 'grid', gap: 12 }}>
          {canQuote && inq.status >= STATUS.SOURCING && (
            <>
              <h2 style={{ margin: 0, fontSize: 16, color: palette.ink }}>
                报价单
              </h2>
              {!quotations ? (
                <Skeleton active paragraph={{ rows: 2 }} />
              ) : quotations.length === 0 ? (
                <Card style={{ padding: 16 }}>
                  <span style={{ color: palette.mute }}>
                    {pricedCount > 0
                      ? '还没有报价单。点右上角「去报价」，带入已回价的型号，定价后发给客户。'
                      : '型号回价后就可以报价，询价中的询盘可以先报已回价的型号。'}
                  </span>
                </Card>
              ) : (
                quotations.map((qt) => (
                  <Card key={qt.id} style={{ padding: 16 }}>
                    <div
                      style={{ display: 'flex', alignItems: 'center', gap: 8 }}
                    >
                      <Link to={QUOTATION_PATHS.detail(qt.id)}>
                        <b>{qt.quotationNo}</b>
                      </Link>
                      <span style={{ marginLeft: 'auto' }}>
                        <QuotationStatusPill status={qt.status} />
                      </span>
                    </div>
                    <div
                      style={{
                        display: 'flex',
                        gap: 8,
                        marginTop: 8,
                        color: palette.sub,
                        fontSize: 12,
                      }}
                    >
                      <span>{qt.itemCount} 个型号</span>
                      <b
                        style={{
                          marginLeft: 'auto',
                          color: palette.ink,
                          fontVariantNumeric: 'tabular-nums',
                        }}
                      >
                        {formatAmount(qt.totalAmount, qt.currencyCode)}
                      </b>
                    </div>
                    <div
                      style={{
                        color: palette.mute,
                        fontSize: 12,
                        marginTop: 4,
                      }}
                    >
                      {qt.ownerName ?? '—'} ·{' '}
                      {formatDateTime(qt.sentAt ?? qt.createTime).slice(0, 16)}
                      {qt.sentAt ? ' 发送' : ' 创建'}
                    </div>
                  </Card>
                ))
              )}
              {quotations && quotations.length > 0 && (
                <ChainCard type="inquiry" id={inquiryId} />
              )}
            </>
          )}
          <h2 style={{ margin: 0, fontSize: 16, color: palette.ink }}>
            询价任务
          </h2>
          {detail.tasks.length === 0 && (
            <Card>
              <span style={{ color: palette.mute }}>
                {inq.status >= STATUS.SOURCING
                  ? '全部型号复用了历史价，不需要询价。'
                  : '确认型号后，待询价的型号会按品牌拆成询价任务。'}
              </span>
            </Card>
          )}
          {detail.tasks.map((t) => (
            <Card key={t.id} style={{ padding: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ color: palette.link, fontWeight: 600 }}>
                  {t.taskCode}
                </span>
                <span style={{ marginLeft: 'auto' }}>
                  <TaskStatusPill status={t.status} timeout={t.timeout} />
                </span>
              </div>
              <div style={{ color: palette.ink, margin: '8px 0' }}>
                {t.brand}
                {t.category ? ` · ${t.category}` : ''}
              </div>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  color: palette.sub,
                  fontSize: 12,
                }}
              >
                <UserOutlined />
                {t.assigneeNames.length
                  ? t.assigneeNames.join(' + ')
                  : '待分配'}
                {t.assigneeNames.length > 1 && (
                  <span style={{ color: palette.mute }}>（比价）</span>
                )}
                <span style={{ marginLeft: 'auto', color: palette.mute }}>
                  已回价 {t.pricedCount}/{t.itemCount}
                </span>
              </div>
              {t.timeout && t.firstAssignedAt && (
                <div style={{ color: palette.red, fontSize: 12, marginTop: 6 }}>
                  分配于 {formatDateTime(t.firstAssignedAt).slice(5, 16)}
                  ，已超过回价时限
                </div>
              )}
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
};

export default CustomerInquiryDetailPage;
