// 在 ego 浏览器的 Node 运行环境里执行（由 pull.js 注入 CONFIG 后经 stdin 传入），不能直接用 node 运行。
// 借用浏览器里已登录的飞书会话，调用飞书网页端的只读接口：
//   字段定义  GET /space/api/v1/bitable/{base}/clientvars?tableID=…（table 字段为 gzip + base64）
//   全部记录  GET /space/api/v1/bitable/{base}/records?tableID=…&offset=N&limit=1000
//   附件      GET /space/api/box/stream/download/all/{token}/?mount_point=bitable_image&mount_node_token={base}&extra=…
// 只读：不调用任何写接口。
/* global CONFIG, taskSpace, listTaskSpaces */
const fs = await import('node:fs/promises');
const path = await import('node:path');
const zlib = await import('node:zlib');

const { wikiUrl, outDir, tables: wanted, mode, skipMime, concurrency } = CONFIG;
const SPACE_NAME = '历史数据迁移-飞书读取';
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);

const unzipJson = (b64) => JSON.parse(zlib.gunzipSync(Buffer.from(b64, 'base64')).toString('utf8'));

// ---------- 打开页面，取多维表格标识 ----------
const existing = (await listTaskSpaces()).filter((s) => s.name === SPACE_NAME && s.ownership !== 'user');
const task = existing.length ? await taskSpace(existing[existing.length - 1].spaceId ?? existing[existing.length - 1].id) : await taskSpace(SPACE_NAME);
const page = task.page('p1');
await page.goto(wikiUrl, { timeout: 60000 }).catch(() => {}); // 飞书页面 load 事件很慢，接口可用即可
await page.waitForFunction(() => performance.getEntriesByType('resource').some((e) => e.name.includes('/clientvars')), undefined, { timeout: 90000 });
const firstVars = await page.evaluate(() => performance.getEntriesByType('resource').map((e) => e.name).find((n) => n.includes('/clientvars')));
const baseToken = firstVars.match(/\/bitable\/([A-Za-z0-9]+)\/clientvars/)[1];
log('多维表格', baseToken, '任务空间', task.spaceId);

async function getJson(url) {
  const text = await page.evaluate(async (u) => {
    const res = await fetch(u, { credentials: 'include' });
    return `${res.status}\n${await res.text()}`;
  }, url);
  const i = text.indexOf('\n');
  const status = Number(text.slice(0, i));
  const body = JSON.parse(text.slice(i + 1));
  if (status !== 200 || body.code !== 0) throw new Error(`请求失败 ${status} ${body.code} ${body.msg} ${url}`);
  return body.data;
}

async function clientVars(tableId, needBase) {
  const q = new URLSearchParams({ tableID: tableId, recordLimit: '1', ondemandLimit: '1', needBase: String(needBase), removeFmlExtra: 'true' });
  const data = await getJson(`/space/api/v1/bitable/${baseToken}/clientvars?${q}`);
  return { table: data.table ? unzipJson(data.table) : null, base: data.base ? unzipJson(data.base) : null };
}

// 表清单（名称 → 表 ID）
const anyTable = (firstVars.match(/tableID=(\w+)/) || [])[1];
const { base } = await clientVars(anyTable, true);
const tableIdByName = {};
for (const [id, info] of Object.entries(base.blockInfos || {})) if (id.startsWith('tbl') && info.name) tableIdByName[info.name] = id;

await fs.mkdir(path.join(outDir, 'tables'), { recursive: true });

// ---------- 读记录 ----------
async function pullTable(key, name) {
  const tableId = /^tbl[A-Za-z0-9]{13}$/.test(name) ? name : tableIdByName[name];
  if (!tableId) { log('找不到表', name); return null; }
  const { table } = await clientVars(tableId, false);
  const fields = {};
  for (const [id, f] of Object.entries(table.fieldMap)) fields[id] = { name: f.name, type: f.type, property: f.property ?? null };
  const records = {};
  for (let offset = 0; ; offset += 1000) {
    const data = await getJson(`/space/api/v1/bitable/${baseToken}/records?tableID=${tableId}&offset=${offset}&limit=1000`);
    const raw = typeof data.records === 'string' ? unzipJson(data.records) : data.records;
    const map = raw.recordMap || raw;
    Object.assign(records, map);
    if (Object.keys(map).length < 1000) break;
  }
  const out = { key, name, tableId, rev: table.meta.rev, recordsNum: table.meta.recordsNum, pulledAt: new Date().toISOString(), fields, records };
  await fs.writeFile(path.join(outDir, 'tables', `${key}.json`), JSON.stringify(out));
  log(`表 ${name}：${Object.keys(records).length} 条（飞书显示 ${table.meta.recordsNum} 条）`);
  return out;
}

const pulled = {};
for (const [key, name] of Object.entries(wanted)) {
  if (mode === 'attachments') {
    try { pulled[key] = JSON.parse(await fs.readFile(path.join(outDir, 'tables', `${key}.json`), 'utf8')); } catch { pulled[key] = await pullTable(key, name); }
  } else {
    pulled[key] = await pullTable(key, name);
  }
}

// ---------- 下载附件 ----------
if (mode === 'attachments' || mode === 'all') {
  const attDir = path.join(outDir, 'attachments');
  await fs.mkdir(attDir, { recursive: true });
  const indexFile = path.join(outDir, 'attachments.json');
  let index = {};
  try { index = JSON.parse(await fs.readFile(indexFile, 'utf8')); } catch { /* 第一次运行 */ }

  const jobs = [];
  for (const t of Object.values(pulled)) {
    if (!t) continue;
    const attFields = Object.entries(t.fields).filter(([, f]) => f.type === 17).map(([id]) => id);
    for (const [recordId, rec] of Object.entries(t.records)) {
      for (const fid of attFields) {
        for (const a of rec[fid]?.value || []) {
          const mime = a.mimeType || '';
          const ext = (path.extname(a.name || '') || `.${mime.split('/')[1] || 'bin'}`).toLowerCase().slice(0, 10);
          const entry = { table: t.key, tableId: t.tableId, rev: t.rev, recordId, field: t.fields[fid].name, name: a.name, mime, size: a.size, file: `${a.attachmentToken}${ext}` };
          if (skipMime.some((p) => mime.startsWith(p))) { index[a.attachmentToken] = { ...entry, status: 'skipped' }; continue; }
          jobs.push([a.attachmentToken, entry]);
        }
      }
    }
  }

  let done = 0; let failed = 0; let skipped = 0;
  async function download([token, entry]) {
    const file = path.join(attDir, entry.file);
    const stat = await fs.stat(file).catch(() => null);
    if (stat && stat.size === entry.size) { index[token] = { ...entry, status: 'ok' }; skipped++; return; }
    const extra = encodeURIComponent(JSON.stringify({ bitablePerm: { tableId: entry.tableId, rev: entry.rev } }));
    const url = `/space/api/box/stream/download/all/${token}/?mount_point=bitable_image&mount_node_token=${baseToken}&extra=${extra}`;
    try {
      const res = await page.fetch(url, { timeout: 120000, saveAs: file });
      const size = (await fs.stat(file).catch(() => ({ size: -1 }))).size;
      if (res.status !== 200 || size !== entry.size) throw new Error(`状态 ${res.status}，大小 ${size}/${entry.size}`);
      index[token] = { ...entry, status: 'ok' };
      done++;
    } catch (e) {
      index[token] = { ...entry, status: 'failed', error: String(e.message || e).slice(0, 200) };
      failed++;
    }
  }
  log(`附件共 ${jobs.length} 个，开始下载（并发 ${concurrency}）`);
  for (let i = 0; i < jobs.length; i += concurrency) {
    await Promise.all(jobs.slice(i, i + concurrency).map(download));
    if ((i / concurrency) % 25 === 0) {
      await fs.writeFile(indexFile, JSON.stringify(index));
      log(`进度 ${Math.min(i + concurrency, jobs.length)}/${jobs.length}：新下载 ${done}，已有 ${skipped}，失败 ${failed}`);
    }
  }
  await fs.writeFile(indexFile, JSON.stringify(index));
  log(`附件完成：新下载 ${done}，已有 ${skipped}，失败 ${failed}，按类型跳过 ${Object.values(index).filter((x) => x.status === 'skipped').length}`);
}

await task.finish({ keep: [] });
log('完成');
