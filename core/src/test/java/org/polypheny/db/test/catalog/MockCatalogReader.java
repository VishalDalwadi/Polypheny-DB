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

package org.polypheny.db.test.catalog;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.linq4j.tree.Expression;
import org.polypheny.db.adapter.DataContext;
import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.catalog.Catalog.SchemaType;
import org.polypheny.db.jdbc.PolyphenyDbPrepare.AnalyzeViewResult;
import org.polypheny.db.plan.RelOptSchema;
import org.polypheny.db.plan.RelOptTable;
import org.polypheny.db.prepare.PolyphenyDbCatalogReader;
import org.polypheny.db.prepare.Prepare;
import org.polypheny.db.rel.RelCollation;
import org.polypheny.db.rel.RelCollations;
import org.polypheny.db.rel.RelDistribution;
import org.polypheny.db.rel.RelDistributions;
import org.polypheny.db.rel.RelFieldCollation;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.RelReferentialConstraint;
import org.polypheny.db.rel.logical.LogicalFilter;
import org.polypheny.db.rel.logical.LogicalProject;
import org.polypheny.db.rel.logical.LogicalTableScan;
import org.polypheny.db.rel.type.DynamicRecordTypeImpl;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeComparability;
import org.polypheny.db.rel.type.RelDataTypeFactory;
import org.polypheny.db.rel.type.RelDataTypeFamily;
import org.polypheny.db.rel.type.RelDataTypeField;
import org.polypheny.db.rel.type.RelDataTypeImpl;
import org.polypheny.db.rel.type.RelDataTypePrecedenceList;
import org.polypheny.db.rel.type.RelProtoDataType;
import org.polypheny.db.rel.type.RelRecordType;
import org.polypheny.db.rel.type.StructKind;
import org.polypheny.db.rex.RexBuilder;
import org.polypheny.db.rex.RexInputRef;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.schema.AbstractPolyphenyDbSchema;
import org.polypheny.db.schema.CustomColumnResolvingTable;
import org.polypheny.db.schema.ExtensibleTable;
import org.polypheny.db.schema.Path;
import org.polypheny.db.schema.PolyphenyDbSchema;
import org.polypheny.db.schema.Schema;
import org.polypheny.db.schema.SchemaPlus;
import org.polypheny.db.schema.Schemas;
import org.polypheny.db.schema.Statistic;
import org.polypheny.db.schema.StreamableTable;
import org.polypheny.db.schema.Table;
import org.polypheny.db.schema.Wrapper;
import org.polypheny.db.schema.impl.AbstractSchema;
import org.polypheny.db.schema.impl.ModifiableViewTable;
import org.polypheny.db.schema.impl.ViewTableMacro;
import org.polypheny.db.sql.SqlAccessType;
import org.polypheny.db.sql.SqlCall;
import org.polypheny.db.sql.SqlCollation;
import org.polypheny.db.sql.SqlIdentifier;
import org.polypheny.db.sql.SqlIntervalQualifier;
import org.polypheny.db.sql.SqlKind;
import org.polypheny.db.sql.SqlNode;
import org.polypheny.db.sql.validate.SqlModality;
import org.polypheny.db.sql.validate.SqlMonotonicity;
import org.polypheny.db.sql.validate.SqlNameMatcher;
import org.polypheny.db.sql.validate.SqlNameMatchers;
import org.polypheny.db.sql.validate.SqlValidatorCatalogReader;
import org.polypheny.db.sql.validate.SqlValidatorUtil;
import org.polypheny.db.sql2rel.InitializerExpressionFactory;
import org.polypheny.db.sql2rel.NullInitializerExpressionFactory;
import org.polypheny.db.test.JdbcTest;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.ImmutableBitSet;
import org.polypheny.db.util.ImmutableIntList;
import org.polypheny.db.util.Pair;
import org.polypheny.db.util.Util;


/**
 * Mock implementation of {@link SqlValidatorCatalogReader} which returns tables "EMP", "DEPT", "BONUS", "SALGRADE" (same as Oracle's SCOTT schema).
 * Also two streams "ORDERS", "SHIPMENTS"; and a view "EMP_20".
 */
public abstract class MockCatalogReader extends PolyphenyDbCatalogReader {

    static final String DEFAULT_CATALOG = "CATALOG";
    static final String DEFAULT_SCHEMA = "SALES";
    static final List<String> PREFIX = ImmutableList.of( DEFAULT_SCHEMA );


    /**
     * Creates a MockCatalogReader.
     *
     * Caller must then call {@link #init} to populate with data.
     *
     * @param typeFactory Type factory
     */
    public MockCatalogReader( RelDataTypeFactory typeFactory, boolean caseSensitive ) {
        super(
                AbstractPolyphenyDbSchema.createRootSchema( DEFAULT_CATALOG ),
                SqlNameMatchers.withCaseSensitive( caseSensitive ),
                ImmutableList.of( PREFIX, ImmutableList.of() ),
                typeFactory );
    }


    @Override
    public SqlNameMatcher nameMatcher() {
        return nameMatcher;
    }


    /**
     * Initializes this catalog reader.
     */
    public abstract MockCatalogReader init();


    protected void registerTablesWithRollUp( MockSchema schema, Fixture f ) {
        // Register "EMP_R" table. Contains a rolled up column.
        final MockTable empRolledTable = MockTable.create( this, schema, "EMP_R", false, 14 );
        empRolledTable.addColumn( "EMPNO", f.intType, true );
        empRolledTable.addColumn( "DEPTNO", f.intType );
        empRolledTable.addColumn( "SLACKER", f.booleanType );
        empRolledTable.addColumn( "SLACKINGMIN", f.intType );
        empRolledTable.registerRolledUpColumn( "SLACKINGMIN" );
        registerTable( empRolledTable );

        // Register the "DEPT_R" table. Doesn't contain a rolled up column,
        // but is useful for testing join
        MockTable deptSlackingTable = MockTable.create( this, schema, "DEPT_R", false, 4 );
        deptSlackingTable.addColumn( "DEPTNO", f.intType, true );
        deptSlackingTable.addColumn( "SLACKINGMIN", f.intType );
        registerTable( deptSlackingTable );

        // Register nested schema NEST that contains table with a rolled up column.
        MockSchema nestedSchema = new MockSchema( "NEST" );
        registerNestedSchema( schema, nestedSchema );

        // Register "EMP_R" table which contains a rolled up column in NEST schema.
        ImmutableList<String> tablePath = ImmutableList.of( schema.getCatalogName(), schema.name, nestedSchema.name, "EMP_R" );
        final MockTable nestedEmpRolledTable = MockTable.create( this, tablePath, false, 14 );
        nestedEmpRolledTable.addColumn( "EMPNO", f.intType, true );
        nestedEmpRolledTable.addColumn( "DEPTNO", f.intType );
        nestedEmpRolledTable.addColumn( "SLACKER", f.booleanType );
        nestedEmpRolledTable.addColumn( "SLACKINGMIN", f.intType );
        nestedEmpRolledTable.registerRolledUpColumn( "SLACKINGMIN" );
        registerTable( nestedEmpRolledTable );
    }


    protected void registerType( final List<String> names, final RelProtoDataType relProtoDataType ) {
        assert names.get( 0 ).equals( DEFAULT_CATALOG );
        final List<String> schemaPath = Util.skipLast( names );
        final PolyphenyDbSchema schema = SqlValidatorUtil.getSchema( rootSchema, schemaPath, SqlNameMatchers.withCaseSensitive( true ) );
        schema.add( Util.last( names ), relProtoDataType );
    }


    protected void registerTable( final MockTable table ) {
        table.onRegister( typeFactory );
        final WrapperTable wrapperTable = new WrapperTable( table );
        if ( table.stream ) {
            registerTable( table.names,
                    new StreamableWrapperTable( table ) {
                        @Override
                        public Table stream() {
                            return wrapperTable;
                        }
                    } );
        } else {
            registerTable( table.names, wrapperTable );
        }
    }


    private void registerTable( final List<String> names, final Table table ) {
        assert names.get( 0 ).equals( DEFAULT_CATALOG );
        final List<String> schemaPath = Util.skipLast( names );
        final String tableName = Util.last( names );
        final PolyphenyDbSchema schema = SqlValidatorUtil.getSchema( rootSchema, schemaPath, SqlNameMatchers.withCaseSensitive( true ) );
        schema.add( tableName, table );
    }


    protected void registerSchema( MockSchema schema ) {
        rootSchema.add( schema.name, new AbstractSchema(), SchemaType.RELATIONAL );
    }


    private void registerNestedSchema( MockSchema parentSchema, MockSchema schema ) {
        rootSchema.getSubSchema( parentSchema.getName(), true ).add( schema.name, new AbstractSchema(), SchemaType.RELATIONAL );
    }


    private static List<RelCollation> deduceMonotonicity( Prepare.PreparingTable table ) {
        final List<RelCollation> collationList = new ArrayList<>();

        // Deduce which fields the table is sorted on.
        int i = -1;
        for ( RelDataTypeField field : table.getRowType().getFieldList() ) {
            ++i;
            final SqlMonotonicity monotonicity = table.getMonotonicity( field.getName() );
            if ( monotonicity != SqlMonotonicity.NOT_MONOTONIC ) {
                final RelFieldCollation.Direction direction =
                        monotonicity.isDecreasing()
                                ? RelFieldCollation.Direction.DESCENDING
                                : RelFieldCollation.Direction.ASCENDING;
                collationList.add( RelCollations.of( new RelFieldCollation( i, direction ) ) );
            }
        }
        return collationList;
    }


    /**
     * Column resolver
     */
    public interface ColumnResolver {

        List<Pair<RelDataTypeField, List<String>>> resolveColumn( RelDataType rowType, RelDataTypeFactory typeFactory, List<String> names );

    }


    /**
     * Mock schema.
     */
    public static class MockSchema {

        private final List<String> tableNames = new ArrayList<>();
        private String name;


        public MockSchema( String name ) {
            this.name = name;
        }


        public void addTable( String name ) {
            tableNames.add( name );
        }


        public String getCatalogName() {
            return DEFAULT_CATALOG;
        }


        public String getName() {
            return name;
        }

    }


    /**
     * Mock implementation of
     * {@link Prepare.PreparingTable}.
     */
    public static class MockTable extends Prepare.AbstractPreparingTable {

        protected final MockCatalogReader catalogReader;
        protected final boolean stream;
        protected final double rowCount;
        protected final List<Map.Entry<String, RelDataType>> columnList = new ArrayList<>();
        protected final List<Integer> keyList = new ArrayList<>();
        protected final List<RelReferentialConstraint> referentialConstraints = new ArrayList<>();
        protected RelDataType rowType;
        protected List<RelCollation> collationList;
        protected final List<String> names;
        protected final Set<String> monotonicColumnSet = new HashSet<>();
        protected StructKind kind = StructKind.FULLY_QUALIFIED;
        protected final ColumnResolver resolver;
        protected final InitializerExpressionFactory initializerFactory;
        protected final Set<String> rolledUpColumns = new HashSet<>();


        public MockTable( MockCatalogReader catalogReader, String catalogName, String schemaName, String name, boolean stream, double rowCount,
                ColumnResolver resolver, InitializerExpressionFactory initializerFactory ) {
            this( catalogReader, ImmutableList.of( catalogName, schemaName, name ), stream, rowCount, resolver, initializerFactory );
        }


        public void registerRolledUpColumn( String columnName ) {
            rolledUpColumns.add( columnName );
        }


        private MockTable( MockCatalogReader catalogReader, List<String> names, boolean stream, double rowCount, ColumnResolver resolver, InitializerExpressionFactory initializerFactory ) {
            this.catalogReader = catalogReader;
            this.stream = stream;
            this.rowCount = rowCount;
            this.names = names;
            this.resolver = resolver;
            this.initializerFactory = initializerFactory;
        }


        /**
         * Copy constructor.
         */
        protected MockTable( MockCatalogReader catalogReader, boolean stream, double rowCount, List<Map.Entry<String, RelDataType>> columnList, List<Integer> keyList, RelDataType rowType, List<RelCollation> collationList,
                List<String> names, Set<String> monotonicColumnSet, StructKind kind, ColumnResolver resolver, InitializerExpressionFactory initializerFactory ) {
            this.catalogReader = catalogReader;
            this.stream = stream;
            this.rowCount = rowCount;
            this.rowType = rowType;
            this.collationList = collationList;
            this.names = names;
            this.kind = kind;
            this.resolver = resolver;
            this.initializerFactory = initializerFactory;
            for ( String name : monotonicColumnSet ) {
                addMonotonic( name );
            }
        }


        /**
         * Implementation of AbstractModifiableTable.
         */
        private class ModifiableTable extends JdbcTest.AbstractModifiableTable implements ExtensibleTable, Wrapper {

            protected ModifiableTable( String tableName ) {
                super( tableName );
            }


            @Override
            public RelDataType getRowType( RelDataTypeFactory typeFactory ) {
                return typeFactory.createStructType( MockTable.this.getRowType().getFieldList() );
            }


            @Override
            public Collection getModifiableCollection() {
                return null;
            }


            @Override
            public <E> Queryable<E> asQueryable( DataContext dataContext, SchemaPlus schema, String tableName ) {
                return null;
            }


            @Override
            public Type getElementType() {
                return null;
            }


            @Override
            public Expression getExpression( SchemaPlus schema, String tableName, Class clazz ) {
                return null;
            }


            @Override
            public <C> C unwrap( Class<C> aClass ) {
                if ( aClass.isInstance( initializerFactory ) ) {
                    return aClass.cast( initializerFactory );
                } else if ( aClass.isInstance( MockTable.this ) ) {
                    return aClass.cast( MockTable.this );
                }
                return super.unwrap( aClass );
            }


            @Override
            public Table extend( final List<RelDataTypeField> fields ) {
                return new ModifiableTable( Util.last( names ) ) {
                    @Override
                    public RelDataType getRowType( RelDataTypeFactory typeFactory ) {
                        ImmutableList<RelDataTypeField> allFields = ImmutableList.copyOf( Iterables.concat( ModifiableTable.this.getRowType( typeFactory ).getFieldList(), fields ) );
                        return typeFactory.createStructType( allFields );
                    }
                };
            }


            @Override
            public int getExtendedColumnOffset() {
                return rowType.getFieldCount();
            }

        }


        @Override
        protected RelOptTable extend( final Table extendedTable ) {
            return new MockTable( catalogReader, names, stream, rowCount, resolver, initializerFactory ) {
                @Override
                public RelDataType getRowType() {
                    return extendedTable.getRowType( catalogReader.typeFactory );
                }
            };
        }


        public static MockTable create( MockCatalogReader catalogReader, MockSchema schema, String name, boolean stream, double rowCount ) {
            return create( catalogReader, schema, name, stream, rowCount, null );
        }


        public static MockTable create( MockCatalogReader catalogReader, List<String> names, boolean stream, double rowCount ) {
            return new MockTable( catalogReader, names, stream, rowCount, null, NullInitializerExpressionFactory.INSTANCE );
        }


        public static MockTable create( MockCatalogReader catalogReader,
                MockSchema schema, String name, boolean stream, double rowCount,
                ColumnResolver resolver ) {
            return create( catalogReader, schema, name, stream, rowCount, resolver,
                    NullInitializerExpressionFactory.INSTANCE );
        }


        public static MockTable create( MockCatalogReader catalogReader, MockSchema schema, String name, boolean stream, double rowCount, ColumnResolver resolver, InitializerExpressionFactory initializerExpressionFactory ) {
            MockTable table = new MockTable( catalogReader, schema.getCatalogName(), schema.name, name, stream, rowCount, resolver, initializerExpressionFactory );
            schema.addTable( name );
            return table;
        }


        @Override
        public <T> T unwrap( Class<T> clazz ) {
            if ( clazz.isInstance( this ) ) {
                return clazz.cast( this );
            }
            if ( clazz.isInstance( initializerFactory ) ) {
                return clazz.cast( initializerFactory );
            }
            if ( clazz.isAssignableFrom( Table.class ) ) {
                final Table table = resolver == null
                        ? new ModifiableTable( Util.last( names ) )
                        : new ModifiableTableWithCustomColumnResolving( Util.last( names ) );
                return clazz.cast( table );
            }
            return null;
        }


        @Override
        public double getRowCount() {
            return rowCount;
        }


        @Override
        public RelOptSchema getRelOptSchema() {
            return catalogReader;
        }


        @Override
        public RelNode toRel( ToRelContext context ) {
            return LogicalTableScan.create( context.getCluster(), this );
        }


        @Override
        public List<RelCollation> getCollationList() {
            return collationList;
        }


        @Override
        public RelDistribution getDistribution() {
            return RelDistributions.BROADCAST_DISTRIBUTED;
        }


        @Override
        public boolean isKey( ImmutableBitSet columns ) {
            return !keyList.isEmpty() && columns.contains( ImmutableBitSet.of( keyList ) );
        }


        @Override
        public List<RelReferentialConstraint> getReferentialConstraints() {
            return referentialConstraints;
        }


        @Override
        public RelDataType getRowType() {
            return rowType;
        }


        @Override
        public boolean supportsModality( SqlModality modality ) {
            return modality == (stream ? SqlModality.STREAM : SqlModality.RELATION);
        }


        public void onRegister( RelDataTypeFactory typeFactory ) {
            rowType = typeFactory.createStructType( kind, Pair.right( columnList ), Pair.left( columnList ) );
            collationList = deduceMonotonicity( this );
        }


        @Override
        public List<String> getQualifiedName() {
            return names;
        }


        @Override
        public SqlMonotonicity getMonotonicity( String columnName ) {
            return monotonicColumnSet.contains( columnName )
                    ? SqlMonotonicity.INCREASING
                    : SqlMonotonicity.NOT_MONOTONIC;
        }


        @Override
        public SqlAccessType getAllowedAccess() {
            return SqlAccessType.ALL;
        }


        @Override
        public Expression getExpression( Class clazz ) {
            throw new UnsupportedOperationException();
        }


        public void addColumn( String name, RelDataType type ) {
            addColumn( name, type, false );
        }


        public void addColumn( String name, RelDataType type, boolean isKey ) {
            if ( isKey ) {
                keyList.add( columnList.size() );
            }
            columnList.add( Pair.of( name, type ) );
        }


        public void addMonotonic( String name ) {
            monotonicColumnSet.add( name );
            assert Pair.left( columnList ).contains( name );
        }


        public void setKind( StructKind kind ) {
            this.kind = kind;
        }


        public StructKind getKind() {
            return kind;
        }


        /**
         * Subclass of {@link ModifiableTable} that also implements {@link CustomColumnResolvingTable}.
         */
        private class ModifiableTableWithCustomColumnResolving extends ModifiableTable implements CustomColumnResolvingTable, Wrapper {

            ModifiableTableWithCustomColumnResolving( String tableName ) {
                super( tableName );
            }


            @Override
            public List<Pair<RelDataTypeField, List<String>>> resolveColumn( RelDataType rowType, RelDataTypeFactory typeFactory, List<String> names ) {
                return resolver.resolveColumn( rowType, typeFactory, names );
            }

        }

    }


    /**
     * Alternative to MockViewTable that exercises code paths in ModifiableViewTable and ModifiableViewTableInitializerExpressionFactory.
     */
    public static class MockModifiableViewRelOptTable extends MockTable {

        private final MockModifiableViewTable modifiableViewTable;


        private MockModifiableViewRelOptTable( MockModifiableViewTable modifiableViewTable, MockCatalogReader catalogReader, String catalogName, String schemaName, String name, boolean stream,
                double rowCount, ColumnResolver resolver, InitializerExpressionFactory initializerExpressionFactory ) {
            super( catalogReader, ImmutableList.of( catalogName, schemaName, name ), stream, rowCount, resolver, initializerExpressionFactory );
            this.modifiableViewTable = modifiableViewTable;
        }


        /**
         * Copy constructor.
         */
        private MockModifiableViewRelOptTable( MockModifiableViewTable modifiableViewTable, MockCatalogReader catalogReader, boolean stream, double rowCount, List<Map.Entry<String, RelDataType>> columnList, List<Integer> keyList,
                RelDataType rowType, List<RelCollation> collationList, List<String> names, Set<String> monotonicColumnSet, StructKind kind, ColumnResolver resolver, InitializerExpressionFactory initializerFactory ) {
            super( catalogReader, stream, rowCount, columnList, keyList, rowType, collationList, names, monotonicColumnSet, kind, resolver, initializerFactory );
            this.modifiableViewTable = modifiableViewTable;
        }


        public static MockModifiableViewRelOptTable create( MockModifiableViewTable modifiableViewTable, MockCatalogReader catalogReader, String catalogName, String schemaName, String name, boolean stream, double rowCount, ColumnResolver resolver ) {
            final Table underlying = modifiableViewTable.unwrap( Table.class );
            final InitializerExpressionFactory initializerExpressionFactory =
                    underlying instanceof Wrapper
                            ? ((Wrapper) underlying).unwrap( InitializerExpressionFactory.class )
                            : NullInitializerExpressionFactory.INSTANCE;
            return new MockModifiableViewRelOptTable( modifiableViewTable, catalogReader, catalogName, schemaName, name, stream, rowCount, resolver, Util.first( initializerExpressionFactory, NullInitializerExpressionFactory.INSTANCE ) );
        }


        public static MockViewTableMacro viewMacro( PolyphenyDbSchema schema, String viewSql, List<String> schemaPath, List<String> viewPath, Boolean modifiable ) {
            return new MockViewTableMacro( schema, viewSql, schemaPath, viewPath, modifiable );
        }


        @Override
        public RelDataType getRowType() {
            return modifiableViewTable.getRowType( catalogReader.typeFactory );
        }


        @Override
        protected RelOptTable extend( Table extendedTable ) {
            return new MockModifiableViewRelOptTable( (MockModifiableViewTable) extendedTable, catalogReader, stream, rowCount, columnList, keyList, rowType, collationList, names, monotonicColumnSet, kind, resolver, initializerFactory );
        }


        @Override
        public <T> T unwrap( Class<T> clazz ) {
            if ( clazz.isInstance( modifiableViewTable ) ) {
                return clazz.cast( modifiableViewTable );
            }
            return super.unwrap( clazz );
        }


        /**
         * A TableMacro that creates mock ModifiableViewTable.
         */
        public static class MockViewTableMacro extends ViewTableMacro {

            MockViewTableMacro( PolyphenyDbSchema schema, String viewSql, List<String> schemaPath, List<String> viewPath, Boolean modifiable ) {
                super( schema, viewSql, schemaPath, viewPath, modifiable );
            }


            @Override
            protected ModifiableViewTable modifiableViewTable( AnalyzeViewResult parsed, String viewSql, List<String> schemaPath, List<String> viewPath, PolyphenyDbSchema schema ) {
                final JavaTypeFactory typeFactory = (JavaTypeFactory) parsed.typeFactory;
                final Type elementType = typeFactory.getJavaClass( parsed.rowType );
                return new MockModifiableViewTable( elementType, RelDataTypeImpl.proto( parsed.rowType ), viewSql, schemaPath, viewPath, parsed.table, Schemas.path( schema.root(), parsed.tablePath ), parsed.constraint, parsed.columnMapping );
            }

        }


        /**
         * A mock of ModifiableViewTable that can unwrap a mock RelOptTable.
         */
        public static class MockModifiableViewTable extends ModifiableViewTable {

            private final RexNode constraint;


            MockModifiableViewTable( Type elementType, RelProtoDataType rowType, String viewSql, List<String> schemaPath, List<String> viewPath, Table table, Path tablePath, RexNode constraint, ImmutableIntList columnMapping ) {
                super( elementType, rowType, viewSql, schemaPath, viewPath, table, tablePath, constraint, columnMapping );
                this.constraint = constraint;
            }


            @Override
            public ModifiableViewTable extend( Table extendedTable, RelProtoDataType protoRowType, ImmutableIntList newColumnMapping ) {
                return new MockModifiableViewTable( getElementType(), protoRowType, getViewSql(), getSchemaPath(), getViewPath(), extendedTable, getTablePath(), constraint, newColumnMapping );
            }

        }

    }


    /**
     * Mock implementation of {@link Prepare.PreparingTable} for views.
     */
    public abstract static class MockViewTable extends MockTable {

        private final MockTable fromTable;
        private final Table table;
        private final ImmutableIntList mapping;


        MockViewTable( MockCatalogReader catalogReader, String catalogName, String schemaName, String name, boolean stream, double rowCount, MockTable fromTable, ImmutableIntList mapping, ColumnResolver resolver, InitializerExpressionFactory initializerFactory ) {
            super( catalogReader, catalogName, schemaName, name, stream, rowCount, resolver, initializerFactory );
            this.fromTable = fromTable;
            this.table = fromTable.unwrap( Table.class );
            this.mapping = mapping;
        }


        /**
         * Implementation of AbstractModifiableView.
         */
        private class ModifiableView extends JdbcTest.AbstractModifiableView implements Wrapper {

            @Override
            public Table getTable() {
                return fromTable.unwrap( Table.class );
            }


            @Override
            public Path getTablePath() {
                final ImmutableList.Builder<Pair<String, Schema>> builder = ImmutableList.builder();
                for ( String name : fromTable.names ) {
                    builder.add( Pair.of( name, null ) );
                }
                return Schemas.path( builder.build() );
            }


            @Override
            public ImmutableIntList getColumnMapping() {
                return mapping;
            }


            @Override
            public RexNode getConstraint( RexBuilder rexBuilder, RelDataType tableRowType ) {
                return MockViewTable.this.getConstraint( rexBuilder, tableRowType );
            }


            @Override
            public RelDataType
            getRowType( final RelDataTypeFactory typeFactory ) {
                return typeFactory.createStructType(
                        new AbstractList<Map.Entry<String, RelDataType>>() {
                            @Override
                            public Map.Entry<String, RelDataType>
                            get( int index ) {
                                return table.getRowType( typeFactory ).getFieldList().get( mapping.get( index ) );
                            }


                            @Override
                            public int size() {
                                return mapping.size();
                            }
                        } );
            }


            @Override
            public <C> C unwrap( Class<C> aClass ) {
                if ( table instanceof Wrapper ) {
                    final C c = ((Wrapper) table).unwrap( aClass );
                    if ( c != null ) {
                        return c;
                    }
                }
                return super.unwrap( aClass );
            }

        }


        /**
         * Subclass of ModifiableView that also implements CustomColumnResolvingTable.
         */
        private class ModifiableViewWithCustomColumnResolving extends ModifiableView implements CustomColumnResolvingTable, Wrapper {

            @Override
            public List<Pair<RelDataTypeField, List<String>>> resolveColumn( RelDataType rowType, RelDataTypeFactory typeFactory, List<String> names ) {
                return resolver.resolveColumn( rowType, typeFactory, names );
            }


            @Override
            public <C> C unwrap( Class<C> aClass ) {
                if ( table instanceof Wrapper ) {
                    final C c = ((Wrapper) table).unwrap( aClass );
                    if ( c != null ) {
                        return c;
                    }
                }
                return super.unwrap( aClass );
            }

        }


        protected abstract RexNode getConstraint( RexBuilder rexBuilder, RelDataType tableRowType );


        @Override
        public void onRegister( RelDataTypeFactory typeFactory ) {
            super.onRegister( typeFactory );
            // To simulate getRowType() behavior in ViewTable.
            final RelProtoDataType protoRowType = RelDataTypeImpl.proto( rowType );
            rowType = protoRowType.apply( typeFactory );
        }


        @Override
        public RelNode toRel( ToRelContext context ) {
            RelNode rel = LogicalTableScan.create( context.getCluster(), fromTable );
            final RexBuilder rexBuilder = context.getCluster().getRexBuilder();
            rel = LogicalFilter.create( rel, getConstraint( rexBuilder, rel.getRowType() ) );
            final List<RelDataTypeField> fieldList = rel.getRowType().getFieldList();
            final List<Pair<RexNode, String>> projects =
                    new AbstractList<Pair<RexNode, String>>() {
                        @Override
                        public Pair<RexNode, String> get( int index ) {
                            return RexInputRef.of2( mapping.get( index ), fieldList );
                        }


                        @Override
                        public int size() {
                            return mapping.size();
                        }
                    };
            return LogicalProject.create( rel, Pair.left( projects ), Pair.right( projects ) );
        }


        @Override
        public <T> T unwrap( Class<T> clazz ) {
            if ( clazz.isAssignableFrom( ModifiableView.class ) ) {
                ModifiableView view =
                        resolver == null
                                ? new ModifiableView()
                                : new ModifiableViewWithCustomColumnResolving();
                return clazz.cast( view );
            }
            return super.unwrap( clazz );
        }

    }


    /**
     * Mock implementation of {@link Prepare.PreparingTable} with dynamic record type.
     */
    public static class MockDynamicTable extends MockTable {

        public MockDynamicTable( MockCatalogReader catalogReader, String catalogName, String schemaName, String name, boolean stream, double rowCount ) {
            super( catalogReader, catalogName, schemaName, name, stream, rowCount, null, NullInitializerExpressionFactory.INSTANCE );
        }


        @Override
        public void onRegister( RelDataTypeFactory typeFactory ) {
            rowType = new DynamicRecordTypeImpl( typeFactory );
        }


        /**
         * Recreates an immutable rowType, if the table has Dynamic Record Type, when converts table to Rel.
         */
        @Override
        public RelNode toRel( ToRelContext context ) {
            if ( rowType.isDynamicStruct() ) {
                rowType = new RelRecordType( rowType.getFieldList() );
            }
            return super.toRel( context );
        }

    }


    /**
     * Struct type based on another struct type.
     */
    private static class DelegateStructType implements RelDataType {

        private RelDataType delegate;
        private StructKind structKind;


        DelegateStructType( RelDataType delegate, StructKind structKind ) {
            assert delegate.isStruct();
            this.delegate = delegate;
            this.structKind = structKind;
        }


        @Override
        public boolean isStruct() {
            return delegate.isStruct();
        }


        @Override
        public boolean isDynamicStruct() {
            return delegate.isDynamicStruct();
        }


        @Override
        public List<RelDataTypeField> getFieldList() {
            return delegate.getFieldList();
        }


        @Override
        public List<String> getFieldNames() {
            return delegate.getFieldNames();
        }


        @Override
        public int getFieldCount() {
            return delegate.getFieldCount();
        }


        @Override
        public StructKind getStructKind() {
            return structKind;
        }


        @Override
        public RelDataTypeField getField( String fieldName, boolean caseSensitive, boolean elideRecord ) {
            return delegate.getField( fieldName, caseSensitive, elideRecord );
        }


        @Override
        public boolean isNullable() {
            return delegate.isNullable();
        }


        @Override
        public RelDataType getComponentType() {
            return delegate.getComponentType();
        }


        @Override
        public RelDataType getKeyType() {
            return delegate.getKeyType();
        }


        @Override
        public RelDataType getValueType() {
            return delegate.getValueType();
        }


        @Override
        public Charset getCharset() {
            return delegate.getCharset();
        }


        @Override
        public SqlCollation getCollation() {
            return delegate.getCollation();
        }


        @Override
        public SqlIntervalQualifier getIntervalQualifier() {
            return delegate.getIntervalQualifier();
        }


        @Override
        public int getPrecision() {
            return delegate.getPrecision();
        }


        @Override
        public int getRawPrecision() {
            return delegate.getRawPrecision();
        }


        @Override
        public int getScale() {
            return delegate.getScale();
        }


        @Override
        public PolyType getPolyType() {
            return delegate.getPolyType();
        }


        @Override
        public SqlIdentifier getSqlIdentifier() {
            return delegate.getSqlIdentifier();
        }


        @Override
        public String getFullTypeString() {
            return delegate.getFullTypeString();
        }


        @Override
        public RelDataTypeFamily getFamily() {
            return delegate.getFamily();
        }


        @Override
        public RelDataTypePrecedenceList getPrecedenceList() {
            return delegate.getPrecedenceList();
        }


        @Override
        public RelDataTypeComparability getComparability() {
            return delegate.getComparability();
        }

    }


    /**
     * Wrapper around a {@link MockTable}, giving it a {@link Table} interface. You can get the {@code MockTable} by calling {@link #unwrap(Class)}.
     */
    private static class WrapperTable implements Table, Wrapper {

        private final MockTable table;


        WrapperTable( MockTable table ) {
            this.table = table;
        }


        @Override
        public <C> C unwrap( Class<C> aClass ) {
            return aClass.isInstance( this )
                    ? aClass.cast( this )
                    : aClass.isInstance( table )
                            ? aClass.cast( table )
                            : null;
        }


        @Override
        public RelDataType getRowType( RelDataTypeFactory typeFactory ) {
            return table.getRowType();
        }


        @Override
        public Statistic getStatistic() {
            return new Statistic() {
                @Override
                public Double getRowCount() {
                    return table.rowCount;
                }


                @Override
                public boolean isKey( ImmutableBitSet columns ) {
                    return table.isKey( columns );
                }


                @Override
                public List<RelReferentialConstraint> getReferentialConstraints() {
                    return table.getReferentialConstraints();
                }


                @Override
                public List<RelCollation> getCollations() {
                    return table.collationList;
                }


                @Override
                public RelDistribution getDistribution() {
                    return table.getDistribution();
                }
            };
        }


        @Override
        public boolean isRolledUp( String column ) {
            return table.rolledUpColumns.contains( column );
        }


        @Override
        public boolean rolledUpColumnValidInsideAgg( String column, SqlCall call, SqlNode parent ) {
            // For testing
            return call.getKind() != SqlKind.MAX && (parent.getKind() == SqlKind.SELECT || parent.getKind() == SqlKind.FILTER);
        }


        @Override
        public Schema.TableType getJdbcTableType() {
            return table.stream ? Schema.TableType.STREAM : Schema.TableType.TABLE;
        }

    }


    /**
     * Wrapper around a {@link MockTable}, giving it a {@link StreamableTable} interface.
     */
    private static class StreamableWrapperTable extends WrapperTable implements StreamableTable {

        StreamableWrapperTable( MockTable table ) {
            super( table );
        }


        @Override
        public Table stream() {
            return this;
        }

    }

}
