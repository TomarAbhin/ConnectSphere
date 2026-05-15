package com.connectsphere.follow.resource;

import com.connectsphere.follow.dto.FollowCountResponse;
import com.connectsphere.follow.dto.FollowResponse;
import com.connectsphere.follow.dto.FollowStatusResponse;
import com.connectsphere.follow.dto.FollowerResponse;
import com.connectsphere.follow.dto.FollowingResponse;
import com.connectsphere.follow.dto.SuggestedUserResponse;
import com.connectsphere.follow.service.FollowService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/follows")
public class FollowResource {

    private final FollowService followService;

    public FollowResource(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/{followedId}")
    public ResponseEntity<FollowResponse> follow(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long followedId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(followService.follow(authorization, followedId));
    }

    @DeleteMapping("/{followedId}")
    public ResponseEntity<Void> unfollow(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long followedId
    ) {
        followService.unfollow(authorization, followedId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{followedId}/following-status")
    public ResponseEntity<FollowStatusResponse> isFollowing(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long followedId
    ) {
        return ResponseEntity.ok(followService.isFollowing(authorization, followedId));
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<FollowerResponse>> getFollowers(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(followService.getFollowers(authorization, userId));
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<List<FollowingResponse>> getFollowing(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(followService.getFollowing(authorization, userId));
    }

    @GetMapping("/{userId}/counts")
    public ResponseEntity<FollowCountResponse> getCounts(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(followService.getCounts(authorization, userId));
    }

    @GetMapping("/{userId}/mutual")
    public ResponseEntity<List<FollowerResponse>> getMutualFollows(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(followService.getMutualFollows(authorization, userId));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<SuggestedUserResponse>> getSuggestedUsers(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ResponseEntity.ok(followService.getSuggestedUsers(authorization));
    }
}
