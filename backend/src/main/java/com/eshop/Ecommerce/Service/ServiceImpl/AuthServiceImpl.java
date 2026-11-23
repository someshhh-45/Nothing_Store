package com.eshop.Ecommerce.Service.ServiceImpl;


import com.eshop.Ecommerce.Model.AppRole;
import com.eshop.Ecommerce.Model.Role;
import com.eshop.Ecommerce.Model.User;
import com.eshop.Ecommerce.Payload.UserDTO;
import com.eshop.Ecommerce.Payload.UserResponse;
import com.eshop.Ecommerce.Repositories.RoleRepository;
import com.eshop.Ecommerce.Repositories.UserRepository;
import com.eshop.Ecommerce.Security.JWT.JwtUtils;
import com.eshop.Ecommerce.Security.Request.LoginRequest;
import com.eshop.Ecommerce.Security.Request.SignupRequest;
import com.eshop.Ecommerce.Security.Response.MessageResponse;
import com.eshop.Ecommerce.Security.Response.UserInfoResponse;
import com.eshop.Ecommerce.Security.Services.UserDetailsImpl;
import com.eshop.Ecommerce.Service.AuthService;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

//Note --
// Cookie + Session → Used in most traditional web apps (e.g., banking sites, e-commerce websites, Django/Express/Spring MVC apps).
//JWT → Used in mobile apps, SPAs (React, Flutter, Angular), and microservices where stateless auth is preferred.

@Service
@Transactional
public class AuthServiceImpl implements AuthService {


    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private com.eshop.Ecommerce.Security.Services.EmailSignupService emailSignup;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    ModelMapper modelMapper;

    @Override
    public ResponseEntity<?> login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());
        UserInfoResponse response = new UserInfoResponse(
                userDetails.getId(),
                userDetails.getUsername(),
                roles,
                userDetails.getEmail(),
                ""
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(response);
    }

    //sign in
    @Override
    public ResponseEntity<MessageResponse> register(SignupRequest signUpRequest) {
        // No username becomes a real customer until the mailbox OTP is verified.
        return emailSignup.signup(signUpRequest);
    }

    @Override
    public ResponseCookie logoutUser() {
        return jwtUtils.getCleanJwtCookie();
    }

    @Override
    public ResponseEntity<?> getUserDetail(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());
        UserInfoResponse response = new UserInfoResponse(
                userDetails.getId(),
                userDetails.getUsername(),
                roles,
                userDetails.getEmail()
        );
        return ResponseEntity.ok()
                .body(response);
    }

    @Override
    public Object getAllSellers(Pageable pageDetails) {
        Page<User> allUser = userRepository.findByRoleName(AppRole.ROLE_SELLER, pageDetails);
        List<UserDTO> userDTOS = allUser.getContent()
                .stream()
                .map((element) -> modelMapper.map(element, UserDTO.class))
                .toList();
        UserResponse response = new UserResponse();
        response.setContent(userDTOS);
        response.setPageSize(pageDetails.getPageSize());
        response.setPageNumber(pageDetails.getPageNumber());
        response.setTotalElements(allUser.getTotalElements());
        response.setTotalPages((long) allUser.getTotalPages());
        response.setLastPage(allUser.isLast());
        return response;
    }
}

