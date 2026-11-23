package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A fake mailbox cannot become an account without receiving the SMTP OTP. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties={"spring.mail.host=test.smtp.invalid","nothing.mail.from=no-reply@nothing.invalid"})
class EmailSignupVerificationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepository users;
 @Autowired PasswordEncoder encoder;
 @MockBean JavaMailSender mail;

 @Test
 void signupRequiresMailboxOtpBeforeAccountCanSignIn()throws Exception{
  String suffix=UUID.randomUUID().toString().substring(0,8);
  String username="otp"+suffix,password="SecurePass!2026";
  String email=username+"@example.net";
  String signup=json.writeValueAsString(Map.of("username",username,"email",email,"password",password));
  mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup))
   .andExpect(status().isAccepted());
  assertFalse(users.existsByUserName(username),"Unverified email must not create a customer");

  var captured=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
  verify(mail).send(captured.capture());
  String body=captured.getValue().getText();
  Matcher match=Pattern.compile("\\b[0-9]{6}\\b").matcher(body);
  assertTrue(match.find(),"The code must be delivered by the injected mail provider");
  String otp=match.group();

  mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("username",username,"password",password))))
   .andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("username",username,"code",otp))))
   .andExpect(status().isOk());
  var created=users.findByUserName(username).orElseThrow();
  assertTrue(encoder.matches(password,created.getPassword()));
  assertFalse(password.equals(created.getPassword()));
  mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("username",username,"password",password))))
   .andExpect(status().isOk()).andExpect(header().exists("Set-Cookie"));
 }
}
