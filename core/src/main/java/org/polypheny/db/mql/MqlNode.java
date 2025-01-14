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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.polypheny.db.mql.parser.MqlParserPos;


@Accessors(fluent = true)
public abstract class MqlNode {

    @Getter
    protected final MqlParserPos pos;

    @Getter
    @Setter
    List<String> stores = new ArrayList<>();

    @Setter
    @Getter
    List<String> primary = new ArrayList<>();


    protected MqlNode( MqlParserPos pos ) {
        this.pos = pos;
    }


    protected BsonDocument getDocumentOrNull( BsonDocument document, String name ) {
        if ( document != null && document.containsKey( name ) && document.get( name ).isDocument() ) {
            return document.getDocument( name );
        } else {
            return null;
        }
    }


    protected BsonArray getArrayOrNull( BsonDocument document, String name ) {
        if ( document != null && document.containsKey( name ) && document.get( name ).isArray() ) {
            return document.getBoolean( name ).asArray();
        } else {
            return null;
        }
    }


    protected boolean getBoolean( BsonDocument document, String name ) {
        if ( document != null && document.containsKey( name ) && document.get( name ).isBoolean() ) {
            return document.getBoolean( name ).asBoolean().getValue();
        } else {
            return false;
        }
    }


    public abstract Mql.Type getKind();


    public Mql.Family getFamily() {
        return Mql.getFamily( getKind() );
    }


    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{}";
    }


    public MqlParserPos getParserPosition() {
        return new MqlParserPos( 0, 0, 0, 0 ); // todo dl fix
    }

}
