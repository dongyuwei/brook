# Brook

A cross-platform programmable network tool.

## Android VPN 客户端（本仓库新增）

基于本仓库 brook Go 核心（`NewBrookLink` → 本地 SOCKS5 → 全协议隧道）实现的 Android VPN 客户端，源码位于 [`android/`](android/)：

- **纯文本 brook link 导入**：粘贴 / 浏览器打开 `brook://` 链接 / 系统分享，参数（`udpovertcp` 等）原样透传
- **白名单模式**：仅勾选的应用走代理，其余一律直连（`VpnService.addAllowedApplication`），App 自身永不进隧道、无路由回环
- **DNS 远端解析**：VPN 下发公网 DNS，域名由 brook 服务端在远端解析，本地不泄露
- 支持全部隧道协议：server / wsserver / wssserver / quicserver；已在模拟器与小米 10 Pro（Android 13）真机验证

### 构建

```bash
cd android
./build-aar.sh                                # gomobile 产出 app/libs/brook.aar（需 Go ≥ 1.26 + NDK，旧版 Go 会经 GOTOOLCHAIN=auto 自动下载工具链）
./gradlew :app:assembleDebug                  # 通用包（约 112MB，含 4 个 ABI）
./gradlew :app:assembleDebug -Pabi=arm64-v8a  # 单 ABI 瘦身包（约 37MB）
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### CI 自动发布

推送到 GitHub 后，`.github/workflows/android.yml` 自动工作：

- **push master** → 自动构建并发布滚动 **pre-release**（tag `pre-release`，含 universal + arm64 两个 APK 和 SHA256 校验文件，每次覆盖更新）
- **push tag `v*`**（如 `v1.0.0`）→ 构建并发布正式 **Release**，自动生成 release notes
- 修改 `android/**` 的 PR → 仅构建验证，产物在 Actions 页面下载

> 正式签名（可选）：在仓库 Secrets 配置 `ANDROID_KEYSTORE_BASE64`（keystore 的 base64）、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。未配置时 APK 用 debug 证书签名（可安装，但升级正式版前需卸载）。

### adb reverse 本机联调技巧（无需真实服务器）

在电脑上起一个本地 brook server，用 `adb reverse` 把手机端口经 USB 反向映射到电脑，手机上用 `127.0.0.1` 就能连到电脑的服务器，随时测试：

```bash
# 电脑上
brook server -l 127.0.0.1:9999 -p testpass   # 或: go run ./cli/brook server -l 127.0.0.1:9999 -p testpass
adb reverse tcp:9999 tcp:9999                # 手机 127.0.0.1:9999 -> 电脑 127.0.0.1:9999
```

然后在 App 中导入 link 并连接：

```
brook://server?password=testpass&server=127.0.0.1:9999
```

> 架构图、MIUI 后台保活设置与更多注意事项见 [android/README.md](android/README.md)。

---

> ↓↓↓ **以下为 brook 原始 README** ↓↓↓

**Sponsor**: [Shiliew - Focuses on providing stable network services](https://www.txthinking.com/shiliew.html)

## Server

```
bash <(curl https://bash.ooo/nami.sh)
```

```
nami install brook
```

```
brook server -l :9999 -p hello
```

## Client

- [iOS](https://apps.apple.com/us/app/brook-network-tool/id1216002642)
- [Android](https://github.com/txthinking/brook/releases/latest/download/Brook.apk)
- [macOS](https://apps.apple.com/us/app/brook-network-tool/id1216002642)
	- [About App Mode on macOS](https://www.txthinking.com/talks/articles/macos-app-mode-en.article)
- [Windows](https://github.com/txthinking/brook/releases/latest/download/Brook.msix)
- [Linux](https://github.com/txthinking/brook/releases/latest/download/Brook.bin)
	- [How to install Brook on Linux](https://www.txthinking.com/talks/articles/linux-app-brook-en.article)
- [OpenWrt](https://www.txthinking.com/talks/articles/brook-openwrt-one-en.article)

> You may want to use `brook link` to customize some parameters

## Docs

https://www.txthinking.com/brook.html

## Brook Script Gallery

https://brook.app

