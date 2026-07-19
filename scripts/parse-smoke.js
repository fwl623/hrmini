const fs = require('fs');

function tryParse(file) {
  const raw = fs.readFileSync(file, 'utf8');
  // Case 1: plain JSON
  try {
    const j = JSON.parse(raw);
    return typeof j === 'string' ? JSON.parse(j) : j;
  } catch (_) {}

  // Case 2: CLI wraps as "{\n ... }"
  const quoteStart = raw.indexOf('"{\\n');
  if (quoteStart >= 0) {
    // find matching end quote before ### or code dump
    let i = quoteStart + 1;
    let escaped = false;
    let end = -1;
    for (; i < raw.length; i++) {
      const c = raw[i];
      if (escaped) {
        escaped = false;
        continue;
      }
      if (c === '\\') {
        escaped = true;
        continue;
      }
      if (c === '"') {
        end = i;
        break;
      }
    }
    if (end > quoteStart) {
      const lit = raw.slice(quoteStart, end + 1);
      const unescaped = JSON.parse(lit);
      return JSON.parse(unescaped);
    }
  }

  // Case 3: find summary object
  const idx = raw.indexOf('"summary"');
  if (idx > 0) {
    const start = raw.lastIndexOf('{', idx);
    // naive brace match
    let depth = 0;
    for (let i = start; i < raw.length; i++) {
      if (raw[i] === '{') depth++;
      if (raw[i] === '}') {
        depth--;
        if (depth === 0) {
          return JSON.parse(raw.slice(start, i + 1));
        }
      }
    }
  }
  throw new Error('Cannot parse ' + file);
}

let data;
try {
  data = tryParse('docs/pw-smoke-result.json');
} catch (e1) {
  data = tryParse('docs/pw-smoke-raw.txt');
}

fs.writeFileSync('docs/pw-smoke-parsed.json', JSON.stringify(data, null, 2), 'utf8');
console.log('SUMMARY', JSON.stringify(data.summary));
console.log('\nFAILS:');
(data.results || [])
  .filter((r) => r.verdict === 'FAIL')
  .forEach((r) => {
    console.log('-', r.id, r.path, '|', r.note);
    if (r.apiFails && r.apiFails.length) console.log('  api:', JSON.stringify(r.apiFails));
  });
console.log('\nALL:');
(data.results || []).forEach((r) => console.log(r.verdict, r.id, r.path, r.note || ''));
