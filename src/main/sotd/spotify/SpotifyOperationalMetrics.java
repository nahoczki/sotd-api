package sotd.spotify;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import org.springframework.stereotype.Component;
import sotd.spotify.persistence.SpotifyAccountRepository;

/**
 * Centralizes custom Micrometer instrumentation for Spotify auth, token refresh, and polling flows.
 */
@Component
public class SpotifyOperationalMetrics {

    private final MeterRegistry meterRegistry;
    private final SpotifyAccountRepository spotifyAccountRepository;
    private final DistributionSummary pollInsertedEventsSummary;

    public SpotifyOperationalMetrics(MeterRegistry meterRegistry, SpotifyAccountRepository spotifyAccountRepository) {
        this.meterRegistry = meterRegistry;
        this.spotifyAccountRepository = spotifyAccountRepository;
        this.pollInsertedEventsSummary = DistributionSummary.builder("sotd.spotify.poll.inserted_events")
                .description("Number of playback events inserted during successful recently-played polls.")
                .baseUnit("events")
                .register(meterRegistry);

        registerAccountGauges();
    }

    public Timer.Sample startCallbackTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordCallbackSuccess(Timer.Sample sample) {
        recordOutcome(sample, "sotd.spotify.callback", Tags.of("outcome", "success"));
    }

    public void recordCallbackFailure(SpotifyCallbackStage stage, Timer.Sample sample) {
        recordOutcome(
                sample,
                "sotd.spotify.callback",
                Tags.of("outcome", "failure", "stage", stage.name().toLowerCase(Locale.ROOT))
        );
    }

    public Timer.Sample startTokenRefreshTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordTokenRefreshSuccess(Timer.Sample sample) {
        recordOutcome(sample, "sotd.spotify.token_refresh", Tags.of("outcome", "success"));
    }

    public void recordTokenRefreshReauthRequired(Timer.Sample sample) {
        recordOutcome(sample, "sotd.spotify.token_refresh", Tags.of("outcome", "reauth_required"));
    }

    public void recordTokenRefreshInactive(Timer.Sample sample) {
        recordOutcome(sample, "sotd.spotify.token_refresh", Tags.of("outcome", "inactive"));
    }

    public void recordTokenRefreshFailure(Timer.Sample sample) {
        recordOutcome(sample, "sotd.spotify.token_refresh", Tags.of("outcome", "failure"));
    }

    public Timer.Sample startPollAccountTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordPollAccountSuccess(Timer.Sample sample, int insertedEvents) {
        pollInsertedEventsSummary.record(insertedEvents);
        recordOutcome(sample, "sotd.spotify.poll.account", Tags.of("outcome", "success"));
    }

    public void recordPollAccountRateLimited(Timer.Sample sample) {
        recordOutcome(sample, "sotd.spotify.poll.account", Tags.of("outcome", "rate_limited"));
    }

    public void recordPollAccountUnauthorized(Timer.Sample sample) {
        recordOutcome(sample, "sotd.spotify.poll.account", Tags.of("outcome", "unauthorized"));
    }

    public void recordPollAccountReauthRequired(Timer.Sample sample) {
        recordOutcome(sample, "sotd.spotify.poll.account", Tags.of("outcome", "reauth_required"));
    }

    public void recordPollAccountFailure(Timer.Sample sample) {
        recordOutcome(sample, "sotd.spotify.poll.account", Tags.of("outcome", "failure"));
    }

    private void registerAccountGauges() {
        registerAccountStatusGauge("ACTIVE", "active");
        registerAccountStatusGauge("REAUTH_REQUIRED", "reauth_required");
        registerAccountStatusGauge("DISCONNECTED", "disconnected");

        Gauge.builder(
                        "sotd.spotify.poll.active_without_successful_poll",
                        this,
                        metrics -> metrics.safeCountActiveAccountsWithoutSuccessfulPoll()
                )
                .description("Active Spotify accounts that have not completed a successful poll yet.")
                .baseUnit("accounts")
                .register(meterRegistry);

        Gauge.builder(
                        "sotd.spotify.poll.oldest_success_age.seconds",
                        this,
                        metrics -> metrics.safeOldestSuccessfulPollAgeSeconds()
                )
                .description("Age in seconds of the oldest successful poll across active Spotify accounts.")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    private void registerAccountStatusGauge(String status, String tagValue) {
        Gauge.builder(
                        "sotd.spotify.accounts",
                        this,
                        metrics -> metrics.safeCountAccountsByStatus(status)
                )
                .description("Spotify account counts by lifecycle status.")
                .baseUnit("accounts")
                .tag("status", tagValue)
                .register(meterRegistry);
    }

    private void recordOutcome(Timer.Sample sample, String meterPrefix, Tags tags) {
        Counter.builder(meterPrefix + ".outcomes")
                .description("Outcome counter for " + meterPrefix + ".")
                .tags(tags)
                .register(meterRegistry)
                .increment();

        sample.stop(Timer.builder(meterPrefix + ".duration")
                .description("Duration timer for " + meterPrefix + ".")
                .tags(tags)
                .register(meterRegistry));
    }

    private double safeCountAccountsByStatus(String status) {
        try {
            return spotifyAccountRepository.countAccountsByStatus(status);
        }
        catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private double safeCountActiveAccountsWithoutSuccessfulPoll() {
        try {
            return spotifyAccountRepository.countActiveAccountsWithoutSuccessfulPoll();
        }
        catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private double safeOldestSuccessfulPollAgeSeconds() {
        try {
            Double ageSeconds = spotifyAccountRepository.findOldestSuccessfulPollAgeSeconds();
            return ageSeconds != null ? ageSeconds : Double.NaN;
        }
        catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }
}
