/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.search.aggregations.metrics;

import org.opensearch.search.DocValueFormat;
import org.opensearch.search.aggregations.Aggregator;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.support.ValuesSource;
import org.opensearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Map;

class TDigestPercentileRanksAggregator extends AbstractTDigestPercentilesAggregator {

    TDigestPercentileRanksAggregator(String name,
                                        ValuesSource valuesSource,
                                        SearchContext context,
                                         Aggregator parent,
                                        double[] percents,
                                        double compression,
                                        boolean keyed,
                                        DocValueFormat formatter,
                                        Map<String, Object> metadata) throws IOException {
        super(name, valuesSource, context, parent, percents, compression, keyed, formatter, metadata);
    }

    @Override
    public InternalAggregation buildAggregation(long owningBucketOrdinal) {
        TDigestState state = getState(owningBucketOrdinal);
        if (state == null) {
            return buildEmptyAggregation();
        } else {
            return new InternalTDigestPercentileRanks(name, keys, state, keyed, formatter, metadata());
        }
    }

    @Override
    public InternalAggregation buildEmptyAggregation() {
        return new InternalTDigestPercentileRanks(name, keys, new TDigestState(compression), keyed, formatter, metadata());
    }

    @Override
    public double metric(String name, long bucketOrd) {
        TDigestState state = getState(bucketOrd);
        if (state == null) {
            return Double.NaN;
        } else {
            return InternalTDigestPercentileRanks.percentileRank(state, Double.valueOf(name));
        }
    }
}
