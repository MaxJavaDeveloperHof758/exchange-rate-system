import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, switchMap } from 'rxjs';

import { ApiErrorResponse } from '../../core/models/api-error.model';
import { InsightService } from '../../core/services/insight.service';

type InsightState =
  | { status: 'loading' }
  | { status: 'success'; insight: string }
  | { status: 'error'; message: string };

const LOADING_STATE: InsightState = { status: 'loading' };

/**
 * T049: the AI trend commentary, with its own loading/error/success state
 * — entirely independent of whatever drives the table/chart's data (FR-017,
 * spec.md User Story 2 Acceptance Scenarios 3–4). Fetches its own insight
 * reactively whenever its from/to/fromDate/toDate inputs change, via
 * `switchMap` — so a stale, still-in-flight request for a previous date
 * range can never overwrite a newer one's result (the AI call is the
 * "typically slower" one per contracts/insight.md, making rapid range
 * changes a real scenario, not a hypothetical).
 */
@Component({
  selector: 'app-insight-panel',
  templateUrl: './insight-panel.component.html',
  styleUrl: './insight-panel.component.scss',
})
export class InsightPanelComponent {
  readonly from = input.required<string>();
  readonly to = input.required<string>();
  readonly fromDate = input.required<string>();
  readonly toDate = input.required<string>();

  private readonly insightService = inject(InsightService);

  private readonly params = computed(() => ({
    from: this.from(),
    to: this.to(),
    fromDate: this.fromDate(),
    toDate: this.toDate(),
  }));

  protected readonly state = toSignal(
    toObservable(this.params).pipe(
      switchMap((p) =>
        this.insightService.getInsight(p.from, p.to, p.fromDate, p.toDate).pipe(
          map((response): InsightState => ({ status: 'success', insight: response.insight })),
          catchError((err: HttpErrorResponse) =>
            of<InsightState>({ status: 'error', message: extractErrorMessage(err) }),
          ),
          startWith(LOADING_STATE),
        ),
      ),
    ),
    { initialValue: LOADING_STATE },
  );
}

function extractErrorMessage(err: HttpErrorResponse): string {
  const body = err.error as ApiErrorResponse | null;
  return body?.message ?? 'The trend insight could not be generated.';
}
