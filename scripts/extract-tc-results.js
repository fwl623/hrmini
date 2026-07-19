const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const outPath = path.resolve(__dirname, '../docs/tc-results.json');
let map = {};

// merge existing file
if (fs.existsSync(outPath)) {
  try {
    const prev = JSON.parse(fs.readFileSync(outPath, 'utf8'));
    for (const r of prev.results || []) if (r && r.id) map[r.id] = r;
  } catch (e) {}
}

function ingest(raw) {
  let data;
  try {
    data = JSON.parse(String(raw).trim());
  } catch (e) {
    const m = String(raw).match(/(\{[\s\S]*\}|\[[\s\S]*\])/);
    if (!m) return;
    data = JSON.parse(m[1]);
  }
  if (typeof data === 'string') {
    try {
      data = JSON.parse(data);
    } catch (e) {
      return;
    }
  }
  let arr = [];
  if (Array.isArray(data)) arr = data;
  else if (data && Array.isArray(data.results)) arr = data.results;
  else if (data && typeof data === 'object') arr = Object.values(data);
  for (const r of arr) {
    if (r && r.id && r.verdict) map[r.id] = r;
  }
}

try {
  const out1 = execSync(
    'playwright-cli --raw eval "JSON.stringify(window.__HRMS_TC__ || null)"',
    { cwd: path.resolve(__dirname, '..'), encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 },
  );
  ingest(out1);
} catch (e) {
  console.error('eval __HRMS_TC__ failed', e.message);
}

try {
  const out2 = execSync(
    'playwright-cli --raw eval "localStorage.getItem(\'hrms_tc_all\')"',
    { cwd: path.resolve(__dirname, '..'), encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 },
  );
  ingest(out2);
} catch (e) {
  console.error('eval hrms_tc_all failed', e.message);
}

const results = Object.values(map).filter((r) => r && r.id && !String(r.id).startsWith('__'));
const summary = {
  total: results.length,
  pass: results.filter((r) => r.verdict === 'PASS').length,
  fail: results.filter((r) => r.verdict === 'FAIL').length,
  blocked: results.filter((r) => r.verdict === 'BLOCKED').length,
  skipped: results.filter((r) => r.verdict === 'SKIPPED').length,
  info: results.filter((r) => r.verdict === 'INFO').length,
};

fs.writeFileSync(outPath, JSON.stringify({ summary, results, updatedAt: new Date().toISOString() }, null, 2), 'utf8');
console.log(JSON.stringify(summary, null, 2));
console.log('--- FAIL ---');
results.filter((r) => r.verdict === 'FAIL').forEach((r) => console.log(r.id + ': ' + (r.note || '').slice(0, 160)));
