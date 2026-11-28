'use strict';
/* Site publisher (openspec website-publish design 1). Internal service on the Compose
 * network; every request carries X-Publisher-Token.
 *
 *   POST /check          synchronous: { site, secrets } -> { items: [{ key, ok, message }] }
 *   POST /jobs           { jobId, type: import|compare|publish, callbackUrl, site, secrets, … }
 *                        -> 202; 409 while the same site has a job running
 *   GET  /jobs/:jobId    current state of a recent job (no secrets)
 *   GET  /health
 *
 * Async jobs call back callbackUrl (same token header) with
 *   { jobId, event: "step", step, status: running|success|failed|warning, startedAt, endedAt?, message?, output? }
 *   { jobId, event: "done", status: success|failed|deploy_failed, failedStep?, error?, output?, result? }
 * Secrets never leave this process except in git/ssh child environments and temp files
 * (0600, deleted when the job ends); all output is redacted before logging or callbacks. */
const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const path = require('path');
const jobs = require('./jobs');
const { validateSite, siteKey, credentials } = require('./site');
const { StepError, makeRedactor, tempDir, sleep } = require('./util');

const PORT = Number(process.env.SITE_PUBLISHER_PORT || 8910);
const TOKEN = process.env.SITE_PUBLISHER_TOKEN || '';
const WORK_ROOT = process.env.SITE_PUBLISHER_WORK || '/work';
const CALLBACK_RETRIES = Number(process.env.PUBLISHER_CALLBACK_RETRIES || 3);
const CALLBACK_BACKOFF_MS = Number(process.env.PUBLISHER_CALLBACK_BACKOFF_MS || 1000);
const KEEP_JOBS = 100;
const TYPES = { import: jobs.importJob, compare: jobs.compare, publish: jobs.publish };
const SECRET_KEYS = ['repoToken', 'deployKey', 'indexNowKey'];

const busySites = new Set();
const recent = new Map();

function log(msg) { console.log(`[site-publisher] ${msg}`); }

function authorized(req) {
  const got = Buffer.from(String(req.headers['x-publisher-token'] || ''));
  const want = Buffer.from(TOKEN);
  return TOKEN !== '' && got.length === want.length && crypto.timingSafeEqual(got, want);
}

function send(res, status, body) {
  const text = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(text) });
  res.end(text);
}

function readBody(req, limit = 64 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > limit) { reject(new StepError('请求太大')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => {
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')); } catch (e) { reject(new StepError('请求不是合法的 JSON')); }
    });
    req.on('error', reject);
  });
}

function cleanSecrets(s) {
  const out = {};
  for (const k of SECRET_KEYS) if (typeof (s || {})[k] === 'string' && s[k]) out[k] = s[k];
  return out;
}

function validateJob(job) {
  if (!job.jobId || !/^[\w.-]{1,64}$/.test(String(job.jobId))) return 'jobId 不正确';
  if (!TYPES[job.type]) return '任务类型不正确';
  if (!/^https?:\/\//.test(String(job.callbackUrl || ''))) return '缺少回调地址';
  const siteErr = validateSite(job.site);
  if (siteErr) return siteErr;
  if (job.type === 'compare' || (job.type === 'publish' && job.startFrom !== 'deploy')) {
    if (!job.siteData || job.siteData.schemaVersion !== 1 || !Array.isArray(job.siteData.products)) return '缺少站点数据';
  }
  if (job.type === 'publish') {
    if (!/^WP\w+$/.test(String(job.release || ''))) return '缺少发布单号';
    if (!Array.isArray(job.checks)) return '缺少检查清单';
    if (job.startFrom === 'deploy' && !/^[0-9a-f]{40}$/.test(String(job.commit || ''))) return '从部署重试需要提交号';
  }
  return null;
}

async function callback(job, payload, redact) {
  const body = redact(JSON.stringify({ jobId: job.jobId, ...payload }));
  for (let i = 0; i < CALLBACK_RETRIES; i += 1) {
    try {
      const status = await postJson(job.callbackUrl, body);
      if (status >= 200 && status < 300) return true;
      log(`job ${job.jobId}: callback ${payload.event} returned ${status}`);
    } catch (e) {
      log(`job ${job.jobId}: callback ${payload.event} failed: ${e.code || e.message}`);
    }
    if (i < CALLBACK_RETRIES - 1) await sleep(CALLBACK_BACKOFF_MS * 3 ** i);
  }
  return false;
}

function postJson(url, body) {
  const u = new URL(url);
  const lib = u.protocol === 'https:' ? require('https') : http;
  return new Promise((resolve, reject) => {
    const req = lib.request(u, { method: 'POST', timeout: 30000, headers: {
      'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body), 'X-Publisher-Token': TOKEN } }, (res) => {
      res.resume();
      res.on('end', () => resolve(res.statusCode));
    });
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('error', reject);
    req.end(body);
  });
}

async function runJob(job, secrets) {
  const key = siteKey(job.site.code);
  const redact = makeRedactor(Object.values(secrets));
  const t = tempDir('publisher-job-');
  const state = { jobId: job.jobId, type: job.type, site: job.site.code, status: 'running', steps: [], startedAt: new Date().toISOString() };
  remember(state);
  const { gitEnv, keyFile } = credentials(secrets, t.dir);
  const ctx = {
    workRoot: WORK_ROOT, tmp: t.dir, secrets, redact, gitEnv, keyFile, warnings: [], t: jobs.timeouts(job.timeouts),
    pushRetries: Number(process.env.PUBLISHER_PUSH_RETRIES || 3), pushBackoffMs: Number(process.env.PUBLISHER_PUSH_BACKOFF_MS || 2000),
    current: null,
    async step(name, fn, { soft = false, report = true } = {}) {
      const s = { step: name, status: 'running', startedAt: new Date().toISOString() };
      ctx.current = name;
      if (report) { state.steps.push(s); await callback(job, { event: 'step', ...s }, redact); }
      try {
        const v = await fn();
        const warn = soft && v ? v.warn : null;
        Object.assign(s, { status: warn ? 'warning' : 'success', endedAt: new Date().toISOString() }, warn ? { message: redact(warn) } : {});
        if (warn) ctx.warnings.push(redact(warn));
        if (report) await callback(job, { event: 'step', ...s }, redact);
        return soft ? (v ? v.value : undefined) : v;
      } catch (e) {
        const msg = e instanceof StepError ? e.message : `${e.name}: ${e.message}`;
        Object.assign(s, { status: soft ? 'warning' : 'failed', endedAt: new Date().toISOString(), message: redact(msg), output: redact(e.output || '') });
        if (report) await callback(job, { event: 'step', ...s }, redact);
        if (soft) { ctx.warnings.push(redact(msg)); return undefined; }
        e.failedStep = name;
        throw e;
      }
    }
  };
  try {
    log(`job ${job.jobId}: ${job.type} ${key} started`);
    const result = await TYPES[job.type](job, ctx);
    Object.assign(state, { status: 'success', result: summary(result) });
    await callback(job, { event: 'done', status: 'success', result }, redact);
  } catch (e) {
    const status = e.deployFailed ? 'deploy_failed' : 'failed';
    const msg = e instanceof StepError ? e.message : `${e.name}: ${e.message}`;
    Object.assign(state, { status, failedStep: e.failedStep || ctx.current, error: redact(msg) });
    if (!(e instanceof StepError)) log(`job ${job.jobId}: unexpected ${redact(e.stack || msg)}`);
    await callback(job, { event: 'done', status, failedStep: state.failedStep, error: msg, output: e.output || '',
      result: { commit: e.commit || null, warnings: ctx.warnings } }, redact);
  } finally {
    state.endedAt = new Date().toISOString();
    t.cleanup();
    busySites.delete(key);
    log(`job ${job.jobId}: ${state.status}`);
  }
}

function summary(result) {
  const { exported, ...rest } = result || {};
  return exported ? { ...rest, exportedProducts: (exported.products || []).length } : rest;
}

function remember(state) {
  recent.set(state.jobId, state);
  while (recent.size > KEEP_JOBS) recent.delete(recent.keys().next().value);
}

async function handle(req, res) {
  const url = new URL(req.url, 'http://x');
  if (req.method === 'GET' && url.pathname === '/health') return send(res, 200, { ok: true, busySites: [...busySites] });
  if (!TOKEN) return send(res, 503, { detail: '发布服务没有配置令牌' });
  if (!authorized(req)) return send(res, 401, { detail: '令牌不正确' });
  try {
    if (req.method === 'POST' && url.pathname === '/check') {
      const body = await readBody(req);
      const err = validateSite(body.site);
      if (err) return send(res, 400, { detail: err });
      const secrets = cleanSecrets(body.secrets);
      return send(res, 200, await jobs.check({ site: body.site, secrets }, { redact: makeRedactor(Object.values(secrets)), workRoot: WORK_ROOT }));
    }
    if (req.method === 'POST' && url.pathname === '/jobs') {
      const job = await readBody(req);
      const err = validateJob(job);
      if (err) return send(res, 400, { detail: err });
      const key = siteKey(job.site.code);
      if (busySites.has(key)) return send(res, 409, { detail: '这个站点正在执行其他任务' });
      if (recent.has(job.jobId) && recent.get(job.jobId).status === 'running') return send(res, 409, { detail: '任务已在执行' });
      busySites.add(key);
      const secrets = cleanSecrets(job.secrets);
      delete job.secrets;
      setImmediate(() => runJob(job, secrets));
      return send(res, 202, { accepted: true });
    }
    const m = url.pathname.match(/^\/jobs\/([\w.-]+)$/);
    if (req.method === 'GET' && m) {
      const s = recent.get(m[1]);
      return s ? send(res, 200, s) : send(res, 404, { detail: '没有这个任务' });
    }
    return send(res, 404, { detail: 'not found' });
  } catch (e) {
    return send(res, e instanceof StepError ? 400 : 500, { detail: e instanceof StepError ? e.message : '发布服务内部错误' });
  }
}

function start(port = PORT) {
  fs.mkdirSync(path.join(WORK_ROOT, 'sites'), { recursive: true });
  const server = http.createServer((req, res) => { handle(req, res).catch(() => send(res, 500, { detail: '发布服务内部错误' })); });
  return new Promise((resolve) => server.listen(port, () => { log(`listening on ${server.address().port}`); resolve(server); }));
}

if (require.main === module) start();

module.exports = { start, validateJob };
