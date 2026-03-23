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
  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
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

  // Helper
  async function screenshot(path, name, waitMs = 2000) {
    await page.goto(BASE + path, { waitUntil: 'networkidle2' });
    await setTimeout(waitMs);
    await page.screenshot({ path: `${DIR}/${name}.png`, fullPage: false });
    console.log(`  Captured: ${name}`);
  }

  console.log('Taking screenshots...');

  // 1. Login page
  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
  // Clear auth for login screenshot
  await page.evaluate(() => localStorage.removeItem('social-auth'));
  await page.reload({ waitUntil: 'networkidle2' });
  await setTimeout(1000);
  await page.screenshot({ path: `${DIR}/01-login.png` });
  console.log('  Captured: 01-login');

  // Re-set auth
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

  // 2. Home feed
  await screenshot('/', '02-home-feed');

  // 3. Search page
  await page.goto(BASE + '/search?q=coffee', { waitUntil: 'networkidle2' });
  await setTimeout(2000);
  await page.screenshot({ path: `${DIR}/03-search.png` });
  console.log('  Captured: 03-search');

  // 4. Profile page
  await screenshot('/profile/' + DEBUG_USER, '04-profile');

  // 5. Messages
  await screenshot('/messages', '05-messages');

  // 6. About page
  await screenshot('/about', '06-about');

  // 7. Group page - find a group
  const groupResp = await page.evaluate(async () => {
    const r = await fetch('/api/groups/mine', { headers: { 'X-Debug-User-Id': '72057594037927937' } });
    return r.json();
  });
  if (groupResp.length > 0) {
    await screenshot('/group/' + groupResp[0].id, '07-group');
  }

  // 8. Page page - find a page
  const pageResp = await page.evaluate(async () => {
    const r = await fetch('/api/pages/mine', { headers: { 'X-Debug-User-Id': '72057594037927937' } });
    return r.json();
  });
  if (pageResp.length > 0) {
    await screenshot('/page/' + pageResp[0].id, '08-page');
  }

  await browser.close();
  console.log('Done! Screenshots saved to docs/screenshots/');
}

run().catch(e => { console.error(e); process.exit(1); });
