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

package org.polypheny.db.sql.ddl.altertable;


import static org.polypheny.db.util.Static.RESOURCE;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.adapter.DataStore;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.ddl.DdlManager;
import org.polypheny.db.ddl.exception.IndexPreventsRemovalException;
import org.polypheny.db.ddl.exception.LastPlacementException;
import org.polypheny.db.ddl.exception.PlacementNotExistsException;
import org.polypheny.db.jdbc.Context;
import org.polypheny.db.sql.SqlIdentifier;
import org.polypheny.db.sql.SqlNode;
import org.polypheny.db.sql.SqlNodeList;
import org.polypheny.db.sql.SqlUtil;
import org.polypheny.db.sql.SqlWriter;
import org.polypheny.db.sql.ddl.SqlAlterTable;
import org.polypheny.db.sql.parser.SqlParserPos;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.util.ImmutableNullableList;


/**
 * Parse tree for {@code ALTER TABLE name MODIFY PLACEMENT (columnList) ON STORE storeName} statement.
 */
@Slf4j
public class SqlAlterTableModifyPlacement extends SqlAlterTable {

    private final SqlIdentifier table;
    private final SqlNodeList columnList;
    private final SqlIdentifier storeName;
    private final List<Integer> partitionGroupList;
    private final List<SqlIdentifier> partitionGroupNamesList;


    public SqlAlterTableModifyPlacement(
            SqlParserPos pos,
            SqlIdentifier table,
            SqlNodeList columnList,
            SqlIdentifier storeName,
            List<Integer> partitionGroupList,
            List<SqlIdentifier> partitionGroupNamesList ) {
        super( pos );
        this.table = Objects.requireNonNull( table );
        this.columnList = Objects.requireNonNull( columnList );
        this.storeName = Objects.requireNonNull( storeName );
        this.partitionGroupList = partitionGroupList;
        this.partitionGroupNamesList = partitionGroupNamesList;
    }


    @Override
    public List<SqlNode> getOperandList() {
        return ImmutableNullableList.of( table, columnList, storeName );
    }


    @Override
    public void unparse( SqlWriter writer, int leftPrec, int rightPrec ) {
        writer.keyword( "ALTER" );
        writer.keyword( "TABLE" );
        table.unparse( writer, leftPrec, rightPrec );
        writer.keyword( "MODIFY" );
        writer.keyword( "PLACEMENT" );
        columnList.unparse( writer, leftPrec, rightPrec );
        writer.keyword( "ON" );
        writer.keyword( "STORE" );
        storeName.unparse( writer, leftPrec, rightPrec );

        if ( partitionGroupList != null || partitionGroupNamesList != null ) {
            writer.keyword( " WITH " );
            writer.keyword( " PARTITIONS" );
            SqlWriter.Frame frame = writer.startList( "(", ")" );
            if ( partitionGroupNamesList != null ) {
                for ( int i = 0; i < partitionGroupNamesList.size(); i++ ) {
                    partitionGroupNamesList.get( i ).unparse( writer, leftPrec, rightPrec );
                    if ( i + 1 < partitionGroupNamesList.size() ) {
                        writer.sep( "," );
                    }
                }
            }
        }

    }


    @Override
    public void execute( Context context, Statement statement ) {
        CatalogTable catalogTable = getCatalogTable( context, table );

        if ( catalogTable.isView() ) {
            throw new RuntimeException( "Not possible to use ALTER TABLE with Views" );
        }

        // You can't partition placements if the table is not partitioned
        if ( !catalogTable.isPartitioned && (!partitionGroupList.isEmpty() || !partitionGroupNamesList.isEmpty()) ) {
            throw new RuntimeException( "Partition Placement is not allowed for unpartitioned table '" + catalogTable.name + "'" );
        }

        // Check if all columns exist
        for ( SqlNode node : columnList.getList() ) {
            getCatalogColumn( catalogTable.id, (SqlIdentifier) node );
        }

        DataStore storeInstance = getDataStoreInstance( storeName );
        try {
            DdlManager.getInstance().modifyColumnPlacement(
                    catalogTable,
                    columnList.getList().stream()
                            .map( c -> getCatalogColumn( catalogTable.id, (SqlIdentifier) c ).id )
                            .collect( Collectors.toList() ),
                    partitionGroupList,
                    partitionGroupNamesList.stream()
                            .map( SqlIdentifier::toString )
                            .collect( Collectors.toList() ),
                    storeInstance,
                    statement );
        } catch ( PlacementNotExistsException e ) {
            throw SqlUtil.newContextException(
                    storeName.getParserPosition(),
                    RESOURCE.placementDoesNotExist( storeName.getSimple(), catalogTable.name ) );
        } catch ( IndexPreventsRemovalException e ) {
            throw SqlUtil.newContextException(
                    storeName.getParserPosition(),
                    RESOURCE.indexPreventsRemovalOfPlacement( e.getIndexName(), e.getColumnName() ) );
        } catch ( LastPlacementException e ) {
            throw SqlUtil.newContextException(
                    storeName.getParserPosition(),
                    RESOURCE.onlyOnePlacementLeft() );
        }

    }

}
