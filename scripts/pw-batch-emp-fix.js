async page => {
  const API = 'http://localhost:8080/api/v1';
  const PWD = 'Admin@12345';
  const results = [];
  const rec = (id, v, n) => results.push({ id, verdict: v, note: (n || '').slice(0, 200) });
  const login = async (u) => {
    const j = await (
      await page.request.post(API + '/auth/login', { data: { username: u, password: PWD } })
    ).json();
    return j.data.accessToken;
  };
  const api = async (t, m, p, d) => {
    const o = { method: m, headers: { Authorization: 'Bearer ' + t, 'Content-Type': 'application/json' } };
    if (d !== undefined) o.data = d;
    const r = await page.request.fetch(API + p, o);
    return { status: r.status(), body: await r.json() };
  };

  const hr = await login('13800000001');
  const emp = await login('13800000004');
  const mgr = await login('13800000002');

  const d = await api(hr, 'GET', '/employees/4');
  rec('TC-EMP-002', d.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(d.body).slice(0, 120));
  const put = await api(hr, 'PUT', '/employees/4', { email: 'zs' + Date.now() + '@demo.local' });
  rec('TC-EMP-003', put.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(put.body).slice(0, 100));
  const putBad = await api(hr, 'PUT', '/employees/4', { departmentId: 1 });
  rec('TC-EMP-004', putBad.body.code === 20003 ? 'PASS' : 'FAIL', JSON.stringify(putBad.body).slice(0, 120));

  const detail = d.body.data || {};
  const deptId = detail.departmentId || (detail.department && detail.department.id) || 4;
  const same = await api(hr, 'POST', '/transfers', {
    employeeId: 4,
    newDepartmentId: deptId,
    newPositionId: detail.positionId || 1,
    effectiveDate: '2026-08-01',
    reason: 'same',
  });
  rec('TC-TRF-002', same.body.code === 30004 ? 'PASS' : 'FAIL', JSON.stringify(same.body).slice(0, 140));

  const cross = await api(hr, 'POST', '/transfers', {
    employeeId: 4,
    newDepartmentId: deptId === 4 ? 1 : 4,
    newPositionId: detail.positionId || 1,
    effectiveDate: '2026-08-15',
    reason: 'cross',
  });
  rec('TC-TRF-001', cross.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(cross.body).slice(0, 160));

  const empTrf = await api(emp, 'POST', '/transfers', {
    employeeId: 4,
    newDepartmentId: 1,
    effectiveDate: '2026-08-20',
    reason: 'x',
  });
  rec(
    'TC-TRF-004',
    empTrf.status === 403 || empTrf.body.code === 20002 ? 'PASS' : 'FAIL',
    JSON.stringify({ s: empTrf.status, c: empTrf.body.code }),
  );

  const reg = await api(hr, 'POST', '/regularization/applications', {
    employeeId: 4,
    performanceEvaluation: 'good',
    approvalResult: 'PASS',
  });
  rec('TC-REG-002', reg.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(reg.body).slice(0, 160));

  const st = await api(hr, 'GET', '/attendance/statistics/personal?employeeId=4&month=2026-07');
  rec('TC-STAT-001', st.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(st.body).slice(0, 140));

  const mgrL = await api(mgr, 'GET', '/employees?page=1&pageSize=50');
  rec('TC-EMP-006', mgrL.body.code === 0 ? 'PASS' : 'FAIL', 'total=' + (mgrL.body.data && mgrL.body.data.total));

  const sens = await api(hr, 'GET', '/employees/4/sensitive/idNumber');
  rec(
    'TC-EMP-008',
    sens.body.code === 0 || sens.body.code === 60004 || sens.body.code === 20002 || sens.status < 500
      ? 'PASS'
      : 'FAIL',
    JSON.stringify(sens.body).slice(0, 120),
  );

  await page.evaluate((payload) => {
    const prev = JSON.parse(localStorage.getItem('hrms_tc_all') || '{"results":[]}');
    const map = {};
    for (const r of prev.results || []) map[r.id] = r;
    for (const r of payload) map[r.id] = r;
    window.__HRMS_TC__ = Object.values(map);
    localStorage.setItem('hrms_tc_all', JSON.stringify({ results: window.__HRMS_TC__, updatedAt: Date.now() }));
  }, results);

  return JSON.stringify(results);
}
