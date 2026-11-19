package com.example.classroom.service;

import com.example.classroom.model.Comment;
import com.example.classroom.service.CommentService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CommentService {

    // 使用内存存储，实际项目中应该使用数据库
    private final Map<String, Comment> commentStore = new ConcurrentHashMap<>();
    private final Map<String, List<String>> classroomCommentIndex = new ConcurrentHashMap<>();


    public Comment addComment(Comment comment) {
        // 如果ID为空，生成新ID
        if (comment.getId() == null || comment.getId().isEmpty()) {
            comment.setId("comment_" + System.currentTimeMillis() + "_" +
                    (comment.isTeacher() ? "teacher" : "student"));
        }

        // 确保有时间戳
        if (comment.getSendTime() == null) {
            comment.setSendTime(new Date());
        }

        // 保存评论
        commentStore.put(comment.getId(), comment);

        // 更新课堂索引
        String classroomId = comment.getClassroomId();
        classroomCommentIndex.computeIfAbsent(classroomId, k -> new ArrayList<>())
                .add(comment.getId());

        System.out.println("保存评论: " + comment);
        return comment;
    }


    public List<Comment> getCommentsByClassroomId(String classroomId) {
        List<String> commentIds = classroomCommentIndex.get(classroomId);
        if (commentIds == null || commentIds.isEmpty()) {
            return new ArrayList<>();
        }

        return commentIds.stream()
                .map(commentStore::get)
                .filter(Objects::nonNull)
                .sorted((c1, c2) -> c2.getSendTime().compareTo(c1.getSendTime())) // 按时间倒序
                .collect(Collectors.toList());
    }


    public Comment getCommentById(String commentId) {
        return commentStore.get(commentId);
    }


    public boolean deleteComment(String commentId) {
        Comment comment = commentStore.get(commentId);
        if (comment != null) {
            // 从主存储删除
            commentStore.remove(commentId);

            // 从课堂索引删除
            String classroomId = comment.getClassroomId();
            List<String> commentIds = classroomCommentIndex.get(classroomId);
            if (commentIds != null) {
                commentIds.remove(commentId);
                if (commentIds.isEmpty()) {
                    classroomCommentIndex.remove(classroomId);
                }
            }
            return true;
        }
        return false;
    }

    public int getCommentCountByClassroomId(String classroomId) {
        List<String> commentIds = classroomCommentIndex.get(classroomId);
        return commentIds != null ? commentIds.size() : 0;
    }


    public List<Comment> getRecentComments(String classroomId, int limit) {
        return getCommentsByClassroomId(classroomId).stream()
                .limit(limit)
                .collect(Collectors.toList());
    }


    public List<Comment> getTeacherComments(String classroomId) {
        return getCommentsByClassroomId(classroomId).stream()
                .filter(Comment::isTeacher)
                .collect(Collectors.toList());
    }


    public List<Comment> getStudentComments(String classroomId) {
        return getCommentsByClassroomId(classroomId).stream()
                .filter(comment -> !comment.isTeacher())
                .collect(Collectors.toList());
    }


    public int clearClassroomComments(String classroomId) {
        List<String> commentIds = classroomCommentIndex.get(classroomId);
        if (commentIds == null) {
            return 0;
        }

        int count = commentIds.size();

        // 删除所有评论
        for (String commentId : commentIds) {
            commentStore.remove(commentId);
        }

        // 删除课堂索引
        classroomCommentIndex.remove(classroomId);

        System.out.println("清除了课堂 " + classroomId + " 的 " + count + " 条评论");
        return count;
    }

    // 辅助方法：获取所有评论（用于调试）
    public List<Comment> getAllComments() {
        return new ArrayList<>(commentStore.values());
    }

    // 辅助方法：获取所有课堂的评论统计
    public Map<String, Integer> getCommentStats() {
        Map<String, Integer> stats = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : classroomCommentIndex.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }
}