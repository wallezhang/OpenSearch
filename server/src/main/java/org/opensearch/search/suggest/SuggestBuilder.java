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

package org.opensearch.search.suggest;

import org.opensearch.common.Nullable;
import org.opensearch.common.ParseField;
import org.opensearch.common.ParsingException;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.lucene.BytesRefs;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.search.suggest.SuggestionSearchContext.SuggestionContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * Defines how to perform suggesting. This builders allows a number of global options to be specified and
 * an arbitrary number of {@link SuggestionBuilder} instances.
 * <p>
 * Suggesting works by suggesting terms/phrases that appear in the suggest text that are similar compared
 * to the terms in provided text. These suggestions are based on several options described in this class.
 */
public class SuggestBuilder implements Writeable, ToXContentObject {
    protected static final ParseField GLOBAL_TEXT_FIELD = new ParseField("text");

    private String globalText;
    private final Map<String, SuggestionBuilder<?>> suggestions = new HashMap<>();

    /**
     * Build an empty SuggestBuilder.
     */
    public SuggestBuilder() {
    }

    /**
     * Read from a stream.
     */
    public SuggestBuilder(StreamInput in) throws IOException {
        globalText = in.readOptionalString();
        final int size = in.readVInt();
        for (int i = 0; i < size; i++) {
            suggestions.put(in.readString(), in.readNamedWriteable(SuggestionBuilder.class));
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(globalText);
        final int size = suggestions.size();
        out.writeVInt(size);
        for (Entry<String, SuggestionBuilder<?>> suggestion : suggestions.entrySet()) {
            out.writeString(suggestion.getKey());
            out.writeNamedWriteable(suggestion.getValue());
        }
    }

    /**
     * Sets the text to provide suggestions for. The suggest text is a required option that needs
     * to be set either via this setter or via the {@link org.opensearch.search.suggest.SuggestionBuilder#text(String)} method.
     * <p>
     * The suggest text gets analyzed by the suggest analyzer or the suggest field search analyzer.
     * For each analyzed token, suggested terms are suggested if possible.
     */
    public SuggestBuilder setGlobalText(@Nullable String globalText) {
        this.globalText = globalText;
        return this;
    }

    /**
     * Gets the global suggest text
     */
    @Nullable
    public String getGlobalText() {
        return globalText;
    }

    /**
     * Adds an {@link org.opensearch.search.suggest.SuggestionBuilder} instance under a user defined name.
     * The order in which the <code>Suggestions</code> are added, is the same as in the response.
     * @throws IllegalArgumentException if two suggestions added have the same name
     */
    public SuggestBuilder addSuggestion(String name, SuggestionBuilder<?> suggestion) {
        Objects.requireNonNull(name, "every suggestion needs a name");
        if (suggestions.get(name) == null) {
            suggestions.put(name, suggestion);
        } else {
            throw new IllegalArgumentException("already added another suggestion with name [" + name + "]");
        }
        return this;
    }

    /**
     * Get all the <code>Suggestions</code> that were added to the global {@link SuggestBuilder},
     * together with their names
     */
    public Map<String, SuggestionBuilder<?>> getSuggestions() {
        return suggestions;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (globalText != null) {
            builder.field("text", globalText);
        }
        for (Entry<String, SuggestionBuilder<?>> suggestion : suggestions.entrySet()) {
            builder.startObject(suggestion.getKey());
            suggestion.getValue().toXContent(builder, params);
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }

    public static SuggestBuilder fromXContent(XContentParser parser) throws IOException {
        SuggestBuilder suggestBuilder = new SuggestBuilder();
        String fieldName = null;

        if (parser.currentToken() == null) {
            // when we parse from RestSuggestAction the current token is null, advance the token
            parser.nextToken();
        }
        assert parser.currentToken() == XContentParser.Token.START_OBJECT : "current token must be a start object";
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                fieldName = parser.currentName();
            } else if (token.isValue()) {
                if (GLOBAL_TEXT_FIELD.match(fieldName, parser.getDeprecationHandler())) {
                    suggestBuilder.setGlobalText(parser.text());
                } else {
                    throw new IllegalArgumentException("[suggest] does not support [" + fieldName + "]");
                }
            } else if (token == XContentParser.Token.START_OBJECT) {
                String suggestionName = fieldName;
                if (suggestionName == null) {
                    throw new IllegalArgumentException("suggestion must have name");
                }
                suggestBuilder.addSuggestion(suggestionName, SuggestionBuilder.fromXContent(parser));
            } else {
                throw new ParsingException(parser.getTokenLocation(), "unexpected token [" + token + "] after [" + fieldName + "]");
            }
        }
        return suggestBuilder;
    }

    public SuggestionSearchContext build(QueryShardContext context) throws IOException {
        SuggestionSearchContext suggestionSearchContext = new SuggestionSearchContext();
        for (Entry<String, SuggestionBuilder<?>> suggestion : suggestions.entrySet()) {
            SuggestionContext suggestionContext = suggestion.getValue().build(context);
            if (suggestionContext.getText() == null) {
                if (globalText == null) {
                    throw new IllegalArgumentException("The required text option is missing");
                }
                suggestionContext.setText(BytesRefs.toBytesRef(globalText));
            }
            suggestionSearchContext.addSuggestion(suggestion.getKey(), suggestionContext);
        }
        return suggestionSearchContext;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        SuggestBuilder o = (SuggestBuilder) other;
        return Objects.equals(globalText, o.globalText) &&
               Objects.equals(suggestions, o.suggestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(globalText, suggestions);
    }

    @Override
    public String toString() {
        return Strings.toString(this, true, true);
    }
}
