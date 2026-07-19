const fs = require('fs');
const path = require('path');

const data = JSON.parse(fs.readFileSync(path.join(__dirname, '../docs/tc-results.json'), 'utf8'));

const script = `async (page) => {
  const data = ${JSON.stringify(data)};
  await page.goto('http://localhost:8000/login');
  await page.evaluate((data) => {
    localStorage.setItem('hrms_tc_all', JSON.stringify(data));
    const map = {};
    for (const r of data.results || []) map[r.id] = r;
    localStorage.setItem('__HRMS_TC__', JSON.stringify(map));
    window.__HRMS_TC__ = data.results;
  }, data);
  return (data.results || []).length;
}
`;

fs.writeFileSync(path.join(__dirname, '_inject-tc.js'), script);
console.log('wrote inject, count=', (data.results || []).length);
