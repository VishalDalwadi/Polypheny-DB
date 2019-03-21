/*
 * This file is based on code taken from the Apache Calcite project, which was released under the Apache License.
 * The changes are released under the MIT license.
 *
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
 *
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ch.unibas.dmi.dbis.polyphenydb.jdbc;


import ch.unibas.dmi.dbis.polyphenydb.DataContext;
import ch.unibas.dmi.dbis.polyphenydb.adapter.java.AbstractQueryableTable;
import ch.unibas.dmi.dbis.polyphenydb.adapter.java.JavaTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.PolyphenyDbPrepare.Context;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.PolyphenyDbPrepare.PolyphenyDbSignature;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.PolyphenyDbPrepare.Query;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeFactoryImpl;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeSystem;
import ch.unibas.dmi.dbis.polyphenydb.runtime.FlatLists;
import ch.unibas.dmi.dbis.polyphenydb.runtime.Hook;
import ch.unibas.dmi.dbis.polyphenydb.schema.Schema;
import ch.unibas.dmi.dbis.polyphenydb.schema.Schema.TableType;
import ch.unibas.dmi.dbis.polyphenydb.schema.SchemaPlus;
import ch.unibas.dmi.dbis.polyphenydb.schema.Table;
import ch.unibas.dmi.dbis.polyphenydb.schema.impl.AbstractTableQueryable;
import ch.unibas.dmi.dbis.polyphenydb.server.PolyphenyDbServerStatement;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlJdbcFunctionCall;
import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParser;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlTypeName;
import ch.unibas.dmi.dbis.polyphenydb.tools.FrameworkConfig;
import ch.unibas.dmi.dbis.polyphenydb.tools.Frameworks;
import ch.unibas.dmi.dbis.polyphenydb.util.Holder;
import ch.unibas.dmi.dbis.polyphenydb.util.Pair;
import ch.unibas.dmi.dbis.polyphenydb.util.Util;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.calcite.avatica.AvaticaStatement;
import org.apache.calcite.avatica.AvaticaUtils;
import org.apache.calcite.avatica.ColumnMetaData;
import org.apache.calcite.avatica.Meta;
import org.apache.calcite.avatica.MetaImpl;
import org.apache.calcite.avatica.NoSuchStatementException;
import org.apache.calcite.avatica.QueryState;
import org.apache.calcite.avatica.remote.TypedValue;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.function.Functions;
import org.apache.calcite.linq4j.function.Predicate1;


/**
 * Helper for implementing the {@code getXxx} methods such as {@link org.apache.calcite.avatica.AvaticaDatabaseMetaData#getTables}.
 */
public class PolyphenyDbMetaImpl extends MetaImpl {

    static final EmbeddedDriver EMBEDDED_DRIVER = new EmbeddedDriver();


    public PolyphenyDbMetaImpl( PolyphenyDbConnectionImpl connection ) {
        super( connection );
        this.connProps
                .setAutoCommit( false )
                .setReadOnly( false )
                .setTransactionIsolation( Connection.TRANSACTION_NONE );
        this.connProps.setDirty( false );
    }


    static <T extends Named> Predicate1<T> namedMatcher( final Pat pattern ) {
        if ( pattern.s == null || pattern.s.equals( "%" ) ) {
            return Functions.truePredicate1();
        }
        final Pattern regex = likeToRegex( pattern );
        return v1 -> regex.matcher( v1.getName() ).matches();
    }


    static Predicate1<String> matcher( final Pat pattern ) {
        if ( pattern.s == null || pattern.s.equals( "%" ) ) {
            return Functions.truePredicate1();
        }
        final Pattern regex = likeToRegex( pattern );
        return v1 -> regex.matcher( v1 ).matches();
    }


    /**
     * Converts a LIKE-style pattern (where '%' represents a wild-card, escaped using '\') to a Java regex.
     */
    public static Pattern likeToRegex( Pat pattern ) {
        StringBuilder buf = new StringBuilder( "^" );
        char[] charArray = pattern.s.toCharArray();
        int slash = -2;
        for ( int i = 0; i < charArray.length; i++ ) {
            char c = charArray[i];
            if ( slash == i - 1 ) {
                buf.append( '[' ).append( c ).append( ']' );
            } else {
                switch ( c ) {
                    case '\\':
                        slash = i;
                        break;
                    case '%':
                        buf.append( ".*" );
                        break;
                    case '[':
                        buf.append( "\\[" );
                        break;
                    case ']':
                        buf.append( "\\]" );
                        break;
                    default:
                        buf.append( '[' ).append( c ).append( ']' );
                }
            }
        }
        buf.append( "$" );
        return Pattern.compile( buf.toString() );
    }


    @Override
    public StatementHandle createStatement( ConnectionHandle ch ) {
        final StatementHandle h = super.createStatement( ch );
        final PolyphenyDbConnectionImpl connection = getConnection();
        connection.server.addStatement( connection, h );
        return h;
    }


    @Override
    public void closeStatement( StatementHandle h ) {
        final PolyphenyDbConnectionImpl connection = getConnection();
        final PolyphenyDbServerStatement stmt;
        try {
            stmt = connection.server.getStatement( h );
        } catch ( NoSuchStatementException e ) {
            // statement is not valid; nothing to do
            return;
        }
        // stmt.close(); // TODO: implement
        connection.server.removeStatement( h );
    }


    private <E> MetaResultSet createResultSet( Enumerable<E> enumerable, Class clazz, String... names ) {
        final List<ColumnMetaData> columns = new ArrayList<>();
        final List<Field> fields = new ArrayList<>();
        final List<String> fieldNames = new ArrayList<>();
        for ( String name : names ) {
            final int index = fields.size();
            final String fieldName = AvaticaUtils.toCamelCase( name );
            final Field field;
            try {
                field = clazz.getField( fieldName );
            } catch ( NoSuchFieldException e ) {
                throw new RuntimeException( e );
            }
            columns.add( columnMetaData( name, index, field.getType(), false ) );
            fields.add( field );
            fieldNames.add( fieldName );
        }
        //noinspection unchecked
        final Iterable<Object> iterable = (Iterable<Object>) (Iterable) enumerable;
        return createResultSet( Collections.emptyMap(), columns, CursorFactory.record( clazz, fields, fieldNames ), new Frame( 0, true, iterable ) );
    }


    @Override
    protected <E> MetaResultSet createEmptyResultSet( final Class<E> clazz ) {
        final List<ColumnMetaData> columns = fieldMetaData( clazz ).columns;
        final CursorFactory cursorFactory = CursorFactory.deduce( columns, clazz );
        return createResultSet( Collections.emptyMap(), columns, cursorFactory, Frame.EMPTY );
    }


    protected MetaResultSet createResultSet( Map<String, Object> internalParameters, List<ColumnMetaData> columns, CursorFactory cursorFactory, final Frame firstFrame ) {
        try {
            final PolyphenyDbConnectionImpl connection = getConnection();
            final AvaticaStatement statement = connection.createStatement();
            final PolyphenyDbSignature<Object> signature =
                    new PolyphenyDbSignature<Object>( "", ImmutableList.of(), internalParameters, null, columns, cursorFactory, null, ImmutableList.of(), -1, null, Meta.StatementType.SELECT ) {
                        @Override
                        public Enumerable<Object> enumerable( DataContext dataContext ) {
                            return Linq4j.asEnumerable( firstFrame.rows );
                        }
                    };
            return MetaResultSet.create( connection.id, statement.getId(), true, signature, firstFrame );
        } catch ( SQLException e ) {
            throw new RuntimeException( e );
        }
    }


    PolyphenyDbConnectionImpl getConnection() {
        return (PolyphenyDbConnectionImpl) connection;
    }


    @Override
    public Map<DatabaseProperty, Object> getDatabaseProperties( ConnectionHandle ch ) {
        final ImmutableMap.Builder<DatabaseProperty, Object> builder = ImmutableMap.builder();
        for ( DatabaseProperty p : DatabaseProperty.values() ) {
            addProperty( builder, p );
        }
        return builder.build();
    }


    private ImmutableMap.Builder<DatabaseProperty, Object> addProperty( ImmutableMap.Builder<DatabaseProperty, Object> builder, DatabaseProperty p ) {
        switch ( p ) {
            case GET_S_Q_L_KEYWORDS:
                return builder.put( p, SqlParser.create( "" ).getMetadata().getJdbcKeywords() );
            case GET_NUMERIC_FUNCTIONS:
                return builder.put( p, SqlJdbcFunctionCall.getNumericFunctions() );
            case GET_STRING_FUNCTIONS:
                return builder.put( p, SqlJdbcFunctionCall.getStringFunctions() );
            case GET_SYSTEM_FUNCTIONS:
                return builder.put( p, SqlJdbcFunctionCall.getSystemFunctions() );
            case GET_TIME_DATE_FUNCTIONS:
                return builder.put( p, SqlJdbcFunctionCall.getTimeDateFunctions() );
            default:
                return builder;
        }
    }


    public MetaResultSet getTables( ConnectionHandle ch, String catalog, final Pat schemaPattern, final Pat tableNamePattern, final List<String> typeList ) {
        final Predicate1<MetaTable> typeFilter;
        if ( typeList == null ) {
            typeFilter = Functions.truePredicate1();
        } else {
            typeFilter = v1 -> typeList.contains( v1.tableType );
        }
        final Predicate1<MetaSchema> schemaMatcher = namedMatcher( schemaPattern );
        return createResultSet(
                schemas( catalog )
                        .where( schemaMatcher )
                        .selectMany( schema -> tables( schema, matcher( tableNamePattern ) ) )
                        .where( typeFilter ),
                MetaTable.class,
                "TABLE_CAT",
                "TABLE_SCHEM",
                "TABLE_NAME",
                "TABLE_TYPE",
                "REMARKS",
                "TYPE_CAT",
                "TYPE_SCHEM",
                "TYPE_NAME",
                "SELF_REFERENCING_COL_NAME",
                "REF_GENERATION" );
    }


    public MetaResultSet getTypeInfo( ConnectionHandle ch ) {
        return createResultSet(
                allTypeInfo(),
                MetaTypeInfo.class,
                "TYPE_NAME",
                "DATA_TYPE",
                "PRECISION",
                "LITERAL_PREFIX",
                "LITERAL_SUFFIX",
                "CREATE_PARAMS",
                "NULLABLE",
                "CASE_SENSITIVE",
                "SEARCHABLE",
                "UNSIGNED_ATTRIBUTE",
                "FIXED_PREC_SCALE",
                "AUTO_INCREMENT",
                "LOCAL_TYPE_NAME",
                "MINIMUM_SCALE",
                "MAXIMUM_SCALE",
                "SQL_DATA_TYPE",
                "SQL_DATETIME_SUB",
                "NUM_PREC_RADIX" );
    }


    public MetaResultSet getColumns( ConnectionHandle ch, String catalog, Pat schemaPattern, Pat tableNamePattern, Pat columnNamePattern ) {
        final Predicate1<String> tableNameMatcher = matcher( tableNamePattern );
        final Predicate1<MetaSchema> schemaMatcher = namedMatcher( schemaPattern );
        final Predicate1<MetaColumn> columnMatcher = namedMatcher( columnNamePattern );
        return createResultSet(
                schemas( catalog )
                        .where( schemaMatcher )
                        .selectMany( schema -> tables( schema, tableNameMatcher ) )
                        .selectMany( this::columns )
                        .where( columnMatcher ),
                MetaColumn.class,
                "TABLE_CAT",
                "TABLE_SCHEM",
                "TABLE_NAME",
                "COLUMN_NAME",
                "DATA_TYPE",
                "TYPE_NAME",
                "COLUMN_SIZE",
                "BUFFER_LENGTH",
                "DECIMAL_DIGITS",
                "NUM_PREC_RADIX",
                "NULLABLE",
                "REMARKS",
                "COLUMN_DEF",
                "SQL_DATA_TYPE",
                "SQL_DATETIME_SUB",
                "CHAR_OCTET_LENGTH",
                "ORDINAL_POSITION",
                "IS_NULLABLE",
                "SCOPE_CATALOG",
                "SCOPE_SCHEMA",
                "SCOPE_TABLE",
                "SOURCE_DATA_TYPE",
                "IS_AUTOINCREMENT",
                "IS_GENERATEDCOLUMN" );
    }


    Enumerable<MetaCatalog> catalogs() {
        final String catalog;
        try {
            catalog = connection.getCatalog();
        } catch ( SQLException e ) {
            throw new RuntimeException( e );
        }
        return Linq4j.asEnumerable( ImmutableList.of( new MetaCatalog( catalog ) ) );
    }


    Enumerable<MetaTableType> tableTypes() {
        return Linq4j.asEnumerable( ImmutableList.of( new MetaTableType( "TABLE" ), new MetaTableType( "VIEW" ) ) );
    }


    Enumerable<MetaSchema> schemas( final String catalog ) {
        return Linq4j.asEnumerable( getConnection().rootSchema.getSubSchemaMap().values() )
                .select( (Function1<PolyphenyDbSchema, MetaSchema>) polyphenyDbSchema -> new PolyphenyDbMetaSchema( polyphenyDbSchema, catalog, polyphenyDbSchema.getName() ) )
                .orderBy( (Function1<MetaSchema, Comparable>) metaSchema -> (Comparable) FlatLists.of( Util.first( metaSchema.tableCatalog, "" ), metaSchema.tableSchem ) );
    }


    Enumerable<MetaTable> tables( String catalog ) {
        return schemas( catalog ).selectMany( schema -> tables( schema, Functions.<String>truePredicate1() ) );
    }


    Enumerable<MetaTable> tables( final MetaSchema schema_ ) {
        final PolyphenyDbMetaSchema schema = (PolyphenyDbMetaSchema) schema_;
        return Linq4j.asEnumerable( schema.polyphenyDbSchema.getTableNames() )
                .select( (Function1<String, MetaTable>) name -> {
                    final Table table = schema.polyphenyDbSchema.getTable( name, true ).getTable();
                    return new PolyphenyDbMetaTable( table, schema.tableCatalog, schema.tableSchem, name );
                } )
                .concat( Linq4j.asEnumerable(
                        schema.polyphenyDbSchema.getTablesBasedOnNullaryFunctions().entrySet() )
                        .select( pair -> {
                            final Table table = pair.getValue();
                            return new PolyphenyDbMetaTable( table, schema.tableCatalog, schema.tableSchem, pair.getKey() );
                        } ) );
    }


    Enumerable<MetaTable> tables( final MetaSchema schema, final Predicate1<String> matcher ) {
        return tables( schema ).where( v1 -> matcher.apply( v1.getName() ) );
    }


    private ImmutableList<MetaTypeInfo> getAllDefaultType() {
        final ImmutableList.Builder<MetaTypeInfo> allTypeList = ImmutableList.builder();
        final PolyphenyDbConnectionImpl conn = (PolyphenyDbConnectionImpl) connection;
        final RelDataTypeSystem typeSystem = conn.typeFactory.getTypeSystem();
        for ( SqlTypeName sqlTypeName : SqlTypeName.values() ) {
            allTypeList.add(
                    new MetaTypeInfo(
                            sqlTypeName.getName(),
                            sqlTypeName.getJdbcOrdinal(),
                            typeSystem.getMaxPrecision( sqlTypeName ),
                            typeSystem.getLiteral( sqlTypeName, true ),
                            typeSystem.getLiteral( sqlTypeName, false ),
                            // All types are nullable
                            (short) DatabaseMetaData.typeNullable,
                            typeSystem.isCaseSensitive( sqlTypeName ),
                            // Making all type searchable; we may want to
                            // be specific and declare under SqlTypeName
                            (short) DatabaseMetaData.typeSearchable,
                            false,
                            false,
                            typeSystem.isAutoincrement( sqlTypeName ),
                            (short) sqlTypeName.getMinScale(),
                            (short) typeSystem.getMaxScale( sqlTypeName ),
                            typeSystem.getNumTypeRadix( sqlTypeName ) ) );
        }
        return allTypeList.build();
    }


    protected Enumerable<MetaTypeInfo> allTypeInfo() {
        return Linq4j.asEnumerable( getAllDefaultType() );
    }


    public Enumerable<MetaColumn> columns( final MetaTable table_ ) {
        final PolyphenyDbMetaTable table = (PolyphenyDbMetaTable) table_;
        final RelDataType rowType = table.polyphenyDbTable.getRowType( getConnection().typeFactory );
        return Linq4j.asEnumerable( rowType.getFieldList() )
                .select( field -> {
                    final int precision =
                            field.getType().getSqlTypeName().allowsPrec() && !(field.getType() instanceof RelDataTypeFactoryImpl.JavaType)
                                    ? field.getType().getPrecision()
                                    : -1;
                    return new MetaColumn(
                            table.tableCat,
                            table.tableSchem,
                            table.tableName,
                            field.getName(),
                            field.getType().getSqlTypeName().getJdbcOrdinal(),
                            field.getType().getFullTypeString(),
                            precision,
                            field.getType().getSqlTypeName().allowsScale()
                                    ? field.getType().getScale()
                                    : null,
                            10,
                            field.getType().isNullable()
                                    ? DatabaseMetaData.columnNullable
                                    : DatabaseMetaData.columnNoNulls,
                            precision,
                            field.getIndex() + 1,
                            field.getType().isNullable() ? "YES" : "NO" );
                } );
    }


    public MetaResultSet getSchemas( ConnectionHandle ch, String catalog, Pat schemaPattern ) {
        final Predicate1<MetaSchema> schemaMatcher = namedMatcher( schemaPattern );
        return createResultSet(
                schemas( catalog ).where( schemaMatcher ),
                MetaSchema.class,
                "TABLE_SCHEM",
                "TABLE_CATALOG" );
    }


    public MetaResultSet getCatalogs( ConnectionHandle ch ) {
        return createResultSet( catalogs(), MetaCatalog.class, "TABLE_CAT" );
    }


    public MetaResultSet getTableTypes( ConnectionHandle ch ) {
        return createResultSet( tableTypes(), MetaTableType.class, "TABLE_TYPE" );
    }


    @Override
    public Iterable<Object> createIterable( StatementHandle handle, QueryState state, Signature signature, List<TypedValue> parameterValues, Frame firstFrame ) {
        // Drop QueryState
        return _createIterable( handle, signature, parameterValues, firstFrame );
    }


    Iterable<Object> _createIterable( StatementHandle handle, Signature signature, List<TypedValue> parameterValues, Frame firstFrame ) {
        try {
            //noinspection unchecked
            final PolyphenyDbSignature<Object> polyphenyDbSignature = (PolyphenyDbSignature<Object>) signature;
            return getConnection().enumerable( handle, polyphenyDbSignature );
        } catch ( SQLException e ) {
            throw new RuntimeException( e.getMessage() );
        }
    }


    @Override
    public StatementHandle prepare( ConnectionHandle ch, String sql, long maxRowCount ) {
        final StatementHandle h = createStatement( ch );
        final PolyphenyDbConnectionImpl connection = getConnection();

        final PolyphenyDbServerStatement statement;
        try {
            statement = connection.server.getStatement( h );
        } catch ( NoSuchStatementException e ) {
            // Not possible. We just created a statement.
            throw new AssertionError( "missing statement", e );
        }
        final Context context = statement.createPrepareContext();
        final PolyphenyDbPrepare.Query<Object> query = toQuery( context, sql );
        h.signature = connection.parseQuery( query, context, maxRowCount );
        statement.setSignature( h.signature );
        return h;
    }


    @SuppressWarnings("deprecation")
    @Override
    public ExecuteResult prepareAndExecute( StatementHandle h, String sql, long maxRowCount, PrepareCallback callback ) throws NoSuchStatementException {
        return prepareAndExecute( h, sql, maxRowCount, -1, callback );
    }


    @Override
    public ExecuteResult prepareAndExecute( StatementHandle h, String sql, long maxRowCount, int maxRowsInFirstFrame, PrepareCallback callback ) throws NoSuchStatementException {
        final PolyphenyDbSignature<Object> signature;
        try {
            synchronized ( callback.getMonitor() ) {
                callback.clear();
                final PolyphenyDbConnectionImpl connection = getConnection();
                final PolyphenyDbServerStatement statement = connection.server.getStatement( h );
                final Context context = statement.createPrepareContext();
                final PolyphenyDbPrepare.Query<Object> query = toQuery( context, sql );
                signature = connection.parseQuery( query, context, maxRowCount );
                statement.setSignature( signature );
                final int updateCount;
                switch ( signature.statementType ) {
                    case CREATE:
                    case DROP:
                    case ALTER:
                    case OTHER_DDL:
                        updateCount = 0; // DDL produces no result set
                        break;
                    default:
                        updateCount = -1; // SELECT and DML produces result set
                        break;
                }
                callback.assign( signature, null, updateCount );
            }
            callback.execute();
            final MetaResultSet metaResultSet = MetaResultSet.create( h.connectionId, h.id, false, signature, null );
            return new ExecuteResult( ImmutableList.of( metaResultSet ) );
        } catch ( SQLException e ) {
            throw new RuntimeException( e );
        }
        // TODO: share code with prepare and createIterable
    }


    /**
     * Wraps the SQL string in a
     * {@link Query} object, giving the
     * {@link Hook#STRING_TO_QUERY} hook chance to override.
     */
    private PolyphenyDbPrepare.Query<Object> toQuery( Context context, String sql ) {
        final Holder<PolyphenyDbPrepare.Query<Object>> queryHolder = Holder.of( PolyphenyDbPrepare.Query.of( sql ) );
        final FrameworkConfig config = Frameworks.newConfigBuilder()
                .parserConfig( SqlParser.Config.DEFAULT )
                .defaultSchema( context.getRootSchema().plus() )
                .build();
        Hook.STRING_TO_QUERY.run( Pair.of( config, queryHolder ) );
        return queryHolder.get();
    }


    @Override
    public Frame fetch( StatementHandle h, long offset, int fetchMaxRowCount ) throws NoSuchStatementException {
        final PolyphenyDbConnectionImpl connection = getConnection();
        PolyphenyDbServerStatement stmt = connection.server.getStatement( h );
        final Signature signature = stmt.getSignature();
        final Iterator<Object> iterator;
        if ( stmt.getResultSet() == null ) {
            final Iterable<Object> iterable = _createIterable( h, signature, null, null );
            iterator = iterable.iterator();
            stmt.setResultSet( iterator );
        } else {
            iterator = stmt.getResultSet();
        }
        final List rows = MetaImpl.collect( signature.cursorFactory, LimitIterator.of( iterator, fetchMaxRowCount ), new ArrayList<List<Object>>() );
        boolean done = fetchMaxRowCount == 0 || rows.size() < fetchMaxRowCount;
        @SuppressWarnings("unchecked") List<Object> rows1 = (List<Object>) rows;
        return new Meta.Frame( offset, done, rows1 );
    }


    @SuppressWarnings("deprecation")
    @Override
    public ExecuteResult execute( StatementHandle h, List<TypedValue> parameterValues, long maxRowCount ) throws NoSuchStatementException {
        return execute( h, parameterValues, Ints.saturatedCast( maxRowCount ) );
    }


    @Override
    public ExecuteResult execute( StatementHandle h, List<TypedValue> parameterValues, int maxRowsInFirstFrame ) throws NoSuchStatementException {
        final PolyphenyDbConnectionImpl connection = getConnection();
        PolyphenyDbServerStatement stmt = connection.server.getStatement( h );
        final Signature signature = stmt.getSignature();

        MetaResultSet metaResultSet;
        if ( signature.statementType.canUpdate() ) {
            final Iterable<Object> iterable = _createIterable( h, signature, parameterValues, null );
            final Iterator<Object> iterator = iterable.iterator();
            stmt.setResultSet( iterator );
            metaResultSet = MetaResultSet.count( h.connectionId, h.id, ((Number) iterator.next()).intValue() );
        } else {
            // Don't populate the first frame.
            // It's not worth saving a round-trip, since we're local.
            final Meta.Frame frame = new Meta.Frame( 0, false, Collections.emptyList() );
            metaResultSet = MetaResultSet.create( h.connectionId, h.id, false, signature, frame );
        }

        return new ExecuteResult( ImmutableList.of( metaResultSet ) );
    }


    @Override
    public ExecuteBatchResult executeBatch( StatementHandle h, List<List<TypedValue>> parameterValueLists ) throws NoSuchStatementException {
        final List<Long> updateCounts = new ArrayList<>();
        for ( List<TypedValue> parameterValueList : parameterValueLists ) {
            ExecuteResult executeResult = execute( h, parameterValueList, -1 );
            final long updateCount =
                    executeResult.resultSets.size() == 1
                            ? executeResult.resultSets.get( 0 ).updateCount
                            : -1L;
            updateCounts.add( updateCount );
        }
        return new ExecuteBatchResult( Longs.toArray( updateCounts ) );
    }


    @Override
    public ExecuteBatchResult prepareAndExecuteBatch( final StatementHandle h, List<String> sqlCommands ) throws NoSuchStatementException {
        final PolyphenyDbConnectionImpl connection = getConnection();
        final PolyphenyDbServerStatement statement = connection.server.getStatement( h );
        final List<Long> updateCounts = new ArrayList<>();
        final Meta.PrepareCallback callback =
                new Meta.PrepareCallback() {
                    long updateCount;
                    Signature signature;


                    public Object getMonitor() {
                        return statement;
                    }


                    public void clear() throws SQLException {
                    }


                    public void assign( Meta.Signature signature, Meta.Frame firstFrame, long updateCount ) throws SQLException {
                        this.signature = signature;
                        this.updateCount = updateCount;
                    }


                    public void execute() throws SQLException {
                        if ( signature.statementType.canUpdate() ) {
                            final Iterable<Object> iterable = _createIterable( h, signature, ImmutableList.of(), null );
                            final Iterator<Object> iterator = iterable.iterator();
                            updateCount = ((Number) iterator.next()).longValue();
                        }
                        updateCounts.add( updateCount );
                    }
                };
        for ( String sqlCommand : sqlCommands ) {
            Util.discard( prepareAndExecute( h, sqlCommand, -1L, -1, callback ) );
        }
        return new ExecuteBatchResult( Longs.toArray( updateCounts ) );
    }


    /**
     * A trojan-horse method, subject to change without notice.
     */
    @VisibleForTesting
    public static DataContext createDataContext( PolyphenyDbConnection connection ) {
        return ((PolyphenyDbConnectionImpl) connection).createDataContext( ImmutableMap.of(), PolyphenyDbSchema.from( connection.getRootSchema() ) );
    }


    /**
     * A trojan-horse method, subject to change without notice.
     */
    @VisibleForTesting
    public static PolyphenyDbConnection connect( PolyphenyDbSchema schema, JavaTypeFactory typeFactory ) {
        return EMBEDDED_DRIVER.connect( schema, typeFactory );
    }


    public boolean syncResults( StatementHandle h, QueryState state, long offset ) throws NoSuchStatementException {
        // Doesn't have application in Polypheny-DB itself.
        throw new UnsupportedOperationException();
    }


    @Override
    public void commit( ConnectionHandle ch ) {
        throw new UnsupportedOperationException();
    }


    @Override
    public void rollback( ConnectionHandle ch ) {
        throw new UnsupportedOperationException();
    }


    /**
     * Metadata describing a Polypheny-DB table.
     */
    private static class PolyphenyDbMetaTable extends MetaTable {

        private final Table polyphenyDbTable;


        PolyphenyDbMetaTable( Table polyphenyDbTable, String tableCat, String tableSchem, String tableName ) {
            super( tableCat, tableSchem, tableName, polyphenyDbTable.getJdbcTableType().jdbcName );
            this.polyphenyDbTable = Objects.requireNonNull( polyphenyDbTable );
        }
    }


    /**
     * Metadata describing a Polypheny-DB schema.
     */
    private static class PolyphenyDbMetaSchema extends MetaSchema {

        private final PolyphenyDbSchema polyphenyDbSchema;


        PolyphenyDbMetaSchema( PolyphenyDbSchema polyphenyDbSchema, String tableCatalog, String tableSchem ) {
            super( tableCatalog, tableSchem );
            this.polyphenyDbSchema = polyphenyDbSchema;
        }
    }


    /**
     * Table whose contents are metadata.
     *
     * @param <E> element type
     */
    abstract static class MetadataTable<E> extends AbstractQueryableTable {

        MetadataTable( Class<E> clazz ) {
            super( clazz );
        }


        public RelDataType getRowType( RelDataTypeFactory typeFactory ) {
            return ((JavaTypeFactory) typeFactory).createType( elementType );
        }


        @Override
        public TableType getJdbcTableType() {
            return Schema.TableType.SYSTEM_TABLE;
        }


        @SuppressWarnings("unchecked")
        @Override
        public Class<E> getElementType() {
            return (Class<E>) elementType;
        }


        protected abstract Enumerator<E> enumerator( PolyphenyDbMetaImpl connection );


        public <T> Queryable<T> asQueryable( QueryProvider queryProvider, SchemaPlus schema, String tableName ) {
            return new AbstractTableQueryable<T>( queryProvider, schema, this, tableName ) {
                @SuppressWarnings("unchecked")
                public Enumerator<T> enumerator() {
                    return (Enumerator<T>) MetadataTable.this.enumerator( ((PolyphenyDbConnectionImpl) queryProvider).meta() );
                }
            };
        }
    }


    /**
     * Iterator that returns at most {@code limit} rows from an underlying {@link Iterator}.
     *
     * @param <E> element type
     */
    private static class LimitIterator<E> implements Iterator<E> {

        private final Iterator<E> iterator;
        private final long limit;
        int i = 0;


        private LimitIterator( Iterator<E> iterator, long limit ) {
            this.iterator = iterator;
            this.limit = limit;
        }


        static <E> Iterator<E> of( Iterator<E> iterator, long limit ) {
            if ( limit <= 0 ) {
                return iterator;
            }
            return new LimitIterator<>( iterator, limit );
        }


        public boolean hasNext() {
            return iterator.hasNext() && i < limit;
        }


        public E next() {
            ++i;
            return iterator.next();
        }


        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}

