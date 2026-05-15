package com.connectsphere.like.resource;

import com.connectsphere.like.dto.ChangeReactionRequest;
import com.connectsphere.like.dto.HasLikedResponse;
import com.connectsphere.like.dto.LikeCountResponse;
import com.connectsphere.like.dto.LikeRequest;
import com.connectsphere.like.dto.LikeResponse;
import com.connectsphere.like.dto.LikeSummaryResponse;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.service.LikeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/likes")
public class LikeResource {

    private final LikeService likeService;

    public LikeResource(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping
    public ResponseEntity<LikeResponse> likeTarget(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody LikeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(likeService.likeTarget(authorization, request));
    }

    @DeleteMapping
    public ResponseEntity<LikeResponse> unlikeTarget(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam TargetType targetType,
            @RequestParam Long targetId
    ) {
        return ResponseEntity.ok(likeService.unlikeTarget(authorization, targetType, targetId));
    }

    @GetMapping("/has-liked")
    public ResponseEntity<HasLikedResponse> hasLiked(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam TargetType targetType,
            @RequestParam Long targetId
    ) {
        return ResponseEntity.ok(likeService.hasLiked(authorization, targetType, targetId));
    }

    @GetMapping("/target/{targetType}/{targetId}")
    public ResponseEntity<List<LikeResponse>> getLikesByTarget(
            @PathVariable TargetType targetType,
            @PathVariable Long targetId
    ) {
        return ResponseEntity.ok(likeService.getLikesByTarget(targetType, targetId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<LikeResponse>> getLikesByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(likeService.getLikesByUser(userId));
    }

    @GetMapping("/count/{targetType}/{targetId}")
    public ResponseEntity<LikeCountResponse> getLikeCount(
            @PathVariable TargetType targetType,
            @PathVariable Long targetId
    ) {
        return ResponseEntity.ok(likeService.getLikeCount(targetType, targetId));
    }

    @GetMapping("/count/{targetType}/{targetId}/{reactionType}")
    public ResponseEntity<LikeCountResponse> getLikeCountByType(
            @PathVariable TargetType targetType,
            @PathVariable Long targetId,
            @PathVariable ReactionType reactionType
    ) {
        return ResponseEntity.ok(likeService.getLikeCountByType(targetType, targetId, reactionType));
    }

    @GetMapping("/summary/{targetType}/{targetId}")
    public ResponseEntity<LikeSummaryResponse> getReactionSummary(
            @PathVariable TargetType targetType,
            @PathVariable Long targetId
    ) {
        return ResponseEntity.ok(likeService.getReactionSummary(targetType, targetId));
    }

    @PutMapping("/{likeId}/reaction")
    public ResponseEntity<LikeResponse> changeReaction(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long likeId,
            @Valid @RequestBody ChangeReactionRequest request
    ) {
        return ResponseEntity.ok(likeService.changeReaction(authorization, likeId, request));
    }
}
