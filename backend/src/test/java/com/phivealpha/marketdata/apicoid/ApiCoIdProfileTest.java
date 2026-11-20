package com.phivealpha.marketdata.apicoid;

import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.marketdata.ApiCoIdProvider;
import com.phivealpha.marketdata.FixtureApiCoIdProvider;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApiCoIdProfileTest {
    @Test
    void activatesOnlyTheHttpProviderWithAnExplicitProfileAndBlocksTheNetworkProfileInCi() throws Exception {
        var resource = getClass().getResource("/phivealpha/api-co-id/calendar.json");
        var calendar = Path.of(resource.toURI());
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ApiCoIdConfiguration.class, FixtureApiCoIdProvider.class)
                .withPropertyValues(
                        "spring.config.name=phivealpha",
                        "spring.profiles.active=phivealpha-api-co-id",
                        "API_CO_ID_BASE_URL=https://use.api.co.id",
                        "API_CO_ID_KEY=offline-profile-test",
                        "API_CO_ID_REQUEST_BUDGET=1",
                        "API_CO_ID_MIN_REQUEST_INTERVAL=1s",
                        "API_CO_ID_CACHE_TTL=1s",
                        "API_CO_ID_MAX_PAGES=1",
                        "API_CO_ID_MAX_ROWS=3",
                        "API_CO_ID_EXPECTED_UNIVERSE=BBCA,BBRI",
                        "API_CO_ID_UNIVERSE_VERSION=offline-test-v1",
                        "API_CO_ID_CALENDAR_PATH=" + calendar,
                        "phivealpha.api-co-id.max-age.quote=3m")
                .run(context -> {
                    if ("true".equalsIgnoreCase(System.getenv("CI"))
                            || "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) {
                        assertThat(context.getStartupFailure())
                                .hasRootCauseMessage("The API.co.id network profile is disabled in CI");
                    } else {
                        assertThat(context).hasSingleBean(ApiCoIdProvider.class)
                                .hasSingleBean(ApiCoIdHttpProvider.class).doesNotHaveBean(FixtureApiCoIdProvider.class);
                        assertThat(context.getBean(ApiCoIdHttpProvider.class).remainingRequestBudget()).isEqualTo(1);
                        assertThat(context.getBean(ApiCoIdSettings.class).expectedUniverse()).containsExactlyInAnyOrder(
                                "BBCA", "BBRI");
                        assertThat(context.getBean(ApiCoIdSettings.class).maxAge()).containsKey(ApiCoIdEndpoint.QUOTE);
                    }
                });
    }
}
