package sotd.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import sotd.spotify.persistence.SpotifyAccountRepository;

class SpotifyOperationalMetricsTest {

    @Test
    void gaugesReflectRepositoryState() {
        SpotifyAccountRepository repository = mock(SpotifyAccountRepository.class);
        when(repository.countAccountsByStatus("ACTIVE")).thenReturn(2L);
        when(repository.countAccountsByStatus("REAUTH_REQUIRED")).thenReturn(1L);
        when(repository.countAccountsByStatus("DISCONNECTED")).thenReturn(3L);
        when(repository.countActiveAccountsWithoutSuccessfulPoll()).thenReturn(1L);
        when(repository.findOldestSuccessfulPollAgeSeconds()).thenReturn(120.0);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        new SpotifyOperationalMetrics(meterRegistry, repository);

        assertThat(meterRegistry.get("sotd.spotify.accounts").tag("status", "active").gauge().value()).isEqualTo(2.0);
        assertThat(meterRegistry.get("sotd.spotify.accounts").tag("status", "reauth_required").gauge().value()).isEqualTo(1.0);
        assertThat(meterRegistry.get("sotd.spotify.accounts").tag("status", "disconnected").gauge().value()).isEqualTo(3.0);
        assertThat(meterRegistry.get("sotd.spotify.poll.active_without_successful_poll").gauge().value()).isEqualTo(1.0);
        assertThat(meterRegistry.get("sotd.spotify.poll.oldest_success_age.seconds").gauge().value()).isEqualTo(120.0);
    }

    @Test
    void recordsOperationalOutcomeMetrics() {
        SpotifyAccountRepository repository = mock(SpotifyAccountRepository.class);
        when(repository.countAccountsByStatus("ACTIVE")).thenReturn(0L);
        when(repository.countAccountsByStatus("REAUTH_REQUIRED")).thenReturn(0L);
        when(repository.countAccountsByStatus("DISCONNECTED")).thenReturn(0L);
        when(repository.countActiveAccountsWithoutSuccessfulPoll()).thenReturn(0L);
        when(repository.findOldestSuccessfulPollAgeSeconds()).thenReturn(null);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SpotifyOperationalMetrics metrics = new SpotifyOperationalMetrics(meterRegistry, repository);

        Timer.Sample callbackSample = metrics.startCallbackTimer();
        metrics.recordCallbackFailure(SpotifyCallbackStage.TOKEN_EXCHANGE, callbackSample);

        Timer.Sample refreshSample = metrics.startTokenRefreshTimer();
        metrics.recordTokenRefreshSuccess(refreshSample);

        Timer.Sample pollSample = metrics.startPollAccountTimer();
        metrics.recordPollAccountSuccess(pollSample, 4);

        assertThat(meterRegistry.get("sotd.spotify.callback.outcomes")
                .tag("outcome", "failure")
                .tag("stage", "token_exchange")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("sotd.spotify.token_refresh.outcomes")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("sotd.spotify.poll.account.outcomes")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("sotd.spotify.poll.inserted_events").summary().totalAmount()).isEqualTo(4.0);
    }
}
