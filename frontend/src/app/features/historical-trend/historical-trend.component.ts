import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiErrorResponse } from '../../core/models/api-error.model';
import { CURRENCY_CODE_PATTERN, CURRENCY_OPTIONS } from '../../core/models/currency.model';
import { HistoryResponse } from '../../core/models/history.model';
import { HistoryService } from '../../core/services/history.service';
import { CurrencyComboboxComponent } from '../../shared/currency-combobox/currency-combobox.component';
import { InsightPanelComponent } from './insight-panel.component';
import { RateTableComponent } from './rate-table.component';
import { RatePoint, SvgLineChartComponent } from './svg-line-chart.component';

interface InsightParams {
  from: string;
  to: string;
  fromDate: string;
  toDate: string;
}

export type DateRangePreset = 'last7' | 'last30' | 'thisMonth';

/** `YYYY-MM-DD`, matching what a native `<input type="date">` control holds
 * — built from local date parts, not `toISOString()`, so this always means
 * the browser's own "today," never shifted by a UTC offset. */
function toDateInputValue(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function presetRange(preset: DateRangePreset, today: Date): { startDate: string; endDate: string } {
  let start: Date;
  switch (preset) {
    case 'last7':
      start = new Date(today);
      start.setDate(start.getDate() - 6);
      break;
    case 'last30':
      start = new Date(today);
      start.setDate(start.getDate() - 29);
      break;
    case 'thisMonth':
      start = new Date(today.getFullYear(), today.getMonth(), 1);
      break;
  }
  return { startDate: toDateInputValue(start), endDate: toDateInputValue(today) };
}

/**
 * Composes a pair+date-range picker with `RateTableComponent`,
 * `SvgLineChartComponent`, and `InsightPanelComponent` side by side. On
 * submit, the history fetch (owned here, feeding the table/chart) and the
 * insight fetch (owned entirely by `InsightPanelComponent`, triggered by
 * updating its inputs) both start in the same synchronous tick — genuinely
 * in parallel, with no dependency on one another's outcome, per FR-017 and
 * spec.md User Story 2 Acceptance Scenarios 3–4.
 */
@Component({
  selector: 'app-historical-trend',
  imports: [
    ReactiveFormsModule,
    RateTableComponent,
    SvgLineChartComponent,
    InsightPanelComponent,
    CurrencyComboboxComponent,
  ],
  templateUrl: './historical-trend.component.html',
  styleUrl: './historical-trend.component.scss',
})
export class HistoricalTrendComponent {
  private readonly fb = inject(FormBuilder);
  private readonly historyService = inject(HistoryService);

  protected readonly currencyOptions = CURRENCY_OPTIONS;
  protected readonly historyLoading = signal(false);
  protected readonly historyError = signal<string | null>(null);
  protected readonly historyResponse = signal<HistoryResponse | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly insightParams = signal<InsightParams | null>(null);

  protected readonly chartPoints = computed<RatePoint[]>(() => {
    const history = this.historyResponse();
    return history
      ? history.points.map((point) => ({ date: point.date, rate: point.adjustedRate }))
      : [];
  });

  protected readonly chartTitle = computed(() => {
    const history = this.historyResponse();
    return history ? `Historical Exchange Rate: ${history.from}/${history.to}` : 'Exchange Rate Trend';
  });

  protected readonly form = this.fb.group({
    from: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.pattern(CURRENCY_CODE_PATTERN),
    ]),
    to: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.pattern(CURRENCY_CODE_PATTERN),
    ]),
    startDate: this.fb.nonNullable.control('', [Validators.required]),
    endDate: this.fb.nonNullable.control('', [Validators.required]),
  });

  /** Sets both date controls at once to a preset range ending today — a
   * shortcut for the repeated real-world case (a recent range), not a
   * replacement for hand-picking an arbitrary day in either field. */
  protected applyPreset(preset: DateRangePreset): void {
    const { startDate, endDate } = presetRange(preset, new Date());
    this.form.patchValue({ startDate, endDate });
    this.form.controls.startDate.markAsTouched();
    this.form.controls.endDate.markAsTouched();
  }

  onSubmit(): void {
    this.formError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { from, to, startDate, endDate } = this.form.getRawValue();
    if (startDate > endDate) {
      this.formError.set('Start date must not be after end date.');
      return;
    }

    const fromCode = from.toUpperCase();
    const toCode = to.toUpperCase();

    this.historyLoading.set(true);
    this.historyError.set(null);
    this.historyResponse.set(null);

    this.historyService.getHistory(fromCode, toCode, startDate, endDate).subscribe({
      next: (response) => {
        this.historyResponse.set(response);
        this.historyLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.historyError.set(this.extractErrorMessage(err));
        this.historyLoading.set(false);
      },
    });

    // Independent of the call above: updating this signal is what starts
    // InsightPanelComponent's own request, via its own reactive inputs.
    this.insightParams.set({ from: fromCode, to: toCode, fromDate: startDate, toDate: endDate });
  }

  private extractErrorMessage(err: HttpErrorResponse): string {
    const body = err.error as ApiErrorResponse | null;
    return body?.message ?? 'Something went wrong. Please try again.';
  }
}
