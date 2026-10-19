import React from 'react';
import { ACCEPT_PROOF } from '@/pages/finance/payables/components';
import {
  MediaTile,
  UploadTile,
  usePreview,
} from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';

/** 缩略图边长与间距：一行 3 个，附件区宽度随之固定 */
export const QUOTE_TILE = 44;
const GAP = 8;
const PER_ROW = 3;
export const QUOTE_ATTACH_WIDTH = PER_ROW * QUOTE_TILE + (PER_ROW - 1) * GAP;
/** 每条回价最多 5 个附件 */
export const QUOTE_MAX_FILES = 5;

/** 回价附件：44px 缩略图一行 3 个，满额不再显示上传；没有附件时是一个整宽的「添加附件」按钮 */
const QuoteAttachments: React.FC<{
  value: Attachment[];
  onChange: (list: Attachment[]) => void;
}> = ({ value, onChange }) => {
  const preview = usePreview();
  const upload = (label: string, width?: number) => (
    <UploadTile
      ownerType="SOURCING_QUOTE"
      accept={ACCEPT_PROOF}
      size={QUOTE_TILE}
      width={width}
      label={label}
      onUploaded={(a) => onChange([...value, a])}
    />
  );
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${PER_ROW}, ${QUOTE_TILE}px)`,
        gap: GAP,
        width: QUOTE_ATTACH_WIDTH,
      }}
    >
      {value.map((a) => (
        <MediaTile
          key={a.id}
          attachment={a}
          size={QUOTE_TILE}
          onOpen={() => preview.open(a)}
          onRemove={() => onChange(value.filter((x) => x.id !== a.id))}
        />
      ))}
      {value.length === 0 ? (
        <span style={{ gridColumn: `1 / span ${PER_ROW}` }}>
          {upload('添加附件', QUOTE_ATTACH_WIDTH)}
        </span>
      ) : (
        value.length < QUOTE_MAX_FILES && upload('')
      )}
      {preview.node}
    </div>
  );
};

export default QuoteAttachments;
