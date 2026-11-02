import { MoreOutlined } from '@ant-design/icons';
import { App, Button, Dropdown, type MenuProps } from 'antd';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { Card, Pill } from '../shared/components';
import { FileTile, useFilePreview } from '../shared/FilePreview';
import type { PreviewFile } from '../shared/files';
import {
  type InquiryAttachment,
  type InquiryDetail,
  inquiryApi,
  readBizError,
} from '../shared/service';

const UNMATCHED = '0';
const DELETE = 'delete';

/**
 * 询盘单详情的「铭牌图片」（spec inquiry/inquiry-intake「铭牌图片」）：按型号分组，没对上的放在「未对应型号」；
 * 能修改询盘时可以改对应的型号或删除（删除走修改询盘，软删除）。
 */
const NameplateCard: React.FC<{
  detail: InquiryDetail;
  editable: boolean;
  onChanged: () => void;
}> = ({ detail, editable, onChanged }) => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const preview = useFilePreview();
  const inq = detail.inquiry;
  const plates = detail.nameplates ?? [];
  if (plates.length === 0) return null;

  const endpointOf = (f: PreviewFile) =>
    inquiryApi.attachmentEndpoint(inq.id, f.id);
  const itemLabel = (itemId?: number) => {
    const it = detail.items.find((i) => i.id === itemId);
    return it ? `${it.lineNo} · ${it.confirmedModel}` : '未对应型号';
  };
  const ownerOf = (f: InquiryAttachment) =>
    detail.items.some((i) => i.id === f.inquiryItemId)
      ? (f.inquiryItemId as number)
      : 0;

  const relink = async (f: InquiryAttachment, itemId: number) => {
    try {
      await inquiryApi.linkNameplate(inq.id, f.id, itemId);
      message.success(
        itemId ? `已改为对应 ${itemLabel(itemId)}` : '已改为未对应型号',
      );
      onChanged();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const remove = (f: InquiryAttachment) =>
    modal.confirm({
      title: `删除铭牌图片「${f.fileName}」？`,
      content: '删除后采购也看不到这张图，并记一条修改询盘的操作日志。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await inquiryApi.update(inq.id, {
            source: inq.source,
            inquiryDate: inq.inquiryDate,
            quoteDeadline: inq.quoteDeadline,
            urgent: inq.urgent,
            opportunityId: inq.opportunityId || undefined,
            keepAttachmentIds: [...detail.attachments, ...plates]
              .filter((a) => a.id !== f.id)
              .map((a) => a.id),
            attachments: [],
            remark: detail.remark || undefined,
          });
          message.success('已删除');
          onChanged();
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });

  const menuOf = (f: InquiryAttachment): MenuProps => ({
    selectable: true,
    selectedKeys: [String(ownerOf(f))],
    items: [
      {
        type: 'group',
        label: '改对应到',
        children: [
          ...detail.items.map((i) => ({
            key: String(i.id),
            label: `${i.lineNo} · ${i.confirmedModel}`,
          })),
          { key: UNMATCHED, label: '未对应型号' },
        ],
      },
      { type: 'divider' },
      { key: DELETE, label: '删除这张图', danger: true },
    ],
    onClick: ({ key }) => {
      if (key === DELETE) remove(f);
      else if (Number(key) !== ownerOf(f)) relink(f, Number(key));
    },
  });

  const groups = [
    ...detail.items
      .map((i) => ({
        key: i.id,
        title: `${i.lineNo} · ${i.confirmedModel}`,
        sub: [i.brand, i.category].filter(Boolean).join(' · '),
        files: plates.filter((p) => p.inquiryItemId === i.id),
      }))
      .filter((g) => g.files.length > 0),
    {
      key: 0,
      title: '未对应型号',
      sub: '采购会在任务顶部看到',
      files: plates.filter((p) => ownerOf(p) === 0),
    },
  ].filter((g) => g.files.length > 0);

  return (
    <Card
      title={`铭牌图片 ${plates.length}`}
      extra={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          {editable && (
            <span style={{ fontSize: 12, color: palette.mute }}>
              点图片右下角的「···」改对应型号
            </span>
          )}
          <Pill tone="accent">采购能看到</Pill>
        </span>
      }
    >
      <div style={{ display: 'grid', gap: 12 }}>
        {groups.map((g) => {
          const warn = g.key === 0;
          return (
            <div
              key={g.key}
              style={{
                display: 'grid',
                gap: 10,
                padding: '12px 14px',
                borderRadius: 12,
                background: warn ? palette.orangeSoft : palette.inset,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                <b style={{ color: warn ? palette.orange : palette.ink }}>
                  {g.title}
                </b>
                <span style={{ fontSize: 12, color: palette.mute }}>
                  {g.sub}
                </span>
              </div>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                {g.files.map((f) => (
                  <div key={f.id} style={{ position: 'relative' }}>
                    <FileTile
                      file={f}
                      endpoint={endpointOf(f)}
                      onOpen={() =>
                        preview.open(
                          plates,
                          plates.indexOf(f),
                          endpointOf,
                          (x) =>
                            `铭牌图片 · ${itemLabel(ownerOf(x as InquiryAttachment) || undefined)}`,
                        )
                      }
                    />
                    {editable && (
                      <Dropdown menu={menuOf(f)} trigger={['click']}>
                        <Button
                          size="small"
                          shape="circle"
                          icon={<MoreOutlined />}
                          aria-label={`改 ${f.fileName} 对应的型号`}
                          style={{ position: 'absolute', right: 4, top: 46 }}
                        />
                      </Dropdown>
                    )}
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
      {preview.node}
    </Card>
  );
};

export default NameplateCard;
