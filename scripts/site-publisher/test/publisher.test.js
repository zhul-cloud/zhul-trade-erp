/* Publisher tests: node --test test/
 * A tiny site repo (bare, file://) stands in for the real one; test/fake-ssh.sh makes rsync
 * deploy into a local directory. Nothing leaves the machine. */
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const http = require('http');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'publisher-test-'));
const TOKEN = 'publisher-test-token-0123456789';
const REPO_TOKEN = 'ghp_repoTokenThatMustNeverLeak123';
Object.assign(process.env, {
  SITE_PUBLISHER_TOKEN: TOKEN,
  SITE_PUBLISHER_WORK: path.join(TMP, 'work'),
  PUBLISHER_ALLOW_FILE_REPOS: '1',
  PUBLISHER_SSH_BIN: path.join(__dirname, 'fake-ssh.sh'),
  PUBLISHER_CALLBACK_BACKOFF_MS: '10',
  PUBLISHER_PUSH_BACKOFF_MS: '10'
});

const { start, validateJob } = require('../src/server');
const { makeRedactor } = require('../src/util');
const { diffCatalogs } = require('../src/jobs');

const BARE = path.join(TMP, 'site.git');
const DEPLOY = path.join(TMP, 'deploy');
const KEY = path.join(TMP, 'id_ed25519');
let base;
let server;
let cb;
const ORIG_LOG = console.log;
let events = [];
let waiters = [];
const logs = [];

test.before(async () => {
  makeSiteRepo();
  fs.mkdirSync(DEPLOY);
  execFileSync('ssh-keygen', ['-q', '-t', 'ed25519', '-N', '', '-f', KEY]);
  console.log = (...a) => { logs.push(a.join(' ')); };
  server = await start(0);
  base = `http://127.0.0.1:${server.address().port}`;
  cb = http.createServer((req, res) => {
    let body = '';
    req.on('data', (d) => { body += d; });
    req.on('end', () => {
      assert.strictEqual(req.headers['x-publisher-token'], TOKEN);
      const e = JSON.parse(body);
      e.raw = body;
      events.push(e);
      res.end('{}');
      if (e.event === 'done') waiters.filter((w) => w.id === e.jobId).forEach((w) => w.resolve(e));
    });
  });
  await new Promise((r) => cb.listen(0, '127.0.0.1', r));
  process.env.CALLBACK = `http://127.0.0.1:${cb.address().port}/cb`;
});

test.after(() => {
  console.log = ORIG_LOG;
  if (server) server.close();
  if (cb) cb.close();
  fs.rmSync(TMP, { recursive: true, force: true });
});

function makeSiteRepo() {
  const src = path.join(TMP, 'src');
  fs.mkdirSync(path.join(src, 'erp'), { recursive: true });
  fs.writeFileSync(path.join(src, 'erp', 'build.sh'), `#!/usr/bin/env bash
set -e
node -e '
const fs = require("fs");
const d = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
if (d.sleep) { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, d.sleep); }
if (d.failBuild) { console.log("leak: " + (d.echo || "")); console.error(JSON.stringify({ errors: ["boom " + (d.echo || "")] })); process.exit(1); }
fs.writeFileSync("catalog.json", JSON.stringify(d.products, null, 1));
for (const p of d.products) {
  fs.mkdirSync("products/" + p.slug, { recursive: true });
  fs.writeFileSync("products/" + p.slug + "/index.html", "<html>" + p.model + " " + (p.release || "") + "</html>");
}
console.log(JSON.stringify({ ok: true }));' "$1"
`);
  fs.writeFileSync(path.join(src, 'erp', 'verify.js'), `const fs = require("fs");
const checks = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const results = checks.map((c) => ({ url: c.url, ok: !c.url.includes("broken"), errors: c.url.includes("broken") ? ["main image not loaded"] : [], found: {} }));
console.log(JSON.stringify({ ok: results.every((r) => r.ok), results }));
process.exit(results.every((r) => r.ok) ? 0 : 1);
`);
  fs.writeFileSync(path.join(src, 'erp', 'export-existing.js'), `const fs = require("fs");
const products = fs.existsSync("catalog.json") ? JSON.parse(fs.readFileSync("catalog.json", "utf8")) : [];
fs.writeFileSync(process.argv[process.argv.indexOf("--out") + 1], JSON.stringify({ schemaVersion: 1, products }));
console.error(JSON.stringify({ warnings: [{ type: "orphan", key: "x" }] }));
`);
  fs.writeFileSync(path.join(src, 'catalog.json'), JSON.stringify([{ model: 'A1', slug: 'acme-a1', brand: 'Acme', spec: 'old' }], null, 1));
  const g = (...a) => execFileSync('git', a, { cwd: src, stdio: 'pipe' });
  g('init', '-q', '-b', 'main');
  g('add', '-A');
  g('-c', 'user.name=t', '-c', 'user.email=t@t', 'commit', '-q', '-m', 'init');
  execFileSync('git', ['clone', '-q', '--bare', src, BARE]);
}

function site(overrides = {}) {
  return { code: 'test.example', repoUrl: `file://${BARE}`, branch: 'main', url: 'https://test.example',
    deploy: { host: 'deploy.test', port: 22, user: 'u', path: DEPLOY }, ...overrides };
}

function secrets() { return { repoToken: REPO_TOKEN, deployKey: fs.readFileSync(KEY, 'utf8') }; }

function siteData(extra = {}) {
  return { schemaVersion: 1, site: { code: 'test.example' }, products: [
    { model: 'A1', slug: 'acme-a1', brand: 'Acme', spec: 'new', release: 'WP1' },
    { model: 'B2', slug: 'acme-b2', brand: 'Acme', spec: 'b', release: 'WP1' }], ...extra };
}

async function post(pathname, body, token = TOKEN) {
  const r = await fetch(base + pathname, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Publisher-Token': token }, body: JSON.stringify(body) });
  return { status: r.status, body: await r.json() };
}

async function submit(job) {
  const done = new Promise((resolve) => waiters.push({ id: job.jobId, resolve }));
  const r = await post('/jobs', { callbackUrl: process.env.CALLBACK, site: site(), secrets: secrets(), ...job });
  assert.strictEqual(r.status, 202, JSON.stringify(r.body));
  const final = await done;
  return { final, steps: events.filter((e) => e.jobId === job.jobId && e.event === 'step') };
}

const checks = [{ url: 'https://test.example/products/acme-a1/', images: 1, video: false },
  { url: 'https://test.example/products/acme-b2/', images: 1, video: false }];
const bareLog = () => execFileSync('git', ['log', '--format=%s', 'main'], { cwd: BARE }).toString().trim().split('\n');

test('wrong token is rejected', async () => {
  const r = await post('/jobs', {}, 'nope');
  assert.strictEqual(r.status, 401);
});

test('job validation names the missing piece', () => {
  const ok = { jobId: 'j1', type: 'publish', callbackUrl: 'http://b/cb', site: site(), siteData: siteData(), release: 'WP1', checks: [] };
  assert.strictEqual(validateJob(ok), null);
  assert.match(validateJob({ ...ok, release: '' }), /发布单号/);
  assert.match(validateJob({ ...ok, siteData: null }), /站点数据/);
  assert.match(validateJob({ ...ok, site: site({ repoUrl: 'https://u:p@github.com/x.git' }) }), /仓库地址/);
  assert.match(validateJob({ ...ok, startFrom: 'deploy' }), /提交号/);
  assert.match(validateJob({ ...ok, site: site({ deploy: { host: 'h', port: 22, user: 'u', path: '/x; rm -rf /' } }) }), /部署目录/);
});

test('redactor hides secrets, private key lines and URL credentials', () => {
  const key = fs.readFileSync(KEY, 'utf8');
  const r = makeRedactor([REPO_TOKEN, key]);
  const line = key.split('\n')[1];
  const out = r(`token=${REPO_TOKEN} key-line=${line} url=https://x:${REPO_TOKEN}@github.com/a https://bob:pw@h/x`);
  assert.ok(!out.includes(REPO_TOKEN) && !out.includes(line) && !out.includes('bob:pw'), out);
});

test('publish: all nine steps succeed, deploys, pushes and reports links', async () => {
  const { final, steps } = await submit({ jobId: 'p1', type: 'publish', siteData: siteData(), release: 'WP1', checks });
  assert.strictEqual(final.status, 'success', JSON.stringify(final));
  const done = steps.filter((s) => s.status !== 'running').map((s) => `${s.step}:${s.status}`);
  assert.deepStrictEqual(done, ['pull:success', 'write:success', 'build:success', 'local_check:success', 'commit:success',
    'deploy:success', 'online_check:success', 'push:success']);
  assert.match(fs.readFileSync(path.join(DEPLOY, 'products', 'acme-b2', 'index.html'), 'utf8'), /B2 WP1/);
  assert.ok(!fs.existsSync(path.join(DEPLOY, 'erp')), 'erp/ is not deployed');
  assert.ok(!fs.existsSync(path.join(DEPLOY, '.git')));
  assert.strictEqual(bareLog()[0], 'ERP release WP1: 2 product(s)');
  assert.match(final.result.commit, /^[0-9a-f]{40}$/);
  assert.strictEqual(final.result.online.length, 2);
});

test('publish: build failure stops before deploy and leaves the clone clean', async () => {
  const before = bareLog().length;
  fs.writeFileSync(path.join(DEPLOY, 'marker'), 'untouched');
  const { final } = await submit({ jobId: 'p2', type: 'publish', release: 'WP2', checks,
    siteData: siteData({ failBuild: true, echo: REPO_TOKEN }) });
  assert.strictEqual(final.status, 'failed');
  assert.strictEqual(final.failedStep, 'build');
  assert.match(final.error, /构建失败：boom \*\*\*/);
  assert.strictEqual(bareLog().length, before);
  const clone = path.join(process.env.SITE_PUBLISHER_WORK, 'sites', 'test.example', 'repo');
  assert.strictEqual(execFileSync('git', ['status', '--porcelain'], { cwd: clone }).toString(), '');
  assert.strictEqual(fs.readFileSync(path.join(DEPLOY, 'marker'), 'utf8'), 'untouched');
});

test('publish: local check failure names the page', async () => {
  const bad = [{ url: 'https://test.example/products/broken/', images: 1, video: false }];
  const { final } = await submit({ jobId: 'p3', type: 'publish', release: 'WP3', checks: bad, siteData: siteData() });
  assert.strictEqual(final.failedStep, 'local_check');
  assert.match(final.error, /本地检查：https:\/\/test.example\/products\/broken\/ main image not loaded/);
});

test('publish: deploy failure is deploy_failed and can be retried from the deploy step', async () => {
  process.env.FAKE_SSH_FAIL = '1';
  const data = siteData();
  data.products[0].spec = 'newer';
  data.products.forEach((p) => { p.release = 'WP4'; });
  let r;
  try {
    r = await submit({ jobId: 'p4', type: 'publish', release: 'WP4', checks, siteData: data });
  } finally {
    delete process.env.FAKE_SSH_FAIL;
  }
  assert.strictEqual(r.final.status, 'deploy_failed');
  assert.strictEqual(r.final.failedStep, 'deploy');
  assert.match(r.final.error, /登录失败：密钥被拒绝/);
  const commit = r.final.result.commit;
  assert.match(commit, /^[0-9a-f]{40}$/);
  assert.ok(!bareLog().includes('ERP release WP4: 2 product(s)'), 'not pushed before deploy');

  const retry = await submit({ jobId: 'p4r', type: 'publish', release: 'WP4', checks, startFrom: 'deploy', commit });
  assert.strictEqual(retry.final.status, 'success', JSON.stringify(retry.final));
  assert.deepStrictEqual(retry.steps.filter((s) => s.status !== 'running').map((s) => s.step), ['deploy', 'online_check', 'push']);
  assert.match(fs.readFileSync(path.join(DEPLOY, 'products', 'acme-a1', 'index.html'), 'utf8'), /WP4/);
  assert.strictEqual(bareLog()[0], 'ERP release WP4: 2 product(s)');
});

test('retry from deploy refuses a commit that is no longer checked out', async () => {
  const { final } = await submit({ jobId: 'p5', type: 'publish', release: 'WP5', checks, startFrom: 'deploy', commit: 'a'.repeat(40) });
  assert.strictEqual(final.status, 'failed');
  assert.match(final.error, /不能从部署一步重试/);
});

test('import returns the exported catalog and its warnings', async () => {
  const { final } = await submit({ jobId: 'i1', type: 'import' });
  assert.strictEqual(final.status, 'success');
  assert.deepStrictEqual(final.result.exported.products.map((p) => p.model), ['A1', 'B2']);
  assert.deepStrictEqual(final.result.exportWarnings, [{ type: 'orphan', key: 'x' }]);
});

test('compare builds in a throw-away worktree and lists field differences', async () => {
  const before = bareLog().length;
  const data = siteData();
  data.products[1].spec = 'changed in ERP';
  data.products.push({ model: 'C3', slug: 'acme-c3', brand: 'Acme', spec: 'c' });
  const { final } = await submit({ jobId: 'c1', type: 'compare', siteData: data });
  assert.strictEqual(final.status, 'success', JSON.stringify(final));
  assert.deepStrictEqual(final.result.differences.map((d) => `${d.model}.${d.field}`), ['A1.spec', 'B2.spec']);
  assert.deepStrictEqual(final.result.onlyInErp, ['C3']);
  assert.strictEqual(bareLog().length, before, 'compare never commits');
});

test('diffCatalogs ignores release bookkeeping', () => {
  const r = diffCatalogs([{ model: 'A', spec: 'x', release: 'WP1' }], [{ model: 'A', spec: 'x', release: 'WP2' }]);
  assert.strictEqual(r.same, 1);
  assert.deepStrictEqual(r.differences, []);
});

test('check: repo, adapter, deploy and url items', async () => {
  const r = await post('/check', { site: site({ url: 'http://127.0.0.1:1/' }), secrets: secrets() });
  assert.strictEqual(r.status, 200);
  const byKey = Object.fromEntries(r.body.items.map((i) => [i.key, i]));
  assert.strictEqual(byKey.repo.ok, true);
  assert.strictEqual(byKey.adapter.ok, true);
  assert.strictEqual(byKey.deploy.ok, true, byKey.deploy.message);
  assert.strictEqual(byKey.url.ok, false);
  process.env.FAKE_SSH_FAIL = '1';
  try {
    const bad = await post('/check', { site: site(), secrets: secrets() });
    assert.strictEqual(bad.body.items.find((i) => i.key === 'deploy').message, '登录失败：密钥被拒绝');
  } finally {
    delete process.env.FAKE_SSH_FAIL;
  }
});

test('a passphrase-protected deploy key is reported, not used', async () => {
  const k = path.join(TMP, 'locked');
  execFileSync('ssh-keygen', ['-q', '-t', 'ed25519', '-N', 'secret-pass', '-f', k]);
  const r = await post('/check', { site: site(), secrets: { repoToken: REPO_TOKEN, deployKey: fs.readFileSync(k, 'utf8') } });
  assert.match(r.body.items.find((i) => i.key === 'deploy').message, /密码保护/);
});

test('a failed push is a warning, and the next release pushes it first', async () => {
  const hook = path.join(BARE, 'hooks', 'pre-receive');
  fs.writeFileSync(hook, '#!/bin/sh\necho "remote: rejected for test" >&2\nexit 1\n', { mode: 0o755 });
  const data = siteData();
  data.products.forEach((p) => { p.release = 'WP6'; p.spec = 'six'; });
  let r;
  try {
    r = await submit({ jobId: 'p6', type: 'publish', release: 'WP6', checks, siteData: data });
  } finally {
    fs.rmSync(hook);
  }
  assert.strictEqual(r.final.status, 'success');
  assert.strictEqual(r.steps.find((s) => s.step === 'push' && s.status !== 'running').status, 'warning');
  assert.ok(r.final.result.warnings.some((w) => /推送仓库失败/.test(w)));
  assert.ok(!bareLog().includes('ERP release WP6: 2 product(s)'));

  data.products.forEach((p) => { p.release = 'WP7'; p.spec = 'seven'; });
  const next = await submit({ jobId: 'p7', type: 'publish', release: 'WP7', checks, siteData: data });
  assert.strictEqual(next.final.status, 'success');
  assert.ok(next.final.result.warnings.some((w) => /补推了 1 个/.test(w)), JSON.stringify(next.final.result.warnings));
  assert.deepStrictEqual(bareLog().slice(0, 2), ['ERP release WP7: 2 product(s)', 'ERP release WP6: 2 product(s)']);
});

test('IndexNow gets the release links when the site has a key', async () => {
  let got = null;
  const srv = http.createServer((req, res) => { let b = ''; req.on('data', (d) => { b += d; }); req.on('end', () => { got = JSON.parse(b); res.writeHead(202); res.end(); }); });
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  process.env.PUBLISHER_INDEXNOW_ENDPOINT = `http://127.0.0.1:${srv.address().port}/indexnow`;
  try {
    const data = siteData();
    data.products.forEach((p) => { p.release = 'WP8'; p.spec = 'eight'; });
    const urls = checks.map((c) => c.url);
    const done = new Promise((resolve) => waiters.push({ id: 'p8', resolve }));
    const resp = await post('/jobs', { jobId: 'p8', type: 'publish', callbackUrl: process.env.CALLBACK, site: site(),
      secrets: { ...secrets(), indexNowKey: 'abcdef0123456789abcdef0123456789' }, release: 'WP8', checks, siteData: data, indexNowUrls: urls });
    assert.strictEqual(resp.status, 202);
    const final = await done;
    assert.strictEqual(final.status, 'success');
    assert.deepStrictEqual(got, { host: 'test.example', key: 'abcdef0123456789abcdef0123456789',
      keyLocation: 'https://test.example/abcdef0123456789abcdef0123456789.txt', urlList: urls });
    assert.ok(events.some((e) => e.jobId === 'p8' && e.step === 'indexnow' && e.status === 'success'));
  } finally {
    delete process.env.PUBLISHER_INDEXNOW_ENDPOINT;
    srv.close();
  }
});

test('a step that runs past its timeout fails', async () => {
  const { final } = await submit({ jobId: 'p9', type: 'publish', release: 'WP9', checks,
    siteData: siteData({ sleep: 5000 }), timeouts: { build: 1000 } });
  assert.strictEqual(final.status, 'failed');
  assert.strictEqual(final.failedStep, 'build');
  assert.match(final.error, /超时/);
});

test('no secret appears in any callback or log line', () => {
  const key = fs.readFileSync(KEY, 'utf8');
  const needles = [REPO_TOKEN, ...key.split('\n').filter((l) => l.length >= 16)];
  for (const text of [...events.map((e) => e.raw), ...logs]) {
    for (const n of needles) assert.ok(!text.includes(n), `secret leaked in: ${text.slice(0, 200)}`);
  }
  assert.ok(events.length > 20);
});
