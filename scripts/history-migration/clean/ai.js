'use strict';
// AI 拆分（design.md 决策 3）：经本机 Claude Code 订阅调用 `claude -p`，结果按「内容哈希 + 提示词版本」缓存。
// 简单的记录（单一型号、纯数字进价）用规则处理，不调用 AI。
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');
const r = require('../rules');

const OUT_DIR = path.resolve(process.env.HM_OUT_DIR || path.join(__dirname, '../out'));
const CACHE_DIR = path.join(OUT_DIR, 'ai-cache');
const WORK_DIR = path.join(OUT_DIR, 'ai-work'); // claude -p 的工作目录：不在仓库里，不会加载项目的 CLAUDE.md
const PROMPT_VERSION = { lines: 'lines-v1', quotes: 'quotes-v1', attachment: 'att-v1', pi: 'pi-v1' };

class UsageLimitError extends Error {}

// ---------- 调用与缓存 ----------

/**
 * 调用一次 claude -p。返回解析后的 JSON 对象（取输出里第一段 JSON）。
 * 额度用完时抛 UsageLimitError，其他失败抛 Error。
 */
function runClaude({ prompt, model, allowRead = false, cwd = WORK_DIR, timeoutMs = 300000 }) {
  fs.mkdirSync(cwd, { recursive: true });
  const args = ['-p', '--model', model, '--output-format', 'json'];
  if (allowRead) args.push('--allowedTools', 'Read');
  return new Promise((resolve, reject) => {
    const child = spawn('claude', args, { cwd, stdio: ['pipe', 'pipe', 'pipe'] });
    let out = ''; let err = '';
    const timer = setTimeout(() => { child.kill('SIGTERM'); reject(new Error('调用超时')); }, timeoutMs);
    child.stdout.on('data', (b) => { out += b; });
    child.stderr.on('data', (b) => { err += b; });
    child.on('close', () => {
      clearTimeout(timer);
      let env;
      try { env = JSON.parse(out); } catch { return reject(new Error(`输出不是 JSON：${(err || out).slice(0, 300)}`)); }
      const text = String(env.result ?? '');
      if (env.is_error) {
        if (/limit|usage|quota|额度/i.test(text)) return reject(new UsageLimitError(text.slice(0, 200)));
        return reject(new Error(`调用失败：${text.slice(0, 300)}`));
      }
      const json = extractJson(text);
      if (json === undefined) return reject(new Error(`结果里没有 JSON：${text.slice(0, 300)}`));
      resolve({ json, costUsd: env.total_cost_usd || 0 });
    });
    child.stdin.end(prompt);
  });
}

/** 从模型输出里取第一段 JSON（可能包在 ```json 代码块里） */
function extractJson(text) {
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/);
  const body = (fenced ? fenced[1] : text).trim();
  const start = body.search(/[[{]/);
  if (start < 0) return undefined;
  for (let end = body.length; end > start; end--) {
    if (!'}]'.includes(body[end - 1])) continue;
    try { return JSON.parse(body.slice(start, end)); } catch { /* 继续向前找 */ }
  }
  return undefined;
}

const hashOf = (...parts) => crypto.createHash('sha1').update(parts.map((p) => JSON.stringify(p)).join('\u0001')).digest('hex').slice(0, 20);
const cacheFile = (kind, key) => path.join(CACHE_DIR, kind, `${key}.json`);

function readCache(kind, key) {
  const f = cacheFile(kind, key);
  return fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')) : null;
}
function writeCache(kind, key, value) {
  const f = cacheFile(kind, key);
  fs.mkdirSync(path.dirname(f), { recursive: true });
  fs.writeFileSync(f, JSON.stringify(value));
}

// ---------- 品牌提示 ----------

/** 旧系统品牌「Siemens (西门子)」→「Siemens」；「其他」和空返回 '' */
function brandHint(raw) {
  const s = r.clean(raw).split(/,\s*/)[0];
  if (!s || s === '其他') return '';
  return s.replace(/\s*[（(].*$/, '').trim() || s;
}

// ---------- 型号文本拆分（询盘、订单） ----------

/** 单一型号（一个词、无中文与分隔符、含数字）：用规则，不调 AI */
function isSimpleModel(text) {
  const s = r.clean(text);
  return !!s && !/\n/.test(s) && !/[一-龥:：,，;；]/.test(s) && s.split(/\s+/).length === 1 && s.length <= 40 && /[0-9]/.test(s);
}

/**
 * 型号拆分的输入记录：{ id, brand, category, text, info, quantity }
 * 输出（每条记录）：{ lines: [{ brand, model, quantity, condition, description, confidence }], seeAttachment, by }
 */
function ruleLines(rec) {
  if (isSimpleModel(rec.text)) {
    const q = Number(rec.quantity);
    return { lines: [{ brand: rec.brand, model: r.clean(rec.text), quantity: q > 0 ? q : null, condition: '', description: '', confidence: 'high' }], seeAttachment: false, by: 'rule' };
  }
  if (!r.clean(rec.text) && !r.clean(rec.info)) return { lines: [], seeAttachment: true, by: 'rule' };
  return null;
}

function linesPrompt(batch) {
  return `你是工控产品（PLC、变频器、伺服、HMI、传感器、接触器等）型号解析器。下面是 ${batch.length} 条外贸询盘或销售订单记录，JSON 数组，每条有：
id；brand（系统登记的品牌，可能为空）；category（品类）；text（型号栏原文）；info（询盘信息原文，可能为空）；quantity（整条记录的总数量，可能为空）。

请把每条记录拆成型号行，规则：
1. 一个型号一行；同一记录里同一型号出现多次时合并为一行，数量相加。
2. model 保留原文写法（去掉数量、单价、成色、品牌等文字），不要补全或编造；没有可识别型号时 lines 为空数组。
3. brand：文本里写了品牌就用文本的，否则用记录的 brand；都没有为空字符串。
4. quantity：文本写明的数量；只有一个型号且文本没写时用记录的 quantity；否则为 null。
5. condition：文本出现「全新、原装、翻新、二手、拆机、国产、99新」等成色词时填原文词，否则为空字符串。
6. description：型号旁边的产品说明（如「伺服驱动器 750W」），不超过 60 个字，没有为空字符串。
7. confidence：high（明确是型号）、medium（像型号但不确定）、low（猜测）。
8. seeAttachment：文本表示「见附件、见图、看铭牌照片」或型号明显不全、需要看附件时为 true，否则为 false。

只输出 JSON，格式：{"records":[{"id":"…","seeAttachment":false,"lines":[{"brand":"","model":"","quantity":null,"condition":"","description":"","confidence":"high"}]}]}

记录：
${JSON.stringify(batch)}`;
}

// ---------- 询价进价抽取 ----------

/**
 * 询价的输入记录：{ id, model（询盘型号原文）, price（进价原文）, note, condition, leadTime }
 * 输出：{ quotes: [{ model, unitPrice, quantity, taxIncluded, noStock, condition, leadTime, note }], by }
 */
function ruleQuotes(rec) {
  const p = r.clean(rec.price);
  if (/^\d+(\.\d+)?$/.test(p) && isSimpleModel(rec.model)) {
    return { quotes: [{ model: r.clean(rec.model), unitPrice: p, quantity: null, taxIncluded: null, noStock: false, condition: '', leadTime: '', note: '' }], by: 'rule' };
  }
  if (!p) return { quotes: [], by: 'rule' };
  return null;
}

function quotesPrompt(batch) {
  return `你是工控产品采购询价记录解析器。下面是 ${batch.length} 条采购向供应商询价的记录，JSON 数组，每条有：
id；model（客户询盘的型号原文，可能有多个型号）；price（采购填写的「进价」原文，常把型号、单价、数量、货期写在一起）；note（备注）；condition（成色）；leadTime（货期）。

请从 price（必要时参考 model、note）中抽出每个型号的报价，规则：
1. 一个型号一条；price 只有一个数字且 model 只有一个型号时，就是这个型号的单价。
2. unitPrice：人民币单价，只填数字字符串（如 "1350"）；写的是总价且有数量时换算成单价；同一型号有多个价格（不同成色或不同货源）分成多条。
3. model：价格对应的型号原文；price 里没写型号时用 model 里的型号。
4. quantity：写明的数量，否则 null。
5. taxIncluded：写了「含税、含票」为 true，写了「未税、不含税」为 false，没写为 null。
6. noStock：写了「无货、停产、没货、找不到」为 true，此时 unitPrice 为 null。
7. condition、leadTime：price 里写到的成色、货期原文，没有为空字符串（记录本身的 condition、leadTime 不用重复填）。
8. note：运费、质保等其他说明，不超过 60 个字。
9. 实在无法判断价格的，quotes 为空数组。

只输出 JSON，格式：{"records":[{"id":"…","quotes":[{"model":"","unitPrice":"","quantity":null,"taxIncluded":null,"noStock":false,"condition":"","leadTime":"","note":""}]}]}

记录：
${JSON.stringify(batch)}`;
}

// ---------- 附件解析 ----------

function attachmentPrompt(file, context) {
  return `读取文件 ./${file}。这是外贸询盘或订单的附件（截图、铭牌照片、型号清单或报价单），相关记录：${JSON.stringify(context)}。
请识别其中的工控产品型号，规则：
1. 一个型号一行，保留原文写法，不要补全或编造；只有产品说明、没有型号的行不要输出。
2. brand 写图里或记录里的品牌；quantity 写明才填，否则 null；description 为型号旁的说明，不超过 60 个字。
3. confidence：high、medium、low。
4. 图里看不到任何型号时 lines 为空数组。
只输出 JSON：{"lines":[{"brand":"","model":"","quantity":null,"description":"","confidence":"high"}]}`;
}

function piPrompt(file, context) {
  return `读取文件 ./${file}。这是外贸销售订单的附件，可能是形式发票（PI）或商业发票（INV），也可能不是。相关订单：${JSON.stringify(context)}。
如果是 PI 或发票，请逐行抽出：model（型号原文）、quantity（数量）、unitPrice（单价，数字字符串）、amount（行金额，数字字符串）；currency 为币种（如 USD）；total 为合计。
不是 PI 或发票时 isInvoice 为 false、lines 为空数组。
只输出 JSON：{"isInvoice":true,"currency":"USD","total":"","lines":[{"model":"","quantity":null,"unitPrice":"","amount":""}]}`;
}

/** 表格附件（xlsx）先转成文本交给 Haiku，不需要读文件工具 */
function sheetPrompt(text, context) {
  return `下面是外贸销售订单附件中一个 Excel 表格的内容（制表符分隔），可能是形式发票（PI）或商业发票（INV），也可能不是。相关订单：${JSON.stringify(context)}。
如果是 PI 或发票，请逐行抽出：model（型号原文）、quantity（数量）、unitPrice（单价，数字字符串）、amount（行金额，数字字符串）；currency 为币种；total 为合计。
不是 PI 或发票时 isInvoice 为 false、lines 为空数组。
只输出 JSON：{"isInvoice":true,"currency":"USD","total":"","lines":[{"model":"","quantity":null,"unitPrice":"","amount":""}]}

表格内容：
${text.slice(0, 20000)}`;
}

// ---------- 批量执行 ----------

/**
 * 按批处理一类记录，结果写缓存（每条记录一个缓存文件）。已缓存或规则可处理的不调用 AI。
 *   kind：'lines' | 'quotes'
 * 返回 { done, cached, rule, failed, costUsd, stoppedByLimit }。
 */
async function runBatches(kind, records, { batchSize = 25, concurrency = 3, model = 'haiku', limit = Infinity, log = console.log } = {}) {
  const rule = kind === 'lines' ? ruleLines : ruleQuotes;
  const prompt = kind === 'lines' ? linesPrompt : quotesPrompt;
  const version = PROMPT_VERSION[kind];
  const stats = { done: 0, cached: 0, rule: 0, failed: 0, costUsd: 0, stoppedByLimit: false };
  const pending = [];
  for (const rec of records) {
    const key = hashOf(version, rec);
    if (readCache(kind, key)) { stats.cached++; continue; }
    const byRule = rule(rec);
    if (byRule) { writeCache(kind, key, { id: rec.id, ...byRule }); stats.rule++; continue; }
    pending.push({ rec, key });
  }
  const batches = [];
  for (let i = 0; i < pending.length && batches.length * batchSize < limit; i += batchSize) batches.push(pending.slice(i, i + batchSize));
  log(`${kind}：已缓存 ${stats.cached}，规则处理 ${stats.rule}，待 AI 处理 ${pending.length} 条，本次 ${batches.length} 批`);

  let next = 0;
  async function worker() {
    while (next < batches.length && !stats.stoppedByLimit) {
      const batch = batches[next++];
      try {
        const { json, costUsd } = await runClaude({ prompt: prompt(batch.map((b) => b.rec)), model });
        stats.costUsd += costUsd;
        const byId = new Map((json.records || []).map((x) => [String(x.id), x]));
        for (const { rec, key } of batch) {
          const got = byId.get(String(rec.id));
          if (!got) { stats.failed++; continue; } // 模型漏掉的记录下次再跑
          writeCache(kind, key, { ...got, id: rec.id, by: 'ai' });
          stats.done++;
        }
      } catch (e) {
        if (e instanceof UsageLimitError) { stats.stoppedByLimit = true; log(`额度用完，停止：${e.message}`); return; }
        stats.failed += batch.length;
        log(`一批失败（下次重试）：${e.message}`);
      }
      log(`  进度：AI 完成 ${stats.done}，失败 ${stats.failed}，累计折算费用 $${stats.costUsd.toFixed(2)}`);
    }
  }
  await Promise.all(Array.from({ length: concurrency }, worker));
  return stats;
}

/**
 * 解析单个附件（图片、PDF 交给 Sonnet 读文件；xlsx 转文本交给 Haiku）。
 *   job：{ token, file（附件目录下的文件名）, absPath, mime, mode: 'models' | 'pi', context }
 * 结果写缓存 'attachment'，返回解析结果；额度用完抛 UsageLimitError。
 */
async function parseAttachment(job, { readSheet } = {}) {
  const version = PROMPT_VERSION[job.mode === 'pi' ? 'pi' : 'attachment'];
  const key = hashOf(version, job.mode, job.token);
  const cached = readCache('attachment', key);
  if (cached) return { ...cached, cached: true };
  let res;
  if (/sheet|excel/.test(job.mime) && readSheet) {
    const text = readSheet(job.absPath);
    res = await runClaude({ prompt: job.mode === 'pi' ? sheetPrompt(text, job.context) : `${attachmentPrompt('（见下方表格内容）', job.context)}\n\n表格内容：\n${text.slice(0, 20000)}`, model: 'haiku' });
  } else {
    const prompt = job.mode === 'pi' ? piPrompt(job.file, job.context) : attachmentPrompt(job.file, job.context);
    res = await runClaude({ prompt, model: 'sonnet', allowRead: true, cwd: path.dirname(job.absPath) });
  }
  const value = { token: job.token, mode: job.mode, ...res.json, costUsd: res.costUsd };
  writeCache('attachment', key, value);
  return value;
}

/** 读取缓存结果（清洗汇总时用）；没有缓存返回 null */
function cachedResult(kind, rec) {
  return readCache(kind, hashOf(PROMPT_VERSION[kind], rec));
}
function cachedAttachment(token, mode) {
  return readCache('attachment', hashOf(PROMPT_VERSION[mode === 'pi' ? 'pi' : 'attachment'], mode, token));
}

module.exports = {
  runClaude, extractJson, runBatches, parseAttachment, cachedResult, cachedAttachment,
  brandHint, isSimpleModel, ruleLines, ruleQuotes, linesPrompt, quotesPrompt, UsageLimitError, PROMPT_VERSION,
};
