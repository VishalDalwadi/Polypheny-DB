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

package org.polypheny.db.sql.ddl;


import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Getter;
import org.polypheny.db.catalog.Catalog.Collation;
import org.polypheny.db.catalog.exceptions.UnknownCollationException;
import org.polypheny.db.schema.ColumnStrategy;
import org.polypheny.db.sql.SqlCall;
import org.polypheny.db.sql.SqlDataTypeSpec;
import org.polypheny.db.sql.SqlIdentifier;
import org.polypheny.db.sql.SqlKind;
import org.polypheny.db.sql.SqlNode;
import org.polypheny.db.sql.SqlOperator;
import org.polypheny.db.sql.SqlSpecialOperator;
import org.polypheny.db.sql.SqlWriter;
import org.polypheny.db.sql.parser.SqlParserPos;
import org.polypheny.db.type.PolyTypeFamily;


/**
 * Parse tree for {@code UNIQUE}, {@code PRIMARY KEY} constraints.
 *
 * And {@code FOREIGN KEY}, when we support it.
 */
public class SqlColumnDeclaration extends SqlCall {

    private static final SqlSpecialOperator OPERATOR = new SqlSpecialOperator( "COLUMN_DECL", SqlKind.COLUMN_DECL );

    @Getter
    final SqlIdentifier name;
    @Getter
    final SqlDataTypeSpec dataType;
    @Getter
    final SqlNode expression;
    final ColumnStrategy strategy;
    public final String collation;


    /**
     * Creates a SqlColumnDeclaration; use {@link SqlDdlNodes#column}.
     */
    SqlColumnDeclaration( SqlParserPos pos, SqlIdentifier name, SqlDataTypeSpec dataType, String collation, SqlNode expression, ColumnStrategy strategy ) {
        super( pos );
        this.name = name;
        this.dataType = dataType;
        this.expression = expression;
        this.strategy = strategy;
        this.collation = collation;
    }


    /**
     * Parses the collation from string format to an collation object
     *
     * @return the parsed collation
     */
    public Collation getCollation() {
        try {
            if ( dataType.getType().getFamily() == PolyTypeFamily.CHARACTER ) {
                if ( collation != null ) {
                    return Collation.parse( collation );
                } else {
                    return Collation.getDefaultCollation(); // Set default collation
                }
            }
            return null;

        } catch ( UnknownCollationException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public SqlOperator getOperator() {
        return OPERATOR;
    }


    @Override
    public List<SqlNode> getOperandList() {
        return ImmutableList.of( name, dataType );
    }


    @Override
    public void unparse( SqlWriter writer, int leftPrec, int rightPrec ) {
        name.unparse( writer, 0, 0 );
        dataType.unparse( writer, 0, 0 );
        if ( dataType.getNullable() != null && !dataType.getNullable() ) {
            writer.keyword( "NOT NULL" );
        }
        if ( expression != null ) {
            switch ( strategy ) {
                case VIRTUAL:
                case STORED:
                    writer.keyword( "AS" );
                    exp( writer );
                    writer.keyword( strategy.name() );
                    break;
                case DEFAULT:
                    writer.keyword( "DEFAULT" );
                    exp( writer );
                    break;
                default:
                    throw new AssertionError( "unexpected: " + strategy );
            }
        }
        if ( collation != null ) {
            writer.keyword( "COLLATE" );
            writer.literal( collation );
        }
    }


    private void exp( SqlWriter writer ) {
        if ( writer.isAlwaysUseParentheses() ) {
            expression.unparse( writer, 0, 0 );
        } else {
            writer.sep( "(" );
            expression.unparse( writer, 0, 0 );
            writer.sep( ")" );
        }
    }

}
