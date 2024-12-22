package com.example.application.helper;

import com.example.application.domain.comment.response.CommentListResponse;
import com.example.application.domain.comment.response.CommentResponse;
import com.example.application.domain.post.response.PostListResponse;
import com.example.application.domain.post.response.PostResponse;

import java.util.List;
import java.util.stream.Collectors;

public class JsonSerializer {

    public static String toJson(PostListResponse postListResponse) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        json.append("\"postResponses\":");
        json.append(toJsonPostResponseList(postListResponse.postResponses()));

        json.append(",\"totalCount\":");
        json.append(postListResponse.totalCount());

        json.append("}");
        return json.toString();
    }

    private static String toJsonPostResponseList(List<PostResponse> postResponses) {
        String jsonArray = postResponses.stream()
                .map(JsonSerializer::toJsonPostResponse)
                .collect(Collectors.joining(","));

        return "[" + jsonArray + "]";
    }

    private static String toJsonPostResponse(PostResponse postResponse) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        json.append("\"postId\":");
        json.append(postResponse.postId());

        json.append(",\"nickname\":\"");
        json.append(postResponse.nickname());
        json.append("\"");

        json.append(",\"content\":\"");
        json.append(postResponse.content());
        json.append("\"");

        json.append(",\"imageName\":\"");
        json.append(postResponse.imageName());
        json.append("\"");

        json.append(",\"commentList\":");
        json.append(toJsonCommentListResponse(postResponse.commentList()));

        json.append("}");
        return json.toString();
    }

    private static String toJsonCommentListResponse(CommentListResponse commentListResponse) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        json.append("\"postId\":");
        json.append(commentListResponse.postId());

        json.append(",\"commentList\":");
        json.append(toJsonCommentResponseList(commentListResponse.commentList()));

        json.append(",\"commentCount\":");
        json.append(commentListResponse.commentCount());

        json.append("}");
        return json.toString();
    }

    private static String toJsonCommentResponseList(List<CommentResponse> commentResponses) {
        String jsonArray = commentResponses.stream()
                .map(JsonSerializer::toJsonCommentResponse)
                .collect(Collectors.joining(","));
        return "[" + jsonArray + "]";
    }

    private static String toJsonCommentResponse(CommentResponse commentResponse) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        json.append("\"commentId\":");
        json.append(commentResponse.commentId());

        json.append(",\"nickname\":\"");
        json.append(commentResponse.nickname());
        json.append("\"");

        json.append(",\"content\":\"");
        json.append(commentResponse.content());
        json.append("\"");

        json.append("}");
        return json.toString();
    }
}
