import {
  CloseOutlined,
  CloudUploadOutlined,
  FilePdfOutlined,
  FireOutlined,
  InfoCircleOutlined,
  LoadingOutlined,
  ThunderboltOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { App, Button, Input, Modal, Segmented, Upload } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useTabActive } from '@/components/PageTabs/hooks';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { readBizError } from '../service';
import {
  type BackgroundReport,
  backgroundApi,
  ERR_RECENT,
  kilo,
  PRIORITY_NAMES,
  type RecentDetail,
  reportPath,
  SOURCE_AI,
  STATUS,
} from './service';

/** 跟进优先级标签：立即重点跟进红、正常跟进橙，其余灰 */
export const PriorityPill: React.FC<{ value?: number }> = ({ value }) => {
  const { palette } = useAppTheme();
  if (!value) return null;
  const tone =
    value === 1
      ? { fg: palette.red, bg: palette.redSoft }
      : value === 2
        ? { fg: palette.orange, bg: palette.orangeSoft }
        : { fg: palette.sub, bg: palette.inset };
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 22,
        padding: '0 8px',
        borderRadius: 6,
        fontSize: 12,
        fontWeight: 600,
        color: tone.fg,
        background: tone.bg,
        whiteSpace: 'nowrap',
      }}
    >
      {PRIORITY_NAMES[value]}
    </span>
  );
};

/** 来源标签：AI 背调 / 上传 */
export const SourcePill: React.FC<{ source: number }> = ({ source }) => {
  const { palette } = useAppTheme();
  const ai = source === SOURCE_AI;
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 22,
        padding: '0 8px',
        borderRadius: 6,
        fontSize: 12,
        fontWeight: 600,
        color: ai ? palette.violet : palette.sub,
        background: ai ? palette.violetSoft : palette.inset,
        whiteSpace: 'nowrap',
      }}
    >
      {ai ? 'AI 背调' : '上传'}
    </span>
  );
};

const NOTE_ROWS: [keyof BackgroundReport, string][] = [
  ['mainConflict', '主要矛盾'],
  ['firstAction', '第一步行动'],
  ['identityNote', '客户身份'],
  ['supplyNote', '产品货源'],
  ['keyFinding', '关键发现'],
  ['obstacle', '最大障碍'],
];

/** 核心结论：优先级 + 综合评分 + 结论各项；compact 只显示主要矛盾和第一步行动 */
export const ConclusionBox: React.FC<{
  report: BackgroundReport;
  compact?: boolean;
  extra?: React.ReactNode;
}> = ({ report: r, compact, extra }) => {
  const { palette } = useAppTheme();
  const hot = r.priority === 1;
  const fg = hot
    ? palette.red
    : r.priority === 2
      ? palette.orange
      : palette.sub;
  const rows = (compact ? NOTE_ROWS.slice(0, 2) : NOTE_ROWS).filter(
    ([k]) => r[k],
  );
  return (
    <div
      style={{
        padding: compact ? 16 : 20,
        borderRadius: 14,
        background: hot ? palette.redSoft : palette.inset,
        border: `1px solid ${hot ? `${palette.red}55` : palette.hairline}`,
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '4px 10px',
        }}
      >
        <FireOutlined style={{ color: fg }} />
        <span
          style={{
            fontSize: compact ? 16 : 17,
            fontWeight: 700,
            color: fg,
            whiteSpace: 'nowrap',
          }}
        >
          {PRIORITY_NAMES[r.priority] ?? '未出结论'}
        </span>
        {r.score > 0 && (
          <span
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: fg,
              whiteSpace: 'nowrap',
            }}
          >
            综合 {r.score} / 5
          </span>
        )}
        <span style={{ marginLeft: 'auto', fontSize: 12, color: palette.mute }}>
          {extra}
        </span>
      </div>
      {rows.map(([k, label]) => (
        <div key={k}>
          <div style={{ fontSize: 11, color: palette.mute, marginBottom: 2 }}>
            {label}
          </div>
          <div
            style={{
              fontSize: 13,
              fontWeight: 600,
              color: palette.ink,
              lineHeight: 1.6,
            }}
          >
            {r[k] as string}
          </div>
        </div>
      ))}
    </div>
  );
};

/** 客户的历次背调；有「背调中」的每 5 秒刷新一次，完成后停并提示「背调完成」 */
export const useBackgroundReports = (customerId?: number) => {
  const { message } = App.useApp();
  const [reports, setReports] = useState<BackgroundReport[]>();
  const runningIds = useRef<number[]>([]);
  const load = useCallback(() => {
    if (!customerId) return;
    backgroundApi
      .list(customerId)
      .then((rows) => {
        for (const r of rows) {
          if (!runningIds.current.includes(r.id)) continue;
          if (r.status === STATUS.DONE) {
            message.success(`背调完成：${r.reportNo}，结论在右侧「背调报告」`);
          } else if (r.status === STATUS.FAILED) {
            message.warning(`背调失败：${r.failReason || r.reportNo}`);
          }
        }
        runningIds.current = rows
          .filter((r) => r.status === STATUS.RUNNING)
          .map((r) => r.id);
        setReports(rows);
      })
      .catch(() => setReports([]));
  }, [customerId, message]);
  useEffect(load, [load]);
  const running = reports?.some((r) => r.status === STATUS.RUNNING);
  // 在后台标签里不刷新，切回来再继续
  const tabActive = useTabActive();
  useEffect(() => {
    if (!running || !tabActive) return undefined;
    const timer = window.setInterval(load, 5000);
    return () => window.clearInterval(timer);
  }, [running, tabActive, load]);
  return { reports, reload: load, running: !!running };
};

/** 客户详情右侧「背调报告」卡（原型 C1）：最近一份已完成报告的结论 + 历次报告 */
export const BackgroundCard: React.FC<{
  customerId: number;
  reports: BackgroundReport[];
}> = ({ customerId, reports }) => {
  const { palette } = useAppTheme();
  const [all, setAll] = useState(false);
  const latest = reports.find((r) => r.status === STATUS.DONE);
  const shown = all ? reports : reports.slice(0, 3);
  const open = (r: BackgroundReport) =>
    history.push(reportPath(customerId, r.id));
  return (
    <section
      aria-label="背调报告"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        padding: 20,
        borderRadius: 16,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <FilePdfOutlined style={{ color: palette.link }} />
        <h2
          style={{
            margin: 0,
            fontSize: 14,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          背调报告
        </h2>
        {reports.length > 3 && (
          <button
            type="button"
            className="zhul-link"
            style={{ marginLeft: 'auto', fontSize: 13 }}
            onClick={() => setAll(!all)}
          >
            {all ? '收起' : `全部 ${reports.length} 份`}
          </button>
        )}
      </div>
      {reports.length === 0 ? (
        <div style={{ fontSize: 13, color: palette.mute, lineHeight: 1.6 }}>
          还没有背调。点右上角「AI
          背调」联网查一次，或「上传报告」导入桌面端做好的报告。
        </div>
      ) : (
        <>
          {latest && (
            <button
              type="button"
              className="zhul-link"
              onClick={() => open(latest)}
              style={{ color: 'inherit' }}
            >
              <ConclusionBox
                report={latest}
                compact
                extra={`${latest.reportNo} · ${formatDateTime(latest.createTime).slice(5, 10)}`}
              />
            </button>
          )}
          {shown.map((r) => (
            <ReportRow key={r.id} report={r} onOpen={() => open(r)} />
          ))}
        </>
      )}
    </section>
  );
};

const ReportRow: React.FC<{ report: BackgroundReport; onOpen: () => void }> = ({
  report: r,
  onOpen,
}) => {
  const { palette } = useAppTheme();
  const clickable = r.status !== STATUS.RUNNING;
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 8,
        padding: '10px 12px',
        borderRadius: 12,
        background: palette.inset,
        fontSize: 12,
      }}
    >
      {clickable ? (
        <button
          type="button"
          className="zhul-link num"
          onClick={onOpen}
          style={{ fontSize: 13 }}
        >
          {r.reportNo}
        </button>
      ) : (
        <span className="num" style={{ fontSize: 13, color: palette.sub }}>
          {r.reportNo}
        </span>
      )}
      <SourcePill source={r.source} />
      {r.status === STATUS.RUNNING && (
        <span style={{ color: palette.link, fontWeight: 600 }}>
          <LoadingOutlined /> 背调中…
        </span>
      )}
      {r.status === STATUS.FAILED && (
        <span
          style={{ color: palette.red, fontWeight: 600 }}
          title={r.failReason}
        >
          失败
        </span>
      )}
      {r.status === STATUS.DONE && (
        <>
          <PriorityPill value={r.priority} />
          {r.score > 0 && (
            <span style={{ color: palette.ink, fontWeight: 600 }}>
              {r.score} 分
            </span>
          )}
        </>
      )}
      <span style={{ marginLeft: 'auto', color: palette.mute }}>
        {r.requestedByName} · {formatDateTime(r.createTime).slice(5, 16)}
      </span>
      {r.status === STATUS.FAILED && r.failReason && (
        <div style={{ flexBasis: '100%', color: palette.mute }}>
          {r.failReason}
        </div>
      )}
    </div>
  );
};

/**
 * 页头的「上传报告」「AI 背调」两个按钮及其弹窗（原型 C9、C10）。
 * 24 小时内背调过只提醒不拦截；同一客户同时只能有一份「背调中」。
 */
export const BackgroundActions: React.FC<{
  customerId: number;
  customerName: string;
  running: boolean;
  onStarted: () => void;
}> = ({ customerId, customerName, running, onStarted }) => {
  const { message } = App.useApp();
  const [starting, setStarting] = useState(false);
  const [recent, setRecent] = useState<RecentDetail>();
  const [uploadOpen, setUploadOpen] = useState(false);

  const start = async (confirmed: boolean) => {
    setStarting(true);
    try {
      await backgroundApi.research(customerId, confirmed);
      setRecent(undefined);
      message.success(
        '已开始背调，大约需要 3–5 分钟，完成后在右侧「背调报告」查看',
      );
      onStarted();
    } catch (e) {
      const err = readBizError(e);
      if (err.errorCode === ERR_RECENT) {
        setRecent(err.detail as unknown as RecentDetail);
      } else {
        message.error(err.message);
      }
    } finally {
      setStarting(false);
    }
  };

  return (
    <>
      <Button icon={<UploadOutlined />} onClick={() => setUploadOpen(true)}>
        上传报告
      </Button>
      <Button
        type="primary"
        icon={running ? <LoadingOutlined /> : <ThunderboltOutlined />}
        loading={starting}
        disabled={running}
        onClick={() => start(false)}
      >
        {running ? '背调中' : 'AI 背调'}
      </Button>
      <RecentModal
        customerId={customerId}
        detail={recent}
        starting={starting}
        onCancel={() => setRecent(undefined)}
        onConfirm={() => start(true)}
      />
      <UploadModal
        open={uploadOpen}
        customerId={customerId}
        customerName={customerName}
        onClose={() => setUploadOpen(false)}
        onDone={() => {
          setUploadOpen(false);
          onStarted();
        }}
      />
    </>
  );
};

/** 再背调一次？（原型 C10） */
const RecentModal: React.FC<{
  customerId: number;
  detail?: RecentDetail;
  starting: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}> = ({ customerId, detail: d, starting, onCancel, onConfirm }) => {
  const { palette } = useAppTheme();
  const ago = d && (d.hoursAgo < 1 ? '不到 1 小时前' : `${d.hoursAgo} 小时前`);
  const conclusion = d
    ? [
        d.reportNo,
        d.priority ? PRIORITY_NAMES[d.priority] : '',
        d.score ? `${d.score} 分` : '',
      ]
        .filter(Boolean)
        .join(' · ')
    : '';
  const usage =
    d &&
    [
      d.inputTokens != null &&
        `${kilo((d.inputTokens ?? 0) + (d.outputTokens ?? 0))} token`,
      d.webSearches != null && `联网 ${d.webSearches} 次`,
    ].filter(Boolean);
  return (
    <Modal
      open={!!d}
      title="再背调一次？"
      onCancel={onCancel}
      onOk={onConfirm}
      okText="仍要背调"
      confirmLoading={starting}
      width={520}
    >
      {d && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: palette.ink }}>
            这个客户 {ago}由 {d.requestedByName} 背调过（{conclusion}）。
          </div>
          <div style={{ fontSize: 13, color: palette.sub }}>
            再背调一次会重新联网搜索
            {usage && usage.length > 0
              ? `，上次大约用了 ${usage.join('、')}`
              : ''}
            。
          </div>
          <button
            type="button"
            className="zhul-link"
            style={{ fontSize: 13 }}
            onClick={() => history.push(reportPath(customerId, d.reportId))}
          >
            先看上次的报告
          </button>
        </div>
      )}
    </Modal>
  );
};

const MAX_PDF = 10 * 1024 * 1024;
const MAX_TEXT = 60000;

/** 上传背调报告（原型 C9）：PDF 或粘贴文字，AI 只做提取不联网 */
const UploadModal: React.FC<{
  open: boolean;
  customerId: number;
  customerName: string;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, customerId, customerName, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [mode, setMode] = useState<'pdf' | 'text'>('pdf');
  const [file, setFile] = useState<File>();
  const [text, setText] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();

  useEffect(() => {
    if (!open) return;
    setMode('pdf');
    setFile(undefined);
    setText('');
    setError(undefined);
  }, [open]);

  const submit = async () => {
    if (mode === 'pdf' ? !file : !text.trim()) {
      setError(mode === 'pdf' ? '请选择一个 PDF' : '请粘贴报告文字');
      return;
    }
    setSaving(true);
    setError(undefined);
    try {
      const r = await backgroundApi.upload(
        customerId,
        mode === 'pdf' ? file : undefined,
        mode === 'text' ? text : undefined,
      );
      message.success(r.notice ?? '已上传，正在提取结论和字段建议，约 1 分钟');
      onDone();
    } catch (e) {
      const msg = readBizError(e).message;
      setError(msg);
      // 扫描件没有文字时，直接切到粘贴
      if (msg.includes('没有可读文字')) setMode('text');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={
        <div>
          上传背调报告
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            {customerName}
          </div>
        </div>
      }
      onCancel={onClose}
      onOk={submit}
      okText="上传并提取"
      confirmLoading={saving}
      width={640}
      destroyOnHidden
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Segmented
          value={mode}
          onChange={(v) => {
            setMode(v as 'pdf' | 'text');
            setError(undefined);
          }}
          options={[
            { value: 'pdf', label: '上传 PDF' },
            { value: 'text', label: '粘贴报告文字' },
          ]}
          style={{ alignSelf: 'flex-start' }}
        />
        {mode === 'pdf' ? (
          <>
            <Upload.Dragger
              accept=".pdf,application/pdf"
              showUploadList={false}
              disabled={saving}
              beforeUpload={(f) => {
                if (!f.name.toLowerCase().endsWith('.pdf')) {
                  setError('只支持 PDF 文件');
                } else if (f.size > MAX_PDF) {
                  setError('PDF 不能超过 10MB');
                } else {
                  setFile(f);
                  setError(undefined);
                }
                return Upload.LIST_IGNORE;
              }}
            >
              <p className="ant-upload-drag-icon">
                <CloudUploadOutlined />
              </p>
              <p className="ant-upload-text">把背调 PDF 拖到这里，或点击选择</p>
              <p className="ant-upload-hint">
                只收 PDF，不超过 10MB；扫描件没有文字，请改用粘贴
              </p>
            </Upload.Dragger>
            {file && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  padding: '10px 14px',
                  borderRadius: 12,
                  background: palette.inset,
                  fontSize: 13,
                }}
              >
                <FilePdfOutlined style={{ color: palette.red }} />
                <span
                  style={{
                    fontWeight: 600,
                    color: palette.ink,
                    wordBreak: 'break-all',
                  }}
                >
                  {file.name}
                </span>
                <span style={{ color: palette.mute, whiteSpace: 'nowrap' }}>
                  {Math.max(1, Math.round(file.size / 1024))} KB
                </span>
                <Button
                  type="text"
                  size="small"
                  aria-label="移除文件"
                  icon={<CloseOutlined />}
                  onClick={() => setFile(undefined)}
                  style={{ marginLeft: 'auto' }}
                />
              </div>
            )}
          </>
        ) : (
          <Input.TextArea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="把背调报告的文字粘贴到这里"
            autoSize={{ minRows: 8, maxRows: 16 }}
            showCount
            maxLength={MAX_TEXT}
          />
        )}
        {error && (
          <div role="alert" style={{ fontSize: 13, color: palette.red }}>
            {error}
          </div>
        )}
        <div
          style={{
            display: 'flex',
            gap: 10,
            padding: '12px 16px',
            borderRadius: 12,
            background: palette.accentSoft,
          }}
        >
          <InfoCircleOutlined style={{ color: palette.link, marginTop: 3 }} />
          <div style={{ fontSize: 13, lineHeight: 1.6 }}>
            <div style={{ fontWeight: 600, color: palette.link }}>
              AI 只从报告里提取核心结论和字段建议，不联网
            </div>
            <div style={{ color: palette.sub }}>
              比一键 AI 背调便宜得多；原 PDF 会保存在报告上，可以下载。
            </div>
          </div>
        </div>
      </div>
    </Modal>
  );
};
