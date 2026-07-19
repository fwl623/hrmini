async page => {
  const results = [];
  const base = 'http://localhost:8000';
  const routes = [
    ['TC-WB-001', '/admin/workbench'],
    ['TC-ORG-DEPT-001', '/admin/org/departments'],
    ['TC-ORG-POS-001', '/admin/org/positions'],
    ['TC-EMP-001', '/admin/employee/list'],
    ['TC-MOB-HR', '/admin/employee/mobile-change'],
    ['TC-ONB-001', '/admin/onboarding'],
    ['TC-REG-001', '/admin/regularization'],
    ['TC-TRF-001', '/admin/transfers'],
    ['TC-RES-001', '/admin/resignation'],
    ['TC-APPR-001', '/admin/approval'],
    ['TC-DLG-001', '/admin/delegation'],
    ['TC-ATT-GRP-001', '/admin/attendance/groups'],
    ['TC-PUNCH-001', '/admin/attendance/punch'],
    ['TC-PUNCH-008', '/admin/attendance/records'],
    ['TC-HOL-001', '/admin/attendance/holidays'],
    ['TC-SUM-001', '/admin/attendance/summary'],
    ['TC-STAT-001', '/admin/attendance/statistics'],
    ['TC-LEAVE-008', '/admin/leave/list'],
    ['TC-OT-006', '/admin/overtime/list'],
    ['TC-PAY-SCH', '/admin/payroll/schemes'],
    ['TC-PAY-BAT', '/admin/payroll/batches'],
    ['TC-PAY-SLIP', '/admin/payroll/payslips'],
    ['TC-PAY-COST', '/admin/payroll/cost-report'],
    ['TC-SYS-USER-001', '/admin/system/users'],
    ['TC-SYS-ROLE-001', '/admin/system/roles'],
    ['TC-SYS-LOG-001', '/admin/system/operation-logs'],
    ['TC-SYS-LOG-002', '/admin/system/login-logs'],
  ];

  for (const [id, path] of routes) {
    const apiFails = [];
    const onResp = async (resp) => {
      try {
        const url = resp.url();
        if (!url.includes('/api/')) return;
        const status = resp.status();
        let code = null;
        let message = '';
        try {
          const j = await resp.json();
          code = j && j.code;
          message = (j && j.message) || '';
        } catch (_) {}
        if (status >= 400 || (code !== null && code !== 0 && code !== undefined)) {
          apiFails.push({
            u: url.replace(/^https?:\/\/[^/]+/, '').slice(0, 100),
            s: status,
            c: code,
            m: String(message).slice(0, 60),
          });
        }
      } catch (_) {}
    };
    page.on('response', onResp);

    let navOk = true;
    let err = '';
    try {
      await page.goto(base + path, { waitUntil: 'networkidle', timeout: 25000 });
      await page.waitForTimeout(700);
    } catch (e) {
      navOk = false;
      err = String(e.message || e).slice(0, 80);
    }
    page.off('response', onResp);

    const bodyText = await page.locator('body').innerText().catch(() => '');
    const url = page.url();
    const isPayroll = path.includes('/payroll/');
    const hasInternal = bodyText.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef');
    const hasNoPerm =
      bodyText.includes('403') ||
      bodyText.includes('\u65e0\u6743\u9650') ||
      bodyText.includes('\u65e0\u6743');
    const redirectedLogin = url.includes('/login');
    const blankish = bodyText.trim().length < 30;

    let verdict = 'PASS';
    let note = '';

    if (redirectedLogin) {
      verdict = 'FAIL';
      note = 'redirected_to_login';
    } else if (!navOk) {
      verdict = 'FAIL';
      note = 'nav_fail:' + err;
    } else if (hasInternal) {
      verdict = 'FAIL';
      note = 'ui_internal_error';
    } else if (isPayroll) {
      const still = url.includes('/payroll/');
      const content =
        bodyText.includes('\u8d26\u5957') ||
        bodyText.includes('\u6838\u7b97') ||
        bodyText.includes('\u5de5\u8d44\u6761') ||
        bodyText.includes('\u6210\u672c');
      if (still && content && !hasNoPerm) {
        verdict = 'FAIL';
        note = 'sys_admin_can_access_payroll';
      } else {
        verdict = 'PASS';
        note = 'payroll_blocked_ok';
      }
    } else if (blankish) {
      verdict = 'FAIL';
      note = 'blank_page';
    }

    const serious = apiFails.filter(
      (f) => f.s >= 500 || f.c === 90001 || (f.m && f.m.indexOf('\u7cfb\u7edf\u5185\u90e8') >= 0),
    );
    if (!isPayroll && serious.length) {
      verdict = 'FAIL';
      note = (note ? note + ';' : '') + 'api_5xx_or_90001';
    }

    results.push({ id, path, url, verdict, note, apiFails: apiFails.slice(0, 5) });
  }

  const summary = {
    total: results.length,
    pass: results.filter((x) => x.verdict === 'PASS').length,
    fail: results.filter((x) => x.verdict === 'FAIL').length,
  };
  // persist for retrieval
  await page.evaluate((payload) => {
    localStorage.setItem('hrms_smoke_result', JSON.stringify(payload));
  }, { results, summary });

  return JSON.stringify({ summary, fails: results.filter((r) => r.verdict === 'FAIL'), results });
}
