/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.debug;

import java.util.Iterator;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Representation of guest language scope at the current suspension point. It contains a set of
 * declared and valid variables as well as arguments, if any. The scope is only valid as long as the
 * associated {@link DebugStackFrame frame} is valid.
 *
 * @see DebugStackFrame#getScope()
 * @since 0.26
 */
public final class DebugScope {

    private final Scope scope;
    private final Iterator<Scope> iterator;
    private final Debugger debugger;
    private final SuspendedEvent event;
    private final MaterializedFrame frame;
    private final RootNode root;
    private final LanguageInfo language;
    private DebugScope parent;
    private ValuePropertiesCollection variables;

    DebugScope(Scope scope, Iterator<Scope> iterator, Debugger debugger,
                    SuspendedEvent event, MaterializedFrame frame, RootNode root) {
        this(scope, iterator, debugger, event, frame, root, null);
    }

    DebugScope(Scope scope, Iterator<Scope> iterator, Debugger debugger,
                    LanguageInfo language) {
        this(scope, iterator, debugger, null, null, null, language);
    }

    private DebugScope(Scope scope, Iterator<Scope> iterator, Debugger debugger,
                    SuspendedEvent event, MaterializedFrame frame, RootNode root,
                    LanguageInfo language) {
        this.scope = scope;
        this.iterator = iterator;
        this.debugger = debugger;
        this.event = event;
        this.frame = frame;
        this.root = root;
        this.language = language;
    }

    /**
     * Get a human readable name of this scope.
     *
     * @since 0.26
     */
    public String getName() {
        return scope.getName();
    }

    /**
     * Get a parent scope.
     *
     * @return the parent scope, or <code>null</code>.
     * @since 0.26
     */
    public DebugScope getParent() {
        verifyValidState();
        if (parent == null && iterator.hasNext()) {
            parent = new DebugScope(iterator.next(), iterator, debugger, event, frame, root, language);
        }
        return parent;
    }

    /**
     * Test if this scope represents the function scope at the frame it was
     * {@link DebugStackFrame#getScope() obtained from}. {@link #getArguments() arguments} of
     * function scope represent arguments of the appropriate function.
     *
     * @since 0.26
     */
    public boolean isFunctionScope() {
        return root != null && root.equals(scope.getNode());
    }

    /**
     * Get a source section representing this scope. Please note that while this scope does not
     * provide variables that are valid only after the suspension point, the source section can
     * actually span after the suspension point.
     *
     * @return the source section, or <code>null</code> when not available.
     * @since 0.29
     */
    public SourceSection getSourceSection() {
        Node node = scope.getNode();
        if (node != null) {
            return node.getEncapsulatingSourceSection();
        } else {
            return null;
        }
    }

    /**
     * Get arguments of this scope. If this scope is a {@link #isFunctionScope() function} scope,
     * function arguments are returned.
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @return an iterable of arguments, or <code>null</code> when this scope does not have a
     *         concept of arguments.
     * @since 0.26
     */
    public Iterable<DebugValue> getArguments() {
        verifyValidState();
        Object argumentssObj = scope.getArguments();
        ValuePropertiesCollection arguments = (argumentssObj != null) ? DebugValue.getProperties(argumentssObj, debugger, getLanguage(), this) : null;
        return arguments;
    }

    /**
     * Get local variables declared in this scope, valid at the current suspension point. Call this
     * method on {@link #getParent() parent}, to get values of variables declared in parent scope,
     * if any.
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @since 0.26
     */
    public Iterable<DebugValue> getDeclaredValues() {
        return getVariables();
    }

    /**
     * Get a local variable declared in this scope by name. Call this method on {@link #getParent()
     * parent}, to get value of a variable declared in parent scope, if any.
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @return a value of requested name, or <code>null</code> when no such value was found.
     * @since 0.26
     */
    public DebugValue getDeclaredValue(String name) {
        return getVariables().get(name);
    }

    private ValuePropertiesCollection getVariables() {
        verifyValidState();
        if (variables == null) {
            Object variablesObj = scope.getVariables();
            variables = DebugValue.getProperties(variablesObj, debugger, getLanguage(), this);
        }
        return variables;
    }

    LanguageInfo getLanguage() {
        if (root != null) {
            return root.getLanguageInfo();
        } else {
            return language;
        }
    }

    void verifyValidState() {
        if (event != null) {
            event.verifyValidState(false);
        }
    }
}
