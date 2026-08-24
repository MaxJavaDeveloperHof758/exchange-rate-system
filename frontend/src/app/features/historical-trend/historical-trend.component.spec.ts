import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { HistoryResponse } from '../../core/models/history.model';
import { InsightResponse } from '../../core/models/insight.model';
import { HistoryService } from '../../core/services/history.service';
import { InsightService } from '../../core/services/insight.service';
import { HistoricalTrendComponent } from './historical-trend.component';

function mockHistoryResponse(): HistoryResponse {
  return {
    from: 'EUR',
    to: 'PLN',
    startDate: '2026-08-18',
    endDate: '2026-08-24',
    points: [{ date: '2026-08-24', fromRateToUsd: 0.8, toRateToUsd: 3.7, adjustedRate: 4.4978125 }],
    missingDates: [],
  };
}

function mockInsightResponse(): InsightResponse {
  return { from: 'EUR', to: 'PLN', fromDate: '2026-08-18', toDate: '2026-08-24', insight: 'Steady.' };
}

/**
 * Drives the component purely through its rendered DOM — same convention
 * as CalculatorComponent.spec.ts. No coverage existed for this component
 * before the currency-combobox/date-preset redesign; this is the first.
 */
describe('HistoricalTrendComponent', () => {
  let fixture: ComponentFixture<HistoricalTrendComponent>;
  let getHistory: ReturnType<typeof vi.fn>;
  let getInsight: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    getHistory = vi.fn().mockReturnValue(of(mockHistoryResponse()));
    getInsight = vi.fn().mockReturnValue(of(mockInsightResponse()));

    await TestBed.configureTestingModule({
      imports: [HistoricalTrendComponent],
      providers: [
        { provide: HistoryService, useValue: { getHistory } },
        { provide: InsightService, useValue: { getInsight } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HistoricalTrendComponent);
    fixture.detectChanges();
  });

  function setInputValue(selector: string, value: string): void {
    const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function fillForm(from: string, to: string, startDate: string, endDate: string): void {
    setInputValue('#from', from);
    setInputValue('#to', to);
    setInputValue('#startDate', startDate);
    setInputValue('#endDate', endDate);
    fixture.detectChanges();
  }

  function submit(): void {
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    button.click();
    fixture.detectChanges();
  }

  function clickPreset(label: string): void {
    const buttons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('button.preset'),
    );
    const button = buttons.find((el) => el.textContent?.trim() === label);
    button!.click();
    fixture.detectChanges();
  }

  it('rejects an incomplete form before submit and never calls the service', () => {
    fillForm('E', '', '', '');

    submit();

    expect(getHistory).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('.field-error').length).toBe(4);
  });

  it('rejects a start date after the end date without calling the service', () => {
    fillForm('EUR', 'PLN', '2026-08-24', '2026-08-18');

    submit();

    const errorEl: HTMLElement = fixture.nativeElement.querySelector('.status.error');
    expect(errorEl?.textContent).toContain('Start date must not be after end date.');
    expect(getHistory).not.toHaveBeenCalled();
  });

  it('submits with uppercased codes and renders the table/chart on success', () => {
    fillForm('eur', 'pln', '2026-08-18', '2026-08-24');

    submit();

    expect(getHistory).toHaveBeenCalledWith('EUR', 'PLN', '2026-08-18', '2026-08-24');
    expect(fixture.nativeElement.querySelector('app-rate-table')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-svg-line-chart')).toBeTruthy();
    // Setting insightParams (independent of the history call above) is
    // what starts InsightPanelComponent's own request via its inputs.
    expect(fixture.nativeElement.querySelector('app-insight-panel')).toBeTruthy();
  });

  it('renders a distinct error message on a mocked failure', () => {
    getHistory.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { error: 'RATE_NOT_AVAILABLE', message: 'No common rate date available for EUR/PLN' },
          }),
      ),
    );
    fillForm('EUR', 'PLN', '2026-08-18', '2026-08-24');

    submit();

    const errorEl: HTMLElement = fixture.nativeElement.querySelector('.status.error');
    expect(errorEl?.textContent).toContain('No common rate date available for EUR/PLN');
  });

  describe('quick-range presets', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date(2026, 7, 24)); // 24 Aug 2026 (month is 0-based)
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    function dateFields(): { start: string; end: string } {
      const start: HTMLInputElement = fixture.nativeElement.querySelector('#startDate');
      const end: HTMLInputElement = fixture.nativeElement.querySelector('#endDate');
      return { start: start.value, end: end.value };
    }

    it('"Last 7 days" sets a 7-day range ending today', () => {
      clickPreset('Last 7 days');

      expect(dateFields()).toEqual({ start: '2026-08-18', end: '2026-08-24' });
    });

    it('"Last 30 days" sets a 30-day range ending today', () => {
      clickPreset('Last 30 days');

      expect(dateFields()).toEqual({ start: '2026-07-26', end: '2026-08-24' });
    });

    it('"This month" sets a range from the 1st of the current month through today', () => {
      clickPreset('This month');

      expect(dateFields()).toEqual({ start: '2026-08-01', end: '2026-08-24' });
    });

    it('a preset click alone does not submit the form', () => {
      clickPreset('Last 7 days');

      expect(getHistory).not.toHaveBeenCalled();
    });
  });
});
