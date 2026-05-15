package com.connectsphere.follow.service;

import com.connectsphere.follow.dto.FollowCountResponse;
import com.connectsphere.follow.dto.FollowResponse;
import com.connectsphere.follow.dto.FollowStatusResponse;
import com.connectsphere.follow.dto.FollowerResponse;
import com.connectsphere.follow.dto.FollowingResponse;
import com.connectsphere.follow.dto.SuggestedUserResponse;
import java.util.List;

public interface FollowService {

    FollowResponse follow(String authorizationHeader, Long followedId);

    void unfollow(String authorizationHeader, Long followedId);

    FollowStatusResponse isFollowing(String authorizationHeader, Long followedId);

    List<FollowerResponse> getFollowers(String authorizationHeader, Long userId);

    List<FollowingResponse> getFollowing(String authorizationHeader, Long userId);

    FollowCountResponse getCounts(String authorizationHeader, Long userId);

    List<FollowerResponse> getMutualFollows(String authorizationHeader, Long userId);

    List<SuggestedUserResponse> getSuggestedUsers(String authorizationHeader);
}
