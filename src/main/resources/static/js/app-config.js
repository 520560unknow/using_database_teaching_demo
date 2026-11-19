/*
 * 前端统一配置入口：集中处理接口地址、WebSocket 协议与身份读取，
 * 页面里不要再手写主机、端口、ws:// 或默认 ID。
 * 可在页面中先定义 window.APP_CONFIG 覆盖默认值。
 */
(function () {
    var conf = window.APP_CONFIG || {};
    var params = new URLSearchParams(location.search);

    function readStored(key, field) {
        try {
            var raw = localStorage.getItem(key);
            if (!raw) {
                return '';
            }
            var obj = JSON.parse(raw);
            return obj && obj[field] ? String(obj[field]) : '';
        } catch (e) {
            return '';
        }
    }

    window.AppConfig = {
        apiBase: conf.apiBase || '',

        /** WebSocket 地址：自动跟随当前页面的协议与主机 */
        ws: function (path) {
            var proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
            return proto + '//' + location.host + path;
        },

        /** HTTP 接口地址：默认同源，部署到别处只需设置 apiBase */
        api: function (path) {
            return (this.apiBase || '') + path;
        },

        param: function (name) {
            return params.get(name) || '';
        },

        classroomId: function () {
            return this.param('classroomId')
                || readStored('classroomInfo', 'classroomId')
                || readStored('studentInfo', 'classroomId')
                || conf.classroomId || '';
        },

        teacherId: function () {
            return this.param('teacherId')
                || readStored('teacherInfo', 'teacherId')
                || conf.teacherId || '';
        },

        studentId: function () {
            return this.param('studentId')
                || readStored('studentInfo', 'studentId')
                || readStored('studentInfo', 'id')
                || conf.studentId || '';
        }
    };
})();
