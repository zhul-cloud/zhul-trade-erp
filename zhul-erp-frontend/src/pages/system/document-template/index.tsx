// biome-ignore-all lint/suspicious/noTemplateCurlyInString: 占位符说明里的 ${…} 是给管理员看的模版占位符，不是模板字符串
import {
  BookOutlined,
  CloseCircleOutlined,
  DownloadOutlined,
  FileExcelOutlined,
  InfoCircleOutlined,
  StarOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Drawer,
  Input,
  Modal,
  Skeleton,
  Space,
  Table,
  Tooltip,
} from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { readBizError } from '@/pages/crm/opportunity/service';
import {
  Card,
  PageTitle,
  Pill,
  useWide,
} from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  TEXT_QUOTE,
  type TemplateType,
  type TemplateVersion,
  templateApi,
} from './service';

const PLACEHOLDERS: { group: string; rows: [string, string][] }[] = [
  {
    group: '表头',
    rows: [
      ['${quotation.no}', '报价单编号'],
      ['${quotation.date}', '报价日期'],
      ['${quotation.validUntil}', '有效期至'],
      ['${quotation.currency}', '币种代码，如 USD'],
      ['${quotation.currencySymbol}', '币种符号，如 $'],
      ['${quotation.incoterm}', '贸易术语与地点，如 FOB Shanghai'],
      ['${quotation.remark}', '备注'],
      [
        '${quotation.itemTotal} / ${quotation.feeTotal} / ${quotation.total}',
        '型号小计 / 费用 / 合计（数字）',
      ],
      [
        '${customer.name} / ${customer.contact} / ${customer.country}',
        '客户名称 / 联系人 / 国家',
      ],
      [
        '${customer.address} / ${customer.email} / ${customer.phone}',
        '客户地址 / 邮箱 / 电话',
      ],
      [
        '${seller.name} / ${seller.email} / ${seller.phone}',
        '业务员姓名 / 邮箱 / 电话',
      ],
    ],
  },
  {
    group: '明细行（整行写在一行里，导出时按型号数展开）',
    rows: [
      ['${item.no}', '序号'],
      [
        '${item.model} / ${item.brand} / ${item.category}',
        '型号 / 品牌 / 品类',
      ],
      ['${item.description}', '描述（英文）'],
      ['${item.condition} / ${item.conditionEn}', '货况（中文 / 英文）'],
      ['${item.leadTime} / ${item.leadTimeEn}', '货期（中文 / 英文）'],
      ['${item.warranty}', '质保'],
      ['${item.qty}', '数量（数字）'],
      [
        '${item.unitPrice} / ${item.unitPriceShort}',
        '单价（数字 / 去掉末尾 0 的文字）',
      ],
      ['${item.amount}', '小计（数字）'],
    ],
  },
  {
    group: '费用行（可选，整行写在一行里）',
    rows: [
      ['${fee.no}', '序号（接着型号行编号，数字）'],
      ['${fee.name}', '费用名称'],
      ['${fee.amount}', '金额（数字）'],
    ],
  },
  {
    group: '文字报价',
    rows: [
      ['{{#items}} … {{/items}}', '中间的内容按型号逐行重复'],
      ['{{#fees}} … {{/fees}}', '中间的内容按费用逐行重复'],
    ],
  },
];

/** PI 模版（单据类型 2）的占位符：明细与费用的通用字段同报价单 */
const PI_PLACEHOLDERS: { group: string; rows: [string, string][] }[] = [
  {
    group: 'PI 表头',
    rows: [
      ['${pi.no} / ${pi.date}', 'PI 编号 / 日期（单据上不显示版本号）'],
      ['${pi.currency} / ${pi.currencySymbol}', '币种代码 / 符号'],
      ['${pi.deliveryTime} / ${pi.paymentTerm}', '交期 / 付款条件'],
      ['${pi.incoterm} / ${pi.portOfShipment}', '贸易术语与地点 / 起运港'],
      ['${pi.remark}', '备注'],
      [
        '${pi.itemTotal} / ${pi.feeTotal} / ${pi.discount} / ${pi.total}',
        '型号小计 / 费用 / 折扣（负数）/ 合计（数字）',
      ],
      [
        '${buyer.name} / ${buyer.address} / ${buyer.country} / ${buyer.taxId}',
        '买方（发票抬头）名称 / 地址 / 国家 / 税号',
      ],
      [
        '${buyer.contact} / ${buyer.phone} / ${buyer.email}',
        '买方联系人 / 电话 / 邮箱',
      ],
      [
        '${consignee.name} / ${consignee.address} / ${consignee.country}',
        '收货人名称 / 地址 / 国家',
      ],
      ['${consignee.contact} / ${consignee.phone}', '收货人联系人 / 电话'],
      [
        '${seller.name} / ${seller.email} / ${seller.phone}',
        '业务员姓名 / 邮箱 / 电话',
      ],
      [
        '${bank.name} / ${bank.accountName} / ${bank.accountNo}',
        '收款银行 / 账户名称 / 账号（取 PI 上选的收款账户）',
      ],
      [
        '${bank.swift} / ${bank.country} / ${bank.address}',
        'SWIFT Code / 国家地区 / 银行地址',
      ],
      ['${bank.bankCode} / ${bank.branchCode}', 'Bank Code / Branch Code'],
    ],
  },
  {
    group: 'PI 明细行（另有报价单明细的全部字段）',
    rows: [
      ['${item.hsCode} / ${item.origin}', 'HS 编码 / 原产国'],
      ['${item.remark}', '型号备注'],
    ],
  },
  {
    group: 'PI 费用行（运费、手续费；整单折扣作为一行负数「Discount」输出）',
    rows: [
      [
        '${fee.no} / ${fee.name} / ${fee.amount} / ${fee.remark}',
        '序号（接着型号行编号）/ 费用名称 / 金额（数字）/ 备注',
      ],
    ],
  },
];

const DocumentTemplatePage: React.FC = () => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['system:document-template:edit'];
  const [types, setTypes] = useState<TemplateType[]>();
  const [docType, setDocType] = useState(1);
  const [versions, setVersions] = useState<TemplateVersion[]>();
  const [error, setError] = useState<string>();
  const [helpOpen, setHelpOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [file, setFile] = useState<File>();
  const [note, setNote] = useState('');
  const [problems, setProblems] = useState<string[]>([]);
  const [content, setContent] = useState('');
  const [busy, setBusy] = useState(false);
  const [textPreview, setTextPreview] = useState<string>();
  const fileInput = useRef<HTMLInputElement>(null);

  const current = types?.find((t) => t.docType === docType);
  const isText = docType === TEXT_QUOTE;

  const loadTypes = useCallback(() => {
    templateApi
      .types()
      .then(setTypes)
      .catch((e) => setError(readBizError(e).message));
  }, []);

  const loadVersions = useCallback(() => {
    setVersions(undefined);
    templateApi
      .versions(docType)
      .then(setVersions)
      .catch((e) => setError(readBizError(e).message));
  }, [docType]);

  useEffect(loadTypes, [loadTypes]);
  useEffect(loadVersions, [loadVersions]);

  const refresh = () => {
    loadTypes();
    loadVersions();
  };

  const openUpload = () => {
    setFile(undefined);
    setNote('');
    setProblems([]);
    const def = versions?.find((v) => v.isDefault);
    setContent(isText ? (def?.content ?? '') : '');
    setUploadOpen(true);
  };

  const nextNo =
    (versions ?? []).reduce((m, v) => Math.max(m, v.versionNo), 0) + 1;

  const submit = async () => {
    if (!note.trim()) {
      setProblems(['请填写版本说明']);
      return;
    }
    setBusy(true);
    setProblems([]);
    try {
      const v = isText
        ? await templateApi.saveText(content, note.trim())
        : await templateApi.upload(docType, file as File, note.trim());
      message.success(
        `已保存为 V${v.versionNo}，设为默认后业务员导出才会使用它`,
      );
      if (v.warnings?.length) {
        modal.info({
          title: '已保存，有一点提醒',
          content: v.warnings.join('\n'),
        });
      }
      setUploadOpen(false);
      refresh();
    } catch (e) {
      const err = readBizError(e);
      const detail = err.detail as unknown;
      setProblems(
        Array.isArray(detail)
          ? [err.message, ...(detail as string[])]
          : [err.message],
      );
    } finally {
      setBusy(false);
    }
  };

  const setDefault = (v: TemplateVersion) => {
    const old = versions?.find((x) => x.isDefault);
    modal.confirm({
      title: `把 V${v.versionNo} 设为默认版本？`,
      icon: <StarOutlined style={{ color: palette.orange }} />,
      content: `设为默认后，所有业务员导出的${current?.name.split(' ')[0] ?? '单据'}都使用 V${v.versionNo}${old ? `（原默认 V${old.versionNo}）` : ''}。本次切换会记入操作日志。建议先「预览导出」确认效果。`,
      okText: '设为默认',
      onOk: async () => {
        try {
          await templateApi.setDefault(v.id);
          message.success(`已把 V${v.versionNo} 设为默认`);
          refresh();
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });
  };

  const toggle = async (v: TemplateVersion) => {
    try {
      await templateApi.setEnabled(v.id, !v.enabled);
      message.success(
        v.enabled ? `已停用 V${v.versionNo}` : `已启用 V${v.versionNo}`,
      );
      refresh();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const preview = async (v: TemplateVersion) => {
    try {
      if (isText) {
        setTextPreview((await templateApi.preview(v.id, 'text')) as string);
      } else {
        await templateApi.preview(v.id, 'pdf');
      }
    } catch (e) {
      message.error((e as Error).message || readBizError(e).message);
    }
  };

  if (error) return <ErrorHint message={error} onRetry={refresh} />;

  const builtin = versions?.find((v) => v.builtin);

  return (
    <div>
      <PageTitle
        crumbs={['单据模版']}
        title="单据模版"
        description="报价单、PI、CI、PL 与文字报价统一管理；每类单据只有一个默认版本，所有业务员导出都用它。"
        actions={
          <>
            <Button icon={<BookOutlined />} onClick={() => setHelpOpen(true)}>
              占位符说明
            </Button>
            {!isText && builtin && (
              <Button
                icon={<DownloadOutlined />}
                onClick={() =>
                  templateApi
                    .download(builtin.id)
                    .catch((e) => message.error(e.message))
                }
              >
                下载示例模版
              </Button>
            )}
            {canEdit && (
              <Button
                type="primary"
                icon={<UploadOutlined />}
                onClick={openUpload}
              >
                {isText ? '编辑新版本' : '上传新版本'}
              </Button>
            )}
          </>
        }
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide ? '320px minmax(0, 1fr)' : 'minmax(0, 1fr)',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <div style={{ display: 'grid', gap: 10 }}>
          {!types && <Skeleton active />}
          {types?.map((t) => {
            const active = t.docType === docType;
            return (
              <button
                key={t.docType}
                type="button"
                onClick={() => setDocType(t.docType)}
                style={{
                  all: 'unset',
                  cursor: 'pointer',
                  padding: '14px 16px',
                  borderRadius: 14,
                  background: active ? palette.accentSoft : palette.card,
                  border: `1px solid ${active ? palette.accentLine : palette.hairline}`,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                <span style={{ flex: 1 }}>
                  <span
                    style={{
                      display: 'block',
                      fontWeight: 600,
                      color: active ? palette.link : palette.ink,
                    }}
                  >
                    {t.name}
                  </span>
                  <span style={{ fontSize: 12, color: palette.mute }}>
                    默认 V{t.defaultVersionNo ?? '—'} · {t.versionCount} 个版本
                  </span>
                </span>
                {!t.generatable && (
                  <span style={{ fontSize: 12, color: palette.sub }}>
                    生成随发货模块上线
                  </span>
                )}
              </button>
            );
          })}
        </div>
        <div>
          <Card style={{ padding: 0, overflow: 'hidden' }}>
            {!versions ? (
              <Skeleton active style={{ padding: 24 }} />
            ) : (
              <Table<TemplateVersion>
                rowKey="id"
                pagination={false}
                dataSource={versions}
                onRow={(v) =>
                  v.isDefault
                    ? { style: { background: palette.accentSoft } }
                    : {}
                }
                columns={[
                  {
                    title: '版本',
                    key: 'v',
                    width: 70,
                    render: (_, v) => <b>V{v.versionNo}</b>,
                  },
                  {
                    title: '状态',
                    key: 's',
                    width: 90,
                    render: (_, v) =>
                      v.isDefault ? (
                        <Pill tone="green" dot>
                          默认
                        </Pill>
                      ) : !v.enabled ? (
                        <Pill tone="mute">已停用</Pill>
                      ) : null,
                  },
                  {
                    title: '版本说明',
                    key: 'n',
                    render: (_, v) => (
                      <span
                        style={{ color: v.enabled ? palette.ink : palette.sub }}
                      >
                        {v.note}
                        {v.fileName && !v.builtin && (
                          <span style={{ color: palette.mute, fontSize: 12 }}>
                            {' '}
                            · {v.fileName}
                          </span>
                        )}
                      </span>
                    ),
                  },
                  {
                    title: '上传',
                    key: 'u',
                    width: 220,
                    render: (_, v) => (
                      <span style={{ color: palette.sub }}>
                        {v.uploadedByName ?? '—'} ·{' '}
                        {formatDateTime(v.createTime).slice(0, 16)}
                      </span>
                    ),
                  },
                  {
                    title: '操作',
                    key: 'a',
                    width: 260,
                    render: (_, v) => (
                      <Space size={12}>
                        {current?.generatable ? (
                          <a onClick={() => preview(v)}>预览导出</a>
                        ) : (
                          <Tooltip title="生成功能随发货模块上线，暂时只能下载模版查看">
                            <span style={{ color: palette.mute }}>
                              预览导出
                            </span>
                          </Tooltip>
                        )}
                        <a
                          onClick={() =>
                            isText
                              ? setTextPreview(v.content ?? '')
                              : templateApi
                                  .download(v.id)
                                  .catch((e) => message.error(e.message))
                          }
                        >
                          {isText ? '查看' : '下载'}
                        </a>
                        {canEdit && !v.isDefault && v.enabled && (
                          <a onClick={() => setDefault(v)}>设为默认</a>
                        )}
                        {canEdit && !v.isDefault && (
                          <a
                            style={{
                              color: v.enabled ? palette.sub : palette.link,
                            }}
                            onClick={() => toggle(v)}
                          >
                            {v.enabled ? '停用' : '启用'}
                          </a>
                        )}
                      </Space>
                    ),
                  },
                ]}
              />
            )}
          </Card>
          <div
            style={{
              marginTop: 12,
              padding: '10px 14px',
              borderRadius: 10,
              background: palette.inset,
              color: palette.sub,
              fontSize: 13,
            }}
          >
            <InfoCircleOutlined />{' '}
            版本保存后不能修改，要调整就上传新版本；设为默认前可以先「预览导出」用示例数据看效果。默认版本不能停用。
          </div>
        </div>
      </div>

      <Modal
        open={uploadOpen}
        title={
          isText
            ? '文字报价模版新版本'
            : `上传${current?.name.split(' ')[0] ?? ''}模版新版本`
        }
        onCancel={() => setUploadOpen(false)}
        onOk={submit}
        okText={`保存为 V${nextNo}`}
        okButtonProps={{ disabled: isText ? !content.trim() : !file }}
        confirmLoading={busy}
        width={600}
      >
        {isText ? (
          <>
            <div style={{ marginBottom: 6, color: palette.sub }}>模版内容</div>
            <Input.TextArea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              autoSize={{ minRows: 4, maxRows: 12 }}
              maxLength={2000}
              style={{
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                fontSize: 13,
              }}
              aria-label="文字报价模版内容"
            />
          </>
        ) : (
          <>
            <input
              ref={fileInput}
              type="file"
              accept=".xlsx"
              style={{ display: 'none' }}
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f && f.size > 5 * 1024 * 1024) {
                  setProblems(['模版文件不能超过 5MB']);
                } else {
                  setFile(f);
                  setProblems([]);
                }
                e.target.value = '';
              }}
            />
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              style={{
                all: 'unset',
                boxSizing: 'border-box',
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '14px 16px',
                borderRadius: 12,
                cursor: 'pointer',
                background: palette.inset,
                border: `1px dashed ${palette.control}`,
              }}
            >
              <FileExcelOutlined
                style={{ fontSize: 20, color: palette.green }}
              />
              <span style={{ flex: 1 }}>
                <span
                  style={{
                    display: 'block',
                    fontWeight: 600,
                    color: palette.ink,
                  }}
                >
                  {file ? file.name : '选择 Excel 模版（.xlsx，不超过 5MB）'}
                </span>
                {file && (
                  <span style={{ fontSize: 12, color: palette.mute }}>
                    {Math.ceil(file.size / 1024)} KB
                  </span>
                )}
              </span>
              <span style={{ color: palette.link }}>
                {file ? '重新选择' : '选择文件'}
              </span>
            </button>
          </>
        )}
        {problems.length > 0 && (
          <Alert
            style={{ marginTop: 12 }}
            type="error"
            showIcon
            icon={<CloseCircleOutlined />}
            title={problems[0]}
            description={
              problems.length > 1
                ? problems.slice(1).map((p) => <div key={p}>· {p}</div>)
                : undefined
            }
          />
        )}
        <div style={{ margin: '16px 0 6px', color: palette.sub }}>
          版本说明 <span style={{ color: palette.red }}>*</span>
        </div>
        <Input
          value={note}
          maxLength={200}
          onChange={(e) => setNote(e.target.value)}
          placeholder="说明这一版改了什么，如「更换公司新地址」"
          aria-label="版本说明"
        />
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
          看不懂占位符？打开「
          <a onClick={() => setHelpOpen(true)}>占位符说明</a>
          」，或下载示例模版照着改。
        </div>
      </Modal>

      <Modal
        open={textPreview != null}
        title="文字报价（示例数据）"
        footer={null}
        onCancel={() => setTextPreview(undefined)}
        width={640}
      >
        <pre
          style={{
            whiteSpace: 'pre-wrap',
            fontSize: 13,
            background: palette.inset,
            padding: 12,
            borderRadius: 10,
            margin: 0,
          }}
        >
          {textPreview}
        </pre>
      </Modal>

      <Drawer
        open={helpOpen}
        onClose={() => setHelpOpen(false)}
        title="占位符说明"
        size={560}
      >
        <p style={{ color: palette.sub }}>
          在 Excel
          单元格里写上占位符，导出时替换成报价单内容。整格只有一个数字类占位符时会写成数字，公式可以正常计算；明细行和费用行各只能有一行，合计公式写成只引用这一行（如
          =SUM(K11:K11)），导出时会自动扩展到全部行。
        </p>
        {(docType === 2
          ? [...PI_PLACEHOLDERS, ...PLACEHOLDERS.slice(1, 3)]
          : PLACEHOLDERS
        ).map((g) => (
          <div key={g.group} style={{ marginBottom: 16 }}>
            <b style={{ color: palette.ink }}>{g.group}</b>
            {g.rows.map(([k, v]) => (
              <div
                key={k}
                style={{
                  display: 'flex',
                  gap: 12,
                  padding: '6px 0',
                  borderBottom: `1px solid ${palette.hairline}`,
                }}
              >
                <code style={{ flex: 1, color: palette.link, fontSize: 12 }}>
                  {k}
                </code>
                <span style={{ width: 200, color: palette.sub, fontSize: 13 }}>
                  {v}
                </span>
              </div>
            ))}
          </div>
        ))}
      </Drawer>
    </div>
  );
};

export default DocumentTemplatePage;
