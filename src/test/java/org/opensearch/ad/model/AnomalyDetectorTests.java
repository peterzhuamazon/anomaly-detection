/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.ad.model;

import static org.opensearch.ad.constant.ADCommonName.CUSTOM_RESULT_INDEX_PREFIX;
import static org.opensearch.timeseries.model.Config.MAX_RESULT_INDEX_NAME_SIZE;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.opensearch.ad.constant.ADCommonMessages;
import org.opensearch.ad.constant.ADCommonName;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.common.io.stream.NamedWriteableAwareStreamInput;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.metrics.ValueCountAggregationBuilder;
import org.opensearch.timeseries.AbstractTimeSeriesTest;
import org.opensearch.timeseries.TestHelpers;
import org.opensearch.timeseries.common.exception.ValidationException;
import org.opensearch.timeseries.constant.CommonMessages;
import org.opensearch.timeseries.dataprocessor.ImputationMethod;
import org.opensearch.timeseries.dataprocessor.ImputationOption;
import org.opensearch.timeseries.model.Config;
import org.opensearch.timeseries.model.Feature;
import org.opensearch.timeseries.model.IntervalTimeConfiguration;
import org.opensearch.timeseries.model.ValidationIssueType;
import org.opensearch.timeseries.settings.TimeSeriesSettings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class AnomalyDetectorTests extends AbstractTimeSeriesTest {

    public void testParseAnomalyDetector() throws IOException {
        AnomalyDetector detector = TestHelpers.randomAnomalyDetector(TestHelpers.randomUiMetadata(), Instant.now());
        String detectorString = TestHelpers.xContentBuilderToString(detector.toXContent(TestHelpers.builder(), ToXContent.EMPTY_PARAMS));
        LOG.info(detectorString);
        detectorString = detectorString
            .replaceFirst("\\{", String.format(Locale.ROOT, "{\"%s\":\"%s\",", randomAlphaOfLength(5), randomAlphaOfLength(5)));
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString));
        assertEquals("Parsing anomaly detector doesn't work", detector, parsedDetector);
    }

    public void testParseAnomalyDetectorWithCustomIndex() throws IOException {
        String resultIndex = ADCommonName.CUSTOM_RESULT_INDEX_PREFIX + "test";
        AnomalyDetector detector = TestHelpers
            .randomDetector(
                ImmutableList.of(TestHelpers.randomFeature()),
                randomAlphaOfLength(5),
                randomIntBetween(1, 5),
                randomAlphaOfLength(5),
                ImmutableList.of(randomAlphaOfLength(5)),
                resultIndex
            );
        String detectorString = TestHelpers.xContentBuilderToString(detector.toXContent(TestHelpers.builder(), ToXContent.EMPTY_PARAMS));
        LOG.info(detectorString);
        detectorString = detectorString
            .replaceFirst("\\{", String.format(Locale.ROOT, "{\"%s\":\"%s\",", randomAlphaOfLength(5), randomAlphaOfLength(5)));
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString));
        assertEquals("Parsing result index doesn't work", resultIndex, parsedDetector.getCustomResultIndexOrAlias());
        assertEquals("Parsing anomaly detector doesn't work", detector, parsedDetector);
    }

    public void testAnomalyDetectorWithInvalidCustomIndex() throws Exception {
        String resultIndex = ADCommonName.CUSTOM_RESULT_INDEX_PREFIX + "test@@";
        TestHelpers
            .assertFailWith(
                ValidationException.class,
                () -> (TestHelpers
                    .randomDetector(
                        ImmutableList.of(TestHelpers.randomFeature()),
                        randomAlphaOfLength(5),
                        randomIntBetween(1, 5),
                        randomAlphaOfLength(5),
                        ImmutableList.of(randomAlphaOfLength(5)),
                        resultIndex
                    ))
            );
    }

    public void testParseAnomalyDetectorWithoutParams() throws IOException {
        AnomalyDetector detector = TestHelpers.randomAnomalyDetector(TestHelpers.randomUiMetadata(), Instant.now());
        String detectorString = TestHelpers.xContentBuilderToString(detector.toXContent(TestHelpers.builder()));
        LOG.info(detectorString);
        detectorString = detectorString
            .replaceFirst("\\{", String.format(Locale.ROOT, "{\"%s\":\"%s\",", randomAlphaOfLength(5), randomAlphaOfLength(5)));
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString));
        assertEquals("Parsing anomaly detector doesn't work", detector, parsedDetector);
    }

    public void testParseAnomalyDetectorWithCustomDetectionDelay() throws IOException {
        AnomalyDetector detector = TestHelpers.randomAnomalyDetector(TestHelpers.randomUiMetadata(), Instant.now());
        String detectorString = TestHelpers.xContentBuilderToString(detector.toXContent(TestHelpers.builder()));
        LOG.info(detectorString);
        TimeValue detectionInterval = new TimeValue(1, TimeUnit.MINUTES);
        TimeValue detectionWindowDelay = new TimeValue(10, TimeUnit.MINUTES);
        detectorString = detectorString
            .replaceFirst("\\{", String.format(Locale.ROOT, "{\"%s\":\"%s\",", randomAlphaOfLength(5), randomAlphaOfLength(5)));
        AnomalyDetector parsedDetector = AnomalyDetector
            .parse(TestHelpers.parser(detectorString), detector.getId(), detector.getVersion(), detectionInterval, detectionWindowDelay);
        assertEquals("Parsing anomaly detector doesn't work", detector, parsedDetector);
    }

    public void testParseSingleEntityAnomalyDetector() throws IOException {
        AnomalyDetector detector = TestHelpers
            .randomAnomalyDetector(
                ImmutableList.of(TestHelpers.randomFeature()),
                TestHelpers.randomUiMetadata(),
                Instant.now(),
                AnomalyDetectorType.SINGLE_ENTITY.name()
            );
        String detectorString = TestHelpers.xContentBuilderToString(detector.toXContent(TestHelpers.builder(), ToXContent.EMPTY_PARAMS));
        LOG.info(detectorString);
        detectorString = detectorString
            .replaceFirst("\\{", String.format(Locale.ROOT, "{\"%s\":\"%s\",", randomAlphaOfLength(5), randomAlphaOfLength(5)));
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString));
        assertEquals("Parsing anomaly detector doesn't work", detector, parsedDetector);
    }

    public void testParseHistoricalAnomalyDetectorWithoutUser() throws IOException {
        AnomalyDetector detector = TestHelpers
            .randomAnomalyDetector(
                ImmutableList.of(TestHelpers.randomFeature()),
                TestHelpers.randomUiMetadata(),
                Instant.now(),
                false,
                null
            );
        String detectorString = TestHelpers.xContentBuilderToString(detector.toXContent(TestHelpers.builder(), ToXContent.EMPTY_PARAMS));
        LOG.info(detectorString);
        detectorString = detectorString
            .replaceFirst("\\{", String.format(Locale.ROOT, "{\"%s\":\"%s\",", randomAlphaOfLength(5), randomAlphaOfLength(5)));
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString));
        assertEquals("Parsing anomaly detector doesn't work", detector, parsedDetector);
    }

    public void testParseAnomalyDetectorWithNullFilterQuery() throws IOException {
        String detectorString = "{\"name\":\"todagtCMkwpcaedpyYUM\",\"description\":"
            + "\"ClrcaMpuLfeDSlVduRcKlqPZyqWDBf\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\""
            + ":true,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}],\"detection_interval\":"
            + "{\"period\":{\"interval\":425,\"unit\":\"Minutes\"}},\"window_delay\":{\"period\":{\"interval\":973,"
            + "\"unit\":\"Minutes\"}},\"shingle_size\":4,\"schema_version\":-1203962153,\"ui_metadata\":{\"JbAaV\":{\"feature_id\":"
            + "\"rIFjS\",\"feature_name\":\"QXCmS\",\"feature_enabled\":false,\"aggregation_query\":{\"aa\":"
            + "{\"value_count\":{\"field\":\"ok\"}}}}},\"last_update_time\":1568396089028}";
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString));
        assertTrue(parsedDetector.getFilterQuery() instanceof MatchAllQueryBuilder);
    }

    public void testParseAnomalyDetectorWithEmptyFilterQuery() throws IOException {
        String detectorString = "{\"name\":\"todagtCMkwpcaedpyYUM\",\"description\":"
            + "\"ClrcaMpuLfeDSlVduRcKlqPZyqWDBf\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\":"
            + "true,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}],\"filter_query\":{},"
            + "\"detection_interval\":{\"period\":{\"interval\":425,\"unit\":\"Minutes\"}},\"window_delay\":"
            + "{\"period\":{\"interval\":973,\"unit\":\"Minutes\"}},\"shingle_size\":4,\"schema_version\":-1203962153,\"ui_metadata\":"
            + "{\"JbAaV\":{\"feature_id\":\"rIFjS\",\"feature_name\":\"QXCmS\",\"feature_enabled\":false,"
            + "\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}},"
            + "\"last_update_time\":1568396089028}";
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString));
        assertTrue(parsedDetector.getFilterQuery() instanceof MatchAllQueryBuilder);
    }

    public void testParseAnomalyDetectorWithWrongFilterQuery() throws Exception {
        String detectorString = "{\"name\":\"todagtCMkwpcaedpyYUM\",\"description\":"
            + "\"ClrcaMpuLfeDSlVduRcKlqPZyqWDBf\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\":"
            + "true,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}],\"filter_query\":"
            + "{\"aa\":\"bb\"},\"detection_interval\":{\"period\":{\"interval\":425,\"unit\":\"Minutes\"}},"
            + "\"window_delay\":{\"period\":{\"interval\":973,\"unit\":\"Minutes\"}},\"shingle_size\":4,\"schema_version\":"
            + "-1203962153,\"ui_metadata\":{\"JbAaV\":{\"feature_id\":\"rIFjS\",\"feature_name\":\"QXCmS\","
            + "\"feature_enabled\":false,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}},"
            + "\"last_update_time\":1568396089028}";
        TestHelpers.assertFailWith(ValidationException.class, () -> AnomalyDetector.parse(TestHelpers.parser(detectorString)));
    }

    public void testParseAnomalyDetectorWithoutOptionalParams() throws IOException {
        String detectorString = "{\"name\":\"todagtCMkwpcaedpyYUM\",\"description\":"
            + "\"ClrcaMpuLfeDSlVduRcKlqPZyqWDBf\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\""
            + ":true,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}],\"detection_interval\":"
            + "{\"period\":{\"interval\":425,\"unit\":\"Minutes\"}},\"schema_version\":-1203962153,\"ui_metadata\":"
            + "{\"JbAaV\":{\"feature_id\":\"rIFjS\",\"feature_name\":\"QXCmS\",\"feature_enabled\":false,"
            + "\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}},\"last_update_time\":1568396089028}";
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString), "id", 1L, null, null);
        assertTrue(parsedDetector.getFilterQuery() instanceof MatchAllQueryBuilder);
        assertEquals((long) parsedDetector.getShingleSize(), (long) TimeSeriesSettings.DEFAULT_SHINGLE_SIZE);
    }

    public void testParseAnomalyDetectorWithInvalidShingleSize() throws Exception {
        String detectorString = "{\"name\":\"todagtCMkwpcaedpyYUM\",\"description\":"
            + "\"ClrcaMpuLfeDSlVduRcKlqPZyqWDBf\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\""
            + ":true,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}],\"detection_interval\":"
            + "{\"period\":{\"interval\":425,\"unit\":\"Minutes\"}},\"shingle_size\":-1,\"schema_version\":-1203962153,\"ui_metadata\":"
            + "{\"JbAaV\":{\"feature_id\":\"rIFjS\",\"feature_name\":\"QXCmS\",\"feature_enabled\":false,"
            + "\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}},\"last_update_time\":1568396089028}";
        TestHelpers.assertFailWith(ValidationException.class, () -> AnomalyDetector.parse(TestHelpers.parser(detectorString)));
    }

    public void testParseAnomalyDetectorWithNegativeWindowDelay() throws Exception {
        String detectorString = "{\"name\":\"todagtCMkwpcaedpyYUM\",\"description\":"
            + "\"ClrcaMpuLfeDSlVduRcKlqPZyqWDBf\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\""
            + ":true,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}],\"detection_interval\":"
            + "{\"period\":{\"interval\":425,\"unit\":\"Minutes\"}},\"window_delay\":{\"period\":{\"interval\":-973,"
            + "\"unit\":\"Minutes\"}},\"shingle_size\":4,\"schema_version\":-1203962153,\"ui_metadata\":{\"JbAaV\":{\"feature_id\":"
            + "\"rIFjS\",\"feature_name\":\"QXCmS\",\"feature_enabled\":false,\"aggregation_query\":{\"aa\":"
            + "{\"value_count\":{\"field\":\"ok\"}}}}},\"last_update_time\":1568396089028}";
        TestHelpers.assertFailWith(ValidationException.class, () -> AnomalyDetector.parse(TestHelpers.parser(detectorString)));
    }

    public void testParseAnomalyDetectorWithNegativeDetectionInterval() throws Exception {
        String detectorString = "{\"name\":\"todagtCMkwpcaedpyYUM\",\"description\":"
            + "\"ClrcaMpuLfeDSlVduRcKlqPZyqWDBf\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\""
            + ":true,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}],\"detection_interval\":"
            + "{\"period\":{\"interval\":-425,\"unit\":\"Minutes\"}},\"window_delay\":{\"period\":{\"interval\":973,"
            + "\"unit\":\"Minutes\"}},\"shingle_size\":4,\"schema_version\":-1203962153,\"ui_metadata\":{\"JbAaV\":{\"feature_id\":"
            + "\"rIFjS\",\"feature_name\":\"QXCmS\",\"feature_enabled\":false,\"aggregation_query\":{\"aa\":"
            + "{\"value_count\":{\"field\":\"ok\"}}}}},\"last_update_time\":1568396089028}";
        TestHelpers.assertFailWith(ValidationException.class, () -> AnomalyDetector.parse(TestHelpers.parser(detectorString)));
    }

    public void testParseAnomalyDetectorWithIncorrectFeatureQuery() throws Exception {
        String detectorString = "{\"name\":\"todagdpyYUM\",\"description\":"
            + "\"ClrcaMpuLfeDSlVduRcKlqPZyqWDBf\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\""
            + ":true,\"aggregation_query\":{\"aa\":\"bb\"}}],\"detection_interval\":"
            + "{\"period\":{\"interval\":425,\"unit\":\"Minutes\"}},\"window_delay\":{\"period\":{\"interval\":973,"
            + "\"unit\":\"Minutes\"}},\"shingle_size\":4,\"schema_version\":-1203962153,\"ui_metadata\":{\"JbAaV\":{\"feature_id\":"
            + "\"rIFjS\",\"feature_name\":\"QXCmS\",\"feature_enabled\":false,\"aggregation_query\":{\"aa\":"
            + "{\"value_count\":{\"field\":\"ok\"}}}}},\"last_update_time\":1568396089028}";
        TestHelpers.assertFailWith(ValidationException.class, () -> AnomalyDetector.parse(TestHelpers.parser(detectorString)));
    }

    public void testParseAnomalyDetectorWithInvalidDetectorIntervalUnits() {
        String detectorString = "{\"name\":\"todagtCMkwpcaedpyYUM\",\"description\":"
            + "\"ClrcaMpuLfeDSlVduRcKlqPZyqWDBf\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\""
            + ":true,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}],\"detection_interval\":"
            + "{\"period\":{\"interval\":425,\"unit\":\"Millis\"}},\"window_delay\":{\"period\":{\"interval\":973,"
            + "\"unit\":\"Minutes\"}},\"shingle_size\":4,\"schema_version\":-1203962153,\"ui_metadata\":{\"JbAaV\":{\"feature_id\":"
            + "\"rIFjS\",\"feature_name\":\"QXCmS\",\"feature_enabled\":false,\"aggregation_query\":{\"aa\":"
            + "{\"value_count\":{\"field\":\"ok\"}}}}},\"last_update_time\":1568396089028}";
        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> AnomalyDetector.parse(TestHelpers.parser(detectorString))
        );
        assertEquals(
            String.format(Locale.ROOT, ADCommonMessages.INVALID_TIME_CONFIGURATION_UNITS, ChronoUnit.MILLIS),
            exception.getMessage()
        );
    }

    public void testParseAnomalyDetectorInvalidWindowDelayUnits() {
        String detectorString = "{\"name\":\"todagtCMkwpcaedpyYUM\",\"description\":"
            + "\"ClrcaMpuLfeDSlVduRcKlqPZyqWDBf\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\""
            + ":true,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}],\"detection_interval\":"
            + "{\"period\":{\"interval\":425,\"unit\":\"Minutes\"}},\"window_delay\":{\"period\":{\"interval\":973,"
            + "\"unit\":\"Millis\"}},\"shingle_size\":4,\"schema_version\":-1203962153,\"ui_metadata\":{\"JbAaV\":{\"feature_id\":"
            + "\"rIFjS\",\"feature_name\":\"QXCmS\",\"feature_enabled\":false,\"aggregation_query\":{\"aa\":"
            + "{\"value_count\":{\"field\":\"ok\"}}}}},\"last_update_time\":1568396089028}";
        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> AnomalyDetector.parse(TestHelpers.parser(detectorString))
        );
        assertEquals(
            String.format(Locale.ROOT, ADCommonMessages.INVALID_TIME_CONFIGURATION_UNITS, ChronoUnit.MILLIS),
            exception.getMessage()
        );
    }

    public void testParseAnomalyDetectorWithNullUiMetadata() throws IOException {
        AnomalyDetector detector = TestHelpers.randomAnomalyDetector(null, Instant.now());
        String detectorString = TestHelpers.xContentBuilderToString(detector.toXContent(TestHelpers.builder(), ToXContent.EMPTY_PARAMS));
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString));
        assertEquals("Parsing anomaly detector doesn't work", detector, parsedDetector);
        assertNull(parsedDetector.getUiMetadata());
    }

    public void testParseAnomalyDetectorWithEmptyUiMetadata() throws IOException {
        AnomalyDetector detector = TestHelpers.randomAnomalyDetector(ImmutableMap.of(), Instant.now());
        String detectorString = TestHelpers.xContentBuilderToString(detector.toXContent(TestHelpers.builder(), ToXContent.EMPTY_PARAMS));
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString));
        assertEquals("Parsing anomaly detector doesn't work", detector, parsedDetector);
    }

    public void testInvalidShingleSize() throws Exception {
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        TestHelpers
            .assertFailWith(
                ValidationException.class,
                () -> new AnomalyDetector(
                    randomAlphaOfLength(5),
                    randomLong(),
                    randomAlphaOfLength(5),
                    randomAlphaOfLength(5),
                    randomAlphaOfLength(5),
                    ImmutableList.of(randomAlphaOfLength(5)),
                    featureList,
                    TestHelpers.randomQuery(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    0,
                    null,
                    1,
                    Instant.now(),
                    null,
                    TestHelpers.randomUser(),
                    null,
                    TestHelpers.randomImputationOption(featureList),
                    randomIntBetween(2, 10000),
                    randomInt(TimeSeriesSettings.MAX_SHINGLE_SIZE / 2),
                    randomIntBetween(1, 1000),
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.now()
                )
            );
    }

    public void testNullDetectorName() throws Exception {
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        TestHelpers
            .assertFailWith(
                ValidationException.class,
                () -> new AnomalyDetector(
                    randomAlphaOfLength(5),
                    randomLong(),
                    null,
                    randomAlphaOfLength(5),
                    randomAlphaOfLength(5),
                    ImmutableList.of(randomAlphaOfLength(5)),
                    featureList,
                    TestHelpers.randomQuery(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TimeSeriesSettings.DEFAULT_SHINGLE_SIZE,
                    null,
                    1,
                    Instant.now(),
                    null,
                    TestHelpers.randomUser(),
                    null,
                    TestHelpers.randomImputationOption(featureList),
                    randomIntBetween(2, 10000),
                    randomInt(TimeSeriesSettings.MAX_SHINGLE_SIZE / 2),
                    randomIntBetween(1, 1000),
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.now()
                )
            );
    }

    public void testBlankDetectorName() throws Exception {
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        TestHelpers
            .assertFailWith(
                ValidationException.class,
                () -> new AnomalyDetector(
                    randomAlphaOfLength(5),
                    randomLong(),
                    "",
                    randomAlphaOfLength(5),
                    randomAlphaOfLength(5),
                    ImmutableList.of(randomAlphaOfLength(5)),
                    featureList,
                    TestHelpers.randomQuery(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TimeSeriesSettings.DEFAULT_SHINGLE_SIZE,
                    null,
                    1,
                    Instant.now(),
                    null,
                    TestHelpers.randomUser(),
                    null,
                    TestHelpers.randomImputationOption(featureList),
                    randomIntBetween(2, 10000),
                    randomInt(TimeSeriesSettings.MAX_SHINGLE_SIZE / 2),
                    randomIntBetween(1, 1000),
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.now()
                )
            );
    }

    public void testNullTimeField() throws Exception {
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        TestHelpers
            .assertFailWith(
                ValidationException.class,
                () -> new AnomalyDetector(
                    randomAlphaOfLength(5),
                    randomLong(),
                    randomAlphaOfLength(5),
                    randomAlphaOfLength(5),
                    null,
                    ImmutableList.of(randomAlphaOfLength(5)),
                    featureList,
                    TestHelpers.randomQuery(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TimeSeriesSettings.DEFAULT_SHINGLE_SIZE,
                    null,
                    1,
                    Instant.now(),
                    null,
                    TestHelpers.randomUser(),
                    null,
                    TestHelpers.randomImputationOption(featureList),
                    randomIntBetween(2, 10000),
                    randomInt(TimeSeriesSettings.MAX_SHINGLE_SIZE / 2),
                    randomIntBetween(1, 1000),
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.now()
                )
            );
    }

    public void testNullIndices() throws Exception {
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        TestHelpers
            .assertFailWith(
                ValidationException.class,
                () -> new AnomalyDetector(
                    randomAlphaOfLength(5),
                    randomLong(),
                    randomAlphaOfLength(5),
                    randomAlphaOfLength(5),
                    randomAlphaOfLength(5),
                    null,
                    featureList,
                    TestHelpers.randomQuery(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TimeSeriesSettings.DEFAULT_SHINGLE_SIZE,
                    null,
                    1,
                    Instant.now(),
                    null,
                    TestHelpers.randomUser(),
                    null,
                    TestHelpers.randomImputationOption(featureList),
                    randomIntBetween(2, 10000),
                    randomInt(TimeSeriesSettings.MAX_SHINGLE_SIZE / 2),
                    randomIntBetween(1, 1000),
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.now()
                )
            );
    }

    public void testEmptyIndices() throws Exception {
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        TestHelpers
            .assertFailWith(
                ValidationException.class,
                () -> new AnomalyDetector(
                    randomAlphaOfLength(5),
                    randomLong(),
                    randomAlphaOfLength(5),
                    randomAlphaOfLength(5),
                    randomAlphaOfLength(5),
                    ImmutableList.of(),
                    featureList,
                    TestHelpers.randomQuery(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TimeSeriesSettings.DEFAULT_SHINGLE_SIZE,
                    null,
                    1,
                    Instant.now(),
                    null,
                    TestHelpers.randomUser(),
                    null,
                    TestHelpers.randomImputationOption(featureList),
                    randomIntBetween(2, 10000),
                    randomInt(TimeSeriesSettings.MAX_SHINGLE_SIZE / 2),
                    randomIntBetween(1, 1000),
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.now()
                )
            );
    }

    public void testNullDetectionInterval() throws Exception {
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        TestHelpers
            .assertFailWith(
                ValidationException.class,
                () -> new AnomalyDetector(
                    randomAlphaOfLength(5),
                    randomLong(),
                    randomAlphaOfLength(5),
                    randomAlphaOfLength(5),
                    randomAlphaOfLength(5),
                    ImmutableList.of(randomAlphaOfLength(5)),
                    featureList,
                    TestHelpers.randomQuery(),
                    null,
                    TestHelpers.randomIntervalTimeConfiguration(),
                    TimeSeriesSettings.DEFAULT_SHINGLE_SIZE,
                    null,
                    1,
                    Instant.now(),
                    null,
                    TestHelpers.randomUser(),
                    null,
                    TestHelpers.randomImputationOption(featureList),
                    randomIntBetween(2, 10000),
                    randomInt(TimeSeriesSettings.MAX_SHINGLE_SIZE / 2),
                    randomIntBetween(1, 1000),
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.now()
                )
            );
    }

    public void testInvalidRecency() {
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        ValidationException exception = expectThrows(
            ValidationException.class,
            () -> new AnomalyDetector(
                randomAlphaOfLength(10),
                randomLong(),
                randomAlphaOfLength(20),
                randomAlphaOfLength(30),
                randomAlphaOfLength(5),
                ImmutableList.of(randomAlphaOfLength(10).toLowerCase(Locale.ROOT)),
                featureList,
                TestHelpers.randomQuery(),
                new IntervalTimeConfiguration(0, ChronoUnit.MINUTES),
                TestHelpers.randomIntervalTimeConfiguration(),
                randomIntBetween(1, 20),
                null,
                randomInt(),
                Instant.now(),
                null,
                null,
                null,
                TestHelpers.randomImputationOption(featureList),
                -1,
                randomIntBetween(1, 256),
                randomIntBetween(1, 1000),
                null,
                null,
                null,
                null,
                null,
                Instant.now()
            )
        );
        assertEquals("Recency emphasis must be an integer greater than 1.", exception.getMessage());
    }

    public void testInvalidDetectionInterval() {
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        ValidationException exception = expectThrows(
            ValidationException.class,
            () -> new AnomalyDetector(
                randomAlphaOfLength(10),
                randomLong(),
                randomAlphaOfLength(20),
                randomAlphaOfLength(30),
                randomAlphaOfLength(5),
                ImmutableList.of(randomAlphaOfLength(10).toLowerCase(Locale.ROOT)),
                featureList,
                TestHelpers.randomQuery(),
                new IntervalTimeConfiguration(0, ChronoUnit.MINUTES),
                TestHelpers.randomIntervalTimeConfiguration(),
                randomIntBetween(1, 20),
                null,
                randomInt(),
                Instant.now(),
                null,
                null,
                null,
                TestHelpers.randomImputationOption(featureList),
                null, // emphasis is not customized
                randomIntBetween(1, 256),
                randomIntBetween(1, 1000),
                null,
                null,
                null,
                null,
                null,
                Instant.now()
            )
        );
        assertEquals("Detection interval must be a positive integer", exception.getMessage());
    }

    public void testInvalidWindowDelay() {
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> new AnomalyDetector(
                randomAlphaOfLength(10),
                randomLong(),
                randomAlphaOfLength(20),
                randomAlphaOfLength(30),
                randomAlphaOfLength(5),
                ImmutableList.of(randomAlphaOfLength(10).toLowerCase(Locale.ROOT)),
                featureList,
                TestHelpers.randomQuery(),
                new IntervalTimeConfiguration(1, ChronoUnit.MINUTES),
                new IntervalTimeConfiguration(-1, ChronoUnit.MINUTES),
                randomIntBetween(1, 20),
                null,
                randomInt(),
                Instant.now(),
                null,
                null,
                null,
                TestHelpers.randomImputationOption(featureList),
                null, // emphasis is not customized
                randomIntBetween(1, 256),
                randomIntBetween(1, 1000),
                null,
                null,
                null,
                null,
                null,
                Instant.now()
            )
        );
        assertEquals("Interval -1 should be non-negative", exception.getMessage());
    }

    public void testNullFeatures() throws IOException {
        AnomalyDetector detector = TestHelpers.randomAnomalyDetector(null, null, Instant.now().truncatedTo(ChronoUnit.SECONDS));
        String detectorString = TestHelpers.xContentBuilderToString(detector.toXContent(TestHelpers.builder(), ToXContent.EMPTY_PARAMS));
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString));
        assertEquals(0, parsedDetector.getFeatureAttributes().size());
    }

    public void testEmptyFeatures() throws IOException {
        AnomalyDetector detector = TestHelpers
            .randomAnomalyDetector(ImmutableList.of(), null, Instant.now().truncatedTo(ChronoUnit.SECONDS));
        String detectorString = TestHelpers.xContentBuilderToString(detector.toXContent(TestHelpers.builder(), ToXContent.EMPTY_PARAMS));
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString));
        assertEquals(0, parsedDetector.getFeatureAttributes().size());
    }

    public void testGetShingleSize() throws IOException {
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        Config anomalyDetector = new AnomalyDetector(
            randomAlphaOfLength(5),
            randomLong(),
            randomAlphaOfLength(5),
            randomAlphaOfLength(5),
            randomAlphaOfLength(5),
            ImmutableList.of(randomAlphaOfLength(5)),
            featureList,
            TestHelpers.randomQuery(),
            TestHelpers.randomIntervalTimeConfiguration(),
            TestHelpers.randomIntervalTimeConfiguration(),
            5,
            null,
            1,
            Instant.now(),
            null,
            TestHelpers.randomUser(),
            null,
            TestHelpers.randomImputationOption(featureList),
            randomIntBetween(2, 10000),
            randomInt(TimeSeriesSettings.MAX_SHINGLE_SIZE / 2),
            randomIntBetween(1, 1000),
            null,
            null,
            null,
            null,
            null,
            Instant.now()
        );
        assertEquals((int) anomalyDetector.getShingleSize(), 5);
    }

    public void testGetShingleSizeReturnsDefaultValue() throws IOException {
        int seasonalityIntervals = randomInt(TimeSeriesSettings.MAX_SHINGLE_SIZE / 2);
        Feature feature = TestHelpers.randomFeature();
        List<Feature> featureList = ImmutableList.of(feature);
        Config anomalyDetector = new AnomalyDetector(
            randomAlphaOfLength(5),
            randomLong(),
            randomAlphaOfLength(5),
            randomAlphaOfLength(5),
            randomAlphaOfLength(5),
            ImmutableList.of(randomAlphaOfLength(5)),
            ImmutableList.of(feature),
            TestHelpers.randomQuery(),
            TestHelpers.randomIntervalTimeConfiguration(),
            TestHelpers.randomIntervalTimeConfiguration(),
            null,
            null,
            1,
            Instant.now(),
            null,
            TestHelpers.randomUser(),
            null,
            TestHelpers.randomImputationOption(featureList),
            randomIntBetween(2, 10000),
            seasonalityIntervals,
            randomIntBetween(1, 1000),
            null,
            null,
            null,
            null,
            null,
            Instant.now()
        );
        // seasonalityIntervals is not null and custom shingle size is null, use seasonalityIntervals to deterine shingle size
        assertEquals(seasonalityIntervals / TimeSeriesSettings.SEASONALITY_TO_SHINGLE_RATIO, (int) anomalyDetector.getShingleSize());

        anomalyDetector = new AnomalyDetector(
            randomAlphaOfLength(5),
            randomLong(),
            randomAlphaOfLength(5),
            randomAlphaOfLength(5),
            randomAlphaOfLength(5),
            ImmutableList.of(randomAlphaOfLength(5)),
            ImmutableList.of(feature),
            TestHelpers.randomQuery(),
            TestHelpers.randomIntervalTimeConfiguration(),
            TestHelpers.randomIntervalTimeConfiguration(),
            null,
            null,
            1,
            Instant.now(),
            null,
            TestHelpers.randomUser(),
            null,
            TestHelpers.randomImputationOption(featureList),
            null,
            null,
            randomIntBetween(1, 1000),
            null,
            null,
            null,
            null,
            null,
            Instant.now()
        );
        // seasonalityIntervals is null and custom shingle size is null, use default shingle size
        assertEquals(TimeSeriesSettings.DEFAULT_SHINGLE_SIZE, (int) anomalyDetector.getShingleSize());
    }

    public void testNullFeatureAttributes() throws IOException {
        Config anomalyDetector = new AnomalyDetector(
            randomAlphaOfLength(5),
            randomLong(),
            randomAlphaOfLength(5),
            randomAlphaOfLength(5),
            randomAlphaOfLength(5),
            ImmutableList.of(randomAlphaOfLength(5)),
            null,
            TestHelpers.randomQuery(),
            TestHelpers.randomIntervalTimeConfiguration(),
            TestHelpers.randomIntervalTimeConfiguration(),
            null,
            null,
            1,
            Instant.now(),
            null,
            TestHelpers.randomUser(),
            null,
            null,
            randomIntBetween(2, 10000),
            randomInt(TimeSeriesSettings.MAX_SHINGLE_SIZE / 2),
            randomIntBetween(1, 1000),
            null,
            null,
            null,
            null,
            null,
            Instant.now()
        );
        assertNotNull(anomalyDetector.getFeatureAttributes());
        assertEquals(0, anomalyDetector.getFeatureAttributes().size());
    }

    public void testValidateResultIndex() throws IOException {
        Config anomalyDetector = new AnomalyDetector(
            randomAlphaOfLength(5),
            randomLong(),
            randomAlphaOfLength(5),
            randomAlphaOfLength(5),
            randomAlphaOfLength(5),
            ImmutableList.of(randomAlphaOfLength(5)),
            null,
            TestHelpers.randomQuery(),
            TestHelpers.randomIntervalTimeConfiguration(),
            TestHelpers.randomIntervalTimeConfiguration(),
            null,
            null,
            1,
            Instant.now(),
            null,
            TestHelpers.randomUser(),
            null,
            null,
            randomIntBetween(2, 10000),
            randomIntBetween(1, TimeSeriesSettings.MAX_SHINGLE_SIZE * TimeSeriesSettings.SEASONALITY_TO_SHINGLE_RATIO),
            randomIntBetween(1, 1000),
            null,
            null,
            null,
            null,
            null,
            Instant.now()
        );
        String errorMessage = anomalyDetector.validateCustomResultIndex("abc");
        assertEquals(ADCommonMessages.INVALID_RESULT_INDEX_PREFIX, errorMessage);

        StringBuilder resultIndexNameBuilder = new StringBuilder(CUSTOM_RESULT_INDEX_PREFIX);
        for (int i = 0; i < MAX_RESULT_INDEX_NAME_SIZE - CUSTOM_RESULT_INDEX_PREFIX.length(); i++) {
            resultIndexNameBuilder.append("a");
        }
        assertNull(anomalyDetector.validateCustomResultIndex(resultIndexNameBuilder.toString()));
        resultIndexNameBuilder.append("a");

        errorMessage = anomalyDetector.validateCustomResultIndex(resultIndexNameBuilder.toString());
        assertEquals(Config.INVALID_RESULT_INDEX_NAME_SIZE, errorMessage);

        errorMessage = anomalyDetector.validateCustomResultIndex(CUSTOM_RESULT_INDEX_PREFIX + "abc#");
        assertEquals(CommonMessages.INVALID_CHAR_IN_RESULT_INDEX_NAME, errorMessage);
    }

    public void testParseAnomalyDetectorWithNoDescription() throws IOException {
        String detectorString = "{\"name\":\"todagtCMkwpcaedpyYUM\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\""
            + ":true,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}],\"detection_interval\":"
            + "{\"period\":{\"interval\":425,\"unit\":\"Minutes\"}},\"window_delay\":{\"period\":{\"interval\":973,"
            + "\"unit\":\"Minutes\"}},\"shingle_size\":4,\"schema_version\":-1203962153,\"ui_metadata\":{\"JbAaV\":{\"feature_id\":"
            + "\"rIFjS\",\"feature_name\":\"QXCmS\",\"feature_enabled\":false,\"aggregation_query\":{\"aa\":"
            + "{\"value_count\":{\"field\":\"ok\"}}}}},\"last_update_time\":1568396089028}";
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString), "id", 1L, null, null);
        assertEquals(parsedDetector.getDescription(), "");
    }

    public void testParseAnomalyDetectorWithRuleWithNullValue() throws IOException {
        String detectorString = "{\"name\":\"AhtYYGWTgqkzairTchcs\",\"description\":\"iIiAVPMyFgnFlEniLbMyfJxyoGvJAl\","
            + "\"time_field\":\"HmdFH\",\"indices\":[\"ffsBF\"],\"filter_query\":{\"bool\":{\"filter\":[{\"exists\":"
            + "{\"field\":\"value\",\"boost\":1}}],\"adjust_pure_negative\":true,\"boost\":1}},\"window_delay\":"
            + "{\"period\":{\"interval\":2,\"unit\":\"Minutes\"}},\"shingle_size\":8,\"schema_version\":-512063255,"
            + "\"feature_attributes\":[{\"feature_id\":\"OTYJs\",\"feature_name\":\"eYYCM\",\"feature_enabled\":true,"
            + "\"aggregation_query\":{\"XzewX\":{\"value_count\":{\"field\":\"ok\"}}}}],\"recency_emphasis\":3342,"
            + "\"history\":62,\"last_update_time\":1717192049845,\"category_field\":[\"Tcqcb\"],\"result_index\":"
            + "\"opensearch-ad-plugin-result-test\",\"imputation_option\":{\"method\":\"FIXED_VALUES\",\"default_fill\""
            + ":[{\"feature_name\":\"eYYCM\", \"data\": 3}]},\"suggested_seasonality\":64,\"detection_interval\":{\"period\":"
            + "{\"interval\":5,\"unit\":\"Minutes\"}},\"detector_type\":\"MULTI_ENTITY\",\"rules\":[{\"action\":\"IGNORE_ANOMALY\","
            + "\"conditions\":[{\"feature_name\":\"eYYCM\",\"threshold_type\":\"ACTUAL_IS_OVER_EXPECTED\","
            + "\"value\":null,\"operator\":null}]}],\"result_index_min_size\":1500}";
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString), "id", 1L, null, null);
        assertEquals(parsedDetector.getRules().get(0).getConditions().get(0).getValue(), null);
        assertEquals(parsedDetector.getRules().get(0).getConditions().get(0).getOperator(), null);
    }

    public void testParseAnomalyDetectorWithRuleWithNoValue() throws IOException {
        String detectorString = "{\"name\":\"AhtYYGWTgqkzairTchcs\",\"description\":\"iIiAVPMyFgnFlEniLbMyfJxyoGvJAl\","
            + "\"time_field\":\"HmdFH\",\"indices\":[\"ffsBF\"],\"filter_query\":{\"bool\":{\"filter\":[{\"exists\":"
            + "{\"field\":\"value\",\"boost\":1}}],\"adjust_pure_negative\":true,\"boost\":1}},\"window_delay\":"
            + "{\"period\":{\"interval\":2,\"unit\":\"Minutes\"}},\"shingle_size\":8,\"schema_version\":-512063255,"
            + "\"feature_attributes\":[{\"feature_id\":\"OTYJs\",\"feature_name\":\"eYYCM\",\"feature_enabled\":true,"
            + "\"aggregation_query\":{\"XzewX\":{\"value_count\":{\"field\":\"ok\"}}}}],\"recency_emphasis\":3342,"
            + "\"history\":62,\"last_update_time\":1717192049845,\"category_field\":[\"Tcqcb\"],\"result_index\":"
            + "\"opensearch-ad-plugin-result-test\",\"imputation_option\":{\"method\":\"FIXED_VALUES\",\"default_fill\""
            + ":[{\"feature_name\":\"eYYCM\", \"data\": 3}]},\"suggested_seasonality\":64,\"detection_interval\":{\"period\":"
            + "{\"interval\":5,\"unit\":\"Minutes\"}},\"detector_type\":\"MULTI_ENTITY\",\"rules\":[{\"action\":\"IGNORE_ANOMALY\","
            + "\"conditions\":[{\"feature_name\":\"eYYCM\",\"threshold_type\":\"ACTUAL_IS_OVER_EXPECTED\""
            + "}]}],\"result_index_min_size\":1500}";
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString), "id", 1L, null, null);
        assertEquals(parsedDetector.getRules().get(0).getConditions().get(0).getValue(), null);
        assertEquals(parsedDetector.getRules().get(0).getConditions().get(0).getOperator(), null);
    }

    public void testParseAnomalyDetector_withCustomIndex_withCustomResultIndexMinSize() throws IOException {
        String detectorString = "{\"name\":\"AhtYYGWTgqkzairTchcs\",\"description\":\"iIiAVPMyFgnFlEniLbMyfJxyoGvJAl\","
            + "\"time_field\":\"HmdFH\",\"indices\":[\"ffsBF\"],\"filter_query\":{\"bool\":{\"filter\":[{\"exists\":"
            + "{\"field\":\"value\",\"boost\":1}}],\"adjust_pure_negative\":true,\"boost\":1}},\"window_delay\":"
            + "{\"period\":{\"interval\":2,\"unit\":\"Minutes\"}},\"shingle_size\":8,\"schema_version\":-512063255,"
            + "\"feature_attributes\":[{\"feature_id\":\"OTYJs\",\"feature_name\":\"eYYCM\",\"feature_enabled\":true,"
            + "\"aggregation_query\":{\"XzewX\":{\"value_count\":{\"field\":\"ok\"}}}}],\"recency_emphasis\":3342,"
            + "\"history\":62,\"last_update_time\":1717192049845,\"category_field\":[\"Tcqcb\"],\"result_index\":"
            + "\"opensearch-ad-plugin-result-test\",\"imputation_option\":{\"method\":\"FIXED_VALUES\",\"default_fill\""
            + ":[{\"feature_name\":\"eYYCM\", \"data\": 3}]},\"suggested_seasonality\":64,\"detection_interval\":{\"period\":"
            + "{\"interval\":5,\"unit\":\"Minutes\"}},\"detector_type\":\"MULTI_ENTITY\",\"rules\":[],\"result_index_min_size\":1500}";
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString), "id", 1L, null, null);
        assertEquals(1500, (int) parsedDetector.getCustomResultIndexMinSize());
    }

    public void testParseAnomalyDetector_withCustomIndex_withNullCustomResultIndexMinSize() throws IOException {
        String detectorString = "{\"name\":\"todagtCMkwpcaedpyYUM\",\"time_field\":\"dJRwh\",\"indices\":[\"eIrgWMqAED\"],"
            + "\"feature_attributes\":[{\"feature_id\":\"lxYRN\",\"feature_name\":\"eqSeU\",\"feature_enabled\""
            + ":true,\"aggregation_query\":{\"aa\":{\"value_count\":{\"field\":\"ok\"}}}}],\"detection_interval\":"
            + "{\"period\":{\"interval\":425,\"unit\":\"Minutes\"}},\"window_delay\":{\"period\":{\"interval\":973,"
            + "\"unit\":\"Minutes\"}},\"shingle_size\":4,\"schema_version\":-1203962153,\"ui_metadata\":{\"JbAaV\":{\"feature_id\":"
            + "\"rIFjS\",\"feature_name\":\"QXCmS\",\"feature_enabled\":false,\"aggregation_query\":{\"aa\":"
            + "{\"value_count\":{\"field\":\"ok\"}}}}},\"last_update_time\":1568396089028}";
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString), "id", 1L, null, null);
        assertEquals(null, parsedDetector.getCustomResultIndexMinSize());
    }

    public void testParseAnomalyDetector_withCustomIndex_withCustomResultIndexMinAge() throws IOException {
        String detectorString = "{\"name\":\"AhtYYGWTgqkzairTchcs\",\"description\":\"iIiAVPMyFgnFlEniLbMyfJxyoGvJAl\","
            + "\"time_field\":\"HmdFH\",\"indices\":[\"ffsBF\"],\"filter_query\":{\"bool\":{\"filter\":[{\"exists\":"
            + "{\"field\":\"value\",\"boost\":1}}],\"adjust_pure_negative\":true,\"boost\":1}},\"window_delay\":"
            + "{\"period\":{\"interval\":2,\"unit\":\"Minutes\"}},\"shingle_size\":8,\"schema_version\":-512063255,"
            + "\"feature_attributes\":[{\"feature_id\":\"OTYJs\",\"feature_name\":\"eYYCM\",\"feature_enabled\":true,"
            + "\"aggregation_query\":{\"XzewX\":{\"value_count\":{\"field\":\"ok\"}}}}],\"recency_emphasis\":3342,"
            + "\"history\":62,\"last_update_time\":1717192049845,\"category_field\":[\"Tcqcb\"],\"result_index\":"
            + "\"opensearch-ad-plugin-result-test\",\"imputation_option\":{\"method\":\"FIXED_VALUES\",\"default_fill\""
            + ":[{\"feature_name\":\"eYYCM\", \"data\": 3}]},\"suggested_seasonality\":64,\"detection_interval\":{\"period\":"
            + "{\"interval\":5,\"unit\":\"Minutes\"}},\"detector_type\":\"MULTI_ENTITY\",\"rules\":[],\"result_index_min_age\":7}";
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString), "id", 1L, null, null);
        assertEquals(7, (int) parsedDetector.getCustomResultIndexMinAge());
    }

    public void testParseAnomalyDetector_withCustomIndex_withCustomResultIndexTTL() throws IOException {
        String detectorString = "{\"name\":\"AhtYYGWTgqkzairTchcs\",\"description\":\"iIiAVPMyFgnFlEniLbMyfJxyoGvJAl\","
            + "\"time_field\":\"HmdFH\",\"indices\":[\"ffsBF\"],\"filter_query\":{\"bool\":{\"filter\":[{\"exists\":"
            + "{\"field\":\"value\",\"boost\":1}}],\"adjust_pure_negative\":true,\"boost\":1}},\"window_delay\":"
            + "{\"period\":{\"interval\":2,\"unit\":\"Minutes\"}},\"shingle_size\":8,\"schema_version\":-512063255,"
            + "\"feature_attributes\":[{\"feature_id\":\"OTYJs\",\"feature_name\":\"eYYCM\",\"feature_enabled\":true,"
            + "\"aggregation_query\":{\"XzewX\":{\"value_count\":{\"field\":\"ok\"}}}}],\"recency_emphasis\":3342,"
            + "\"history\":62,\"last_update_time\":1717192049845,\"category_field\":[\"Tcqcb\"],\"result_index\":"
            + "\"opensearch-ad-plugin-result-test\",\"imputation_option\":{\"method\":\"FIXED_VALUES\",\"default_fill\""
            + ":[{\"feature_name\":\"eYYCM\", \"data\": 3}]},\"suggested_seasonality\":64,\"detection_interval\":{\"period\":"
            + "{\"interval\":5,\"unit\":\"Minutes\"}},\"detector_type\":\"MULTI_ENTITY\",\"rules\":[],\"result_index_ttl\":30}";
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString), "id", 1L, null, null);
        assertEquals(30, (int) parsedDetector.getCustomResultIndexTTL());
    }

    public void testParseAnomalyDetector_withCustomIndex_withFlattenResultIndexMapping() throws IOException {
        String detectorString = "{\"name\":\"AhtYYGWTgqkzairTchcs\",\"description\":\"iIiAVPMyFgnFlEniLbMyfJxyoGvJAl\","
            + "\"time_field\":\"HmdFH\",\"indices\":[\"ffsBF\"],\"filter_query\":{\"bool\":{\"filter\":[{\"exists\":"
            + "{\"field\":\"value\",\"boost\":1}}],\"adjust_pure_negative\":true,\"boost\":1}},\"window_delay\":"
            + "{\"period\":{\"interval\":2,\"unit\":\"Minutes\"}},\"shingle_size\":8,\"schema_version\":-512063255,"
            + "\"feature_attributes\":[{\"feature_id\":\"OTYJs\",\"feature_name\":\"eYYCM\",\"feature_enabled\":false,"
            + "\"aggregation_query\":{\"XzewX\":{\"value_count\":{\"field\":\"ok\"}}}}],\"recency_emphasis\":3342,"
            + "\"history\":62,\"last_update_time\":1717192049845,\"category_field\":[\"Tcqcb\"],\"result_index\":"
            + "\"opensearch-ad-plugin-result-test\",\"imputation_option\":{\"method\":\"ZERO\"},\"suggested_seasonality\":64,\"detection_interval\":{\"period\":"
            + "{\"interval\":5,\"unit\":\"Minutes\"}},\"detector_type\":\"MULTI_ENTITY\",\"rules\":[],\"flatten_custom_result_index\":true}";
        AnomalyDetector parsedDetector = AnomalyDetector.parse(TestHelpers.parser(detectorString), "id", 1L, null, null);
        assertEquals(true, (boolean) parsedDetector.getFlattenResultIndexMapping());
    }

    public void testSerializeAndDeserializeAnomalyDetector() throws IOException {
        // register writer and reader for type Feature
        Writeable.WriteableRegistry.registerWriter(Feature.class, (o, v) -> {
            o.writeByte((byte) 23);
            ((Feature) v).writeTo(o);
        });
        Writeable.WriteableRegistry.registerReader((byte) 23, Feature::new);

        // write to streamOutput
        AnomalyDetector detector = TestHelpers.randomAnomalyDetector(TestHelpers.randomUiMetadata(), Instant.now());
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        detector.writeTo(bytesStreamOutput);

        // register namedWriteables
        List<NamedWriteableRegistry.Entry> namedWriteables = new ArrayList<>();
        namedWriteables.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, BoolQueryBuilder.NAME, BoolQueryBuilder::new));
        namedWriteables.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, TermQueryBuilder.NAME, TermQueryBuilder::new));
        namedWriteables.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, RangeQueryBuilder.NAME, RangeQueryBuilder::new));
        namedWriteables
            .add(
                new NamedWriteableRegistry.Entry(
                    AggregationBuilder.class,
                    ValueCountAggregationBuilder.NAME,
                    ValueCountAggregationBuilder::new
                )
            );

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        StreamInput input = new NamedWriteableAwareStreamInput(streamInput, new NamedWriteableRegistry(namedWriteables));

        AnomalyDetector deserializedDetector = new AnomalyDetector(input);
        Assert.assertEquals(deserializedDetector, detector);
        Assert.assertEquals(deserializedDetector.getSeasonIntervals(), detector.getSeasonIntervals());
    }

    public void testNullFixedValue() throws IOException {
        org.opensearch.timeseries.common.exception.ValidationException e = assertThrows(
            org.opensearch.timeseries.common.exception.ValidationException.class,
            () -> new AnomalyDetector(
                randomAlphaOfLength(5),
                randomLong(),
                randomAlphaOfLength(5),
                randomAlphaOfLength(5),
                randomAlphaOfLength(5),
                ImmutableList.of(randomAlphaOfLength(5)),
                ImmutableList.of(TestHelpers.randomFeature()),
                TestHelpers.randomQuery(),
                TestHelpers.randomIntervalTimeConfiguration(),
                TestHelpers.randomIntervalTimeConfiguration(),
                null,
                null,
                1,
                Instant.now(),
                null,
                TestHelpers.randomUser(),
                null,
                new ImputationOption(ImputationMethod.FIXED_VALUES, null),
                randomIntBetween(2, 10000),
                randomInt(TimeSeriesSettings.MAX_SHINGLE_SIZE / 2),
                randomIntBetween(1, 1000),
                null,
                null,
                null,
                null,
                null,
                Instant.now()
            )
        );
        assertEquals("Got: " + e.getMessage(), "Enabled features are present, but no default fill values are provided.", e.getMessage());
        assertEquals("Got :" + e.getType(), ValidationIssueType.IMPUTATION, e.getType());
    }

    /**
     * Test that validation passes when rules are null.
     */
    public void testValidateRulesWithNullRules() throws IOException {
        AnomalyDetector detector = TestHelpers.AnomalyDetectorBuilder.newInstance(1).setRules(null).build();

        // Should pass validation; no exception should be thrown
        assertNotNull(detector);
    }

    /**
     * Test that validation fails when features are null but rules are provided.
     */
    public void testValidateRulesWithNullFeatures() throws IOException {
        List<Rule> rules = Arrays.asList(createValidRule());

        try {
            TestHelpers.AnomalyDetectorBuilder.newInstance(0).setFeatureAttributes(null).setRules(rules).build();
            fail("Expected ValidationException due to features being null while rules are provided");
        } catch (ValidationException e) {
            assertEquals("Suppression Rule Error: Features are not defined while suppression rules are provided.", e.getMessage());
            assertEquals(ValidationIssueType.RULE, e.getType());
        }
    }

    /**
     * Test that validation fails when a rule is null.
     */
    public void testValidateRulesWithNullRule() throws IOException {
        List<Rule> rules = Arrays.asList((Rule) null);

        try {
            TestHelpers.AnomalyDetectorBuilder.newInstance(1).setRules(rules).build();
            fail("Expected ValidationException due to null rule");
        } catch (ValidationException e) {
            assertEquals("Suppression Rule Error: A suppression rule or its conditions are not properly defined.", e.getMessage());
            assertEquals(ValidationIssueType.RULE, e.getType());
        }
    }

    /**
     * Test that validation fails when a rule's conditions are null.
     */
    public void testValidateRulesWithNullConditions() throws IOException {
        Rule rule = new Rule(Action.IGNORE_ANOMALY, null);
        List<Rule> rules = Arrays.asList(rule);

        try {
            TestHelpers.AnomalyDetectorBuilder.newInstance(1).setRules(rules).build();
            fail("Expected ValidationException due to rule with null conditions");
        } catch (ValidationException e) {
            assertEquals("Suppression Rule Error: A suppression rule or its conditions are not properly defined.", e.getMessage());
            assertEquals(ValidationIssueType.RULE, e.getType());
        }
    }

    /**
     * Test that validation fails when a condition is null.
     */
    public void testValidateRulesWithNullCondition() throws IOException {
        Rule rule = new Rule(Action.IGNORE_ANOMALY, Arrays.asList((Condition) null));
        List<Rule> rules = Arrays.asList(rule);

        try {
            TestHelpers.AnomalyDetectorBuilder.newInstance(1).setRules(rules).build();
            fail("Expected ValidationException due to null condition in rule");
        } catch (ValidationException e) {
            assertEquals("Suppression Rule Error: A condition within a suppression rule is not properly defined.", e.getMessage());
            assertEquals(ValidationIssueType.RULE, e.getType());
        }
    }

    /**
     * Test that validation fails when a condition's featureName is null.
     */
    public void testValidateRulesWithNullFeatureName() throws IOException {
        Condition condition = new Condition(
            null, // featureName is null
            ThresholdType.ACTUAL_OVER_EXPECTED_RATIO,
            Operator.LTE,
            0.5
        );
        Rule rule = new Rule(Action.IGNORE_ANOMALY, Arrays.asList(condition));
        List<Rule> rules = Arrays.asList(rule);

        try {
            TestHelpers.AnomalyDetectorBuilder.newInstance(1).setRules(rules).build();
            fail("Expected ValidationException due to condition with null feature name");
        } catch (ValidationException e) {
            assertEquals("Suppression Rule Error: A condition is missing the feature name.", e.getMessage());
            assertEquals(ValidationIssueType.RULE, e.getType());
        }
    }

    /**
     * Test that validation fails when a condition's featureName does not exist in features.
     */
    public void testValidateRulesWithNonexistentFeatureName() throws IOException {
        Condition condition = new Condition(
            "nonexistentFeature", // featureName not in features
            ThresholdType.ACTUAL_OVER_EXPECTED_RATIO,
            Operator.LTE,
            0.5
        );
        Rule rule = new Rule(Action.IGNORE_ANOMALY, Arrays.asList(condition));
        List<Rule> rules = Arrays.asList(rule);

        try {
            TestHelpers.AnomalyDetectorBuilder.newInstance(1).setRules(rules).build();
            fail("Expected ValidationException due to condition with nonexistent feature name");
        } catch (ValidationException e) {
            assertEquals(
                "Suppression Rule Error: Feature \"nonexistentFeature\" specified in a suppression rule does not exist.",
                e.getMessage()
            );
            assertEquals(ValidationIssueType.RULE, e.getType());
        }
    }

    /**
     * Test that validation fails when a ACTUAL_IS_BELOW_EXPECTED plus non-null or empty operator and value are given
     */
    public void testValidateRulesWithValueAndAbsoluteThresholdType() throws IOException {
        String featureName = "testFeature";
        Feature feature = TestHelpers.randomFeature(featureName, "agg", true);

        Condition condition = new Condition(
            featureName, // featureName not in features
            ThresholdType.ACTUAL_IS_BELOW_EXPECTED,
            Operator.LTE,
            0.5
        );
        Rule rule = new Rule(Action.IGNORE_ANOMALY, Arrays.asList(condition));
        List<Rule> rules = Arrays.asList(rule);

        try {
            TestHelpers.AnomalyDetectorBuilder.newInstance(1).setFeatureAttributes(Arrays.asList(feature)).setRules(rules).build();
            fail("both operator and value must be empty or null");
        } catch (ValidationException e) {
            assertTrue(e.getMessage().contains("both operator and value must be empty or null"));
            assertEquals(ValidationIssueType.RULE, e.getType());
        }
    }

    /**
     * Test that validation fails when a ACTUAL_IS_BELOW_EXPECTED plus non-null operator given
     */
    public void testValidateRulesWithOperatorAndAbsoluteThresholdType() throws IOException {
        String featureName = "testFeature";
        Feature feature = TestHelpers.randomFeature(featureName, "agg", true);

        Condition condition = new Condition(featureName, ThresholdType.ACTUAL_IS_BELOW_EXPECTED, Operator.LTE, null);
        Rule rule = new Rule(Action.IGNORE_ANOMALY, Arrays.asList(condition));
        List<Rule> rules = Arrays.asList(rule);

        try {
            TestHelpers.AnomalyDetectorBuilder.newInstance(1).setFeatureAttributes(Arrays.asList(feature)).setRules(rules).build();
            fail("both operator and value must be empty or null");
        } catch (ValidationException e) {
            assertTrue(e.getMessage().contains("both operator and value must be empty or null"));
            assertEquals(ValidationIssueType.RULE, e.getType());
        }
    }

    /**
     * Test that validation fails when a ACTUAL_IS_BELOW_EXPECTED with null value and operator
     */
    public void testValidateRulesWithNullValueAndOperatorAndAbsoluteThresholdType() throws IOException {
        String featureName = "testFeature";
        Feature feature = TestHelpers.randomFeature(featureName, "agg", true);

        Condition condition = new Condition(featureName, ThresholdType.ACTUAL_IS_BELOW_EXPECTED, null, null);
        Rule rule = new Rule(Action.IGNORE_ANOMALY, Arrays.asList(condition));
        List<Rule> rules = Arrays.asList(rule);
        TestHelpers.AnomalyDetectorBuilder.newInstance(1).setFeatureAttributes(Arrays.asList(feature)).setRules(rules).build();
    }

    /**
     * Test that validation fails when the feature in condition is disabled.
     */
    public void testValidateRulesWithDisabledFeature() throws IOException {
        String featureName = "testFeature";
        Feature disabledFeature = TestHelpers.randomFeature(featureName, "agg", false);

        Condition condition = new Condition(featureName, ThresholdType.ACTUAL_OVER_EXPECTED_RATIO, Operator.LTE, 0.5);
        Rule rule = new Rule(Action.IGNORE_ANOMALY, Arrays.asList(condition));
        List<Rule> rules = Arrays.asList(rule);

        try {
            TestHelpers.AnomalyDetectorBuilder.newInstance(1).setFeatureAttributes(Arrays.asList(disabledFeature)).setRules(rules).build();
            fail("Expected ValidationException due to condition with disabled feature");
        } catch (ValidationException e) {
            assertEquals(
                "Suppression Rule Error: Feature \"" + featureName + "\" specified in a suppression rule is not enabled.",
                e.getMessage()
            );
            assertEquals(ValidationIssueType.RULE, e.getType());
        }
    }

    /**
     * Test that validation fails when the value in condition is NaN for specific threshold types.
     */
    public void testValidateRulesWithNaNValue() throws IOException {
        String featureName = "testFeature";
        Feature enabledFeature = TestHelpers.randomFeature(featureName, "agg", true);

        Condition condition = new Condition(
            featureName,
            ThresholdType.ACTUAL_OVER_EXPECTED_RATIO,
            Operator.LTE,
            Double.NaN // Value is NaN
        );
        Rule rule = new Rule(Action.IGNORE_ANOMALY, Arrays.asList(condition));
        List<Rule> rules = Arrays.asList(rule);

        try {
            TestHelpers.AnomalyDetectorBuilder.newInstance(1).setFeatureAttributes(Arrays.asList(enabledFeature)).setRules(rules).build();
            fail("Expected ValidationException due to NaN value in condition");
        } catch (ValidationException e) {
            assertEquals(
                "Suppression Rule Error: The threshold value for feature \"" + featureName + "\" is not a valid number.",
                e.getMessage()
            );
            assertEquals(ValidationIssueType.RULE, e.getType());
        }
    }

    /**
     * Test that validation fails when the value in condition is not positive for specific threshold types.
     */
    public void testValidateRulesWithNonPositiveValue() throws IOException {
        String featureName = "testFeature";
        Feature enabledFeature = TestHelpers.randomFeature(featureName, "agg", true);

        Condition condition = new Condition(
            featureName,
            ThresholdType.ACTUAL_OVER_EXPECTED_RATIO,
            Operator.LTE,
            -0.5 // Value is negative
        );
        Rule rule = new Rule(Action.IGNORE_ANOMALY, Arrays.asList(condition));
        List<Rule> rules = Arrays.asList(rule);

        try {
            TestHelpers.AnomalyDetectorBuilder.newInstance(1).setFeatureAttributes(Arrays.asList(enabledFeature)).setRules(rules).build();
            fail("Expected ValidationException due to non-positive value in condition");
        } catch (ValidationException e) {
            assertEquals(
                "Suppression Rule Error: The threshold value for feature \"" + featureName + "\" must be a positive number.",
                e.getMessage()
            );
            assertEquals(ValidationIssueType.RULE, e.getType());
        }
    }

    /**
     * Test that validation passes when the threshold type is not one of the specified types and value is NaN.
     */
    public void testValidateRulesWithOtherThresholdTypeAndNaNValue() throws IOException {
        String featureName = "testFeature";
        Feature enabledFeature = TestHelpers.randomFeature(featureName, "agg", true);

        Condition condition = new Condition(
            featureName,
            null, // ThresholdType is null or another type not specified
            Operator.LTE,
            Double.NaN // Value is NaN, but should not be checked
        );
        Rule rule = new Rule(Action.IGNORE_ANOMALY, Arrays.asList(condition));
        List<Rule> rules = Arrays.asList(rule);

        AnomalyDetector detector = TestHelpers.AnomalyDetectorBuilder
            .newInstance(1)
            .setFeatureAttributes(Arrays.asList(enabledFeature))
            .setRules(rules)
            .build();

        // Should pass validation; no exception should be thrown
        assertNotNull(detector);
    }

    /**
     * Helper method to create a valid rule for testing.
     *
     * @return A valid Rule instance
     */
    private Rule createValidRule() {
        String featureName = "testFeature";
        Condition condition = new Condition(featureName, ThresholdType.ACTUAL_OVER_EXPECTED_RATIO, Operator.LTE, 0.5);
        return new Rule(Action.IGNORE_ANOMALY, Arrays.asList(condition));
    }
}
