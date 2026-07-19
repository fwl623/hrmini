async page => {
  const out = [];
  const base = 'http://localhost:8000';

  async function switchUser(user, pass) {
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await page.goto(base + '/login', { waitUntil: 'networkidle' });
    await page.waitForTimeout(300);
    await page.getByRole('textbox', { name: '* \u624b\u673a\u53f7' }).fill(user);
    await page.getByRole('textbox', { name: '* \u5bc6\u7801' }).fill(pass);
    await page.getByRole('button', { name: '\u767b \u5f55' }).click();
    await page.waitForTimeout(2200);
    return page.url();
  }

  // Admin leave/ot again
  out.push({ step: 'login_admin', url: await switchUser('13800000000', 'Admin@12345') });
  {
    const hits = [];
    const h = async (resp) => {
      const u = resp.url();
      if (!u.includes('/api/')) return;
      if (!u.includes('leave') && !u.includes('overtime') && !u.includes('payroll')) return;
      let c = null, m = '';
      try {
        const j = await resp.json();
        c = j.code;
        m = j.message;
      } catch (_) {}
      hits.push({ u: u.replace(base, '').slice(0, 90), s: resp.status(), c, m });
    };
    page.on('response', h);
    await page.goto(base + '/admin/leave/list', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    await page.goto(base + '/admin/overtime/list', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    await page.goto(base + '/admin/payroll/cost-report', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    page.off('response', h);
    const sider = await page.locator('.ant-layout-sider').innerText().catch(() => '');
    out.push({
      id: 'ADMIN-LEAVE-OT-PAY',
      hits,
      payrollInMenu: /薪资|账套|工资/.test(sider) || sider.includes('\u85aa\u8d44'),
      url: page.url(),
    });
  }

  // login-logs recheck as admin
  {
    await page.goto(base + '/admin/system/login-logs', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    out.push({
      id: 'TC-SYS-LOG-002-recheck',
      url: page.url(),
      ok: page.url().includes('login-logs') && !page.url().endsWith('/login'),
      hasRows: (await page.locator('.ant-table-row, tr').count()) > 1,
    });
  }

  // EMPLOYEE portal
  out.push({ step: 'login_emp', url: await switchUser('13800000004', 'Admin@12345') });
  {
    const urls = [];
    for (const p of [
      '/portal/profile',
      '/portal/attendance',
      '/portal/leave',
      '/portal/overtime',
      '/portal/payslips',
      '/portal/resignation',
      '/portal/security',
      '/admin/workbench',
      '/admin/employee/list',
    ]) {
      const hits = [];
      const h = async (resp) => {
        const u = resp.url();
        if (!u.includes('/api/')) return;
        let c = null, m = '';
        try {
          const j = await resp.json();
          c = j.code;
          m = j.message;
        } catch (_) {}
        if (resp.status() >= 400 || (c !== null && c !== 0)) {
          hits.push({ u: u.replace(base, '').slice(0, 80), s: resp.status(), c, m: String(m).slice(0, 40) });
        }
      };
      page.on('response', h);
      await page.goto(base + p, { waitUntil: 'domcontentloaded', timeout: 20000 }).catch((e) => e.message);
      await page.waitForTimeout(700);
      page.off('response', h);
      const body = await page.locator('body').innerText().catch(() => '');
      urls.push({
        path: p,
        url: page.url(),
        internal: body.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef'),
        apiFails: hits.slice(0, 4),
      });
    }
    out.push({ id: 'EMP-PORTAL-SMOKE', pages: urls });
  }

  // FINANCE
  out.push({ step: 'login_fin', url: await switchUser('13800000003', 'Admin@12345') });
  {
    await page.goto(base + '/admin/workbench', { waitUntil: 'networkidle' });
    await page.waitForTimeout(600);
    const sider = await page.locator('.ant-layout-sider').innerText().catch(() => '');
    const hits = [];
    const h = async (resp) => {
      const u = resp.url();
      if (!u.includes('/payroll/schemes')) return;
      let c = null, m = '';
      try {
        const j = await resp.json();
        c = j.code;
        m = j.message;
      } catch (_) {}
      hits.push({ s: resp.status(), c, m });
    };
    page.on('response', h);
    await page.goto(base + '/admin/payroll/schemes', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    page.off('response', h);
    out.push({
      id: 'FINANCE-MENU-PAY',
      siderHasPayroll: sider.includes('\u85aa\u8d44') || /payroll|账套/.test(sider),
      siderHasOrg: sider.includes('\u7ec4\u7ec7') || sider.includes('\u90e8\u95e8'),
      payApis: hits,
      url: page.url(),
    });
  }

  // DEPT_MANAGER data scope quick
  out.push({ step: 'login_mgr', url: await switchUser('13800000002', 'Admin@12345') });
  {
    const hits = [];
    const h = async (resp) => {
      const u = resp.url();
      if (!u.includes('/employees?') && !u.includes('/employees')) return;
      if (resp.request().method() !== 'GET') return;
      let c = null;
      let total = null;
      try {
        const j = await resp.json();
        c = j.code;
        total = j.data && (j.data.total ?? (j.data.list && j.data.list.length));
      } catch (_) {}
      hits.push({ u: u.replace(base, '').slice(0, 100), s: resp.status(), c, total });
    };
    page.on('response', h);
    await page.goto(base + '/admin/employee/list', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    page.off('response', h);
    out.push({ id: 'MGR-EMP-SCOPE', hits: hits.slice(0, 5), url: page.url() });
  }

  await page.evaluate((p) => localStorage.setItem('hrms_batch3', JSON.stringify(p)), out);
  return JSON.stringify(out);
}
