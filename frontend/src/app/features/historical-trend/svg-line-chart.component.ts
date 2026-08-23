import { Component, computed, input } from '@angular/core';

/** One (date, value) point this chart plots — deliberately generic, not tied to any one response shape. */
export interface RatePoint {
  date: string;
  rate: number;
}

export interface AxisTick {
  position: number;
  label: string;
}

export interface LineChartGeometry {
  polylinePoints: string;
  xTicks: AxisTick[];
  yTicks: AxisTick[];
}

/**
 * T046: pure function mapping `{date, rate}[]` to an SVG `<polyline>` points
 * string plus simple min/max axis ticks (research.md Decision 1 — no
 * charting-library dependency). Kept separate from the component class so
 * T047 can unit-test the point-to-path mapping directly: input points in,
 * geometry out, no DOM/component instantiation involved.
 */
export function mapPointsToLineChart(
  points: readonly RatePoint[],
  width: number,
  height: number,
  padding: number,
): LineChartGeometry {
  if (points.length === 0) {
    return { polylinePoints: '', xTicks: [], yTicks: [] };
  }

  const rates = points.map((point) => point.rate);
  const minRate = Math.min(...rates);
  const maxRate = Math.max(...rates);
  const rateRange = maxRate - minRate || 1;

  const innerWidth = width - padding * 2;
  const innerHeight = height - padding * 2;
  const xStep = points.length > 1 ? innerWidth / (points.length - 1) : 0;

  const coordinates = points.map((point, index) => ({
    x: padding + index * xStep,
    y: padding + innerHeight - ((point.rate - minRate) / rateRange) * innerHeight,
  }));

  return {
    polylinePoints: coordinates.map((c) => `${c.x},${c.y}`).join(' '),
    yTicks: [
      { position: padding, label: formatTickLabel(maxRate) },
      { position: padding + innerHeight, label: formatTickLabel(minRate) },
    ],
    xTicks: [
      { position: padding, label: points[0].date },
      { position: padding + innerWidth, label: points[points.length - 1].date },
    ],
  };
}

function formatTickLabel(value: number): string {
  return Number.isInteger(value) ? value.toString() : value.toFixed(4);
}

/**
 * Hand-rolled line chart for the historical rate trend — a thin wrapper
 * around {@link mapPointsToLineChart}. No third-party charting dependency,
 * per research.md Decision 1 ("the chart does not need to be elaborate").
 */
@Component({
  selector: 'app-svg-line-chart',
  templateUrl: './svg-line-chart.component.html',
  styleUrl: './svg-line-chart.component.scss',
})
export class SvgLineChartComponent {
  readonly points = input<RatePoint[]>([]);
  readonly width = input(600);
  readonly height = input(240);
  readonly padding = input(32);

  protected readonly geometry = computed(() =>
    mapPointsToLineChart(this.points(), this.width(), this.height(), this.padding()),
  );
}
