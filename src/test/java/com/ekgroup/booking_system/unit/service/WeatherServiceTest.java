package com.ekgroup.booking_system.unit.service;

import com.ekgroup.booking_system.client.weather.WeatherClient;
import com.ekgroup.booking_system.dto.WeatherAvailabilityResponse;
import com.ekgroup.booking_system.exception.NotFoundException;
import com.ekgroup.booking_system.model.Activity;
import com.ekgroup.booking_system.model.WeatherRuleType;
import com.ekgroup.booking_system.model.WeatherSnapshot;
import com.ekgroup.booking_system.repository.ActivityRepository;
import com.ekgroup.booking_system.service.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    private static final long ACTIVITY_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 5, 7);
    private static final LocalTime TIME = LocalTime.of(10, 0);

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private WeatherClient weatherClient;

    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        weatherService = new WeatherService(activityRepository, weatherClient);
    }

    @Test
    void TC_WEA_010_activityNotFound_throwsNotFoundException() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> weatherService.checkAvailability(ACTIVITY_ID, DATE, TIME));

        verifyNoInteractions(weatherClient);
    }

    @Test
    void TC_WEA_011_inactiveActivity_throwsNotFoundException() {
        Activity inactive = activity(WeatherRuleType.MAX_WIND_SPEED, 12.0, false);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(inactive));

        assertThrows(NotFoundException.class,
                () -> weatherService.checkAvailability(ACTIVITY_ID, DATE, TIME));

        verifyNoInteractions(weatherClient);
    }

    @Test
    void TC_WEA_012_weatherWithinRule_returnsAllowedTrue() {
        Activity activity = activity(WeatherRuleType.MAX_WIND_SPEED, 12.0, true);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(weatherClient.getWeather(DATE, TIME))
                .thenReturn(snapshot(5.0, 18.0, 30));

        WeatherAvailabilityResponse response = weatherService.checkAvailability(ACTIVITY_ID, DATE, TIME);

        assertTrue(response.allowed());
        assertEquals("Weather conditions satisfy activity rule.", response.reason());
        assertEquals(ACTIVITY_ID, response.activityId());
        assertEquals(DATE, response.date());
        assertEquals(TIME, response.time());
    }

    @Test
    void TC_WEA_013_weatherViolatesRule_returnsAllowedFalse() {
        Activity activity = activity(WeatherRuleType.MAX_WIND_SPEED, 12.0, true);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(weatherClient.getWeather(DATE, TIME))
                .thenReturn(snapshot(20.0, 18.0, 30));

        WeatherAvailabilityResponse response = weatherService.checkAvailability(ACTIVITY_ID, DATE, TIME);

        assertFalse(response.allowed());
        assertEquals("Weather conditions violate activity rule.", response.reason());
    }

    @Test
    void TC_WEA_020_isAllowed_maxWindSpeed_belowAtAbove() {
        assertTrue(weatherService.isAllowed(WeatherRuleType.MAX_WIND_SPEED, 12.0, snapshot(11.9, 0, 0)));
        assertTrue(weatherService.isAllowed(WeatherRuleType.MAX_WIND_SPEED, 12.0, snapshot(12.0, 0, 0)));
        assertFalse(weatherService.isAllowed(WeatherRuleType.MAX_WIND_SPEED, 12.0, snapshot(12.1, 0, 0)));
    }

    @Test
    void TC_WEA_021_isAllowed_minTemperature_belowAtAbove() {
        assertFalse(weatherService.isAllowed(WeatherRuleType.MIN_TEMPERATURE, 0.0, snapshot(0, -0.1, 0)));
        assertTrue(weatherService.isAllowed(WeatherRuleType.MIN_TEMPERATURE, 0.0, snapshot(0, 0.0, 0)));
        assertTrue(weatherService.isAllowed(WeatherRuleType.MIN_TEMPERATURE, 0.0, snapshot(0, 0.1, 0)));
    }

    @Test
    void TC_WEA_022_isAllowed_maxCloudCoverage_belowAtAbove() {
        assertTrue(weatherService.isAllowed(WeatherRuleType.MAX_CLOUD_COVERAGE, 80.0, snapshot(0, 0, 79)));
        assertTrue(weatherService.isAllowed(WeatherRuleType.MAX_CLOUD_COVERAGE, 80.0, snapshot(0, 0, 80)));
        assertFalse(weatherService.isAllowed(WeatherRuleType.MAX_CLOUD_COVERAGE, 80.0, snapshot(0, 0, 81)));
    }

    @Test
    void TC_WEA_023_isAllowed_unknownRuleType_returnsFalse() {
        assertFalse(weatherService.isAllowed(null, 12.0, snapshot(5.0, 18.0, 30)));
    }

    private static Activity activity(WeatherRuleType ruleType, double threshold, boolean active) {
        return new Activity(
                "Quidditch Training",
                "Outdoor session",
                "Quidditch Pitch",
                10,
                ruleType,
                threshold,
                active
        );
    }

    private static WeatherSnapshot snapshot(double windMs, double tempC, int cloudPercent) {
        return new WeatherSnapshot(windMs, tempC, cloudPercent, Instant.parse("2026-05-07T10:00:00Z"));
    }
}
