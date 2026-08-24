import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HistoryResponse } from '../../core/models/history.model';
import { RateTableComponent } from './rate-table.component';

/**
 * Drives the component purely through its rendered DOM, matching the
 * convention already used by CalculatorComponent.spec.ts. Covers the
 * behavior this component gained when the backend started returning each
 * currency's raw rate-to-USD alongside the derived pair rate (FR-014):
 * both raw columns render for a normal pair, both render as "—" for a
 * same-currency pair (no lookup is performed for those), and a missing
 * date still spans a single "No data available" indicator rather than
 * three empty cells.
 */
describe('RateTableComponent', () => {
  let fixture: ComponentFixture<RateTableComponent>;

  function render(history: HistoryResponse | null): void {
    fixture = TestBed.createComponent(RateTableComponent);
    fixture.componentRef.setInput('history', history);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RateTableComponent] }).compileComponents();
  });

  it('renders each currency raw rate-to-USD alongside the adjusted pair rate', () => {
    render({
      from: 'EUR',
      to: 'PLN',
      startDate: '2026-03-01',
      endDate: '2026-03-01',
      points: [{ date: '2026-03-01', fromRateToUsd: 0.8, toRateToUsd: 3.7, adjustedRate: 4.4978125 }],
      missingDates: [],
    });

    const cells: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('tbody td'));
    const cellText = cells.map((cell) => cell.textContent?.trim());

    expect(cellText).toEqual(['2026-03-01', '0.800000', '3.700000', '4.497813']);
    expect(fixture.nativeElement.querySelector('.missing-indicator')).toBeFalsy();
  });

  it('renders "—" for the raw-rate columns of a same-currency pair, which has no lookup at all', () => {
    render({
      from: 'EUR',
      to: 'EUR',
      startDate: '2026-03-01',
      endDate: '2026-03-01',
      points: [{ date: '2026-03-01', fromRateToUsd: null, toRateToUsd: null, adjustedRate: 1 }],
      missingDates: [],
    });

    const cells: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('tbody td'));
    const cellText = cells.map((cell) => cell.textContent?.trim());

    expect(cellText).toEqual(['2026-03-01', '—', '—', '1.000000']);
  });

  it('shows a single missing-data indicator spanning the row, not three empty raw-rate cells', () => {
    render({
      from: 'EUR',
      to: 'PLN',
      startDate: '2026-03-01',
      endDate: '2026-03-01',
      points: [],
      missingDates: ['2026-03-01'],
    });

    const row: HTMLElement = fixture.nativeElement.querySelector('tbody tr');
    expect(row.classList).toContain('missing');
    expect(row.querySelectorAll('td').length).toBe(2);
    expect(row.querySelector('.missing-indicator')?.textContent).toContain('No data available');
  });

  it('shows a fallback message when no history has been loaded yet', () => {
    render(null);

    expect(fixture.nativeElement.textContent).toContain('No data to display.');
    expect(fixture.nativeElement.querySelector('table')).toBeFalsy();
  });
});
