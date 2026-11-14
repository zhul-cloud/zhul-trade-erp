import { useAccess, useParams } from '@umijs/max';
import { App, Button, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import {
  DocLogCard,
  InfoGrid,
  RelatedDocsCard,
} from '@/components/DocFields/DocPage';
import { DocFilesCard } from '@/components/DocFiles';
import { usePageTitle } from '@/components/PageTabs/hooks';
import {
  MigratedPill,
  PageTitle,
  Pill,
  useWide,
} from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/sales/service';
import { ReasonModal, relatedRows } from '@/pages/warehouse/components';
import { UnitPill, useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import { IncomeModal } from './index';
import { incomeApi, type OtherIncome } from './service';

/** 其他收入详情（change finance-expense-docs）：日期与金额生效后不能改；系统生成的汇兑损益只读 */
const OtherIncomeDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [bu] = useBusinessUnits();
  const [x, setX] = useState<OtherIncome>();
  const [error, setError] = useState<string>();
  const [editing, setEditing] = useState(false);
  const [voiding, setVoiding] = useState(false);
  usePageTitle(x ? `其他收入 ${x.incomeNo}` : undefined, x?.incomeNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setX(await incomeApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!x) return <Skeleton active paragraph={{ rows: 10 }} />;

  const valid = x.status === 1;
  const manual = !x.sourceType;
  const canEdit = !!x.editable && manual && !!access['finance:income:edit'];

  return (
    <div>
      <PageTitle
        crumbs={[
          <MenuLink key="list" to="/finance/other-incomes" />,
          x.incomeNo,
        ]}
        title={
          <>
            {x.incomeNo}
            <Pill tone={valid ? 'green' : 'mute'}>
              {valid ? '有效' : '已作废'}
            </Pill>
            {!manual && <Pill tone="mute">系统生成</Pill>}
            {x.migrated && <MigratedPill migrated />}
          </>
        }
        description={
          <>
            {x.categoryName ?? x.category} · {x.createBy ?? '—'} ·{' '}
            {formatDateTime(x.createTime).slice(0, 16)} 创建
          </>
        }
        actions={
          canEdit && (
            <>
              <Button onClick={() => setEditing(true)}>修改</Button>
              <Button danger onClick={() => setVoiding(true)}>
                作废
              </Button>
            </>
          )
        }
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide
            ? 'minmax(0, 2.6fr) minmax(340px, 1fr)'
            : '1fr',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <div style={{ minWidth: 0 }}>
          <InfoGrid
            items={[
              { label: '日期', value: x.incomeDate },
              { label: '类别', value: x.categoryName ?? x.category },
              {
                label: '所属事业部',
                value: <UnitPill info={bu} id={x.businessUnitId} />,
                hidden: !bu.enabled,
              },
              { label: '金额', value: <b>{formatAmount(x.amount, 'CNY')}</b> },
              {
                label: '来源',
                value: '余额提现自动生成，作废来源退款单时一起作废',
                hidden: manual,
              },
              { label: '作废原因', value: x.voidReason, hidden: valid },
            ]}
          />
          <DocFilesCard
            ownerType="OTHER_INCOME"
            files={x.files ?? []}
            remark={x.remark}
            editable={!!x.editable && manual}
            onAttach={(ids) => incomeApi.attach(x.id, ids)}
            onRemove={(fid) => incomeApi.removeAttachment(x.id, fid)}
            onRemark={(text) => incomeApi.remark(x.id, text)}
            onChanged={load}
          />
        </div>
        <div style={{ minWidth: 0 }}>
          <RelatedDocsCard docs={relatedRows(x.related)} />
          <DocLogCard logs={x.logs} />
        </div>
      </div>
      <IncomeModal
        open={editing}
        editing={x}
        onClose={() => setEditing(false)}
        onDone={load}
      />
      <ReasonModal
        open={voiding}
        title={`作废 ${x.incomeNo}`}
        description="作废后不再计入经营统计。金额录错请作废后重新登记。"
        okText="作废"
        danger
        onCancel={() => setVoiding(false)}
        onOk={async (why) => {
          try {
            await incomeApi.voidIncome(x.id, why);
            message.success('已作废');
            setVoiding(false);
            load();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default OtherIncomeDetail;
