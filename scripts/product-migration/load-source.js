'use strict';
// 读取独立站的 js/data.js。这个文件是浏览器脚本（用 const 声明），不是模块，
// 所以放进 vm 沙箱里执行，再取出需要的常量。
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');

const DEFAULT_SOURCE = path.join(os.homedir(), 'Documents/llm-wiki/fouwell-website/js/data.js');

function loadSource(file = process.env.FOUWELL_DATA_JS || DEFAULT_SOURCE) {
  const code = fs.readFileSync(file, 'utf8');
  const sandbox = {};
  vm.createContext(sandbox);
  vm.runInContext(
    `${code}\n;globalThis.__out = { PRODUCTS, BRANDS, CATEGORIES, NON_GENUINE_BRANDS: [...NON_GENUINE_BRANDS] };`,
    sandbox,
    { filename: file },
  );
  return { ...sandbox.__out, file };
}

module.exports = { loadSource, DEFAULT_SOURCE };
