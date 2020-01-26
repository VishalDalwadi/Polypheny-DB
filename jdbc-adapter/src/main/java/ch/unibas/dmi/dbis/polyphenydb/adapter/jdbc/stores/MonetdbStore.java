/*
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
 *
 */

package ch.unibas.dmi.dbis.polyphenydb.adapter.jdbc.stores;


import ch.unibas.dmi.dbis.polyphenydb.PolySqlType;
import ch.unibas.dmi.dbis.polyphenydb.adapter.jdbc.connection.ConnectionFactory;
import ch.unibas.dmi.dbis.polyphenydb.adapter.jdbc.connection.TransactionalConnectionFactory;
import ch.unibas.dmi.dbis.polyphenydb.catalog.entity.combined.CatalogCombinedTable;
import ch.unibas.dmi.dbis.polyphenydb.schema.Schema;
import ch.unibas.dmi.dbis.polyphenydb.schema.Table;
import ch.unibas.dmi.dbis.polyphenydb.sql.dialect.MonetdbSqlDialect;
import com.google.common.collect.ImmutableList;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp2.BasicDataSource;


@Slf4j
public class MonetdbStore extends AbstractJdbcStore {

    public static final String ADAPTER_NAME = "MonetDB";

    public static final String DESCRIPTION = "MonetDB is an open-source column-oriented database management system.";

    public static final List<AdapterSetting> AVAILABLE_SETTINGS = ImmutableList.of(
            new AdapterSettingString( "host", false, true, false, "localhost" ),
            new AdapterSettingInteger( "port", false, true, false, 50000 ),
            new AdapterSettingString( "database", false, true, false, "polypheny" ),
            new AdapterSettingString( "username", false, true, false, "polypheny" ),
            new AdapterSettingString( "password", false, true, false, "" ),
            new AdapterSettingInteger( "maxConnections", false, true, false, 25 )
    );


    public MonetdbStore( int storeId, String uniqueName, final Map<String, String> settings ) {
        super( storeId, uniqueName, settings, createConnectionFactory( settings ), MonetdbSqlDialect.DEFAULT );
    }


    public static ConnectionFactory createConnectionFactory( final Map<String, String> settings ) {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName( "nl.cwi.monetdb.jdbc.MonetDriver" );

        final String connectionUrl = getConnectionUrl( settings.get( "host" ), Integer.parseInt( settings.get( "port" ) ), settings.get( "database" ) );
        dataSource.setUrl( connectionUrl );
        log.info( "MonetDB Connection URL: {}", connectionUrl );
        dataSource.setUsername( settings.get( "username" ) );
        dataSource.setPassword( settings.get( "password" ) );
        dataSource.setDefaultAutoCommit( false );
        return new TransactionalConnectionFactory( dataSource, Integer.parseInt( settings.get( "maxConnections" ) ) );
    }


    @Override
    public Table createTableSchema( CatalogCombinedTable combinedTable ) {
        return currentJdbcSchema.createJdbcTable( combinedTable );
    }


    @Override
    public Schema getCurrentSchema() {
        return currentJdbcSchema;
    }


    @Override
    public String getAdapterName() {
        return ADAPTER_NAME;
    }


    @Override
    public List<AdapterSetting> getAvailableSettings() {
        return AVAILABLE_SETTINGS;
    }


    @Override
    public void shutdown() {
        try {
            removeInformationPage();
            connectionFactory.close();
        } catch ( SQLException e ) {
            log.warn( "Exception while shutting down {}", getUniqueName(), e );
        }
    }


    @Override
    protected void reloadSettings( List<String> updatedSettings ) {
        // TODO: Implement disconnect and reconnect to MonetDB instance.
    }


    @Override
    protected String getTypeString( PolySqlType polySqlType ) {
        switch ( polySqlType ) {
            case BOOLEAN:
                return "BOOLEAN";
            case VARBINARY:
                throw new RuntimeException( "Unsupported datatype: " + polySqlType.name() );
            case INTEGER:
                return "INT";
            case BIGINT:
                return "BIGINT";
            case REAL:
                return "REAL";
            case DOUBLE:
                return "DOUBLE";
            case DECIMAL:
                return "DECIMAL";
            case VARCHAR:
                return "VARCHAR";
            case TEXT:
                return "TEXT";
            case DATE:
                return "DATE";
            case TIME:
                return "TIME";
            case TIMESTAMP:
                return "TIMESTAMP";
        }
        throw new RuntimeException( "Unknown type: " + polySqlType.name() );
    }


    private static String getConnectionUrl( final String dbHostname, final int dbPort, final String dbName ) {
        return String.format( "jdbc:monetdb://%s:%d/%s", dbHostname, dbPort, dbName );
    }

}