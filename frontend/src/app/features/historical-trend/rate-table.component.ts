import { DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { HistoryResponse } from '../../core/models/history.model';

interface RateTableRow {
  date: string;
  fromRateToUsd: number | null;
  toRateToUsd: number | null;
  adjustedRate: number | null;
  missing: boolean;
}

/**
 * Renders `HistoryResponse.points` as a table, merging in
 * `missingDates` as visibly distinct rows rather than silently omitting
 * them — per contracts/exchange.md and spec.md User Story 2, Acceptance
 * Scenario 5 ("clearly indicate which parts of the requested range are
 * missing, rather than ... fabricating missing points"). Shows each
 * currency's own raw rate-to-USD alongside the derived pair rate (FR-014:
 * "the raw rates that are actually stored," not just the computed value).
 */
@Component({
  selector: 'app-rate-table',
  imports: [DecimalPipe],
  templateUrl: './rate-table.component.html',
  styleUrl: './rate-table.component.scss',
})
export class RateTableComponent {
  readonly history = input<HistoryResponse | null>(null);

  protected readonly rows = computed<RateTableRow[]>(() => {
    const history = this.history();
    if (!history) {
      return [];
    }

    const pointByDate = new Map(history.points.map((point) => [point.date, point]));
    const allDates = [...pointByDate.keys(), ...history.missingDates].sort();

    return allDates.map((date) => {
      const point = pointByDate.get(date);
      return {
        date,
        fromRateToUsd: point?.fromRateToUsd ?? null,
        toRateToUsd: point?.toRateToUsd ?? null,
        adjustedRate: point?.adjustedRate ?? null,
        missing: !point,
      };
    });
  });
}
