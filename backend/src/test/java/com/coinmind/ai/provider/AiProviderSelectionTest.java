package com.coinmind.ai.provider;

import com.coinmind.ai.config.AiConfiguration;
import com.coinmind.ai.status.AiStatusService;
import com.coinmind.ai.usage.AiUsageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiProviderSelectionTest {
    @TempDir Path stateDirectory;
    private String previousStateDirectory;

    @BeforeEach void isolateDurableState() {
        previousStateDirectory = System.getProperty("coinmind.gemini.state-dir");
        System.setProperty("coinmind.gemini.state-dir", stateDirectory.toString());
    }

    @AfterEach void restoreStateDirectory() {
        if (previousStateDirectory == null) System.clearProperty("coinmind.gemini.state-dir");
        else System.setProperty("coinmind.gemini.state-dir", previousStateDirectory);
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfiguration.class, NoOpAiProvider.class, MockAiProvider.class,
                    OpenAiLunaProvider.class, GeminiAiProvider.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(AiUsageService.class, () -> mock(AiUsageService.class))
            .withBean(AiStatusService.class, () -> mock(AiStatusService.class))
            .withPropertyValues("coinmind.ai.base-url=https://api.openai.com");
    @Test void absentProviderHasOnlyNoOp() {
        runner.run(context -> assertThat(context).hasSingleBean(AiProvider.class).hasSingleBean(NoOpAiProvider.class));
    }
    @Test void explicitProviderSelection() {
        for (String selection : new String[]{"mock", "openai", "gemini", "none", "unknown", "GEMINI"}) {
            Class<?> expected = switch (selection) {
                case "mock" -> MockAiProvider.class;
                case "openai" -> OpenAiLunaProvider.class;
                case "gemini", "GEMINI" -> GeminiAiProvider.class;
                default -> NoOpAiProvider.class;
            };
            runner.withPropertyValues("coinmind.ai.provider=" + selection).run(context -> {
                assertThat(context).hasSingleBean(AiProvider.class);
                assertThat(context.getBean(AiProvider.class)).isInstanceOf(expected);
            });
        }
    }
    @Test void defaultGeminiModelAndDisabledOptIn() {
        runner.withPropertyValues("coinmind.ai.provider=gemini").run(context -> {
            assertThat(context.getBean(AiProvider.class).name()).isEqualTo("gemini-3.5-flash-lite");
            assertThat(context.getBean(com.coinmind.ai.config.GeminiProperties.class).newsEnabled()).isFalse();
        });
    }
}
