/* 页面通用 UI 工具：转义、通知、JSON 请求。各模板通过 UiCommon.* 调用，避免逐页复制。 */
(function () {
    'use strict';

    var ESCAPE_MAP = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

    function esc(value) {
        return String(value == null ? '' : value).replace(/[&<>"']/g, function (ch) {
            return ESCAPE_MAP[ch];
        });
    }

    function iconFor(type) {
        if (type === 'success') return 'fa-check-circle';
        if (type === 'error') return 'fa-exclamation-circle';
        if (type === 'warning') return 'fa-exclamation-triangle';
        return 'fa-info-circle';
    }

    /** 右上角卡片通知，3 秒后滑出。title 可省略。 */
    function notify(title, message, type) {
        type = type || 'info';
        var box = document.createElement('div');
        box.className = 'notification notification-' + type;
        box.innerHTML = '<i class="fas ' + iconFor(type) + '"></i>'
            + '<div>'
            + (title ? '<strong>' + esc(title) + '</strong>' : '')
            + '<p style="margin: 0; font-size: 14px;">' + esc(message) + '</p>'
            + '</div>';

        document.body.appendChild(box);
        setTimeout(function () {
            box.style.animation = 'slideInRight 0.5s ease-out reverse';
            setTimeout(function () {
                box.remove();
            }, 500);
        }, 3000);
    }

    /** cyber 风格通知：同一时刻只保留一条。 */
    function cyberNotify(message, type) {
        type = type || 'info';
        document.querySelectorAll('.cyber-notification').forEach(function (item) {
            item.remove();
        });

        var icon = type === 'success' ? 'check-circle'
            : type === 'error' ? 'exclamation-circle'
            : 'exclamation-triangle';

        var box = document.createElement('div');
        box.className = 'cyber-notification cyber-notification-' + type;
        box.innerHTML = '<i class="fas fa-' + icon + '"></i><span>' + esc(message) + '</span>';
        document.body.appendChild(box);
        box.classList.add('cyber-notification-show');

        setTimeout(function () {
            box.style.animation = 'cyber-slide-out 0.5s ease-out forwards';
            setTimeout(function () {
                if (box.parentNode) {
                    box.remove();
                }
            }, 500);
        }, 3000);
    }

    /** fetch + JSON 解析；非 2xx 抛出异常。 */
    async function fetchJson(url, options) {
        var response = await fetch(url, options);
        if (!response.ok) {
            throw new Error('HTTP ' + response.status);
        }
        return response.json();
    }

    window.UiCommon = {
        esc: esc,
        notify: notify,
        cyberNotify: cyberNotify,
        fetchJson: fetchJson
    };
})();
