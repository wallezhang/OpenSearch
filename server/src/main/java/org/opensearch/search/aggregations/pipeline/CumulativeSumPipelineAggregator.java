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

package org.opensearch.search.aggregations.pipeline;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.InternalAggregation.ReduceContext;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.aggregations.InternalMultiBucketAggregation;
import org.opensearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket;
import org.opensearch.search.aggregations.bucket.histogram.HistogramFactory;
import org.opensearch.search.aggregations.pipeline.BucketHelpers.GapPolicy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.opensearch.search.aggregations.pipeline.BucketHelpers.resolveBucketValue;

public class CumulativeSumPipelineAggregator extends PipelineAggregator {
    private final DocValueFormat formatter;

    CumulativeSumPipelineAggregator(String name, String[] bucketsPaths, DocValueFormat formatter,
                                    Map<String, Object> metadata) {
        super(name, bucketsPaths, metadata);
        this.formatter = formatter;
    }

    /**
     * Read from a stream.
     */
    public CumulativeSumPipelineAggregator(StreamInput in) throws IOException {
        super(in);
        formatter = in.readNamedWriteable(DocValueFormat.class);
    }

    @Override
    public void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(formatter);
    }

    @Override
    public String getWriteableName() {
        return CumulativeSumPipelineAggregationBuilder.NAME;
    }

    @Override
    public InternalAggregation reduce(InternalAggregation aggregation, ReduceContext reduceContext) {
        InternalMultiBucketAggregation<? extends InternalMultiBucketAggregation, ? extends InternalMultiBucketAggregation.InternalBucket>
                histo = (InternalMultiBucketAggregation<? extends InternalMultiBucketAggregation, ? extends
                InternalMultiBucketAggregation.InternalBucket>) aggregation;
        List<? extends InternalMultiBucketAggregation.InternalBucket> buckets = histo.getBuckets();
        HistogramFactory factory = (HistogramFactory) histo;
        List<Bucket> newBuckets = new ArrayList<>(buckets.size());
        double sum = 0;
        for (InternalMultiBucketAggregation.InternalBucket bucket : buckets) {
            Double thisBucketValue = resolveBucketValue(histo, bucket, bucketsPaths()[0], GapPolicy.INSERT_ZEROS);

            // Only increment the sum if it's a finite value, otherwise "increment by zero" is correct
            if (thisBucketValue != null && thisBucketValue.isInfinite() == false && thisBucketValue.isNaN() == false) {
                sum += thisBucketValue;
            }

            List<InternalAggregation> aggs = StreamSupport.stream(bucket.getAggregations().spliterator(), false)
                .map((p) -> (InternalAggregation) p)
                .collect(Collectors.toList());
            aggs.add(new InternalSimpleValue(name(), sum, formatter, metadata()));
            Bucket newBucket = factory.createBucket(factory.getKey(bucket), bucket.getDocCount(), InternalAggregations.from(aggs));
            newBuckets.add(newBucket);
        }
        return factory.createAggregation(newBuckets);
    }
}
