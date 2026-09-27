#!/usr/bin/env node
'use strict';
// 核对：specs 下每个 Scenario 都在 scenario-coverage.json 里登记了至少一个测试，
// 且登记的每个测试方法在后端测试源码里真实存在。通过只说明"场景都有对应测试"；
// 这些测试是否通过，要看后端全量测试（mvn test）的结果。
// 用法：node openspec/changes/archive/2026-09-20-add-product-master-core/check-scenario-coverage.js
const fs = require('node:fs');
const path = require('node:path');

const here = __dirname;
const specsDir = path.join(here, 'specs', 'product');
const testRoot = path.resolve(here, '../../../../zhul-erp-backend/src/test/java');
const map = JSON.parse(fs.readFileSync(path.join(here, 'scenario-coverage.json'), 'utf8'));

const walk = (dir) => fs.readdirSync(dir, { withFileTypes: true })
  .flatMap((e) => (e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]));
const testFiles = new Map(walk(testRoot).filter((f) => f.endsWith('.java')).map((f) => [path.basename(f, '.java'), f]));

const errors = [];
const scenarios = [];
for (const spec of fs.readdirSync(specsDir)) {
  const file = path.join(specsDir, spec, 'spec.md');
  if (!fs.existsSync(file)) continue;
  for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
    const m = line.match(/^#### Scenario: (.+)$/);
    if (m) scenarios.push(`${spec}/${m[1].trim()}`);
  }
}
for (const s of scenarios) {
  const refs = map[s];
  if (!refs || refs.length === 0) { errors.push(`场景没有对应测试：${s}`); continue; }
  for (const ref of refs) {
    const [cls, method] = ref.split('#');
    const file = testFiles.get(cls);
    if (!file) { errors.push(`测试类不存在：${ref}（场景 ${s}）`); continue; }
    if (!new RegExp(`void\\s+${method}\\s*\\(`).test(fs.readFileSync(file, 'utf8'))) errors.push(`测试方法不存在：${ref}（场景 ${s}）`);
  }
}
for (const k of Object.keys(map)) if (!scenarios.includes(k)) errors.push(`登记了不存在的场景：${k}`);

if (errors.length) { console.error(errors.join('\n')); process.exit(1); }
const refCount = new Set(Object.values(map).flat()).size;
console.log(`✓ ${scenarios.length} 个场景全部有对应测试（共引用 ${refCount} 个不同的测试方法）`);
