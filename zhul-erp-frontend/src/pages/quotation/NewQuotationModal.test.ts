import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import NewQuotationModal from './NewQuotationModal';
import { quotationApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(() => Promise.resolve({ data: [] })),
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  quotationApi: {
    quoteInquiries: jest.fn(),
    readyInquiries: jest.fn(),
    batchCreate: jest.fn(),
  },
}));

const inquiry = (id: number) => ({
  inquiryId: id,
  inquiryCode: `INQ-${id}`,
  customerId: 7,
  customerName: 'ACROBOT',
  customerType: 1,
  inquiryDate: '2026-11-15',
  quoteDeadline: '2026-11-30',
  urgent: false,
  level: 1,
  status: 6,
  itemCount: 1,
  pricedCount: 1,
});

describe('新建报价单：批量生成', () => {
  beforeEach(() => {
    (quotationApi.quoteInquiries as jest.Mock).mockResolvedValue({
      total: 2,
      records: [inquiry(1), inquiry(2)],
      readyCount: 2,
      sourcingCount: 0,
    });
    (quotationApi.readyInquiries as jest.Mock).mockResolvedValue([]);
    (quotationApi.batchCreate as jest.Mock).mockResolvedValue({
      created: [
        {
          quotationId: 11,
          quotationNo: 'QT-11',
          inquiryId: 1,
          inquiryCode: 'INQ-1',
          itemCount: 1,
          totalAmount: 100,
          currencyCode: 'USD',
        },
      ],
      skipped: [],
    });
  });

  // audit UI-080：结果页用右上角关闭，也要刷新列表
  it('生成后用右上角关闭也刷新列表', async () => {
    const onClose = jest.fn();
    const onBatchDone = jest.fn();
    // 有 jest.mock 时 JSX 编译后找不到 React，与其他测试一样用 createElement
    render(
      React.createElement(
        App,
        null,
        React.createElement(NewQuotationModal, {
          open: true,
          onClose,
          onCreated: () => {},
          onBatchDone,
        }),
      ),
    );
    fireEvent.click(screen.getByText('下一步'));
    await screen.findByText('INQ-1', {}, { timeout: 3000 });
    for (const label of screen.getAllByRole('checkbox')) {
      fireEvent.click(label);
    }
    fireEvent.click(await screen.findByText('生成 2 张报价单'));
    await screen.findByText('QT-11');
    fireEvent.click(screen.getByRole('button', { name: /close/i }));
    await waitFor(() => expect(onBatchDone).toHaveBeenCalledTimes(1));
    expect(onClose).toHaveBeenCalled();
  });
});
