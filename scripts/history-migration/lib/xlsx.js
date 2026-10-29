'use strict';
// 最小的 xlsx 读取器：只读第一张工作表，返回「表头 → 单元格文本」的对象数组。
// 只用 Node 自带模块（zip 用 zlib.inflateRawSync 解），不引入第三方依赖。
const fs = require('node:fs');
const zlib = require('node:zlib');

/** 读出 zip 里的全部文件：{ 路径: Buffer } */
function unzip(buf) {
  // 从末尾找「中央目录结束」记录
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('不是有效的 xlsx（找不到 zip 目录）');
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const files = {};
  for (let n = 0; n < count; n++) {
    const method = buf.readUInt16LE(p + 10);
    const size = buf.readUInt32LE(p + 20);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    const name = buf.toString('utf8', p + 46, p + 46 + nameLen);
    const dataStart = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
    const raw = buf.subarray(dataStart, dataStart + size);
    files[name] = method === 0 ? raw : zlib.inflateRawSync(raw);
    p += 46 + nameLen + extraLen + commentLen;
  }
  return files;
}

const XML_ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };
function unescapeXml(s) {
  return s.replace(/&(#x[0-9a-f]+|#\d+|\w+);/gi, (m, e) => {
    if (e[0] === '#') return String.fromCodePoint(e[1] === 'x' || e[1] === 'X' ? parseInt(e.slice(2), 16) : Number(e.slice(1)));
    return XML_ENTITIES[e] ?? m;
  });
}

/** 一个 <si> 或 <is> 里所有 <t> 的文本拼起来（富文本有多段） */
function textOf(xml) {
  let out = '';
  for (const m of xml.matchAll(/<t(?:\s[^>]*)?>([\s\S]*?)<\/t>/g)) out += unescapeXml(m[1]);
  return out;
}

/** 列号 A、B、…、AA 转成从 0 开始的下标 */
function colIndex(ref) {
  let n = 0;
  for (const ch of ref.replace(/\d+$/, '')) n = n * 26 + (ch.charCodeAt(0) - 64);
  return n - 1;
}

// Excel 内置的日期格式编号
const BUILTIN_DATE_FMTS = new Set([14, 15, 16, 17, 18, 19, 20, 21, 22, 45, 46, 47]);

/** 哪些单元格样式是日期：返回 Set(样式下标) */
function dateStyles(stylesXml) {
  if (!stylesXml) return new Set();
  const custom = new Map();
  for (const m of stylesXml.matchAll(/<numFmt\s+numFmtId="(\d+)"\s+formatCode="([^"]*)"/g)) {
    custom.set(Number(m[1]), unescapeXml(m[2]));
  }
  const cellXfs = (stylesXml.match(/<cellXfs[^>]*>([\s\S]*?)<\/cellXfs>/) || [])[1] || '';
  const result = new Set();
  let i = 0;
  for (const m of cellXfs.matchAll(/<xf\b([^>]*)/g)) {
    const id = Number((m[1].match(/numFmtId="(\d+)"/) || [])[1] || 0);
    const code = custom.get(id);
    // 去掉引号里的文字和颜色等方括号内容后，含 y/d/h/s 才算日期时间
    const isDate = BUILTIN_DATE_FMTS.has(id) || (code && /[ydhs]/i.test(code.replace(/"[^"]*"|\[[^\]]*\]/g, '')));
    if (isDate) result.add(i);
    i++;
  }
  return result;
}

/** Excel 日期序列号 → 'YYYY-MM-DD HH:mm:ss'（工作簿按北京时间录入，不做时区换算） */
function serialToText(serial) {
  const ms = Math.round((Number(serial) - 25569) * 86400000);
  const d = new Date(ms);
  const pad = (x) => String(x).padStart(2, '0');
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} `
    + `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`;
}

/**
 * 读取 xlsx 第一张工作表。
 * 返回 { sheetName, headers, rows }，rows 为对象数组（键为表头，值为文本；空单元格为 ''）。
 * 全空的行跳过；每行带 __row（Excel 行号，从 1 开始，表头是第 1 行）。
 */
function readXlsx(file) {
  const files = unzip(fs.readFileSync(file));
  const workbook = files['xl/workbook.xml'].toString('utf8');
  const sheetName = unescapeXml((workbook.match(/<sheet\b[^>]*name="([^"]*)"/) || [])[1] || '');
  const sheetPath = Object.keys(files).filter((k) => /^xl\/worksheets\/sheet\d+\.xml$/.test(k)).sort()[0];
  const shared = [];
  if (files['xl/sharedStrings.xml']) {
    for (const m of files['xl/sharedStrings.xml'].toString('utf8').matchAll(/<si>([\s\S]*?)<\/si>/g)) shared.push(textOf(m[1]));
  }
  const dates = dateStyles(files['xl/styles.xml'] && files['xl/styles.xml'].toString('utf8'));
  const sheet = files[sheetPath].toString('utf8');

  const grid = [];
  for (const rm of sheet.matchAll(/<row\b([^>]*)>([\s\S]*?)<\/row>/g)) {
    const rowNo = Number((rm[1].match(/\br="(\d+)"/) || [])[1]);
    const cells = [];
    for (const cm of rm[2].matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const attrs = cm[1];
      const body = cm[2] || '';
      const ref = (attrs.match(/\br="([A-Z]+\d+)"/) || [])[1];
      const type = (attrs.match(/\bt="(\w+)"/) || [])[1];
      const style = Number((attrs.match(/\bs="(\d+)"/) || [])[1] || 0);
      const v = (body.match(/<v>([\s\S]*?)<\/v>/) || [])[1];
      let text = '';
      if (type === 's') text = shared[Number(v)] ?? '';
      else if (type === 'inlineStr') text = textOf(body);
      else if (type === 'str' || type === 'e') text = v === undefined ? '' : unescapeXml(v);
      else if (type === 'b') text = v === '1' ? 'TRUE' : 'FALSE';
      else if (v !== undefined) text = dates.has(style) ? serialToText(v) : v;
      cells[ref ? colIndex(ref) : cells.length] = text;
    }
    grid.push({ rowNo, cells });
  }
  if (!grid.length) return { sheetName, headers: [], rows: [] };
  const headers = Array.from(grid[0].cells, (h) => (h ?? '').trim());
  const rows = [];
  for (const { rowNo, cells } of grid.slice(1)) {
    if (!cells.some((c) => c !== undefined && String(c).trim() !== '')) continue;
    const row = { __row: rowNo };
    headers.forEach((h, i) => { if (h) row[h] = cells[i] ?? ''; });
    rows.push(row);
  }
  return { sheetName, headers, rows };
}

module.exports = { readXlsx, serialToText };
