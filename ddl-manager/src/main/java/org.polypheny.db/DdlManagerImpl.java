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

package org.polypheny.db;

import static org.polypheny.db.util.Static.RESOURCE;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.calcite.linq4j.Ord;
import org.apache.commons.lang3.StringUtils;
import org.polypheny.db.adapter.Adapter;
import org.polypheny.db.adapter.AdapterManager;
import org.polypheny.db.adapter.DataSource;
import org.polypheny.db.adapter.DataSource.ExportedColumn;
import org.polypheny.db.adapter.DataStore;
import org.polypheny.db.adapter.DataStore.AvailableIndexMethod;
import org.polypheny.db.adapter.index.IndexManager;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.Catalog.Collation;
import org.polypheny.db.catalog.Catalog.ForeignKeyOption;
import org.polypheny.db.catalog.Catalog.IndexType;
import org.polypheny.db.catalog.Catalog.PlacementType;
import org.polypheny.db.catalog.Catalog.SchemaType;
import org.polypheny.db.catalog.Catalog.TableType;
import org.polypheny.db.catalog.NameGenerator;
import org.polypheny.db.catalog.entity.CatalogAdapter;
import org.polypheny.db.catalog.entity.CatalogAdapter.AdapterType;
import org.polypheny.db.catalog.entity.CatalogColumn;
import org.polypheny.db.catalog.entity.CatalogColumnPlacement;
import org.polypheny.db.catalog.entity.CatalogConstraint;
import org.polypheny.db.catalog.entity.CatalogForeignKey;
import org.polypheny.db.catalog.entity.CatalogIndex;
import org.polypheny.db.catalog.entity.CatalogKey;
import org.polypheny.db.catalog.entity.CatalogPrimaryKey;
import org.polypheny.db.catalog.entity.CatalogSchema;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.catalog.entity.CatalogUser;
import org.polypheny.db.catalog.exceptions.ColumnAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.GenericCatalogException;
import org.polypheny.db.catalog.exceptions.SchemaAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.TableAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.UnknownAdapterException;
import org.polypheny.db.catalog.exceptions.UnknownCollationException;
import org.polypheny.db.catalog.exceptions.UnknownColumnException;
import org.polypheny.db.catalog.exceptions.UnknownConstraintException;
import org.polypheny.db.catalog.exceptions.UnknownDatabaseException;
import org.polypheny.db.catalog.exceptions.UnknownForeignKeyException;
import org.polypheny.db.catalog.exceptions.UnknownIndexException;
import org.polypheny.db.catalog.exceptions.UnknownKeyException;
import org.polypheny.db.catalog.exceptions.UnknownSchemaException;
import org.polypheny.db.catalog.exceptions.UnknownTableException;
import org.polypheny.db.catalog.exceptions.UnknownUserException;
import org.polypheny.db.config.RuntimeConfig;
import org.polypheny.db.ddl.DdlManager;
import org.polypheny.db.ddl.exception.AlterSourceException;
import org.polypheny.db.ddl.exception.DdlOnSourceException;
import org.polypheny.db.ddl.exception.IndexExistsException;
import org.polypheny.db.ddl.exception.IndexPreventsRemovalException;
import org.polypheny.db.ddl.exception.LastPlacementException;
import org.polypheny.db.ddl.exception.MissingColumnPlacementException;
import org.polypheny.db.ddl.exception.NotNullAndDefaultValueException;
import org.polypheny.db.ddl.exception.PlacementAlreadyExistsException;
import org.polypheny.db.ddl.exception.PlacementIsPrimaryException;
import org.polypheny.db.ddl.exception.PlacementNotExistsException;
import org.polypheny.db.ddl.exception.SchemaNotExistException;
import org.polypheny.db.ddl.exception.UnknownIndexMethodException;
import org.polypheny.db.processing.DataMigrator;
import org.polypheny.db.processing.QueryProcessor;
import org.polypheny.db.routing.Router;
import org.polypheny.db.runtime.PolyphenyDbContextException;
import org.polypheny.db.runtime.PolyphenyDbException;
import org.polypheny.db.sql.SqlDataTypeSpec;
import org.polypheny.db.sql.SqlNode;
import org.polypheny.db.sql.SqlUtil;
import org.polypheny.db.sql.ddl.SqlColumnDeclaration;
import org.polypheny.db.sql.ddl.SqlKeyConstraint;
import org.polypheny.db.sql.parser.SqlParserPos;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.TransactionException;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.PolyTypeFamily;


public class DdlManagerImpl extends DdlManager {

    private final Catalog catalog;


    public DdlManagerImpl( Catalog catalog ) {
        this.catalog = catalog;
    }


    private void checkIfTableType( TableType tableType ) throws DdlOnSourceException {
        if ( tableType != TableType.TABLE ) {
            throw new DdlOnSourceException();
        }
    }


    private CatalogColumn getCatalogColumn( long tableId, String columnName ) throws UnknownColumnException {
        return catalog.getColumn( tableId, columnName );
    }


    private Collation getCollation( SqlColumnDeclaration columnDeclaration, PolyType dataType ) throws UnknownCollationException {
        if ( dataType.getFamily() == PolyTypeFamily.CHARACTER ) {
            if ( columnDeclaration.collation != null ) {
                return Collation.parse( columnDeclaration.collation );
            } else {
                return getDefaultCollation(); // Set default collation
            }
        }
        return null;
    }


    protected DataStore getDataStoreInstance( int storeId ) throws DdlOnSourceException {
        Adapter adapterInstance = AdapterManager.getInstance().getAdapter( storeId );
        if ( adapterInstance == null ) {
            throw new RuntimeException( "Unknown store id: " + storeId );
        }
        // Make sure it is a data store instance
        if ( adapterInstance instanceof DataStore ) {
            return (DataStore) adapterInstance;
        } else if ( adapterInstance instanceof DataSource ) {
            throw new DdlOnSourceException();
        } else {
            throw new RuntimeException( "Unknown kind of adapter: " + adapterInstance.getClass().getName() );
        }
    }


    private Collation getDefaultCollation() {
        return Collation.getById( RuntimeConfig.DEFAULT_COLLATION.getInteger() );
    }


    @Override
    public void createSchema( String name, long databaseId, SchemaType type, int userId, boolean ifNotExists, boolean replace ) throws SchemaAlreadyExistsException {
        // Check if there is already a schema with this name
        if ( catalog.checkIfExistsSchema( databaseId, name ) ) {
            if ( ifNotExists ) {
                // It is ok that there is already a schema with this name because "IF NOT EXISTS" was specified
                return;
            } else if ( replace ) {
                throw new RuntimeException( "Replacing schema is not yet supported." );
            } else {
                throw new SchemaAlreadyExistsException();
            }
        } else {
            long id = catalog.addSchema(
                    name,
                    databaseId,
                    userId,
                    type );
        }
    }


    @Override
    public void addAdapter( String storeName, String adapterName, Map<String, String> config ) {

        Adapter adapter = AdapterManager.getInstance().addAdapter( adapterName, storeName, config );
        if ( adapter instanceof DataSource ) {
            Map<String, List<ExportedColumn>> exportedColumns;
            try {
                exportedColumns = ((DataSource) adapter).getExportedColumns();
            } catch ( Exception e ) {
                AdapterManager.getInstance().removeAdapter( adapter.getAdapterId() );
                throw new RuntimeException( "Could not deploy adapter", e );
            }
            // Create table, columns etc.
            for ( Map.Entry<String, List<ExportedColumn>> entry : exportedColumns.entrySet() ) {
                // Make sure the table name is unique
                String tableName = entry.getKey();
                if ( catalog.checkIfExistsTable( 1, tableName ) ) {
                    int i = 0;
                    while ( catalog.checkIfExistsTable( 1, tableName + i ) ) {
                        i++;
                    }
                    tableName += i;
                }

                long tableId = catalog.addTable( tableName, 1, 1, TableType.SOURCE, !((DataSource) adapter).isDataReadOnly(), null );
                List<Long> primaryKeyColIds = new ArrayList<>();
                int colPos = 1;
                for ( ExportedColumn exportedColumn : entry.getValue() ) {
                    long columnId = catalog.addColumn(
                            exportedColumn.name,
                            tableId,
                            colPos++,
                            exportedColumn.type,
                            exportedColumn.collectionsType,
                            exportedColumn.length,
                            exportedColumn.scale,
                            exportedColumn.dimension,
                            exportedColumn.cardinality,
                            exportedColumn.nullable,
                            Collation.CASE_INSENSITIVE );
                    catalog.addColumnPlacement(
                            adapter.getAdapterId(),
                            columnId,
                            PlacementType.STATIC,
                            exportedColumn.physicalSchemaName,
                            exportedColumn.physicalTableName,
                            exportedColumn.physicalColumnName );
                    catalog.updateColumnPlacementPhysicalPosition( adapter.getAdapterId(), columnId, exportedColumn.physicalPosition );
                    if ( exportedColumn.primary ) {
                        primaryKeyColIds.add( columnId );
                    }
                }
                try {
                    catalog.addPrimaryKey( tableId, primaryKeyColIds );
                } catch ( GenericCatalogException e ) {
                    throw new RuntimeException( "Exception while adding primary key" );
                }
            }
        }
    }


    @Override
    public void dropAdapter( String name, Router router, QueryProcessor processor ) throws UnknownAdapterException {
        if ( name.startsWith( "'" ) ) {
            name = name.substring( 1 );
        }
        if ( name.endsWith( "'" ) ) {
            name = StringUtils.chop( name );
        }

        CatalogAdapter catalogAdapter = catalog.getAdapter( name );
        if ( catalogAdapter.type == AdapterType.SOURCE ) {
            Set<Long> tablesToDrop = new HashSet<>();
            for ( CatalogColumnPlacement ccp : catalog.getColumnPlacementsOnAdapter( catalogAdapter.id ) ) {
                tablesToDrop.add( ccp.tableId );
            }
            for ( Long tableId : tablesToDrop ) {
                CatalogTable table = catalog.getTable( tableId );

                // Make sure that there is only one adapter
                if ( table.placementsByAdapter.keySet().size() != 1 ) {
                    throw new RuntimeException( "The data source contains tables with more than one placement. This should not happen!" );
                }

                // Make sure table is of type source
                if ( table.tableType != TableType.SOURCE ) {
                    throw new RuntimeException( "Trying to drop a table located on a data source which is not of table type SOURCE. This should not happen!" );
                }

                // Inform routing
                router.dropPlacements( catalog.getColumnPlacementsOnAdapter( catalogAdapter.id, table.id ) );
                // Delete column placement in catalog
                for ( Long columnId : table.columnIds ) {
                    if ( catalog.checkIfExistsColumnPlacement( catalogAdapter.id, columnId ) ) {
                        catalog.deleteColumnPlacement( catalogAdapter.id, columnId );
                    }
                }

                // Delete keys and constraints
                try {
                    // Remove primary key
                    catalog.deletePrimaryKey( table.id );
                } catch ( GenericCatalogException e ) {
                    throw new PolyphenyDbContextException( "Exception while dropping primary key.", e );
                }

                // Delete columns
                for ( Long columnId : table.columnIds ) {
                    catalog.deleteColumn( columnId );
                }

                // Delete the table
                catalog.deleteTable( table.id );
            }

            // Rest plan cache and implementation cache
            processor.resetCaches();
        }

        AdapterManager.getInstance().removeAdapter( catalogAdapter.id );

    }


    @Override
    public void alterSchemaOwner( String schemaName, String ownerName, long databaseId ) throws UnknownUserException, UnknownSchemaException {

        CatalogSchema catalogSchema = catalog.getSchema( databaseId, schemaName );
        CatalogUser catalogUser = catalog.getUser( ownerName );
        catalog.setSchemaOwner( catalogSchema.id, catalogUser.id );

    }


    @Override
    public void alterSchemaRename( String newName, String oldName, long databaseId ) throws SchemaAlreadyExistsException, UnknownSchemaException {

        if ( catalog.checkIfExistsSchema( databaseId, newName ) ) {
            throw new SchemaAlreadyExistsException();
        }
        CatalogSchema catalogSchema = catalog.getSchema( databaseId, oldName );
        catalog.renameSchema( catalogSchema.id, newName );

    }


    @Override
    public void alterSourceTableAddColumn( CatalogTable catalogTable, String columnPhysicalName, String columnLogicalName, CatalogColumn beforeColumn, CatalogColumn afterColumn, SqlNode defaultValue, Statement statement ) throws ColumnAlreadyExistsException {

        if ( catalog.checkIfExistsColumn( catalogTable.id, columnLogicalName ) ) {
            throw new ColumnAlreadyExistsException( columnLogicalName, catalogTable.name );
        }

        // Make sure that the table is of table type SOURCE
        if ( catalogTable.tableType != TableType.SOURCE ) {
            throw new RuntimeException( "Table '" + catalogTable.name + "' is not of type SOURCE!" );
        }

        // Make sure there is only one adapter
        if ( catalog.getColumnPlacements( catalogTable.columnIds.get( 0 ) ).size() != 1 ) {
            throw new RuntimeException( "The table has an unexpected number of placements!" );
        }

        int adapterId = catalog.getColumnPlacements( catalogTable.columnIds.get( 0 ) ).get( 0 ).adapterId;
        DataSource dataSource = (DataSource) AdapterManager.getInstance().getAdapter( adapterId );

        String physicalTableName = catalog.getColumnPlacements( catalogTable.columnIds.get( 0 ) ).get( 0 ).physicalTableName;
        List<ExportedColumn> exportedColumns = dataSource.getExportedColumns().get( physicalTableName );

        // Check if physicalColumnName is valid
        ExportedColumn exportedColumn = null;
        for ( ExportedColumn ec : exportedColumns ) {
            if ( ec.physicalColumnName.equalsIgnoreCase( columnPhysicalName ) ) {
                exportedColumn = ec;
            }
        }
        if ( exportedColumn == null ) {
            throw new RuntimeException( "Invalid physical column name '" + columnPhysicalName + "'!" );
        }

        // Make sure this physical column has not already been added to this table
        for ( CatalogColumnPlacement ccp : catalog.getColumnPlacementsOnAdapter( adapterId, catalogTable.id ) ) {
            if ( ccp.physicalColumnName.equalsIgnoreCase( columnPhysicalName ) ) {
                throw new RuntimeException( "The physical column '" + columnPhysicalName + "' has already been added to this table!" );
            }
        }

        List<CatalogColumn> columns = catalog.getColumns( catalogTable.id );
        int position = columns.size() + 1;
        if ( beforeColumn != null || afterColumn != null ) {
            if ( beforeColumn != null ) {
                position = beforeColumn.position;
            } else {
                position = afterColumn.position + 1;
            }
            // Update position of the other columns
            for ( int i = columns.size(); i >= position; i-- ) {
                catalog.setColumnPosition( columns.get( i - 1 ).id, i + 1 );
            }
        }

        long columnId = catalog.addColumn(
                columnLogicalName,
                catalogTable.id,
                position,
                exportedColumn.type,
                exportedColumn.collectionsType,
                exportedColumn.length,
                exportedColumn.scale,
                exportedColumn.dimension,
                exportedColumn.cardinality,
                exportedColumn.nullable,
                Collation.CASE_INSENSITIVE
        );
        CatalogColumn addedColumn = catalog.getColumn( columnId );

        // Add default value
        if ( defaultValue != null ) {
            // TODO: String is only a temporal solution for default values
            String v = defaultValue.toString();
            if ( v.startsWith( "'" ) ) {
                v = v.substring( 1, v.length() - 1 );
            }
            catalog.setDefaultValue( addedColumn.id, PolyType.VARCHAR, v );

            // Update addedColumn variable
            addedColumn = catalog.getColumn( columnId );
        }

        // Add column placement
        catalog.addColumnPlacement(
                adapterId,
                addedColumn.id,
                PlacementType.STATIC,
                exportedColumn.physicalSchemaName,
                exportedColumn.physicalTableName,
                exportedColumn.physicalColumnName );

        // Set column position
        catalog.updateColumnPlacementPhysicalPosition( adapterId, columnId, exportedColumn.physicalPosition );

        // Rest plan cache and implementation cache (not sure if required in this case)
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void alterTableAddColumn( String columnName, CatalogTable catalogTable, CatalogColumn beforeColumn, CatalogColumn afterColumn, SqlDataTypeSpec type, boolean nullable, SqlNode defaultValue, Statement statement ) throws NotNullAndDefaultValueException, ColumnAlreadyExistsException {

        // Check if the column either allows null values or has a default value defined.
        if ( defaultValue == null && !nullable ) {
            throw new NotNullAndDefaultValueException();
        }

        if ( catalog.checkIfExistsColumn( catalogTable.id, columnName ) ) {
            throw new ColumnAlreadyExistsException( columnName, catalogTable.name );
        }

        List<CatalogColumn> columns = catalog.getColumns( catalogTable.id );
        int position = columns.size() + 1;
        if ( beforeColumn != null || afterColumn != null ) {
            if ( beforeColumn != null ) {
                position = beforeColumn.position;
            } else {
                position = afterColumn.position + 1;
            }
            // Update position of the other columns
            for ( int i = columns.size(); i >= position; i-- ) {
                catalog.setColumnPosition( columns.get( i - 1 ).id, i + 1 );
            }
        }
        final PolyType collectionsType = type.getCollectionsTypeName() == null ?
                null : PolyType.get( type.getCollectionsTypeName().getSimple() );
        long columnId = catalog.addColumn(
                columnName,
                catalogTable.id,
                position,
                PolyType.get( type.getTypeName().getSimple() ),
                collectionsType,
                type.getPrecision() == -1 ? null : type.getPrecision(),
                type.getScale() == -1 ? null : type.getScale(),
                type.getDimension() == -1 ? null : type.getDimension(),
                type.getCardinality() == -1 ? null : type.getCardinality(),
                nullable,
                Collation.CASE_INSENSITIVE
        );
        CatalogColumn addedColumn = catalog.getColumn( columnId );

        // Add default value
        if ( defaultValue != null ) {
            // TODO: String is only a temporal solution for default values
            String v = defaultValue.toString();
            if ( v.startsWith( "'" ) ) {
                v = v.substring( 1, v.length() - 1 );
            }
            catalog.setDefaultValue( addedColumn.id, PolyType.VARCHAR, v );

            // Update addedColumn variable
            addedColumn = catalog.getColumn( columnId );
        }

        // Ask router on which stores this column shall be placed
        List<DataStore> stores = statement.getRouter().addColumn( catalogTable, statement );

        // Add column on underlying data stores and insert default value
        for ( DataStore store : stores ) {
            catalog.addColumnPlacement(
                    store.getAdapterId(),
                    addedColumn.id,
                    PlacementType.AUTOMATIC,
                    null, // Will be set later
                    null, // Will be set later
                    null ); // Will be set later
            AdapterManager.getInstance().getStore( store.getAdapterId() ).addColumn( statement.getPrepareContext(), catalogTable, addedColumn );
        }

        // Rest plan cache and implementation cache (not sure if required in this case)
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void alterTableAddForeignKey( CatalogTable catalogTable, CatalogTable refTable, List<String> columnNames, List<String> refColumnNames, SqlParserPos columnListPos, String constraintName, ForeignKeyOption onUpdate, ForeignKeyOption onDelete ) throws UnknownColumnException, GenericCatalogException {

        List<Long> columnIds = new LinkedList<>();
        for ( String columnName : columnNames ) {
            CatalogColumn catalogColumn = catalog.getColumn( catalogTable.id, columnName );
            columnIds.add( catalogColumn.id );
        }
        List<Long> referencesIds = new LinkedList<>();
        for ( String columnName : refColumnNames ) {
            CatalogColumn catalogColumn = catalog.getColumn( refTable.id, columnName );
            referencesIds.add( catalogColumn.id );
        }
        catalog.addForeignKey( catalogTable.id, columnIds, refTable.id, referencesIds, constraintName, onUpdate, onDelete );

    }


    @Override
    public void alterTableAddIndex( CatalogTable catalogTable, String indexMethodName, List<String> columnNames, String indexName, boolean isUnique, DataStore storeInstance, Statement statement ) throws UnknownColumnException, UnknownIndexMethodException, GenericCatalogException, UnknownTableException, UnknownUserException, UnknownSchemaException, UnknownKeyException, UnknownDatabaseException, TransactionException, AlterSourceException, IndexExistsException, MissingColumnPlacementException {

        List<Long> columnIds = new LinkedList<>();
        for ( String columnName : columnNames ) {
            CatalogColumn catalogColumn = catalog.getColumn( catalogTable.id, columnName );
            columnIds.add( catalogColumn.id );
        }

        IndexType type = IndexType.MANUAL;

        // Make sure that this is a table of type TABLE (and not SOURCE)
        if ( catalogTable.tableType != TableType.TABLE ) {
            throw new AlterSourceException();
        }

        // Check if there is already an index with this name for this table
        if ( catalog.checkIfExistsIndex( catalogTable.id, indexName ) ) {
            throw new IndexExistsException();
        }

        if ( storeInstance == null ) { // Polystore Index
            String method;
            String methodDisplayName;
            if ( indexMethodName != null ) {
                AvailableIndexMethod aim = null;
                for ( AvailableIndexMethod availableIndexMethod : IndexManager.getAvailableIndexMethods() ) {
                    if ( availableIndexMethod.name.equals( indexMethodName ) ) {
                        aim = availableIndexMethod;
                    }
                }
                if ( aim == null ) {
                    throw new UnknownIndexMethodException();
                }
                method = aim.name;
                methodDisplayName = aim.displayName;
            } else {
                method = IndexManager.getDefaultIndexMethod().name;
                methodDisplayName = IndexManager.getDefaultIndexMethod().displayName;
            }

            long indexId = catalog.addIndex(
                    catalogTable.id,
                    columnIds,
                    isUnique,
                    method,
                    methodDisplayName,
                    0,
                    type,
                    indexName );

            IndexManager.getInstance().addIndex( catalog.getIndex( indexId ), statement );
        } else { // Store Index

            // Check if there if all required columns are present on this store
            for ( long columnId : columnIds ) {
                if ( !catalog.checkIfExistsColumnPlacement( storeInstance.getAdapterId(), columnId ) ) {
                    throw new MissingColumnPlacementException( catalog.getColumn( columnId ).name );
                }
            }

            String method;
            String methodDisplayName;
            if ( indexMethodName != null ) {
                AvailableIndexMethod aim = null;
                for ( AvailableIndexMethod availableIndexMethod : storeInstance.getAvailableIndexMethods() ) {
                    if ( availableIndexMethod.name.equals( indexMethodName ) ) {
                        aim = availableIndexMethod;
                    }
                }
                if ( aim == null ) {
                    throw new UnknownIndexMethodException();
                }
                method = aim.name;
                methodDisplayName = aim.displayName;
            } else {
                method = storeInstance.getDefaultIndexMethod().name;
                methodDisplayName = storeInstance.getDefaultIndexMethod().displayName;
            }

            long indexId = catalog.addIndex(
                    catalogTable.id,
                    columnIds,
                    isUnique,
                    method,
                    methodDisplayName,
                    storeInstance.getAdapterId(),
                    type,
                    indexName );

            storeInstance.addIndex( statement.getPrepareContext(), catalog.getIndex( indexId ) );
        }

    }


    @Override
    public void alterTableAddPlacement( CatalogTable catalogTable, List<Long> columnIds, DataStore dataStore, Statement statement ) throws PlacementAlreadyExistsException {

        List<CatalogColumn> addedColumns = new LinkedList<>();

        // Check whether this placement already exists
        for ( int storeId : catalogTable.placementsByAdapter.keySet() ) {
            if ( storeId == dataStore.getAdapterId() ) {
                throw new PlacementAlreadyExistsException();
            }
        }
        // Check whether the list is empty (this is a short hand for a full placement)
        if ( columnIds.size() == 0 ) {
            columnIds = ImmutableList.copyOf( catalogTable.columnIds );
        }
        // Create column placements
        for ( long cid : columnIds ) {
            catalog.addColumnPlacement(
                    dataStore.getAdapterId(),
                    cid,
                    PlacementType.MANUAL,
                    null,
                    null,
                    null );
            addedColumns.add( catalog.getColumn( cid ) );
        }
        //Check if placement includes primary key columns
        CatalogPrimaryKey primaryKey = catalog.getPrimaryKey( catalogTable.primaryKey );
        for ( long cid : primaryKey.columnIds ) {
            if ( !columnIds.contains( cid ) ) {
                catalog.addColumnPlacement(
                        dataStore.getAdapterId(),
                        cid,
                        PlacementType.AUTOMATIC,
                        null,
                        null,
                        null );
                addedColumns.add( catalog.getColumn( cid ) );
            }
        }
        // Create table on store
        dataStore.createTable( statement.getPrepareContext(), catalogTable );
        // Copy data to the newly added placements
        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
        dataMigrator.copyData( statement.getTransaction(), catalog.getAdapter( dataStore.getAdapterId() ), addedColumns );
    }


    @Override
    public void alterTableAddPrimaryKey( CatalogTable catalogTable, List<String> columnNames, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfTableType( catalogTable.tableType );

        try {
            CatalogPrimaryKey oldPk = catalog.getPrimaryKey( catalogTable.primaryKey );

            List<Long> columnIds = new LinkedList<>();
            for ( String columnName : columnNames ) {
                CatalogColumn catalogColumn = catalog.getColumn( catalogTable.id, columnName );
                columnIds.add( catalogColumn.id );
            }
            catalog.addPrimaryKey( catalogTable.id, columnIds );

            // Add new column placements
            long pkColumnId = oldPk.columnIds.get( 0 ); // It is sufficient to check for one because all get replicated on all stores
            List<CatalogColumnPlacement> oldPkPlacements = catalog.getColumnPlacements( pkColumnId );
            for ( CatalogColumnPlacement ccp : oldPkPlacements ) {
                for ( long columnId : columnIds ) {
                    if ( !catalog.checkIfExistsColumnPlacement( ccp.adapterId, columnId ) ) {
                        catalog.addColumnPlacement(
                                ccp.adapterId,
                                columnId,
                                PlacementType.AUTOMATIC,
                                null, // Will be set later
                                null, // Will be set later
                                null ); // Will be set later
                        AdapterManager.getInstance().getStore( ccp.adapterId ).addColumn(
                                statement.getPrepareContext(),
                                catalog.getTable( ccp.tableId ),
                                catalog.getColumn( columnId ) );
                    }
                }
            }
        } catch ( GenericCatalogException | UnknownColumnException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void alterTableAddUniqueConstraint( CatalogTable catalogTable, List<String> columnNames, String constraintName ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfTableType( catalogTable.tableType );

        try {
            List<Long> columnIds = new LinkedList<>();
            for ( String columnName : columnNames ) {
                CatalogColumn catalogColumn = catalog.getColumn( catalogTable.id, columnName );
                columnIds.add( catalogColumn.id );
            }
            catalog.addUniqueConstraint( catalogTable.id, constraintName, columnIds );
        } catch ( GenericCatalogException | UnknownColumnException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void alterTableDropColumn( CatalogTable catalogTable, CatalogColumn catalogColumn, Statement statement ) {

        if ( catalogTable.columnIds.size() < 2 ) {
            throw new RuntimeException( "Cannot drop sole column of table " + catalogTable.name );
        }

        // Check if column is part of an key
        for ( CatalogKey key : catalog.getTableKeys( catalogTable.id ) ) {
            if ( key.columnIds.contains( catalogColumn.id ) ) {
                if ( catalog.isPrimaryKey( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + catalogColumn.name + "' because it is part of the primary key." );
                } else if ( catalog.isIndex( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + catalogColumn.name + "' because it is part of the index with the name: '" + catalog.getIndexes( key ).get( 0 ).name + "'." );
                } else if ( catalog.isForeignKey( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + catalogColumn.name + "' because it is part of the foreign key with the name: '" + catalog.getForeignKeys( key ).get( 0 ).name + "'." );
                } else if ( catalog.isConstraint( key.id ) ) {
                    throw new PolyphenyDbException( "Cannot drop column '" + catalogColumn.name + "' because it is part of the constraint with the name: '" + catalog.getConstraints( key ).get( 0 ).name + "'." );
                }
                throw new PolyphenyDbException( "Ok, strange... Something is going wrong here!" );
            }
        }

        // Delete column from underlying data stores
        for ( CatalogColumnPlacement dp : catalog.getColumnPlacementsByColumn( catalogColumn.id ) ) {
            if ( catalogTable.tableType == TableType.TABLE ) {
                AdapterManager.getInstance().getStore( dp.adapterId ).dropColumn( statement.getPrepareContext(), dp );
            }
            catalog.deleteColumnPlacement( dp.adapterId, dp.columnId );
        }

        // Delete from catalog
        List<CatalogColumn> columns = catalog.getColumns( catalogTable.id );
        catalog.deleteColumn( catalogColumn.id );
        if ( catalogColumn.position != columns.size() ) {
            // Update position of the other columns
            for ( int i = catalogColumn.position; i < columns.size(); i++ ) {
                catalog.setColumnPosition( columns.get( i ).id, i );
            }
        }

        // Rest plan cache and implementation cache (not sure if required in this case)
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void alterTableDropConstraint( CatalogTable catalogTable, String constraintName ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfTableType( catalogTable.tableType );

        try {
            CatalogConstraint constraint = catalog.getConstraint( catalogTable.id, constraintName );
            catalog.deleteConstraint( constraint.id );
        } catch ( GenericCatalogException | UnknownConstraintException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void alterTableDropForeignKey( CatalogTable catalogTable, String foreignKeyName ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfTableType( catalogTable.tableType );

        try {
            CatalogForeignKey foreignKey = catalog.getForeignKey( catalogTable.id, foreignKeyName );
            catalog.deleteForeignKey( foreignKey.id );
        } catch ( GenericCatalogException | UnknownForeignKeyException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void alterTableDropIndex( CatalogTable catalogTable, String indexName, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfTableType( catalogTable.tableType );

        try {
            CatalogIndex index = catalog.getIndex( catalogTable.id, indexName );

            if ( index.location == 0 ) {
                IndexManager.getInstance().deleteIndex( index );
            } else {
                DataStore storeInstance = AdapterManager.getInstance().getStore( index.location );
                storeInstance.dropIndex( statement.getPrepareContext(), index );
            }

            catalog.deleteIndex( index.id );
        } catch ( UnknownIndexException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void alterTableDropPlacement( CatalogTable catalogTable, DataStore storeInstance, Statement statement ) throws PlacementNotExistsException, LastPlacementException {
        // Check whether this placement exists
        if ( !catalogTable.placementsByAdapter.containsKey( storeInstance.getAdapterId() ) ) {
            throw new PlacementNotExistsException();
        }

        // Check if there are is another placement for every column on this store
        for ( CatalogColumnPlacement placement : catalog.getColumnPlacementsOnAdapter( storeInstance.getAdapterId(), catalogTable.id ) ) {
            List<CatalogColumnPlacement> existingPlacements = catalog.getColumnPlacements( placement.columnId );
            if ( existingPlacements.size() < 2 ) {
                throw new LastPlacementException();
            }
        }
        // Drop all indexes on this store
        for ( CatalogIndex index : catalog.getIndexes( catalogTable.id, false ) ) {
            if ( index.location == storeInstance.getAdapterId() ) {
                if ( index.location == 0 ) {
                    // Delete polystore index
                    IndexManager.getInstance().deleteIndex( index );
                } else {
                    // Delete index on store
                    AdapterManager.getInstance().getStore( index.location ).dropIndex( statement.getPrepareContext(), index );
                }
                // Delete index in catalog
                catalog.deleteIndex( index.id );
            }
        }
        // Physically delete the data from the store
        storeInstance.dropTable( statement.getPrepareContext(), catalogTable );
        // Inform routing
        statement.getRouter().dropPlacements( catalog.getColumnPlacementsOnAdapter( storeInstance.getAdapterId(), catalogTable.id ) );
        // Delete placement in the catalog
        List<CatalogColumnPlacement> placements = catalog.getColumnPlacementsOnAdapter( storeInstance.getAdapterId(), catalogTable.id );
        for ( CatalogColumnPlacement placement : placements ) {
            catalog.deleteColumnPlacement( storeInstance.getAdapterId(), placement.columnId );
        }
    }


    @Override
    public void alterTableDropPrimaryKey( CatalogTable catalogTable ) throws DdlOnSourceException {
        try {
            // Make sure that this is a table of type TABLE (and not SOURCE)
            checkIfTableType( catalogTable.tableType );

            catalog.deletePrimaryKey( catalogTable.id );
        } catch ( GenericCatalogException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void alterTableModifyColumn( CatalogTable catalogTable, CatalogColumn catalogColumn, SqlDataTypeSpec type, String collation, SqlNode defaultValue, Boolean nullable, Boolean dropDefault, CatalogColumn beforeColumn, CatalogColumn afterColumn, Statement statement ) throws DdlOnSourceException {
        try {
            if ( type != null ) {
                // Make sure that this is a table of type TABLE (and not SOURCE)
                checkIfTableType( catalogTable.tableType );

                PolyType dataType = PolyType.get( type.getTypeName().getSimple() );
                final PolyType collectionsType = type.getCollectionsTypeName() == null ?
                        null : PolyType.get( type.getCollectionsTypeName().getSimple() );
                catalog.setColumnType(
                        catalogColumn.id,
                        dataType,
                        collectionsType,
                        type.getPrecision() == -1 ? null : type.getPrecision(),
                        type.getScale() == -1 ? null : type.getScale(),
                        type.getDimension() == -1 ? null : type.getDimension(),
                        type.getCardinality() == -1 ? null : type.getCardinality() );
                for ( CatalogColumnPlacement placement : catalog.getColumnPlacements( catalogColumn.id ) ) {
                    AdapterManager.getInstance().getStore( placement.adapterId ).updateColumnType(
                            statement.getPrepareContext(),
                            placement,
                            catalogColumn );
                }
            } else if ( nullable != null ) {
                // Make sure that this is a table of type TABLE (and not SOURCE)
                checkIfTableType( catalogTable.tableType );

                catalog.setNullable( catalogColumn.id, nullable );
            } else if ( beforeColumn != null || afterColumn != null ) {
                int targetPosition;
                CatalogColumn refColumn;
                if ( beforeColumn != null ) {
                    refColumn = beforeColumn;
                    targetPosition = refColumn.position;
                } else {
                    refColumn = afterColumn;
                    targetPosition = refColumn.position + 1;
                }
                if ( catalogColumn.id == refColumn.id ) {
                    throw new RuntimeException( "Same column!" );
                }
                List<CatalogColumn> columns = catalog.getColumns( catalogTable.id );
                if ( targetPosition < catalogColumn.position ) {  // Walk from last column to first column
                    for ( int i = columns.size(); i >= 1; i-- ) {
                        if ( i < catalogColumn.position && i >= targetPosition ) {
                            catalog.setColumnPosition( columns.get( i - 1 ).id, i + 1 );
                        } else if ( i == catalogColumn.position ) {
                            catalog.setColumnPosition( catalogColumn.id, columns.size() + 1 );
                        }
                        if ( i == targetPosition ) {
                            catalog.setColumnPosition( catalogColumn.id, targetPosition );
                        }
                    }
                } else if ( targetPosition > catalogColumn.position ) { // Walk from first column to last column
                    targetPosition--;
                    for ( int i = 1; i <= columns.size(); i++ ) {
                        if ( i > catalogColumn.position && i <= targetPosition ) {
                            catalog.setColumnPosition( columns.get( i - 1 ).id, i - 1 );
                        } else if ( i == catalogColumn.position ) {
                            catalog.setColumnPosition( catalogColumn.id, columns.size() + 1 );
                        }
                        if ( i == targetPosition ) {
                            catalog.setColumnPosition( catalogColumn.id, targetPosition );
                        }
                    }
                } else {
                    // Do nothing
                }
            } else if ( collation != null ) {
                // Make sure that this is a table of type TABLE (and not SOURCE)
                checkIfTableType( catalogTable.tableType );

                Collation col = Collation.parse( collation );
                catalog.setCollation( catalogColumn.id, col );
            } else if ( defaultValue != null ) {
                // TODO: String is only a temporal solution for default values
                String v = defaultValue.toString();
                if ( v.startsWith( "'" ) ) {
                    v = v.substring( 1, v.length() - 1 );
                }
                catalog.setDefaultValue( catalogColumn.id, PolyType.VARCHAR, v );
            } else if ( dropDefault != null && dropDefault ) {
                catalog.deleteDefaultValue( catalogColumn.id );
            } else {
                throw new RuntimeException( "Unknown option" );
            }

            // Rest plan cache and implementation cache (not sure if required in this case)
            statement.getQueryProcessor().resetCaches();
        } catch ( GenericCatalogException | UnknownCollationException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void alterTableModifyPlacement( CatalogTable catalogTable, List<Long> columnIds, DataStore storeInstance, Statement statement ) throws PlacementNotExistsException, IndexPreventsRemovalException, LastPlacementException {

        // Check whether this placement already exists
        if ( !catalogTable.placementsByAdapter.containsKey( storeInstance.getAdapterId() ) ) {

            throw new PlacementNotExistsException();
        }

        // Which columns to remove
        for ( CatalogColumnPlacement placement : catalog.getColumnPlacementsOnAdapter( storeInstance.getAdapterId(), catalogTable.id ) ) {
            if ( !columnIds.contains( placement.columnId ) ) {
                // Check whether there are any indexes located on the store requiring this column
                for ( CatalogIndex index : catalog.getIndexes( catalogTable.id, false ) ) {
                    if ( index.location == storeInstance.getAdapterId() && index.key.columnIds.contains( placement.columnId ) ) {
                        throw new IndexPreventsRemovalException( index.name, catalog.getColumn( placement.columnId ).name );

                    }
                }
                // Check whether the column is a primary key column
                CatalogPrimaryKey primaryKey = catalog.getPrimaryKey( catalogTable.primaryKey );
                if ( primaryKey.columnIds.contains( placement.columnId ) ) {
                    // Check if the placement type is manual. If so, change to automatic
                    if ( placement.placementType == PlacementType.MANUAL ) {
                        // Make placement manual
                        catalog.updateColumnPlacementType(
                                storeInstance.getAdapterId(),
                                placement.columnId,
                                PlacementType.AUTOMATIC );
                    }
                } else {
                    // It is not a primary key. Remove the column
                    // Check if there are is another placement for this column
                    List<CatalogColumnPlacement> existingPlacements = catalog.getColumnPlacements( placement.columnId );
                    if ( existingPlacements.size() < 2 ) {
                        throw new LastPlacementException();
                    }
                    // Drop Column on store
                    storeInstance.dropColumn( statement.getPrepareContext(), catalog.getColumnPlacement( storeInstance.getAdapterId(), placement.columnId ) );
                    // Drop column placement
                    catalog.deleteColumnPlacement( storeInstance.getAdapterId(), placement.columnId );
                }
            }
        }
        // Which columns to add
        List<CatalogColumn> addedColumns = new LinkedList<>();
        for ( long cid : columnIds ) {
            if ( catalog.checkIfExistsColumnPlacement( storeInstance.getAdapterId(), cid ) ) {
                CatalogColumnPlacement placement = catalog.getColumnPlacement( storeInstance.getAdapterId(), cid );
                if ( placement.placementType == PlacementType.AUTOMATIC ) {
                    // Make placement manual
                    catalog.updateColumnPlacementType( storeInstance.getAdapterId(), cid, PlacementType.MANUAL );
                }
            } else {
                // Create column placement
                catalog.addColumnPlacement(
                        storeInstance.getAdapterId(),
                        cid,
                        PlacementType.MANUAL,
                        null,
                        null,
                        null );
                // Add column on store
                storeInstance.addColumn( statement.getPrepareContext(), catalogTable, catalog.getColumn( cid ) );
                // Add to list of columns for which we need to copy data
                addedColumns.add( catalog.getColumn( cid ) );
            }
        }
        // Copy the data to the newly added column placements
        DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
        if ( addedColumns.size() > 0 ) {
            dataMigrator.copyData( statement.getTransaction(), catalog.getAdapter( storeInstance.getAdapterId() ), addedColumns );
        }
    }


    @Override
    public void alterTableModifyPlacementAndColumn( CatalogTable catalogTable, CatalogColumn catalogColumn, DataStore storeInstance, Statement statement ) throws UnknownAdapterException, PlacementNotExistsException, PlacementAlreadyExistsException {
        if ( storeInstance == null ) {
            throw new UnknownAdapterException( "" );
        }
        // Check whether this placement already exists
        if ( !catalogTable.placementsByAdapter.containsKey( storeInstance.getAdapterId() ) ) {
            throw new PlacementNotExistsException();
        }
        // Make sure that this store does not contain a placement of this column
        if ( catalog.checkIfExistsColumnPlacement( storeInstance.getAdapterId(), catalogColumn.id ) ) {
            CatalogColumnPlacement placement = catalog.getColumnPlacement( storeInstance.getAdapterId(), catalogColumn.id );
            if ( placement.placementType == PlacementType.AUTOMATIC ) {
                // Make placement manual
                catalog.updateColumnPlacementType(
                        storeInstance.getAdapterId(),
                        catalogColumn.id,
                        PlacementType.MANUAL );
            } else {
                throw new PlacementAlreadyExistsException();
            }
        } else {
            // Create column placement
            catalog.addColumnPlacement(
                    storeInstance.getAdapterId(),
                    catalogColumn.id,
                    PlacementType.MANUAL,
                    null,
                    null,
                    null );
            // Add column on store
            storeInstance.addColumn( statement.getPrepareContext(), catalogTable, catalogColumn );
            // Copy the data to the newly added column placements
            DataMigrator dataMigrator = statement.getTransaction().getDataMigrator();
            dataMigrator.copyData( statement.getTransaction(), catalog.getAdapter( storeInstance.getAdapterId() ), ImmutableList.of( catalogColumn ) );
        }
    }


    @Override
    public void alterTableModifyPlacementDropColumn( CatalogTable catalogTable, CatalogColumn catalogColumn, DataStore storeInstance, Statement statement ) throws UnknownAdapterException, PlacementNotExistsException, IndexPreventsRemovalException, LastPlacementException, PlacementIsPrimaryException {
        if ( storeInstance == null ) {
            throw new UnknownAdapterException( "" );

        }
        // Check whether this placement already exists
        if ( !catalogTable.placementsByAdapter.containsKey( storeInstance.getAdapterId() ) ) {
            throw new PlacementNotExistsException();

        }
        // Check whether this store actually contains a placement of this column
        if ( !catalog.checkIfExistsColumnPlacement( storeInstance.getAdapterId(), catalogColumn.id ) ) {
            throw new PlacementNotExistsException();
        }
        // Check whether there are any indexes located on the store requiring this column
        for ( CatalogIndex index : catalog.getIndexes( catalogTable.id, false ) ) {
            if ( index.location == storeInstance.getAdapterId() && index.key.columnIds.contains( catalogColumn.id ) ) {
                throw new IndexPreventsRemovalException( index.name, catalogColumn.name );
            }
        }
        // Check if there are is another placement for this column
        List<CatalogColumnPlacement> existingPlacements = catalog.getColumnPlacements( catalogColumn.id );
        if ( existingPlacements.size() < 2 ) {
            throw new LastPlacementException();
        }
        // Check whether the column to drop is a primary key
        CatalogPrimaryKey primaryKey = catalog.getPrimaryKey( catalogTable.primaryKey );
        if ( primaryKey.columnIds.contains( catalogColumn.id ) ) {
            throw new PlacementIsPrimaryException();
        }
        // Drop Column on store
        storeInstance.dropColumn( statement.getPrepareContext(), catalog.getColumnPlacement( storeInstance.getAdapterId(), catalogColumn.id ) );
        // Drop column placement
        catalog.deleteColumnPlacement( storeInstance.getAdapterId(), catalogColumn.id );
    }


    @Override
    public void alterTableOwner( CatalogTable catalogTable, String newOwnerName ) throws UnknownUserException {
        CatalogUser catalogUser = catalog.getUser( newOwnerName );
        catalog.setTableOwner( catalogTable.id, catalogUser.id );
    }


    @Override
    public void alterTableRename( CatalogTable catalogTable, String newTableName, Statement statement ) throws TableAlreadyExistsException {
        if ( catalog.checkIfExistsTable( catalogTable.schemaId, newTableName ) ) {
            throw new TableAlreadyExistsException();
        }
        catalog.renameTable( catalogTable.id, newTableName );

        // Rest plan cache and implementation cache (not sure if required in this case)
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void renameColumn( CatalogColumn catalogColumn, String newColumnName, Statement statement ) {
        catalog.renameColumn( catalogColumn.id, newColumnName );

        // Rest plan cache and implementation cache (not sure if required in this case)
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void createTable( long schemaId, String tableName, List<SqlNode> columnList, boolean ifNotExists, List<DataStore> stores, PlacementType placementType, Statement statement ) throws TableAlreadyExistsException {
        try {
            // Check if there is already a table with this name
            if ( catalog.checkIfExistsTable( schemaId, tableName ) ) {
                if ( ifNotExists ) {
                    // It is ok that there is already a table with this name because "IF NOT EXISTS" was specified
                    return;
                } else {
                    throw new TableAlreadyExistsException();
                }
            }

            if ( columnList == null ) {
                // "CREATE TABLE t" is invalid; because there is no "AS query" we need a list of column names and types, "CREATE TABLE t (INT c)".
                throw SqlUtil.newContextException( SqlParserPos.ZERO, RESOURCE.createTableRequiresColumnList() );
            }

            if ( stores == null ) {
                // Ask router on which store(s) the table should be placed
                stores = statement.getRouter().createTable( schemaId, statement );
            }

            long tableId = catalog.addTable(
                    tableName,
                    schemaId,
                    statement.getPrepareContext().getCurrentUserId(),
                    TableType.TABLE,
                    true,
                    null );

            int position = 1;
            for ( Ord<SqlNode> c : Ord.zip( columnList ) ) {
                addColumn( c, tableId, position, stores, placementType );
                position++;
            }

            CatalogTable catalogTable = catalog.getTable( tableId );
            for ( DataStore store : stores ) {
                store.createTable( statement.getPrepareContext(), catalogTable );
            }
        } catch ( GenericCatalogException | UnknownColumnException | UnknownCollationException e ) {
            throw new RuntimeException( e );
        }
    }


    // TODO DL: refactor remove SqlNode
    @Override
    public void addColumn( Ord<SqlNode> c, long tableId, int position, List<DataStore> stores, PlacementType placementType ) throws GenericCatalogException, UnknownCollationException, UnknownColumnException {
        if ( c.e instanceof SqlColumnDeclaration ) {
            final SqlColumnDeclaration columnDeclaration = (SqlColumnDeclaration) c.e;
            final PolyType dataType = PolyType.get( columnDeclaration.getDataType().getTypeName().getSimple() );
            final PolyType collectionsType = columnDeclaration.getDataType().getCollectionsTypeName() == null ?
                    null : PolyType.get( columnDeclaration.getDataType().getCollectionsTypeName().getSimple() );
            Collation collation = getCollation( columnDeclaration, dataType );
            long addedColumnId = catalog.addColumn(
                    columnDeclaration.getName().getSimple(),
                    tableId,
                    position,
                    dataType,
                    collectionsType,
                    columnDeclaration.getDataType().getPrecision() == -1 ? null : columnDeclaration.getDataType().getPrecision(),
                    columnDeclaration.getDataType().getScale() == -1 ? null : columnDeclaration.getDataType().getScale(),
                    columnDeclaration.getDataType().getDimension() == -1 ? null : columnDeclaration.getDataType().getDimension(),
                    columnDeclaration.getDataType().getCardinality() == -1 ? null : columnDeclaration.getDataType().getCardinality(),
                    columnDeclaration.getDataType().getNullable(),
                    collation
            );

            for ( DataStore s : stores ) {
                catalog.addColumnPlacement(
                        s.getAdapterId(),
                        addedColumnId,
                        placementType,
                        null,
                        null,
                        null );
            }

            // Add default value
            if ( ((SqlColumnDeclaration) c.e).getExpression() != null ) {
                // TODO: String is only a temporal solution for default values
                String v = ((SqlColumnDeclaration) c.e).getExpression().toString();
                if ( v.startsWith( "'" ) ) {
                    v = v.substring( 1, v.length() - 1 );
                }
                catalog.setDefaultValue( addedColumnId, PolyType.VARCHAR, v );
            }
        } else if ( c.e instanceof SqlKeyConstraint ) {
            SqlKeyConstraint constraint = (SqlKeyConstraint) c.e;
            List<Long> columnIds = new LinkedList<>();
            for ( SqlNode node : constraint.getColumnList().getList() ) {
                String columnName = node.toString();
                CatalogColumn catalogColumn = catalog.getColumn( tableId, columnName );
                columnIds.add( catalogColumn.id );
            }
            if ( constraint.getOperator() == SqlKeyConstraint.PRIMARY ) {
                catalog.addPrimaryKey( tableId, columnIds );
            } else if ( constraint.getOperator() == SqlKeyConstraint.UNIQUE ) {
                String constraintName;
                if ( constraint.getName() == null ) {
                    constraintName = NameGenerator.generateConstraintName();
                } else {
                    constraintName = constraint.getName().getSimple();
                }
                catalog.addUniqueConstraint( tableId, constraintName, columnIds );
            }
        } else {
            throw new AssertionError( c.e.getClass() );
        }
    }


    @Override
    public void dropSchema( long databaseId, String schemaName, boolean ifExists, Statement statement ) throws SchemaNotExistException, DdlOnSourceException {
        try {
            // Check if there is a schema with this name
            if ( catalog.checkIfExistsSchema( databaseId, schemaName ) ) {
                CatalogSchema catalogSchema = catalog.getSchema( databaseId, schemaName );

                // Drop all tables in this schema
                List<CatalogTable> catalogTables = catalog.getTables( catalogSchema.id, null );
                for ( CatalogTable catalogTable : catalogTables ) {
                    dropTable( catalogTable, statement );
                }

                // Drop schema
                catalog.deleteSchema( catalogSchema.id );
            } else {
                if ( ifExists ) {
                    // This is ok because "IF EXISTS" was specified
                    return;
                } else {
                    throw new SchemaNotExistException();
                }
            }
        } catch ( UnknownSchemaException e ) {
            throw new RuntimeException( e );
        }
    }


    @Override
    public void dropTable( CatalogTable catalogTable, Statement statement ) throws DdlOnSourceException {
        // Make sure that this is a table of type TABLE (and not SOURCE)
        checkIfTableType( catalogTable.tableType );

        // Check if there are foreign keys referencing this table
        List<CatalogForeignKey> selfRefsToDelete = new LinkedList<>();
        List<CatalogForeignKey> exportedKeys = catalog.getExportedKeys( catalogTable.id );
        if ( exportedKeys.size() > 0 ) {
            for ( CatalogForeignKey foreignKey : exportedKeys ) {
                if ( foreignKey.tableId == catalogTable.id ) {
                    // If this is a self-reference, drop it later.
                    selfRefsToDelete.add( foreignKey );
                } else {
                    throw new PolyphenyDbException( "Cannot drop table '" + catalogTable.getSchemaName() + "." + catalogTable.name + "' because it is being referenced by '" + exportedKeys.get( 0 ).getSchemaName() + "." + exportedKeys.get( 0 ).getTableName() + "'." );
                }
            }

        }

        // Make sure that all adapters are of type store (and not source)
        for ( int storeId : catalogTable.placementsByAdapter.keySet() ) {
            getDataStoreInstance( storeId );
        }

        // Delete all indexes
        for ( CatalogIndex index : catalog.getIndexes( catalogTable.id, false ) ) {
            if ( index.location == 0 ) {
                // Delete polystore index
                IndexManager.getInstance().deleteIndex( index );
            } else {
                // Delete index on store
                AdapterManager.getInstance().getStore( index.location ).dropIndex( statement.getPrepareContext(), index );
            }
            // Delete index in catalog
            catalog.deleteIndex( index.id );
        }

        // Delete data from the stores and remove the column placement
        for ( int storeId : catalogTable.placementsByAdapter.keySet() ) {
            // Delete table on store
            AdapterManager.getInstance().getStore( storeId ).dropTable( statement.getPrepareContext(), catalogTable );
            // Inform routing
            statement.getRouter().dropPlacements( catalog.getColumnPlacementsOnAdapter( storeId, catalogTable.id ) );
            // Delete column placement in catalog
            for ( Long columnId : catalogTable.columnIds ) {
                if ( catalog.checkIfExistsColumnPlacement( storeId, columnId ) ) {
                    catalog.deleteColumnPlacement( storeId, columnId );
                }
            }
        }

        // Delete the self-referencing foreign keys
        try {
            for ( CatalogForeignKey foreignKey : selfRefsToDelete ) {
                catalog.deleteForeignKey( foreignKey.id );
            }
        } catch ( GenericCatalogException e ) {
            throw new PolyphenyDbContextException( "Exception while deleting self-referencing foreign key constraints.", e );
        }

        // Delete indexes of this table
        List<CatalogIndex> indexes = catalog.getIndexes( catalogTable.id, false );
        for ( CatalogIndex index : indexes ) {
            catalog.deleteIndex( index.id );
            IndexManager.getInstance().deleteIndex( index );
        }

        // Delete keys and constraints
        try {
            // Remove primary key
            catalog.deletePrimaryKey( catalogTable.id );
            // Delete all foreign keys of the table
            List<CatalogForeignKey> foreignKeys = catalog.getForeignKeys( catalogTable.id );
            for ( CatalogForeignKey foreignKey : foreignKeys ) {
                catalog.deleteForeignKey( foreignKey.id );
            }
            // Delete all constraints of the table
            for ( CatalogConstraint constraint : catalog.getConstraints( catalogTable.id ) ) {
                catalog.deleteConstraint( constraint.id );
            }
        } catch ( GenericCatalogException e ) {
            throw new PolyphenyDbContextException( "Exception while dropping keys.", e );
        }

        // Delete columns
        for ( Long columnId : catalogTable.columnIds ) {
            catalog.deleteColumn( columnId );
        }

        // Delete the table
        catalog.deleteTable( catalogTable.id );

        // Rest plan cache and implementation cache
        statement.getQueryProcessor().resetCaches();
    }


    @Override
    public void truncate( CatalogTable catalogTable, Statement statement ) {
        // Make sure that the table can be modified
        if ( !catalogTable.modifiable ) {
            throw new RuntimeException( "Unable to modify a read-only table!" );
        }

        //  Execute truncate on all placements
        catalogTable.placementsByAdapter.forEach( ( adapterId, placements ) -> {
            AdapterManager.getInstance().getAdapter( adapterId ).truncate( statement.getPrepareContext(), catalogTable );
        } );
    }


}