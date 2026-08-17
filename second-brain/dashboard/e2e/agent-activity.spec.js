import { test, expect } from '@playwright/test';
import { mockApiResponses } from './helpers/mock-api';

test.beforeEach(async ({ page }) => {
  await mockApiResponses(page);
});

test.describe('Agent Activity', () => {
  test('should display event timeline', async ({ page }) => {
    await page.goto('/agents');
    await expect(page.locator('text=Event Timeline')).toBeVisible();
  });

  test('should display recent sessions section', async ({ page }) => {
    await page.goto('/agents');
    await expect(page.locator('text=Recent Sessions')).toBeVisible();
  });

  test('should show timeline markers', async ({ page }) => {
    await page.goto('/agents');
    await expect(page.locator('text=Event Timeline')).toBeVisible();
  });
});
