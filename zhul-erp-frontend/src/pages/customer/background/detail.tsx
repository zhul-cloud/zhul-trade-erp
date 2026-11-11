import {
  DashboardOutlined,
  DeleteOutlined,
  DownloadOutlined,
  FileTextOutlined,
  LoadingOutlined,
  PaperClipOutlined,
} from '@ant-design/icons';
import XMarkdown from '@ant-design/x-markdown';
import { history, useAccess, useParams } from '@umijs/max';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import { App, Button, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { usePageTitle, useTabActive } from '@/components/PageTabs/hooks';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { LIST_PATH, PageTitle } from '../components';
import { customerApi, readBizError } from '../service';
import { ConclusionBox } from './components';
import Suggestions from './Suggestions';
import {
  type BackgroundReportDetail,
  backgroundApi,
  duration,
  SOURCE_AI,
  STATUS,
} from './service';

const Card: React.FC<{
  icon?: React.ReactNode;
  title?: React.ReactNode;
  children: React.ReactNode;
  style?: React.CSSProperties;
}> = ({ icon, title, children, style }) => {
  const { palette } = useAppTheme();
  return (
    <section
      style={{
        padding: 20,
        borderRadius: 16,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        minWidth: 0,
        ...style,
      }}
    >
      {title && (
        <h2
          style={{
            margin: 0,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            fontSize: 14,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          <span style={{ color: palette.link }}>{icon}</span>
          {title}
        </h2>
      )}
      {children}
    </section>
  );
};

/** 背调报告详情（原型 C8）：核心结论、用量、正文快照、字段建议与采纳 */
const BackgroundReportPage: React.FC = () => {
  const { id, reportId } = useParams<{ id: string; reportId: string }>();
  const customerId = Number(id);
  const access = useAccess() as Record<string, boolean>;
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const [report, setReport] = useState<BackgroundReportDetail | null>();
  usePageTitle(
    report ? `背调报告 ${report.reportNo}` : undefined,
    report?.reportNo,
  );
  const [customerName, setCustomerName] = useState('');
  const [error, setError] = useState<string>();
  const [exporting, setExporting] = useState(false);

  const load = useCallback(() => {
    setError(undefined);
    backgroundApi
      .detail(customerId, Number(reportId))
      .then(setReport)
      .catch((e) => {
        const err = readBizError(e);
        if (err.message.includes('不存在')) setReport(null);
        else setError(err.message);
      });
  }, [customerId, reportId]);

  useEffect(() => {
    load();
    customerApi
      .detail(customerId)
      .then((c) => setCustomerName(c.displayName))
      .catch(() => undefined);
  }, [load, customerId]);

  // 背调中每 5 秒刷新，完成后停
  const running = report?.status === STATUS.RUNNING;
  // 在后台标签里不刷新，切回来再继续
  const tabActive = useTabActive();
  useEffect(() => {
    if (!running || !tabActive) return undefined;
    const timer = window.setInterval(load, 5000);
    return () => window.clearInterval(timer);
  }, [running, tabActive, load]);

  const back = () => history.push(`${LIST_PATH}/${customerId}`);

  const exportPdf = async (r: BackgroundReportDetail) => {
    setExporting(true);
    try {
      await backgroundApi.exportPdf(
        customerId,
        r.id,
        `${customerName || '客户'}_${r.reportNo}.pdf`,
      );
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setExporting(false);
    }
  };

  const download = (r: BackgroundReportDetail) =>
    backgroundApi
      .fileUrl(customerId, r.id)
      .then((f) => window.open(f.url, '_blank', 'noopener'))
      .catch((e) => message.error(readBizError(e).message));

  const remove = (r: BackgroundReportDetail) =>
    modal.confirm({
      title: `删除报告 ${r.reportNo}？`,
      content:
        '删除后报告不再显示；已经采纳进档案的字段保留，来源显示「背调 编号（已删除）」。',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: () =>
        backgroundApi
          .remove(customerId, r.id)
          .then(() => {
            message.success('已删除');
            back();
          })
          .catch((e) => message.error(readBizError(e).message)),
    });

  let body: React.ReactNode;
  let actions: React.ReactNode = null;
  if (error) {
    body = <ErrorHint message={error} onRetry={load} />;
  } else if (report === undefined) {
    body = (
      <div style={{ background: palette.card, borderRadius: 16, padding: 24 }}>
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    );
  } else if (report === null) {
    body = (
      <EmptyHint
        title="报告不存在或已删除"
        description="这份背调报告可能已被删除，或不在你的数据范围内。"
        actionText="返回客户"
        onAction={back}
      />
    );
  } else {
    const r = report;
    actions = (
      <>
        {r.canDelete && (
          <Button danger icon={<DeleteOutlined />} onClick={() => remove(r)}>
            删除
          </Button>
        )}
        {r.uploadFileName && (
          <Button icon={<PaperClipOutlined />} onClick={() => download(r)}>
            下载原件
          </Button>
        )}
        {r.status === STATUS.DONE && (
          <Button
            icon={<DownloadOutlined />}
            loading={exporting}
            onClick={() => exportPdf(r)}
          >
            导出 PDF
          </Button>
        )}
      </>
    );
    if (r.status !== STATUS.DONE) {
      body = (
        <div
          style={{
            padding: 40,
            borderRadius: 16,
            textAlign: 'center',
            background: palette.card,
            border: `1px solid ${palette.hairline}`,
            color: running ? palette.sub : palette.red,
          }}
        >
          {running ? (
            <>
              <LoadingOutlined style={{ fontSize: 24, color: palette.link }} />
              <div style={{ marginTop: 12, fontSize: 15 }}>
                {r.source === SOURCE_AI
                  ? '正在联网背调，大约需要 3–5 分钟，完成后自动刷新'
                  : '正在从报告里提取结论和字段建议，约 1 分钟'}
              </div>
            </>
          ) : (
            <div style={{ fontSize: 15 }}>
              背调失败：{r.failReason || '原因未知'}。可以回到客户重新发起。
            </div>
          )}
        </div>
      );
    } else {
      const usage: [string, React.ReactNode][] = [
        ['模型', r.model || '—'],
        ['输入 token', r.inputTokens?.toLocaleString() ?? '—'],
        ['输出 token', r.outputTokens?.toLocaleString() ?? '—'],
        ['联网搜索', r.webSearches == null ? '—' : `${r.webSearches} 次`],
        ['耗时', duration(r.durationSeconds)],
      ];
      body = (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div
            style={{
              display: 'flex',
              gap: 20,
              flexWrap: 'wrap',
              alignItems: 'flex-start',
            }}
          >
            <div style={{ flex: '1 1 480px', minWidth: 0 }}>
              <ConclusionBox report={r} />
            </div>
            <Card
              icon={<DashboardOutlined />}
              title="本次用量"
              style={{ flex: '0 0 320px', maxWidth: '100%' }}
            >
              {usage.map(([k, v]) => (
                <div
                  key={k}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    fontSize: 13,
                  }}
                >
                  <span style={{ color: palette.mute }}>{k}</span>
                  <span
                    className="num"
                    style={{ color: palette.ink, fontWeight: 600 }}
                  >
                    {v}
                  </span>
                </div>
              ))}
              <div style={{ fontSize: 12, color: palette.sub }}>
                只记 token 和次数，不折算金额
              </div>
            </Card>
          </div>
          <div
            style={{
              display: 'flex',
              gap: 20,
              flexWrap: 'wrap',
              alignItems: 'flex-start',
            }}
          >
            <Card
              icon={<FileTextOutlined />}
              title={
                <>
                  报告正文
                  <span
                    style={{
                      fontSize: 12,
                      fontWeight: 400,
                      color: palette.mute,
                    }}
                  >
                    当时的快照，之后改档案不影响
                  </span>
                </>
              }
              style={{ flex: '1 1 440px' }}
            >
              <div
                className="bg-report-body"
                style={{
                  fontSize: 13,
                  lineHeight: 1.8,
                  color: palette.ink,
                  overflowX: 'auto',
                }}
              >
                {/* 报告来自 AI 或上传的文字：原始 HTML 一律当文字显示 */}
                <XMarkdown escapeRawHtml openLinksInNewTab>
                  {r.reportMarkdown ?? ''}
                </XMarkdown>
              </div>
              <style>{`
                .bg-report-body h1,.bg-report-body h2,.bg-report-body h3{font-size:14px;font-weight:700;margin:14px 0 6px;color:${palette.ink}}
                .bg-report-body p{margin:6px 0}
                .bg-report-body ul,.bg-report-body ol{margin:4px 0;padding-left:20px;list-style:disc}
                .bg-report-body ol{list-style:decimal}
                .bg-report-body table{border-collapse:collapse;width:100%;margin:8px 0}
                .bg-report-body th,.bg-report-body td{border:1px solid ${palette.hairline};padding:6px 8px;text-align:left;vertical-align:top}
                .bg-report-body th{background:${palette.inset}}
                .bg-report-body a{color:${palette.link}}
              `}</style>
            </Card>
            <Card style={{ flex: '1 1 440px' }}>
              <Suggestions
                customerId={customerId}
                reportId={r.id}
                suggestions={r.suggestions}
                canEdit={!!access['partner:customer:edit']}
                onChanged={load}
              />
              <div
                style={{ fontSize: 12, color: palette.mute, lineHeight: 1.6 }}
              >
                当前为空的建议默认勾选，已有值的默认不勾（勾上会覆盖原值）；
                联系人按邮箱或姓名找已有联系人，找到就更新，否则新建。采纳后字段来源记为这份报告。
              </div>
            </Card>
          </div>
        </div>
      );
    }
  }

  return (
    <div style={{ color: palette.ink }}>
      <PageTitle
        title={report?.reportNo ?? '背调报告'}
        current={customerName || '背调报告'}
        description={
          report
            ? [
                report.source === SOURCE_AI ? 'AI 背调' : '上传',
                report.requestedByName,
                formatDateTime(report.createTime).slice(0, 16),
                report.durationSeconds != null &&
                  `用时 ${duration(report.durationSeconds)}`,
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

export default BackgroundReportPage;
