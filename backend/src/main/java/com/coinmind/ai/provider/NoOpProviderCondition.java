package com.coinmind.ai.provider;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import java.util.Set;

/** Unknown/absent selection fails closed with exactly one provider. */
public class NoOpProviderCondition implements Condition {
    @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !Set.of("openai", "mock", "gemini").contains(
                context.getEnvironment().getProperty("coinmind.ai.provider", "").toLowerCase(java.util.Locale.ROOT));
    }
}
