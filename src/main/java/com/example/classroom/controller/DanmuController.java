package com.example.classroom.controller;

import com.example.classroom.model.Danmaku;
import com.example.classroom.service.DanmakuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/danmu")
public class DanmuController {

    @Autowired
    private DanmakuService danmakuService;

    // 获取视频弹幕列表（按时间范围）
    @GetMapping("/list/{vid}")
    @ResponseBody
    public List<Danmaku> getDanmakuList(@PathVariable Integer vid,
                                        @RequestParam(required = false, defaultValue = "0") double startTime,
                                        @RequestParam(required = false, defaultValue = "3600") double endTime) {
        return danmakuService.getVideoDanmaku(vid, startTime, endTime);
    }

    // 获取视频所有弹幕
    @GetMapping("/all/{vid}")
    @ResponseBody
    public List<Danmaku> getAllDanmaku(@PathVariable Integer vid) {
        return danmakuService.getAllVideoDanmaku(vid);
    }

    // 获取视频弹幕统计
    @GetMapping("/stats/{vid}")
    @ResponseBody
    public Map<String, Object> getDanmakuStats(@PathVariable Integer vid) {
        return danmakuService.getVideoDanmakuStats(vid);
    }

    // 删除视频弹幕
    @DeleteMapping("/delete/{vid}/{id}")
    @ResponseBody
    public String deleteDanmaku(@PathVariable Integer vid, @PathVariable Integer id) {
        boolean success = danmakuService.deleteVideoDanmaku(vid, id);
        return "{\"success\": " + success + "}";
    }

    // 添加视频弹幕（HTTP方式，备用）
    @PostMapping("/add")
    @ResponseBody
    public Danmaku addDanmaku(@RequestBody Danmaku danmaku) {
        return danmakuService.addVideoDanmaku(danmaku);
    }

    // 获取视频最近弹幕
    @GetMapping("/recent/{vid}")
    @ResponseBody
    public List<Danmaku> getRecentDanmaku(@PathVariable Integer vid,
                                          @RequestParam(required = false, defaultValue = "50") int limit) {
        return danmakuService.getRecentVideoDanmaku(vid, limit);
    }
}
