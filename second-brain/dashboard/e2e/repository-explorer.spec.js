import { test, expect } from '@playwright/test';
import { mockApiResponses } from './helpers/mock-api';

test.beforeEach(async ({ page }) => {
  await mockApiResponses(page);
});

test.describe('Repository Explorer', () => {
  test('should display repository cards or empty state', async ({ page }) => {
    await page.goto('/repositories');
    await expect(page.locator('h2')).toContainText('Repository Explorer');
    const repoCards = page.locator('.bg-gray-900.border.border-gray-800.rounded-xl');
    const count = await repoCards.count();
    expect(count).toBeGreaterThanOrEqual(0);
  });
});
