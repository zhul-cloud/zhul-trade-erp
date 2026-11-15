import { render, screen } from '@testing-library/react';
import React from 'react';
import { ITEM_PENDING, ITEM_PRICED } from '../shared/constants';
import type { InquiryItem } from '../shared/service';
import GoQuoteModal from './GoQuoteModal';

const item = (id: number, quoteStatus: number) =>
  ({
    id,
    brand: 'Siemens',
    confirmedModel: `M-${id}`,
    originalModel: `M-${id}`,
    quantity: 1,
    quoteStatus,
  }) as unknown as InquiryItem;

describe('询盘详情「去报价」选型号', () => {
  // audit UI-079：未回价型号报出去后采购不能再回价、任务完结，提示与实际规则一致
  it('勾未回价型号的提示说明发出后采购不能再回价', () => {
    render(
      <GoQuoteModal
        open
        title="去报价"
        items={[item(1, ITEM_PRICED), item(2, ITEM_PENDING)]}
        busy={false}
        onCancel={() => {}}
        onOk={() => {}}
      />,
    );
    expect(screen.getByText(/采购不能再回价/)).toBeTruthy();
    expect(screen.queryByText(/询价任务照常进行/)).toBeNull();
  });
});
