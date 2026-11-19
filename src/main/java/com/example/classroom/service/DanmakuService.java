package com.example.classroom.service;

import com.example.classroom.model.Danmaku;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DanmakuService {

    // 课堂弹幕存储
    private Map<String, List<Danmaku>> classroomDanmaku = new HashMap<>();
    private Map<String, Map<String, Integer>> keywordStats = new HashMap<>();
    private Map<String, Map<String, Object>> danmakuMetadata = new HashMap<>();

    // 视频弹幕存储
    private Map<Integer, List<Danmaku>> videoDanmaku = new ConcurrentHashMap<>();
    private AtomicInteger danmakuIdGenerator = new AtomicInteger(1);

    // 添加课堂弹幕
    public Danmaku addDanmaku(Danmaku danmaku) {
        String classroomId = danmaku.getClassroomId();

        // 生成ID如果为空
        if (danmaku.getId() == null) {
            danmaku.setId(danmakuIdGenerator.getAndIncrement());
        }

        // 设置发送时间如果为空
        if (danmaku.getSendTime() == null) {
            danmaku.setSendTime(new Date());
        }

        // 设置颜色如果为空
        if (danmaku.getColor() == null || danmaku.getColor().isEmpty()) {
            danmaku.setColor("#FFFFFF");
        }

        // 保存弹幕
        classroomDanmaku
                .computeIfAbsent(classroomId, k -> new ArrayList<>())
                .add(danmaku);

        // 更新关键词统计
        if (danmaku.getKeywords() != null) {
            Map<String, Integer> stats = keywordStats
                    .computeIfAbsent(classroomId, k -> new HashMap<>());

            for (String keyword : danmaku.getKeywords()) {
                stats.put(keyword, stats.getOrDefault(keyword, 0) + 1);
            }
        }

        // 更新元数据
        Map<String, Object> metadata = danmakuMetadata
                .computeIfAbsent(classroomId, k -> new HashMap<>());
        metadata.put("lastUpdate", new Date());
        metadata.put("totalCount", getTotalDanmakuCount(classroomId));

        return danmaku;
    }

    // 添加视频弹幕
    public Danmaku addVideoDanmaku(Danmaku danmaku) {
        Integer vid = danmaku.getVid();

        // 生成ID
        danmaku.setId(danmakuIdGenerator.getAndIncrement());

        // 设置创建时间
        danmaku.setCreateDate(new Date());

        // 设置默认值
        if (danmaku.getFontsize() == null) {
            danmaku.setFontsize(25);
        }
        if (danmaku.getMode() == null) {
            danmaku.setMode(1); // 1滚动
        }
        if (danmaku.getColor() == null || danmaku.getColor().isEmpty()) {
            danmaku.setColor("#FFFFFF");
        }
        if (danmaku.getState() == null) {
            danmaku.setState(1); // 1正常
        }

        // 保存弹幕
        videoDanmaku
                .computeIfAbsent(vid, k -> new ArrayList<>())
                .add(danmaku);

        return danmaku;
    }

    // 获取课堂最近弹幕
    public List<Danmaku> getRecentDanmaku(String classroomId, int limit) {
        List<Danmaku> danmakuList = classroomDanmaku.getOrDefault(classroomId, new ArrayList<>());

        // 按时间倒序排序
        danmakuList.sort((a, b) -> b.getSendTime().compareTo(a.getSendTime()));

        if (limit > 0 && danmakuList.size() > limit) {
            return new ArrayList<>(danmakuList.subList(0, limit));
        }
        return new ArrayList<>(danmakuList);
    }

    // 获取视频弹幕（按时间范围）
    public List<Danmaku> getVideoDanmaku(Integer vid, double startTime, double endTime) {
        List<Danmaku> danmakuList = videoDanmaku.getOrDefault(vid, new ArrayList<>());
        List<Danmaku> result = new ArrayList<>();

        for (Danmaku danmaku : danmakuList) {
            if (danmaku.getTimePoint() >= startTime && danmaku.getTimePoint() <= endTime) {
                result.add(danmaku);
            }
        }

        // 按时间点正序排序
        result.sort(Comparator.comparing(Danmaku::getTimePoint));
        return result;
    }

    // 获取视频所有弹幕
    public List<Danmaku> getAllVideoDanmaku(Integer vid) {
        List<Danmaku> danmakuList = videoDanmaku.getOrDefault(vid, new ArrayList<>());
        // 按时间点正序排序
        danmakuList.sort(Comparator.comparing(Danmaku::getTimePoint));
        return new ArrayList<>(danmakuList);
    }

    // 新增方法：获取所有课堂弹幕
    public List<Danmaku> getAllDanmaku(String classroomId) {
        List<Danmaku> allDanmaku = classroomDanmaku.getOrDefault(classroomId, new ArrayList<>());
        // 按时间正序排序
        allDanmaku.sort(Comparator.comparing(Danmaku::getSendTime));
        return new ArrayList<>(allDanmaku);
    }

    // 新增方法：获取热门弹幕（简单的实现，按时间排序，返回指定数量）
    public List<Danmaku> getPopularDanmaku(String classroomId, int limit) {
        List<Danmaku> allDanmaku = getAllDanmaku(classroomId);
        // 这里可以添加更复杂的算法，比如根据点赞数、回复数等
        // 目前简单的返回最新的弹幕
        Collections.reverse(allDanmaku);
        if (limit > 0 && allDanmaku.size() > limit) {
            return new ArrayList<>(allDanmaku.subList(0, limit));
        }
        return new ArrayList<>(allDanmaku);
    }

    // 获取课堂弹幕统计
    public Map<String, Object> getDanmakuStats(String classroomId) {
        List<Danmaku> recentDanmaku = getRecentDanmaku(classroomId, 100);
        Map<String, Integer> keywordStats = getKeywordStats(classroomId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDanmaku", getTotalDanmakuCount(classroomId));
        stats.put("teacherDanmakuCount", getTeacherDanmakuCount(classroomId));
        stats.put("studentDanmakuCount", getStudentDanmakuCount(classroomId));
        stats.put("recentDanmaku", recentDanmaku);
        stats.put("keywordStats", keywordStats);

        // 计算活跃度
        Date oneMinuteAgo = new Date(System.currentTimeMillis() - 60000);

        long activeCount = recentDanmaku.stream()
                .filter(d -> d.getSendTime() != null && d.getSendTime().after(oneMinuteAgo))
                .count();

        stats.put("activityLevel", activeCount > 10 ? "高" : activeCount > 5 ? "中" : "低");
        stats.put("activeCount", activeCount);

        return stats;
    }

    // 获取视频弹幕统计
    public Map<String, Object> getVideoDanmakuStats(Integer vid) {
        List<Danmaku> danmakuList = videoDanmaku.getOrDefault(vid, new ArrayList<>());

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDanmaku", danmakuList.size());
        stats.put("recentDanmaku", getRecentVideoDanmaku(vid, 50));

        return stats;
    }

    // 获取视频最近弹幕
    public List<Danmaku> getRecentVideoDanmaku(Integer vid, int limit) {
        List<Danmaku> danmakuList = videoDanmaku.getOrDefault(vid, new ArrayList<>());

        // 按时间倒序排序
        danmakuList.sort((a, b) -> b.getCreateDate().compareTo(a.getCreateDate()));

        if (limit > 0 && danmakuList.size() > limit) {
            return new ArrayList<>(danmakuList.subList(0, limit));
        }
        return new ArrayList<>(danmakuList);
    }

    // 删除视频弹幕
    public boolean deleteVideoDanmaku(Integer vid, Integer danmakuId) {
        List<Danmaku> danmakuList = videoDanmaku.get(vid);
        if (danmakuList != null) {
            return danmakuList.removeIf(d -> d.getId().equals(danmakuId));
        }
        return false;
    }

    // 获取关键词统计
    public Map<String, Integer> getKeywordStats(String classroomId) {
        return keywordStats.getOrDefault(classroomId, new HashMap<>());
    }

    // 辅助方法
    private int getTotalDanmakuCount(String classroomId) {
        return classroomDanmaku.getOrDefault(classroomId, new ArrayList<>()).size();
    }

    private int getTeacherDanmakuCount(String classroomId) {
        return (int) classroomDanmaku.getOrDefault(classroomId, new ArrayList<>())
                .stream()
                .filter(Danmaku::isTeacher)
                .count();
    }

    private int getStudentDanmakuCount(String classroomId) {
        return (int) classroomDanmaku.getOrDefault(classroomId, new ArrayList<>())
                .stream()
                .filter(d -> !d.isTeacher())
                .count();
    }
}
