'use strict';
/* Git and SSH access to one site: repo clone in the work volume, credentials as short-lived
   files/env, and operator-readable error messages. */
const fs = require('fs');
const path = require('path');
const { StepError, run, must, tail } = require('./util');

const SSH_BIN = process.env.PUBLISHER_SSH_BIN || 'ssh';
const ALLOW_FILE_REPOS = process.env.PUBLISHER_ALLOW_FILE_REPOS === '1';
const GIT_NAME = process.env.PUBLISHER_GIT_NAME || 'ZHUL ERP Publisher';
const GIT_EMAIL = process.env.PUBLISHER_GIT_EMAIL || 'publisher@zhul-erp.local';

function siteKey(code) {
  const key = String(code || '').toLowerCase().replace(/[^a-z0-9.-]+/g, '-').replace(/^[-.]+|[-.]+$/g, '');
  if (!key) throw new StepError('站点代码不正确');
  return key;
}

function validateSite(site) {
  if (!site || typeof site !== 'object') return '缺少站点';
  const repo = String(site.repoUrl || '');
  if (!(/^https:\/\/[^\s@]+$/.test(repo) || (ALLOW_FILE_REPOS && /^file:\/\//.test(repo)))) return '仓库地址必须是不带账号的 https 地址';
  if (!/^[\w./-]+$/.test(String(site.branch || ''))) return '分支名不正确';
  if (!/^https?:\/\//.test(String(site.url || ''))) return '网址不正确';
  const d = site.deploy || {};
  if (!d.host || !/^[\w.-]+$/.test(d.host)) return '部署主机不正确';
  if (!/^[\w.-]+$/.test(String(d.user || ''))) return '部署用户不正确';
  if (!(Number(d.port) > 0 && Number(d.port) < 65536)) return '部署端口不正确';
  if (!d.path || /[\s;&|`$]/.test(d.path)) return '部署目录不正确';
  return null;
}

/* Credentials for one job. Files live in a private temp dir and are removed by cleanup(). */
function credentials(secrets, tmp) {
  const askpass = path.join(tmp, 'askpass.sh');
  fs.writeFileSync(askpass, '#!/bin/sh\ncase "$1" in Username*) echo x-access-token ;; *) printf "%s\\n" "$PUBLISHER_GIT_TOKEN" ;; esac\n', { mode: 0o700 });
  const gitEnv = { GIT_ASKPASS: askpass, GIT_TERMINAL_PROMPT: '0', PUBLISHER_GIT_TOKEN: secrets.repoToken || '' };
  let keyFile = null;
  if (secrets.deployKey) {
    keyFile = path.join(tmp, 'deploy_key');
    fs.writeFileSync(keyFile, secrets.deployKey.replace(/\r\n/g, '\n').replace(/\s*$/, '\n'), { mode: 0o600 });
  }
  return { gitEnv, keyFile };
}

async function assertUsableKey(keyFile, redact) {
  if (!keyFile) throw new StepError('没有配置部署私钥');
  const r = await run('ssh-keygen', ['-y', '-P', '', '-f', keyFile], { redact, timeoutMs: 15000 });
  if (r.code !== 0) throw new StepError('部署私钥有密码保护或格式不对；发布服务需要不带密码的私钥');
}

function sshArgs(site, keyFile, knownHosts) {
  return ['-p', String(site.deploy.port), '-i', keyFile, '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes',
    '-o', 'StrictHostKeyChecking=accept-new', '-o', `UserKnownHostsFile=${knownHosts}`, '-o', 'ConnectTimeout=20'];
}

function sshError(out) {
  const s = String(out || '');
  if (/Permission denied \(publickey/i.test(s)) return '登录失败：密钥被拒绝';
  if (/Could not resolve hostname/i.test(s)) return '登录失败：主机名无法解析';
  if (/Connection refused/i.test(s)) return '登录失败：连接被拒绝（检查端口）';
  if (/timed out/i.test(s)) return '登录失败：连接超时';
  if (/Host key verification failed|REMOTE HOST IDENTIFICATION HAS CHANGED/i.test(s)) return '登录失败：服务器指纹与上次不一致';
  return null;
}

function gitError(out) {
  const s = String(out || '');
  if (/Authentication failed|could not read Username|Invalid username or password|403/i.test(s)) return '仓库认证失败：检查仓库令牌';
  if (/Repository not found|not found|does not appear to be a git repository/i.test(s)) return '仓库不存在或令牌没有权限';
  if (/Remote branch .* not found|couldn't find remote ref/i.test(s)) return '分支不存在';
  if (/Could not resolve host|Failed to connect|SSL|timed out|EOF/i.test(s)) return '连不上仓库（网络问题）';
  return null;
}

/* Clones on first use (blobless: history without old file contents), otherwise fetches and
   resets to the remote branch. Commits left unpushed by an earlier release are rebased and
   pushed first; if that is impossible they are dropped with a warning (the next build is
   regenerated from full data anyway). Returns warnings. */
async function syncRepo({ site, repoDir, gitEnv, redact, timeoutMs, pushRetries }) {
  const warnings = [];
  const b = site.branch;
  const git = (args, msg, t = timeoutMs) => must(msg, 'git', args, { cwd: repoDir, env: gitEnv, redact, timeoutMs: t });
  const fail = (e, fallback) => { throw new StepError(gitError(e.output) || fallback, e.output); };
  if (!fs.existsSync(path.join(repoDir, '.git'))) {
    fs.mkdirSync(path.dirname(repoDir), { recursive: true });
    fs.rmSync(repoDir, { recursive: true, force: true });
    try {
      await must('克隆仓库失败', 'git', ['clone', '--filter=blob:none', '--single-branch', '--branch', b, site.repoUrl, repoDir],
        { env: gitEnv, redact, timeoutMs });
    } catch (e) { fail(e, '克隆仓库失败'); }
  } else {
    await git(['remote', 'set-url', 'origin', site.repoUrl], '设置仓库地址失败');
    await git(['reset', '--hard'], '清理工作区失败');
    await git(['clean', '-fd'], '清理工作区失败');
    try { await git(['fetch', 'origin', `+refs/heads/${b}:refs/remotes/origin/${b}`], '拉取仓库失败'); } catch (e) { fail(e, '拉取仓库失败'); }
    const cur = (await run('git', ['rev-parse', '--abbrev-ref', 'HEAD'], { cwd: repoDir, redact })).out.trim();
    if (cur !== b) await git(['checkout', '-B', b, `origin/${b}`], '切换分支失败');
    const ahead = Number((await run('git', ['rev-list', '--count', `origin/${b}..HEAD`], { cwd: repoDir, redact })).out.trim()) || 0;
    if (ahead > 0) {
      const rebased = await run('git', ['rebase', `origin/${b}`], { cwd: repoDir, env: gitEnv, redact, timeoutMs });
      if (rebased.code === 0 && await pushHead({ repoDir, branch: b, gitEnv, redact, timeoutMs, retries: pushRetries })) {
        warnings.push(`补推了 ${ahead} 个上次没有推送成功的提交`);
      } else {
        if (rebased.code !== 0) await run('git', ['rebase', '--abort'], { cwd: repoDir, redact });
        await git(['reset', '--hard', `origin/${b}`], '重置工作区失败');
        warnings.push(`上次有 ${ahead} 个提交没有推送成功，现在也推不上去，已丢弃（本次按全量数据重新生成，线上内容不受影响）`);
      }
    } else {
      await git(['reset', '--hard', `origin/${b}`], '重置工作区失败');
    }
  }
  await git(['config', 'user.name', GIT_NAME], '设置提交人失败');
  await git(['config', 'user.email', GIT_EMAIL], '设置提交人失败');
  return warnings;
}

/* Pushes HEAD with retries (exponential backoff). Returns true on success. */
async function pushHead({ repoDir, branch, gitEnv, redact, timeoutMs, retries = 3, backoffMs = 2000, onFail }) {
  let last = '';
  for (let i = 0; i < retries; i += 1) {
    const r = await run('git', ['push', 'origin', `HEAD:refs/heads/${branch}`], { cwd: repoDir, env: gitEnv, redact, timeoutMs }).catch((e) => ({ code: 1, out: e.output || e.message }));
    if (r.code === 0) return true;
    last = r.out;
    if (i < retries - 1) await new Promise((res) => setTimeout(res, backoffMs * 2 ** i));
  }
  if (onFail) onFail(tail(last));
  return false;
}

module.exports = { siteKey, validateSite, credentials, assertUsableKey, sshArgs, sshError, gitError, syncRepo, pushHead, SSH_BIN };
