package com.linearizability.broadcast;

import lombok.extern.slf4j.Slf4j;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * 广播接收
 *
 * @author ZhangBoyuan
 * @since 2026-01-08
 */
@Slf4j
public class BroadcastReceiver {

    /**
     * 监听端口
     */
    private static final int BROADCAST_PORT = 8080;

    /**
     * 是否自动回复（模拟设备应答）
     */
    private static final boolean SEND_RESPONSE = true;

    static void main() {
        log.info("正在监听 UDP 广播端口: " + BROADCAST_PORT);

        try (DatagramSocket socket = new DatagramSocket(BROADCAST_PORT)) {
            // 设置 SO_REUSEADDR，防止无法绑定
            socket.setReuseAddress(true);

            while (true) {
                byte[] buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                InetAddress senderIP = packet.getAddress();
                int senderPort = packet.getPort();

                log.info("收到广播消息:");
                log.info("来源: {}:{}", senderIP.getHostAddress(), senderPort);
                log.info("内容: {}", message);
                log.info("----------------------------------------");

                if (SEND_RESPONSE) {
                    sendResponse(socket, senderIP, senderPort, """
                            {
                                "deviceIp": "192.168.1.100",
                                "status": "online"
                            }
                            """);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 模拟设备响应（单播回复给请求方）
    private static void sendResponse(DatagramSocket socket, InetAddress targetIP, int targetPort, String responseJson) {
        try {
            byte[] data = responseJson.getBytes(StandardCharsets.UTF_8);
            DatagramPacket responsePacket = new DatagramPacket(data, data.length, targetIP, targetPort);
            socket.send(responsePacket);
            log.info("已回复设备信息给 {}:{}", targetIP.getHostAddress(), targetPort);
        } catch (Exception e) {
            log.error("回复失败: {}", e.getMessage());
        }
    }
}
