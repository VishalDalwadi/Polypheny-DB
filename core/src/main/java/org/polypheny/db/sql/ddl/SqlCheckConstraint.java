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

package org.polypheny.db.sql.ddl;


import org.polypheny.db.sql.SqlCall;
import org.polypheny.db.sql.SqlIdentifier;
import org.polypheny.db.sql.SqlKind;
import org.polypheny.db.sql.SqlNode;
import org.polypheny.db.sql.SqlOperator;
import org.polypheny.db.sql.SqlSpecialOperator;
import org.polypheny.db.sql.SqlWriter;
import org.polypheny.db.sql.parser.SqlParserPos;
import org.polypheny.db.util.ImmutableNullableList;
import java.util.List;


/**
 * Parse tree for {@code UNIQUE}, {@code PRIMARY KEY} constraints.
 *
 * And {@code FOREIGN KEY}, when we support it.
 */
public class SqlCheckConstraint extends SqlCall {

    private static final SqlSpecialOperator OPERATOR = new SqlSpecialOperator( "CHECK", SqlKind.CHECK );

    private final SqlIdentifier name;
    private final SqlNode expression;


    /**
     * Creates a SqlCheckConstraint; use {@link SqlDdlNodes#check}.
     */
    SqlCheckConstraint( SqlParserPos pos, SqlIdentifier name, SqlNode expression ) {
        super( pos );
        this.name = name; // may be null
        this.expression = expression;
    }


    @Override
    public SqlOperator getOperator() {
        return OPERATOR;
    }


    @Override
    public List<SqlNode> getOperandList() {
        return ImmutableNullableList.of( name, expression );
    }


    @Override
    public void unparse( SqlWriter writer, int leftPrec, int rightPrec ) {
        if ( name != null ) {
            writer.keyword( "CONSTRAINT" );
            name.unparse( writer, 0, 0 );
        }
        writer.keyword( "CHECK" );
        if ( writer.isAlwaysUseParentheses() ) {
            expression.unparse( writer, 0, 0 );
        } else {
            writer.sep( "(" );
            expression.unparse( writer, 0, 0 );
            writer.sep( ")" );
        }
    }
}
