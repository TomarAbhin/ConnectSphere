package com.connectsphere.search.serviceimpl;

import com.connectsphere.search.dto.AuthProfileResponse;
import com.connectsphere.search.dto.HashtagResponse;
import com.connectsphere.search.entity.Hashtag;
import com.connectsphere.search.repository.HashtagRepository;
import com.connectsphere.search.repository.PostHashtagRepository;
import com.connectsphere.search.repository.SearchRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private SearchRepository searchRepository;

    @Mock
    private HashtagRepository hashtagRepository;

    @Mock
    private PostHashtagRepository postHashtagRepository;

    @Mock
    private RestTemplate restTemplate;

    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchServiceImpl(
                searchRepository,
                hashtagRepository,
                postHashtagRepository,
                restTemplate,
                "http://auth",
                "http://follow"
        );
    }

    @Test
    void upsertHashtagForAdminNormalizesAndSavesTag() {
        AuthProfileResponse adminProfile = new AuthProfileResponse(
                1L,
                "admin",
                "admin@example.com",
                "Admin User",
                null,
                null,
                "ADMIN",
                "LOCAL",
                true,
                Instant.parse("2026-05-05T00:00:00Z")
        );

        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(adminProfile, HttpStatus.OK));
        when(hashtagRepository.findByTagIgnoreCase("helloworld")).thenReturn(Optional.empty());
        when(hashtagRepository.save(any(Hashtag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HashtagResponse response = searchService.upsertHashtagForAdmin("Bearer token", "  #HelloWorld  ");

        assertNotNull(response);
        assertEquals("helloworld", response.tag());
        assertEquals(1L, response.postCount());

        ArgumentCaptor<Hashtag> hashtagCaptor = ArgumentCaptor.forClass(Hashtag.class);
        org.mockito.Mockito.verify(hashtagRepository).save(hashtagCaptor.capture());
        assertEquals("helloworld", hashtagCaptor.getValue().getTag());
        assertEquals(1L, hashtagCaptor.getValue().getPostCount());
    }
}