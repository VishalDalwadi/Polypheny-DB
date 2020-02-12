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
 */

package org.polypheny.db;


import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogSchema;
import org.polypheny.db.information.InformationManager;
import org.polypheny.db.jdbc.Context;
import org.polypheny.db.prepare.PolyphenyDbCatalogReader;
import org.polypheny.db.schema.PolyphenyDbSchema;
import org.polypheny.db.sql.parser.SqlParser.SqlParserConfig;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public interface Transaction {

    PolyXid getXid();

    QueryProcessor getQueryProcessor();

    SqlProcessor getSqlProcessor( SqlParserConfig parserConfig );

    Catalog getCatalog();

    void commit() throws TransactionException;

    void rollback() throws TransactionException;

    void registerInvolvedStore( Store store );

    List<Store> getInvolvedStores();

    PolyphenyDbSchema getSchema();

    boolean isAnalyze();

    InformationManager getQueryAnalyzer();

    DataContext getDataContext();

    JavaTypeFactory getTypeFactory();

    AtomicBoolean getCancelFlag();

    Context getPrepareContext();

    CatalogSchema getDefaultSchema();

    PolyphenyDbCatalogReader getCatalogReader();

    void resetQueryProcessor();

    void addChangedTable( String qualifiedTableName );

}