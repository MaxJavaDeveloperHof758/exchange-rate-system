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

    // width/height/padding chosen so plotLeft=24, plotRight=124,
    // plotTop=24, plotBottom=64 -> innerWidth=100, innerHeight=40.
    const geometry = mapPointsToLineChart(points, 124, 96, 0);

    // min (10) -> bottom (y=64), max (20) -> top (y=24), mid (15) -> exact
    // midpoint (y=44); x is evenly spaced across the 3 points.
    expect(geometry.polylinePoints).toBe('24,64 74,44 124,24');
  });

  it('produces 5 evenly-spaced y-axis ticks and short-format first/last date ticks with a full-date tooltip', () => {
    const points = [
      { date: '2024-03-01', rate: 4.41 },
      { date: '2024-03-05', rate: 4.47 },
    ];

    // plotLeft=34, plotRight=114, plotTop=34, plotBottom=114 ->
    // innerWidth=80, innerHeight=80.
    const geometry = mapPointsToLineChart(points, 124, 156, 10);

    // 5 ticks evenly interpolated between min (4.41) and max (4.47),
    // bottom (min) to top (max).
    expect(geometry.yTicks).toEqual([
      { position: 114, label: '4.4100' },
      { position: 94, label: '4.4250' },
      { position: 74, label: '4.4400' },
      { position: 54, label: '4.4550' },
      { position: 34, label: '4.4700' },
    ]);
    // Only 2 points fit within a narrow 80px-wide plot at the minimum
    // tick spacing, so both ends are ticked (first/last), same as before —
    // but the label is now the short DD/MM form, with the full ISO date
    // preserved on `title` for a hover tooltip.
    expect(geometry.xTicks).toEqual([
      { position: 34, label: '01/03', title: '2024-03-01' },
      { position: 114, label: '05/03', title: '2024-03-05' },
    ]);
    expect(geometry.rotateXLabels).toBe(false);
  });

  it('samples evenly-spaced x-axis ticks from a larger series, at perfectly uniform pixel spacing', () => {
    const points = Array.from({ length: 9 }, (_, i) => ({
      date: `2024-01-0${i + 1}`,
      rate: 1,
    }));

    // plotLeft=24, plotRight=304 -> innerWidth=280, wide enough for
    // floor(280/45)+1 = 7 ticks, capped at MAX_X_TICKS.
    const geometry = mapPointsToLineChart(points, 304, 96, 0);

    expect(geometry.xTicks.length).toBe(7);
    expect(geometry.xTicks[0]).toEqual({ position: 24, label: '01/01', title: '2024-01-01' });
    expect(geometry.xTicks.at(-1)).toEqual({ position: 304, label: '09/01', title: '2024-01-09' });
    // The actual point of the fix: positions are perfectly evenly spaced,
    // not merely close to it (rounded to tolerate float noise from the
    // fraction arithmetic itself, not from any unevenness in the spacing).
    const gaps = geometry.xTicks.slice(1).map((tick, i) => tick.position - geometry.xTicks[i].position);
    const roundedGaps = new Set(gaps.map((gap) => Math.round(gap * 1000) / 1000));
    expect(roundedGaps.size).toBe(1);
  });

  it('caps x-ticks at 7 even for a very wide chart with many points', () => {
    const points = Array.from({ length: 50 }, (_, i) => ({ date: `2024-01-${i + 1}`, rate: 1 }));

    const geometry = mapPointsToLineChart(points, 2000, 96, 0);

    expect(geometry.xTicks.length).toBe(7);
    expect(geometry.rotateXLabels).toBe(false);
  });

  it('flags rotateXLabels when even the shortened format would crowd at a very narrow width', () => {
    const points = [
      { date: '2024-03-01', rate: 1 },
      { date: '2024-03-02', rate: 1 },
    ];

    // plotLeft=24, plotRight=50 -> innerWidth=26, forcing 2 ticks 26px
    // apart — below the shortened label's rotation threshold.
    const geometry = mapPointsToLineChart(points, 50, 96, 0);

    expect(geometry.rotateXLabels).toBe(true);
  });

  it('returns empty geometry (but still-computed plot bounds) for an empty points array', () => {
    const geometry = mapPointsToLineChart([], 600, 240, 32);

    expect(geometry).toEqual({
      polylinePoints: '',
      xTicks: [],
      yTicks: [],
      rotateXLabels: false,
      plotLeft: 56,
      plotRight: 568,
      plotTop: 56,
      plotBottom: 176,
    });
  });

  it('also returns empty geometry for a single point — no line can be drawn with one coordinate', () => {
    const points = [{ date: '2026-08-23', rate: 4.4978125 }];

    const geometry = mapPointsToLineChart(points, 100, 100, 10);

    expect(geometry).toEqual({
      polylinePoints: '',
      xTicks: [],
      yTicks: [],
      rotateXLabels: false,
      plotLeft: 34,
      plotRight: 90,
      plotTop: 34,
      plotBottom: 58,
    });
  });

  it('does not divide by zero when every point has the same rate', () => {
    const points = [
      { date: '2024-03-01', rate: 5 },
      { date: '2024-03-02', rate: 5 },
    ];

    const geometry = mapPointsToLineChart(points, 100, 100, 10);

    expect(geometry.polylinePoints).toBe('34,58 90,58');
    // Every tick must show '5' — a flat series has a real (zero) rate
    // range, which must not be confused with the position-scaling
    // fallback used only to avoid a division by zero.
    expect(geometry.yTicks).toEqual([
      { position: 58, label: '5' },
      { position: 52, label: '5' },
      { position: 46, label: '5' },
      { position: 40, label: '5' },
      { position: 34, label: '5' },
    ]);
  });
});
