import {
  ArrowLeftOutlined,
  EditOutlined,
  EnvironmentOutlined,
  FileTextOutlined,
  InfoCircleOutlined,
  SwapOutlined,
  UserOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams } from '@umijs/max';
import { Button, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useCountries } from '@/pages/product/components/useCountries';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  GradePill,
  LIST_PATH,
  PageTitle,
  PartyGroups,
  RolePill,
  SectionCard,
  StatusPill,
  TransferModal,
} from './components';
import {
  CURRENCY_OPTIONS,
  INDUSTRY_OPTIONS,
  labelOf,
  PAYMENT_OPTIONS,
  SHIPPING_OPTIONS,
  SOURCE_OPTIONS,
} from './constants';
import { type CustomerDetail, customerApi, readBizError } from './service';
import { localTimeInfo, zoneLabel } from './time';

const Field: React.FC<{
  label: string;
  children?: React.ReactNode;
  full?: boolean;
  mono?: boolean;
}> = ({ label, children, full, mono }) => {
  const { palette } = useAppTheme();
  const empty = children === undefined || children === null || children === '';
  return (
    <div style={{ gridColumn: full ? '1 / -1' : undefined, minWidth: 0 }}>
      <dt style={{ fontSize: 13, color: palette.mute, marginBottom: 6 }}>
        {label}
      </dt>
      <dd
        className={mono ? 'num' : undefined}
        style={{
          margin: 0,
          fontSize: 14,
          fontWeight: 600,
          color: empty ? palette.mute : palette.ink,
          wordBreak: 'break-word',
        }}
      >
        {empty ? '—' : children}
      </dd>
    </div>
  );
};

const Grid: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <dl
    style={{
      display: 'grid',
      gridTemplateColumns:
        'repeat(auto-fit, minmax(max(240px, calc(50% - 12px)), 1fr))',
      gap: '20px 24px',
      margin: 0,
    }}
  >
    {children}
  </dl>
);

/** 客户当地时间（每分钟刷新） */
const LocalTimeCard: React.FC<{ timezone: string }> = ({ timezone }) => {
  const { palette } = useAppTheme();
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  const info = timezone ? localTimeInfo(timezone, now) : null;
  const diff = info
    ? info.diffHours === 0
      ? '与北京时间相同'
      : `比北京${info.diffHours < 0 ? '晚' : '早'} ${Math.abs(info.diffHours)} 小时`
    : '';
  return (
    <SideCard title="客户当地时间">
      {info ? (
        <>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span
              className="num"
              style={{
                fontSize: 40,
                fontWeight: 700,
                color: palette.ink,
                lineHeight: 1,
              }}
            >
              {info.time}
            </span>
            <span
              style={{
                padding: '2px 10px',
                borderRadius: 12,
                fontSize: 12,
                fontWeight: 600,
                color: info.working ? palette.green : palette.sub,
                background: info.working ? palette.greenSoft : palette.inset,
              }}
            >
              {info.working ? '工作时间' : '非工作时间'}
            </span>
          </div>
          <div style={{ fontSize: 13, color: palette.sub }}>
            {zoneLabel(timezone)} · {diff}
          </div>
          <div style={{ fontSize: 12, color: palette.mute, lineHeight: 1.6 }}>
            当地 9:00–18:00 显示「工作时间」，方便安排电话和 WhatsApp 联系。
          </div>
        </>
      ) : (
        <div style={{ fontSize: 13, color: palette.mute }}>
          还没有设置时区。编辑客户选择时区后，这里会显示客户当地时间。
        </div>
      )}
    </SideCard>
  );
};

const SideCard: React.FC<{ title: string; children: React.ReactNode }> = ({
  title,
  children,
}) => {
  const { palette } = useAppTheme();
  return (
    <section
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 14,
        padding: 20,
        borderRadius: 16,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <h2
        style={{ margin: 0, fontSize: 14, fontWeight: 600, color: palette.ink }}
      >
        {title}
      </h2>
      {children}
    </section>
  );
};

const SideRow: React.FC<{ label: string; children: React.ReactNode }> = ({
  label,
  children,
}) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: 12,
        fontSize: 13,
      }}
    >
      <span style={{ color: palette.mute }}>{label}</span>
      <span style={{ color: palette.ink, fontWeight: 600, textAlign: 'right' }}>
        {children || '—'}
      </span>
    </div>
  );
};

const CustomerDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const access = useAccess() as Record<string, boolean>;
  const { palette } = useAppTheme();
  const { labelOf: countryLabel, zhOf } = useCountries();
  const [record, setRecord] = useState<CustomerDetail | null>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [transferOpen, setTransferOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setRecord(await customerApi.detail(Number(id)));
    } catch (e) {
      const err = readBizError(e);
      if (err.message.includes('不存在')) setRecord(null);
      else setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const back = () => history.push(LIST_PATH);
  const link = (href: string, value?: string) =>
    value ? (
      <a href={href} target="_blank" rel="noreferrer">
        {value}
      </a>
    ) : undefined;

  let body: React.ReactNode;
  let title: React.ReactNode = '客户详情';
  let actions: React.ReactNode = (
    <Button icon={<ArrowLeftOutlined />} onClick={back}>
      返回
    </Button>
  );
  if (loading) {
    body = (
      <div
        style={{
          background: palette.card,
          borderRadius: 16,
          padding: 24,
          border: `1px solid ${palette.hairline}`,
        }}
      >
        <Skeleton active paragraph={{ rows: 12 }} />
      </div>
    );
  } else if (error) {
    body = <ErrorHint message={error} onRetry={load} />;
  } else if (!record) {
    body = (
      <EmptyHint
        title="客户不存在或无权查看"
        description="该客户可能已被删除，或不在你的数据范围内。"
        actionText="返回列表"
        onAction={back}
      />
    );
  } else {
    const r = record;
    title = (
      <>
        <span style={{ wordBreak: 'break-word' }}>{r.name}</span>
        <RolePill value={r.customerRole} />
        <GradePill value={r.customerGrade} />
        <StatusPill status={r.status} />
      </>
    );
    actions = (
      <>
        <Button icon={<ArrowLeftOutlined />} onClick={back}>
          返回
        </Button>
        {access['partner:customer:transfer'] && (
          <Button icon={<SwapOutlined />} onClick={() => setTransferOpen(true)}>
            转移
          </Button>
        )}
        {access['partner:customer:edit'] && (
          <Button
            type="primary"
            icon={<EditOutlined />}
            onClick={() =>
              history.push(`${LIST_PATH}/${r.id}/edit?from=detail`)
            }
          >
            编辑
          </Button>
        )}
      </>
    );
    const payment = labelOf(PAYMENT_OPTIONS, r.paymentMethod);
    const summary = [
      r.currency,
      r.incoterm && `${r.incoterm} ${r.incotermPlace}`,
      payment &&
        [
          payment,
          r.depositRatio != null && `${r.depositRatio}% 定金`,
          r.paymentDays != null && `${r.paymentDays} 天`,
        ]
          .filter(Boolean)
          .join(' '),
    ]
      .filter(Boolean)
      .join(' · ');
    body = (
      <div
        style={{
          display: 'flex',
          gap: 20,
          alignItems: 'flex-start',
          flexWrap: 'wrap',
        }}
      >
        <div style={{ flex: '1 1 440px', minWidth: 0 }}>
          <SectionCard icon={<InfoCircleOutlined />} title="基本信息">
            <Grid>
              <Field label="客户编码" mono>
                {r.customerCode}
              </Field>
              <Field label="客户简称">{r.shortName}</Field>
              <Field label="中文名称">{r.nameCn}</Field>
              <Field label="官网">{link(r.website, r.website)}</Field>
              <Field label="应用行业">
                {labelOf(INDUSTRY_OPTIONS, r.industry)}
              </Field>
              <Field label="小满客户编号">{r.externalRef}</Field>
              <Field label="备注" full>
                {r.remark}
              </Field>
            </Grid>
          </SectionCard>
          <SectionCard icon={<EnvironmentOutlined />} title="注册地址与税务">
            <Grid>
              <Field label="国家/地区">{countryLabel(r.country)}</Field>
              <Field label="州/省">{r.state}</Field>
              <Field label="城市">{r.city}</Field>
              <Field label="邮编">{r.postcode}</Field>
              <Field label="详细地址（英文）" full>
                {r.address}
              </Field>
              <Field label="税号" mono>
                {r.taxId}
              </Field>
              <Field label="时区">{r.timezone && zoneLabel(r.timezone)}</Field>
            </Grid>
          </SectionCard>
          <SectionCard icon={<UserOutlined />} title="主联系人">
            <Grid>
              <Field label="联系人姓名">{r.contactName}</Field>
              <Field label="职位">{r.contactTitle}</Field>
              <Field label="邮箱">
                {link(`mailto:${r.contactEmail}`, r.contactEmail)}
              </Field>
              <Field label="电话">
                {link(
                  `tel:${r.contactPhone.replace(/[^+\d]/g, '')}`,
                  r.contactPhone,
                )}
              </Field>
              <Field label="WhatsApp">
                {link(
                  `https://wa.me/${r.whatsapp.replace(/\D/g, '')}`,
                  r.whatsapp,
                )}
              </Field>
              <Field label="其他联系方式">{r.otherIm}</Field>
            </Grid>
          </SectionCard>
          <SectionCard icon={<WalletOutlined />} title="默认交易条件">
            <div
              style={{
                padding: '14px 16px',
                marginBottom: 20,
                borderRadius: 12,
                fontSize: 16,
                fontWeight: 600,
                background: palette.accentSoft,
                border: `1px solid ${palette.accentLine}`,
              }}
            >
              {summary}
            </div>
            <Grid>
              <Field label="默认币种">
                {CURRENCY_OPTIONS.find((c) => c.value === r.currency)?.label}
              </Field>
              <Field label="贸易术语">
                {r.incoterm && `${r.incoterm} ${r.incotermPlace}`}
              </Field>
              <Field label="付款方式">{payment}</Field>
              {r.depositRatio != null && (
                <Field label="定金比例">{`${r.depositRatio}%`}</Field>
              )}
              {r.paymentDays != null && (
                <Field label="账期">{`${r.paymentDays} 天`}</Field>
              )}
              <Field label="信用额度" mono>
                {r.creditLimit != null &&
                  `${r.creditCurrency} ${Number(r.creditLimit).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`}
              </Field>
              <Field label="运输方式">
                {labelOf(SHIPPING_OPTIONS, r.shippingMethod)}
              </Field>
              <Field label="目的港">{r.destinationPort}</Field>
            </Grid>
          </SectionCard>
          <SectionCard icon={<FileTextOutlined />} title="收货与单证信息">
            <PartyGroups parties={r.parties} countryLabel={countryLabel} />
          </SectionCard>
        </div>
        <aside
          aria-label="负责人与系统信息"
          style={{
            flex: '0 0 320px',
            maxWidth: '100%',
            display: 'flex',
            flexDirection: 'column',
            gap: 20,
          }}
        >
          <SideCard title="负责与归属">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: 18,
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 700,
                  color: palette.link,
                  background: palette.accentSoft,
                }}
              >
                {(r.ownerName || '?').slice(0, 1)}
              </span>
              <div>
                <div style={{ fontWeight: 600, color: palette.ink }}>
                  {r.ownerName || '未分配'}
                </div>
                <div style={{ fontSize: 12, color: palette.mute }}>
                  负责业务员{r.ownerDeptName ? ` · ${r.ownerDeptName}` : ''}
                </div>
              </div>
            </div>
            <SideRow label="客户来源">
              {labelOf(SOURCE_OPTIONS, r.sourceChannel)}
            </SideRow>
            <SideRow label="客户等级">
              <GradePill value={r.customerGrade} />
            </SideRow>
            <SideRow label="所在国家">{zhOf(r.country)}</SideRow>
          </SideCard>
          <LocalTimeCard timezone={r.timezone} />
          <SideCard title="系统信息">
            <SideRow label="创建人">{r.createBy}</SideRow>
            <SideRow label="创建时间">
              <span className="num">{formatDateTime(r.createTime)}</span>
            </SideRow>
            <SideRow label="最后修改人">{r.updateBy}</SideRow>
            <SideRow label="最后修改时间">
              <span className="num">{formatDateTime(r.updateTime)}</span>
            </SideRow>
          </SideCard>
        </aside>
        <TransferModal
          open={transferOpen}
          customers={[r]}
          onClose={() => setTransferOpen(false)}
          onDone={() => {
            setTransferOpen(false);
            back();
          }}
        />
      </div>
    );
  }

  return (
    <div style={{ color: palette.ink }}>
      <a className="zhul-skip" href="#customer-main">
        跳到主要内容
      </a>
      <PageTitle
        title={title}
        current="客户详情"
        description={
          record
            ? [
                record.nameCn,
                record.customerCode,
                zhOf(record.country),
                record.city,
              ]
                .filter(Boolean)
                .join(' · ')
            : undefined
        }
        actions={actions}
      />
      {body}
    </div>
  );
};

export default CustomerDetailPage;
