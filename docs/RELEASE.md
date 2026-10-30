# 发布与版本命名

## 命名约定

| 产物 | 文件名 |
| --- | --- |
| Debug APK | `Count-1.0.0-debug.apk` |
| Signed release APK | `Count-1.0.0.apk` |
| Unsigned release APK | `Count-1.0.0-unsigned.apk` |
| Google Play 上传 | `Count-1.0.0.aab`（构建产物默认名，上传时不看文件名） |

规则：

- 文件名 = `Count-<versionName>.apk`，`versionName` 使用语义版本，并与 Git tag `v1.0.0` 对齐。
- `versionCode` 单调递增；文件名不必包含它，Play/GitHub 以 `versionCode` 判断升级。
- 不使用空格、中文、`final`、`latest` 等无法区分版本的名称。
- Debug 与 release 分开命名；不要把 debug 包改名后当作正式版发布。
- 每次公开发布同时提供 `SHA256SUMS`，例如：
  ```
  D3EED3403A79D94AF66710A90BB9A5DF825531D3DEB68E3616AAB262EF986998  Count-1.0.0.apk
  ```

## 生成本地 release keystore

签名密钥只保存在本机，**不要提交到 Git**。首次发布前生成一次：

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/count-release.jks \
  -alias count \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12 \
  -dname "CN=Count, OU=Open Source, O=Count, C=CN"
```

复制配置模板并填入真实密码：

```bash
cp keystore.properties.example keystore.properties
```

`keystore.properties` 格式：

```properties
storeFile=keystore/count-release.jks
storePassword=你的密码
keyAlias=count
keyPassword=你的密码
```

`keystore/`、`keystore.properties`、`*.jks`、`*.keystore` 都已在 `.gitignore` 中。

> **务必备份 keystore 和密码**。丢失后无法再发布能覆盖安装的更新；泄露则任何人都能冒充你签名。不要把它放进仓库、网盘公开链接或 CI 日志。

## 构建

```bash
./gradlew :app:assembleDebug     # Count-1.0.0-debug.apk
./gradlew :app:assembleRelease   # Count-1.0.0.apk（有 keystore.properties 时）
./gradlew :app:testDebugUnitTest
```

输出位置：

```
app/build/outputs/apk/debug/Count-1.0.0-debug.apk
app/build/outputs/apk/release/Count-1.0.0.apk
```

没有 `keystore.properties` 时，release 仍可构建，但会命名为 `Count-1.0.0-unsigned.apk`，不能直接分发。

## 校验

```bash
apksigner verify --print-certs app/build/outputs/apk/release/Count-1.0.0.apk
sha256sum app/build/outputs/apk/release/Count-1.0.0.apk
```

应看到 `CN=Count` 的签名证书；发布页附上 SHA-256。

## Google Play

Play 只接受 AAB：

```bash
./gradlew :app:bundleRelease
# app/build/outputs/bundle/release/app-release.aab
```

上传文件名不影响 Play；正式上架建议启用 Play App Signing，并把上面的 keystore 作为 upload key 备份。

## 发布检查清单

1. 修改 `app/build.gradle.kts` 中的 `appVersionName` / `appVersionCode`。
2. 运行 `:app:testDebugUnitTest` 和真机冒烟测试。
3. 构建 release，`apksigner verify` 通过。
4. 生成 SHA-256，打 Git tag（如 `v1.0.0`）。
5. 在 GitHub Release 附上 `Count-1.0.0.apk` 与 `SHA256SUMS`；Play 上传 AAB。
6. 确认 keystore 与 `keystore.properties` 没有被提交。
