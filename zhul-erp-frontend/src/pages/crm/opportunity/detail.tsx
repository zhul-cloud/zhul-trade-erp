import {
  ArrowRightOutlined,
  CheckCircleFilled,
  CloseCircleOutlined,
  DownloadOutlined,
  DownOutlined,
  EditOutlined,
  FileAddOutlined,
  RollbackOutlined,
  StopOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams } from '@umijs/max';
import { App, Button, Dropdown, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { StatusPill } from '@/pages/inquiry/shared/components';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { formatDateTime } from '@/utils/format';
import {
  Card,
  FileChip,
  fileMeta,
  iconButton,
  PageTitle,
  Pill,
  PreviewButton,
  StagePill,
} from './components';
import { CATEGORY_ACTIVE, LIST_PATH } from './constants';
import { CloseModal, CreateInquiryModal, EditModal } from './dialogs';
import {
  type OpportunityDetail,
  type OpportunityStage,
  opportunityApi,
  readBizError,
} from './service';

const EMPTY = '—';

const OpportunityDetailPage: React.FC = () => {
  const { labelOf: sourceLabel } = useDictOptions(
    DICT_SOURCE_CHANNEL,
    '未设置',
  );
  const { id } = useParams<{ id: string }>();
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['crm:opportunity:edit'];

  const [detail, setDetail] = useState<OpportunityDetail | null>();
  const [stages, setStages] = useState<OpportunityStage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [closeKind, setCloseKind] = useState<'INVALID' | 'LOST' | null>(null);
  const [editing, setEditing] = useState(false);
  const [creatingInquiry, setCreatingInquiry] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setDetail(await opportunityApi.detail(Number(id)));
    } catch (e) {
      const err = readBizError(e);
      if (err.message.includes('不存在')) setDetail(null);
      else setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
    opportunityApi
      .stages()
      .then(setStages)
      .catch(() => undefined);
  }, [load]);

  const active = stages.filter((s) => s.category === CATEGORY_ACTIVE);
  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    try {
      await fn();
      message.success(ok);
      await load();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  if (loading && detail === undefined) {
    return (
      <>
        <PageTitle title="商机详情" current="商机详情" />
        <Card>
          <Skeleton active paragraph={{ rows: 8 }} />
        </Card>
      </>
    );
  }
  if (error) {
    return (
      <>
        <PageTitle title="商机详情" current="商机详情" />
        <ErrorHint message={error} onRetry={load} />
      </>
    );
  }
  if (!detail) {
    return (
      <>
        <PageTitle title="商机详情" current="商机详情" />
        <EmptyHint
          title="商机不存在"
          description="它可能已被删除，或不在你的数据权限范围内。"
          actionText="返回列表"
          onAction={() => history.push(LIST_PATH)}
        />
      </>
    );
  }

  const d = detail;
  const isActive = d.stageCategory === CATEGORY_ACTIVE;
  const idx = active.findIndex((s) => s.code === d.stageCode);
  const next = idx >= 0 ? active[idx + 1] : undefined;
  const last = active[active.length - 1];
  const inValidStage = idx >= 0 && active[idx].countsAsValid;
  // 结束状态时，阶段条停在结束前的阶段
  const railIdx = isActive
    ? idx
    : active.findIndex((s) => s.code === d.reopenStageCode);

  const actions = canEdit && (
    <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
      {isActive ? (
        <>
          {inValidStage ? (
            <Button
              danger
              icon={<CloseCircleOutlined />}
              disabled={busy}
              onClick={() => setCloseKind('LOST')}
            >
              标记输单
            </Button>
          ) : (
            <Button
              icon={<StopOutlined />}
              disabled={busy}
              onClick={() => setCloseKind('INVALID')}
            >
              标记无效
            </Button>
          )}
          <Dropdown
            trigger={['click']}
            menu={{
              items: active
                .filter((s) => s.code !== d.stageCode)
                .map((s) => ({ key: s.code, label: `${s.code} ${s.name}` })),
              onClick: ({ key }) =>
                run(() => opportunityApi.changeStage(d.id, key), '阶段已更新'),
            }}
          >
            <Button disabled={busy}>
              调整阶段 <DownOutlined />
            </Button>
          </Dropdown>
          {next ? (
            <Button
              type="primary"
              icon={<ArrowRightOutlined />}
              loading={busy}
              onClick={() =>
                run(
                  () => opportunityApi.changeStage(d.id, next.code),
                  `已推进到 ${next.code} ${next.name}`,
                )
              }
            >
              推进到 {next.code} {next.name}
            </Button>
          ) : (
            <Button
              type="primary"
              icon={<TrophyOutlined />}
              loading={busy}
              onClick={() =>
                modal.confirm({
                  title: '确认赢单？',
                  content: '赢单后商机结束，计入赢单统计。之后仍可重新打开。',
                  okText: '确认赢单',
                  cancelText: '取消',
                  onOk: () =>
                    run(
                      () => opportunityApi.close(d.id, 'WON'),
                      '恭喜，已赢单',
                    ),
                })
              }
            >
              标记赢单
            </Button>
          )}
        </>
      ) : (
        <Button
          icon={<RollbackOutlined />}
          loading={busy}
          onClick={() => run(() => opportunityApi.reopen(d.id), '已重新打开')}
        >
          重新打开
        </Button>
      )}
    </div>
  );

  const kv = (label: string, value?: React.ReactNode, mute?: boolean) => (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 4 }}>
        {label}
      </div>
      <div
        style={{
          color: mute || !value ? palette.mute : palette.ink,
          fontWeight: 500,
          wordBreak: 'break-all',
        }}
      >
        {value || EMPTY}
      </div>
    </div>
  );

  return (
    <div style={{ color: palette.ink }}>
      <PageTitle
        title={d.customerName}
        current={d.opportunityCode}
        description={
          <div
            style={{
              display: 'flex',
              gap: 10,
              alignItems: 'center',
              flexWrap: 'wrap',
              marginTop: 4,
            }}
          >
            <StagePill
              code={d.stageCode}
              name={d.stageName}
              category={d.stageCategory}
              index={idx}
            />
            <Pill tone="gray">{sourceLabel(d.sourceChannel)}</Pill>
            {d.customerNameMissing && <Pill tone="orange">未填客户名称</Pill>}
            <span style={{ color: palette.mute }}>
              首次接触 {d.firstContactDate} · 负责人 {d.ownerName || '未分配'} ·{' '}
              {d.country}
            </span>
          </div>
        }
        actions={
          <>
            {canEdit && (
              <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>
                编辑
              </Button>
            )}
            {isActive && (
              <Button
                icon={<FileAddOutlined />}
                onClick={() => setCreatingInquiry(true)}
              >
                创建客户询盘
              </Button>
            )}
          </>
        }
      />

      <Card title="阶段" extra={actions} style={{ marginBottom: 20 }}>
        {!isActive && (
          <div
            style={{
              marginBottom: 16,
              padding: '10px 14px',
              borderRadius: 10,
              background: palette.inset,
              color: palette.sub,
            }}
          >
            已结束：{d.stageName}
            {d.closeReasonLabel ? `（${d.closeReasonLabel}）` : ''}
            {d.closeNote ? `，${d.closeNote}` : ''}
          </div>
        )}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: `repeat(${active.length || 7}, 1fr)`,
            gap: 8,
          }}
        >
          {active.map((s, i) => {
            const done = i < railIdx;
            const cur = i === railIdx;
            return (
              <div
                key={s.code}
                aria-current={cur ? 'step' : undefined}
                style={{
                  padding: '12px 14px',
                  borderRadius: 12,
                  background: cur ? palette.accentSoft : palette.inset,
                  border: `1px solid ${cur ? palette.accentLine : palette.hairline}`,
                  opacity: !isActive && !cur && !done ? 0.6 : 1,
                }}
              >
                <div
                  style={{
                    fontSize: 12,
                    fontWeight: 700,
                    color: cur
                      ? palette.link
                      : done
                        ? palette.green
                        : palette.mute,
                  }}
                >
                  {done && <CheckCircleFilled style={{ marginRight: 6 }} />}
                  {s.code}
                </div>
                <div
                  style={{
                    fontWeight: cur ? 700 : 500,
                    color: cur
                      ? palette.ink
                      : done
                        ? palette.sub
                        : palette.mute,
                  }}
                >
                  {s.name}
                </div>
              </div>
            );
          })}
        </div>
        <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
          可以前进或退回到任一阶段；S1、S2 可标记无效，S3
          起没做成请标记输单，推进到{' '}
          {last ? `${last.code} ${last.name}` : '最后阶段'} 后才能标记赢单。
        </div>
      </Card>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 1fr) 380px',
          gap: 20,
          alignItems: 'start',
        }}
      >
        <div style={{ display: 'grid', gap: 20 }}>
          <Card
            title="客户与联系方式"
            extra={
              <a onClick={() => history.push(`/customer/list/${d.customerId}`)}>
                去客户档案补全
              </a>
            }
          >
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: 20,
              }}
            >
              {kv('联系人', d.contactName)}
              {kv(
                '客户名称',
                d.customerNameMissing
                  ? '未填写（出报价单前需补全）'
                  : d.customerName,
                d.customerNameMissing,
              )}
              {kv('国家/地区', d.country)}
              {kv('WhatsApp', d.whatsapp)}
              {kv('邮箱', d.email)}
              {kv('联系电话', d.phone)}
              {kv('官网', d.website)}
              {kv('客户编码', d.customerCode)}
            </div>
          </Card>

          <Card title="需求摘要">
            <div
              style={{
                color: d.demandSummary ? palette.sub : palette.mute,
                lineHeight: 1.7,
                whiteSpace: 'pre-wrap',
              }}
            >
              {d.demandSummary || '还没有填写需求摘要'}
            </div>
            {d.attachments.length > 0 && (
              <div
                style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 10,
                  marginTop: 16,
                }}
              >
                {d.attachments.map((a) => (
                  <FileChip
                    key={a.id}
                    fileName={a.fileName}
                    contentType={a.contentType}
                    meta={fileMeta(
                      a.contentType,
                      a.fileSize,
                      a.createBy,
                      a.createTime,
                    )}
                    actions={
                      <>
                        <PreviewButton
                          id={d.id}
                          attachmentId={a.id}
                          name={a.fileName}
                        />
                        <button
                          type="button"
                          aria-label={`下载 ${a.fileName}`}
                          style={iconButton}
                          onClick={async () => {
                            try {
                              const url =
                                await opportunityApi.attachmentBlobUrl(
                                  d.id,
                                  a.id,
                                );
                              const link = document.createElement('a');
                              link.href = url;
                              link.download = a.fileName;
                              document.body.appendChild(link);
                              link.click();
                              document.body.removeChild(link);
                              window.URL.revokeObjectURL(url);
                            } catch (e) {
                              message.error(readBizError(e).message);
                            }
                          }}
                        >
                          <DownloadOutlined />
                        </button>
                      </>
                    }
                  />
                ))}
              </div>
            )}
          </Card>

          <Card
            title="关联询盘"
            extra={
              isActive && (
                <a onClick={() => setCreatingInquiry(true)}>创建客户询盘</a>
              )
            }
          >
            {d.inquiries.length === 0 ? (
              <div style={{ color: palette.mute }}>
                {isActive
                  ? '还没有询盘。客户需求明确后，可以从这里创建客户询盘交给 AI 解析。'
                  : '还没有询盘。商机已结束，老客户的新需求请在客户询盘列表新建。'}
              </div>
            ) : (
              <div style={{ display: 'grid', gap: 8 }}>
                {d.inquiries.map((q) => {
                  return (
                    <div
                      key={q.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 16,
                        padding: '12px 16px',
                        borderRadius: 12,
                        background: palette.inset,
                      }}
                    >
                      <a
                        onClick={() =>
                          history.push(`/inquiry/customer-inquiries/${q.id}`)
                        }
                      >
                        {q.inquiryCode}
                      </a>
                      <StatusPill status={q.status} />
                      <span style={{ color: palette.mute }}>
                        {q.totalOrderCount} 张询盘单 · {q.inquiryDate}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </Card>
        </div>

        <Card title="阶段记录">
          <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {d.stageLogs.map((l, i) => {
              const title =
                l.action === 1
                  ? `登记商机（${l.toStage} ${l.toStageName}）`
                  : l.action === 4
                    ? `重新打开：${l.fromStageName} → ${l.toStage} ${l.toStageName}`
                    : `${l.fromStage} ${l.fromStageName} → ${l.action === 3 ? l.toStageName : `${l.toStage} ${l.toStageName}`}`;
              return (
                <li key={l.id} style={{ display: 'flex', gap: 12 }}>
                  <div
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      width: 12,
                    }}
                  >
                    <span
                      style={{
                        width: 10,
                        height: 10,
                        borderRadius: 5,
                        marginTop: 5,
                        background: i === 0 ? palette.link : palette.control,
                      }}
                    />
                    {i < d.stageLogs.length - 1 && (
                      <span
                        style={{
                          flex: 1,
                          width: 2,
                          background: palette.hairline,
                          marginTop: 4,
                        }}
                      />
                    )}
                  </div>
                  <div style={{ paddingBottom: 18, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, color: palette.ink }}>
                      {title}
                    </div>
                    <div
                      style={{
                        fontSize: 12,
                        color: palette.mute,
                        marginTop: 2,
                      }}
                    >
                      {l.operator} · {formatDateTime(l.createTime)}
                    </div>
                    {(l.reasonLabel || l.note) && (
                      <div
                        style={{
                          fontSize: 12,
                          color: palette.sub,
                          marginTop: 4,
                        }}
                      >
                        {[l.reasonLabel, l.note].filter(Boolean).join('：')}
                      </div>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>
        </Card>
      </div>

      <CloseModal
        detail={d}
        kind={closeKind}
        onClose={() => setCloseKind(null)}
        onDone={() => {
          setCloseKind(null);
          load();
        }}
      />
      <EditModal
        detail={editing ? d : null}
        onClose={() => setEditing(false)}
        onDone={() => {
          setEditing(false);
          load();
        }}
      />
      <CreateInquiryModal
        detail={d}
        advanceTo={
          isActive && !inValidStage
            ? active.find((s) => s.countsAsValid)
            : undefined
        }
        open={creatingInquiry}
        onClose={() => setCreatingInquiry(false)}
      />
    </div>
  );
};

export default OpportunityDetailPage;
