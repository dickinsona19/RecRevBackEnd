package com.BossLiftingClub.BossLifting.Config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Custom naming strategy that preserves exact column names when explicitly specified
 * in @Column annotations, but applies snake_case transformation for implicit names.
 */
public class HibernateNamingConfig extends PhysicalNamingStrategyStandardImpl {
    
    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment context) {
        // If the identifier is already quoted or contains mixed case, preserve it exactly
        if (name.isQuoted() || containsMixedCase(name.getText())) {
            return Identifier.toIdentifier(name.getText(), true); // true = quoted
        }
        // Otherwise, apply default transformation (snake_case)
        return super.toPhysicalColumnName(name, context);
    }
    
    private boolean containsMixedCase(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // Check if it contains both uppercase and lowercase letters
        boolean hasUpper = false;
        boolean hasLower = false;
        for (char c : text.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            }
            if (hasUpper && hasLower) {
                return true;
            }
        }
        return false;
    }
}

