import { test, expect } from '@playwright/test';
import { mockApiResponses } from './helpers/mock-api';

test.beforeEach(async ({ page }) => {
  await mockApiResponses(page);
});

test.describe('Accessibility', () => {
  test('should have proper heading hierarchy', async ({ page }) => {
    await page.goto('/');
    const h1 = page.locator('h1');
    await expect(h1).toBeVisible();
    const h2 = page.locator('h2').first();
    await expect(h2).toBeVisible();
  });

  test('should have focusable navigation links', async ({ page }) => {
    await page.goto('/');
    const navLinks = page.locator('nav a');
    const count = await navLinks.count();
    expect(count).toBeGreaterThanOrEqual(6);

    for (let i = 0; i < count; i++) {
      const link = navLinks.nth(i);
      await link.focus();
      await expect(link).toBeFocused();
    }
  });

  test('should have accessible search input', async ({ page }) => {
    await page.goto('/memory');
    const searchInput = page.locator('input[placeholder="Search memories..."]');
    await expect(searchInput).toBeVisible();
    await searchInput.focus();
    await expect(searchInput).toBeFocused();
  });
});
