package com.linearizability.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

@Slf4j
public class SimpleHttpServer {

    private static final Integer PORT = 8080;

    static void main() throws Exception {
        // 创建 HTTP 服务器，监听 0.0.0.0:${PORT}
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // 注册处理器（所有路径都交给同一个处理）
        server.createContext("/", new RequestHandler());

        // 使用固定线程池处理请求（可选，默认是单线程）
        server.setExecutor(Executors.newFixedThreadPool(10));

        server.start();
        log.info("HTTP 服务器已启动，监听端口: {}", PORT);
        log.info("测试命令:");
        log.info("GET:  curl http://localhost:{}/test?name=alice", PORT);
        log.info("POST: curl -X POST -d 'user=Byron&age=18' http://localhost:{}/api", PORT);

        // 优雅停止
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭服务器...");
            server.stop(0);
            log.info("服务器已停止");
        }));
    }

    /**
     * 通用请求处理器
     */
    static class RequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String uri = exchange.getRequestURI().toString();
            String query = exchange.getRequestURI().getQuery();

            log.info("[{}] {}", method, uri);

            // 解析查询参数（GET）
            Map<String, String> queryParams = parseQueryString(query);

            // 读取请求体（POST/PUT）
            String requestBody = "";
            Map<String, String> formParams = new HashMap<>();
            if ("POST".equals(method) || "PUT".equals(method)) {
                requestBody = readRequestBody(exchange);
                // 尝试解析为表单数据（如 user=alice&age=30）
                formParams = parseFormBody(requestBody);
            }

            // 打印所有参数
            if (!queryParams.isEmpty()) {
                log.info("GET 参数: " + queryParams);
            }
            if (!formParams.isEmpty()) {
                log.info("POST 表单参数: " + formParams);
            } else if (!requestBody.isEmpty()) {
                log.info("原始请求体: " + requestBody);
            }

            // 构造响应
            String response = """
                    {"code":0}
                    """;
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

            // 设置响应头
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, responseBytes.length);

            // 发送响应体
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            } // 自动 flush & close

            log.info("已发送 200 OK 响应");
        }

        /**
         * 从 HttpExchange 读取请求体
         */
        private String readRequestBody(HttpExchange exchange) throws IOException {
            InputStream is = exchange.getRequestBody();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        /**
         * 解析查询字符串（如 "name=alice&age=30"）
         */
        private Map<String, String> parseQueryString(String query) {
            Map<String, String> params = new HashMap<>();
            if (query == null || query.isEmpty()) return params;

            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = keyValue.length > 1 ?
                        URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                params.put(key, value);
            }
            return params;
        }

        /**
         * 解析 POST 表单体（application/x-www-form-urlencoded）
         */
        private Map<String, String> parseFormBody(String body) {
            Map<String, String> params = new HashMap<>();
            if (body == null || body.isEmpty() || !body.contains("=")) return params;

            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = keyValue.length > 1 ?
                        URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                params.put(key, value);
            }
            return params;
        }
    }
}