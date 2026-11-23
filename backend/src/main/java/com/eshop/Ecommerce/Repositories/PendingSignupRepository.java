package com.eshop.Ecommerce.Repositories;

import com.eshop.Ecommerce.Model.PendingSignup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PendingSignupRepository extends JpaRepository<PendingSignup,Long> {
    Optional<PendingSignup> findByUsername(String username);
    Optional<PendingSignup> findByEmailIgnoreCase(String email);
}
