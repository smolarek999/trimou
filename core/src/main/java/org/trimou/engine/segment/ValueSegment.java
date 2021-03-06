/*
 * Copyright 2013 Martin Kouba
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
package org.trimou.engine.segment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import org.trimou.annotations.Internal;
import org.trimou.engine.MustacheTagType;
import org.trimou.engine.config.EngineConfigurationKey;
import org.trimou.engine.context.ExecutionContext;
import org.trimou.engine.context.ValueWrapper;
import org.trimou.engine.parser.Template;
import org.trimou.engine.resolver.EnhancedResolver.Hint;
import org.trimou.engine.text.TextSupport;
import org.trimou.lambda.Lambda;
import org.trimou.util.Strings;

/**
 * Value segment (aka variable tag).
 *
 * @author Martin Kouba
 */
@Internal
public class ValueSegment extends AbstractSegment implements HelperAwareSegment {

    private final boolean unescape;

    private final HelperExecutionHandler helperHandler;

    private final TextSupport textSupport;

    private final String[] keyParts;

    /**
     * The hint is currently only used to skip the resolver chain for a simple
     * value reference - a key with single part, i.e. {{this}}
     *
     * @see EngineConfigurationKey#RESOLVER_HINTS_ENABLED
     */
    private final AtomicReference<Hint> hint;

    /**
     *
     * @param text
     * @param origin
     * @param unescape
     */
    public ValueSegment(String text, Origin origin, boolean unescape) {
        super(text, origin);
        this.unescape = unescape;
        this.helperHandler = isHandlebarsSupportEnabled() ? HelperExecutionHandler
                .from(text, getEngine(), this) : null;
        if (helperHandler == null) {
            this.textSupport = getEngineConfiguration().getTextSupport();
            ArrayList<String> parts = new ArrayList<String>();
            for (Iterator<String> iterator = getEngineConfiguration()
                    .getKeySplitter().split(text); iterator.hasNext();) {
                parts.add(iterator.next());
            }
            this.keyParts = parts.toArray(new String[parts.size()]);
            if (getEngineConfiguration().getBooleanPropertyValue(
                    EngineConfigurationKey.RESOLVER_HINTS_ENABLED)) {
                this.hint = new AtomicReference<Hint>();
            } else {
                this.hint = null;
            }
        } else {
            this.textSupport = null;
            this.keyParts = null;
            this.hint = null;
        }
    }

    public SegmentType getType() {
        return SegmentType.VALUE;
    }

    public boolean isUnescape() {
        return unescape;
    }

    public Appendable execute(Appendable appendable, ExecutionContext context) {
        if (helperHandler != null) {
            return helperHandler.execute(appendable, context);
        } else {
            ValueWrapper value = context.getValue(getText(), keyParts, hint);
            try {
                if (value.isNull()) {
                    Object replacement = getEngineConfiguration()
                            .getMissingValueHandler().handle(getTagInfo());
                    if (replacement != null) {
                        processValue(appendable, context, replacement);
                    }
                } else {
                    if (hint != null && value.getHint() != null) {
                        hint.compareAndSet(null, value.getHint());
                    }
                    processValue(appendable, context, value.get());
                }
            } finally {
                value.release();
            }
            return appendable;
        }
    }

    @Override
    public Appendable fn(Appendable appendable, ExecutionContext context) {
        // No-op
        return appendable;
    }

    @Override
    protected String getSegmentName() {
        return getText();
    }

    @Override
    protected MustacheTagType getTagType() {
        // Because of one segment is used for both a variable and an unescape
        // variable
        return unescape ? MustacheTagType.UNESCAPE_VARIABLE
                : MustacheTagType.VARIABLE;
    }

    private void processValue(Appendable appendable, ExecutionContext context,
            Object value) {
        if (value instanceof Lambda) {
            processLambda(appendable, context, value);
        } else {
            writeValue(appendable, value.toString());
        }
    }

    private void writeValue(Appendable appendable, String text) {
        append(appendable, unescape ? text : textSupport.escapeHtml(text));
    }

    private void processLambda(Appendable appendable, ExecutionContext context,
            Object value) {

        Lambda lambda = (Lambda) value;
        String returnValue = lambda.invoke(null);

        if (returnValue == null) {
            Object replacement = getEngineConfiguration()
                    .getMissingValueHandler().handle(getTagInfo());
            if (replacement != null) {
                processValue(appendable, context, replacement);
                return;
            }
        } else if (!returnValue.equals(Strings.EMPTY)) {
            if (lambda.isReturnValueInterpolated()) {
                // Parse and interpolate the return value
                StringBuilder interpolated = new StringBuilder();
                Template temp = (Template) getEngine().compileMustache(
                        Lambdas.constructLambdaOneoffTemplateName(this),
                        returnValue);
                temp.getRootSegment().execute(interpolated, context);
                writeValue(appendable, interpolated.toString());
            } else {
                writeValue(appendable, returnValue);
            }
        }
    }

}
