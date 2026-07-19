async page => {
  const out = [];
  const base = 'http://localhost:8000';

  function listen(pred) {
    const hits = [];
    const handler = async (resp) => {
      try {
        const u = resp.url();
        if (!pred(u)) return;
        let c = null, m = '';
        try {
          const j = await resp.json();
          c = j.code;
          m = j.message;
        } catch (_) {}
        hits.push({ u: u.replace(/^https?:\/\/[^/]+/, '').slice(0, 100), s: resp.status(), c, m: String(m).slice(0, 80) });
      } catch (_) {}
    };
    page.on('response', handler);
    return {
      hits,
      stop: () => page.off('response', handler),
    };
  }

  // Leave list
  {
    const L = listen((u) => u.includes('/leaves/'));
    await page.goto(base + '/admin/leave/list', { waitUntil: 'networkidle', timeout: 25000 });
    await page.waitForTimeout(1000);
    L.stop();
    const body = await page.locator('body').innerText();
    out.push({
      id: 'HR-LEAVE',
      url: page.url(),
      internal: body.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef'),
      apis: L.hits,
      ok: !body.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef') && !L.hits.some((h) => h.c === 90001 || h.s >= 500),
    });
  }

  // Overtime list
  {
    const L = listen((u) => u.includes('/overtime/'));
    await page.goto(base + '/admin/overtime/list', { waitUntil: 'networkidle', timeout: 25000 });
    await page.waitForTimeout(1000);
    L.stop();
    const body = await page.locator('body').innerText();
    out.push({
      id: 'HR-OT',
      url: page.url(),
      internal: body.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef'),
      apis: L.hits,
      ok: !body.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef') && !L.hits.some((h) => h.c === 90001 || h.s >= 500),
    });
  }

  // Payroll schemes as HR
  {
    const L = listen((u) => u.includes('/payroll/'));
    await page.goto(base + '/admin/payroll/schemes', { waitUntil: 'networkidle', timeout: 25000 });
    await page.waitForTimeout(1000);
    L.stop();
    const body = await page.locator('body').innerText();
    const sider = await page.locator('.ant-layout-sider').innerText().catch(() => '');
    out.push({
      id: 'HR-PAY-SCH',
      url: page.url(),
      payrollMenu: sider.includes('\u85aa\u8d44') || sider.includes('\u8d26\u5957'),
      apis: L.hits,
      ok: page.url().includes('/payroll/schemes') && !L.hits.some((h) => h.c === 20002 && h.s === 403),
    });
  }

  // Employee list filter
  {
    await page.goto(base + '/admin/employee/list', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    const body = await page.locator('body').innerText();
    out.push({
      id: 'HR-EMP-LIST',
      ok: body.includes('\u82b1\u540d\u518c') || body.includes('\u5458\u5de5') || body.length > 50,
      hasTable: await page.locator('.ant-table, table').count().then((c) => c > 0),
    });
  }

  // Onboarding page actions visible
  {
    await page.goto(base + '/admin/onboarding', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    const body = await page.locator('body').innerText();
    out.push({
      id: 'HR-ONB',
      ok: !body.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef'),
      hasCreate: body.includes('\u65b0\u5efa') || body.includes('\u65b0\u589e'),
    });
  }

  // Delegation create + conflict attempt
  {
    await page.goto(base + '/admin/delegation', { waitUntil: 'networkidle' });
    await page.waitForTimeout(600);
    const L = listen((u) => u.includes('/delegations'));
    const btn = page.getByRole('button', { name: /\u65b0\u589e\u59d4\u6258/ });
    if (await btn.count()) {
      await btn.click();
      await page.waitForTimeout(500);
      const dlg = page.getByRole('dialog');
      if (await dlg.isVisible()) {
        // fill delegate user id 1004 if input exists
        const inputs = dlg.locator('input');
        const n = await inputs.count();
        if (n > 0) await inputs.first().fill('1004');
        // try OK
        const ok = dlg.getByRole('button', { name: /OK|确\s*定/ });
        if (await ok.count()) await ok.click();
        await page.waitForTimeout(1200);
      }
    }
    L.stop();
    out.push({ id: 'HR-DLG-CREATE', apis: L.hits, bodySnippet: (await page.locator('body').innerText()).slice(0, 120).replace(/\s+/g, ' ') });
  }

  // Holiday create submit
  {
    await page.goto(base + '/admin/attendance/holidays', { waitUntil: 'networkidle' });
    await page.waitForTimeout(500);
    const L = listen((u) => u.includes('/holidays'));
    await page.getByRole('button', { name: /\u65b0\u589e\u8282\u5047\u65e5/ }).click();
    await page.waitForTimeout(400);
    const dlg = page.getByRole('dialog');
    out.push({ id: 'HR-HOL-MODAL', ok: await dlg.isVisible() });
    await page.keyboard.press('Escape');
    L.stop();
  }

  // Attendance summary generate button
  {
    await page.goto(base + '/admin/attendance/summary', { waitUntil: 'networkidle' });
    await page.waitForTimeout(600);
    const body = await page.locator('body').innerText();
    out.push({
      id: 'HR-SUM',
      hasGenerate: body.includes('\u751f\u6210\u6708\u6c47\u603b'),
      ok: !body.includes('\u7cfb\u7edf\u5185\u90e8\u9519\u8bef'),
    });
  }

  // Users page should be forbidden for HR?
  {
    const L = listen((u) => u.includes('/system/users'));
    await page.goto(base + '/admin/system/users', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    L.stop();
    out.push({
      id: 'TC-SEC-18-HR-users',
      url: page.url(),
      apis: L.hits,
      // HR should get 403 on API or be redirected
      ok: L.hits.some((h) => h.s === 403 || h.c === 20002) || !page.url().includes('/system/users'),
    });
  }

  await page.evaluate((p) => localStorage.setItem('hrms_batch2', JSON.stringify(p)), out);
  return JSON.stringify(out);
}
