package com.eshop.Ecommerce.Repositories;
import com.eshop.Ecommerce.Model.StripeCheckoutAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface StripeCheckoutAttemptRepository extends JpaRepository<StripeCheckoutAttempt,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select s from StripeCheckoutAttempt s where s.id = :id")
 Optional<StripeCheckoutAttempt> lockById(String id);
 Optional<StripeCheckoutAttempt> findBySessionIdAndEmailIgnoreCase(String sessionId,String email);
}
