import { Component, computed, input } from '@angular/core';

/** One (date, value) point this chart plots — deliberately generic, not tied to any one response shape. */
export interface RatePoint {
  date: string;
  rate: number;
}

export interface AxisTick {
  position: number;
  label: string;
  /** Full-precision text for a hover tooltip — e.g. the full ISO date behind a shortened x-tick label. */
  title?: string;
}

export interface LineChartGeometry {
  polylinePoints: string;
  xTicks: AxisTick[];
  yTicks: AxisTick[];
  /** True once even the shortened date format would crowd at the selected tick spacing. */
  rotateXLabels: boolean;
  /** Plot-area bounds — distinct from the raw `padding` input once axis titles need their own reserved bands outside it. */
  plotLeft: number;
  plotRight: number;
  plotTop: number;
  plotBottom: number;
}

const Y_TICK_COUNT = 5;
const MAX_X_TICKS = 7;
/** Minimum horizontal space (px) reserved per x-tick, sized for the shortened "DD/MM" label (5 chars) at 0.7rem. */
const MIN_X_TICK_SPACING = 45;
/** Below this actual per-tick spacing (px), even the shortened label needs rotating to avoid crowding. */
const ROTATION_THRESHOLD = 40;
/** Extra left margin (beyond `padding`) for the rotated Y-axis title, kept clear of the tick-value labels. */
const LEFT_AXIS_TITLE_SPACE = 24;
/** Extra bottom margin (beyond `padding`) for the X-axis title, kept clear of the date tick labels. */
const BOTTOM_AXIS_TITLE_SPACE = 32;
/** Extra top margin (beyond `padding`) for the chart's own title. */
const TOP_TITLE_SPACE = 24;

/**
 * Pure function mapping `{date, rate}[]` to an SVG `<polyline>` points
 * string plus min/max-scaled axis ticks (research.md Decision 1 — no
 * charting-library dependency). Kept separate from the component class so
 * it can be unit-tested directly: input points in, geometry out, no
 * DOM/component instantiation involved.
 *
 * Fewer than two points is treated as "nothing to draw a trend with," not
 * just "empty": a single point has no line to plot, and axis ticks derived
 * from one value would just repeat the same min/max/date on both ends,
 * telling the viewer nothing. `SvgLineChartComponent` shows a plain message
 * for this whole `< 2` case instead of a degenerate chart.
 *
 * X-tick positions are placed at perfectly even fractions of the plot
 * width — not at the (slightly index-rounded) coordinate of whichever data
 * point they label — so tick spacing is always exactly uniform regardless
 * of how the desired tick count divides into the point count. Labels use a
 * short `DD/MM` format (the full ISO date is still on `title`, for a hover
 * tooltip, and stays available in full elsewhere — e.g. RateTableComponent
 * — unaffected by this); `rotateXLabels` is a last-resort signal for the
 * rare case (e.g. an unusually narrow custom `width`) where even that short
 * format would still crowd at the tick spacing this function had to fall
 * back to.
 */
export function mapPointsToLineChart(
  points: readonly RatePoint[],
  width: number,
  height: number,
  padding: number,
): LineChartGeometry {
  const plotLeft = padding + LEFT_AXIS_TITLE_SPACE;
  const plotRight = width - padding;
  const plotTop = padding + TOP_TITLE_SPACE;
  const plotBottom = height - padding - BOTTOM_AXIS_TITLE_SPACE;

  if (points.length < 2) {
    return {
      polylinePoints: '',
      xTicks: [],
      yTicks: [],
      rotateXLabels: false,
      plotLeft,
      plotRight,
      plotTop,
      plotBottom,
    };
  }

  const rates = points.map((point) => point.rate);
  const minRate = Math.min(...rates);
  const maxRate = Math.max(...rates);
  // Two distinct ranges on purpose: trueRateRange (can legitimately be 0,
  // when every point has the same rate) drives tick *values* — 0 there
  // just means every tick equals that one rate, which is correct. Dividing
  // by it to scale a *pixel position* would NaN, so that division alone
  // uses the guarded fallback instead.
  const trueRateRange = maxRate - minRate;
  const scaleRateRange = trueRateRange || 1;

  const innerWidth = plotRight - plotLeft;
  const innerHeight = plotBottom - plotTop;
  const xStep = innerWidth / (points.length - 1);

  const coordinates = points.map((point, index) => ({
    x: plotLeft + index * xStep,
    y: plotTop + innerHeight - ((point.rate - minRate) / scaleRateRange) * innerHeight,
  }));

  const yTicks: AxisTick[] = [];
  for (let i = 0; i < Y_TICK_COUNT; i++) {
    const fraction = i / (Y_TICK_COUNT - 1);
    yTicks.push({
      position: plotTop + innerHeight - fraction * innerHeight,
      label: formatTickLabel(minRate + fraction * trueRateRange),
    });
  }

  const desiredXTickCount = Math.max(2, Math.min(MAX_X_TICKS, Math.floor(innerWidth / MIN_X_TICK_SPACING) + 1));
  const xTickCount = Math.min(desiredXTickCount, points.length);
  const xTickSpacing = xTickCount > 1 ? innerWidth / (xTickCount - 1) : innerWidth;
  const rotateXLabels = xTickSpacing < ROTATION_THRESHOLD;

  const xTicks: AxisTick[] = [];
  for (let i = 0; i < xTickCount; i++) {
    const fraction = xTickCount > 1 ? i / (xTickCount - 1) : 0;
    const pointIndex = Math.round(fraction * (points.length - 1));
    const isoDate = points[pointIndex].date;
    xTicks.push({
      position: plotLeft + fraction * innerWidth,
      label: formatShortDate(isoDate),
      title: isoDate,
    });
  }

  return {
    polylinePoints: coordinates.map((c) => `${c.x},${c.y}`).join(' '),
    yTicks,
    xTicks,
    rotateXLabels,
    plotLeft,
    plotRight,
    plotTop,
    plotBottom,
  };
}

function formatTickLabel(value: number): string {
  return Number.isInteger(value) ? value.toString() : value.toFixed(4);
}

/** `"YYYY-MM-DD"` -> `"DD/MM"` — short enough to fit up to 7 ticks without crowding. */
function formatShortDate(isoDate: string): string {
  const [, month, day] = isoDate.split('-');
  return `${day}/${month}`;
}

/**
 * Hand-rolled line chart for the historical rate trend — a thin wrapper
 * around {@link mapPointsToLineChart}. No third-party charting dependency,
 * per research.md Decision 1 ("the chart does not need to be elaborate") —
 * more ticks, axis titles, and horizontal-only reference lines improve
 * readability without growing this into a full charting component (no
 * vertical gridlines, no interactivity, no library). `title` stays a plain
 * string input rather than e.g. currency-code props, so this component
 * remains generic and reusable — a composing parent decides what the title
 * says.
 */
@Component({
  selector: 'app-svg-line-chart',
  templateUrl: './svg-line-chart.component.html',
  styleUrl: './svg-line-chart.component.scss',
})
export class SvgLineChartComponent {
  readonly points = input<RatePoint[]>([]);
  readonly width = input(600);
  readonly height = input(300);
  readonly padding = input(48);
  readonly title = input('Exchange Rate Trend');

  protected readonly geometry = computed(() =>
    mapPointsToLineChart(this.points(), this.width(), this.height(), this.padding()),
  );
}
