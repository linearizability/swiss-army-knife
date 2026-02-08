package com.linearizability.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件中转
 *
 * @author ZhangBoyuan
 * @since 2026-02-08
 */
@Slf4j
public class FileTransferServer {

    // ============ 服务器配置常量 ============
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_STORAGE_SUFFIX = "data/transfer_storage";
    private static final String DEFAULT_STORAGE_FALLBACK = System.getProperty("user.home") + "/.filetransfer/storage";

    // ============ HTTP 路由常量 ============
    private static final String CONTEXT_ROOT = "/";
    private static final String CONTEXT_UPLOAD = "/upload";
    private static final String CONTEXT_FILES = "/files/";

    // ============ 资源与模板常量 ============
    private static final String TEMPLATE_RESOURCE = "/static/index.html";
    private static final String FILE_ITEM_RESOURCE = "/static/file-item.html";
    private static final String FILES_PLACEHOLDER = "{{FILES}}";
    private static final String UPLOAD_PATH_PLACEHOLDER = "{{UPLOAD_PATH}}";

    // ============ HTTP 协议常量 ============
    private static final String HTTP_POST = "POST";
    private static final String HTTP_GET = "GET";
    private static final String HEADER_LOCATION = "Location";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    private static final String CONTENT_TYPE_HTML = "text/html; charset=utf-8";
    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final String CONTENT_DISPOSITION_ATTACHMENT = "attachment; filename=\"";

    // ============ Multipart 协议常量 ============
    private static final String CRLF = "\r\n";
    private static final byte[] CRLF_BYTES = CRLF.getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] CRLF_CRLF_BYTES = (CRLF + CRLF).getBytes(StandardCharsets.ISO_8859_1);
    private static final String BOUNDARY_PREFIX = "--";
    private static final String BOUNDARY_PARAM = "boundary=";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String FILENAME = "filename";
    private static final String FILENAME_STAR = "filename*";

    // ============ 缓冲区与编码常量 ============
    private static final int BUFFER_SIZE = 65536; // 64KB 缓冲区，提升大文件传输效率
    private static final String CHARSET_UTF8 = StandardCharsets.UTF_8.name();
    private static final String CHARSET_ISO_8859_1 = StandardCharsets.ISO_8859_1.name();

    // ============ 性能优化缓存 ============
    // Content-Type 缓存：避免重复检测文件类型，提升下载响应速度
    private static final ConcurrentHashMap<String, String> contentTypeCache = new ConcurrentHashMap<>();

    // ============ 存储路径解析逻辑 ============
    private static String resolveDefaultStorage() {
        // 优先尝试使用 resources 根目录（IDE/target/classes）
        try {
            java.net.URL url = FileTransferServer.class.getResource("/");
            if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
                try {
                    Path resRoot = Paths.get(url.toURI());
                    Path candidate = resRoot.resolve(DEFAULT_STORAGE_SUFFIX);
                    // 尝试创建目录以验证可写性
                    Files.createDirectories(candidate);
                    return candidate.toString();
                } catch (Exception ignore) {
                    // 资源目录不可写，进入回退流程
                }
            }
        } catch (Exception ignore) {
        }

        // 回退1：用户主目录下的隐藏目录
        try {
            Path fb = Paths.get(DEFAULT_STORAGE_FALLBACK);
            Files.createDirectories(fb);
            return fb.toString();
        } catch (Exception e) {
        }

        // 回退2：当前工作目录
        return Paths.get("transfer_storage").toAbsolutePath().toString();
    }

    // ============ Multipart 解析逻辑 ============
    private static void parseMulitpartBody(byte[] body, byte[] boundaryBytes, Path storage) throws IOException {
        int pos = indexOf(body, boundaryBytes, 0);
        if (pos < 0) return;
        pos += boundaryBytes.length;

        while (true) {
            // 跳过 CRLF
            if (pos + 2 <= body.length && body[pos] == '\r' && body[pos + 1] == '\n') {
                pos += 2;
            }

            // 查找下一个 boundary
            int next = indexOf(body, boundaryBytes, pos);
            if (next < 0) break;

            // 查找 headers 和 body 的分界 CRLF CRLF
            int headerEnd = indexOf(body, CRLF_CRLF_BYTES, pos);
            if (headerEnd < 0 || headerEnd > next) break;

            // 解析 headers 获取文件名
            String headers = new String(body, pos, headerEnd - pos, StandardCharsets.ISO_8859_1);
            String filename = parseFileNameFromHeaders(headers);

            // 计算文件数据范围
            int dataStart = headerEnd + CRLF_CRLF_BYTES.length;
            int dataEnd = next - CRLF_BYTES.length; // 去掉 boundary 前的 CRLF

            if (dataEnd >= dataStart && filename != null && !filename.isEmpty()) {
                String normalized = normalizeFilename(filename);
                String safe = Paths.get(normalized).getFileName().toString();
                Path target = storage.resolve(safe);
                log.info("Received filename raw: {}, normalized: {}", filename, normalized);
                Files.write(target, Arrays.copyOfRange(body, dataStart, dataEnd));
                log.info("Saved: {}", target);
            }

            // 移到下一个 boundary
            pos = next + boundaryBytes.length;

            // 检查是否为最后一个 boundary（以 -- 结尾）
            if (pos + 2 <= body.length && body[pos] == '-' && body[pos + 1] == '-') {
                break;
            }
        }
    }

    // ============ 文件名解析逻辑 ============
    private static String parseFileNameFromHeaders(String headers) {
        for (String line : headers.split(CRLF)) {
            String lower = line.toLowerCase();
            if (lower.startsWith(CONTENT_DISPOSITION.toLowerCase() + ":")) {
                // 优先尝试 filename* (RFC5987)
                if (lower.contains(FILENAME_STAR.toLowerCase())) {
                    String result = parseRFC5987Filename(line);
                    if (result != null) return result;
                }
                // 回退到普通 filename
                String result = parseSimpleFilename(line);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static String parseRFC5987Filename(String line) {
        int start = line.toLowerCase().indexOf(FILENAME_STAR.toLowerCase() + "=");
        if (start < 0) return null;
        String part = line.substring(start + FILENAME_STAR.length() + 1).trim();
        if (part.startsWith("\"")) part = part.substring(1);
        if (part.endsWith("\"")) part = part.substring(0, part.length() - 1);
        try {
            String enc = CHARSET_UTF8;
            String value = part;
            int q = part.indexOf("''");
            if (q > 0) {
                enc = part.substring(0, q);
                value = part.substring(q + 2);
            }
            return URLDecoder.decode(value, enc);
        } catch (Exception e) {
            return null;
        }
    }

    private static String parseSimpleFilename(String line) {
        int idx = line.toLowerCase().indexOf(FILENAME.toLowerCase() + "=");
        if (idx < 0) return null;
        String fn = line.substring(idx + FILENAME.length() + 1).trim();
        if (fn.startsWith("\"") && fn.endsWith("\"")) {
            fn = fn.substring(1, fn.length() - 1);
        }
        // 尝试 UTF-8 百分号解码
        try {
            if (fn.contains("%")) {
                String decoded = URLDecoder.decode(fn, CHARSET_UTF8);
                if (decoded != null && !decoded.isEmpty()) return decoded;
            }
        } catch (Exception e) {
        }
        // 尝试 ISO-8859-1 -> UTF-8 转换
        try {
            byte[] bytes = fn.getBytes(StandardCharsets.ISO_8859_1);
            String maybe = new String(bytes, StandardCharsets.UTF_8);
            return maybe;
        } catch (Exception e) {
        }
        return fn;
    }

    // ============ 文件名归一化逻辑 ============
    private static String normalizeFilename(String fn) {
        if (fn == null) return null;
        String s = fn;
        // 1. 尝试百分号解码
        try {
            if (s.contains("%")) {
                String d = URLDecoder.decode(s, CHARSET_UTF8);
                if (d != null && !d.isEmpty()) return d;
            }
        } catch (Exception e) {
        }

        // 2. 尝试 ISO-8859-1 -> UTF-8 转换（针对中文等）
        int highCount = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) highCount++;
        }
        if (highCount > 0) {
            try {
                byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
                String maybe = new String(bytes, StandardCharsets.UTF_8);
                // 如果结果包含 CJK 字符，认为转换成功
                for (int i = 0; i < maybe.length(); i++) {
                    Character.UnicodeBlock ub = Character.UnicodeBlock.of(maybe.charAt(i));
                    if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION || ub == Character.UnicodeBlock.HIRAGANA || ub == Character.UnicodeBlock.KATAKANA) {
                        return maybe;
                    }
                }
                // 否则，如果非 ASCII 字符增多也认为成功
                int mHigh = 0;
                for (int i = 0; i < maybe.length(); i++) {
                    if (maybe.charAt(i) > 127) mHigh++;
                }
                if (mHigh > highCount) return maybe;
            } catch (Exception e) {
            }
        }

        // 3. 回退：返回原文
        return s;
    }

    private static int indexOf(byte[] outer, byte[] target, int from) {
        outer:
        for (int i = from; i <= outer.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (outer[i + j] != target[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    // ============ 工具方法 ============
    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[BUFFER_SIZE];
        int r;
        while ((r = in.read(buf)) != -1) {
            baos.write(buf, 0, r);
        }
        return baos.toByteArray();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    static class RootHandler implements HttpHandler {
        private final Path storage;
        private final String template;
        private final String fileItemTemplate;

        RootHandler(Path storage) throws IOException {
            this.storage = storage;
            String t = null;
            try (InputStream is = FileTransferServer.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
                if (is != null) t = new String(readAll(is), StandardCharsets.UTF_8);
            }
            if (t == null) throw new IOException("Template resource missing: " + TEMPLATE_RESOURCE);
            this.template = t;

            String fi = null;
            try (InputStream is = FileTransferServer.class.getResourceAsStream(FILE_ITEM_RESOURCE)) {
                if (is != null) fi = new String(readAll(is), StandardCharsets.UTF_8);
            }
            if (fi == null) throw new IOException("File item template missing: " + FILE_ITEM_RESOURCE);
            this.fileItemTemplate = fi;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!HTTP_GET.equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            StringBuilder filesHtml = new StringBuilder();
            Files.list(storage).sorted(Comparator.reverseOrder()).forEach(p -> {
                String name = p.getFileName().toString();
                try {
                    String url = CONTEXT_FILES + URLEncoder.encode(name, CHARSET_UTF8);
                    String item = fileItemTemplate.replace("{{URL}}", escapeHtml(url)).replace("{{NAME}}", escapeHtml(name)).replace("{{SIZE}}", String.valueOf(Files.size(p)));
                    filesHtml.append(item);
                } catch (Exception e) { /* ignore */ }
            });

            String page = template.replace(FILES_PLACEHOLDER, filesHtml.toString()).replace(UPLOAD_PATH_PLACEHOLDER, CONTEXT_UPLOAD);
            byte[] resp = page.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HEADER_CONTENT_TYPE, CONTENT_TYPE_HTML);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }
    }

    static class UploadHandler implements HttpHandler {
        private final Path storage;

        UploadHandler(Path storage) {
            this.storage = storage;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!HTTP_POST.equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst(HEADER_CONTENT_TYPE);
            if (contentType == null || !contentType.contains(BOUNDARY_PARAM)) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            String boundary = BOUNDARY_PREFIX + contentType.substring(contentType.indexOf(BOUNDARY_PARAM) + BOUNDARY_PARAM.length());
            byte[] body = readAll(exchange.getRequestBody());
            parseMulitpartBody(body, boundary.getBytes(StandardCharsets.ISO_8859_1), storage);

            exchange.getResponseHeaders().set(HEADER_LOCATION, CONTEXT_ROOT);
            exchange.sendResponseHeaders(302, -1);
        }
    }

    static class FileHandler implements HttpHandler {
        private final Path storage;

        FileHandler(Path storage) {
            this.storage = storage;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!HTTP_GET.equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String uri = exchange.getRequestURI().getPath();
            if (!uri.startsWith(CONTEXT_FILES)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String name = uri.substring(CONTEXT_FILES.length());
            name = URLDecoder.decode(name, CHARSET_UTF8);
            Path target = storage.resolve(name).normalize();
            if (!target.startsWith(storage) || !Files.exists(target) || Files.isDirectory(target)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
            long len = Files.size(target);
            log.info("Download - ClientIP: {}, File: {}, Size: {} bytes", clientIP, name, len);
            // Content-Type 缓存：避免重复 Files.probeContentType() 调用
            String contentType = contentTypeCache.computeIfAbsent(target.toString(), p -> {
                String detected = null;
                try {
                    detected = Files.probeContentType(target);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return detected != null ? detected : CONTENT_TYPE_OCTET_STREAM;
            });

            exchange.getResponseHeaders().set(HEADER_CONTENT_TYPE, contentType);
            exchange.getResponseHeaders().set(HEADER_CONTENT_DISPOSITION, CONTENT_DISPOSITION_ATTACHMENT + target.getFileName().toString() + "\"");
            exchange.sendResponseHeaders(200, len);

            // 零拷贝下载：使用 FileChannel.transferTo() 直接将文件传输到 HTTP 响应流
            // 避免在 JVM 堆中缓冲整个文件，适合超大文件
            try (FileChannel fileChannel = FileChannel.open(target, StandardOpenOption.READ); WritableByteChannel responseChannel = new ByteChannelAdapter(exchange.getResponseBody())) {
                fileChannel.transferTo(0, len, responseChannel);
            }
        }
    }

    // ============ 零拷贝适配器 ============
    private static class ByteChannelAdapter implements WritableByteChannel {
        private final OutputStream out;
        private boolean closed = false;

        ByteChannelAdapter(OutputStream out) {
            this.out = out;
        }

        @Override
        public int write(java.nio.ByteBuffer src) throws IOException {
            int remaining = src.remaining();
            byte[] buf = new byte[remaining];
            src.get(buf);
            out.write(buf);
            return remaining;
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            out.close();
        }
    }

    static void main() throws Exception {
        int port = DEFAULT_PORT;
        String dir = resolveDefaultStorage();
        Path storage = Paths.get(dir).toAbsolutePath();
        Files.createDirectories(storage);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(CONTEXT_ROOT, new RootHandler(storage));
        server.createContext(CONTEXT_UPLOAD, new UploadHandler(storage));
        server.createContext(CONTEXT_FILES.substring(0, CONTEXT_FILES.length() - 1), new FileHandler(storage));
        server.setExecutor(null);

        // 获取本机 IP 地址用于显示
        String localIP = java.net.InetAddress.getLocalHost().getHostAddress();
        String serverUrl = "http://" + localIP + ":" + port + "/";

        log.info("==================== FileTransferServer ====================");
        log.info("Server URL: {}", serverUrl);
        log.info("Storage directory: {}", storage);
        log.info("==========================================================");

        server.start();
    }

}
