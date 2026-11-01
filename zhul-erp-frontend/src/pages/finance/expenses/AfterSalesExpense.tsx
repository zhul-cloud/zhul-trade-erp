import {
  App,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Space,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import type { AfterSales } from '@/pages/sales/after-sales/service';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { readBizError } from '@/pages/sales/service';
import { AttachmentWall } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { ACCEPT_PROOF } from '../payables/components';
import { METHOD, PAYEE } from '../payables/service';
import { expenseApi } from '../reimbursements/service';

/**
 * 售后单上登记费用（spec finance/expense「费用单关联售后单」）：补寄运费、维修费、检测费等，
 * 作为订单费用分摊到所选型号，关联这张售后单。复杂情况（多订单分摊、付给供应商）请到费用管理登记。
 */
const AfterSalesExpense: React.FC<{
  open: boolean;
  afterSales: AfterSales;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, afterSales, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const users = useUserOptions();
  const items = (afterSales.items ?? []).filter((i) => i.soItemId);
  const [name, setName] = useState('补寄运费');
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [payee, setPayee] = useState('');
  const [amount, setAmount] = useState<number | null>(null);
  const [method, setMethod] = useState<number>(METHOD.COMPANY);
  const [advanceUserId, setAdvanceUserId] = useState<number>();
  const [soItemId, setSoItemId] = useState<number>();
  const [remark, setRemark] = useState('');
  const [files, setFiles] = useState<Attachment[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName('补寄运费');
    setDate(dayjs());
    setPayee('');
    setAmount(null);
    setMethod(METHOD.COMPANY);
    setAdvanceUserId(undefined);
    setSoItemId(items[0]?.soItemId ?? undefined);
    setRemark('');
    setFiles([]);
  }, [open]);

  const label = (t: string, required = true) => (
    <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
      {required && <span style={{ color: palette.red }}>* </span>}
      {t}
    </div>
  );

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={600}
      title={`登记费用 · ${afterSales.asNo}`}
      okText="保存费用单"
      okButtonProps={{
        loading: busy,
        disabled: !name.trim() || !amount || !soItemId || !payee.trim(),
      }}
      onOk={async () => {
        if (!amount || !soItemId) return;
        setBusy(true);
        try {
          await expenseApi.create({
            name: name.trim(),
            category: 'ORDER',
            occurDate: date.format('YYYY-MM-DD'),
            payeeType: PAYEE.OTHER,
            payeeName: payee.trim(),
            currencyCode: 'CNY',
            amount,
            method,
            advanceUserId:
              method === METHOD.ADVANCE ? advanceUserId : undefined,
            remark: remark.trim() || undefined,
            attachmentIds: files.map((f) => f.id),
            allocations: [{ soItemId, amount }],
            afterSalesId: afterSales.id,
          });
          message.success('费用单已保存');
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}
        >
          <div>
            {label('费用名称')}
            <Input
              value={name}
              maxLength={50}
              onChange={(e) => setName(e.target.value)}
              aria-label="费用名称"
            />
          </div>
          <div>
            {label('发生日期')}
            <DatePicker
              value={date}
              allowClear={false}
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
              onChange={(d) => d && setDate(d)}
              style={{ width: '100%' }}
              aria-label="发生日期"
            />
          </div>
          <div>
            {label('收款方')}
            <Input
              value={payee}
              maxLength={50}
              onChange={(e) => setPayee(e.target.value)}
              placeholder="如 顺丰、当地维修商"
              aria-label="收款方"
            />
          </div>
          <div>
            {label('金额（CNY）')}
            <InputNumber<number>
              min={0.01}
              precision={2}
              value={amount}
              onChange={setAmount}
              style={{ width: '100%' }}
              aria-label="金额"
            />
          </div>
        </div>
        <div>
          {label('计入哪个型号的成本')}
          <Select
            style={{ width: '100%' }}
            value={soItemId}
            onChange={setSoItemId}
            options={items.map((i) => ({
              value: i.soItemId as number,
              label: `${afterSales.soNo} · ${i.model}`,
            }))}
            aria-label="分摊型号"
          />
        </div>
        <div>
          {label('付款方式')}
          <Segmented<number>
            value={method}
            onChange={setMethod}
            options={[
              { value: METHOD.COMPANY, label: '公司对公' },
              { value: METHOD.ADVANCE, label: '员工垫付' },
            ]}
          />
          {method === METHOD.ADVANCE && (
            <Select
              style={{ width: 220, marginLeft: 12 }}
              placeholder="垫付人"
              value={advanceUserId}
              onChange={setAdvanceUserId}
              options={users}
              aria-label="垫付人"
            />
          )}
        </div>
        <div>
          {label('凭证', method === METHOD.ADVANCE)}
          <AttachmentWall
            ownerType="EXPENSE"
            value={files}
            onChange={setFiles}
            max={10}
            accept={ACCEPT_PROOF}
          />
        </div>
        <Input
          value={remark}
          maxLength={500}
          onChange={(e) => setRemark(e.target.value)}
          placeholder="说明（选填）"
          aria-label="说明"
        />
        <div style={{ fontSize: 12, color: palette.mute }}>
          费用计入原订单成本，售后单汇总显示；要分摊到多张订单或付给供应商，请到「财务管理
          → 费用管理」登记并选择这张售后单。
        </div>
      </Space>
    </Modal>
  );
};

export default AfterSalesExpense;
