package com.eshop.Ecommerce.Security.Services;

import com.eshop.Ecommerce.Model.*;
import com.eshop.Ecommerce.Repositories.*;
import com.eshop.Ecommerce.Security.Request.SignupRequest;
import com.eshop.Ecommerce.Security.Response.MessageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Signup verification is fail-closed. No configured mail provider means no new customer accounts.
 * No OTPs or plaintext passwords are retained or printed to logs.
 */
@Service
public class EmailSignupService {
    private final PendingSignupRepository pending;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> senders;
    private final SecureRandom random=new SecureRandom();
    @Value("${spring.mail.host:}") private String mailHost;
    @Value("${nothing.mail.provider:smtp}") private String mailProvider;
    @Value("${nothing.mail.from:}") private String from;

    public EmailSignupService(PendingSignupRepository pending,UserRepository users,
            RoleRepository roles,PasswordEncoder encoder,ObjectProvider<JavaMailSender> senders){
        this.pending=pending;this.users=users;this.roles=roles;
        this.encoder=encoder;this.senders=senders;
    }

    private void sendCode(String to,String code) {
        JavaMailSender sender=senders.getIfAvailable();
        if((mailHost==null||mailHost.isBlank())&&!"gmail-api".equalsIgnoreCase(mailProvider)
           ||sender==null||from==null||from.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Signup requires configured SMTP or Gmail API delivery and EMAIL_FROM.");
        SimpleMailMessage mail=new SimpleMailMessage();
        mail.setTo(to);mail.setFrom(from);
        mail.setSubject("NOTHING Market - verify your email address");
        mail.setText("Your NOTHING Market verification code is "+code+
            ". It expires in 10 minutes. If you did not request this, ignore this email.");
        try{sender.send(mail);}
        catch(org.springframework.mail.MailException ex){
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Verification email could not be delivered. Check your email provider configuration.");
        }
    }
    private String code(){return String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));}
    public record VerifyRequest(@NotBlank String username,@NotBlank @Pattern(regexp="[0-9]{6}") String code){}
    public record ResendRequest(@NotBlank String username){}

    @Transactional
    public ResponseEntity<MessageResponse> signup(SignupRequest req){
        String username=req.getUsername().trim();
        String email=req.getEmail().trim().toLowerCase(Locale.ROOT);
        if(users.existsByUserName(username)||users.existsByEmail(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Username or email already registered");
        Instant now=Instant.now();
        PendingSignup record=pending.findByUsername(username).orElse(null);
        if(record!=null && !record.getEmail().equalsIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Username has a pending registration");
        PendingSignup emailPending=pending.findByEmailIgnoreCase(email).orElse(null);
        if(emailPending!=null && (record==null || !emailPending.getId().equals(record.getId())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Email has a pending registration");
        if(record!=null && now.isBefore(record.getResendNotBefore()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please wait before requesting another verification code");
        if(record==null)record=new PendingSignup();
        record.setUsername(username);record.setEmail(email);
        record.setPasswordHash(encoder.encode(req.getPassword()));
        String otp=code();
        record.setCodeHash(encoder.encode(otp));
        record.setExpiresAt(now.plus(10,ChronoUnit.MINUTES));
        record.setResendNotBefore(now.plus(60,ChronoUnit.SECONDS));
        record.setFailedAttempts(0);
        sendCode(email,otp);
        pending.save(record);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new MessageResponse("Verification code sent. Check your email to activate your account."));
    }

    @Transactional
    public ResponseEntity<MessageResponse> verify(@Valid VerifyRequest req){
        PendingSignup record=pending.findByUsername(req.username().trim()).orElseThrow(()->
            new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid or expired code"));
        if(Instant.now().isAfter(record.getExpiresAt()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid or expired code");
        if(record.getFailedAttempts()>=5)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many verification attempts. Request a new code.");
        if(!encoder.matches(req.code(),record.getCodeHash())){
            record.setFailedAttempts(record.getFailedAttempts()+1);
            pending.save(record);
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid verification code"));
        }
        if(users.existsByUserName(record.getUsername())||users.existsByEmail(record.getEmail()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Account already exists");
        Role role=roles.findByRoleName(AppRole.ROLE_USER)
            .orElseThrow(()->new IllegalStateException("Customer role not initialized"));
        User user=new User(record.getUsername(),record.getEmail(),record.getPasswordHash());
        user.setRoles(Set.of(role));
        users.save(user);
        pending.delete(record);
        return ResponseEntity.ok(new MessageResponse("Email verified. Your account is ready."));
    }

    @Transactional
    public ResponseEntity<MessageResponse> resend(ResendRequest req){
        // Never reveal whether a username has a pending email registration.
        PendingSignup record=pending.findByUsername(req.username().trim()).orElse(null);
        if(record==null)return ResponseEntity.accepted()
            .body(new MessageResponse("If a verification is pending, a new code will be sent when allowed."));
        Instant now=Instant.now();
        if(now.isBefore(record.getResendNotBefore()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please wait 60 seconds before requesting another code");
        String otp=code();
        record.setCodeHash(encoder.encode(otp));
        record.setExpiresAt(now.plus(10,ChronoUnit.MINUTES));
        record.setResendNotBefore(now.plus(60,ChronoUnit.SECONDS));
        record.setFailedAttempts(0);
        sendCode(record.getEmail(),otp);
        pending.save(record);
        return ResponseEntity.accepted().body(new MessageResponse("If a verification is pending, a new code has been sent."));
    }
}
