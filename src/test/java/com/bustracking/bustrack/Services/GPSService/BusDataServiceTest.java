package com.bustracking.bustrack.Services.GPSService;

import com.bustracking.bustrack.Services.GPSService.BusDataService.FetchResult;
import com.bustracking.bustrack.dto.BusLocationDTO;
import com.bustracking.bustrack.util.TimestampNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class BusDataServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private BusDataService service;
    private StringRedisTemplate redis;
    private HashOperations hash;
    private ValueOperations values;
    private RestTemplate http;

    @BeforeEach
    void setUp() {
        service = new BusDataService();
        redis = mock(StringRedisTemplate.class);
        hash = mock(HashOperations.class);
        values = mock(ValueOperations.class);
        http = mock(RestTemplate.class);
        doReturn(hash).when(redis).opsForHash();
        doReturn(values).when(redis).opsForValue();
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        ReflectionTestUtils.setField(service, "restTemplate", http);
        ReflectionTestUtils.setField(service, "url1", "http://provider.test/api?api_key=SECRET");
        ReflectionTestUtils.setField(service, "urlNMTTrack", "http://nmt.test/track");
        ReflectionTestUtils.setField(service, "urlApiTATATrack", "http://tata.test/track");
    }

    private Map<String, String> published() {
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(hash).putAll(eq("LIVE_BUS_LOCATIONS"), captor.capture());
        return captor.getValue();
    }

    // ---- per-record handling ----

    @Test
    void oneBadRecordDoesNotDropTheRestOfTheBatch() throws Exception {
        when(http.getForObject(anyString(), eq(String.class))).thenReturn("""
                {"data":[
                  {"vehicle_number":"TN 11 BS 7470","lat_message":12.75,"lon_message":80.20,"speed":3,"gps_datetime":"2026-09-30 12:19:05"},
                  {"vehicle_number":"TN11XX0001","lat_message":12.75,"lon_message":80.20,"gps_datetime":"2026-09-30 12:19:05"},
                  {"lat_message":12.75,"lon_message":80.20},
                  {"vehicle_number":"TN11NOFIX","lat_message":0,"lon_message":0,"speed":0},
                  {"vehicle_number":"TN11OUTSIDE","lat_message":123.0,"lon_message":80.20},
                  {"vehicle_number":"tn-11-cb-8467","lat_message":"12.7529","lon_message":"80.2034","speed":0,"gps_datetime":"2026-09-30 12:18:56"}
                ]}""");

        FetchResult result = service.fetchAndPublishApi1();

        assertTrue(result.isSuccess());
        Map<String, String> batch = published();
        assertEquals(Set.of("TN11BS7470", "TN11XX0001", "TN11CB8467"), batch.keySet());

        BusLocationDTO first = mapper.readValue(batch.get("TN11BS7470"), BusLocationDTO.class);
        assertEquals("API_1", first.getSource());
        assertEquals("2026-09-30 12:19:05", first.getTimestamp());
        assertTrue(first.getEpochMs() > 0);
        // a missing speed is not fatal
        assertEquals(0.0, mapper.readValue(batch.get("TN11XX0001"), BusLocationDTO.class).getSpeed());
    }

    @Test
    void everyRecordBadIsAFailureNotAQuietSuccess() {
        when(http.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"data\":[{\"vehicle_number\":\"A\"},{\"nothing\":1}]}");

        assertFalse(service.fetchAndPublishApi1().isSuccess());
        verify(hash, never()).putAll(any(), any());
    }

    // ---- empty vs broken ----

    @Test
    void emptyDataArrayIsSuccessSoTheNormalCadenceIsKept() {
        when(http.getForObject(anyString(), eq(String.class))).thenReturn("{\"data\":[]}");

        assertTrue(service.fetchAndPublishApi1().isSuccess());
        verify(hash, never()).putAll(any(), any());
    }

    @Test
    void unreadableOrShapelessResponsesAreFailures() {
        when(http.getForObject(anyString(), eq(String.class))).thenReturn("<html>502 Bad Gateway</html>");
        assertFalse(service.fetchAndPublishApi1().isSuccess());

        when(http.getForObject(anyString(), eq(String.class))).thenReturn("{\"error\":\"quota\"}");
        assertFalse(service.fetchAndPublishApi1().isSuccess());

        when(http.getForObject(anyString(), eq(String.class))).thenReturn(null);
        assertFalse(service.fetchAndPublishApi1().isSuccess());
    }

    // ---- worker survival ----

    @Test
    void redisFailureReadingTheNmtTokenIsAFailureNotAnException() {
        when(values.get("NMT_TOKEN")).thenThrow(new RedisConnectionFailureException("Redis is down"));

        FetchResult result = assertDoesNotThrow(() -> service.fetchAndPublishApiNMT());

        assertFalse(result.isSuccess());
    }

    @Test
    void redisFailureReadingTheTataTokenIsAFailureNotAnException() {
        when(values.get("API4_TOKEN")).thenThrow(new RedisConnectionFailureException("Redis is down"));

        FetchResult result = assertDoesNotThrow(() -> service.fetchAndPublishApiTata());

        assertFalse(result.isSuccess());
    }

    @Test
    void unauthorizedEvictsTheTokenAndEvenAFailingEvictionDoesNotEscape() {
        when(values.get("NMT_TOKEN")).thenReturn("old-token");
        when(http.exchange(eq("http://nmt.test/track"), any(), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", new HttpHeaders(), new byte[0], null));
        when(redis.delete("NMT_TOKEN")).thenThrow(new RedisConnectionFailureException("Redis is down"));

        FetchResult result = assertDoesNotThrow(() -> service.fetchAndPublishApiNMT());

        assertFalse(result.isSuccess());
        verify(redis).delete("NMT_TOKEN");
    }

    // ---- rate limiting ----

    @Test
    void retryAfterOnA429IsPassedOn() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "120");
        when(http.getForObject(anyString(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, new byte[0], null));

        FetchResult result = service.fetchAndPublishApi1();

        assertFalse(result.isSuccess());
        assertEquals(120_000, result.retryAfterMillis());
    }

    @Test
    void retryAfterParsing() {
        HttpHeaders h = new HttpHeaders();
        assertEquals(0, BusDataService.retryAfterMillis(h));
        assertEquals(0, BusDataService.retryAfterMillis(null));
        h.set(HttpHeaders.RETRY_AFTER, "30");
        assertEquals(30_000, BusDataService.retryAfterMillis(h));
        h.set(HttpHeaders.RETRY_AFTER, "not a value");
        assertEquals(0, BusDataService.retryAfterMillis(h));
        h.set(HttpHeaders.RETRY_AFTER, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(90)));
        long ms = BusDataService.retryAfterMillis(h);
        assertTrue(ms > 80_000 && ms <= 90_000, "was " + ms);
    }

    // ---- logs must not leak provider keys ----

    @Test
    void safeMessageRemovesQueryStrings() {
        Exception e = new ResourceAccessException(
                "I/O error on GET request for \"http://provider.test/api?api_key=SECRET&x=1\": Connect timed out");
        String message = GpsSupport.safeMessage(e);
        assertFalse(message.contains("SECRET"), message);
        assertTrue(message.contains("Connect timed out"), message);
    }

    // ---- NMT / Tata timestamp handling ----

    @Test
    void nmtKeepsTheOldTimestampWhileTheBusHasNotMoved() throws Exception {
        // cached entry written by the previous version: old dd-MM-yyyy text, no epochMs
        String cached = "{\"regNo\":\"TN19BE3559\",\"latitude\":12.5,\"longitude\":80.5,\"speed\":0.0,"
                + "\"timestamp\":\"30-09-2026 11:12:10\",\"source\":\"API_NMT\"}";
        doReturn(Arrays.asList(cached, null)).when(hash).multiGet(eq("LIVE_BUS_LOCATIONS"), anyList());

        BusDataService.ParseResult result = service.parseApiNMT("""
                {"data":[
                  {"vehicle_name":"TN 19 BE 3559","latitude":12.5,"longitude":80.5,"speed":0,"acc":"ACC state low"},
                  {"vehicle_name":"TN19BE5349","latitude":12.6,"longitude":80.6,"speed":0,"acc":"ACC state low"}
                ]}""");

        List<BusLocationDTO> buses = result.buses();
        assertEquals(2, buses.size());
        BusLocationDTO parked = buses.get(0);
        assertEquals("TN19BE3559", parked.getRegNo());
        // same position: the old time is kept, now in the common format
        assertEquals("2026-09-30 11:12:10", parked.getTimestamp());
        assertEquals(TimestampNormalizer.parse("2026-09-30 11:12:10").orElseThrow().epochMs(), parked.getEpochMs());

        BusLocationDTO fresh = buses.get(1);
        assertTrue(System.currentTimeMillis() - fresh.getEpochMs() < 5_000); // new bus: stamped now
    }

    @Test
    void tataIgnoresUnassignedTrackersWithoutCountingThemAsBad() throws Exception {
        doReturn(Arrays.asList((Object) null)).when(hash).multiGet(any(), anyList());

        BusDataService.ParseResult result = service.parseApiTata("""
                {"vehicles":[
                  {"registrationNumber":"TN 14 AQ 9388","gpsLatitude":12.75,"gpsLongitude":80.20,"speed":1.8,"odometer":24519,"ignitionOn":true},
                  {"gpsLatitude":12.75,"gpsLongitude":80.20},
                  {"registrationNumber":"","gpsLatitude":12.75,"gpsLongitude":80.20}
                ]}""");

        assertEquals(1, result.buses().size());
        assertEquals(0, result.stats().skipped);
        assertEquals(2, result.stats().ignored);
        assertEquals("TN14AQ9388", result.buses().get(0).getRegNo());
        assertEquals("ON", result.buses().get(0).getIgnition());
        assertEquals("24519", result.buses().get(0).getOdometer());
    }

    @Test
    void api2ProviderFormatIsNormalised() throws Exception {
        BusDataService.ParseResult result = service.parseApi2("""
                {"data":[{"RegNo":"TN19BD9970","Lat":12.752,"Lng":80.203,"Speed":0,"Time":"30-09-2026 12:18:45",
                          "Odometer":"38941.9","Ignition":"0"}]}""");

        BusLocationDTO bus = result.buses().get(0);
        assertEquals("2026-09-30 12:18:45", bus.getTimestamp());
        assertEquals("38941.9", bus.getOdometer());
        assertEquals("0", bus.getIgnition());
    }

    @Test
    void anUnreadableProviderTimestampStillPublishesTheBus() throws Exception {
        BusDataService.ParseResult result = service.parseApi1("""
                {"data":[{"vehicle_number":"TN11BS7470","lat_message":12.75,"lon_message":80.20,"speed":0,"gps_datetime":"garbage"}]}""");

        assertEquals(1, result.buses().size());
        assertTrue(System.currentTimeMillis() - result.buses().get(0).getEpochMs() < 5_000);
    }
}
