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

package org.polypheny.db.mql;

import lombok.Getter;
import org.bson.BsonDocument;
import org.polypheny.db.mql.Mql.Type;
import org.polypheny.db.mql.parser.MqlParserPos;


public class MqlFindOneAndDelete extends MqlDelete implements MqlQueryStatement {

    @Getter
    private final BsonDocument sort;
    @Getter
    private final BsonDocument collation;


    public MqlFindOneAndDelete( MqlParserPos pos, String collection, BsonDocument query, BsonDocument options ) {
        super( pos, collection, query, options, true );
        this.sort = getDocumentOrNull( options, "sort" );
        this.collation = getDocumentOrNull( options, "collation" );
    }


    @Override
    public Type getKind() {
        return Type.FIND_DELETE;
    }

}
