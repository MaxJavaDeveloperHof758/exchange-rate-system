import { Component, computed, input } from '@angular/core';
import { HistoryResponse } from '../../core/models/history.model';

interface RateTableRow {
  date: string;
  exchange: number | null;
  missing: boolean;
}

/**
 * T048: renders `HistoryResponse.points` as a table, merging in
 * `missingDates` as visibly distinct rows rather than silently omitting
 * them — per contracts/exchange.md and spec.md User Story 2, Acceptance
 * Scenario 5 ("clearly indicate which parts of the requested range are
 * missing, rather than ... fabricating missing points").
 */
@Component({
  selector: 'app-rate-table',
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

    const exchangeByDate = new Map(history.points.map((point) => [point.date, point.exchange]));
    const allDates = [...exchangeByDate.keys(), ...history.missingDates].sort();

    return allDates.map((date) => ({
      date,
      exchange: exchangeByDate.get(date) ?? null,
      missing: !exchangeByDate.has(date),
    }));
  });
}
