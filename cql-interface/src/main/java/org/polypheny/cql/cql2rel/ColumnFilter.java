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

package org.polypheny.cql.cql2rel;

import org.polypheny.cql.parser.Relation;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.type.RelDataTypeField;
import org.polypheny.db.rex.RexBuilder;
import org.polypheny.db.rex.RexNode;

public class ColumnFilter extends Filter {

    private final Index left;
    private final Relation relation;
    private final Index right;


    public ColumnFilter( Index left, Relation relation, Index right ) {
        this.left = left;
        this.relation = relation;
        this.right = right;
    }


    @Override
    public RexNode convert2RexNode( RelNode baseNode, RexBuilder rexBuilder, RelDataTypeField typeField ) {
        throw new RuntimeException( "Not Implemented." );
    }

}
