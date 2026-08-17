import { test, expect } from '@playwright/test';
import { mockApiResponses } from './helpers/mock-api';

test.beforeEach(async ({ page }) => {
  await mockApiResponses(page);
});

test.describe('Navigation', () => {
  test('should display the Second Brain header', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('h1')).toContainText('Second Brain');
  });

  test('should navigate to Memory page', async ({ page }) => {
    await page.goto('/');
    await page.click('a[href="/memory"]');
    await expect(page.locator('h2')).toContainText('Memory Explorer');
  });

  test('should navigate to Agents page', async ({ page }) => {
    await page.goto('/');
    await page.click('a[href="/agents"]');
    await expect(page.locator('h2')).toContainText('Agent Activity');
  });

  test('should navigate to Repositories page', async ({ page }) => {
    await page.goto('/');
    await page.click('a[href="/repositories"]');
    await expect(page.locator('h2')).toContainText('Repository Explorer');
  });

  test('should navigate to Skills page', async ({ page }) => {
    await page.goto('/');
    await page.click('a[href="/skills"]');
    await expect(page.locator('h2')).toContainText('Skills');
  });

  test('should navigate to Handoffs page', async ({ page }) => {
    await page.goto('/');
    await page.click('a[href="/handoffs"]');
    await expect(page.locator('h2')).toContainText('Agent Handoffs');
  });

  test('should highlight active navigation item', async ({ page }) => {
    await page.goto('/memory');
    const memoryLink = page.locator('a[href="/memory"]');
    await expect(memoryLink).toHaveClass(/bg-purple-600/);
  });
});
