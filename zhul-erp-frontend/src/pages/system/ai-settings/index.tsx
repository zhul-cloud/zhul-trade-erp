import {
  ApiOutlined,
  CheckCircleFilled,
  CheckOutlined,
  ClockCircleOutlined,
  CloseCircleFilled,
  EnterOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  KeyOutlined,
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
  modelAfterProviderChange,
  modelOptions,
  testSlot,
  toFormValues,
  toPayload,
} from './aiConfig';
import {
  type AiConfig,
  type AiModelList,
  type AiSubscriptionStatus,
  type AiTestResult,
  getAiConfig,
  getAiModels,
  getAiSubscriptionStatus,
  saveAiConfig,
  testAiConfig,
} from './service';

const PROVIDERS = {
  claude: {
    name: 'Claude',
    desc: 'Anthropic · Opus / Haiku',
    console: 'https://console.anthropic.com/settings/keys',
    consoleName: 'Anthropic',
  },
  openai: {
    name: 'OpenAI',
    desc: 'GPT 系列',
    console: 'https://platform.openai.com/api-keys',
    consoleName: 'OpenAI',
  },
} as const;

/** 可选卡片：服务商、运行方式共用 */
const OptionCard: React.FC<{
  selected: boolean;
  disabled?: boolean;
  icon: React.ReactNode;
  title: string;
  desc: string;
  tag?: React.ReactNode;
  onSelect: () => void;
}> = ({ selected, disabled, icon, title, desc, tag, onSelect }) => {
  const { palette } = useAppTheme();
  return (
    <button
      type="button"
      aria-pressed={selected}
      disabled={disabled}
      onClick={onSelect}
      style={{
        display: 'flex',
        gap: 12,
        alignItems: 'flex-start',
        width: '100%',
        height: '100%',
        padding: 16,
        textAlign: 'left',
        borderRadius: 10,
        cursor: disabled ? 'not-allowed' : 'pointer',
        background: selected ? palette.accentSoft : palette.card,
        border: `${selected ? 1.5 : 1}px solid ${selected ? palette.link : palette.hairline}`,
        transition: 'background 200ms ease-out, border-color 200ms ease-out',
      }}
    >
      <span
        style={{
          display: 'grid',
          placeItems: 'center',
          flex: 'none',
          width: 36,
          height: 36,
          borderRadius: 10,
          fontSize: 18,
          color: selected ? palette.link : palette.sub,
          background: selected ? palette.card : palette.inset,
        }}
      >
        {icon}
      </span>
      <span style={{ flex: 1, minWidth: 0 }}>
        <span
          style={{
            display: 'flex',
            gap: 6,
            alignItems: 'center',
            fontSize: 14,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          {title}
          {tag}
        </span>
        <span
          style={{
            display: 'block',
            marginTop: 4,
            fontSize: 12,
            color: palette.mute,
          }}
        >
          {desc}
        </span>
      </span>
      <span
        style={{
          display: 'grid',
          placeItems: 'center',
          flex: 'none',
          width: 18,
          height: 18,
          borderRadius: 9,
          fontSize: 11,
          color: '#fff',
          background: selected ? palette.link : 'transparent',
          border: `1px solid ${selected ? palette.link : palette.control}`,
        }}
      >
        {selected && <CheckOutlined />}
      </span>
    </button>
  );
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

/** 业务设置 → AI 设置：当前租户的 AI 编排配置，见 openspec ai-platform/tenant-ai-settings */
const AiSettingsPage: React.FC = () => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess();
  const canEdit = !!(access as Record<string, unknown>)[
    'system:ai-config:edit'
  ];
  const [form] = Form.useForm<AiFormValues>();
  const values = Form.useWatch([], form) as AiFormValues | undefined;
  const mode = values?.mode;
  const provider = values?.provider ?? 'claude';

  const [config, setConfig] = useState<AiConfig>();
  const [loadError, setLoadError] = useState(false);
  const [saving, setSaving] = useState(false);
  const [changingKey, setChangingKey] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<AiTestResult>();
  const [models, setModels] = useState<AiModelList>();
  const [modelsLoading, setModelsLoading] = useState(false);
  const [status, setStatus] = useState<AiSubscriptionStatus>();
  const [statusLoading, setStatusLoading] = useState(false);

  const load = useCallback(async () => {
    setLoadError(false);
    try {
      const c = await getAiConfig();
      setConfig(c);
      setChangingKey(false);
      form.setFieldsValue({ ...toFormValues(c), apiKey: '' });
    } catch {
      setLoadError(true);
    }
  }, [form]);

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

  useEffect(() => {
    load();
  }, [load]);

  const subscription = mode === 'subscription';
  useEffect(() => {
    if (config?.subscriptionAllowed && subscription) loadStatus();
  }, [config?.subscriptionAllowed, subscription, loadStatus]);

  /** 切换服务商、运行方式或更换 Key 后，已取到的列表与测试结果都作废 */
  const resetDerived = () => {
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
    modal.confirm({
      title: config?.hasKey ? '确认更换 API Key？' : '确认保存 API Key？',
      content: config?.hasKey
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
          <Skeleton active paragraph={{ rows: 3 }} />
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

  const saved = toFormValues(config);
  const dirty = !!values && isDirty(values, saved);
  const typedKey = !!(values?.apiKey ?? '').trim();
  const keyMissing = !subscription && !config.hasKey && !typedKey;
  const meta = PROVIDERS[provider];
  const keyTest = testResult && testSlot(testResult.items, 'API Key');

  // —— 顶部状态卡 ——
  const tone = testResult
    ? testResult.ok
      ? 'ok'
      : 'fail'
    : config.configured
      ? 'saved'
      : 'empty';
  const toneColor = {
    ok: [palette.green, palette.greenSoft],
    saved: [palette.green, palette.greenSoft],
    fail: [palette.red, palette.redSoft],
    empty: [palette.mute, palette.inset],
  }[tone];
  const savedMeta = [
    {
      icon: <RobotOutlined />,
      text: PROVIDERS[config.provider]?.name ?? config.provider,
    },
    config.mode === 'subscription'
      ? { icon: <UserOutlined />, text: '个人订阅' }
      : {
          icon: <KeyOutlined />,
          text: `API Key ${config.apiKeyMasked || '未填写'}`,
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
            color: toneColor[0],
            background: toneColor[1],
          }}
        >
          {tone === 'fail' ? <WarningOutlined /> : <RobotOutlined />}
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
            {
              {
                ok: 'AI 已就绪',
                saved: 'AI 已配置',
                fail: '连接失败',
                empty: '还没有配置 AI',
              }[tone]
            }
            {tone === 'ok' && <Pill tone="green">测试通过</Pill>}
            {tone === 'fail' && <Pill tone="red">测试未通过</Pill>}
            {tone === 'empty' && <Pill tone="gray">未配置</Pill>}
          </div>
          <div style={{ marginTop: 6, fontSize: 13, color: palette.mute }}>
            {tone === 'empty' &&
              '配置前，询盘解析、型号翻译和联系人音译都不可用。平台的配置不会共享给你的公司，需要单独填写。'}
            {tone === 'fail' &&
              '当前填写的配置无法调用 AI，询盘解析和翻译会失败。按下面的提示修改后重新测试。'}
            {(tone === 'ok' || tone === 'saved') && (
              <span style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
                {savedMeta.map((m) => (
                  <span key={m.text} style={{ display: 'inline-flex', gap: 6 }}>
                    {m.icon}
                    {m.text}
                  </span>
                ))}
                {tone === 'saved' && !dirty && (
                  <span>点「测试连接」确认 Key 和模型可用</span>
                )}
              </span>
            )}
          </div>
        </div>
        {canEdit && (
          <Button
            icon={<ApiOutlined />}
            loading={testing}
            disabled={keyMissing}
            onClick={runTest}
          >
            {tone === 'fail' ? '重新测试' : '测试连接'}
          </Button>
        )}
      </div>

      {testResult && (
        <>
          <div
            style={{
              height: 1,
              background: palette.hairline,
              margin: '16px 0',
            }}
          />
          {subscription ? (
            <Row gutter={[24, 12]}>
              {testResult.items.map((i) => (
                <Col key={i.name} xs={24} md={8}>
                  <CheckItem state={i.ok} title={i.name} detail={i.message} />
                </Col>
              ))}
            </Row>
          ) : (
            <Row gutter={[24, 12]}>
              {(['API Key', '解析模型', '翻译模型'] as const).map((slot) => {
                const item = testSlot(testResult.items, slot);
                const model = item?.name.slice(slot.length).trim();
                const title = !item
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
                    <CheckItem state={item?.ok} title={title} detail={detail} />
                  </Col>
                );
              })}
            </Row>
          )}
        </>
      )}

      {tone === 'empty' && (
        <>
          <div
            style={{
              height: 1,
              background: palette.hairline,
              margin: '16px 0',
            }}
          />
          <Row gutter={[24, 12]}>
            {[
              ['选择服务商', 'Claude 或 OpenAI'],
              ['填写 API Key', '在服务商控制台创建'],
              ['测试连接并保存', '通过后即可使用'],
            ].map(([a, b], i) => {
              const done = i === 0 || (i === 1 && typedKey);
              return (
                <Col key={a} xs={24} md={8}>
                  <div style={{ display: 'flex', gap: 10 }}>
                    <span
                      style={{
                        display: 'grid',
                        placeItems: 'center',
                        flex: 'none',
                        width: 24,
                        height: 24,
                        borderRadius: 12,
                        fontSize: 12,
                        fontWeight: 600,
                        color: done ? '#fff' : palette.sub,
                        background: done ? palette.link : palette.inset,
                      }}
                    >
                      {i + 1}
                    </span>
                    <div>
                      <div
                        style={{
                          fontSize: 13,
                          fontWeight: 600,
                          color: palette.ink,
                        }}
                      >
                        {a}
                      </div>
                      <div style={{ fontSize: 12, color: palette.mute }}>
                        {b}
                      </div>
                    </div>
                  </div>
                </Col>
              );
            })}
          </Row>
        </>
      )}
    </Card>
  );

  // —— 模型输入框 ——
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
                      : subscription
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

  // —— 左：服务商与账号 ——
  const connectCard = (
    <Card style={{ height: '100%' }}>
      <SectionHead palette={palette} title="服务商与账号" />
      <Form.Item name="provider" label="服务商" required>
        <ChoiceGroup>
          {(value, select) => (
            <Row gutter={12}>
              {(['claude', 'openai'] as const).map((p) => (
                <Col span={12} key={p}>
                  <OptionCard
                    selected={(value ?? 'claude') === p}
                    disabled={!canEdit}
                    icon={<RobotOutlined />}
                    title={PROVIDERS[p].name}
                    desc={PROVIDERS[p].desc}
                    onSelect={() => select(p)}
                  />
                </Col>
              ))}
            </Row>
          )}
        </ChoiceGroup>
      </Form.Item>
      {config.subscriptionAllowed && (
        <Form.Item name="mode" label="运行方式">
          <ChoiceGroup>
            {(value, select) => (
              <Row gutter={12}>
                <Col span={12}>
                  <OptionCard
                    selected={value !== 'subscription'}
                    disabled={!canEdit}
                    icon={<KeyOutlined />}
                    title="API Key"
                    desc="按调用量计费，适合正式使用"
                    onSelect={() => select('api')}
                  />
                </Col>
                <Col span={12}>
                  <OptionCard
                    selected={value === 'subscription'}
                    disabled={!canEdit}
                    icon={<UserOutlined />}
                    title="个人订阅"
                    tag={<Pill tone="orange">仅平台</Pill>}
                    desc="使用平台登录的 ChatGPT / Claude 账号"
                    onSelect={() => select('subscription')}
                  />
                </Col>
              </Row>
            )}
          </ChoiceGroup>
        </Form.Item>
      )}

      {subscription ? (
        <SubscriptionBlock
          palette={palette}
          status={status}
          loading={statusLoading}
          onRefresh={loadStatus}
        />
      ) : config.hasKey && !changingKey ? (
        <Form.Item
          label={
            <FieldLabel
              title="API Key"
              hint="加密保存，只显示首尾几位；更换服务商时要一并更换"
            />
          }
          required
          validateStatus={keyTest && !keyTest.ok ? 'error' : undefined}
          help={
            keyTest && !keyTest.ok
              ? 'Key 无效或已失效，点「更换」粘贴新的 Key'
              : undefined
          }
        >
          <Input
            readOnly
            value={config.apiKeyMasked}
            prefix={<KeyOutlined style={{ color: palette.faint }} />}
            suffix={
              canEdit && (
                <Button
                  type="link"
                  size="small"
                  style={{ padding: 0, height: 'auto' }}
                  onClick={() => {
                    setChangingKey(true);
                    resetDerived();
                  }}
                >
                  更换
                </Button>
              )
            }
          />
        </Form.Item>
      ) : (
        <Form.Item
          name="apiKey"
          label={
            <FieldLabel
              title="API Key"
              hint={
                <>
                  加密保存。还没有 Key？
                  <a href={meta.console} target="_blank" rel="noreferrer">
                    去 {meta.consoleName} 控制台创建 <ExportOutlined />
                  </a>
                </>
              }
            />
          }
          required
        >
          {/* 普通文本框：Key 很长，粘贴后能看到内容便于核对；页面不回显已保存的 Key */}
          <Input
            autoComplete="off"
            spellCheck={false}
            autoFocus={changingKey}
            prefix={<KeyOutlined style={{ color: palette.faint }} />}
            placeholder={`粘贴 ${meta.name} 的 API Key`}
            suffix={
              changingKey && (
                <Button
                  type="link"
                  size="small"
                  style={{ padding: 0, height: 'auto' }}
                  onClick={() => {
                    form.setFieldValue('apiKey', '');
                    setChangingKey(false);
                    resetDerived();
                  }}
                >
                  取消更换
                </Button>
              )
            }
          />
        </Form.Item>
      )}
    </Card>
  );

  // —— 右：模型与用量 ——
  const modelCard = (
    <Card style={{ height: '100%' }}>
      <SectionHead palette={palette} title="模型与用量" />
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
        <Form.Item
          name="maxWebSearches"
          noStyle
          rules={[{ required: true, message: '请填写最多搜索次数' }]}
        >
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

  // —— 底部保存条 ——
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
            form.setFieldsValue({ ...saved, apiKey: '' });
            setChangingKey(false);
            resetDerived();
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
        description="询盘解析、型号描述翻译、联系人名音译使用的 AI 服务。只对本公司生效，平台和其他公司的配置互不影响。"
      />
      {statusCard}
      <Form
        form={form}
        layout="vertical"
        disabled={!canEdit}
        requiredMark
        onFinish={onFinish}
        onValuesChange={(changed) => {
          if (
            'provider' in changed ||
            'mode' in changed ||
            'apiKey' in changed
          ) {
            resetDerived();
          }
          if ('provider' in changed) {
            const next = (field: ModelField) =>
              modelAfterProviderChange(
                form.getFieldValue(
                  field === 'parse' ? 'parseModel' : 'translateModel',
                ),
                config.defaultModels,
                changed.provider ?? 'claude',
                field,
              );
            form.setFieldsValue({
              parseModel: next('parse'),
              translateModel: next('translate'),
            });
          }
        }}
      >
        <Row gutter={[16, 16]}>
          <Col xs={24} xl={12}>
            {connectCard}
          </Col>
          <Col xs={24} xl={12}>
            {modelCard}
          </Col>
        </Row>
        {saveBar}
      </Form>
    </>
  );
};

const SectionHead: React.FC<{ palette: Palette; title: string }> = ({
  palette,
  title,
}) => (
  <div
    style={{
      marginBottom: 20,
      fontSize: 16,
      fontWeight: 600,
      color: palette.ink,
    }}
  >
    {title}
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

/** 让卡片式单选接入 Form.Item（value / onChange） */
const ChoiceGroup: React.FC<{
  value?: string;
  onChange?: (v: string) => void;
  children: (
    value: string | undefined,
    select: (v: string) => void,
  ) => React.ReactNode;
}> = ({ value, onChange, children }) => (
  <div>{children(value, (v) => onChange?.(v))}</div>
);

const SubscriptionBlock: React.FC<{
  palette: Palette;
  status?: AiSubscriptionStatus;
  loading: boolean;
  onRefresh: () => void;
}> = ({ palette, status, loading, onRefresh }) => {
  const accounts = status
    ? [
        {
          name: 'ChatGPT 账号（OpenAI）',
          ok: status.codexLoggedIn,
          cmd: status.codexLoginCommand,
        },
        {
          name: 'Claude 账号',
          ok: status.claudeLoggedIn,
          cmd: status.claudeLoginCommand,
        },
      ]
    : [];
  return (
    <>
      <Form.Item
        label={
          <span
            style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}
          >
            账号登录状态
            <Button
              type="link"
              size="small"
              icon={<ReloadOutlined />}
              loading={loading}
              onClick={onRefresh}
              disabled={false}
            >
              刷新
            </Button>
          </span>
        }
      >
        {loading && !status ? (
          <Skeleton active paragraph={{ rows: 2 }} title={false} />
        ) : !status ? (
          <Typography.Text type="secondary">
            暂时查不到登录状态，请确认 AI 编排服务已启动后刷新
          </Typography.Text>
        ) : (
          <div
            style={{
              borderRadius: 10,
              border: `1px solid ${palette.hairline}`,
              overflow: 'hidden',
            }}
          >
            {accounts.map((a, i) => (
              <div
                key={a.name}
                style={{
                  padding: '12px 16px',
                  borderTop: i ? `1px solid ${palette.hairline}` : undefined,
                  background: a.ok ? undefined : palette.inset,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    fontSize: 13,
                    fontWeight: 500,
                    color: palette.ink,
                  }}
                >
                  {a.name}
                  {a.ok ? (
                    <Pill tone="green">已登录</Pill>
                  ) : (
                    <Pill tone="gray">未登录</Pill>
                  )}
                </div>
                {!a.ok && (
                  <div
                    style={{ marginTop: 8, fontSize: 12, color: palette.mute }}
                  >
                    需要时由运维在服务器上执行下面的命令登录，登录后点「刷新」：
                    <Typography.Paragraph
                      code
                      copyable
                      style={{ margin: '6px 0 0', fontSize: 12 }}
                    >
                      {a.cmd}
                    </Typography.Paragraph>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </Form.Item>
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
        个人订阅受账号套餐额度限制，同一时间只处理一个任务，适合演示。正式使用请切换到
        API Key。
      </div>
    </>
  );
};

export default AiSettingsPage;
