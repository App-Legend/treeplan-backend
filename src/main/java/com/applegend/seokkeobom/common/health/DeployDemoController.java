package com.applegend.seokkeobom.common.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeployDemoController {
    @GetMapping("/common/deploy-demo")
    public Map<String, String> version() {
        String pod = System.getenv("HOSTNAME");
        return Map.of(
                "version", "team-v1",
                "pod", pod != null ? pod : "local");
    }
}
