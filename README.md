# Paper 1.21.11 + VLESS / 隧道 / TG通知 / Komari（通用版）

> 这个仓库编译出来的 `paper-server.jar` 里已经内置了：
> VLESS over WebSocket 代理、反向隧道客户端、Telegram 通知、Komari 监控探针。
> 启动服务器后它们全部自动运行，不需要额外装插件。

---

## ⚙️ 配置在哪改

所有可调参数都集中在**一个文件**里，最上方的「配置」区块：

### 👉 [**点击这里直接编辑 `ProxyService.java`**](https://github.com/kuisa/java-ws-tunnel/edit/main/paper-server/src/main/java/io/papermc/paper/proxy/ProxyService.java)

也可以自己找路径：

```
paper-server/src/main/java/io/papermc/paper/proxy/ProxyService.java
```

打开后，第 38 行左右就是这一段：

```java
// ================= 配置（编译前自行修改） =================

static final String VLESS_UID  = "6021c57c-fdf0-44b4-b688-135568139d30";
static final int    VLESS_PORT = 65534;

// 公网隧道服务器（留空则不启动隧道客户端）
static final String TUNNEL_HOST         = "";
static final int    TUNNEL_PORT         = 0;
static final int    TUNNEL_PORT_INGRESS = 0;   // 仅用于 TG 通知里显示的入站端口
static final int    TUNNEL_LOCAL_PORT   = 65534;

// ---- Telegram 通知（可留空） ----
static final String TG_TOKEN   = "";
static final String TG_CHAT_ID = "";

// ---- Komari 监控探针（可留空，留空则不上报） ----
static final String KOMARI_HOST  = "";  // 例: "https://komari.example.com"
static final String KOMARI_TOKEN = "";  // Komari 后台节点详情页的 Client Token

// 服务器名称
static final String SERVER_NAME = "";
```

### 各参数说明

| 变量 | 说明 |
|---|---|
| `VLESS_UID` | 你的 VLESS UUID，客户端要和这里一致 |
| `VLESS_PORT` | 本地 VLESS over WebSocket 监听端口 |
| `TUNNEL_HOST` | 公网隧道服务器地址，**留空 `""` 就不启动隧道** |
| `TUNNEL_PORT` | 隧道服务器端口，留空时填 `0` |
| `TUNNEL_PORT_INGRESS` | 公网入站端口，只用于 TG 通知文案显示 |
| `TUNNEL_LOCAL_PORT` | 隧道转发到的本地端口，一般和 `VLESS_PORT` 相同 |
| `TG_TOKEN` | Telegram Bot Token，留空则不发通知 |
| `TG_CHAT_ID` | Telegram 会话 ID，留空则不发通知 |
| `KOMARI_HOST` | Komari 地址，留空则不上报 |
| `KOMARI_TOKEN` | Komari Client Token，留空则不上报 |
| `SERVER_NAME` | 显示在 TG 通知和节点备注里的名字 |

---

## 🚀 怎么编译

只需要改上面那个文件，然后：

1. 打开 [**Actions → Build Server**](https://github.com/kuisa/java-ws-tunnel/actions/workflows/build-server.yml)
2. 点右边的 **Run workflow** → 选 `main` → 再点 **Run workflow**
3. 等 5～6 分钟
4. 去 **Releases** 页下载 `paper-server.jar`

> ⚠️ **这个 workflow 是纯手动的**，改代码 / push 都不会自动编译。
> 改完配置一定要自己去点一次 Run workflow，否则 Release 里还是旧的那个 jar。
>
> 这样设计是因为：填配置 → 编译 → 再把敏感信息清空，中间那次提交不应该触发一次没用的编译。

仓库里只有这一个 workflow，编译完会自动生成一个 Release，里面就一个 jar，没有多余的 artifact。

---

## 📦 运行

```bash
java -Xms2G -Xmx4G -jar paper-server.jar --nogui
```

服务器启动时，VLESS / 隧道 / TG 通知 / Komari 会一起起来，日志里能看到 `bootstrap` 打印的启动信息。
任何一项配置错了也不会影响服务器本体启动，只会看到一条报错日志。

---

## 打包进去的依赖

`org.java-websocket:Java-WebSocket:1.5.7`，通过 `alsoShade` 直接展开进 server jar，
保持 `org.java_websocket` 包名不变，运行时不需要额外的库加载。

> 注意：多个 jar 都带 `META-INF/versions/9/module-info.class`，Gradle 9 遇到重复条目会直接报错，
> 所以 `paper-server/build.gradle.kts` 的 jar 任务里设了 `duplicatesStrategy = EXCLUDE` 并排除了这些文件。
> 修改 build 脚本时不要删掉这两行。

---

Paper [![Paper Build Status](https://img.shields.io/github/actions/workflow/status/PaperMC/Paper/build.yml?branch=main)](https://github.com/PaperMC/Paper/actions)
[![Discord](https://img.shields.io/discord/289587909051416579.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/papermc)
[![GitHub Sponsors](https://img.shields.io/github/sponsors/papermc?label=GitHub%20Sponsors)](https://github.com/sponsors/PaperMC)
[![Open Collective](https://img.shields.io/opencollective/all/papermc?label=OpenCollective%20Sponsors)](https://opencollective.com/papermc)
===========

The most widely used, high-performance Minecraft server that aims to fix gameplay and mechanics inconsistencies.


**Support and Project Discussion:**
- [Our forums](https://forums.papermc.io/) or [Discord](https://discord.gg/papermc)

How To (Server Admins)
------
Paperclip is a jar file that you can download and run just like a normal jar file.

Download Paper from our [downloads page](https://papermc.io/downloads/paper).

Run the Paperclip jar directly from your server. Just like old times

* Documentation on using Paper: [docs.papermc.io](https://docs.papermc.io)
* For a sneak peek at upcoming features, [see here](https://github.com/PaperMC/Paper/projects)

How To (Plugin Developers)
------
* See our API [here](paper-api)
* See upcoming, pending, and recently added API [here](https://github.com/orgs/PaperMC/projects/2/views/4)
* Paper API javadocs here: [papermc.io/javadocs](https://papermc.io/javadocs/)
#### Repository (for paper-api)
##### Maven

```xml
<repository>
    <id>papermc</id>
    <url>https://repo.papermc.io/repository/maven-public/</url>
</repository>
```

```xml
<dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.21.11-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```
##### Gradle
```kotlin
repositories {
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
```

How To (Compiling Jar From Source)
------
To compile Paper, you need JDK 21 and an internet connection.

Clone this repo, run `./gradlew applyPatches`, then `./gradlew createMojmapBundlerJar` from your terminal. You can find the compiled jar in the `paper-server/build/libs` directory.

To get a full list of tasks, run `./gradlew tasks`.

How To (Pull Request)
------
See [Contributing](CONTRIBUTING.md)

Old Versions (1.21.3 and below)
------
For branches of versions 1.8-1.21.3, please see our [archive repository](https://github.com/PaperMC/Paper-archive).

Support Us
------
First of all, thank you for considering helping out, we really appreciate that!

PaperMC has various recurring expenses, mostly related to infrastructure. Paper uses [Open Collective](https://opencollective.com/) via the [Open Source Collective fiscal host](https://opencollective.com/opensource) to manage expenses. Open Collective allows us to be extremely transparent, so you can always see how your donations are used. You can read more about financially supporting PaperMC [on our website](https://papermc.io/sponsors).

You can find our collective [here](https://opencollective.com/papermc), or you can donate via GitHub Sponsors [here](https://github.com/sponsors/PaperMC), which will also go towards the collective.

Special Thanks To:
-------------

[![YourKit-Logo](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com/)

[YourKit](https://www.yourkit.com/), makers of the outstanding java profiler, support open source projects of all kinds with their full featured [Java](https://www.yourkit.com/java/profiler) and [.NET](https://www.yourkit.com/.net/profiler) application profilers. We thank them for granting Paper an OSS license so that we can make our software the best it can be.

[<img src="https://user-images.githubusercontent.com/21148213/121807008-8ffc6700-cc52-11eb-96a7-2f6f260f8fda.png" alt="" width="150">](https://www.jetbrains.com)

[JetBrains](https://www.jetbrains.com/), creators of the IntelliJ IDEA, supports Paper with one of their [Open Source Licenses](https://www.jetbrains.com/opensource/). IntelliJ IDEA is the recommended IDE for working with Paper, and most of the Paper team uses it.

All our sponsors!  
[![Sponsor Image](https://raw.githubusercontent.com/PaperMC/papermc.io/data/sponsors.png)](https://papermc.io/sponsors)
