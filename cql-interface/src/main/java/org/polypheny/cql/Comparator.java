/*
 * Copyright 2019-2021 The Polypheny Project
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

package org.polypheny.cql;

import org.polypheny.db.sql.SqlBinaryOperator;
import org.polypheny.db.sql.fun.SqlStdOperatorTable;

public enum Comparator {
    SERVER_CHOICE( "=", true ),
    EQUALS( "==", true ),
    NOT_EQUALS( "<>", true ),
    GREATER_THAN( ">", true ),
    LESS_THAN( "<", true ),
    GREATER_THAN_OR_EQUALS( ">=", true ),
    LESS_THAN_OR_EQUALS( "<=", true ),
    NAMED_COMPARATOR( "", true );


    private String comparisonOp;


    private final boolean isSymbolComparator;


    Comparator( String comparisonOp, boolean isSymbolComparator ) {
        this.comparisonOp = comparisonOp;
        this.isSymbolComparator = isSymbolComparator;
    }


    public String getComparisonOp() {
        return this.comparisonOp;
    }


    public boolean isSymbolComparator() {
        return this.isSymbolComparator;
    }


    public boolean isNamedComparator() {
        return !this.isSymbolComparator;
    }


    public SqlBinaryOperator toSqlStdOperatorTable( SqlBinaryOperator fallback ) {
        if ( this == SERVER_CHOICE ) {
            return fallback;
        } else if ( this == EQUALS ) {
            return SqlStdOperatorTable.EQUALS;
        } else if ( this == NOT_EQUALS ) {
            return SqlStdOperatorTable.NOT_EQUALS;
        } else if ( this == GREATER_THAN ) {
            return SqlStdOperatorTable.GREATER_THAN;
        } else if ( this == LESS_THAN ) {
            return SqlStdOperatorTable.LESS_THAN;
        } else if ( this == GREATER_THAN_OR_EQUALS ) {
            return SqlStdOperatorTable.GREATER_THAN_OR_EQUAL;
        } else {
            return SqlStdOperatorTable.LESS_THAN_OR_EQUAL;
        }
    }


    public static Comparator createNamedComparator( String comparisonOp ) {
        Comparator namedComparator = Comparator.NAMED_COMPARATOR;
        namedComparator.comparisonOp = comparisonOp;
        return namedComparator;
    }


    @Override
    public String toString() {
        return name() + "(" + comparisonOp + ") ";
    }
}