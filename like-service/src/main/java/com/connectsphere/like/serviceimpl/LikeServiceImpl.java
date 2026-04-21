package com.connectsphere.like.serviceimpl;

import com.connectsphere.like.dto.AuthProfileResponse;
import com.connectsphere.like.dto.ChangeReactionRequest;
import com.connectsphere.like.dto.HasLikedResponse;
import com.connectsphere.like.dto.LikeCountResponse;
import com.connectsphere.like.dto.LikeRequest;
import com.connectsphere.like.dto.LikeResponse;
import com.connectsphere.like.dto.LikeSummaryResponse;
import com.connectsphere.like.entity.Like;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.repository.LikeRepository;
import com.connectsphere.like.service.LikeService;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class LikeServiceImpl implements LikeService {

    private final LikeRepository likeRepository;
    private final RestTemplate restTemplate;
    private final String authServiceUrl;
    private final String postServiceUrl;
    private final String commentServiceUrl;

    public LikeServiceImpl(
            LikeRepository likeRepository,
            RestTemplate restTemplate,
            @Value("${app.services.auth-service.url:http://localhost:8081}") String authServiceUrl,
            @Value("${app.services.post-service.url:http://localhost:8082}") String postServiceUrl,
            @Value("${app.services.comment-service.url:http://localhost:8083}") String commentServiceUrl
    ) {
        this.likeRepository = likeRepository;
        this.restTemplate = restTemplate;
        this.authServiceUrl = authServiceUrl;
        this.postServiceUrl = postServiceUrl;
        this.commentServiceUrl = commentServiceUrl;
    }

    @Override
    public LikeResponse likeTarget(String authorizationHeader, LikeRequest request) {
        Long userId = resolveCurrentUserId(authorizationHeader);
        validateTarget(authorizationHeader, request.targetType(), request.targetId());

        Like existing = likeRepository.findByUserIdAndTargetIdAndTargetType(userId, request.targetId(), request.targetType())
                .orElse(null);
        if (existing != null) {
            if (existing.getReactionType() == request.reactionType()) {
                return toResponse(existing);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reaction already exists. Use change reaction instead.");
        }

        Like like = new Like();
        like.setUserId(userId);
        like.setTargetId(request.targetId());
        like.setTargetType(request.targetType());
        like.setReactionType(request.reactionType());
        Like saved = likeRepository.save(like);
        incrementTargetReactionCount(authorizationHeader, request.targetType(), request.targetId(), request.reactionType());
        return toResponse(saved);
    }

    @Override
    public LikeResponse unlikeTarget(String authorizationHeader, TargetType targetType, Long targetId) {
        Long userId = resolveCurrentUserId(authorizationHeader);
        Like like = likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reaction not found"));
        likeRepository.delete(like);
        decrementTargetReactionCount(authorizationHeader, targetType, targetId, like.getReactionType());
        return toResponse(like);
    }

    @Override
    @Transactional(readOnly = true)
    public HasLikedResponse hasLiked(String authorizationHeader, TargetType targetType, Long targetId) {
        Long userId = resolveCurrentUserId(authorizationHeader);
        return likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType)
                .map(like -> new HasLikedResponse(true, like.getReactionType()))
                .orElse(new HasLikedResponse(false, null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LikeResponse> getLikesByTarget(TargetType targetType, Long targetId) {
        return likeRepository.findByTargetIdAndTargetTypeOrderByCreatedAtDesc(targetId, targetType)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LikeResponse> getLikesByUser(Long userId) {
        return likeRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LikeCountResponse getLikeCount(TargetType targetType, Long targetId) {
        return new LikeCountResponse(targetType, targetId, likeRepository.countByTargetIdAndTargetType(targetId, targetType));
    }

    @Override
    @Transactional(readOnly = true)
    public LikeCountResponse getLikeCountByType(TargetType targetType, Long targetId, ReactionType reactionType) {
        return new LikeCountResponse(
                targetType,
                targetId,
                likeRepository.countByTargetIdAndTargetTypeAndReactionType(targetId, targetType, reactionType)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public LikeSummaryResponse getReactionSummary(TargetType targetType, Long targetId) {
        long likeCount = likeRepository.countByTargetIdAndTargetTypeAndReactionType(targetId, targetType, ReactionType.LIKE);
        long loveCount = likeRepository.countByTargetIdAndTargetTypeAndReactionType(targetId, targetType, ReactionType.LOVE);
        long hahaCount = likeRepository.countByTargetIdAndTargetTypeAndReactionType(targetId, targetType, ReactionType.HAHA);
        long wowCount = likeRepository.countByTargetIdAndTargetTypeAndReactionType(targetId, targetType, ReactionType.WOW);
        long sadCount = likeRepository.countByTargetIdAndTargetTypeAndReactionType(targetId, targetType, ReactionType.SAD);
        long angryCount = likeRepository.countByTargetIdAndTargetTypeAndReactionType(targetId, targetType, ReactionType.ANGRY);
        long totalCount = likeRepository.countByTargetIdAndTargetType(targetId, targetType);

        return new LikeSummaryResponse(targetType, targetId, totalCount, likeCount, loveCount, hahaCount, wowCount, sadCount, angryCount);
    }

    @Override
    public LikeResponse changeReaction(String authorizationHeader, Long likeId, ChangeReactionRequest request) {
        Long userId = resolveCurrentUserId(authorizationHeader);
        Like like = likeRepository.findByLikeIdAndUserId(likeId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reaction not found"));

        if (like.getReactionType() == request.reactionType()) {
            return toResponse(like);
        }

        ReactionType previousReaction = like.getReactionType();
        like.setReactionType(request.reactionType());
        Like saved = likeRepository.save(like);
        decrementTargetReactionCount(authorizationHeader, like.getTargetType(), like.getTargetId(), previousReaction);
        incrementTargetReactionCount(authorizationHeader, saved.getTargetType(), saved.getTargetId(), saved.getReactionType());
        return toResponse(saved);
    }

    private void validateTarget(String authorizationHeader, TargetType targetType, Long targetId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorizationHeader);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            String url = switch (targetType) {
                case POST -> postServiceUrl + "/posts/" + targetId;
                case COMMENT -> commentServiceUrl + "/comments/" + targetId;
            };
            restTemplate.exchange(url, HttpMethod.GET, request, String.class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target not found or not accessible");
        }
    }

    private Long resolveCurrentUserId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorizationHeader);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            AuthProfileResponse profile = restTemplate.exchange(
                    authServiceUrl + "/auth/profile",
                    HttpMethod.GET,
                    request,
                    AuthProfileResponse.class
            ).getBody();

            if (profile == null || profile.userId() == null || !profile.active()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
            }
            return profile.userId();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
    }

    private void incrementTargetReactionCount(String authorizationHeader, TargetType targetType, Long targetId, ReactionType reactionType) {
        updateTargetReactionCount(authorizationHeader, targetType, targetId, true);
    }

    private void decrementTargetReactionCount(String authorizationHeader, TargetType targetType, Long targetId, ReactionType reactionType) {
        updateTargetReactionCount(authorizationHeader, targetType, targetId, false);
    }

    private void updateTargetReactionCount(String authorizationHeader, TargetType targetType, Long targetId, boolean increment) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorizationHeader);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            if (targetType == TargetType.POST) {
                String path = increment ? "/posts/" + targetId + "/likes" : "/posts/" + targetId + "/likes";
                HttpMethod method = increment ? HttpMethod.POST : HttpMethod.DELETE;
                restTemplate.exchange(postServiceUrl + path, method, request, String.class);
            } else {
                String path = increment ? "/comments/" + targetId + "/likes" : "/comments/" + targetId + "/likes";
                HttpMethod method = increment ? HttpMethod.POST : HttpMethod.DELETE;
                restTemplate.exchange(commentServiceUrl + path, method, request, String.class);
            }
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to update reaction count on target service");
        }
    }

    private LikeResponse toResponse(Like like) {
        return new LikeResponse(
                like.getLikeId(),
                like.getUserId(),
                like.getTargetId(),
                like.getTargetType(),
                like.getReactionType(),
                like.getCreatedAt(),
                like.getUpdatedAt()
        );
    }
}
