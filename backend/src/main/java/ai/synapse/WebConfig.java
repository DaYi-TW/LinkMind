package ai.synapse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration public class WebConfig implements WebMvcConfigurer {
 @Value("${app.cors.allowed-origins:*}") private String allowedOrigins;
 @Override public void addCorsMappings(CorsRegistry registry){
  String[] origins=allowedOrigins.split(",");
  registry.addMapping("/api/**").allowedOriginPatterns(origins).allowedMethods("GET","POST","PUT","DELETE","OPTIONS");
 }
}
