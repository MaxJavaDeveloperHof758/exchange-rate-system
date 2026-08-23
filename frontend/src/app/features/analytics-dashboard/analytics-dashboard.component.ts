import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';

import { ApiErrorResponse } from '../../core/models/api-error.model';
import { CurrencyUsageEntry } from '../../core/models/analytics.model';
import { AnalyticsService } from '../../core/services/analytics.service';

export interface UsageBarGeometry {
  currency: string;
  totalCount: number;
  lastQueried: string;
  barX: number;
  barY: number;
  barWidth: number;
}

export interface BarChartGeometry {
  bars: UsageBarGeometry[];
  labelX: number;
  valueX: number;
  barHeight: number;
  rowHeight: number;
  height: number;
}

const BAR_HEIGHT = 20;
const ROW_HEIGHT = 34;
/** Reserved left column for the 3-letter currency code label. */
const LABEL_WIDTH = 56;
/** Reserved right column for the "N queries · last YYYY-MM-DD" value text. */
const VALUE_WIDTH = 160;

/**
 * T052: pure function mapping `topCurrencies` to horizontal-bar geometry —
 * same no-charting-library approach as `mapPointsToLineChart`
 * (research.md Decision 1), kept separate from the component so the
 * mapping itself stays trivially unit-testable. Bar width is scaled
 * relative to the top entry's `totalCount` (already the max, since the
 * backend sorts `topCurrencies` descending per contracts/analytics.md),
 * so the most-queried currency always draws a full-width bar and the rest
 * are visibly proportional to it.
 */
export function mapEntriesToBarChart(
  entries: readonly CurrencyUsageEntry[],
  width: number,
  padding: number,
): BarChartGeometry {
  const barX = padding + LABEL_WIDTH;
  const labelX = padding;
  const valueX = width - padding;
  const plotWidth = Math.max(0, width - padding - VALUE_WIDTH - barX);

  if (entries.length === 0) {
    return { bars: [], labelX, valueX, barHeight: BAR_HEIGHT, rowHeight: ROW_HEIGHT, height: padding * 2 };
  }

  const maxCount = Math.max(...entries.map((entry) => entry.totalCount)) || 1;

  const bars: UsageBarGeometry[] = entries.map((entry, index) => ({
    currency: entry.currency,
    totalCount: entry.totalCount,
    lastQueried: entry.lastQueried,
    barX,
    barY: padding + index * ROW_HEIGHT + (ROW_HEIGHT - BAR_HEIGHT) / 2,
    barWidth: (entry.totalCount / maxCount) * plotWidth,
  }));

  return {
    bars,
    labelX,
    valueX,
    barHeight: BAR_HEIGHT,
    rowHeight: ROW_HEIGHT,
    height: padding * 2 + entries.length * ROW_HEIGHT,
  };
}

/**
 * User Story 3's Usage Analytics Dashboard view (T052): fetches
 * `GET /api/analytics` once on load (the endpoint takes no parameters —
 * there is no form to submit) and renders `topCurrencies` as a ranked,
 * ever-queried-only bar list, with its own loading/error state per
 * constitution Principle IX. A currency that has never been queried is
 * absent from the response entirely (contracts/analytics.md) — an empty
 * `topCurrencies` array is therefore rendered as an explicit "nothing
 * queried yet" message, not an error and not a blank chart.
 */
@Component({
  selector: 'app-analytics-dashboard',
  templateUrl: './analytics-dashboard.component.html',
  styleUrl: './analytics-dashboard.component.scss',
})
export class AnalyticsDashboardComponent {
  private readonly analyticsService = inject(AnalyticsService);

  protected readonly width = 640;
  protected readonly padding = 16;

  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly entries = signal<CurrencyUsageEntry[]>([]);

  protected readonly geometry = computed(() =>
    mapEntriesToBarChart(this.entries(), this.width, this.padding),
  );

  constructor() {
    this.analyticsService.getAnalytics().subscribe({
      next: (response) => {
        this.entries.set(response.topCurrencies);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(extractErrorMessage(err));
        this.loading.set(false);
      },
    });
  }
}

function extractErrorMessage(err: HttpErrorResponse): string {
  const body = err.error as ApiErrorResponse | null;
  return body?.message ?? 'Usage analytics could not be loaded.';
}
