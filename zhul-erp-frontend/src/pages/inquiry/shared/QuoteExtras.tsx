import React from 'react';
import { Pill } from '@/pages/crm/opportunity/components';
import { ProofLinks } from '@/pages/finance/payables/components';
import type { QuoteFile } from './service';

/** 回价的「不包邮」标记与附件（截图、报价单）；包邮且没有附件时不显示 */
const QuoteExtras: React.FC<{
  quote: {
    noStock?: boolean;
    freeShipping?: boolean;
    attachments?: QuoteFile[];
  };
}> = ({ quote: q }) => {
  if (q.noStock) return null;
  const files = q.attachments ?? [];
  if (q.freeShipping !== false && files.length === 0) return null;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      {q.freeShipping === false && <Pill tone="orange">不包邮</Pill>}
      {files.length > 0 && <ProofLinks files={files} />}
    </span>
  );
};

export default QuoteExtras;
