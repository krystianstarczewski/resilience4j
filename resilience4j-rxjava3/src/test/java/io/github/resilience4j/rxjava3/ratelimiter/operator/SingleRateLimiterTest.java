package io.github.resilience4j.rxjava3.ratelimiter.operator;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.rxjava3.ratelimiter.operator.OverloadException.SpecificOverloadException;
import io.github.resilience4j.rxjava3.ratelimiter.operator.ResponseWithPotentialOverload.SpecificResponseWithPotentialOverload;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.github.resilience4j.core.ResultUtils.isFailedAndThrown;
import static io.github.resilience4j.core.ResultUtils.isSuccessfulAndReturned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * Unit test for {@link SingleRateLimiter}.
 */
public class SingleRateLimiterTest {

    @Test
    public void shouldEmitEvent() {
        RateLimiter rateLimiter = mock(RateLimiter.class, RETURNS_DEEP_STUBS);
        given(rateLimiter.reservePermission()).willReturn(Duration.ofSeconds(0).toNanos());

        Single.just(1)
            .compose(RateLimiterOperator.of(rateLimiter))
            .test()
            .assertResult(1);
    }

    @Test
    public void shouldDelaySubscription() {
        RateLimiter rateLimiter = mock(RateLimiter.class, RETURNS_DEEP_STUBS);
        given(rateLimiter.reservePermission()).willReturn(Duration.ofSeconds(1).toNanos());

        Single.just(1)
            .compose(RateLimiterOperator.of(rateLimiter))
            .test()
            .awaitDone(2, TimeUnit.SECONDS);
    }

    @Test
    public void shouldPropagateError() {
        RateLimiter rateLimiter = mock(RateLimiter.class, RETURNS_DEEP_STUBS);
        given(rateLimiter.reservePermission()).willReturn(Duration.ofSeconds(0).toNanos());

        Single.error(new IOException("BAM!"))
            .compose(RateLimiterOperator.of(rateLimiter))
            .test()
            .assertError(IOException.class)
            .assertNotComplete();
    }

    @Test
    public void shouldEmitErrorWithRequestNotPermittedException() {
        RateLimiter rateLimiter = mock(RateLimiter.class, RETURNS_DEEP_STUBS);
        given(rateLimiter.reservePermission()).willReturn(-1L);

        Single.just(1)
            .compose(RateLimiterOperator.of(rateLimiter))
            .test()
            .assertError(RequestNotPermitted.class)
            .assertNotComplete();
    }

    @Test
    public void shouldDrainRateLimiterInConditionMetOnFailedCall() {
        RateLimiter rateLimiter = RateLimiter.of("someLimiter", RateLimiterConfig.custom()
            .limitForPeriod(5)
            .limitRefreshPeriod(Duration.ofHours(1))
            .drainPermissionsOnResult(
                callsResult -> isFailedAndThrown(callsResult, OverloadException.class))
            .build());

        Single.error(new SpecificOverloadException())
            .compose(RateLimiterOperator.of(rateLimiter))
            .test()
            .assertError(SpecificOverloadException.class)
            .awaitDone(1, TimeUnit.SECONDS);
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isZero();
    }

    @Test
    public void shouldDrainRateLimiterInConditionMetOnSuccessfulCall() {
        RateLimiter rateLimiter = RateLimiter.of("someLimiter", RateLimiterConfig.custom()
            .limitForPeriod(5)
            .limitRefreshPeriod(Duration.ofHours(1))
            .drainPermissionsOnResult(
                callsResult -> isSuccessfulAndReturned(
                    callsResult,
                    ResponseWithPotentialOverload.class,
                    ResponseWithPotentialOverload::isOverload))
            .build());
        SpecificResponseWithPotentialOverload response = new SpecificResponseWithPotentialOverload(true);

        Single.just(response)
            .compose(RateLimiterOperator.of(rateLimiter))
            .test()
            .assertValue(response)
            .assertComplete();
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isZero();
    }

    @Test
    public void shouldNotDrainRateLimiterInConditionNotMetOnSuccessfulCall() {
        RateLimiter rateLimiter = RateLimiter.of("someLimiter", RateLimiterConfig.custom()
            .limitForPeriod(5)
            .limitRefreshPeriod(Duration.ofHours(1))
            .drainPermissionsOnResult(
                callsResult -> isSuccessfulAndReturned(
                    callsResult,
                    ResponseWithPotentialOverload.class,
                    ResponseWithPotentialOverload::isOverload))
            .build());
        SpecificResponseWithPotentialOverload response = new SpecificResponseWithPotentialOverload(false);

        Single.just(response)
            .compose(RateLimiterOperator.of(rateLimiter))
            .test()
            .assertValue(response)
            .assertComplete();
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isEqualTo(4);
    }

}
