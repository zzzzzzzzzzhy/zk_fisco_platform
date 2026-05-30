package com.wereen.competitionplatform.conig;

import com.wereen.competitionplatform.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security 配置
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF（使用 JWT 不需要）
                .csrf(AbstractHttpConfigurer::disable)
                // 启用 CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 配置授权规则
                .authorizeHttpRequests(auth -> auth
                        // 公开接口（注意：context-path已经是/api，所以这里不需要再加/api前缀）
                        .requestMatchers(
                                "/auth/**",
                                "/competitions/**",
                                "/leaderboards/**",
                                "/submissions/**",
                                "/registrations/**",
                                "/forum/posts/**",
                                "/forum/posts/*/comments/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/forum/**").permitAll()
                        // 公开访问的内容分享接口（只允许公开的读取操作）
                        .requestMatchers(HttpMethod.GET, "/content-shares").permitAll()
                        .requestMatchers(HttpMethod.GET, "/content-shares/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/content-shares/*/media").permitAll()
                        .requestMatchers(HttpMethod.GET, "/content-shares/*/polygon-sign-data").authenticated() // Polygon 签名数据需要登录
                        .requestMatchers(HttpMethod.POST, "/content-shares/*/polygon-proof").authenticated() // Polygon 存证提交需要登录
                        .requestMatchers(HttpMethod.POST, "/content-shares/*/consent").authenticated()
                        .requestMatchers("/content-shares/presigned-url").authenticated() // 需要登录
                        .requestMatchers(HttpMethod.POST, "/content-shares").authenticated() // 需要登录
                        // 管理员接口需要认证
                        .requestMatchers(HttpMethod.GET, "/content-shares/admin/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/content-shares/*/visibility").authenticated()
                        // 删除接口需要认证（由@RequireRole注解控制权限）
                        .requestMatchers(HttpMethod.DELETE, "/content-shares/**").authenticated()
                        // 允许打赏和置顶功能
                        .requestMatchers("/content-tips/**").permitAll()
                        .requestMatchers("/content-pins/**").permitAll()
                        // 允许代币相关接口
                        .requestMatchers("/forum-tokens/**").permitAll()
                        .requestMatchers("/forum/token/**").permitAll()
                        // 内容举报管理（管理员）
                        .requestMatchers("/content-reports/**").authenticated()
                        // 链上存证查询（公开，用于演示）
                        .requestMatchers("/chain/evidence/**").permitAll()
                        // 允许Gas监控相关接口（管理员功能）
                        .requestMatchers("/gas/**").permitAll()
                        .requestMatchers("/test/**").permitAll()
                        // 允许签到签名接口（需要公开以便前端调用）
                        .requestMatchers("/checkin/**").permitAll()
                        // 允许验证码相关接口（需要公开）
                        .requestMatchers("/captcha/**").permitAll()
                        // 允许DAO治理相关接口（公开访问）
                        .requestMatchers("/governance/**").permitAll()
                        // 允许 Rollup 批次查询
                        .requestMatchers("/rollup/**").permitAll()
                        // ZK 排名：查询公开（commit/prove 由 @RequireRole 保护）
                        .requestMatchers(HttpMethod.GET, "/zk/**").permitAll()
                        // 其他接口需要认证
                        .anyRequest().authenticated()
                )
                // 无状态会话（使用 JWT）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // 添加 JWT 过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS 配置
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
