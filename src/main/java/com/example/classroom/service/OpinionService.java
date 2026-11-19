package com.example.classroom.service;

import com.example.classroom.model.Opinion;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class OpinionService {

    private final Map<String, Opinion> opinionStore = new ConcurrentHashMap<>();
    private final Map<String, List<String>> classroomOpinionIndex = new ConcurrentHashMap<>();

    public Opinion addOpinion(Opinion opinion) {
        if (opinion.getId() == null || opinion.getId().isEmpty()) {
            opinion.setId("opinion_" + System.currentTimeMillis() + "_" + opinion.getStudentId());
        }

        if (opinion.getSendTime() == null) {
            opinion.setSendTime(new Date());
        }

        opinionStore.put(opinion.getId(), opinion);

        String classroomId = opinion.getClassroomId();
        classroomOpinionIndex.computeIfAbsent(classroomId, k -> new ArrayList<>())
                .add(opinion.getId());

        System.out.println("保存观点: " + opinion);
        return opinion;
    }

    public List<Opinion> getOpinionsByClassroomId(String classroomId) {
        List<String> opinionIds = classroomOpinionIndex.get(classroomId);
        if (opinionIds == null || opinionIds.isEmpty()) {
            return new ArrayList<>();
        }

        return opinionIds.stream()
                .map(opinionStore::get)
                .filter(Objects::nonNull)
                .sorted((o1, o2) -> o2.getSendTime().compareTo(o1.getSendTime()))
                .collect(Collectors.toList());
    }

    public Opinion getOpinionById(String opinionId) {
        return opinionStore.get(opinionId);
    }

    public List<Opinion> getOpinionsByIds(List<String> opinionIds) {
        if (opinionIds == null || opinionIds.isEmpty()) {
            return new ArrayList<>();
        }
        return opinionIds.stream()
                .map(opinionStore::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public boolean deleteOpinion(String opinionId) {
        Opinion opinion = opinionStore.get(opinionId);
        if (opinion != null) {
            opinionStore.remove(opinionId);

            String classroomId = opinion.getClassroomId();
            List<String> opinionIds = classroomOpinionIndex.get(classroomId);
            if (opinionIds != null) {
                opinionIds.remove(opinionId);
                if (opinionIds.isEmpty()) {
                    classroomOpinionIndex.remove(classroomId);
                }
            }
            return true;
        }
        return false;
    }

    public int getOpinionCountByClassroomId(String classroomId) {
        List<String> opinionIds = classroomOpinionIndex.get(classroomId);
        return opinionIds != null ? opinionIds.size() : 0;
    }

    public String exportOpinionsAsTxt(List<Opinion> opinions) {
        StringBuilder sb = new StringBuilder();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sb.append("========================================\n");
        sb.append("         学生观点汇总\n");
        sb.append("         导出时间: ").append(sdf.format(new Date())).append("\n");
        sb.append("         观点数量: ").append(opinions.size()).append("\n");
        sb.append("========================================\n\n");

        for (int i = 0; i < opinions.size(); i++) {
            Opinion opinion = opinions.get(i);
            sb.append("----------------------------------------\n");
            sb.append("【第 ").append(i + 1).append(" 条观点】\n");
            sb.append("----------------------------------------\n");
            sb.append("标题:   ").append(opinion.getTitle() != null ? opinion.getTitle() : "无标题").append("\n");
            sb.append("作者:   ").append(opinion.getDisplayName()).append("\n");
            sb.append("时间:   ").append(sdf.format(opinion.getSendTime())).append("\n");
            sb.append("课堂ID: ").append(opinion.getClassroomId()).append("\n");
            sb.append("内容:\n");
            sb.append("  ").append(opinion.getContent() != null ? opinion.getContent() : "").append("\n\n");
        }

        sb.append("========================================\n");
        sb.append("         观点汇总结束\n");
        sb.append("========================================\n");

        return sb.toString();
    }

    public int clearClassroomOpinions(String classroomId) {
        List<String> opinionIds = classroomOpinionIndex.get(classroomId);
        if (opinionIds == null) {
            return 0;
        }

        int count = opinionIds.size();

        for (String opinionId : opinionIds) {
            opinionStore.remove(opinionId);
        }

        classroomOpinionIndex.remove(classroomId);

        System.out.println("清除了课堂 " + classroomId + " 的 " + count + " 条观点");
        return count;
    }

    public List<Opinion> getAllOpinions() {
        return new ArrayList<>(opinionStore.values());
    }
}
