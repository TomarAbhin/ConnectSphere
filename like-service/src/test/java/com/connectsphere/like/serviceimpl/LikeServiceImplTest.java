package com.connectsphere.like.serviceimpl;

import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.repository.LikeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceImplTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private RestTemplate restTemplate;

    private LikeServiceImpl likeService;

    @BeforeEach
    void setUp() {
        likeService = new LikeServiceImpl(
                likeRepository,
                restTemplate,
                "http://auth",
                "http://post",
                "http://comment",
                "http://media",
                "http://notification"
        );
    }

    @Test
    void getReactionSummaryAggregatesCounts() {
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.LIKE)).thenReturn(3L);
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.LOVE)).thenReturn(2L);
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.HAHA)).thenReturn(1L);
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.WOW)).thenReturn(0L);
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.SAD)).thenReturn(0L);
        when(likeRepository.countByTargetIdAndTargetTypeAndReactionType(7L, TargetType.POST, ReactionType.ANGRY)).thenReturn(0L);
        when(likeRepository.countByTargetIdAndTargetType(7L, TargetType.POST)).thenReturn(6L);

        var response = likeService.getReactionSummary(TargetType.POST, 7L);

        assertEquals(6L, response.totalCount());
        assertEquals(3L, response.likeCount());
        assertEquals(2L, response.loveCount());
        assertEquals(1L, response.hahaCount());
    }
}