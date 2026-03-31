package com.social.gateway;

import io.netty.channel.ChannelOption;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.resources.LoopResources;

/**
 * Netty tuning for high connection counts.
 *
 * Default Netty uses 2x CPU cores for event loop threads. Each thread can handle
 * thousands of connections via non-blocking I/O multiplexing (epoll/kqueue).
 *
 * Key tuning knobs:
 * - Event loop threads: controls parallelism for message processing
 * - SO_BACKLOG: TCP accept queue depth for burst connections
 * - TCP_NODELAY: disable Nagle's algorithm for low-latency push
 * - SO_KEEPALIVE: detect dead connections (important for mobile clients)
 *
 * OS-level tuning (required for >10K connections):
 *   ulimit -n 200000          # file descriptors
 *   sysctl net.core.somaxconn=65535  # accept queue
 *   sysctl net.ipv4.tcp_max_syn_backlog=65535
 */
@Configuration
public class NettyConfig {

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyCustomizer() {
        return factory -> factory.addServerCustomizers(httpServer -> httpServer
                // Use a dedicated event loop group sized for connection handling
                .runOn(LoopResources.create("ws-loop",
                        1,    // accept threads (1 is enough)
                        Math.max(4, Runtime.getRuntime().availableProcessors()), // worker threads
                        true  // daemon threads
                ))
                .option(ChannelOption.SO_BACKLOG, 8192)    // TCP accept queue
                .childOption(ChannelOption.TCP_NODELAY, true)  // Low latency
                .childOption(ChannelOption.SO_KEEPALIVE, true) // Detect dead connections
        );
    }
}
