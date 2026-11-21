// Tree-shaken echarts: bar and line charts with the SVG renderer (crisp, and testable in jsdom).
import {BarChart, LineChart} from 'echarts/charts';
import {GridComponent, LegendComponent, TooltipComponent} from 'echarts/components';
import * as echarts from 'echarts/core';
import {SVGRenderer} from 'echarts/renderers';

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, SVGRenderer]);

export {echarts};
