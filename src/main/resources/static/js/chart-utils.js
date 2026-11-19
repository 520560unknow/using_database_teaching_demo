/* Chart.js 公共默认项：响应式、图例置顶、y 轴从 0 开始。 */
(function () {
    'use strict';

    var DEFAULT_OPTIONS = {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                position: 'top'
            }
        },
        scales: {
            y: {
                beginAtZero: true
            }
        }
    };

    /**
     * @param {HTMLCanvasElement} canvas
     * @param {object} config Chart.js 配置（可不写 options，使用默认项）
     */
    function createChart(canvas, config) {
        if (!canvas || typeof Chart === 'undefined') {
            return null;
        }

        var options = Object.assign({}, DEFAULT_OPTIONS, config.options || {});
        if (!options.plugins) options.plugins = DEFAULT_OPTIONS.plugins;
        if (!options.scales) options.scales = DEFAULT_OPTIONS.scales;

        return new Chart(canvas, Object.assign({}, config, { options: options }));
    }

    window.ChartUtils = {
        createChart: createChart
    };
})();
