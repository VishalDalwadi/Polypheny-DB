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

package org.polypheny.db.sql.validate;


import static org.polypheny.db.util.Static.RESOURCE;

import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.sql.SqlCall;
import org.polypheny.db.sql.SqlKind;
import org.polypheny.db.sql.SqlNode;


/**
 * Namespace based upon a set operation (UNION, INTERSECT, EXCEPT).
 */
public class SetopNamespace extends AbstractNamespace {

    private final SqlCall call;


    /**
     * Creates a <code>SetopNamespace</code>.
     *
     * @param validator Validator
     * @param call Call to set operator
     * @param enclosingNode Enclosing node
     */
    protected SetopNamespace( SqlValidatorImpl validator, SqlCall call, SqlNode enclosingNode ) {
        super( validator, enclosingNode );
        this.call = call;
    }


    @Override
    public SqlNode getNode() {
        return call;
    }


    @Override
    public SqlMonotonicity getMonotonicity( String columnName ) {
        SqlMonotonicity monotonicity = null;
        int index = getRowType().getFieldNames().indexOf( columnName );
        if ( index < 0 ) {
            return SqlMonotonicity.NOT_MONOTONIC;
        }
        for ( SqlNode operand : call.getOperandList() ) {
            final SqlValidatorNamespace namespace = validator.getNamespace( operand );
            monotonicity = combine( monotonicity, namespace.getMonotonicity( namespace.getRowType().getFieldNames().get( index ) ) );
        }
        return monotonicity;
    }


    private SqlMonotonicity combine( SqlMonotonicity m0, SqlMonotonicity m1 ) {
        if ( m0 == null ) {
            return m1;
        }
        if ( m1 == null ) {
            return m0;
        }
        if ( m0 == m1 ) {
            return m0;
        }
        if ( m0.unstrict() == m1 ) {
            return m1;
        }
        if ( m1.unstrict() == m0 ) {
            return m0;
        }
        return SqlMonotonicity.NOT_MONOTONIC;
    }


    @Override
    public RelDataType validateImpl( RelDataType targetRowType ) {
        switch ( call.getKind() ) {
            case UNION:
            case INTERSECT:
            case EXCEPT:
                final SqlValidatorScope scope = validator.scopes.get( call );
                for ( SqlNode operand : call.getOperandList() ) {
                    if ( !(operand.isA( SqlKind.QUERY )) ) {
                        throw validator.newValidationError( operand, RESOURCE.needQueryOp( operand.toString() ) );
                    }
                    validator.validateQuery( operand, scope, targetRowType );
                }
                return call.getOperator().deriveType( validator, scope, call );
            default:
                throw new AssertionError( "Not a query: " + call.getKind() );
        }
    }
}
