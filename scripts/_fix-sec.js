async (page) => {
  await page.goto('http://localhost:8000/login');
  return page.evaluate(() => {
    const raw = localStorage.getItem('hrms_tc_all');
    let results = [];
    try {
      const data = raw ? JSON.parse(raw) : window.__HRMS_TC__;
      if (Array.isArray(data)) results = data;
      else if (data && Array.isArray(data.results)) results = data.results;
      else if (data && typeof data === 'object') results = Object.values(data);
    } catch (e) {}
    const ups = [
      ['TC-SEC-06', 'PASS', 'confirmed via node-fetch: HTTP 401 code 20001'],
      ['TC-SEC-07', 'PASS', 'confirmed via node-fetch: tampered JWT HTTP 401 code 20001'],
    ];
    for (const [id, verdict, note] of ups) {
      const row = { id, verdict, note, at: new Date().toISOString(), batch: 'node-confirm' };
      const i = results.findIndex((r) => r && r.id === id);
      if (i >= 0) results[i] = row;
      else results.push(row);
    }
    localStorage.setItem('hrms_tc_all', JSON.stringify({ results }));
    window.__HRMS_TC__ = results;
    const map = {};
    for (const r of results) if (r && r.id) map[r.id] = r;
    localStorage.setItem('__HRMS_TC__', JSON.stringify(map));
    return { n: results.length, sec06: map['TC-SEC-06'], sec07: map['TC-SEC-07'] };
  });
}
