import {
  AppstoreOutlined,
  ArrowLeftOutlined,
  BankOutlined,
  EditOutlined,
  HistoryOutlined,
  InfoCircleOutlined,
  PhoneOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams } from '@umijs/max';
import { Button, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  LIST_PATH,
  PageTitle,
  SectionCard,
  StatusPill,
  SupplierTypePill,
} from './components';
import { INDUSTRY_OPTIONS, labelOf } from './constants';
import { ScopeLines } from './productScope';
import { readBizError, type SupplierItem, supplierApi } from './service';

const EMPTY = '—';

/** 键值对：标签在上、值在下；full 跨两列 */
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
          wordBreak: 'break-all',
        }}
      >
        {empty ? EMPTY : children}
      </dd>
    </div>
  );
};

const Grid: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <dl
    style={{
      display: 'grid',
      gridTemplateColumns:
        'repeat(auto-fit, minmax(max(280px, calc(50% - 12px)), 1fr))',
      gap: '20px 24px',
      margin: 0,
    }}
  >
    {children}
  </dl>
);

const SupplierDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const access = useAccess() as Record<string, boolean>;
  const { palette } = useAppTheme();
  const [record, setRecord] = useState<SupplierItem | null>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setRecord(await supplierApi.get(Number(id)));
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const back = () => history.push(LIST_PATH);
  const canEdit = !!access['partner:supplier:edit'] && !!record;

  let body: React.ReactNode;
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
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    );
  } else if (error) {
    body = <ErrorHint message={error} onRetry={load} />;
  } else if (!record) {
    body = (
      <EmptyHint
        title="供应商不存在"
        description="该供应商可能已被删除。"
        actionText="返回列表"
        onAction={back}
      />
    );
  } else {
    const capital =
      record.registeredCapital === null ||
      record.registeredCapital === undefined
        ? undefined
        : Number(record.registeredCapital).toFixed(2);
    body = (
      <>
        <SectionCard icon={<InfoCircleOutlined />} title="基本信息">
          <Grid>
            <Field label="供应商编码" mono>
              {record.supplierCode}
            </Field>
            <Field label="供应商名称">{record.name}</Field>
            <Field label="供应商简称">{record.shortName}</Field>
            <Field label="供应商类型">
              <SupplierTypePill value={record.supplierType} />
            </Field>
            <Field label="所属行业">
              {labelOf(INDUSTRY_OPTIONS, record.industry)}
            </Field>
            <Field label="状态">
              <StatusPill status={record.status} />
            </Field>
          </Grid>
        </SectionCard>

        <SectionCard icon={<BankOutlined />} title="工商信息">
          <Grid>
            <Field label="统一社会信用代码" mono>
              {record.creditCode}
            </Field>
            <Field label="法人代表">{record.legalRepresentative}</Field>
            <Field label="注册资本（万元人民币）" mono>
              {capital}
            </Field>
            <Field label="成立日期" mono>
              {record.establishedDate}
            </Field>
          </Grid>
        </SectionCard>

        <SectionCard icon={<PhoneOutlined />} title="联系信息">
          <Grid>
            <Field label="联系人">{record.contactName}</Field>
            <Field label="联系电话" mono>
              {record.contactPhone}
            </Field>
            <Field label="联系邮箱">{record.contactEmail}</Field>
            <Field label="所在地区">
              {record.region ? record.region.split('/').join(' / ') : ''}
            </Field>
            <Field label="详细地址" full>
              {record.address}
            </Field>
          </Grid>
        </SectionCard>

        <SectionCard icon={<WalletOutlined />} title="结算信息">
          <Grid>
            <Field label="开户银行">{record.bankName}</Field>
            <Field label="银行账号" mono>
              {record.bankAccount}
            </Field>
          </Grid>
        </SectionCard>

        <SectionCard icon={<AppstoreOutlined />} title="主营产品">
          <ScopeLines scopes={record.productScopes} />
        </SectionCard>

        <SectionCard icon={<HistoryOutlined />} title="系统信息">
          <Grid>
            <Field label="备注" full>
              {record.remark}
            </Field>
            <Field label="创建人">{record.createBy}</Field>
            <Field label="创建时间" mono>
              {formatDateTime(record.createTime)}
            </Field>
            <Field label="最后修改人">{record.updateBy}</Field>
            <Field label="最后修改时间" mono>
              {formatDateTime(record.updateTime)}
            </Field>
          </Grid>
        </SectionCard>
      </>
    );
  }

  return (
    <div style={{ color: palette.ink }}>
      <a className="zhul-skip" href="#supplier-main">
        跳到主要内容
      </a>
      <PageTitle
        title="供应商详情"
        current="供应商详情"
        actions={
          <>
            <Button icon={<ArrowLeftOutlined />} onClick={back}>
              返回
            </Button>
            {canEdit && (
              <Button
                type="primary"
                icon={<EditOutlined />}
                onClick={() => history.push(`${LIST_PATH}/${id}/edit`)}
              >
                编辑
              </Button>
            )}
          </>
        }
      />
      {body}
    </div>
  );
};

export default SupplierDetailPage;
