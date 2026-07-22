package com.xiaomo.agent.tool.config;

import com.xiaomo.agent.common.entity.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/tools")
public class ToolConfigController {

    @Resource
    private ToolConfigService toolConfigService;

    @GetMapping
    public Result<Map<String, Boolean>> listTools() {
        return Result.success(toolConfigService.listAll());
    }

    @PutMapping("/{name}")
    public Result<Void> setEnabled(@PathVariable String name, @RequestParam boolean enabled) {
        toolConfigService.setEnabled(name, enabled);
        return Result.success();
    }
}
