import { test, expect } from '@playwright/test';
import { mockApiResponses } from './helpers/mock-api';

test.beforeEach(async ({ page }) => {
  await mockApiResponses(page);
});

test.describe('Handoffs View', () => {
  test('should display handoffs page', async ({ page }) => {
    await page.goto('/handoffs');
    await expect(page.locator('h2')).toContainText('Agent Handoffs');
  });

  test('should show instruction text', async ({ page }) => {
    await page.goto('/handoffs');
    await expect(page.locator('text=Select a repository')).toBeVisible();
  });
});
