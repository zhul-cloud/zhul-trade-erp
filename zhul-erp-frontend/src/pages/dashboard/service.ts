import { request } from '@umijs/max';
import type { MyTask } from '@/pages/inquiry/shared/service';

const API = '/api/v1/workbench';
const quiet = { skipErrorHandler: true } as const;

const get = <T>(url: string) =>
  request<{ data: T }>(url, { method: 'GET', ...quiet }).then((r) => r.data);

export interface WorkbenchWidget {
  key: string;
  /** full-整行、half-半行 */
  size: 'full' | 'half';
}

export interface WorkbenchBoard {
  code: string;
  name: string;
  /** 这个岗位看板的组件还没上线或都没有权限，先显示通用看板的组件 */
  fallback: boolean;
  widgets: WorkbenchWidget[];
}

export interface Workbench {
  boards: WorkbenchBoard[];
  defaultCode?: string;
}

export interface WorkbenchTemplate {
  code: string;
  name: string;
  description: string;
}

/** 本人询价统计（采购员看板，原兼职看板） */
export interface QuoteBoard {
  pendingTasks: number;
  timeoutTasks: number;
  urgentTasks: number;
  dueTodayTasks: number;
  returnedItems: number;
  monthQuotedItems: number;
  monthQuotes: number;
  monthNoStock: number;
  monthCompletedTasks: number;
  /** 本月平均回价用时（小时），本月没有提交为空 */
  monthAvgHours?: number | null;
  totalQuotedItems: number;
  trend: { date: string; items: number }[];
  todo: MyTask[];
}

/** 我的采购：没有对应页面权限的数字不返回 */
export interface MyPurchase {
  openRequirements?: number;
  pendingShip?: number;
  overdueShip?: number;
  drafts?: number;
  inTransit?: number;
  discrepancies?: number;
}

export const workbenchApi = {
  current: () => get<Workbench>(API),
  widget: <T>(key: string) => get<T>(`${API}/widgets/${key}`),
  templates: () => get<WorkbenchTemplate[]>(`${API}/templates`),
};
