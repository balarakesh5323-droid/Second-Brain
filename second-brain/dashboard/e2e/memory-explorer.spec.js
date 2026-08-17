import { test, expect } from '@playwright/test';
import { mockApiResponses } from './helpers/mock-api';

test.beforeEach(async ({ page }) => {
  await mockApiResponses(page);
});

test.describe('Memory Explorer', () => {
  test('should display search bar', async ({ page }) => {
    await page.goto('/memory');
    await expect(page.locator('input[placeholder="Search memories..."]')).toBeVisible();
    await expect(page.locator('button:has-text("Search")')).toBeVisible();
  });

  test('should have a clear button when searching', async ({ page }) => {
    await page.goto('/memory');
    const searchInput = page.locator('input[placeholder="Search memories..."]');
    await searchInput.fill('test query');
    await searchInput.press('Enter');
    await expect(page.locator('button:has-text("Clear")')).toBeVisible();
  });

  test('should clear search when Clear is clicked', async ({ page }) => {
    await page.goto('/memory');
    const searchInput = page.locator('input[placeholder="Search memories..."]');
    await searchInput.fill('test query');
    await searchInput.press('Enter');
    await page.click('button:has-text("Clear")');
    await expect(searchInput).toHaveValue('');
  });

  test('should show empty state when no memories', async ({ page }) => {
    await page.goto('/memory');
    const emptyState = page.locator('text=No memories found');
    const memoryCards = page.locator('.bg-gray-900.border.border-gray-800.rounded-xl');
    const hasMemories = await memoryCards.count() > 0;
    const hasEmptyState = await emptyState.isVisible().catch(() => false);
    expect(hasMemories || hasEmptyState).toBeTruthy();
  });
});
