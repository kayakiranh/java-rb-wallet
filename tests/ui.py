"""Browser acceptance tests against running Wallet.Api; no Node application/build is needed."""
import os
import uuid
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

base = os.getenv('WALLET_URL', 'http://localhost:5080')
email = 'ui-' + uuid.uuid4().hex + '@example.test'
password = 'DemoPassword123!'
out = Path('test-results')
out.mkdir(exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch()
    context = browser.new_context(viewport={'width': 1440, 'height': 1050})
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    try:
        response = context.request.post(base + '/api/auth/register', data={
            'name': 'UI Recipient', 'email': 'recipient-' + uuid.uuid4().hex + '@example.test', 'password': password})
        assert response.ok, response.text()
        recipient_token = response.json()['accessToken']
        response = context.request.get(base + '/api/wallet', headers={'Authorization': 'Bearer ' + recipient_token})
        recipient = response.json()['number']
        page.goto(base)
        page.locator('#register-tab').click()
        page.locator('#name').fill('UI Customer')
        page.locator('#email').fill(email)
        page.locator('#password').fill(password)
        page.locator('#auth-submit').click()
        expect(page.locator('#dashboard')).to_be_visible()
        expect(page.locator('#available')).to_contain_text('0,00')
        page.locator('#topup-amount').fill('1000')
        page.locator('#topup-form button').click()
        expect(page.locator('#available')).to_contain_text('1.000,00')
        page.locator('[data-panel=transfer]').click()
        page.locator('#recipient').fill(recipient)
        page.locator('#transfer-amount').fill('200')
        page.locator('#transfer-form button').click()
        expect(page.locator('#available')).to_contain_text('800,00')
        page.locator('[data-panel=bill]').click()
        page.locator('#subscriber').fill('10002')
        page.locator('#bill-query-form button').click()
        expect(page.locator('#bill-amount')).to_contain_text('250,00')
        page.locator('#pay-bill').click()
        expect(page.locator('#available')).to_contain_text('550,00')
        expect(page.locator('#transactions tr')).to_have_count(3)
        expect(page.locator('#previous')).to_be_disabled()
        expect(page.locator('#next')).to_be_disabled()
        page.screenshot(path=str(out / 'wallet-desktop.png'), full_page=True)

        # The server commits, but the response is lost. Reload/login and replay must not credit twice.
        def lose_response(route):
            route.fetch()
            route.abort('connectionfailed')
        page.route('**/api/wallet/top-ups', lose_response, times=1)
        page.locator('[data-panel=topup]').click()
        page.locator('#topup-amount').fill('50')
        page.locator('#topup-form button').click()
        expect(page.locator('#retry-banner')).to_be_visible()
        expect(page.locator('#notice')).to_contain_text('Bağlantı kurulamadı')
        page.reload()
        page.locator('#email').fill(email)
        page.locator('#password').fill(password)
        page.locator('#auth-submit').click()
        expect(page.locator('#dashboard')).to_be_visible()
        expect(page.locator('#available')).to_contain_text('600,00')
        expect(page.locator('#retry-banner')).to_be_visible()
        page.locator('#retry').click()
        expect(page.locator('#retry-banner')).to_be_hidden()
        expect(page.locator('#available')).to_contain_text('600,00')
        page.set_viewport_size({'width': 390, 'height': 844})
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Mobile layout overflows'
        page.screenshot(path=str(out / 'wallet-mobile.png'), full_page=True)
        page.locator('#logout').click()
        expect(page.locator('#auth')).to_be_visible()
        assert page.evaluate('Object.values(localStorage).length') == 0
        assert errors == [], errors
        print('PASS: browser registration, top-up, transfer, bill payment, lost response/reload/replay, mobile layout and logout.')
    except Exception:
        page.screenshot(path=str(out / 'wallet-failure.png'), full_page=True)
        raise
    finally:
        browser.close()
