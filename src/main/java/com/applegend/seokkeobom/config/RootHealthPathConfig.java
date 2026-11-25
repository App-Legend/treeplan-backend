package com.applegend.seokkeobom.config;

import org.apache.catalina.Host;
import org.apache.catalina.valves.rewrite.RewriteValve;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * 앱은 /api 아래에서 돌지만, 클러스터 probe는 /common/health 를 부른다.
 * /common/health, /common/deploy-demo 두 주소만 /api 아래로 넘긴다. 다른 주소는 건드리지 않는다.
 */
@Configuration
public class RootHealthPathConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    static final String RULES = "RewriteRule ^/common/(health|deploy-demo)$ /api/common/$1 [L]";

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        // context 단계에서는 이미 /api 로 매핑이 끝나므로, 그 앞의 Host 단계에 붙인다.
        factory.addContextCustomizers(context -> {
            Host host = (Host) context.getParent();
            RewriteValve valve = new RewriteValve();
            // 규칙을 읽을 때 Host 정보가 필요하므로 먼저 붙인다
            host.getPipeline().addValve(valve);
            try {
                valve.setConfiguration(RULES);
            } catch (Exception e) {
                throw new IllegalStateException("rewrite rule", e);
            }
        });
    }
}
