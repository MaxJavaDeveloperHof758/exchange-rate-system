import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiErrorResponse } from '../../core/models/api-error.model';
import { ExchangeRateResponse } from '../../core/models/exchange-rate.model';
import { ExchangeRateService } from '../../core/services/exchange-rate.service';

const CURRENCY_CODE_PATTERN = /^[A-Za-z]{3}$/;

/**
 * User Story 1's Calculator view (T043): a reactive from/to/optional-date
 * form, a loading state while `GET /api/exchange` is in flight, a distinct
 * error state on 400/404 (contracts/exchange.md, FR-020), and a success
 * state rendering the rate plus both currencies' post-increment query
 * counts.
 */
@Component({
  selector: 'app-calculator',
  imports: [ReactiveFormsModule],
  templateUrl: './calculator.component.html',
  styleUrl: './calculator.component.scss',
})
export class CalculatorComponent {
  private readonly fb = inject(FormBuilder);
  private readonly exchangeRateService = inject(ExchangeRateService);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly result = signal<ExchangeRateResponse | null>(null);

  protected readonly form = this.fb.group({
    from: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.pattern(CURRENCY_CODE_PATTERN),
    ]),
    to: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.pattern(CURRENCY_CODE_PATTERN),
    ]),
    date: this.fb.control<string | null>(null),
  });

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { from, to, date } = this.form.getRawValue();
    this.loading.set(true);
    this.errorMessage.set(null);
    this.result.set(null);

    this.exchangeRateService.getRate(from.toUpperCase(), to.toUpperCase(), date ?? undefined).subscribe({
      next: (response) => {
        this.result.set(response);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(this.extractErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  private extractErrorMessage(err: HttpErrorResponse): string {
    const body = err.error as ApiErrorResponse | null;
    return body?.message ?? 'Something went wrong. Please try again.';
  }
}
