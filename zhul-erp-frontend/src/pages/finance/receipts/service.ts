import { request } from '@umijs/max';
import type { ReceiptRow } from '@/pages/sales/service';
import type { Attachment, DocLog, DocRef } from '@/pages/warehouse/service';
import { openFile } from '@/utils/fileLink';

const FIN = '/api/v1/finance/receipts';
const quiet = { skipErrorHandler: true } as const;

const unwrap = <T>(p: Promise<{ data: T }>) => p.then((r) => r.data);

export interface ReceiptMoney {
  currencyCode: string;
  amount: number;
}

export interface ReceiptSummary {
  /** 1-线下、2-线上、0-合计 */
  channel: number;
  count: number;
  amounts: ReceiptMoney[];
  fees: ReceiptMoney[];
  netAmountCny: number;
  /** 其中还没结完汇的到账（含估算部分） */
  openNetAmountCny?: number;
}

export interface ReceiptRecordQuery {
  keyword?: string;
  dateFrom?: string;
  dateTo?: string;
  paymentMethod?: string;
  channel?: number;
  currencyCode?: string;
  /** 结汇状态：1-未结汇、2-部分结汇、3-已结汇 */
  settleStatus?: number;
  includeVoid?: boolean;
  /** 所属事业部 */
  businessUnitId?: number;
  /** receiptDate 按到账日期倒序；默认按更新时间倒序 */
  sort?: string;
  page: number;
  pageSize: number;
}

/** 财务管理 → 收款单：未认领到账与收款记录 */
export const ledgerApi = {
  unclaimed: (
    keyword?: string,
    currencyCode?: string,
    businessUnitId?: number,
  ) =>
    unwrap(
      request<{
        data: { unclaimed: ReceiptRow[]; recentClaimed: ReceiptRow[] };
      }>(`${FIN}/unclaimed`, {
        params: { keyword, currencyCode, businessUnitId },
        ...quiet,
      }),
    ),
  registerUnclaimed: (body: {
    currencyCode: string;
    amount: number;
    receiptDate: string;
    bankAccountId: number;
    paymentMethod?: string;
    payer?: string;
    note?: string;
  }) =>
    unwrap(
      request<{ data: ReceiptRow }>(`${FIN}/unclaimed`, {
        method: 'POST',
        data: body,
        ...quiet,
      }),
    ),
  /** 给未认领到账补附件（合计最多 5 个） */
  addUnclaimedFiles: (id: number, files: File[]) => {
    const form = new FormData();
    for (const f of files) form.append('files', f);
    return unwrap(
      request<{ data: ReceiptRow }>(`${FIN}/unclaimed/${id}/files`, {
        method: 'POST',
        data: form,
        requestType: 'form',
        ...quiet,
      }),
    );
  },
  openFile: (id: number, index: number) =>
    openFile(`${FIN}/${id}/files/${index}`),
  unclaim: (id: number, reason: string) =>
    unwrap(
      request<{ data: null }>(`${FIN}/${id}/unclaim`, {
        method: 'POST',
        data: { reason },
        ...quiet,
      }),
    ),
  voidUnclaimed: (id: number, reason: string) =>
    unwrap(
      request<{ data: null }>(`${FIN}/${id}/void`, {
        method: 'POST',
        data: { reason },
        ...quiet,
      }),
    ),
  records: (q: ReceiptRecordQuery) =>
    unwrap(
      request<{
        data: {
          total: number;
          records: ReceiptRow[];
          summary: ReceiptSummary[];
        };
      }>(`${FIN}/records`, { method: 'POST', data: q, ...quiet }),
    ),
};

// ---------------------------------------------------------------- 收款单详情（change finance-cash-docs）

export interface ReceiptDoc {
  receipt: ReceiptRow;
  /** 登记时的到账凭证与对应水单的文件（只读） */
  files: { index: number; fileName: string; source: 'RECEIPT' | 'SLIP' }[];
  attachments: Attachment[];
  /** 外币收款单的结汇分摊 */
  settlements: {
    settlementId: number;
    settleNo: string;
    settleDate: string;
    amount: number;
    exchangeRate: number;
    amountCny: number;
  }[];
  unsettledAmount?: number;
  unsettledAmountCny?: number;
  related?: DocRef[];
  logs?: DocLog[];
  editable: boolean;
}

export const receiptDocApi = {
  detail: (id: number) =>
    unwrap(request<{ data: ReceiptDoc }>(`${FIN}/${id}`, quiet)),
  remark: (id: number, remark: string) =>
    unwrap(
      request<{ data: ReceiptDoc }>(`${FIN}/${id}/remark`, {
        method: 'POST',
        data: { remark },
        ...quiet,
      }),
    ),
  attach: (id: number, ids: number[]) =>
    unwrap(
      request<{ data: ReceiptDoc }>(`${FIN}/${id}/attachments`, {
        method: 'POST',
        data: { ids },
        ...quiet,
      }),
    ),
  removeAttachment: (id: number, attachmentId: number) =>
    unwrap(
      request<{ data: ReceiptDoc }>(
        `${FIN}/${id}/attachments/${attachmentId}`,
        { method: 'DELETE', ...quiet },
      ),
    ),
  openFile: (id: number, index: number) =>
    openFile(`${FIN}/${id}/doc-files/${index}`),
};
