import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AnalyticsResponse } from '../../core/models/analytics.model';
import { AnalyticsService } from '../../core/services/analytics.service';
import { AnalyticsDashboardComponent } from './analytics-dashboard.component';

/**
 * T053: drives the component purely through its rendered DOM, same
 * convention as CalculatorComponent.spec.ts — `loading`/`errorMessage`/
 * `entries` are deliberately `protected` (template-bound, not public API).
 * Covers the two states T053 calls out: the empty-data state (no currency
 * has ever been queried — a valid success response, not an error) and a
 * populated-ranking render (bars present, in the order the response
 * already sorted them, sized proportionally to their usage count).
 */
describe('AnalyticsDashboardComponent', () => {
  let fixture: ComponentFixture<AnalyticsDashboardComponent>;
  let getAnalytics: ReturnType<typeof vi.fn>;

  function createComponent(response: AnalyticsResponse): void {
    getAnalytics = vi.fn().mockReturnValue(of(response));

    TestBed.configureTestingModule({
      imports: [AnalyticsDashboardComponent],
      providers: [{ provide: AnalyticsService, useValue: { getAnalytics } }],
    });

    fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    fixture.detectChanges();
  }

  it('renders the empty-data message when no currency has ever been queried', () => {
    createComponent({ topCurrencies: [] });

    expect(getAnalytics).toHaveBeenCalled();
    const emptyEl: HTMLElement = fixture.nativeElement.querySelector('.status.empty');
    expect(emptyEl?.textContent).toContain('No currencies have been queried yet.');
    expect(fixture.nativeElement.querySelector('svg')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('.status.error')).toBeFalsy();
  });

  it('renders a ranked bar for each currency, sized proportionally to its usage count', () => {
    createComponent({
      topCurrencies: [
        { currency: 'EUR', totalCount: 10, lastQueried: '2024-03-15' },
        { currency: 'PLN', totalCount: 5, lastQueried: '2024-03-14' },
      ],
    });

    const labels: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.bar-label'));
    expect(labels.map((el) => el.textContent)).toEqual(['EUR', 'PLN']);

    const values: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.bar-value'));
    expect(values[0].textContent).toContain('10 queries · last 2024-03-15');
    expect(values[1].textContent).toContain('5 queries · last 2024-03-14');

    const bars: SVGRectElement[] = Array.from(fixture.nativeElement.querySelectorAll('rect.bar'));
    const widths = bars.map((bar) => Number(bar.getAttribute('width')));
    expect(widths.length).toBe(2);
    // The top-ranked currency's count is the max, so its bar spans the full
    // plot width; the rest scale proportionally to it (10:5 = 2:1) — not a
    // hardcoded pixel value, since that would just re-assert this test's
    // own arithmetic against the component's internal width/padding
    // constants instead of the actual invariant being tested.
    expect(widths[0]).toBeGreaterThan(0);
    expect(widths[0] / widths[1]).toBeCloseTo(2, 5);

    expect(fixture.nativeElement.querySelector('.status.empty')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('.status.error')).toBeFalsy();
  });
});
