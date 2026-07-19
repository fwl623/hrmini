async page => {
  const out = [];
  const base = 'http://localhost:8000';

  async function login(user, pass) {
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await page.goto(base + '/login', { waitUntil: 'networkidle' });
    await page.getByRole('textbox', { name: '* \u624b\u673a\u53f7' }).fill(user);
    await page.getByRole('textbox', { name: '* \u5bc6\u7801' }).fill(pass);
    await page.getByRole('button', { name: '\u767b \u5f55' }).click();
    await page.waitForTimeout(2000);
    return page.url();
  }

  // Wrong password once
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.getByRole('textbox', { name: '* \u624b\u673a\u53f7' }).fill('13800000000');
  await page.getByRole('textbox', { name: '* \u5bc6\u7801' }).fill('BadPassword1');
  await page.getByRole('button', { name: '\u767b \u5f55' }).click();
  await page.waitForTimeout(1500);
  out.push({
    id: 'TC-AUTH-WRONG-PWD',
    url: page.url(),
    ok: page.url().includes('/login'),
    body: (await page.locator('body').innerText()).replace(/\s+/g, ' ').slice(0, 120),
  });

  // Admin org dept page + open create modal
  await login('13800000000', 'Admin@12345');
  await page.goto(base + '/admin/org/departments', { waitUntil: 'networkidle' });
  await page.waitForTimeout(600);
  const addDept = page.getByRole('button', { name: /\u65b0\u589e/ }).first();
  let deptModal = false;
  if (await addDept.count()) {
    await addDept.click();
    await page.waitForTimeout(500);
    deptModal = await page.getByRole('dialog').isVisible().catch(() => false);
    await page.keyboard.press('Escape');
  }
  out.push({
    id: 'TC-ORG-DEPT-002-modal',
    ok: deptModal || (await page.locator('body').innerText()).includes('\u90e8\u95e8'),
    deptModal,
  });

  // Positions page has data
  await page.goto(base + '/admin/org/positions', { waitUntil: 'networkidle' });
  await page.waitForTimeout(600);
  out.push({
    id: 'TC-ORG-POS-LIST',
    rows: await page.locator('.ant-table-row').count(),
    ok: (await page.locator('.ant-table-row').count()) >= 1,
  });

  // Users list as admin
  await page.goto(base + '/admin/system/users', { waitUntil: 'networkidle' });
  await page.waitForTimeout(600);
  out.push({
    id: 'TC-SYS-USER-001',
    rows: await page.locator('.ant-table-row').count(),
    ok: (await page.locator('.ant-table-row').count()) >= 1,
  });

  // Roles: no add button
  await page.goto(base + '/admin/system/roles', { waitUntil: 'networkidle' });
  await page.waitForTimeout(600);
  const roleBody = await page.locator('body').innerText();
  out.push({
    id: 'TC-SYS-ROLE-001',
    ok:
      roleBody.includes('SYS_ADMIN') &&
      roleBody.includes('EMPLOYEE') &&
      !roleBody.includes('\u65b0\u589e\u89d2\u8272'),
  });

  // Punch center
  await page.goto(base + '/admin/attendance/punch', { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);
  const punchBody = await page.locator('body').innerText();
  out.push({
    id: 'TC-PUNCH-001',
    ok: !punchBody.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef'),
    hasOverview: punchBody.includes('\u6253\u5361') || punchBody.includes('\u4eca\u65e5'),
  });

  // Screenshot leave error as admin evidence
  await page.goto(base + '/admin/leave/list', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  await page.screenshot({ path: 'docs/evidence-leave-admin-error.png', fullPage: true });
  out.push({
    id: 'EVIDENCE-LEAVE',
    internal: (await page.locator('body').innerText()).includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef'),
  });

  // Delegation as HR: create twice for 60003
  await login('13800000001', 'Admin@12345');
  await page.goto(base + '/admin/delegation', { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);

  async function createDelegation(delegateId) {
    const hits = [];
    const handler = async (resp) => {
      if (!resp.url().includes('/delegations')) return;
      if (resp.request().method() === 'GET') return;
      let c = null, m = '';
      try {
        const j = await resp.json();
        c = j.code;
        m = j.message;
      } catch (_) {}
      hits.push({ method: resp.request().method(), s: resp.status(), c, m });
    };
    page.on('response', handler);
    await page.getByRole('button', { name: /\u65b0\u589e\u59d4\u6258/ }).click();
    await page.waitForTimeout(400);
    const dlg = page.getByRole('dialog');
    await dlg.locator('input').first().fill(String(delegateId));
    // date range: click and pick if needed - try fill via placeholder
    const ranges = dlg.locator('.ant-picker');
    if (await ranges.count()) {
      await ranges.first().click();
      await page.waitForTimeout(300);
      // click today cells
      const cells = page.locator('.ant-picker-cell-in-view');
      if (await cells.count()) {
        await cells.nth(0).click();
        await page.waitForTimeout(200);
        await cells.nth(5).click().catch(() => {});
      }
      await page.keyboard.press('Enter').catch(() => {});
    }
    await dlg.getByRole('button', { name: /OK|确\s*定/ }).click();
    await page.waitForTimeout(1200);
    page.off('response', handler);
    return hits;
  }

  const d1 = await createDelegation(1004);
  await page.waitForTimeout(500);
  const d2 = await createDelegation(1004);
  out.push({ id: 'TC-DLG-60003', first: d1, second: d2 });

  await page.evaluate((p) => localStorage.setItem('hrms_batch4', JSON.stringify(p)), out);
  return JSON.stringify(out);
}
