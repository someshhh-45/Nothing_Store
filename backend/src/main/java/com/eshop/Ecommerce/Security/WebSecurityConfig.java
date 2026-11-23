package com.eshop.Ecommerce.Security;

import com.eshop.Ecommerce.Model.AppRole;
import com.eshop.Ecommerce.Model.Role;
import com.eshop.Ecommerce.Model.User;
import com.eshop.Ecommerce.Repositories.RoleRepository;
import com.eshop.Ecommerce.Repositories.UserRepository;
import com.eshop.Ecommerce.Security.JWT.AuthEntryPointJwt;
import com.eshop.Ecommerce.Security.JWT.AuthTokenFilter;
import com.eshop.Ecommerce.Security.Services.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Set;

@Configuration
@EnableWebSecurity
//@EnableMethodSecurity
public class WebSecurityConfig {
    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Bean
    public AuthTokenFilter authTokenFilterBean() {
        return new AuthTokenFilter();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProviderBean() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoderBean());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManagerBean(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth ->
                        auth.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/stripe/webhook").permitAll()
                                .requestMatchers("/api/store/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                                .requestMatchers("/v3/api-docs/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                                .requestMatchers("/h2-console/**").permitAll()
                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                .requestMatchers("/api/seller/**").hasAnyRole("ADMIN","SELLER")
                                // Preserve legacy routes but restrict all catalog mutations to administrators.
                                .requestMatchers(HttpMethod.POST, "/api/public/categories").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/public/categories/*").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/products/*/image").hasRole("ADMIN")
                                .requestMatchers("/api/public/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/search/products").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/checkout/stripe/config").permitAll()
                                .requestMatchers("/swagger-ui/**").permitAll()
                                .requestMatchers("/api/test/**").permitAll()
                                .requestMatchers("/images/**").permitAll()
                                .requestMatchers("/api/carts/**").authenticated()
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .anyRequest().authenticated()
                );

        http.authenticationProvider(authenticationProviderBean());

        http.addFilterBefore(authTokenFilterBean(), UsernamePasswordAuthenticationFilter.class);
        http.headers(headers -> headers.frameOptions(
                frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web -> web.ignoring().requestMatchers("/v2/api-docs",
                "/configuration/ui",
                "/swagger-resources/**",
                "/configuration/security",
                "/swagger-ui.html",
                "/webjars/**"));
    }
    @Bean
    public PasswordEncoder passwordEncoderBean() {
        return new BCryptPasswordEncoder();
    }
    @org.springframework.beans.factory.annotation.Value("${ADMIN_USERNAME:}")
    private String bootstrapAdminUsername;
    @org.springframework.beans.factory.annotation.Value("${ADMIN_PASSWORD:}")
    private String bootstrapAdminPassword;

    @Bean
    public CommandLineRunner initData(RoleRepository roleRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Retrieve or create roles
            Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                    .orElseGet(() -> {
                        Role newUserRole = new Role(AppRole.ROLE_USER);
                        return roleRepository.save(newUserRole);
                    });

            Role sellerRole = roleRepository.findByRoleName(AppRole.ROLE_SELLER)
                    .orElseGet(() -> {
                        Role newSellerRole = new Role(AppRole.ROLE_SELLER);
                        return roleRepository.save(newSellerRole);
                    });

            Role adminRole = roleRepository.findByRoleName(AppRole.ROLE_ADMIN)
                    .orElseGet(() -> {
                        Role newAdminRole = new Role(AppRole.ROLE_ADMIN);
                        return roleRepository.save(newAdminRole);
                    });

            Set<Role> userRoles = Set.of(userRole);
            Set<Role> sellerRoles = Set.of(sellerRole);
            Set<Role> adminRoles = Set.of(userRole, sellerRole, adminRole);

            // Bootstrap an administrator only when explicitly configured.
            // NEVER auto-promote a pre-existing user account by username.
            if(!bootstrapAdminUsername.isBlank() && !bootstrapAdminPassword.isBlank()){
                if(bootstrapAdminUsername.length()>20 || bootstrapAdminPassword.length()<12){
                    throw new IllegalStateException("ADMIN_USERNAME must be <=20 characters and ADMIN_PASSWORD >=12 characters");
                }
                if(userRepository.findByUserName(bootstrapAdminUsername).isEmpty()){
                    User admin=new User(bootstrapAdminUsername,
                        bootstrapAdminUsername+"@nothing-market.invalid",
                        passwordEncoder.encode(bootstrapAdminPassword));
                    admin.setRoles(adminRoles);
                    userRepository.save(admin);
                }
            }
        };
    }
}
