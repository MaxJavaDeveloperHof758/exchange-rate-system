import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiErrorResponse } from '../../core/models/api-error.model';
import { HistoryResponse } from '../../core/models/history.model';
import { HistoryService } from '../../core/services/history.service';
import { InsightPanelComponent } from './insight-panel.component';
import { RateTableComponent } from './rate-table.component';
import { RatePoint, SvgLineChartComponent } from './svg-line-chart.component';

const CURRENCY_CODE_PATTERN = /^[A-Za-z]{3}$/;

interface InsightParams {
  from: string;
  to: string;
  fromDate: string;
  toDate: string;
}

/**
 * T050: composes a pair+date-range picker with `RateTableComponent`,
 * `SvgLineChartComponent`, and `InsightPanelComponent` side by side. On
 * submit, the history fetch (owned here, feeding the table/chart) and the
 * insight fetch (owned entirely by `InsightPanelComponent`, triggered by
 * updating its inputs) both start in the same synchronous tick — genuinely
 * in parallel, with no dependency on one another's outcome, per FR-017 and
 * spec.md User Story 2 Acceptance Scenarios 3–4.
 */
@Component({
  selector: 'app-historical-trend',
  imports: [ReactiveFormsModule, RateTableComponent, SvgLineChartComponent, InsightPanelComponent],
  templateUrl: './historical-trend.component.html',
  styleUrl: './historical-trend.component.scss',
})
export class HistoricalTrendComponent {
  private readonly fb = inject(FormBuilder);
  private readonly historyService = inject(HistoryService);

  protected readonly historyLoading = signal(false);
  protected readonly historyError = signal<string | null>(null);
  protected readonly historyResponse = signal<HistoryResponse | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly insightParams = signal<InsightParams | null>(null);

  protected readonly chartPoints = computed<RatePoint[]>(() => {
    const history = this.historyResponse();
    return history ? history.points.map((point) => ({ date: point.date, rate: point.exchange })) : [];
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
