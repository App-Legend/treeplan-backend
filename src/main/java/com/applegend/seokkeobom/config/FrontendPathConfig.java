package com.applegend.seokkeobom.config;

import org.apache.catalina.Host;
import org.apache.catalina.valves.rewrite.RewriteValve;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * dev 배포 때 프론트 빌드 결과를 이미지의 static/ 에 넣는다. 스프링은 그 파일을 /api 아래에서 내주므로,
 * 서버 루트의 화면 주소(/, /index.html, /assets/**, /fonts/**)만 /api 아래로 넘긴다. 다른 주소는 건드리지 않는다.
 */
@Configuration
public class FrontendPathConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    static final String RULES = String.join("\n",
            "RewriteRule ^/$ /api/index.html [L]",
            "RewriteRule ^/(index\\.html|assets/.+|fonts/.+)$ /api/$1 [L]");

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        // RootHealthPathConfig 와 같은 방식: context(/api) 매핑 전 단계인 Host 에 붙인다
        factory.addContextCustomizers(context -> {
            Host host = (Host) context.getParent();
            RewriteValve valve = new RewriteValve();
            host.getPipeline().addValve(valve);
            try {
                valve.setConfiguration(RULES);
            } catch (Exception e) {
                throw new IllegalStateException("frontend rewrite rule", e);
            }
        });
    }
}
