'use strict';
// 打迁移包（生产在迁移页面上传执行用）：
//   node scripts/history-migration/pack.js [--days 7] [--concurrency 8]
// 1. 生成旧报表数字 out/bundle/reports.json（核对用）；
// 2. 把 media.json 用到的图片传到开发 OSS 私有桶的 migration/feishu/ 目录（已传过的跳过，可断点续传），
//    给每张图生成临时下载链接（默认 7 天，OSS 上限），生产导入时按链接下载后存入生产 OSS——两套 OSS 的密钥互不接触；
// 3. 打成 out/pack/迁移包-<版本>.zip：清洗结果、reports.json、带链接的 media.json、manifest（含链接到期时间）。
// 开发 OSS 的配置读 zhul-erp-backend/src/main/resources/application-dev.yml（已被 Git 忽略），也可用 HM_OSS_* 环境变量覆盖。
// 生产服务器访问不了开发 OSS 时，用 HM_OSS_* 指向生产的私有桶（公网 endpoint），并设 HM_OSS_ALLOW_PROD=1 显式确认。
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const { writeReports } = require('./reports');

const OUT = path.resolve(process.env.HM_OUT_DIR || path.join(__dirname, 'out'));
const BUNDLE = path.join(OUT, 'bundle');
const ATTACHMENTS = path.join(OUT, 'feishu', 'attachments');
const PREFIX = 'migration/feishu/';

function arg(name, def) {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : def;
}

/** 开发 OSS 配置：application-dev.yml 里 storage.oss 一段的 endpoint、access-key-id、access-key-secret、private-bucket */
function ossConfig() {
  const yml = path.join(__dirname, '../../zhul-erp-backend/src/main/resources/application-dev.yml');
  const text = fs.existsSync(yml) ? fs.readFileSync(yml, 'utf8') : '';
  const block = text.slice(text.indexOf('oss:'));
  const pick = (key) => (block.match(new RegExp(`^\\s*${key}:\\s*(\\S+)`, 'm')) || [])[1];
  const c = {
    endpoint: process.env.HM_OSS_ENDPOINT || pick('endpoint'),
    id: process.env.HM_OSS_ACCESS_KEY_ID || pick('access-key-id'),
    secret: process.env.HM_OSS_ACCESS_KEY_SECRET || pick('access-key-secret'),
    bucket: process.env.HM_OSS_BUCKET || pick('private-bucket'),
  };
  for (const [k, v] of Object.entries(c)) if (!v) throw new Error(`缺少开发 OSS 配置：${k}`);
  if (!c.bucket.includes('dev') && process.env.HM_OSS_ALLOW_PROD !== '1') {
    throw new Error(`存储桶 ${c.bucket} 看起来不是开发桶，停止；确认要用生产桶中转时设 HM_OSS_ALLOW_PROD=1`);
  }
  c.host = `${c.bucket}.${c.endpoint.replace(/^https?:\/\//, '')}`;
  return c;
}

// OSS V1 签名：CanonicalizedResource 用原始对象名，URL 里按段编码
const sign = (c, s) => crypto.createHmac('sha1', c.secret).update(s).digest('base64');
const objectUrl = (c, key) => `https://${c.host}/${key.split('/').map(encodeURIComponent).join('/')}`;

async function exists(c, key) {
  const date = new Date().toUTCString();
  const res = await fetch(objectUrl(c, key), {
    method: 'HEAD',
    headers: { Date: date, Authorization: `OSS ${c.id}:${sign(c, `HEAD\n\n\n${date}\n/${c.bucket}/${key}`)}` },
  });
  return res.status === 200;
}

async function put(c, key, file) {
  const date = new Date().toUTCString();
  const type = file.toLowerCase().endsWith('.png') ? 'image/png' : file.toLowerCase().endsWith('.webp') ? 'image/webp' : 'image/jpeg';
  const res = await fetch(objectUrl(c, key), {
    method: 'PUT',
    headers: { Date: date, 'Content-Type': type, Authorization: `OSS ${c.id}:${sign(c, `PUT\n\n${type}\n${date}\n/${c.bucket}/${key}`)}` },
    body: fs.readFileSync(path.join(ATTACHMENTS, file)),
  });
  if (res.status !== 200) throw new Error(`上传 ${file} 失败：HTTP ${res.status} ${await res.text()}`);
}

/** 网络抖动时重试（共 4 次，间隔 2、4、8 秒） */
async function retry(fn) {
  for (let i = 0; ; i++) {
    try {
      return await fn();
    } catch (e) {
      if (i >= 3) throw e;
      await new Promise((r) => setTimeout(r, 2000 * 2 ** i));
    }
  }
}

function presign(c, key, expires) {
  const sig = sign(c, `GET\n\n\n${expires}\n/${c.bucket}/${key}`);
  return `${objectUrl(c, key)}?OSSAccessKeyId=${encodeURIComponent(c.id)}&Expires=${expires}&Signature=${encodeURIComponent(sig)}`;
}

async function main() {
  const days = Math.min(7, Number(arg('days', 7)));
  const concurrency = Number(arg('concurrency', 8));
  const c = ossConfig();
  console.log('旧报表数字：', writeReports(BUNDLE));

  const media = JSON.parse(fs.readFileSync(path.join(BUNDLE, 'media.json'), 'utf8'));
  const files = [...new Set(media.map((m) => m.file))];
  const missing = files.filter((f) => !fs.existsSync(path.join(ATTACHMENTS, f)));
  if (missing.length) throw new Error(`附件目录里缺 ${missing.length} 个文件，例如 ${missing.slice(0, 3).join('、')}`);
  let done = 0;
  let uploaded = 0;
  const queue = [...files];
  await Promise.all(Array.from({ length: concurrency }, async () => {
    for (let f = queue.shift(); f; f = queue.shift()) {
      const key = PREFIX + f;
      if (!(await retry(() => exists(c, key)))) {
        await retry(() => put(c, key, f));
        uploaded++;
      }
      if (++done % 200 === 0 || done === files.length) console.log(`图片 ${done}/${files.length}（本次上传 ${uploaded}）`);
    }
  }));

  const expires = Math.floor(Date.now() / 1000) + days * 86400;
  const staging = fs.mkdtempSync(path.join(OUT, 'pack-'));
  for (const name of fs.readdirSync(BUNDLE).filter((n) => n.endsWith('.json'))) {
    fs.copyFileSync(path.join(BUNDLE, name), path.join(staging, name));
  }
  fs.writeFileSync(path.join(staging, 'media.json'), JSON.stringify(media.map((m) => ({ ...m, url: presign(c, PREFIX + m.file, expires) }))));
  const manifest = JSON.parse(fs.readFileSync(path.join(BUNDLE, 'manifest.json'), 'utf8'));
  manifest.linksExpireAt = new Date(expires * 1000).toISOString();
  fs.writeFileSync(path.join(staging, 'manifest.json'), JSON.stringify(manifest, null, 2));

  const packDir = path.join(OUT, 'pack');
  fs.mkdirSync(packDir, { recursive: true });
  const zip = path.join(packDir, `迁移包-${manifest.version}.zip`);
  fs.rmSync(zip, { force: true });
  execFileSync('zip', ['-q', '-j', zip, ...fs.readdirSync(staging).map((n) => path.join(staging, n))]);
  fs.rmSync(staging, { recursive: true, force: true });
  console.log(`迁移包：${zip}（${(fs.statSync(zip).size / 1048576).toFixed(1)} MB），图片链接 ${manifest.linksExpireAt} 到期，到期前在生产上执行完；过期重新运行本脚本即可`);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
