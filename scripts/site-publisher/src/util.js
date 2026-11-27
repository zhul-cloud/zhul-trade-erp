'use strict';
/* Process running, output redaction and small helpers shared by the publisher jobs. */
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const TAIL_LINES = 50;
const MAX_CAPTURE = 512 * 1024;

class StepError extends Error {
  /** @param {string} message shown to operators (Chinese), @param {string} [output] redacted tail */
  constructor(message, output) {
    super(message);
    this.output = output || '';
  }
}

/* Replaces every known secret value (and each long line of a multi-line secret such as a
   private key) with ***. Also hides credentials embedded in URLs. */
function makeRedactor(secrets) {
  const needles = new Set();
  for (const s of secrets || []) {
    if (typeof s !== 'string' || s.length < 4) continue;
    needles.add(s.trim());
    for (const line of s.split(/\r?\n/)) if (line.trim().length >= 16) needles.add(line.trim());
  }
  const list = [...needles].filter((s) => s.length >= 4).sort((a, b) => b.length - a.length);
  return (text) => {
    let out = String(text == null ? '' : text);
    for (const n of list) out = out.split(n).join('***');
    return out.replace(/(https?:\/\/)[^\s/@:]+:[^\s/@]+@/g, '$1***@');
  };
}

function tail(text, lines = TAIL_LINES) {
  const all = String(text || '').replace(/\s+$/, '').split('\n');
  return all.slice(-lines).join('\n');
}

/* Runs a command without a shell. Resolves {code, out}; rejects with StepError on timeout or
   spawn failure. `out` is stdout+stderr interleaved, already redacted. */
function run(cmd, args, { cwd, env, timeoutMs = 180000, redact = (s) => s, input } = {}) {
  return new Promise((resolve, reject) => {
    let out = '';
    let killedForTimeout = false;
    // Own process group, so a timeout also stops grandchildren (build.sh -> node builders).
    const child = spawn(cmd, args, { cwd, env: { ...process.env, ...env }, stdio: ['pipe', 'pipe', 'pipe'], detached: true });
    const killGroup = (sig) => { try { process.kill(-child.pid, sig); } catch (e) { /* already gone */ } };
    const keep = (d) => { out += d; if (out.length > MAX_CAPTURE) out = out.slice(-MAX_CAPTURE); };
    child.stdout.on('data', keep);
    child.stderr.on('data', keep);
    const timer = setTimeout(() => {
      killedForTimeout = true;
      killGroup('SIGTERM');
      setTimeout(() => killGroup('SIGKILL'), 5000).unref();
    }, timeoutMs);
    child.on('error', (e) => { clearTimeout(timer); reject(new StepError(`无法运行 ${cmd}：${e.code || e.message}`)); });
    child.on('close', (code) => {
      clearTimeout(timer);
      const clean = redact(out);
      if (killedForTimeout) return reject(new StepError(`超时（超过 ${timeoutMs >= 60000 ? `${Math.round(timeoutMs / 60000)} 分钟` : `${Math.round(timeoutMs / 1000)} 秒`}）`, tail(clean)));
      resolve({ code, out: clean });
    });
    if (input !== undefined) child.stdin.end(input); else child.stdin.end();
  });
}

/* run() that treats a non-zero exit as a StepError with `message`. */
async function must(message, cmd, args, opts) {
  const r = await run(cmd, args, opts);
  if (r.code !== 0) throw new StepError(message, tail(r.out));
  return r;
}

/* Last {"errors":[…]} line printed by a site adapter script, if any. */
function adapterErrors(out) {
  const lines = String(out || '').trim().split('\n').reverse();
  for (const l of lines) {
    try {
      const j = JSON.parse(l);
      if (j && Array.isArray(j.errors)) return j.errors;
    } catch (e) { /* not JSON */ }
  }
  return null;
}

function lastJson(out) {
  const lines = String(out || '').trim().split('\n').reverse();
  for (const l of lines) {
    try { return JSON.parse(l); } catch (e) { /* keep looking */ }
  }
  return null;
}

/* A private temp dir (0700) removed by the returned cleanup function. */
function tempDir(prefix) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), prefix));
  fs.chmodSync(dir, 0o700);
  return { dir, cleanup: () => fs.rmSync(dir, { recursive: true, force: true }) };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

module.exports = { StepError, makeRedactor, tail, run, must, adapterErrors, lastJson, tempDir, sleep, TAIL_LINES };
