import {useEffect, useRef, useState} from 'react';
import {BarChart3, Table2} from 'lucide-react';
import type {EChartsCoreOption} from 'echarts/core';
import {Button} from '../../ui/Button';
import {Card} from '../../ui/Card';
import {Table} from '../../ui/Table';
import {uiStrings} from '../../ui/strings';
import {STATUSES, countsTable, passRateTable, type ChartRun} from './chartData';
import {runStrings as t} from './strings';

/** Reads a DS-1 / DS-2 token from the document, so the charts follow the theme. */
function token(name: string): string {
    return getComputedStyle(document.documentElement).getPropertyValue(`--${name}`).trim() || '#888888';
}

/** Re-renders when the theme (the data-theme attribute) changes. */
function useThemeVersion(): number {
    const [version, setVersion] = useState(0);
    useEffect(() => {
        const observer = new MutationObserver(() => setVersion((v) => v + 1));
        observer.observe(document.documentElement, {attributes: true, attributeFilter: ['data-theme']});
        return () => observer.disconnect();
    }, []);
    return version;
}

function axisStyle() {
    return {
        axisLine: {lineStyle: {color: token('border')}},
        axisTick: {show: false},
        axisLabel: {color: token('text-muted'), fontSize: 11},
        splitLine: {lineStyle: {color: token('border')}},
    };
}

export function countsOption(runs: readonly ChartRun[]): EChartsCoreOption {
    const rows = countsTable(runs);
    return {
        animation: !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches,
        grid: {left: 32, right: 12, top: 32, bottom: 28},
        legend: {top: 0, left: 0, itemWidth: 10, itemHeight: 10, textStyle: {color: token('text-muted'), fontSize: 11}},
        tooltip: {trigger: 'axis', backgroundColor: token('surface-overlay'), borderColor: token('border'), textStyle: {color: token('text')}},
        xAxis: {type: 'category', data: rows.map((r) => r.name), ...axisStyle()},
        yAxis: {type: 'value', minInterval: 1, ...axisStyle()},
        series: STATUSES.map((status, i) => ({
            name: uiStrings.status[status] ?? status,
            type: 'bar',
            stack: 'counts',
            barMaxWidth: 28,
            itemStyle: {color: token(`status-${status.toLowerCase()}-fg`)},
            data: rows.map((r) => r.values[i] ?? 0),
        })),
    };
}

export function rateOption(runs: readonly ChartRun[]): EChartsCoreOption {
    const ordered = [...runs].reverse();
    return {
        animation: !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches,
        grid: {left: 40, right: 12, top: 16, bottom: 28},
        tooltip: {trigger: 'axis', valueFormatter: (v: unknown) => (typeof v === 'number' ? `${v.toFixed(1)}%` : '–'), backgroundColor: token('surface-overlay'), borderColor: token('border'), textStyle: {color: token('text')}},
        xAxis: {type: 'category', data: ordered.map((r) => r.name), ...axisStyle()},
        yAxis: {type: 'value', min: 0, max: 100, axisLabel: {color: token('text-muted'), fontSize: 11, formatter: '{value}%'}, splitLine: {lineStyle: {color: token('border')}}},
        series: [
            {
                name: t.rateTitle,
                type: 'line',
                symbol: 'circle',
                symbolSize: 7,
                connectNulls: false,
                lineStyle: {color: token('primary'), width: 2},
                itemStyle: {color: token('primary')},
                data: ordered.map((r) => r.passRate),
            },
        ],
    };
}

function EChart({option, label}: { option: EChartsCoreOption; label: string }) {
    const ref = useRef<HTMLDivElement>(null);
    useEffect(() => {
        const el = ref.current;
        if (!el) return undefined;
        let disposed = false;
        let chart: {dispose: () => void; resize: () => void} | null = null;
        const onResize = () => chart?.resize();
        void import('./echartsSetup').then(({echarts}) => {
            if (disposed) return;
            const instance = echarts.init(el, null, {renderer: 'svg'});
            instance.setOption(option);
            chart = instance;
            window.addEventListener('resize', onResize);
        });
        return () => {
            disposed = true;
            window.removeEventListener('resize', onResize);
            chart?.dispose();
        };
    }, [option]);
    return (
        <div role="img" aria-label={label} className="h-56 w-full">
            <div ref={ref} aria-hidden="true" className="size-full"/>
        </div>
    );
}

function ToggleLabel({parts}: { parts: {before: string; what: string; after: string} }) {
    return (
        <>
            {`${parts.before} ${parts.after}`}
            <span className="sr-only">{`: ${parts.what}`}</span>
        </>
    );
}

/** D15: a stacked bar of counts and a pass-rate line for the runs on this page, each with a table alternative. */
export function RunCharts({runs}: { runs: readonly ChartRun[] }) {
    const theme = useThemeVersion();
    const [countsAsTable, setCountsAsTable] = useState(false);
    const [rateAsTable, setRateAsTable] = useState(false);
    // Rebuild the options (and the token colours) when the data or the theme changes.
    const counts = countsOption(runs);
    const rate = rateOption(runs);
    void theme;
    return (
        <div className="grid gap-4 lg:grid-cols-2">
            <Card
                title={t.countsTitle}
                actions={
                    <Button variant="ghost" size="sm" onPress={() => setCountsAsTable((v) => !v)}>
                        {countsAsTable ? <BarChart3 aria-hidden="true" size={16}/> : <Table2 aria-hidden="true" size={16}/>}
                        <ToggleLabel parts={countsAsTable ? t.showChart(t.countsTitle.toLowerCase()) : t.showTable(t.countsTitle.toLowerCase())}/>
                    </Button>
                }
            >
                {countsAsTable ? (
                    <Table
                        label={t.countsTitle}
                        columns={[{id: 'run', label: t.colRun, isRowHeader: true}, ...STATUSES.map((s) => ({id: s, label: uiStrings.status[s] ?? s, numeric: true}))]}
                        rows={countsTable(runs).map((row, i) => ({
                            id: `${row.name}-${i}`,
                            cells: Object.fromEntries([['run', row.name], ...STATUSES.map((s, j) => [s, row.values[j] ?? 0])]),
                        }))}
                    />
                ) : (
                    <EChart key={`counts-${theme}`} option={counts} label={t.countsChartLabel(runs.length)}/>
                )}
            </Card>
            <Card
                title={t.rateTitle}
                actions={
                    <Button variant="ghost" size="sm" onPress={() => setRateAsTable((v) => !v)}>
                        {rateAsTable ? <BarChart3 aria-hidden="true" size={16}/> : <Table2 aria-hidden="true" size={16}/>}
                        <ToggleLabel parts={rateAsTable ? t.showChart(t.rateTitle.toLowerCase()) : t.showTable(t.rateTitle.toLowerCase())}/>
                    </Button>
                }
            >
                {rateAsTable ? (
                    <Table
                        label={t.rateTitle}
                        columns={[
                            {id: 'run', label: t.colRun, isRowHeader: true},
                            {id: 'rate', label: t.colPassRate, numeric: true},
                        ]}
                        rows={passRateTable(runs).map((row, i) => ({id: `${row.name}-${i}`, cells: {run: row.name, rate: row.rate}}))}
                    />
                ) : (
                    <EChart key={`rate-${theme}`} option={rate} label={t.rateChartLabel(runs.length)}/>
                )}
            </Card>
        </div>
    );
}
