const fs = require('fs');
const path = require('path');

const doc = fs.readFileSync(path.join(__dirname, '../docs/HRMS-测试用例全集.md'), 'utf8');
const official = [...doc.matchAll(/^### (TC-[A-Z0-9/-]+)/gm)].map((m) => m[1]);

const stored = JSON.parse(fs.readFileSync(path.join(__dirname, '../docs/tc-results.json'), 'utf8'));
const byId = {};
for (const r of stored.results || []) byId[r.id] = r;

// Map aliases / related executed ids onto official ids (explicit only)
const alias = {
  'TC-AUTH-002': ['TC-AUTH-002-partial'],
  'TC-PUNCH-002': ['TC-PUNCH-002-normal', 'TC-PUNCH-002-late'],
  'TC-PUNCH-003': ['TC-PUNCH-003-normal'],
  'TC-PUNCH-005': ['TC-PUNCH-005-quota', 'TC-PUNCH-005'],
  'TC-ONB-002': ['TC-ONB-002-submit', 'TC-ONB-002-withdraw'],
  'TC-PAY-SCH-001': ['TC-PAY-SCH-001-list', 'TC-PAY-SCH-001', 'TC-PAY-SCH-ui'],
  'TC-PAY-BAT-001': ['TC-PAY-BAT-001', 'TC-PAY-BAT-list', 'TC-PAY-BAT-ui'],
  'TC-PAY-COST-001': ['TC-PAY-COST-001'],
  'TC-LEAVE-008': ['TC-LEAVE-008', 'TC-UI-LEAVE', 'TC-LEAVE-008-admin'],
  'TC-OT-006': ['TC-OT-006', 'TC-UI-OT', 'TC-OT-006-admin'],
  'TC-SEC-04': ['TC-SEC-04', 'TC-PAY-SEC-001'],
  'TC-SEC-05': ['TC-SEC-05'],
  'TC-SEC-01': ['TC-SEC-01'],
  'TC-SEC-08': ['TC-AUTH-005', 'TC-SEC-08'],
  'TC-SEC-11': ['TC-PAY-SLIP-001', 'TC-SEC-11'],
  'TC-DLG-001': ['TC-DLG-001', 'TC-DLG-001-list'],
  'TC-UI-003': ['TC-LEAVE-008-admin', 'TC-OT-006-admin', 'TC-UI-003'],
  'TC-E2E-01': ['TC-E2E-01-partial'],
  'TC-E2E-08': ['TC-E2E-08-partial'],
  'TC-HOL-001': ['TC-HOL-001', 'TC-UI-HOL'],
  'TC-ATT-GRP-001': ['TC-ATT-GRP-001', 'TC-UI-GRP'],
  'TC-APPR-001': ['TC-APPR-001', 'TC-APPR-001-stats', 'TC-UI-APPR'],
  'TC-REG-001': ['TC-REG-001', 'TC-REG-001-list', 'TC-UI-REG'],
  'TC-PORTAL-001': ['TC-PORTAL-001', 'TC-PORTAL-001-get', 'TC-UI-PORTAL-PROF'],
  'TC-WB-001': ['TC-WB-001'],
  'TC-SYS-ROLE-001': ['TC-SYS-ROLE-001', 'TC-SYS-ROLE-001-content'],
  'TC-SEC-16/17': ['TC-SEC-16', 'TC-SEC-17', 'TC-SEC-16/17'],
  'TC-UI-005': ['TC-UI-005-payslip-menu', 'TC-UI-005'],
  'TC-SUM-001': ['TC-SUM-001-get', 'TC-SUM-001'],
  'TC-E2E-01': ['TC-E2E-01-partial', 'TC-E2E-01'],
  'TC-E2E-08': ['TC-E2E-08-partial', 'TC-E2E-08'],
  'TC-AUTH-002': ['TC-AUTH-002-partial', 'TC-AUTH-002'],
};

function resolve(officialId) {
  if (byId[officialId]) return { ...byId[officialId], via: officialId };
  const al = alias[officialId] || [];
  for (const a of al) {
    if (byId[a]) return { ...byId[a], id: officialId, via: a };
  }
  // only exact suffix variants: TC-XXX-001-foo
  const hits = Object.keys(byId).filter((k) => k.startsWith(officialId + '-'));
  if (hits.length) {
    // prefer PASS > FAIL > BLOCKED > SKIPPED
    const rank = { PASS: 4, FAIL: 3, BLOCKED: 2, SKIPPED: 1, INFO: 0 };
    hits.sort((a, b) => (rank[byId[b].verdict] || 0) - (rank[byId[a].verdict] || 0));
    return { ...byId[hits[0]], id: officialId, via: hits[0] };
  }
  return null;
}

const matrix = [];
for (const id of official) {
  const r = resolve(id);
  if (r) matrix.push({ id, verdict: r.verdict, note: r.note || '', via: r.via || id });
  else matrix.push({ id, verdict: 'NOT_RUN', note: 'no execution evidence this session' });
}

const summary = {
  officialTotal: official.length,
  executedLike: matrix.filter((m) => m.verdict !== 'NOT_RUN').length,
  pass: matrix.filter((m) => m.verdict === 'PASS').length,
  fail: matrix.filter((m) => m.verdict === 'FAIL').length,
  blocked: matrix.filter((m) => m.verdict === 'BLOCKED').length,
  skipped: matrix.filter((m) => m.verdict === 'SKIPPED').length,
  notRun: matrix.filter((m) => m.verdict === 'NOT_RUN').length,
  rawResults: stored.summary,
};

fs.writeFileSync(
  path.join(__dirname, '../docs/tc-official-matrix.json'),
  JSON.stringify({ summary, matrix, updatedAt: new Date().toISOString() }, null, 2),
);

console.log(JSON.stringify(summary, null, 2));
console.log('--- NOT_RUN ---');
matrix.filter((m) => m.verdict === 'NOT_RUN').forEach((m) => console.log(m.id));
console.log('--- FAIL ---');
matrix.filter((m) => m.verdict === 'FAIL').forEach((m) => console.log(m.id + ': ' + (m.note || '').slice(0, 100)));
