/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.common.inject;

import org.opensearch.common.inject.BindingProcessor.CreationListener;
import org.opensearch.common.inject.internal.Errors;
import org.opensearch.common.inject.internal.ErrorsException;
import org.opensearch.common.inject.internal.InternalContext;
import org.opensearch.common.inject.internal.InternalFactory;
import org.opensearch.common.inject.spi.Dependency;

/**
 * Delegates to a custom factory which is also bound in the injector.
 */
class BoundProviderFactory<T> implements InternalFactory<T>, CreationListener {

    private final InjectorImpl injector;
    final Key<? extends Provider<? extends T>> providerKey;
    final Object source;
    private InternalFactory<? extends Provider<? extends T>> providerFactory;

    BoundProviderFactory(
            InjectorImpl injector,
            Key<? extends Provider<? extends T>> providerKey,
            Object source) {
        this.injector = injector;
        this.providerKey = providerKey;
        this.source = source;
    }

    @Override
    public void notify(Errors errors) {
        try {
            providerFactory = injector.getInternalFactory(providerKey, errors.withSource(source));
        } catch (ErrorsException e) {
            errors.merge(e.getErrors());
        }
    }

    @Override
    public T get(Errors errors, InternalContext context, Dependency<?> dependency)
            throws ErrorsException {
        errors = errors.withSource(providerKey);
        Provider<? extends T> provider = providerFactory.get(errors, context, dependency);
        try {
            return errors.checkForNull(provider.get(), source, dependency);
        } catch (RuntimeException userException) {
            throw errors.errorInProvider(userException).toException();
        }
    }

    @Override
    public String toString() {
        return providerKey.toString();
    }
}
