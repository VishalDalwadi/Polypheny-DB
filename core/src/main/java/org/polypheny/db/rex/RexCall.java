/*
 * Copyright 2019-2020 The Polypheny Project
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
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.rex;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.polypheny.db.mql.fun.MqlFunctionOperator;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeFactory;
import org.polypheny.db.sql.SqlKind;
import org.polypheny.db.sql.SqlOperator;
import org.polypheny.db.sql.SqlSyntax;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.PolyTypeUtil;
import org.polypheny.db.util.Litmus;


/**
 * An expression formed by a call to an operator with zero or more expressions as operands.
 *
 * Operators may be binary, unary, functions, special syntactic constructs like <code>CASE ... WHEN ... END</code>, or even internally generated constructs like implicit type conversions. The syntax of the operator is
 * really irrelevant, because row-expressions (unlike {@link org.polypheny.db.sql.SqlNode SQL expressions}) do not directly represent a piece of source code.
 *
 * It's not often necessary to sub-class this class. The smarts should be in the operator, rather than the call. Any extra information about the call can often be encoded as extra arguments. (These don't need to be hidden,
 * because no one is going to be generating source code from this tree.)
 */
public class RexCall extends RexNode {

    public final SqlOperator op;
    public final ImmutableList<RexNode> operands;
    public final RelDataType type;

    private static final Set<SqlKind> SIMPLE_BINARY_OPS;


    static {
        EnumSet<SqlKind> kinds = EnumSet.of( SqlKind.PLUS, SqlKind.MINUS, SqlKind.TIMES, SqlKind.DIVIDE );
        kinds.addAll( SqlKind.COMPARISON );
        SIMPLE_BINARY_OPS = Sets.immutableEnumSet( kinds );
    }


    public RexCall( RelDataType type, SqlOperator op, List<? extends RexNode> operands ) {
        this.type = Objects.requireNonNull( type );
        this.op = Objects.requireNonNull( op );
        this.operands = ImmutableList.copyOf( operands );
        assert op.getKind() != null : op;
        assert op.validRexOperands( operands.size(), Litmus.THROW ) : this;
    }


    /**
     * Appends call operands without parenthesis. {@link RexLiteral} might omit data type depending on the context.
     * For instance, {@code null:BOOLEAN} vs {@code =(true, null)}. The idea here is to omit "obvious" types for readability purposes while still maintain {@link RelNode#getDigest()} contract.
     *
     * @param sb destination
     * @return original StringBuilder for fluent API
     * @see RexLiteral#computeDigest(RexDigestIncludeType)
     */
    protected final StringBuilder appendOperands( StringBuilder sb ) {
        for ( int i = 0; i < operands.size(); i++ ) {
            if ( i > 0 ) {
                sb.append( ", " );
            }
            RexNode operand = operands.get( i );
            if ( !(operand instanceof RexLiteral) ) {
                sb.append( operand );
                continue;
            }
            // Type information might be omitted in certain cases to improve readability
            // For instance, AND/OR arguments should be BOOLEAN, so AND(true, null) is better than AND(true, null:BOOLEAN), and we keep the same info +($0, 2) is better than +($0, 2:BIGINT). Note: if $0 has BIGINT,
            // then 2 is expected to be of BIGINT type as well.
            RexDigestIncludeType includeType = RexDigestIncludeType.OPTIONAL;
            if ( (isA( SqlKind.AND ) || isA( SqlKind.OR )) && operand.getType().getPolyType() == PolyType.BOOLEAN ) {
                includeType = RexDigestIncludeType.NO_TYPE;
            }
            if ( SIMPLE_BINARY_OPS.contains( getKind() ) ) {
                RexNode otherArg = operands.get( 1 - i );
                if ( (!(otherArg instanceof RexLiteral) || ((RexLiteral) otherArg).digestIncludesType() == RexDigestIncludeType.NO_TYPE) && equalSansNullability( operand.getType(), otherArg.getType() ) ) {
                    includeType = RexDigestIncludeType.NO_TYPE;
                }
            }
            sb.append( ((RexLiteral) operand).computeDigest( includeType ) );
        }
        return sb;
    }


    /**
     * This is a poorman's {@link PolyTypeUtil#equalSansNullability(RelDataTypeFactory, RelDataType, RelDataType)}
     * {@code SqlTypeUtil} requires {@link RelDataTypeFactory} which we haven't, so we assume that "not null" is represented in the type's digest as a trailing "NOT NULL" (case sensitive)
     *
     * @param a first type
     * @param b second type
     * @return true if the types are equal or the only difference is nullability
     */
    private static boolean equalSansNullability( RelDataType a, RelDataType b ) {
        String x = a.getFullTypeString();
        String y = b.getFullTypeString();
        if ( x.length() < y.length() ) {
            String c = x;
            x = y;
            y = c;
        }

        return (x.length() == y.length() || x.length() == y.length() + 9 && x.endsWith( " NOT NULL" )) && x.startsWith( y );
    }


    protected @Nonnull
    String computeDigest( boolean withType ) {
        final StringBuilder sb = new StringBuilder( op.getName() );
        if ( (operands.size() == 0) && (op.getSyntax() == SqlSyntax.FUNCTION_ID) ) {
            // Don't print params for empty arg list. For example, we want "SYSTEM_USER", not "SYSTEM_USER()".
        } else {
            sb.append( "(" );
            appendOperands( sb );
            sb.append( ")" );
        }
        if ( withType ) {
            sb.append( ":" );
            // NOTE jvs 16-Jan-2005:  for digests, it is very important to use the full type string.
            sb.append( type.getFullTypeString() );
        }
        return sb.toString();
    }


    @Override
    public final @Nonnull
    String toString() {
        // This data race is intentional
        String localDigest = digest;
        if ( localDigest == null ) {
            localDigest = computeDigest( isA( SqlKind.CAST ) || isA( SqlKind.NEW_SPECIFICATION ) );
            digest = Objects.requireNonNull( localDigest );
        }
        return localDigest;
    }


    @Override
    public <R> R accept( RexVisitor<R> visitor ) {
        return visitor.visitCall( this );
    }


    @Override
    public <R, P> R accept( RexBiVisitor<R, P> visitor, P arg ) {
        return visitor.visitCall( this, arg );
    }


    @Override
    public RelDataType getType() {
        return type;
    }


    @Override
    public boolean isAlwaysTrue() {
        // "c IS NOT NULL" occurs when we expand EXISTS.
        // This reduction allows us to convert it to a semi-join.
        switch ( getKind() ) {
            case IS_NOT_NULL:
                return !operands.get( 0 ).getType().isNullable();
            case IS_NOT_TRUE:
            case IS_FALSE:
            case NOT:
                return operands.get( 0 ).isAlwaysFalse();
            case IS_NOT_FALSE:
            case IS_TRUE:
            case CAST:
                return operands.get( 0 ).isAlwaysTrue();
            default:
                return false;
        }
    }


    @Override
    public boolean isAlwaysFalse() {
        switch ( getKind() ) {
            case IS_NULL:
                return !operands.get( 0 ).getType().isNullable();
            case IS_NOT_TRUE:
            case IS_FALSE:
            case NOT:
                return operands.get( 0 ).isAlwaysTrue();
            case IS_NOT_FALSE:
            case IS_TRUE:
            case CAST:
                return operands.get( 0 ).isAlwaysFalse();
            default:
                return false;
        }
    }


    @Override
    public SqlKind getKind() {
        return op.kind;
    }


    public List<RexNode> getOperands() {
        return operands;
    }


    public SqlOperator getOperator() {
        return op;
    }


    /**
     * Creates a new call to the same operator with different operands.
     *
     * @param type Return type
     * @param operands Operands to call
     * @return New call
     */
    public RexCall clone( RelDataType type, List<RexNode> operands ) {
        return new RexCall( type, op, operands );
    }


    @Override
    public boolean equals( Object obj ) {
        return obj == this
                || obj instanceof RexCall
                && toString().equals( obj.toString() );
    }


    @Override
    public int hashCode() {
        if ( SqlKind.DOC_KIND.contains( op.getKind() ) || op instanceof MqlFunctionOperator ) {
            return (op + "[" + operands.stream().map( rexNode -> Integer.toString( rexNode.hashCode() ) ).collect( Collectors.joining( "," ) ) + "]").hashCode();
        } else {
            return toString().hashCode();
        }
    }

}

