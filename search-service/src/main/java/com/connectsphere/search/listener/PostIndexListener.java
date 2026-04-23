package com.connectsphere.search.listener;

import com.connectsphere.search.dto.IndexPostRequest;
import com.connectsphere.search.service.SearchService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "app.rabbitmq.listener.enabled", havingValue = "true")
public class PostIndexListener {

    private final SearchService searchService;

    public PostIndexListener(SearchService searchService) {
        this.searchService = searchService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.search}")
    public void onPostPublished(IndexPostRequest request) {
        searchService.indexPost(request);
    }
}