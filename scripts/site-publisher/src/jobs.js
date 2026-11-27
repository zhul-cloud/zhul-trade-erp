'use strict';
/* The four jobs (openspec website-publish design 1, 3, 7): check, import, compare, publish.
 * Each async job reports every step through ctx.step() and returns a result for the final
 * callback; StepError messages are shown to operators as they are. */
const fs = require('fs');
const path = require('path');
const https = require('https');
const { StepError, run, must, tail, adapterErrors, lastJson, tempDir } = require('./util');
const { siteKey, credentials, assertUsableKey, sshArgs, sshError, gitError, syncRepo, pushHead, SSH_BIN } = require('./site');

const ADAPTER = ['erp/build.sh', 'erp/verify.js', 'erp/export-existing.js'];
const RSYNC_EXCLUDES = ['.git', '.gstack', '.DS_Store', 'node_modules', '/erp'];
const MIN = 60 * 1000;

function timeouts(cfg = {}) {
  return { build: 10 * MIN, deploy: 10 * MIN, other: 3 * MIN, ...cfg };
}

/* ---------- check (synchronous) ---------- */

async function check({ site, secrets }, { redact, workRoot }) {
  const t = tempDir('publisher-check-');
  const { gitEnv, keyFile } = credentials(secrets, t.dir);
  const items = [];
  const item = async (key, fn) => {
    try { items.push({ key, ok: true, message: (await fn()) || '通过' }); }
    catch (e) { items.push({ key, ok: false, message: e instanceof StepError ? e.message : `检查出错：${e.message}` }); }
  };
  try {
    let repoOk = false;
    await item('repo', async () => {
      const r = await run('git', ['ls-remote', '--heads', site.repoUrl, site.branch], { env: gitEnv, redact, timeoutMs: MIN });
      if (r.code !== 0) throw new StepError(gitError(r.out) || '拉取失败');
      if (!r.out.trim()) throw new StepError('分支不存在');
      repoOk = true;
    });
    await item('adapter', async () => {
      if (!repoOk) throw new StepError('仓库拉取失败，无法检查');
      const dir = path.join(t.dir, 'probe');
      await must('拉取失败', 'git', ['clone', '--depth', '1', '--filter=blob:none', '--no-checkout', '--single-branch', '--branch', site.branch, site.repoUrl, dir],
        { env: gitEnv, redact, timeoutMs: 2 * MIN });
      const ls = await must('读取仓库失败', 'git', ['ls-tree', '-r', '--name-only', 'HEAD', 'erp/'], { cwd: dir, redact });
      const files = new Set(ls.out.split('\n').map((s) => s.trim()));
      const missing = ADAPTER.filter((f) => !files.has(f));
      if (missing.length) throw new StepError(`站点仓库没有可用的 ERP 适配脚本（缺少 ${missing.join('、')}）`);
    });
    await item('deploy', async () => {
      await assertUsableKey(keyFile, redact);
      const d = site.deploy;
      const probe = `${d.path.replace(/\/+$/, '')}/.erp-write-test-${process.pid}`;
      const r = await run(SSH_BIN, [...sshArgs(site, keyFile, knownHosts(workRoot)), `${d.user}@${d.host}`,
        `test -d '${d.path}' && touch '${probe}' && rm -f '${probe}' && echo erp-write-ok`], { redact, timeoutMs: MIN });
      if (r.code === 0 && r.out.includes('erp-write-ok')) return '通过';
      throw new StepError(sshError(r.out) || '目标目录不存在或没有写权限');
    });
    await item('url', async () => {
      const status = await httpStatus(site.url);
      if (status !== 200) throw new StepError(`网址返回 ${status}`);
    });
    return { items };
  } finally {
    t.cleanup();
  }
}

/* ---------- import / compare / publish (asynchronous) ---------- */

async function importJob(job, ctx) {
  const { repoDir } = await prepare(job, ctx);
  const exported = await ctx.step('export', async () => {
    const out = path.join(ctx.tmp, 'export.json');
    const r = await run('node', ['erp/export-existing.js', '--out', out], { cwd: repoDir, redact: ctx.redact, timeoutMs: ctx.t.other });
    if (r.code !== 0) throw new StepError(adapterMessage(r.out, '导出现有商品失败'), tail(r.out));
    const warn = lastJson(r.out);
    return { value: JSON.parse(fs.readFileSync(out, 'utf8')), warnings: warn && warn.warnings ? warn.warnings : [] };
  });
  return { exported: exported.value, exportWarnings: exported.warnings, commit: await head(repoDir, ctx) };
}

/* Builds ERP's data in a throw-away worktree (nothing is committed or deployed), then
   compares ERP's products with the site's, field by field. */
async function compare(job, ctx) {
  const { repoDir } = await prepare(job, ctx);
  const wt = path.join(ctx.tmp, 'compare');
  await must('创建临时工作区失败', 'git', ['worktree', 'add', '--detach', wt, 'HEAD'], { cwd: repoDir, redact: ctx.redact });
  try {
    const siteNow = await exportFrom(repoDir, ctx, 'site.json');
    await ctx.step('build', async () => {
      const file = path.join(wt, 'erp', 'site-data.json');
      fs.writeFileSync(file, JSON.stringify(job.siteData, null, 2));
      await buildIn(wt, file, ctx);
    });
    const erpBuilt = await exportFrom(wt, ctx, 'erp.json');
    return { ...diffCatalogs(siteNow.products, erpBuilt.products), commit: await head(repoDir, ctx) };
  } finally {
    await run('git', ['worktree', 'remove', '--force', wt], { cwd: repoDir, redact: ctx.redact });
  }
}

async function publish(job, ctx) {
  const t = ctx.t;
  const fromDeploy = job.startFrom === 'deploy';
  const repoDir = repoPath(ctx.workRoot, job.site);
  let commit = job.commit || null;
  const warnings = [];
  let local = null;

  if (!fromDeploy) {
    try {
      await prepare(job, ctx);
      await ctx.step('write', async () => {
        fs.writeFileSync(path.join(repoDir, 'erp', 'site-data.json'), JSON.stringify(job.siteData, null, 2) + '\n');
      });
      await ctx.step('build', () => buildIn(repoDir, path.join(repoDir, 'erp', 'site-data.json'), ctx));
      local = await ctx.step('local_check', async () => {
        const res = await verifyPages(repoDir, job.checks, { root: repoDir, fallback: job.site.url }, ctx, t.other);
        if (!res.ok) throw new StepError(`本地检查：${failures(res)}`, JSON.stringify(res.results.filter((r) => !r.ok)));
        return res.results;
      });
      commit = await ctx.step('commit', async () => {
        await must('提交失败', 'git', ['add', '-A'], { cwd: repoDir, redact: ctx.redact });
        const staged = await run('git', ['diff', '--cached', '--quiet'], { cwd: repoDir, redact: ctx.redact });
        if (staged.code === 0) { warnings.push('页面与仓库现有版本相同，没有新的提交'); return head(repoDir, ctx); }
        const n = (job.siteData.products || []).filter((p) => p.release === job.release).length;
        await must('提交失败', 'git', ['commit', '-q', '-m', `ERP release ${job.release}: ${n} product(s)`], { cwd: repoDir, redact: ctx.redact });
        return head(repoDir, ctx);
      });
    } catch (e) {
      await resetRepo(repoDir, job.site.branch, ctx);
      throw e;
    }
  } else {
    await ctx.step('deploy_prepare', async () => {
      if (!fs.existsSync(path.join(repoDir, '.git')) || !commit || (await head(repoDir, ctx)) !== commit) {
        throw new StepError('工作区已不是这张发布单的提交，不能从部署一步重试，请重新发布');
      }
    }, { report: false });
  }

  try {
    await ctx.step('deploy', async () => {
      await assertUsableKey(ctx.keyFile, ctx.redact);
      const d = job.site.deploy;
      const rsh = [SSH_BIN, ...sshArgs(job.site, ctx.keyFile, knownHosts(ctx.workRoot))].map(shellQuote).join(' ');
      const args = ['-az', '--checksum', '-e', rsh, ...RSYNC_EXCLUDES.map((x) => `--exclude=${x}`), `${repoDir}/`, `${d.user}@${d.host}:${d.path.replace(/\/*$/, '/')}`];
      const r = await run('rsync', args, { redact: ctx.redact, timeoutMs: t.deploy });
      if (r.code !== 0) throw new StepError(sshError(r.out) || `同步到部署服务器失败（rsync 退出码 ${r.code}）`, tail(r.out));
    });
  } catch (e) {
    e.deployFailed = true;
    e.commit = commit;
    throw e;
  }

  const online = await ctx.step('online_check', async () => {
    const checks = (job.checks || []).map((c) => ({ ...c, release: job.release }));
    const res = await verifyPages(repoDir, checks, {}, ctx, t.other);
    if (res.ok) return { value: res.results, warn: null };
    const bad = res.results.filter((r) => !r.ok);
    const unreachable = bad.every((r) => r.errors.some((e) => /net::ERR_|Timeout|timed out/i.test(e)));
    const hint = unreachable ? '发布服务访问不了这些页面，稍后在浏览器里打开确认' : '可能还是缓存里的旧页面，清缓存后再确认';
    return { value: res.results, warn: `线上检查没通过：${failures(res)}（${hint}）` };
  }, { soft: true });

  await ctx.step('push', async () => {
    let detail = '';
    const ok = await pushHead({ repoDir, branch: job.site.branch, gitEnv: ctx.gitEnv, redact: ctx.redact, timeoutMs: t.other,
      retries: ctx.pushRetries, backoffMs: ctx.pushBackoffMs, onFail: (out) => { detail = out; } });
    return { warn: ok ? null : `推送仓库失败，下一张发布单开始时会补推：${gitError(detail) || tail(detail, 3)}` };
  }, { soft: true });

  if (ctx.secrets.indexNowKey && (job.indexNowUrls || []).length) {
    await ctx.step('indexnow', async () => {
      try {
        const status = await indexNow(job.site.url, ctx.secrets.indexNowKey, job.indexNowUrls);
        return { warn: status >= 200 && status < 300 ? null : `IndexNow 返回 ${status}` };
      } catch (e) {
        return { warn: `IndexNow 推送失败：${e.message}` };
      }
    }, { soft: true });
  }

  return { commit, local, online: online || null, warnings: [...warnings, ...ctx.warnings] };
}

/* ---------- helpers ---------- */

async function prepare(job, ctx) {
  const repoDir = repoPath(ctx.workRoot, job.site);
  const warnings = await ctx.step('pull', () => syncRepo({ site: job.site, repoDir, gitEnv: ctx.gitEnv, redact: ctx.redact,
    timeoutMs: ctx.t.other * 3, pushRetries: ctx.pushRetries }));
  for (const w of warnings) ctx.warnings.push(w);
  return { repoDir };
}

async function buildIn(dir, file, ctx) {
  if (!ADAPTER.every((f) => fs.existsSync(path.join(dir, f)))) throw new StepError('站点仓库没有可用的 ERP 适配脚本');
  const r = await run('bash', ['erp/build.sh', file], { cwd: dir, redact: ctx.redact, timeoutMs: ctx.t.build,
    env: { CACHE_VERSION: cacheVersion() } });
  if (r.code !== 0) throw new StepError(adapterMessage(r.out, '构建失败'), tail(r.out));
}

async function verifyPages(dir, checks, { root, fallback }, ctx, timeoutMs) {
  const file = path.join(ctx.tmp, `checks-${Date.now()}.json`);
  fs.writeFileSync(file, JSON.stringify(checks || []));
  const args = ['erp/verify.js', file];
  if (root) args.push('--root', root);
  if (fallback) args.push('--asset-fallback', fallback);
  const r = await run('node', args, { cwd: dir, redact: ctx.redact, timeoutMs });
  const res = lastJson(r.out);
  if (!res || !Array.isArray(res.results)) throw new StepError(adapterMessage(r.out, '检查脚本没有给出结果'), tail(r.out));
  return res;
}

async function exportFrom(dir, ctx, name) {
  const out = path.join(ctx.tmp, name);
  const r = await run('node', ['erp/export-existing.js', '--out', out], { cwd: dir, redact: ctx.redact, timeoutMs: ctx.t.other });
  if (r.code !== 0) throw new StepError(adapterMessage(r.out, '导出商品失败'), tail(r.out));
  return JSON.parse(fs.readFileSync(out, 'utf8'));
}

/* Field-level comparison keyed by model. `slug` is computed by the site and `release` is
   ERP bookkeeping, so neither counts as a content difference. */
function diffCatalogs(siteProducts, erpProducts) {
  const ignore = new Set(['release']);
  const byModel = (list) => new Map((list || []).map((p) => [p.model, p]));
  const s = byModel(siteProducts);
  const e = byModel(erpProducts);
  const differences = [];
  let same = 0;
  for (const [model, sp] of s) {
    const ep = e.get(model);
    if (!ep) continue;
    const fields = [...new Set([...Object.keys(sp), ...Object.keys(ep)])].filter((k) => !ignore.has(k));
    const diffs = fields.filter((k) => JSON.stringify(sp[k]) !== JSON.stringify(ep[k]))
      .map((k) => ({ model, field: k, site: sp[k], erp: ep[k] }));
    if (diffs.length) differences.push(...diffs); else same += 1;
  }
  return {
    same,
    differences,
    onlyOnSite: [...s.keys()].filter((m) => !e.has(m)),
    onlyInErp: [...e.keys()].filter((m) => !s.has(m))
  };
}

async function resetRepo(repoDir, branch, ctx) {
  if (!fs.existsSync(path.join(repoDir, '.git'))) return;
  await run('git', ['reset', '--hard', `origin/${branch}`], { cwd: repoDir, redact: ctx.redact });
  await run('git', ['clean', '-fd'], { cwd: repoDir, redact: ctx.redact });
}

async function head(repoDir, ctx) {
  return (await must('读取提交号失败', 'git', ['rev-parse', 'HEAD'], { cwd: repoDir, redact: ctx.redact })).out.trim();
}

function repoPath(workRoot, site) { return path.join(workRoot, 'sites', siteKey(site.code), 'repo'); }
function knownHosts(workRoot) { return path.join(workRoot, 'known_hosts'); }
/* Beijing time, e.g. 20261127153000 (same shape as scripts/deploy.sh in the site repo). */
function cacheVersion() {
  return new Date(Date.now() + 8 * 3600 * 1000).toISOString().replace(/\D/g, '').slice(0, 14);
}
function shellQuote(s) { return /^[\w@%+=:,./-]+$/.test(s) ? s : `'${String(s).replace(/'/g, "'\\''")}'`; }

function adapterMessage(out, fallback) {
  const errs = adapterErrors(out);
  return errs && errs.length ? `${fallback}：${errs.slice(0, 3).join('；')}` : fallback;
}

function failures(res) {
  return res.results.filter((r) => !r.ok).slice(0, 3).map((r) => `${r.url} ${r.errors.join('，')}`).join('；');
}

function httpStatus(url) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https:') ? https : require('http');
    const req = lib.get(url, { timeout: 20000, headers: { 'User-Agent': 'zhul-erp-site-publisher' } }, (res) => {
      res.resume();
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        return httpStatus(new URL(res.headers.location, url).toString()).then(resolve, reject);
      }
      resolve(res.statusCode);
    });
    req.on('timeout', () => req.destroy(new StepError('网址访问超时')));
    req.on('error', (e) => reject(e instanceof StepError ? e : new StepError(`网址访问失败：${e.code || e.message}`)));
  });
}

function indexNow(siteUrl, key, urls) {
  const host = new URL(siteUrl).host;
  const endpoint = new URL(process.env.PUBLISHER_INDEXNOW_ENDPOINT || 'https://api.indexnow.org/indexnow');
  const body = JSON.stringify({ host, key, keyLocation: `${siteUrl.replace(/\/$/, '')}/${key}.txt`, urlList: urls });
  return new Promise((resolve, reject) => {
    const lib = endpoint.protocol === 'https:' ? https : require('http');
    const req = lib.request(endpoint, { method: 'POST', timeout: 30000,
      headers: { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) } }, (res) => {
      res.resume();
      res.on('end', () => resolve(res.statusCode));
    });
    req.on('timeout', () => req.destroy(new Error('超时')));
    req.on('error', reject);
    req.end(body);
  });
}

module.exports = { check, importJob, compare, publish, diffCatalogs, timeouts, repoPath };
