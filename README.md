# PrintBridge 打印桥 🖨️

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/)
[![Jetty](https://img.shields.io/badge/Jetty-9.4-green.svg)](https://www.eclipse.org/jetty/)

[![GitHub stars](https://img.shields.io/github/stars/magicyuan876/PrintBridge?style=social)](https://github.com/magicyuan876/PrintBridge/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/magicyuan876/PrintBridge?style=social)](https://github.com/magicyuan876/PrintBridge/network/members)
[![Gitee stars](https://gitee.com/magic_yuan_admin/PrintBridge/badge/star.svg?theme=dark)](https://gitee.com/magic_yuan_admin/PrintBridge/stargazers)
[![Gitee forks](https://gitee.com/magic_yuan_admin/PrintBridge/badge/fork.svg?theme=dark)](https://gitee.com/magic_yuan_admin/PrintBridge/members)

> **连接Web与打印机的智能桥梁** - 一个企业级Web打印中间件,让浏览器应用轻松调用本地打印服务。

基于Java Swing + Jetty构建,为Web应用提供高性能、跨浏览器的本地打印解决方案。

简体中文 | [English](./README_EN.md)

---

## ⭐ 给个Star吧!

**如果PrintBridge对您有帮助,请给个Star支持一下!** ⭐

您的支持是我们持续改进PrintBridge、交付更多功能的最大动力。一个Star不仅帮助其他开发者发现这个项目,更能让整个社区受益!

**快速点Star:** 点击页面右上角的 ⭐ 按钮 → 只需2秒钟!

---

## 为什么选择PrintBridge? 💭

PrintBridge诞生于真实业务场景中的痛点:当Web应用需要调用本地打印机时,传统方案往往复杂、不稳定。PrintBridge提供了一个轻量、可靠的解决方案。

### 项目演进
- **2020**: v1.0 基于Socket的原型系统
- **2025**: v2.0 企业级重构
  - 🔄 从原始Socket实现升级到企业级Jetty服务器
  - 📊 集成SLF4J + Logback日志系统
  - 🎨 使用FlatLaf现代化界面主题
  - 🧹 遵循现代Java开发规范

## 核心特性 🚀

- 🌐 **即插即用**: 一行API调用,任何浏览器都能打印
- 📄 **多格式支持**: PDF、Word、Excel、PPT、图片等格式
- ⚡ **高性能架构**: Jetty服务器 + 多线程队列,轻松应对高并发
- 🔄 **智能转换**: 基于JODConverter + LibreOffice,完美还原格式
- 🎯 **智能队列**: 可视化任务管理,批量打印不混乱
- 🎨 **极简界面**: FlatLaf现代主题,操作直观不费力
- 📝 **生产级日志**: SLF4J + Logback,问题排查有据可依
- 🔒 **安全可控**: 本地服务,数据不出本机

## 快速开始 ⚡

### 前置条件
- ☕ Java 8+ 运行环境
- 🖨️ 至少一台可用的打印机
- 📦 LibreOffice (可选,用于支持Office格式)

### 启动服务

#### 构建独立运行包（只需构建时安装 Maven）
```bash
# 在项目目录构建包含依赖的可执行 JAR
cd PrintBridge
mvn clean package
```

构建完成后，将 `target/printbridge-2.1.3.jar` 复制到目标电脑。目标电脑只需安装 Java 8 或更高版本，无需安装 Maven 或 LibreOffice（仅打印 Office 文档时才需要 LibreOffice）。

#### 运行
```bash
java -Dfile.encoding=UTF-8 -jar printbridge-2.1.3.jar
```

Windows 命令提示符如需正确显示中文控制台日志，可先执行 `chcp 65001`，再运行上面的命令。也可以将命令保存为批处理文件，便于用户双击启动。

在 Windows 上也可以双击 [Start-PrintBridge.bat](./Start-PrintBridge.bat) 手动启动。将 JAR 放在批处理文件旁，或从项目目录运行（JAR 位于 `target` 子目录时也可自动找到）。PrintBridge 退出后，批处理窗口会保留，按任意键关闭。

#### Windows 登录时自动启动
将 `printbridge-2.1.3.jar` 与 `Register-PrintBridgeStartup.ps1`、`Unregister-PrintBridgeStartup.ps1` 放在同一目录。也可以直接在项目目录运行脚本，此时脚本会查找 `target/printbridge-2.1.3.jar`。

以当前 Windows 用户打开 PowerShell，执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Register-PrintBridgeStartup.ps1
```

脚本会在当前用户的任务计划程序中创建 `PrintBridge-AtLogOn` 任务；用户下次登录时 PrintBridge 会在该用户会话中后台启动。立即启动 PrintBridge 时，可在任务计划程序中运行该任务，或手动执行 `javaw.exe -Dfile.encoding=UTF-8 -jar .\printbridge-2.1.3.jar`。

取消自动启动：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Unregister-PrintBridgeStartup.ps1
```

取消任务不会关闭当前已运行的 PrintBridge。任务按当前用户注册，不需要管理员权限；用户必须登录，后台程序及默认打印机才可用。

### 验证安装
启动后访问 `http://localhost:8281/health` ,看到 `{"status":"ok"}` 即表示服务正常运行。

### 主界面与后台运行
- 启动时不再自动弹出 LibreOffice 安装提示；点击打印队列右上角的“安装多格式支持”按钮可打开安装指引。
- 主界面打印队列右侧显示当前支持的格式；安装 LibreOffice 后会显示 Office 格式。
- 点击主窗口关闭按钮会将程序隐藏到系统托盘，不会退出服务。需要退出时，在托盘图标菜单中选择“退出”。

### LibreOffice配置(可选)

如需打印Word/Excel/PPT等Office格式,请安装LibreOffice:

**Windows:**
1. 访问 https://www.libreoffice.org/download/download/
2. 下载并安装
3. 重启PrintBridge服务

**Linux:**
```bash
# Ubuntu/Debian
sudo apt-get install libreoffice

# CentOS/RHEL
sudo yum install libreoffice
```

**macOS:**
```bash
brew install --cask libreoffice
```

启动时日志会显示:
```
✓ Office格式: Word, Excel, PowerPoint
  LibreOffice已检测到,多格式支持已启用
```

## 使用指南 📖

### API接口

#### 打印文档
**POST** `http://localhost:8281/print`

**请求参数:**
```json
[
  {
    "fileName": "文档名称",           // 文档名称
    "fileUrl": "http://example.com/file.xxx",  // 文件URL
    "landscape": false               // false=竖向, true=横向
  }
]
```

**支持的文件格式:**
- PDF: `http://example.com/document.pdf`
- Word: `http://example.com/report.docx`
- Excel: `http://example.com/table.xlsx`
- PowerPoint: `http://example.com/slides.pptx`
- 图片: `http://example.com/image.png`

**响应示例:**
```json
{
  "code": 200,
  "message": "打印任务已加入队列"
}
```

#### 上传 PDF 并直接打印
浏览器生成的 PDF Blob 不能通过 `fileUrl` 交给本机进程。本接口接收 PDF 二进制数据并静默提交打印任务：

**POST** `http://127.0.0.1:8281/print/pdf?fileName=打印报表.pdf&landscape=false`

- 请求头：`Content-Type: application/pdf`
- 请求体：PDF 文件二进制，最大 50 MB
- 响应：`{"success":true,"message":"PDF print job submitted",...}`

XMReport 的 `report.js` 提供 `webReport.printLocal(options)` 调用此接口：

```javascript
webReport.printLocal({
  templateId: "报表模板ID",
  previewData: [{ name: "示例" }],
  title: "销售报表",
  landscape: false
}).then(console.log).catch(console.error);
```

默认打印地址为 `http://127.0.0.1:8281/print/pdf`。更换本地打印方案时，可通过 `options.url` 传入其他地址。调用仍使用 `POST`、`Content-Type: application/pdf`，并在 URL 查询参数中附带 `fileName` 和 `landscape`。

此方法不会打开浏览器预览页或打印对话框；PrintBridge 会将任务发送到系统默认打印机。

#### 安全配置
PrintBridge HTTP 服务仅绑定到本机 `127.0.0.1`，并验证浏览器 `Origin`。默认允许 `https://a.siksoft.com` 以及本地开发源。部署到其他网页来源时，在启动 PrintBridge 前设置 `PRINTBRIDGE_ALLOWED_ORIGINS`，值为逗号分隔的精确来源（包含 scheme 和端口，不含路径），例如：

```powershell
$env:PRINTBRIDGE_ALLOWED_ORIGINS = "https://reports.example.com"
```

不要使用 `*`；静默打印接口会直接调用本机默认打印机。

### 前端集成示例

#### 原生JavaScript
```javascript
function printDocument(fileUrl, fileName = 'document.pdf') {
    fetch('http://localhost:8281/print', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify([{
            fileName: fileName,
            fileUrl: fileUrl,
            landscape: false  // 横向打印设为true
        }])
    })
    .then(res => res.json())
    .then(data => console.log('✓ 打印任务已提交:', data))
    .catch(err => console.error('✗ 打印失败:', err));
}

// 打印PDF
printDocument('https://example.com/invoice.pdf', '发票.pdf');

// 打印Word文档
printDocument('https://example.com/report.docx', '报告.docx');

// 打印Excel表格
printDocument('https://example.com/data.xlsx', '数据.xlsx');

// 打印图片
printDocument('https://example.com/photo.png', '照片.png');
```

#### jQuery方式
```javascript
$.ajax({
    url: 'http://localhost:8281/print',
    type: 'POST',
    contentType: 'application/json',
    data: JSON.stringify([{
        fileName: '合同.pdf',
        fileUrl: 'https://example.com/contract.pdf',
        landscape: false
    }]),
    success: (res) => alert('打印任务已提交'),
    error: (err) => alert('打印失败: ' + err.statusText)
});
```

#### React/Vue 示例
```javascript
// React组件中
const handlePrint = async (pdfUrl) => {
    try {
        const response = await fetch('http://localhost:8281/print', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify([{
                fileName: '报表.pdf',
                fileUrl: pdfUrl,
                landscape: false
            }])
        });
        const result = await response.json();
        console.log('打印成功', result);
    } catch (error) {
        console.error('打印失败', error);
    }
}
```

## 配置说明 ⚙️

### 默认配置
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 监听端口 | 8281 | HTTP服务端口 |
| 工作线程 | 10-100 | 动态线程池 |
| 支持格式 | PDF | 当前版本支持 |
| 日志目录 | `logs/` | 日志文件位置 |
| 日志保留 | 30天 | 自动清理旧日志 |

### 自定义配置
修改 `src/main/resources/logback.xml` 可调整日志级别和输出策略。

## 常见问题 💡

<details>
<summary><b>Q: 服务启动失败怎么办?</b></summary>

**解决步骤:**
1. 检查8281端口是否被占用: `netstat -ano | findstr 8281`
2. 查看错误日志: `logs/print-server-error.log`
3. 确认Java版本 >= 8: `java -version`
</details>

<details>
<summary><b>Q: 打印任务没有反应?</b></summary>

**排查清单:**
- ✓ PDF文件URL是否可访问(浏览器测试)
- ✓ 打印机是否在线且驱动正常
- ✓ 查看界面打印队列状态
- ✓ 检查日志文件 `logs/print-server.log`
</details>

<details>
<summary><b>Q: 浏览器提示跨域错误?</b></summary>

**解决方案:**
- PrintBridge已内置CORS支持,无需额外配置
- 确认服务已启动: 访问 `http://localhost:8281/health`
- 如在HTTPS页面调用,需配置SSL证书或使用代理
</details>

<details>
<summary><b>Q: 支持哪些文件格式?</b></summary>

**完全支持:**
- ✅ PDF文档

**图片格式**(自动转换为PDF):
- ✅ PNG, JPG, JPEG, GIF, BMP

**Office格式**(需要安装LibreOffice):
- ✅ Word (.docx, .doc)
- ✅ Excel (.xlsx, .xls)
- ✅ PowerPoint (.pptx, .ppt)

如未安装LibreOffice,Office格式将无法打印。
</details>

<details>
<summary><b>Q: 如何启用Office格式支持?</b></summary>

**步骤:**
1. 下载并安装 [LibreOffice](https://www.libreoffice.org/download/download/)
2. 重启PrintBridge服务
3. 启动时会自动检测LibreOffice并启用Office格式支持

**验证**: 查看启动日志,会显示"LibreOffice已检测到,多格式支持已启用"
</details>

## 架构设计 🏗️

```
PrintBridge
├── controller/         # API接口层
├── service/           # 核心业务逻辑
│   ├── JettyPrintService     # HTTP服务
│   ├── PrintService          # 打印引擎
│   └── ServerStatusService   # 状态管理
├── ui/                # Swing界面
├── model/             # 数据模型
└── config/            # 配置中心
```

**技术栈:**
- 🚀 Web服务: Jetty 9.4
- 🖨️ 打印引擎: Apache PDFBox 2.0
- 🔄 格式转换: JODConverter 4.4 + LibreOffice
- 🎨 界面框架: Swing + FlatLaf
- 📝 日志系统: SLF4J + Logback
- 📦 依赖管理: Maven

## 版本历史 📋

| 版本 | 日期      | 更新内容 |
|------|---------|----------|
| **v2.1.0** | 2025-10 | 📄 多格式支持: Word/Excel/PPT/图片 + JODConverter集成 |
| **v2.0.0** | 2025-01 | 🚀 企业级重构: Jetty服务器 + SLF4J日志 + FlatLaf主题 |
| **v1.0.0** | 2020-08 | 🎉 初始版本: Socket HTTP服务器 + PDF打印 |

## 产品路线图 🗺️

### ✅ v2.1 (当前版本)
- [x] 📄 **多格式支持**: Word、Excel、PPT、图片等格式
- [x] 🔄 **JODConverter集成**: 基于LibreOffice的格式转换
- [x] 🖼️ **图片打印**: PNG、JPG、GIF、BMP自动转PDF

### 🎯 v2.2 (计划中)
- [ ] 🖨️ **打印预设**: 保存常用打印配置
- [ ] 📋 **打印历史**: 查看和重新打印历史任务
- [ ] 📊 **打印统计**: 任务数量、成功率等统计

### 🚀 v3.0 (规划中)
- [ ] 🎨 **水印功能**: 文字/图片水印添加
- [ ] ⚙️ **高级设置**: 双面打印、页面范围、份数控制
- [ ] 🖥️ **网络共享**: 局域网内多台电脑共享使用

### 🌟 长期愿景
- [ ] 🔍 **打印预览**: 打印前本地预览
- [ ] ☁️ **云存储集成**: 支持OSS/S3等云存储直接打印
- [ ] 🔐 **安全加固**: IP白名单、API Token认证

> 💡 有好的想法?欢迎提Issue或加入讨论!

## 参与贡献 🤝

欢迎各种形式的贡献!无论是新功能、Bug修复还是文档改进。

### 贡献流程
1. 🍴 Fork本项目
2. 🌿 创建特性分支 `git checkout -b feature/AmazingFeature`
3. 💾 提交更改 `git commit -m 'Add some AmazingFeature'`
4. 📤 推送到分支 `git push origin feature/AmazingFeature`
5. 🎉 提交Pull Request

### 开发规范
- 遵循现有代码风格
- 添加必要的单元测试
- 更新相关文档
- 提交前运行 `mvn clean test`

## 开源协议 📄

本项目基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 开源协议发布。

## 社区与支持 💬

- 🐛 [报告Bug](https://github.com/magicyuan876/PrintBridge/issues)
- 💡 [功能建议](https://github.com/magicyuan876/PrintBridge/issues)
- 💬 [加入讨论](https://github.com/magicyuan876/PrintBridge/discussions)

## 致谢 🙏

感谢所有使用和贡献PrintBridge的开发者!

**核心依赖:**
- [Apache PDFBox](https://pdfbox.apache.org/) - PDF处理引擎
- [JODConverter](https://github.com/jodconverter/jodconverter) - 文档格式转换
- [LibreOffice](https://www.libreoffice.org/) - Office文档渲染
- [Eclipse Jetty](https://www.eclipse.org/jetty/) - HTTP服务器
- [FlatLaf](https://www.formdev.com/flatlaf/) - 现代UI主题

---

<div align="center">

**⭐ 如果PrintBridge对您有帮助,请给个Star支持! ⭐**

您的Star是我们最大的动力! 只需2秒钟,却对我们意义重大!

Made with ❤️ by developers, for developers

[⭐ GitHub上Star](https://github.com/magicyuan876/PrintBridge) · [⭐ Gitee上Star](https://gitee.com/magic_yuan_admin/PrintBridge) · [提交Issue](https://github.com/magicyuan876/PrintBridge/issues) · [参与讨论](https://github.com/magicyuan876/PrintBridge/discussions)

</div>
