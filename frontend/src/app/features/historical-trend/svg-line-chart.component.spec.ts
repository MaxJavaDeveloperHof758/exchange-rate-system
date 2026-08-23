import { mapPointsToLineChart } from './svg-line-chart.component';

/**
 * T047: tests the pure `mapPointsToLineChart` mapping function directly —
 * no TestBed, no component instantiation, no DOM — per research.md
 * Decision 1's own rationale for keeping it a plain function.
 */
describe('mapPointsToLineChart', () => {
  it('scales points onto the chart using min/max rate, linearly in between', () => {
    const points = [
      { date: '2024-03-01', rate: 10 },
      { date: '2024-03-02', rate: 15 },
      { date: '2024-03-03', rate: 20 },
    ];

    const geometry = mapPointsToLineChart(points, 100, 50, 0);

    // min (10) -> bottom (y=50), max (20) -> top (y=0), mid (15) -> exact
    // midpoint (y=25); x is evenly spaced across the 3 points.
    expect(geometry.polylinePoints).toBe('0,50 50,25 100,0');
  });

  it('produces min/max axis ticks and first/last date ticks', () => {
    const points = [
      { date: '2024-03-01', rate: 4.41 },
      { date: '2024-03-05', rate: 4.47 },
    ];

    const geometry = mapPointsToLineChart(points, 100, 100, 10);

    expect(geometry.yTicks).toEqual([
      { position: 10, label: '4.4700' },
      { position: 90, label: '4.4100' },
    ]);
    expect(geometry.xTicks).toEqual([
      { position: 10, label: '2024-03-01' },
      { position: 90, label: '2024-03-05' },
    ]);
  });

  it('returns empty geometry for an empty points array', () => {
    const geometry = mapPointsToLineChart([], 600, 240, 32);

    expect(geometry).toEqual({ polylinePoints: '', xTicks: [], yTicks: [] });
  });

  it('does not divide by zero when every point has the same rate', () => {
    const points = [
      { date: '2024-03-01', rate: 5 },
      { date: '2024-03-02', rate: 5 },
    ];

    const geometry = mapPointsToLineChart(points, 100, 100, 10);

    expect(geometry.polylinePoints).toBe('10,90 90,90');
    expect(geometry.yTicks).toEqual([
      { position: 10, label: '5' },
      { position: 90, label: '5' },
    ]);
  });
});
