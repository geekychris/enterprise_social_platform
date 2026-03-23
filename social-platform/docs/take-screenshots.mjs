import puppeteer from 'puppeteer';
import { setTimeout } from 'timers/promises';

const BASE = 'http://localhost:3999';
const DEBUG_USER = '72057594037927937';
const DIR = './docs/screenshots';

async function run() {
  const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox'] });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  // Set debug auth in localStorage
  async function setAuth() {
    await page.evaluate((uid) => {
      localStorage.setItem('social-auth', JSON.stringify({
        state: {
          token: null,
          userId: uid,
          username: 'user-' + uid,
          debugUserId: uid,
          debugMode: true,
        },
        version: 0,
      }));
    }, DEBUG_USER);
  }

  // Navigate and wait for content
  async function screenshot(path, name, waitMs = 2500) {
    await page.goto(BASE + path, { waitUntil: 'networkidle2' });
    await setTimeout(waitMs);
    await page.screenshot({ path: `${DIR}/${name}.png`, fullPage: false });
    console.log(`  Captured: ${name}`);
  }

  // Initialize auth
  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
  await setAuth();

  console.log('Taking screenshots...\n');

  // ── 1. Login Page ──
  await page.evaluate(() => localStorage.removeItem('social-auth'));
  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
  await setTimeout(1000);
  await page.screenshot({ path: `${DIR}/01-login.png` });
  console.log('  Captured: 01-login');
  await setAuth();

  // ── 2. Home Feed ──
  await screenshot('/', '02-home-feed');

  // ── 3. Search ──
  await page.goto(BASE + '/search', { waitUntil: 'networkidle2' });
  await setTimeout(1000);
  // Type a search query
  const searchInput = await page.$('input[type="text"], input[type="search"]');
  if (searchInput) {
    await searchInput.type('coffee', { delay: 50 });
    await setTimeout(2000);
  }
  await page.screenshot({ path: `${DIR}/03-search.png` });
  console.log('  Captured: 03-search');

  // ── 4. Profile ──
  await screenshot('/profile/' + DEBUG_USER, '04-profile');

  // ── 5. Messages ──
  await screenshot('/messages', '05-messages');

  // ── 6. About ──
  await screenshot('/about', '06-about');

  // ── 7. Group Page ──
  const groups = await page.evaluate(async () => {
    const r = await fetch('/api/groups/mine', { headers: { 'X-Debug-User-Id': '72057594037927937' } });
    return r.json();
  });
  if (groups.length > 0) {
    await screenshot('/group/' + groups[0].id, '07-group');
  }

  // ── 8. Page ──
  const pages = await page.evaluate(async () => {
    const r = await fetch('/api/pages/mine', { headers: { 'X-Debug-User-Id': '72057594037927937' } });
    return r.json();
  });
  if (pages.length > 0) {
    await screenshot('/page/' + pages[0].id, '08-page');
  }

  // ── 9. Admin Dashboard ──
  await screenshot('/admin', '09-admin-dashboard', 3000);

  // ── 10. Admin Engagement ──
  await page.goto(BASE + '/admin', { waitUntil: 'networkidle2' });
  await setTimeout(1500);
  // Click Engagement tab
  const engagementTab = await page.evaluateHandle(() => {
    return [...document.querySelectorAll('button')].find(b => b.textContent.includes('Engagement'));
  });
  if (engagementTab) {
    await engagementTab.click();
    await setTimeout(2000);
    await page.screenshot({ path: `${DIR}/10-admin-engagement.png` });
    console.log('  Captured: 10-admin-engagement');
  }

  // ── 11. Admin Users ──
  const usersTab = await page.evaluateHandle(() => {
    return [...document.querySelectorAll('button')].find(b => b.textContent.includes('Users'));
  });
  if (usersTab) {
    await usersTab.click();
    await setTimeout(2000);
    await page.screenshot({ path: `${DIR}/11-admin-users.png` });
    console.log('  Captured: 11-admin-users');
  }

  // ── 12. Admin Graph Explorer - Profile ──
  const graphTab = await page.evaluateHandle(() => {
    return [...document.querySelectorAll('button')].find(b => b.textContent.includes('Graph Explorer'));
  });
  if (graphTab) {
    await graphTab.click();
    await setTimeout(1500);

    // Type in user search
    const picker = await page.$('input[placeholder*="Search user"]');
    if (picker) {
      await picker.click();
      await picker.type(DEBUG_USER, { delay: 10 });
      await setTimeout(500);
      // Press Enter or click to select (just set the value directly)
      await page.evaluate((uid) => {
        const input = document.querySelector('input[placeholder*="Search user"]');
        if (input) {
          const nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
          nativeInputValueSetter.call(input, uid);
          input.dispatchEvent(new Event('input', { bubbles: true }));
          input.dispatchEvent(new Event('change', { bubbles: true }));
        }
      }, DEBUG_USER);
      await setTimeout(2500);
    }
    await page.screenshot({ path: `${DIR}/12-graph-profile.png` });
    console.log('  Captured: 12-graph-profile');

    // ── 13. Graph Explorer - Network Visualization ──
    const traverseBtn = await page.evaluateHandle(() => {
      return [...document.querySelectorAll('button')].find(b => b.textContent.includes('Graph Traverse'));
    });
    if (traverseBtn) {
      await traverseBtn.click();
      await setTimeout(4000); // Wait for graph to load and stabilize
      await page.screenshot({ path: `${DIR}/13-graph-network.png` });
      console.log('  Captured: 13-graph-network');
    }

    // ── 14. Graph Explorer - FOF ──
    const fofBtn = await page.evaluateHandle(() => {
      return [...document.querySelectorAll('button')].find(b => b.textContent.includes('Friend of Friend'));
    });
    if (fofBtn) {
      await fofBtn.click();
      await setTimeout(1500);
      await page.screenshot({ path: `${DIR}/14-graph-fof.png` });
      console.log('  Captured: 14-graph-fof');
    }
  }

  // ── 15. Right Panel with Friend Requests ──
  await page.setViewport({ width: 1440, height: 900 });
  await screenshot('/', '15-right-panel', 2500);

  await browser.close();
  console.log('\nDone! Screenshots saved to docs/screenshots/');
}

run().catch(e => { console.error(e); process.exit(1); });
