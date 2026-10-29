import { request } from '@umijs/max';

const API = '/api/v1/system/history-migrations';

/** 阶段 → 数据类别 → 新建 / 跳过 / 失败 */
export type StageStats = Record<
  string,
  Record<string, { 新建: number; 跳过: number; 失败: number }>
>;

export interface VerifyCount {
  consistent: number;
  known: number;
  inconsistent: number;
}

export interface VerifyRow {
  report: string;
  scope: string;
  metric: string;
  old: string | number;
  new: string | number;
  diff: string | number;
  tolerance: string | number;
  verdict: '一致' | '已知差异' | '不一致';
  note: string;
}

export interface VerifyResult {
  summary: VerifyCount & { total: number };
  byMetric: (VerifyCount & { report: string; metric: string })[];
  rows?: VerifyRow[];
}

/** 运行方式 1-干跑、2-正式执行；状态 1-运行中、2-成功、3-失败、4-已回滚 */
export interface MigrationBatch {
  id: number;
  tenantId: number;
  batchNo: string;
  bundleHash: string;
  runMode: 1 | 2;
  status: 1 | 2 | 3 | 4;
  errorText: string;
  startedAt?: string;
  finishedAt?: string;
  stageStats?: StageStats;
  verifyResult?: VerifyResult;
  rollingBack: boolean;
}

export interface MigrationBundle {
  id: string;
  version: string;
  linksExpireAt: string;
  counts: Record<string, number>;
}

export async function uploadBundle(file: File): Promise<MigrationBundle> {
  const data = new FormData();
  data.append('file', file);
  const res = await request(`${API}/bundles`, {
    method: 'POST',
    data,
    requestType: 'form',
    timeout: 300000,
  });
  return res.data;
}

export async function runMigration(
  tenantId: number,
  bundleId: string,
  dryRun: boolean,
): Promise<MigrationBatch> {
  const res = await request(API, {
    method: 'POST',
    data: { tenantId, bundleId, dryRun },
  });
  return res.data;
}

export async function rollbackBatch(id: number): Promise<MigrationBatch> {
  const res = await request(`${API}/${id}/rollback`, { method: 'POST' });
  return res.data;
}

export async function listBatches(tenantId: number): Promise<MigrationBatch[]> {
  const res = await request(API, { params: { tenantId } });
  return res.data ?? [];
}

export async function getBatch(id: number): Promise<MigrationBatch> {
  const res = await request(`${API}/${id}`);
  return res.data;
}
