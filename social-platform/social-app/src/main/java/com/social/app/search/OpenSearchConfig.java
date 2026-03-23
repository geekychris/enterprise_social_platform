package com.social.app.search;

import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;

@Configuration
public class OpenSearchConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchConfig.class);

    @Value("${social.opensearch.host}")
    private String host;

    @Value("${social.opensearch.port}")
    private int port;

    @Bean
    public OpenSearchClient openSearchClient() {
        try {
            RestClient restClient = RestClient.builder(
                    new HttpHost(host, port, "http")
            ).build();
            var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            return new OpenSearchClient(transport);
        } catch (Exception e) {
            log.warn("Could not create OpenSearch client: {}", e.getMessage());
            RestClient restClient = RestClient.builder(
                    new HttpHost(host, port, "http")
            ).build();
            var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            return new OpenSearchClient(transport);
        }
    }
}
