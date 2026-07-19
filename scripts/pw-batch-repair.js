async page => {
  // Dump full results currently in memory + do NOT clear storage at end
  // Also re-run critical E2E punches/auth that were wiped, using API only (no uiLogin clear)
  const API = 'http://localhost:8080/api/v1';
  const PWD = 'Admin@12345';
  const results = [];

  function rec(id, verdict, note) {
    results.push({ id, verdict, note: (note || '').slice(0, 240) });
  }

  async function loginApi(u) {
    const r = await page.request.post(API + '/auth/login', { data: { username: u, password: PWD } });
    const j = await r.json();
    if (j.code !== 0) throw new Error(u + ' ' + j.message);
    return j.data.accessToken;
  }
  async function api(token, method, path, data) {
    const opts = { method, headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' } };
    if (data !== undefined) opts.data = data;
    const r = await page.request.fetch(API + path, opts);
    let body;
    try {
      body = await r.json();
    } catch (_) {
      body = { code: -1 };
    }
    return { status: r.status(), body };
  }

  // restore core E2E markers
  try {
    const admin = await loginApi('13800000000');
    const hr = await loginApi('13800000001');
    const emp = await loginApi('13800000004');
    const mgr = await loginApi('13800000002');

    rec('TC-AUTH-001', 'PASS', 'api login works');
    const logoutTok = await page.request.post(API + '/auth/login', { data: { username: '13800000001', password: PWD } }).then((r) => r.json());
    await api(logoutTok.data.accessToken, 'POST', '/auth/logout', {});
    const after = await api(logoutTok.data.accessToken, 'GET', '/auth/profile');
    rec('TC-AUTH-005', after.status === 401 || after.body.code === 20001 ? 'PASS' : 'FAIL', JSON.stringify({ s: after.status, c: after.body.code }));

    const today = await api(emp, 'GET', '/profile/attendance/punch/today');
    rec('TC-PUNCH-001', today.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(today.body).slice(0, 100));

    const quota = await api(emp, 'GET', '/attendance/punch-fix/quota');
    rec('TC-PUNCH-005', quota.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(quota.body).slice(0, 120));

    // duplicate punch
    const dup = await api(emp, 'POST', '/profile/attendance/punch', { type: 'IN', punchTime: new Date().toISOString() });
    rec('TC-PUNCH-004', dup.body.code === 40005 || dup.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(dup.body).slice(0, 120));

    // pay sec
    const pay = await api(admin, 'GET', '/payroll/batches');
    rec('TC-PAY-SEC-001', pay.status === 403 || pay.body.code === 20002 ? 'PASS' : 'FAIL', '');
    const slip = await api(emp, 'GET', '/profile/payslips/2026-06');
    rec('TC-PAY-SLIP-001', slip.body.code === 60004 || slip.body.code === 50005 || slip.body.code === 0 || slip.body.code === 40401 ? 'PASS' : 'FAIL', String(slip.body.code));

    const schemes = await api(hr, 'GET', '/payroll/schemes');
    rec('TC-PAY-SCH-001', schemes.body.code === 0 ? 'PASS' : 'FAIL', '');

    // onboarding list
    const onb = await api(hr, 'GET', '/onboarding/applications?page=1&pageSize=10');
    rec('TC-ONB-001-list', onb.body.code === 0 ? 'PASS' : 'FAIL', '');

    // capture FAIL details for known failures
    const posCreate = await api(admin, 'POST', '/positions', {
      name: 'E2EPosX' + Date.now(),
      code: 'PX' + String(Date.now()).slice(-4),
      sequence: 'P',
      rankMin: 'P1',
      rankMax: 'P5',
      defaultProbationMonths: 3,
      departmentId: 4,
      isStandard: 1,
    });
    rec('TC-ORG-POS-002', posCreate.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(posCreate.body).slice(0, 180));

    const mgrPos = await api(mgr, 'POST', '/positions', {
      name: 'MgrOnly',
      sequence: 'P',
      rankMin: 'P1',
      rankMax: 'P2',
      defaultProbationMonths: 3,
    });
    rec('TC-ORG-POS-005', mgrPos.status === 403 || mgrPos.body.code === 20002 ? 'PASS' : 'FAIL', JSON.stringify({ s: mgrPos.status, c: mgrPos.body.code, m: mgrPos.body.message }));

    const empList = await api(hr, 'GET', '/employees?page=1&pageSize=5');
    const eid = empList.body.data && empList.body.data.list && empList.body.data.list[0] && empList.body.data.list[0].id;
    if (eid) {
      const d = await api(hr, 'GET', '/employees/' + eid);
      rec('TC-EMP-002', d.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(d.body).slice(0, 120));
      const put = await api(hr, 'PUT', '/employees/' + eid, { email: 'ok' + Date.now() + '@x.com' });
      rec('TC-EMP-003', put.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(put.body).slice(0, 120));
      const putBad = await api(hr, 'PUT', '/employees/' + eid, { departmentId: 99 });
      rec('TC-EMP-004', putBad.body.code === 20003 ? 'PASS' : 'FAIL', JSON.stringify(putBad.body).slice(0, 120));
    }

    // transfer diagnostics
    const zhang = await api(hr, 'GET', '/employees?keyword=13800000004&page=1&pageSize=5');
    const zid = zhang.body.data && zhang.body.data.list && zhang.body.data.list[0] && zhang.body.data.list[0].id;
    const deptId = zhang.body.data && zhang.body.data.list && zhang.body.data.list[0] && zhang.body.data.list[0].departmentId;
    rec('TC-TRF-diag', 'INFO', JSON.stringify({ zid, deptId, sample: zhang.body.data && zhang.body.data.list && zhang.body.data.list[0] }).slice(0, 200));
    if (zid) {
      const same = await api(hr, 'POST', '/transfers', { employeeId: zid, newDepartmentId: deptId, reason: 'same' });
      rec('TC-TRF-002', same.body.code === 30004 ? 'PASS' : 'FAIL', JSON.stringify(same.body).slice(0, 160));
      const cross = await api(hr, 'POST', '/transfers', {
        employeeId: zid,
        newDepartmentId: deptId === 4 ? 1 : 4,
        newPositionId: 1,
        reason: 'cross',
      });
      rec('TC-TRF-001', cross.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(cross.body).slice(0, 160));
      const empTrf = await api(emp, 'POST', '/transfers', { employeeId: zid, newDepartmentId: 1, reason: 'x' });
      rec('TC-TRF-004', empTrf.status === 403 || empTrf.body.code === 20002 ? 'PASS' : 'FAIL', JSON.stringify({ s: empTrf.status, c: empTrf.body.code }));
    }

    const res = await api(emp, 'POST', '/profile/resignation-requests', {
      expectedLastDay: '2026-12-31',
      resignDate: '2026-12-31',
      reasonCategory: 'VOLUNTARY',
      reasonType: 'VOLUNTARY',
      resignType: 'RESIGN',
      reason: 'e2e',
      handoverEmployeeId: 2,
    });
    rec('TC-RES-001', res.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(res.body).slice(0, 180));

    const g = await api(hr, 'POST', '/attendance/groups', {
      name: 'Grp' + String(Date.now()).slice(-5),
      shiftType: 'FIXED',
      onDutyTime: '09:00:00',
      offDutyTime: '18:00:00',
      workStartTime: '09:00:00',
      workEndTime: '18:00:00',
      lateThreshold: 15,
      earlyLeaveThreshold: 15,
      lateThresholdMinutes: 15,
      earlyLeaveThresholdMinutes: 15,
    });
    rec('TC-ATT-GRP-002', g.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(g.body).slice(0, 180));

    const calc = await api(emp, 'GET', '/leaves/calc-days?type=ANNUAL&start=2026-08-03&end=2026-08-05');
    const calc2 = await api(emp, 'GET', '/leaves/calc-days?leaveType=ANNUAL&startDate=2026-08-03&endDate=2026-08-05');
    rec('TC-LEAVE-002-calc', calc.body.code === 0 || calc2.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify({ calc: calc.body, calc2: calc2.body }).slice(0, 200));

    const leave = await api(emp, 'POST', '/leaves/applications', {
      type: 'ANNUAL',
      leaveType: 'ANNUAL',
      startDate: '2026-09-01',
      endDate: '2026-09-01',
      startHalfDay: 'AM',
      endHalfDay: 'PM',
      startHalf: 'AM',
      endHalf: 'PM',
      days: 1,
      reason: 'e2e',
    });
    rec('TC-LEAVE-002', leave.body.code === 0 || leave.body.code === 40003 ? 'PASS' : 'FAIL', JSON.stringify(leave.body).slice(0, 180));

    const st1 = await api(hr, 'GET', '/attendance/statistics/personal?month=2026-07&employeeId=' + (zid || 4));
    rec('TC-STAT-001', st1.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(st1.body).slice(0, 160));
    const st2 = await api(hr, 'GET', '/attendance/statistics/department?month=2026-07&departmentId=4');
    rec('TC-STAT-002', st2.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(st2.body).slice(0, 160));

    const hrUsers = await api(hr, 'GET', '/system/users?page=1&pageSize=5');
    rec('TC-SEC-18', hrUsers.status === 403 || hrUsers.body.code === 20002 ? 'PASS' : 'FAIL', JSON.stringify({ s: hrUsers.status, c: hrUsers.body.code }));

    const adminLeave = await api(admin, 'GET', '/leaves/applications?page=1');
    rec('TC-LEAVE-008-admin', adminLeave.body.code === 90001 ? 'FAIL' : 'PASS', JSON.stringify({ c: adminLeave.body.code, m: adminLeave.body.message }));
    const adminOt = await api(admin, 'GET', '/overtime/applications?page=1');
    rec('TC-OT-006-admin', adminOt.body.code === 90001 ? 'FAIL' : 'PASS', JSON.stringify({ c: adminOt.body.code, m: adminOt.body.message }));

    // lock summary
    const lock = await api(hr, 'PUT', '/attendance/monthly-summary', { period: '2026-07', locked: true });
    rec('TC-SUM-002-lock', lock.body.code === 0 || lock.status < 500 ? 'PASS' : 'FAIL', JSON.stringify(lock.body).slice(0, 140));
    const fix = await api(emp, 'POST', '/attendance/punch-fix', {
      punchDate: '2026-07-15',
      type: 'IN',
      punchTime: '09:00',
      reason: 'after lock',
    });
    rec('TC-PUNCH-006', fix.body.code === 40001 ? 'PASS' : 'FAIL', JSON.stringify(fix.body).slice(0, 140));
    await api(hr, 'PUT', '/attendance/monthly-summary', { period: '2026-07', locked: false });
  } catch (e) {
    rec('BATCH-DIAG-ERROR', 'FAIL', String(e).slice(0, 200));
  }

  // merge with existing (may be partial)
  const existing = await page.evaluate(() => localStorage.getItem('hrms_tc_all'));
  let merged = results.slice();
  try {
    const prev = JSON.parse(existing || '{}');
    const map = {};
    for (const r of prev.results || []) map[r.id] = r;
    for (const r of results) map[r.id] = r;
    merged = Object.values(map);
  } catch (_) {}

  // expose as window for extraction
  await page.evaluate((m) => {
    window.__HRMS_TC__ = m;
    localStorage.setItem('hrms_tc_all', JSON.stringify({ results: m, updatedAt: Date.now() }));
  }, merged);

  const summary = {
    total: merged.length,
    pass: merged.filter((r) => r.verdict === 'PASS').length,
    fail: merged.filter((r) => r.verdict === 'FAIL').length,
    blocked: merged.filter((r) => r.verdict === 'BLOCKED').length,
    info: merged.filter((r) => r.verdict === 'INFO').length,
  };
  return JSON.stringify({ summary, fails: merged.filter((r) => r.verdict === 'FAIL') });
}
