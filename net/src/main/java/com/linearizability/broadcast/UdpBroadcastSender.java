package com.linearizability.broadcast;

import lombok.extern.slf4j.Slf4j;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

@Slf4j
public class UdpBroadcastSender {

    // 目标端口（接收方需监听此端口）
    private static final Integer BROADCAST_PORT = 8080;

    private static final String BROADCAST_IP = "255.255.255.255";

    static void main() {
        String message = """
                    {
                        "key1": "value1"
                    }
            """;

        try {
            try (DatagramSocket socket = new DatagramSocket()) {
                // 启用广播权限
                socket.setBroadcast(true);

                InetAddress broadcastAddress = InetAddress.getByName(BROADCAST_IP);

                byte[] buf = message.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(buf, buf.length, broadcastAddress, BROADCAST_PORT);

                socket.send(packet);
                log.info("UDP 广播已发送到端口 {}", BROADCAST_PORT);
            }

        } catch (Exception e) {
            log.error("发送 UDP 广播失败: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}
