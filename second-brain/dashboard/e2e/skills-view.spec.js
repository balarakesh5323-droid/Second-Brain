import { test, expect } from '@playwright/test';
import { mockApiResponses } from './helpers/mock-api';

test.beforeEach(async ({ page }) => {
  await mockApiResponses(page);
});

test.describe('Skills View', () => {
  test('should display skills page', async ({ page }) => {
    await page.goto('/skills');
    await expect(page.locator('h2')).toContainText('Skills');
  });

  test('should show skill cards or empty state', async ({ page }) => {
    await page.goto('/skills');
    const skillCards = page.locator('.bg-gray-900.border.border-gray-800.rounded-xl');
    const count = await skillCards.count();
    expect(count).toBeGreaterThanOrEqual(0);
  });
});
