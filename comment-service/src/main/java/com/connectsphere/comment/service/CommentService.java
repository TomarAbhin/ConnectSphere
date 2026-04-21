package com.connectsphere.comment.service;

import com.connectsphere.comment.dto.AddCommentRequest;
import com.connectsphere.comment.dto.CommentResponse;
import com.connectsphere.comment.dto.UpdateCommentRequest;
import java.util.List;

public interface CommentService {

    CommentResponse addComment(String authorizationHeader, AddCommentRequest request);

    List<CommentResponse> getCommentsByPost(Long postId);

    List<CommentResponse> getCommentsByUser(Long userId);

    List<CommentResponse> getReplies(Long parentCommentId);

    CommentResponse updateComment(String authorizationHeader, Long commentId, UpdateCommentRequest request);

    void deleteComment(String authorizationHeader, Long commentId);

    CommentResponse likeComment(Long commentId);

    CommentResponse unlikeComment(Long commentId);

    long getCommentCount(Long postId);
}
