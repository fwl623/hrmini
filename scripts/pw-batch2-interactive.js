async page => {
  const out = [];
  const base = 'http://localhost:8000';

  async function ensureLogin(user, pass) {
    await page.goto(base + '/login', { waitUntil: 'networkidle' });
    await page.waitForTimeout(400);
    if (!page.url().includes('/login')) {
      // logout if already in
      try {
        await page.getByText(user).first().click({ timeout: 2000 });
      } catch (_) {}
    }
    await page.goto(base + '/login', { waitUntil: 'networkidle' });
    await page.getByRole('textbox', { name: /手机号/ }).fill(user);
    await page.getByRole('textbox', { name: /密码/ }).fill(pass);
    await page.getByRole('button', { name: /登\s*录/ }).click();
    await page.waitForTimeout(2000);
    return page.url();
  }

  // --- re-login admin ---
  const loginUrl = await ensureLogin('13800000000', 'Admin@12345');
  out.push({ step: 'relogin_admin', url: loginUrl, ok: !loginUrl.includes('/login') });

  // --- TC-SYS-LOG-002 recheck ---
  await page.goto(base + '/admin/system/login-logs', { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);
  out.push({
    id: 'TC-SYS-LOG-002',
    url: page.url(),
    bodyHasLogin: (await page.locator('body').innerText()).includes('\u767b\u5f55'),
    ok: page.url().includes('/login-logs') && !page.url().endsWith('/login'),
  });

  // --- TC-SYS-ROLE-001 ---
  await page.goto(base + '/admin/system/roles', { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);
  const roleText = await page.locator('body').innerText();
  const has5 =
    roleText.includes('SYS_ADMIN') &&
    roleText.includes('HR_STAFF') &&
    roleText.includes('DEPT_MANAGER') &&
    roleText.includes('FINANCE') &&
    roleText.includes('EMPLOYEE');
  const noAdd = !roleText.includes('\u65b0\u589e\u89d2\u8272');
  out.push({ id: 'TC-SYS-ROLE-001', ok: has5 && noAdd, has5, noAdd });

  // --- TC-DLG-001 open modal ---
  await page.goto(base + '/admin/delegation', { waitUntil: 'networkidle' });
  await page.waitForTimeout(600);
  const addBtn = page.getByRole('button', { name: /\u65b0\u589e\u59d4\u6258/ });
  await addBtn.click();
  await page.waitForTimeout(500);
  const modalVisible = await page.getByRole('dialog').isVisible().catch(() => false);
  out.push({ id: 'TC-DLG-001-modal', ok: modalVisible });
  if (modalVisible) {
    await page.getByRole('button', { name: /Cancel|取消/ }).click().catch(async () => {
      await page.keyboard.press('Escape');
    });
  }

  // --- TC-HOL-001 add holiday ---
  await page.goto(base + '/admin/attendance/holidays', { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);
  await page.getByRole('button', { name: /\u65b0\u589e\u8282\u5047\u65e5/ }).click();
  await page.waitForTimeout(400);
  const holDialog = await page.getByRole('dialog').isVisible().catch(() => false);
  out.push({ id: 'TC-HOL-001-modal', ok: holDialog });
  if (holDialog) {
    await page.keyboard.press('Escape');
  }

  // --- TC-ATT-GRP open create ---
  await page.goto(base + '/admin/attendance/groups', { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);
  await page.getByRole('button', { name: /\u65b0\u589e\u8003\u52e4\u7ec4/ }).click();
  await page.waitForTimeout(400);
  out.push({
    id: 'TC-ATT-GRP-002-modal',
    ok: await page.getByRole('dialog').isVisible().catch(() => false),
  });
  await page.keyboard.press('Escape');

  // --- menu: payroll hidden for admin? ---
  await page.goto(base + '/admin/workbench', { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);
  const sider = await page.locator('.ant-layout-sider, aside, .ant-menu').first().innerText().catch(() => '');
  const payrollInMenu =
    sider.includes('\u85aa\u8d44') ||
    sider.includes('\u8d26\u5957') ||
    sider.includes('\u5de5\u8d44');
  out.push({
    id: 'TC-SEC-05-menu',
    ok: !payrollInMenu,
    note: payrollInMenu ? 'payroll_visible_in_sider' : 'payroll_hidden_ok',
  });

  // --- wrong password once (not lock) ---
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  // clear session by going login - may need logout
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.getByRole('textbox', { name: /手机号/ }).fill('13800000000');
  await page.getByRole('textbox', { name: /密码/ }).fill('WrongPass999');
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForTimeout(1500);
  const stillLogin = page.url().includes('/login');
  const body = await page.locator('body').innerText();
  out.push({
    id: 'TC-AUTH-002-partial',
    ok: stillLogin,
    note: body.slice(0, 80).replace(/\s+/g, ' '),
  });

  // login HR for leave/overtime check
  await page.getByRole('textbox', { name: /手机号/ }).fill('13800000001');
  await page.getByRole('textbox', { name: /密码/ }).fill('Admin@12345');
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForTimeout(2500);
  out.push({ step: 'login_hr', url: page.url(), ok: !page.url().includes('/login') });

  // leave as HR
  const leaveApis = [];
  const onResp = async (resp) => {
    const u = resp.url();
    if (u.includes('/leaves/') || u.includes('/overtime/')) {
      let c = null, m = '';
      try {
        const j = await resp.json();
        c = j.code;
        m = j.message;
      } catch (_) {}
      leaveApis.push({ u: u.replace(base, ''), s: resp.status(), c, m });
    }
  };
  page.on('response', onResp);
  await page.goto(base + '/admin/leave/list', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  await page.goto(base + '/admin/overtime/list', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  page.off('response', onResp);
  out.push({ id: 'HR_LEAVE_OT', apis: leaveApis.slice(0, 6) });

  // payroll as HR - should work
  const payApis = [];
  const onPay = async (resp) => {
    const u = resp.url();
    if (u.includes('/payroll/')) {
      let c = null, m = '';
      try {
        const j = await resp.json();
        c = j.code;
        m = j.message;
      } catch (_) {}
      payApis.push({ u: u.replace(base, '').slice(0, 80), s: resp.status(), c, m });
    }
  };
  page.on('response', onPay);
  await page.goto(base + '/admin/payroll/schemes', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  page.off('response', onPay);
  const payBody = await page.locator('body').innerText();
  out.push({
    id: 'TC-SEC-16-HR-payroll',
    url: page.url(),
    ok: page.url().includes('/payroll/') && !payBody.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef'),
    apis: payApis.slice(0, 5),
  });

  await page.evaluate((payload) => localStorage.setItem('hrms_batch2', JSON.stringify(payload)), out);
  return JSON.stringify(out);
}
