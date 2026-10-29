'use strict';
// 把 pull.js 读到的飞书原始记录解码成「字段名 → 文本」，并按单号对上导出 Excel 的行。
// 飞书原始记录里没有公式和引用字段的值（由网页端计算），这两类字段解码为 ''，以 Excel 为准。
const fs = require('node:fs');
const path = require('node:path');

const OUT_DIR = path.resolve(process.env.HM_OUT_DIR || path.join(__dirname, '../out'), 'feishu');

const pad = (x) => String(x).padStart(2, '0');
/** 毫秒时间戳 → 北京时间 'YYYY-MM-DD HH:mm:ss' */
function msToText(ms) {
  const d = new Date(Number(ms) + 8 * 3600 * 1000);
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} `
    + `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`;
}

function optionNames(field, ids) {
  const opts = (field.property && field.property.options) || [];
  return ids.map((id) => (opts.find((o) => o.id === id) || {}).name ?? id);
}

/** 单元格 → 文本；关联字段返回 ''（另由 links 给出记录 ID） */
function cellText(field, cell) {
  const v = cell && typeof cell === 'object' && 'value' in cell ? cell.value : cell;
  if (v === null || v === undefined) return '';
  switch (field.type) {
    case 1: // 多行文本：分段
      return Array.isArray(v) ? v.map((s) => s.text ?? s.link ?? '').join('') : String(v);
    case 2: case 13: return String(v);
    case 3: return optionNames(field, [v])[0];
    case 4: return optionNames(field, v).join(', ');
    case 5: return msToText(v);
    case 7: return v ? 'TRUE' : 'FALSE';
    case 11: case 1003: case 1004: return (v.users || []).map((u) => u.name).join(', ');
    case 15: return Array.isArray(v) ? v.map((s) => s.link || s.text).join('') : String(v.link ?? v);
    case 17: return v.map((a) => a.name).join(',');
    case 1001: case 1002: return msToText(Number(v) * 1000);
    case 1005: return v.map((x) => x.number).join(',');
    default: return '';
  }
}

/**
 * 读取并解码一张表。返回 { key, tableId, fields, rows }：
 * rows 每行为 { __recordId, __createdTime, 字段名: 文本, __links: { 字段名: [记录ID] }, __attachments: [附件] }。
 */
function loadTable(key, dir = OUT_DIR) {
  const file = path.join(dir, 'tables', `${key}.json`);
  if (!fs.existsSync(file)) return null;
  const t = JSON.parse(fs.readFileSync(file, 'utf8'));
  const rows = Object.entries(t.records).map(([recordId, rec]) => {
    const row = { __recordId: recordId, __links: {}, __attachments: [] };
    for (const [fid, field] of Object.entries(t.fields)) {
      const cell = rec[fid];
      row[field.name] = cellText(field, cell);
      if ((field.type === 18 || field.type === 21) && Array.isArray(cell && cell.value)) row.__links[field.name] = cell.value;
      if (field.type === 17 && Array.isArray(cell && cell.value)) {
        for (const a of cell.value) row.__attachments.push({ token: a.attachmentToken, name: a.name, mime: a.mimeType, size: a.size, field: field.name });
      }
    }
    return row;
  });
  return { key, tableId: t.tableId, fields: t.fields, rows };
}

/** 附件下载索引：{ token: { status, file, … } }；还没下载过时为空对象 */
function loadAttachmentIndex(dir = OUT_DIR) {
  const file = path.join(dir, 'attachments.json');
  return fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : {};
}

/** 附件在本机的路径；没下载成功返回 null */
function attachmentPath(index, token, dir = OUT_DIR) {
  const e = index[token];
  return e && e.status === 'ok' ? path.join(dir, 'attachments', e.file) : null;
}

module.exports = { loadTable, loadAttachmentIndex, attachmentPath, cellText, msToText, OUT_DIR };
