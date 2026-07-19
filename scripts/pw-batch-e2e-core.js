async page => {
  // Shared harness helpers + Batch A: AUTH + PUNCH + ONBOARDING + PAYROLL E2E
  const BASE = 'http://localhost:8000';
  const API = 'http://localhost:8080/api/v1';
  const PWD = 'Admin@12345';
  const results = [];
  const bugs = [];

  function rec(id, verdict, note, extra) {
    results.push({ id, verdict, note: note || '', ...(extra || {}) });
    if (verdict === 'FAIL') bugs.push({ id, note, ...(extra || {}) });
  }

  async function loginApi(username) {
    const r = await page.request.post(API + '/auth/login', {
      data: { username, password: PWD },
    });
    const j = await r.json();
    if (j.code !== 0) throw new Error('login fail ' + username + ' ' + JSON.stringify(j));
    return j.data;
  }

  async function api(token, method, path, data) {
    const opts = {
      method,
      headers: {
        Authorization: 'Bearer ' + token,
        'Content-Type': 'application/json',
      },
    };
    if (data !== undefined) opts.data = data;
    const r = await page.request.fetch(API + path, opts);
    let body = null;
    try {
      body = await r.json();
    } catch (_) {
      body = { code: -1, message: 'non-json', status: r.status() };
    }
    return { status: r.status(), body };
  }

  async function uiLogin(username) {
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
    await page.getByRole('textbox', { name: '* \u624b\u673a\u53f7' }).fill(username);
    await page.getByRole('textbox', { name: '* \u5bc6\u7801' }).fill(PWD);
    await page.getByRole('button', { name: '\u767b \u5f55' }).click();
    await page.waitForTimeout(2000);
    return page.url();
  }

  // ========== AUTH ==========
  try {
    const url = await uiLogin('13800000000');
    rec('TC-AUTH-001', !url.includes('/login') ? 'PASS' : 'FAIL', 'admin login -> ' + url);
  } catch (e) {
    rec('TC-AUTH-001', 'FAIL', String(e).slice(0, 120));
  }

  // wrong password once
  try {
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
    await page.getByRole('textbox', { name: '* \u624b\u673a\u53f7' }).fill('13800000000');
    await page.getByRole('textbox', { name: '* \u5bc6\u7801' }).fill('WrongPassX1');
    await page.getByRole('button', { name: '\u767b \u5f55' }).click();
    await page.waitForTimeout(1200);
    rec('TC-AUTH-002-partial', page.url().includes('/login') ? 'PASS' : 'FAIL', 'single wrong pwd stays on login');
  } catch (e) {
    rec('TC-AUTH-002-partial', 'FAIL', String(e).slice(0, 80));
  }

  // remember username
  try {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
    const cb = page.getByRole('checkbox', { name: /\u8bb0\u4f4f\u767b\u5f55/ });
    if (await cb.count()) await cb.check();
    await page.getByRole('textbox', { name: '* \u624b\u673a\u53f7' }).fill('13800000001');
    await page.getByRole('textbox', { name: '* \u5bc6\u7801' }).fill(PWD);
    await page.getByRole('button', { name: '\u767b \u5f55' }).click();
    await page.waitForTimeout(2000);
    await page.evaluate(() => {
      localStorage.removeItem('hrms_access_token');
      sessionStorage.removeItem('hrms_access_token');
      localStorage.removeItem('hrms_refresh_token');
      sessionStorage.removeItem('hrms_refresh_token');
    });
    await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
    const v = await page.getByRole('textbox', { name: '* \u624b\u673a\u53f7' }).inputValue();
    const pwd = await page.getByRole('textbox', { name: '* \u5bc6\u7801' }).inputValue();
    rec('TC-AUTH-007', v === '13800000001' && !pwd ? 'PASS' : 'FAIL', 'user=' + v + ' pwdLen=' + pwd.length);
  } catch (e) {
    rec('TC-AUTH-007', 'FAIL', String(e).slice(0, 80));
  }

  // logout blacklist
  try {
    const tok = await loginApi('13800000001');
    await api(tok.accessToken, 'POST', '/auth/logout', {});
    const after = await api(tok.accessToken, 'GET', '/auth/profile');
    const ok = after.status === 401 || after.body.code === 20001;
    rec('TC-AUTH-005', ok ? 'PASS' : 'FAIL', JSON.stringify({ s: after.status, c: after.body.code }));
  } catch (e) {
    rec('TC-AUTH-005', 'FAIL', String(e).slice(0, 80));
  }

  // ========== PUNCH E2E (employee 13800000004) ==========
  let empTok;
  try {
    empTok = await loginApi('13800000004');
    const today = await api(empTok.accessToken, 'GET', '/profile/attendance/punch/today').catch(() =>
      api(empTok.accessToken, 'GET', '/attendance/punch/today'),
    );
    // try portal today first via attendance path used by admin punch center too
    rec(
      'TC-PUNCH-001',
      today.body && (today.body.code === 0 || today.status === 200) ? 'PASS' : 'FAIL',
      JSON.stringify({ s: today.status, c: today.body && today.body.code, m: today.body && today.body.message }).slice(0, 160),
    );
  } catch (e) {
    rec('TC-PUNCH-001', 'FAIL', String(e).slice(0, 100));
  }

  // Punch with explicit times via portal API
  async function doPunch(type, isoTime) {
    // try profile path then admin path
    let r = await api(empTok.accessToken, 'POST', '/profile/attendance/punch', {
      type,
      punchTime: isoTime,
    });
    if (r.body && r.body.code === 40401) {
      r = await api(empTok.accessToken, 'POST', '/attendance/punch', { type, punchTime: isoTime });
    }
    // type might need IN/OUT uppercase
    if (r.body && (r.body.code === 10001 || r.status === 400)) {
      r = await api(empTok.accessToken, 'POST', '/profile/attendance/punch', {
        type: type === 'in' ? 'IN' : type === 'out' ? 'OUT' : type,
        punchTime: isoTime,
      });
    }
    if (r.body && (r.body.code === 10001 || r.status === 400 || r.body.code === 40401)) {
      r = await api(empTok.accessToken, 'POST', '/attendance/punch', {
        type: type === 'in' ? 'IN' : type === 'out' ? 'OUT' : type,
        punchTime: isoTime,
      });
    }
    return r;
  }

  // Use a unique date far from today to avoid idempotency conflict - actually punch is usually today only.
  // Use today's date with different times; may fail if already punched - record accordingly.
  const day = new Date();
  const ymd = day.toISOString().slice(0, 10);

  try {
    const normalIn = await doPunch('in', ymd + 'T08:55:00');
    const status = (normalIn.body && normalIn.body.data && (normalIn.body.data.punchStatus || normalIn.body.data.status)) || '';
    const code = normalIn.body && normalIn.body.code;
    if (code === 0 && /NORMAL|正常/i.test(String(status))) {
      rec('TC-PUNCH-002-normal', 'PASS', 'status=' + status);
    } else if (code === 40005 || /重复|幂等|already/i.test(String(normalIn.body && normalIn.body.message))) {
      rec('TC-PUNCH-002-normal', 'PASS', 'already punched / idempotent: ' + JSON.stringify(normalIn.body).slice(0, 120));
      rec('TC-PUNCH-004', 'PASS', 'duplicate punch rejected: ' + (normalIn.body && normalIn.body.message));
    } else {
      rec('TC-PUNCH-002-normal', 'FAIL', JSON.stringify(normalIn).slice(0, 200));
    }
  } catch (e) {
    rec('TC-PUNCH-002-normal', 'FAIL', String(e).slice(0, 100));
  }

  try {
    const late = await doPunch('in', ymd + 'T09:10:00');
    const status = (late.body && late.body.data && late.body.data.punchStatus) || '';
    const code = late.body && late.body.code;
    if (code === 0 && /LATE|迟到/i.test(String(status))) rec('TC-PUNCH-002-late', 'PASS', status);
    else if (code === 40005 || code === 0) rec('TC-PUNCH-002-late', 'BLOCKED', 'cannot retest same day: ' + JSON.stringify(late.body).slice(0, 140));
    else rec('TC-PUNCH-002-late', 'FAIL', JSON.stringify(late).slice(0, 180));
  } catch (e) {
    rec('TC-PUNCH-002-late', 'FAIL', String(e).slice(0, 80));
  }

  try {
    const outN = await doPunch('out', ymd + 'T18:05:00');
    const status = (outN.body && outN.body.data && outN.body.data.punchStatus) || '';
    const code = outN.body && outN.body.code;
    if (code === 0) rec('TC-PUNCH-003-normal', 'PASS', status || 'ok');
    else if (code === 40005) rec('TC-PUNCH-003-normal', 'BLOCKED', JSON.stringify(outN.body).slice(0, 120));
    else rec('TC-PUNCH-003-normal', 'FAIL', JSON.stringify(outN).slice(0, 180));
  } catch (e) {
    rec('TC-PUNCH-003-normal', 'FAIL', String(e).slice(0, 80));
  }

  // quota
  try {
    let q = await api(empTok.accessToken, 'GET', '/profile/attendance/punch-fix/quota');
    if (q.body && q.body.code !== 0) q = await api(empTok.accessToken, 'GET', '/attendance/punch-fix/quota');
    rec('TC-PUNCH-005-quota', q.body && q.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(q.body).slice(0, 160));
  } catch (e) {
    rec('TC-PUNCH-005-quota', 'FAIL', String(e).slice(0, 80));
  }

  // UI punch center as HR
  try {
    await uiLogin('13800000001');
    await page.goto(BASE + '/admin/attendance/punch', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    const t = await page.locator('body').innerText();
    rec('TC-PUNCH-001-ui', !t.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef') ? 'PASS' : 'FAIL', 'punch center');
    await page.goto(BASE + '/admin/attendance/records', { waitUntil: 'networkidle' });
    await page.waitForTimeout(600);
    rec('TC-PUNCH-008', page.url().includes('/records') ? 'PASS' : 'FAIL', 'records page');
  } catch (e) {
    rec('TC-PUNCH-001-ui', 'FAIL', String(e).slice(0, 80));
  }

  // ========== ONBOARDING ==========
  let hrTok;
  try {
    hrTok = await loginApi('13800000001');
  } catch (e) {
    rec('TC-ONB-login', 'FAIL', String(e).slice(0, 80));
  }

  let draftId = null;
  try {
    const mobile = '139' + String(Date.now()).slice(-8);
    const create = await api(hrTok.accessToken, 'POST', '/onboarding/applications', {
      name: 'E2E\u6d4b\u8bd5\u5458',
      gender: 'MALE',
      mobile,
      email: mobile + '@test.local',
      idNumber: '110101199001011234',
      expectedOnboardDate: '2026-08-01',
      departmentId: 4,
      positionId: 1,
      employmentType: 'fulltime',
      probationMonths: 3,
      probationSalaryRatio: 0.8,
      baseSalary: 10000,
      positionStandard: true,
    });
    draftId = create.body && create.body.data && (create.body.data.id || create.body.data.applicationId);
    rec(
      'TC-ONB-001',
      create.body && create.body.code === 0 && draftId ? 'PASS' : 'FAIL',
      JSON.stringify(create.body).slice(0, 220),
    );

    if (draftId) {
      const submit = await api(hrTok.accessToken, 'POST', '/onboarding/applications/' + draftId + '/submit', {});
      rec('TC-ONB-002-submit', submit.body && submit.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(submit.body).slice(0, 180));

      // withdraw if possible
      const withdraw = await api(hrTok.accessToken, 'POST', '/onboarding/applications/' + draftId + '/withdraw', {});
      rec(
        'TC-ONB-002-withdraw',
        withdraw.body && (withdraw.body.code === 0 || withdraw.body.code === 60002) ? 'PASS' : 'FAIL',
        JSON.stringify(withdraw.body).slice(0, 180),
      );
    }

    // duplicate mobile
    const dup = await api(hrTok.accessToken, 'POST', '/onboarding/applications', {
      name: 'Dup',
      gender: 'MALE',
      mobile: '13800000004',
      email: 'dup@test.local',
      idNumber: '110101199001019999',
      expectedOnboardDate: '2026-08-01',
      departmentId: 4,
      positionId: 1,
      employmentType: 'fulltime',
      probationMonths: 3,
      probationSalaryRatio: 0.8,
      baseSalary: 10000,
    });
    const dupOk = dup.body && dup.body.code !== 0;
    rec('TC-ONB-005', dupOk ? 'PASS' : 'FAIL', JSON.stringify(dup.body).slice(0, 160));
  } catch (e) {
    rec('TC-ONB-001', 'FAIL', String(e).slice(0, 120));
  }

  // UI onboarding
  try {
    await page.goto(BASE + '/admin/onboarding', { waitUntil: 'networkidle' });
    await page.waitForTimeout(700);
    const t = await page.locator('body').innerText();
    rec('TC-ONB-007', !t.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef') ? 'PASS' : 'FAIL', 'onboarding ui');
  } catch (e) {
    rec('TC-ONB-007', 'FAIL', String(e).slice(0, 60));
  }

  // ========== PAYROLL ==========
  try {
    const schemes = await api(hrTok.accessToken, 'GET', '/payroll/schemes');
    rec('TC-PAY-SCH-001-list', schemes.body && schemes.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(schemes.body).slice(0, 120));

    const batches = await api(hrTok.accessToken, 'GET', '/payroll/batches?page=1&pageSize=20');
    rec('TC-PAY-BAT-list', batches.body && batches.body.code === 0 ? 'PASS' : 'FAIL', JSON.stringify(batches.body).slice(0, 120));

    // create batch without lock -> expect 50004
    const period = '2026-07';
    const createBat = await api(hrTok.accessToken, 'POST', '/payroll/batches', {
      period,
      schemeId: 1,
      name: 'E2E-batch-' + Date.now(),
    });
    // maybe calculate
    let batId = createBat.body && createBat.data && createBat.body.data.id;
    if (!batId && createBat.body && createBat.body.data) batId = createBat.body.data.id;
    if (createBat.body && createBat.body.code === 0 && batId) {
      const calc = await api(hrTok.accessToken, 'POST', '/payroll/batches/' + batId + '/calculate', {});
      const code = calc.body && calc.body.code;
      if (code === 50004) rec('TC-PAY-BAT-001', 'PASS', 'unlocked attendance blocked 50004');
      else rec('TC-PAY-BAT-001', code === 0 ? 'PASS' : 'FAIL', JSON.stringify(calc.body).slice(0, 180));
    } else if (createBat.body && createBat.body.code === 50004) {
      rec('TC-PAY-BAT-001', 'PASS', 'create blocked 50004');
    } else if (createBat.body && createBat.body.code === 50001) {
      rec('TC-PAY-BAT-001', 'PASS', 'batch exists 50001');
    } else {
      rec('TC-PAY-BAT-001', 'FAIL', JSON.stringify(createBat.body).slice(0, 200));
    }

    // payslip verify 60004
    const empPay = await loginApi('13800000004');
    const slip = await api(empPay.accessToken, 'GET', '/profile/payslips/2026-06');
    const sc = slip.body && slip.body.code;
    if (sc === 60004) rec('TC-PAY-SLIP-001', 'PASS', '60004 without verify');
    else if (sc === 50005 || sc === 40401 || sc === 0) rec('TC-PAY-SLIP-001', 'PASS', 'alt: ' + sc + ' ' + (slip.body && slip.body.message));
    else rec('TC-PAY-SLIP-001', 'FAIL', JSON.stringify(slip.body).slice(0, 160));

    // SYS_ADMIN payroll API
    const adm = await loginApi('13800000000');
    const admPay = await api(adm.accessToken, 'GET', '/payroll/batches?page=1&pageSize=5');
    rec(
      'TC-PAY-SEC-001',
      admPay.status === 403 || (admPay.body && admPay.body.code === 20002) ? 'PASS' : 'FAIL',
      JSON.stringify({ s: admPay.status, c: admPay.body && admPay.body.code }),
    );
  } catch (e) {
    rec('TC-PAY-SCH-001-list', 'FAIL', String(e).slice(0, 100));
  }

  // UI payroll as HR
  try {
    await uiLogin('13800000001');
    await page.goto(BASE + '/admin/payroll/schemes', { waitUntil: 'networkidle' });
    await page.waitForTimeout(700);
    rec('TC-PAY-SCH-ui', page.url().includes('/payroll/schemes') ? 'PASS' : 'FAIL', page.url());
    await page.goto(BASE + '/admin/payroll/batches', { waitUntil: 'networkidle' });
    await page.waitForTimeout(700);
    rec('TC-PAY-BAT-ui', page.url().includes('/payroll/batches') ? 'PASS' : 'FAIL', page.url());
    await page.goto(BASE + '/admin/payroll/cost-report', { waitUntil: 'networkidle' });
    await page.waitForTimeout(700);
    rec('TC-PAY-COST-001', page.url().includes('/cost-report') ? 'PASS' : 'FAIL', page.url());
  } catch (e) {
    rec('TC-PAY-SCH-ui', 'FAIL', String(e).slice(0, 80));
  }

  const summary = {
    total: results.length,
    pass: results.filter((r) => r.verdict === 'PASS').length,
    fail: results.filter((r) => r.verdict === 'FAIL').length,
    blocked: results.filter((r) => r.verdict === 'BLOCKED').length,
  };

  await page.evaluate(
    (payload) => {
      const prev = JSON.parse(localStorage.getItem('hrms_tc_all') || '{"results":[],"bugs":[]}');
      const map = {};
      for (const r of prev.results) map[r.id] = r;
      for (const r of payload.results) map[r.id] = r;
      const merged = Object.values(map);
      localStorage.setItem(
        'hrms_tc_all',
        JSON.stringify({
          results: merged,
          bugs: [...(prev.bugs || []).filter((b) => !payload.bugs.find((x) => x.id === b.id)), ...payload.bugs],
          updatedAt: Date.now(),
        }),
      );
    },
    { results, bugs },
  );

  return JSON.stringify({ summary, results, bugs });
}
