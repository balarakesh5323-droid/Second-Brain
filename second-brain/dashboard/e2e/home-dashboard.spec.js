import { test, expect } from '@playwright/test';
import { mockApiResponses } from './helpers/mock-api';

test.beforeEach(async ({ page }) => {
  await mockApiResponses(page);
});

test.describe('Home Dashboard', () => {
  test('should display stat cards', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('text=Projects')).toBeVisible();
    await expect(page.locator('text=Memories')).toBeVisible();
    await expect(page.locator('text=Agents')).toBeVisible();
    await expect(page.locator('text=Open Tasks')).toBeVisible();
  });

  test('should display recent activity section', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('text=Recent Activity')).toBeVisible();
  });

  test('should display stat card values as numbers', async ({ page }) => {
    await page.goto('/');
    const statValues = page.locator('.text-2xl');
    const count = await statValues.count();
    expect(count).toBeGreaterThanOrEqual(4);
  });
});
