# 课堂互动系统

## 项目介绍

课堂互动系统是一个基于Spring Boot的在线教学平台，集成了视频播放、弹幕互动、金山文档PPT播放等功能，为教师和学生提供了一个高效、互动的在线教学环境。

## 核心功能

### 1. 课堂管理
- 教师可以创建和管理多个课堂
- 支持设置课堂名称、描述等基本信息
- 课堂创建时可配置视频树类型和相关参数

### 2. 视频树系统
- 支持上传和管理视频树结构（JSON格式）
- 提供视频树编辑器，方便教师组织教学内容
- 支持视频自动跳转功能，优化教学流程

### 3. 实时互动
- 基于WebSocket的实时弹幕系统
- 师生实时评论和消息交流
- 支持学生向教师发送实时消息

### 4. 金山文档PPT集成
- 基于金山文档的在线PPT播放系统
- 支持输入金山文档PPT分享链接直接播放
- 提供教师和学生端的PPT交互界面

### 5. 智能推荐
- 集成智能推荐服务，为学生提供个性化学习内容
- 基于学习行为和偏好的智能分析

## 技术栈

### 后端技术
- Spring Boot 2.7.10
- Java 8
- Spring WebSocket
- Spring Data JPA
- H2内存数据库（开发环境）

### 前端技术
- Thymeleaf模板引擎
- Bootstrap 5.1.3
- jQuery 3.6.0
- Font Awesome 6.4.0
- WebSocket客户端（SockJS + STOMP）

## 项目结构

```
demo/
├── src/
│   ├── main/
│   │   ├── java/com/example/
│   │   │   ├── classroom/          # 课堂相关功能
│   │   │   │   ├── controller/     # 控制器
│   │   │   │   ├── model/          # 数据模型
│   │   │   │   └── service/        # 业务服务
│   │   │   ├── officepptsystem/    # 金山文档PPT系统
│   │   │   ├── websocket/          # WebSocket实现
│   │   │   └── DemoApplication.java # 应用主类
│   │   └── resources/
│   │       ├── templates/          # 前端模板
│   │       │   ├── kdocs/          # 金山文档相关页面
│   │       │   ├── student/        # 学生端页面
│   │       │   ├── teacher/        # 教师端页面
│   │       │   └── video-tree/     # 视频树编辑器页面
│   │       └── application.yml     # 应用配置
│   └── test/                       # 测试代码
├── video-uploads/                  # 视频上传目录
├── json-storage/                   # JSON文件存储目录
├── logs/                           # 日志目录
├── pom.xml                         # Maven依赖配置
└── README.md                       # 项目说明文档
```

## 快速开始

### 环境要求
- JDK 8 或更高版本
- Maven 3.6.0 或更高版本

### 安装和运行

1. **克隆项目**
   ```bash
   git clone <项目地址>
   cd demo
   ```

2. **构建项目**
   ```bash
   mvn clean package
   ```

3. **运行项目**
   ```bash
   mvn spring-boot:run
   ```
   或
   ```bash
   java -jar target/firestDemo-0.0.1-SNAPSHOT.jar
   ```

4. **访问系统**
   - 教师端：`http://localhost:8080/teacher/dashboard`
   - 学生端：`http://localhost:8080/student/dashboard`
   - 金山文档PPT系统：`http://localhost:8080/kdocs/dashboard`

## 使用指南

### 教师操作指南

1. **创建课堂**
   - 登录教师仪表板
   - 点击"创建课堂"按钮
   - 填写课堂信息，选择视频树类型
   - 配置弹幕、评论等功能选项
   - 点击"创建"按钮完成课堂创建

2. **管理视频树**
   - 在教师仪表板中选择"视频树管理"
   - 可以上传JSON格式的视频树文件
   - 或使用视频树编辑器创建和编辑视频树

3. **使用金山文档PPT**
   - 在教师仪表板中选择"金山文档PPT"
   - 输入金山文档PPT的分享链接
   - 点击"开始播放"按钮进入播放模式

4. **课堂互动**
   - 在课堂页面中，教师可以查看学生发送的弹幕和评论
   - 可以通过WebSocket实时与学生交流
   - 可以控制视频播放和跳转

### 学生操作指南

1. **加入课堂**
   - 登录学生仪表板
   - 输入课堂ID或通过邀请链接加入课堂
   - 点击"加入课堂"按钮进入课堂

2. **课堂参与**
   - 在课堂页面中，可以发送弹幕和评论
   - 可以观看教师播放的视频和PPT
   - 可以向教师发送实时消息

3. **视频学习**
   - 跟随教师的视频讲解进行学习
   - 可以在视频播放过程中发送弹幕提问
   - 视频结束后可以查看相关评论和讨论

## 配置说明

### 应用配置

应用配置文件位于 `src/main/resources/application.yml`，主要配置项包括：

- 服务器端口
- 数据库配置
- Thymeleaf模板引擎配置
- WebSocket配置

### 环境变量

| 变量名 | 描述 | 默认值 |
|-------|------|-------|
| SPRING_PROFILES_ACTIVE | 激活的配置文件 | default |
| SERVER_PORT | 服务器端口 | 8080 |
| SPRING_DATASOURCE_URL | 数据库连接URL | jdbc:h2:mem:testdb |

## 开发指南

### 目录结构

- `controller/`：处理HTTP请求，实现RESTful API
- `model/`：定义数据模型和实体类
- `service/`：实现业务逻辑
- `websocket/`：实现WebSocket通信
- `templates/`：前端HTML模板

### 核心API

#### 课堂管理API
- `POST /api/classrooms` - 创建新课堂
- `GET /api/classrooms/{id}` - 获取课堂详情
- `PUT /api/classrooms/{id}` - 更新课堂信息
- `DELETE /api/classrooms/{id}` - 删除课堂

#### 视频树API
- `POST /api/video-trees` - 创建视频树
- `GET /api/video-trees/{id}` - 获取视频树详情
- `PUT /api/video-trees/{id}` - 更新视频树
- `DELETE /api/video-trees/{id}` - 删除视频树

#### 弹幕API
- `POST /api/danmaku` - 发送弹幕
- `GET /api/danmaku/{classroomId}` - 获取课堂弹幕

### WebSocket端点

- `/ws/danmu` - 弹幕WebSocket端点
- `/ws/student` - 学生WebSocket端点
- `/ws/teacher` - 教师WebSocket端点
- `/ws/video-upload` - 视频上传WebSocket端点

## 故障排查

### 常见问题

1. **WebSocket连接失败**
   - 检查浏览器是否支持WebSocket
   - 检查服务器WebSocket配置是否正确
   - 检查网络连接是否正常

2. **视频树上传失败**
   - 检查JSON文件格式是否正确
   - 确保JSON文件包含必要的`elements`数组
   - 检查文件大小是否超过限制

3. **金山文档PPT无法播放**
   - 确保输入的是正确的金山文档PPT分享链接
   - 检查网络连接是否正常
   - 确保分享链接权限设置为"任何人可查看"

### 日志查看

系统日志位于 `logs/` 目录下，可通过查看日志文件排查问题：

```bash
# 查看最新日志
tail -f logs/classroom-system.log
```

## 版本历史

### v1.0.0 (2026-02-22)
- 初始版本发布
- 实现基本课堂管理功能
- 集成视频树系统
- 实现WebSocket实时通信
- 集成金山文档PPT系统

## 贡献指南

欢迎各位开发者贡献代码和提出建议！

1. **提交Issue** - 报告bug或提出新功能建议
2. **提交Pull Request** - 贡献代码修复或新功能实现
3. **代码规范** - 遵循项目现有的代码风格和命名规范
4. **测试** - 确保代码修改后通过所有测试

## 许可证

本项目采用 MIT 许可证，详见 LICENSE 文件。

## 联系方式

- 项目维护者：<维护者邮箱>
- 项目地址：<项目GitHub地址>
- 问题反馈：<项目Issue地址>

---

**感谢使用课堂互动系统！** 希望本系统能够为您的教学工作带来便利和效率。