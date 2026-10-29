import {
  ApiOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
  CloseCircleFilled,
  EnterOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  KeyOutlined,
  LockOutlined,
  ReloadOutlined,
  RobotOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import {
  App,
  AutoComplete,
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Result,
  Row,
  Segmented,
  Skeleton,
  Spin,
  Typography,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import type { Palette } from '@/theme/palette';
import { formatDateTime } from '@/utils/format';
import {
  type AiFormValues,
  DEFAULT_EFFORT,
  defaultModel,
  EFFORT_OPTIONS,
  isDirty,
  type ModelField,
  modelOptions,
  PROVIDERS,
  profileFormValues,
  type SubState,
  subscriptionState,
  testSlot,
  toFormValues,
  toPayload,
} from './aiConfig';
import { ClaudeLogo, OpenAILogo } from './brandLogos';
import {
  type AiConfig,
  type AiMode,
  type AiModelList,
  type AiProvider,
  type AiSubscriptionStatus,
  type AiTestResult,
  getAiConfig,
  getAiModels,
  getAiSubscriptionStatus,
  saveAiConfig,
  testAiConfig,
} from './service';

const PROVIDER_META: Record<
  AiProvider,
  {
    name: string;
    desc: string;
    console: string;
    consoleName: string;
    Logo: React.FC<{ size?: number }>;
  }
> = {
  claude: {
    name: 'Claude',
    desc: 'Anthropic · Opus / Haiku',
    console: 'https://console.anthropic.com/settings/keys',
    consoleName: 'Anthropic',
    Logo: ClaudeLogo,
  },
  openai: {
    name: 'OpenAI',
    desc: 'GPT 系列',
    console: 'https://platform.openai.com/api-keys',
    consoleName: 'OpenAI',
    Logo: OpenAILogo,
  },
};

/** 订阅状态的显示：平台看登录状态，租户只看可用与否 */
const SUB_LABEL: Record<SubState, [string, 'green' | 'red' | 'gray']> = {
  in: ['已登录', 'green'],
  out: ['未登录', 'red'],
  avail: ['可用', 'green'],
  unavail: ['不可用', 'red'],
  unknown: ['状态未知', 'gray'],
};

/** 思考强度：分段选择，空值显示为「中」 */
const EffortField: React.FC<{
  value?: string;
  onChange?: (v: string) => void;
  disabled?: boolean;
}> = ({ value, onChange, disabled }) => (
  <Segmented
    block
    disabled={disabled}
    options={EFFORT_OPTIONS}
    value={value || DEFAULT_EFFORT}
    onChange={(v) => onChange?.(v as string)}
  />
);

const CheckItem: React.FC<{
  state: boolean | undefined;
  title: string;
  detail?: string;
}> = ({ state, title, detail }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
      {state === true ? (
        <CheckCircleFilled
          style={{ color: palette.green, fontSize: 16, marginTop: 2 }}
        />
      ) : state === false ? (
        <CloseCircleFilled
          style={{ color: palette.red, fontSize: 16, marginTop: 2 }}
        />
      ) : (
        <span
          style={{
            width: 16,
            height: 16,
            marginTop: 2,
            flex: 'none',
            borderRadius: 8,
            border: `1.5px dashed ${palette.faint}`,
          }}
        />
      )}
      <div style={{ minWidth: 0 }}>
        <div
          style={{
            fontSize: 13,
            fontWeight: 500,
            color: state === undefined ? palette.mute : palette.ink,
          }}
        >
          {title}
        </div>
        {detail && (
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
            {detail}
          </div>
        )}
      </div>
    </div>
  );
};

const Divider: React.FC<{ palette: Palette }> = ({ palette }) => (
  <div style={{ height: 1, background: palette.hairline, margin: '4px 0' }} />
);

/** 状态标签：带色点 */
const StatePill: React.FC<{
  label: string;
  tone: 'green' | 'red' | 'gray';
}> = ({ label, tone }) => (
  <Pill tone={tone} dot>
    {label}
  </Pill>
);

/** 连接方式的一行：单选 + 用法 + 状态 + 操作，选中或展开时下方放内容 */
const OptionRow: React.FC<{
  selected: boolean;
  disabled?: boolean;
  icon: React.ReactNode;
  label: string;
  extra?: React.ReactNode;
  state: React.ReactNode;
  action?: React.ReactNode;
  onSelect: () => void;
  children?: React.ReactNode;
}> = ({
  selected,
  disabled,
  icon,
  label,
  extra,
  state,
  action,
  onSelect,
  children,
}) => {
  const { palette } = useAppTheme();
  // 选中但已不可用（如授权被收回）用警示色，不用「正在使用」的蓝色
  const warn = selected && disabled;
  return (
    <div
      style={{
        borderRadius: 10,
        border: `${selected ? 1.5 : 1}px solid ${
          warn ? palette.orange : selected ? palette.link : palette.hairline
        }`,
        background: warn
          ? palette.orangeSoft
          : selected
            ? palette.accentSoft
            : disabled
              ? palette.inset
              : palette.card,
        transition: 'background 200ms ease-out, border-color 200ms ease-out',
      }}
    >
      {/* biome-ignore lint/a11y/useSemanticElements: 整行可点的卡片式单选，行内还有「更换」「填写」按钮，不能整体用原生 input 或 button 包裹；按 ARIA radio 模式实现 */}
      <div
        role="radio"
        aria-checked={selected}
        aria-disabled={disabled}
        tabIndex={disabled ? -1 : 0}
        onClick={() => !disabled && onSelect()}
        onKeyDown={(e) => {
          if (!disabled && (e.key === 'Enter' || e.key === ' ')) {
            e.preventDefault();
            onSelect();
          }
        }}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '12px 14px',
          cursor: disabled ? 'not-allowed' : 'pointer',
        }}
      >
        <span
          style={{
            display: 'grid',
            placeItems: 'center',
            flex: 'none',
            width: 18,
            height: 18,
            borderRadius: 9,
            border: `1px solid ${
              selected && !disabled ? palette.link : palette.control
            }`,
            background: selected && !disabled ? palette.link : 'transparent',
          }}
        >
          {selected && !disabled && (
            <span
              style={{
                width: 6,
                height: 6,
                borderRadius: 3,
                background: '#fff',
              }}
            />
          )}
        </span>
        <span
          style={{
            color: disabled
              ? palette.faint
              : selected
                ? palette.link
                : palette.sub,
          }}
        >
          {icon}
        </span>
        <span
          style={{
            flex: 1,
            minWidth: 0,
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            fontSize: 14,
            fontWeight: 500,
            color: disabled ? palette.mute : palette.ink,
          }}
        >
          {label}
          {extra}
        </span>
        {state}
        {action}
      </div>
      {children && (
        <div style={{ padding: '0 14px 14px 42px' }}>{children}</div>
      )}
    </div>
  );
};

const SectionHead: React.FC<{
  palette: Palette;
  title: string;
  hint?: string;
  extra?: React.ReactNode;
}> = ({ palette, title, hint, extra }) => (
  <div
    style={{
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      marginBottom: 20,
    }}
  >
    <span style={{ fontSize: 16, fontWeight: 600, color: palette.ink }}>
      {title}
    </span>
    {hint && <span style={{ fontSize: 12, color: palette.mute }}>{hint}</span>}
    <span style={{ marginLeft: 'auto' }}>{extra}</span>
  </div>
);

/** 字段标题，说明以小字跟在标题后面 */
const FieldLabel: React.FC<{ title: string; hint: React.ReactNode }> = ({
  title,
  hint,
}) => {
  const { palette } = useAppTheme();
  return (
    <span>
      {title}
      <span
        style={{
          marginLeft: 8,
          fontSize: 12,
          fontWeight: 400,
          color: palette.mute,
        }}
      >
        {hint}
      </span>
    </span>
  );
};

/**
 * 业务设置 → AI 设置：按服务商分组列出连接方式（本公司 API Key、平台订阅），选中哪一行就用哪一种；
 * 所有修改只通过底部保存条保存。见 openspec ai-platform/tenant-ai-settings 与原型 AI设置.op 第三页（V3）。
 */
const AiSettingsPage: React.FC = () => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess();
  const canEdit = !!(access as Record<string, unknown>)[
    'system:ai-config:edit'
  ];
  const [form] = Form.useForm<AiFormValues>();
  // preserve：右侧切到只读时模型字段不再渲染，仍要拿到它们的值
  const values = Form.useWatch([], { form, preserve: true }) as
    | AiFormValues
    | undefined;
  const mode: AiMode = values?.mode ?? 'api';
  const provider: AiProvider = values?.provider ?? 'claude';

  const [config, setConfig] = useState<AiConfig>();
  const [loadError, setLoadError] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editingKey, setEditingKey] = useState<AiProvider>();
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<AiTestResult>();
  const [models, setModels] = useState<AiModelList>();
  const [modelsLoading, setModelsLoading] = useState(false);
  const [status, setStatus] = useState<AiSubscriptionStatus>();
  const [statusLoading, setStatusLoading] = useState(false);

  const loadStatus = useCallback(async () => {
    setStatusLoading(true);
    try {
      setStatus(await getAiSubscriptionStatus());
    } catch {
      setStatus(undefined);
    } finally {
      setStatusLoading(false);
    }
  }, []);

  const load = useCallback(async () => {
    setLoadError(false);
    try {
      const c = await getAiConfig();
      setConfig(c);
      setEditingKey(undefined);
      form.setFieldsValue(toFormValues(c));
      if (c.subscriptionAllowed) loadStatus();
    } catch {
      setLoadError(true);
    }
  }, [form, loadStatus]);

  useEffect(() => {
    load();
  }, [load]);

  /** 选中一种连接方式：表单换成这一种已保存的设置；之前取到的模型列表与测试结果作废 */
  const select = (m: AiMode, p: AiProvider, editKey = false) => {
    if (!config) return;
    form.setFieldsValue(profileFormValues(config, m, p));
    setEditingKey(editKey ? p : undefined);
    setModels(undefined);
    setTestResult(undefined);
  };

  const fetchModels = async () => {
    if (!canEdit || models || modelsLoading) return;
    setModelsLoading(true);
    try {
      setModels(await getAiModels(toPayload(form.getFieldsValue(true))));
    } catch {
      setModels({
        models: [],
        source: 'defaults',
        error: '获取模型列表失败，可直接输入模型名',
      });
    } finally {
      setModelsLoading(false);
    }
  };

  const runTest = async () => {
    setTesting(true);
    setTestResult(undefined);
    try {
      const r = await testAiConfig(toPayload(form.getFieldsValue(true)));
      setTestResult(r);
      if (r.ok) message.success('连接正常');
    } catch {
      // 请求层已提示错误原因
    } finally {
      setTesting(false);
    }
  };

  const doSave = async (v: AiFormValues) => {
    setSaving(true);
    try {
      await saveAiConfig(toPayload(v));
      message.success('已保存，之后提交的 AI 任务立即使用新配置');
      setTestResult(undefined);
      await load();
    } finally {
      setSaving(false);
    }
  };

  const onFinish = (v: AiFormValues) => {
    if (!(v.apiKey ?? '').trim()) {
      doSave(v);
      return;
    }
    const replacing = config?.apiProfiles[v.provider ?? 'claude']?.hasKey;
    modal.confirm({
      title: replacing ? '确认更换 API Key？' : '确认保存 API Key？',
      content: replacing
        ? '保存后原来的 Key 不再使用，之后提交的 AI 任务都按新 Key 计费。'
        : '之后提交的 AI 任务会使用这个 Key，费用记在该 Key 所属的账号上。',
      okText: '确认保存',
      cancelText: '取消',
      onOk: () => doSave(v),
    });
  };

  if (loadError) {
    return (
      <>
        <PageTitle title="AI 设置" />
        <Card>
          <Result
            status="warning"
            title="AI 设置加载失败"
            subTitle="请检查网络后重试"
            extra={
              <Button icon={<ReloadOutlined />} onClick={load}>
                重试
              </Button>
            }
          />
        </Card>
      </>
    );
  }
  if (!config) {
    return (
      <>
        <PageTitle title="AI 设置" />
        <Card>
          <Skeleton active paragraph={{ rows: 2 }} />
        </Card>
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={24} xl={12}>
            <Card>
              <Skeleton active paragraph={{ rows: 6 }} />
            </Card>
          </Col>
          <Col xs={24} xl={12}>
            <Card>
              <Skeleton active paragraph={{ rows: 6 }} />
            </Card>
          </Col>
        </Row>
      </>
    );
  }

  const platform = config.platform;
  const subState = (p: AiProvider) =>
    statusLoading && !status
      ? 'unknown'
      : subscriptionState(status, p, platform);
  // 已选用平台订阅、但授权已被收回：只显示原来选的那一行（不可选）
  const revoked =
    config.configured &&
    config.mode === 'subscription' &&
    !config.subscriptionAllowed;
  const dirty = !!values && isDirty(values, toFormValues(config));
  const typedKey = !!(values?.apiKey ?? '').trim();
  const selectedProfile = config.apiProfiles[provider];
  const keyMissing = mode === 'api' && !selectedProfile?.hasKey && !typedKey;
  const subscriptionReadonly = mode === 'subscription' && !platform;
  const meta = PROVIDER_META[provider];
  const keyTest = testResult && testSlot(testResult.items, 'API Key');

  // —— 顶部状态卡：描述已保存的选择 ——
  const savedMeta = PROVIDER_META[config.provider];
  const savedSub = config.mode === 'subscription';
  const savedState = subState(config.provider);
  type Tone = 'ok' | 'warn' | 'bad' | 'idle';
  let tone: Tone = 'ok';
  let title = 'AI 已配置';
  let pill: [string, 'green' | 'red' | 'orange' | 'gray'] | undefined;
  let desc: string | undefined;
  if (!config.configured) {
    tone = 'idle';
    title = '还没有配置 AI';
    pill = ['未配置', 'gray'];
    desc = '填写本公司 API Key 后即可使用。';
  } else if (!savedSub) {
    if (testResult) {
      tone = testResult.ok ? 'ok' : 'bad';
      title = testResult.ok ? 'AI 已就绪' : '连接失败';
      pill = testResult.ok ? ['测试通过', 'green'] : ['测试未通过', 'red'];
    }
  } else if (revoked) {
    tone = 'warn';
    title = '平台订阅不可用';
    pill = ['不可用', 'orange'];
    desc = '请改用本公司 API Key。';
  } else if (savedState === 'in' || savedState === 'avail') {
    title = 'AI 已就绪';
    pill = ['可用', 'green'];
  } else if (savedState === 'out') {
    tone = 'bad';
    title = `${savedMeta.name} 平台订阅未登录`;
    pill = ['不可用', 'red'];
    desc = '登录后才能使用。';
  } else if (savedState === 'unavail') {
    tone = 'bad';
    title = `${savedMeta.name} 平台订阅不可用`;
    pill = ['不可用', 'red'];
    desc = '请改选其他可用的方式。';
  } else if (!statusLoading) {
    tone = 'warn';
    title = '订阅状态获取失败';
    pill = ['状态未知', 'orange'];
  }
  const toneBg = {
    ok: palette.greenSoft,
    warn: palette.orangeSoft,
    bad: palette.redSoft,
    idle: palette.inset,
  }[tone];
  const showMeta = config.configured && !desc && !(tone === 'warn');
  const metaItems = [
    {
      icon: savedSub ? <UserOutlined /> : <KeyOutlined />,
      text: `${savedMeta.name} · ${savedSub ? '平台订阅' : '本公司 API Key'}`,
    },
    config.updateTime
      ? {
          icon: <ClockCircleOutlined />,
          text: `${formatDateTime(config.updateTime)} 由 ${config.updateBy || '—'} 修改`,
        }
      : null,
  ].filter(Boolean) as { icon: React.ReactNode; text: string }[];

  const statusCard = (
    <Card style={{ marginBottom: 16 }}>
      <div
        style={{
          display: 'flex',
          gap: 16,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <span
          style={{
            display: 'grid',
            placeItems: 'center',
            width: 48,
            height: 48,
            borderRadius: 12,
            fontSize: 22,
            color: palette.mute,
            background: toneBg,
          }}
        >
          {config.configured ? (
            <savedMeta.Logo size={24} />
          ) : tone === 'bad' ? (
            <WarningOutlined />
          ) : (
            <RobotOutlined />
          )}
        </span>
        <div style={{ flex: 1, minWidth: 240 }}>
          <div
            style={{
              display: 'flex',
              gap: 8,
              alignItems: 'center',
              fontSize: 18,
              fontWeight: 600,
              color: palette.ink,
            }}
          >
            {title}
            {pill && <Pill tone={pill[1]}>{pill[0]}</Pill>}
          </div>
          <div style={{ marginTop: 6, fontSize: 13, color: palette.mute }}>
            {desc}
            {showMeta && (
              <span style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
                {metaItems.map((m) => (
                  <span key={m.text} style={{ display: 'inline-flex', gap: 6 }}>
                    {m.icon}
                    {m.text}
                  </span>
                ))}
              </span>
            )}
          </div>
        </div>
        {canEdit && mode === 'api' && (
          <Button
            icon={<ApiOutlined />}
            loading={testing}
            disabled={keyMissing}
            onClick={runTest}
          >
            {tone === 'bad' && testResult ? '重新测试' : '测试连接'}
          </Button>
        )}
        {mode === 'subscription' && !revoked && (
          <Button
            icon={<ReloadOutlined />}
            loading={statusLoading}
            onClick={loadStatus}
          >
            {savedState === 'unknown' && savedSub ? '重试' : '刷新状态'}
          </Button>
        )}
      </div>

      {testResult && mode === 'api' && (
        <>
          <div
            style={{
              height: 1,
              background: palette.hairline,
              margin: '16px 0',
            }}
          />
          <Row gutter={[24, 12]}>
            {(['API Key', '解析模型', '翻译模型'] as const).map((slot) => {
              const item = testSlot(testResult.items, slot);
              const model = item?.name.slice(slot.length).trim();
              const itemTitle = !item
                ? `${slot}未检测`
                : slot === 'API Key'
                  ? item.ok
                    ? 'API Key 有效'
                    : 'API Key 无效或已失效'
                  : `${slot}${item.ok ? '可用' : '不可用'}`;
              const detail = !item
                ? '前一项通过后再检测'
                : slot === 'API Key'
                  ? item.ok
                    ? `${meta.consoleName} 已确认身份`
                    : item.message
                  : item.ok
                    ? model
                    : `${model}：${item.message}`;
              return (
                <Col key={slot} xs={24} md={8}>
                  <CheckItem
                    state={item?.ok}
                    title={itemTitle}
                    detail={detail}
                  />
                </Col>
              );
            })}
          </Row>
        </>
      )}
    </Card>
  );

  // —— 左：服务商与账号（连接方式列表） ——
  const keyEditor = (p: AiProvider) => (
    <Form.Item
      name="apiKey"
      style={{ marginBottom: 0 }}
      validateStatus={keyTest && !keyTest.ok ? 'error' : undefined}
      help={
        keyTest && !keyTest.ok ? 'Key 无效或已失效，请粘贴新的 Key' : undefined
      }
      extra={
        <span style={{ fontSize: 12 }}>
          还没有 Key？
          <a href={PROVIDER_META[p].console} target="_blank" rel="noreferrer">
            去 {PROVIDER_META[p].consoleName} 控制台创建 <ExportOutlined />
          </a>
        </span>
      }
    >
      {/* 普通文本框：Key 很长，粘贴后能看到内容便于核对；页面不回显已保存的 Key */}
      <Input
        autoComplete="off"
        spellCheck={false}
        autoFocus={editingKey === p}
        prefix={<KeyOutlined style={{ color: palette.faint }} />}
        placeholder={`粘贴 ${PROVIDER_META[p].name} 的 API Key`}
        suffix={
          config.apiProfiles[p]?.hasKey && (
            <Button
              type="link"
              size="small"
              style={{ padding: 0, height: 'auto' }}
              onClick={() => {
                form.setFieldValue('apiKey', '');
                setEditingKey(undefined);
                setTestResult(undefined);
              }}
            >
              取消
            </Button>
          )
        }
      />
    </Form.Item>
  );

  const providerGroup = (p: AiProvider) => {
    const m = PROVIDER_META[p];
    const apiProfile = config.apiProfiles[p];
    const apiSelected = mode === 'api' && provider === p;
    const subSelected = mode === 'subscription' && provider === p;
    const st = subState(p);
    const showSub =
      config.subscriptionAllowed || (revoked && config.provider === p);
    const subDisabled = revoked || (!platform && st !== 'avail');
    const showKeyEditor =
      apiSelected && (editingKey === p || !apiProfile.hasKey);
    return (
      <div key={p} style={{ display: 'grid', gap: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span
            style={{
              display: 'grid',
              placeItems: 'center',
              width: 32,
              height: 32,
              borderRadius: 8,
              background: palette.card,
              border: `1px solid ${palette.hairline}`,
              color: palette.ink,
            }}
          >
            <m.Logo size={20} />
          </span>
          <span style={{ fontSize: 14, fontWeight: 600, color: palette.ink }}>
            {m.name}
          </span>
          <span style={{ fontSize: 12, color: palette.mute }}>{m.desc}</span>
        </div>
        <OptionRow
          selected={apiSelected}
          disabled={!canEdit && !apiSelected}
          icon={<KeyOutlined />}
          label="本公司 API Key"
          extra={
            apiProfile.hasKey && (
              <span
                style={{
                  fontSize: 12,
                  fontWeight: 400,
                  color: palette.mute,
                  fontFamily: 'monospace',
                }}
              >
                {apiProfile.apiKeyMasked}
              </span>
            )
          }
          state={
            <StatePill
              label={apiProfile.hasKey ? '已配置' : '未配置'}
              tone={apiProfile.hasKey ? 'green' : 'gray'}
            />
          }
          action={
            canEdit &&
            !showKeyEditor && (
              <Button
                type="link"
                size="small"
                style={{ padding: 0 }}
                onClick={(e) => {
                  e.stopPropagation();
                  select('api', p, true);
                }}
              >
                {apiProfile.hasKey ? '更换' : '填写'}
              </Button>
            )
          }
          onSelect={() => !apiSelected && select('api', p)}
        >
          {showKeyEditor && canEdit && keyEditor(p)}
        </OptionRow>
        {showSub && (
          <OptionRow
            selected={subSelected}
            disabled={subDisabled || !canEdit}
            icon={<UserOutlined />}
            label="平台订阅"
            state={
              revoked ? (
                <StatePill label="不可用" tone="gray" />
              ) : (
                <StatePill label={SUB_LABEL[st][0]} tone={SUB_LABEL[st][1]} />
              )
            }
            action={
              platform &&
              st === 'out' &&
              !subSelected &&
              canEdit && (
                <Button
                  type="link"
                  size="small"
                  style={{ padding: 0 }}
                  onClick={(e) => {
                    e.stopPropagation();
                    select('subscription', p);
                  }}
                >
                  登录方法
                </Button>
              )
            }
            onSelect={() => !subSelected && select('subscription', p)}
          >
            {platform &&
              subSelected &&
              st === 'out' &&
              status?.loginCommands && (
                <div style={{ fontSize: 12, color: palette.mute }}>
                  在服务器执行以下命令登录：
                  <Typography.Paragraph
                    code
                    copyable
                    style={{ margin: '6px 0 0', fontSize: 12 }}
                  >
                    {status.loginCommands[p]}
                  </Typography.Paragraph>
                </div>
              )}
          </OptionRow>
        )}
      </div>
    );
  };

  const connectCard = (
    <Card style={{ height: '100%' }}>
      <SectionHead
        palette={palette}
        title="服务商与账号"
        hint="API Key 加密保存，只显示首尾几位"
      />
      <div style={{ display: 'grid', gap: 16 }}>
        {PROVIDERS.map(providerGroup)}
        {platform && mode === 'subscription' && (
          <div
            style={{
              display: 'flex',
              gap: 8,
              padding: '10px 12px',
              borderRadius: 8,
              fontSize: 12,
              color: palette.orange,
              background: palette.orangeSoft,
            }}
          >
            <InfoCircleOutlined style={{ marginTop: 2 }} />
            平台订阅受账号套餐额度限制，同一时间只处理一个任务。
          </div>
        )}
      </div>
    </Card>
  );

  // —— 右：模型与用量 ——
  const modelField = (field: ModelField, label: string, hint: string) => {
    const fallback = defaultModel(config.defaultModels, provider, field);
    return (
      <Form.Item
        name={field === 'parse' ? 'parseModel' : 'translateModel'}
        label={<FieldLabel title={label} hint={hint} />}
      >
        <AutoComplete
          allowClear
          placeholder={`可选择或输入模型名，推荐 ${fallback}`}
          options={modelOptions(models, fallback).map((o) => ({
            value: o.value,
            label: (
              <span
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  gap: 8,
                }}
              >
                {o.value}
                <span style={{ display: 'inline-flex', gap: 4 }}>
                  {o.tags.map((t) => (
                    <Pill key={t} tone={t === '推荐' ? 'accent' : 'gray'}>
                      {t}
                    </Pill>
                  ))}
                </span>
              </span>
            ),
          }))}
          onOpenChange={(open) => open && fetchModels()}
          notFoundContent={modelsLoading ? <Spin size="small" /> : null}
          popupRender={(menu) => (
            <>
              {models?.error ? (
                <Typography.Text
                  type="warning"
                  style={{ display: 'block', padding: '4px 12px' }}
                >
                  {models.error}
                </Typography.Text>
              ) : (
                !modelsLoading &&
                models && (
                  <Typography.Text
                    type="secondary"
                    style={{
                      display: 'block',
                      padding: '4px 12px',
                      fontSize: 12,
                    }}
                  >
                    {models.source === 'provider'
                      ? `${meta.consoleName} 返回的模型`
                      : mode === 'subscription'
                        ? '推荐模型'
                        : '填写 API Key 后可获取完整的模型列表'}
                  </Typography.Text>
                )
              )}
              {menu}
              <div
                style={{
                  padding: '6px 12px',
                  fontSize: 12,
                  color: palette.mute,
                  borderTop: `1px solid ${palette.hairline}`,
                }}
              >
                <EnterOutlined /> 列表里没有的模型可直接输入名称
              </div>
            </>
          )}
        />
      </Form.Item>
    );
  };

  const effortLabel = (v?: string) =>
    EFFORT_OPTIONS.find((o) => o.value === (v || DEFAULT_EFFORT))?.label ??
    '中';

  const readonlyModels = (
    <Card style={{ height: '100%' }}>
      <SectionHead
        palette={palette}
        title="模型与用量"
        extra={
          <Pill tone="gray">
            <LockOutlined /> 由平台设置
          </Pill>
        }
      />
      {[
        ['解析模型', '解析询盘、拆分订单时使用', values?.parseModel],
        ['解析思考强度', '', effortLabel(values?.parseEffort)],
        [
          '翻译模型',
          '翻译型号描述、音译联系人名时使用',
          values?.translateModel,
        ],
        ['翻译思考强度', '', effortLabel(values?.translateEffort)],
        [
          '每次解析最多搜索',
          '联网查型号资料的上限',
          `${values?.maxWebSearches ?? 20} 次`,
        ],
      ].map(([k, d, v], i) => (
        <React.Fragment key={k}>
          {(i === 2 || i === 4) && <Divider palette={palette} />}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '10px 0',
            }}
          >
            <div>
              <div
                style={{ fontSize: 13, fontWeight: 500, color: palette.ink }}
              >
                {k}
              </div>
              {d && (
                <div
                  style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}
                >
                  {d}
                </div>
              )}
            </div>
            <span style={{ fontSize: 14, fontWeight: 500, color: palette.ink }}>
              {v}
            </span>
          </div>
        </React.Fragment>
      ))}
    </Card>
  );

  const editableModels = (
    <Card style={{ height: '100%' }}>
      <SectionHead
        palette={palette}
        title="模型与用量"
        hint={mode === 'subscription' ? '获授权的公司共用' : undefined}
      />
      {modelField(
        'parse',
        '解析模型',
        '解析询盘、拆分订单时使用，建议选能力最强的',
      )}
      <Form.Item name="parseEffort" label="解析思考强度">
        <EffortField disabled={!canEdit} />
      </Form.Item>
      <Divider palette={palette} />
      {modelField(
        'translate',
        '翻译模型',
        '翻译型号描述、音译联系人名时使用，选快且便宜的即可',
      )}
      <Form.Item name="translateEffort" label="翻译思考强度">
        <EffortField disabled={!canEdit} />
      </Form.Item>
      <Divider palette={palette} />
      <div
        style={{
          display: 'flex',
          gap: 16,
          alignItems: 'center',
          justifyContent: 'space-between',
          marginTop: 16,
        }}
      >
        <div>
          <div style={{ fontSize: 13, fontWeight: 500, color: palette.ink }}>
            每次解析最多搜索
          </div>
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
            解析询盘时联网查型号资料的上限，越多越准、费用越高，可填{' '}
            <span style={{ whiteSpace: 'nowrap' }}>1–100</span>
          </div>
        </div>
        <Form.Item name="maxWebSearches" noStyle>
          <InputNumber
            mode="spinner"
            min={1}
            max={100}
            precision={0}
            style={{ width: 130, flex: 'none' }}
          />
        </Form.Item>
      </div>
    </Card>
  );

  // —— 底部保存条：只在有修改时出现，页面唯一的保存入口 ——
  const saveBar = canEdit && dirty && (
    <div
      style={{
        position: 'sticky',
        bottom: 16,
        zIndex: 2,
        marginTop: 16,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: 12,
        padding: '12px 24px',
        borderRadius: 12,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        boxShadow: '0 8px 24px -4px rgba(0,0,0,0.16)',
        animation: 'ai-save-bar-in 200ms ease-out',
      }}
    >
      <style>
        {
          '@keyframes ai-save-bar-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }'
        }
      </style>
      <span
        style={{
          display: 'inline-flex',
          gap: 8,
          alignItems: 'center',
          fontSize: 13,
          color: palette.ink,
        }}
      >
        <span
          style={{
            width: 8,
            height: 8,
            borderRadius: 4,
            background: palette.orange,
          }}
        />
        {keyMissing ? '填写 API Key 后才能保存' : '有修改未保存'}
      </span>
      <span style={{ display: 'inline-flex', gap: 8 }}>
        <Button
          type="text"
          disabled={saving}
          onClick={() => {
            form.setFieldsValue(toFormValues(config));
            setEditingKey(undefined);
            setModels(undefined);
            setTestResult(undefined);
          }}
        >
          撤销修改
        </Button>
        <Button
          type="primary"
          htmlType="submit"
          loading={saving}
          disabled={keyMissing}
        >
          保存
        </Button>
      </span>
    </div>
  );

  return (
    <>
      <PageTitle
        title="AI 设置"
        description="询盘解析、型号描述翻译、联系人名音译使用的 AI 服务。"
      />
      {statusCard}
      <Form
        form={form}
        layout="vertical"
        disabled={!canEdit}
        onFinish={() => onFinish(form.getFieldsValue(true))}
        onValuesChange={(changed) => {
          if ('apiKey' in changed) setTestResult(undefined);
        }}
      >
        <Form.Item name="mode" hidden>
          <Input />
        </Form.Item>
        <Form.Item name="provider" hidden>
          <Input />
        </Form.Item>
        <Row gutter={[16, 16]}>
          <Col xs={24} xl={12}>
            {connectCard}
          </Col>
          <Col xs={24} xl={12}>
            {subscriptionReadonly ? readonlyModels : editableModels}
          </Col>
        </Row>
        {saveBar}
      </Form>
    </>
  );
};

export default AiSettingsPage;
