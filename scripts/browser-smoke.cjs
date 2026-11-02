const { chromium } = require("../target/browser-qa/node_modules/playwright");
const assert = require("node:assert/strict");
(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.BROWSER_EXECUTABLE,
  });
  const context = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
    }),
    page = await context.newPage(),
    errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const root = process.env.EVENTHQ_TEST_URL || "http://localhost:18082";
  const wait = async (selector) =>
    page.locator(selector).first().waitFor({ timeout: 15000 });
  try {
    await page.goto(root);
    await wait(".event-card");
    assert.equal(await page.locator(".event-card").count(), 3);
    await page.screenshot({ path: "target/ui-desktop.png", fullPage: true });
    await page.locator("#event-search").fill("Design");
    assert.equal(await page.locator(".event-card").count(), 1);
    await page.locator("#event-search").fill("");
    await page.locator("#venue-filter").selectOption({ index: 1 });
    assert.equal(await page.locator(".event-card").count(), 1);
    await page.locator("#venue-filter").selectOption("");
    await page.locator("#account-button").click();
    await page.locator('[data-mode="register"]').click();
    await page
      .locator('[name="email"]')
      .fill(`browser-${Date.now()}@example.com`);
    await page.locator('[name="password"]').fill("Browser-password-123");
    await page.locator('form[data-form="auth"] button[type="submit"]').click();
    await page.locator("#modal").waitFor({ state: "hidden" });
    await page.locator('.event-card [data-action="event"]').first().click();
    await wait(".seat");
    await page.locator('.seat[data-id="1"]').click();
    await page.locator('.seat[data-id="7"]').click();
    assert.equal(await page.locator(".seat.selected").count(), 2);
    await page.screenshot({ path: "target/ui-seats.png", fullPage: true });
    await page.locator('[data-action="hold"]').click();
    await wait('[data-action="pay"]');
    assert.match(
      await page.locator(".checkout-line.total").innerText(),
      /2,760/,
    );
    await page.reload();
    await wait('[data-action="resume"]');
    await page.locator('[data-action="resume"]').click();
    await wait('[data-action="pay"]');
    await page.locator('[data-action="pay"]').click();
    await wait(".success-mark");
    await page.locator('[data-action="view-tickets"]').click();
    await wait(".ticket-card");
    assert.equal(await page.locator(".ticket-card").count(), 2);
    await page.screenshot({ path: "target/ui-tickets.png", fullPage: true });
    await page.locator('[data-nav="discover"]').click();
    await wait(".event-card");
    await page.locator('.event-card [data-action="event"]').first().click();
    await wait(".seat");
    assert.equal(await page.locator('.seat[data-id="1"]').isDisabled(), true);
    await page.locator('.seat[data-id="8"]').click();
    await page.locator('[data-action="hold"]').click();
    await wait('[data-action="pay"]');
    await page.locator('[data-action="cancel-hold"]').click();
    await page.locator("#modal").waitFor({ state: "hidden" });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.waitForFunction(
      () =>
        document.querySelector("#sidebar").getBoundingClientRect().right <= 0,
    );
    await page.screenshot({ path: "target/ui-mobile.png", fullPage: true });
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
      true,
    );
    await page.locator("#menu-button").click();
    assert.equal(
      await page
        .locator("#sidebar")
        .evaluate((e) => e.classList.contains("open")),
      true,
    );
    await page.locator('[data-nav="tickets"]').click();
    await wait(".ticket-card");
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.locator("#logout-button").click();
    await wait(".event-card");
    await page.locator("#account-button").click();
    await page
      .locator('[name="email"]')
      .fill(process.env.EVENTHQ_TEST_ADMIN_EMAIL);
    await page
      .locator('[name="password"]')
      .fill(process.env.EVENTHQ_TEST_ADMIN_PASSWORD);
    await page.locator('form[data-form="auth"] button[type="submit"]').click();
    await page.locator("#modal").waitFor({ state: "hidden" });
    await page.locator('[data-nav="admin"]').click();
    await wait('[data-action="create-events"]');
    await page.locator('[data-tab="venues"]').click();
    await wait('[data-action="create-venues"]');
    await page.locator('[data-action="create-venues"]').click();
    await page.locator('[name="name"]').fill("Browser Test Hall");
    await page.locator('[name="address"]').fill("New Delhi");
    await page
      .locator('form[data-form="catalog"] button[type="submit"]')
      .click();
    await page.locator("#modal").waitFor({ state: "hidden" });
    await wait("table");
    const row = page.locator("tr").filter({ hasText: "Browser Test Hall" });
    await row.locator('[data-action="manage-seats"]').click();
    await page.locator('[name="label"]').fill("QA1");
    await page.locator('form[data-form="seat"] button[type="submit"]').click();
    await wait(".seat-label");
    assert.match(await page.locator(".seat-label").innerText(), /QA1/);
    await page.locator('[data-action="close"]').click();
    await page.locator('[data-tab="events"]').click();
    await wait('[data-action="create-events"]');
    await page.locator('[data-action="create-events"]').click();
    await page.locator('[name="name"]').fill("Browser Test Event");
    await page
      .locator('[name="venueId"]')
      .selectOption({ label: "Browser Test Hall" });
    await page
      .locator('form[data-form="catalog"] button[type="submit"]')
      .click();
    await page.locator("#modal").waitFor({ state: "hidden" });
    await wait("table");
    await page
      .locator("tr")
      .filter({ hasText: "Browser Test Event" })
      .locator('[data-action="publish"]')
      .click();
    await page
      .locator("tr")
      .filter({ hasText: "Browser Test Event" })
      .locator(".pill")
      .filter({ hasText: "PUBLISHED" })
      .waitFor();
    await page.screenshot({ path: "target/ui-admin.png", fullPage: true });
    const guestContext = await browser.newContext();
    const guest = await guestContext.newPage();
    guest.on("pageerror", (error) => errors.push(error.message));
    await guest.goto(root);
    await guest.locator(".event-card").first().waitFor();
    await guest.locator('.event-card [data-action="event"]').nth(2).click();
    await guest.locator(".seat:not([disabled])").last().click();
    await guest.locator('[data-action="hold"]').click();
    await guest.locator('[data-mode="register"]').click();
    await guest
      .locator('[name="email"]')
      .fill(`guest-${Date.now()}@example.com`);
    await guest.locator('[name="password"]').fill("Guest-password-123");
    await guest.locator('form[data-form="auth"] button[type="submit"]').click();
    await guest.locator('[data-action="pay"]').waitFor();
    await guest.locator('[data-action="cancel-hold"]').click();
    await guest.locator("#modal").waitFor({ state: "hidden" });
    await guestContext.close();
    assert.deepEqual(errors, []);
    console.log(
      "BROWSER PASSED: catalog/search/filter, registration, seats/pricing, hold recovery, payment, tickets, cancellation, mobile navigation, administrator venue/seats/event publication; no JavaScript errors.",
    );
  } catch (error) {
    await page.screenshot({ path: "target/ui-failure.png", fullPage: true });
    throw error;
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
