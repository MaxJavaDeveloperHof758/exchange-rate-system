import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { ExchangeRateResponse } from '../../core/models/exchange-rate.model';
import { ExchangeRateService } from '../../core/services/exchange-rate.service';
import { CalculatorComponent } from './calculator.component';

function mockSuccessResponse(): ExchangeRateResponse {
  return {
    from: 'EUR',
    to: 'PLN',
    exchange: 4.4978125,
    date: '2026-08-23',
    fromQueryCount: 1,
    toQueryCount: 1,
  };
}

/**
 * T044: drives the component purely through its rendered DOM (typed input,
 * clicked submit, read-back status/result elements) rather than reaching
 * into `component.form`/`component.loading()` etc. — those are
 * deliberately `protected` (template-bound, not public API), and every
 * scenario this task asks for is itself phrased in terms of what's
 * rendered, not internal state.
 */
describe('CalculatorComponent', () => {
  let fixture: ComponentFixture<CalculatorComponent>;
  let getRate: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    getRate = vi.fn();

    await TestBed.configureTestingModule({
      imports: [CalculatorComponent],
      providers: [{ provide: ExchangeRateService, useValue: { getRate } }],
    }).compileComponents();

    fixture = TestBed.createComponent(CalculatorComponent);
    fixture.detectChanges();
  });

  function setInputValue(selector: string, value: string): void {
    const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function fillForm(from: string, to: string, date = ''): void {
    setInputValue('#from', from);
    setInputValue('#to', to);
    if (date) {
      setInputValue('#date', date);
    }
    fixture.detectChanges();
  }

  function submit(): void {
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    button.click();
    fixture.detectChanges();
  }

  it('rejects invalid input before submit and never calls the service', () => {
    fillForm('E', '');

    submit();

    expect(getRate).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('.field-error').length).toBe(2);
  });

  it('shows a loading indicator while the request is in flight', () => {
    const subject = new Subject<ExchangeRateResponse>();
    getRate.mockReturnValue(subject.asObservable());
    fillForm('EUR', 'PLN');

    submit();

    expect(fixture.nativeElement.querySelector('.status.loading')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.result')).toBeFalsy();

    subject.next(mockSuccessResponse());
    subject.complete();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.status.loading')).toBeFalsy();
  });

  it('renders a distinct error message on a mocked 404', () => {
    const notFound = new HttpErrorResponse({
      status: 404,
      error: {
        error: 'RATE_NOT_AVAILABLE',
        message: 'No rate data available for EUR/PLN on 2024-03-15',
      },
    });
    getRate.mockReturnValue(throwError(() => notFound));
    fillForm('EUR', 'PLN', '2024-03-15');

    submit();

    const errorEl: HTMLElement = fixture.nativeElement.querySelector('.status.error');
    expect(errorEl?.textContent).toContain('No rate data available for EUR/PLN on 2024-03-15');
    expect(fixture.nativeElement.querySelector('.result')).toBeFalsy();
  });

  it('renders the success view on a mocked 200', () => {
    getRate.mockReturnValue(of(mockSuccessResponse()));
    fillForm('EUR', 'PLN');

    submit();

    const resultEl: HTMLElement = fixture.nativeElement.querySelector('.result');
    expect(resultEl?.textContent).toContain('4.4978125');
    expect(resultEl?.textContent).toContain('EUR query count: 1');
    expect(resultEl?.textContent).toContain('PLN query count: 1');
    expect(fixture.nativeElement.querySelector('.status.error')).toBeFalsy();
  });
});
