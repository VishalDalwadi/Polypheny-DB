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

package org.polypheny.db.adapter.mongodb;


import com.mongodb.client.MongoCursor;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import java.io.PushbackInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.calcite.avatica.util.DateTimeUtils;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.tree.Primitive;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;


/**
 * Enumerator that reads from a MongoDB collection.
 */
class MongoEnumerator implements Enumerator<Object> {

    private final Iterator<Document> cursor;
    private final Function1<Document, Object> getter;
    private final GridFSBucket bucket;
    private Object current;


    /**
     * Creates a MongoEnumerator.
     *
     * @param cursor Mongo iterator (usually a {@link com.mongodb.ServerCursor})
     * @param getter Converts an object into a list of fields
     */
    MongoEnumerator( Iterator<Document> cursor, Function1<Document, Object> getter, GridFSBucket bucket ) {
        this.cursor = cursor;
        this.getter = getter;
        this.bucket = bucket;
    }


    @Override
    public Object current() {
        return current;
    }


    @Override
    public boolean moveNext() {
        try {
            if ( cursor.hasNext() ) {
                Document map = cursor.next();
                current = getter.apply( map );

                current = handleTransforms( current );

                return true;
            } else {
                current = null;
                return false;
            }
        } catch ( Exception e ) {
            throw new RuntimeException( e );
        }
    }


    private Object handleTransforms( Object current ) {
        if ( current == null ) {
            return null;
        }
        if ( current instanceof Decimal128 ) {
            return ((Decimal128) current).bigDecimalValue();
        } else if ( current.getClass().isArray() ) {
            List<Object> temp = new ArrayList<>();
            for ( Object el : (Object[]) current ) {
                temp.add( handleTransforms( el ) );
            }
            return temp.toArray();
        } else {
            if ( current instanceof List ) {
                return ((List<?>) current).stream().map( el -> {
                    // one possible solution // TODO DL: discuss
                    if ( el instanceof Document ) {
                        return handleDocument( (Document) el );
                    }
                    if ( el instanceof Decimal128 ) {
                        return ((Decimal128) el).bigDecimalValue();
                    } else {
                        return el;
                    }
                } ).collect( Collectors.toList() );
            } else if ( current instanceof Document ) {
                return handleDocument( (Document) current );
            }
        }
        return current;
    }


    // s -> stream
    // f -> float
    private Object handleDocument( Document el ) {
        String type = el.getString( "_type" );
        if ( type.equals( "f" ) ) {
            return el.getDouble( "_obj" ).floatValue();
        } else if ( type.equals( "s" ) ) {
            // if we have inserted a document and have distributed chunks which we have to fetch
            ObjectId objectId = new ObjectId( (String) ((Document) current).get( "_id" ) );
            GridFSDownloadStream stream = bucket.openDownloadStream( objectId );
            return new PushbackInputStream( stream );
        }
        throw new RuntimeException( "The document type was not recognized" );
    }


    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }


    @Override
    public void close() {
        if ( cursor instanceof MongoCursor ) {
            ((MongoCursor) cursor).close();
        }
        // AggregationOutput implements Iterator but not DBCursor. There is no available close() method -- apparently there is no open resource.
    }


    static Function1<Document, Map> mapGetter() {
        return a0 -> (Map) a0;
    }


    static Function1<Document, Object> singletonGetter( final String fieldName, final Class fieldClass ) {
        return a0 -> convert( a0.get( fieldName ), fieldClass );
    }


    /**
     * @param fields List of fields to project; or null to return map
     */
    static Function1<Document, Object[]> listGetter( final List<Map.Entry<String, Class>> fields ) {
        return a0 -> {
            Object[] objects = new Object[fields.size()];
            for ( int i = 0; i < fields.size(); i++ ) {
                final Map.Entry<String, Class> field = fields.get( i );
                final String name = field.getKey();
                objects[i] = convert( a0.get( name ), field.getValue() );
            }
            return objects;
        };
    }


    static Function1<Document, Object> getter( List<Map.Entry<String, Class>> fields ) {
        //noinspection unchecked
        return fields == null
                ? (Function1) mapGetter()
                : fields.size() == 1
                        ? singletonGetter( fields.get( 0 ).getKey(), fields.get( 0 ).getValue() )
                        : (Function1) listGetter( fields );
    }


    private static Object convert( Object o, Class clazz ) {
        if ( o == null ) {
            return null;
        }
        Primitive primitive = Primitive.of( clazz );
        if ( primitive != null ) {
            clazz = primitive.boxClass;
        } else {
            primitive = Primitive.ofBox( clazz );
        }
        if ( clazz.isInstance( o ) ) {
            return o;
        }
        if ( o instanceof Date && primitive != null ) {
            o = ((Date) o).getTime() / DateTimeUtils.MILLIS_PER_DAY;
        }
        if ( o instanceof Number && primitive != null ) {
            return primitive.number( (Number) o );
        }

        if ( clazz.getName().equals( BigDecimal.class.getName() ) ) {
            //assert o instanceof Double; // todo dl maybe use to correct types -> ARRAY
            if ( o instanceof Double ) { // this should not happen anymore
                return BigDecimal.valueOf( (Double) o );
            } else {
                assert o instanceof Decimal128;
                return ((Decimal128) o).bigDecimalValue();
            }
        }

        return o;
    }


    public static class IterWrapper implements Enumerator<Object> {

        private final Iterator<Object> iterator;
        Object current;


        public IterWrapper( Iterator<Object> iterator ) {
            this.iterator = iterator;
        }


        @Override
        public Object current() {
            return current;
        }


        @Override
        public boolean moveNext() {

            if ( iterator.hasNext() ) {
                current = iterator.next();
                return true;
            } else {
                current = null;
                return false;
            }
        }


        @Override
        public void reset() {
            throw new UnsupportedOperationException();
        }


        @Override
        public void close() {
            // do nothing
            //throw new UnsupportedOperationException();
        }

    }


    static class ChangeMongoEnumerator implements Enumerator<Object> {

        private final Iterator<Integer> iterator;
        private Object current = null;
        private int left = 1;


        public ChangeMongoEnumerator( Iterator<Integer> iterator ) {
            this.iterator = iterator;
        }


        @Override
        public Object current() {
            return Collections.singletonList( current ).toArray();
        }


        @Override
        public boolean moveNext() {
            boolean res = left > 0;
            if ( res ) {
                left--;
                current = iterator.next();
            }

            return res;
        }


        @Override
        public void reset() {

        }


        @Override
        public void close() {

        }

    }

}

