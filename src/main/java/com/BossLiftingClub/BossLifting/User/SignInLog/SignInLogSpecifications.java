package com.BossLiftingClub.BossLifting.User.SignInLog;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class SignInLogSpecifications {
    public static Specification<SignInLog> withDateFilters(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            // Eagerly fetch user to match your join
            root.fetch("user", JoinType.LEFT);

            var predicates = cb.conjunction(); // Starts as true

            if (start != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("signInTime"), start));
            }
            if (end != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("signInTime"), end));
            }

            return predicates;
        };
    }
}