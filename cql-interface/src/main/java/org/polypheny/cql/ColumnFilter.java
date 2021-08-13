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

import java.util.Map;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.type.RelDataTypeField;
import org.polypheny.db.rex.RexBuilder;
import org.polypheny.db.rex.RexNode;

public class ColumnFilter implements Filter {

    private final ColumnIndex left;
    private final Relation relation;
    private final ColumnIndex right;


    public ColumnFilter( ColumnIndex left, Relation relation, ColumnIndex right ) {
        this.left = left;
        this.relation = relation;
        this.right = right;
    }


    @Override
    public RexNode convert2RexNode( RelNode baseNode, RexBuilder rexBuilder, Map<String, RelDataTypeField> typeField ) {
        throw new RuntimeException( "Not Implemented." );
    }


    @Override
    public String toString() {
        return left.toString() + relation.toString() + right.toString();
    }

}