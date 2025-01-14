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

package org.polypheny.db.cql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.cql.utils.ExclusiveComparisons;
import org.polypheny.db.rel.RelCollations;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.core.AggregateCall;
import org.polypheny.db.rex.RexBuilder;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.sql.SqlAggFunction;
import org.polypheny.db.sql.fun.SqlStdOperatorTable;
import org.polypheny.db.tools.RelBuilder;
import org.polypheny.db.tools.RelBuilder.AggCall;
import org.polypheny.db.tools.RelBuilder.GroupKey;
import org.polypheny.db.util.ImmutableBitSet;


/**
 * A collection of the columns explicitly stated in the query
 * for projection.
 */
@Slf4j
public class Projections {

    private static final List<String> aggregateFunctions = new ArrayList<>();


    static {
        aggregateFunctions.add( "count" );
        aggregateFunctions.add( "max" );
        aggregateFunctions.add( "min" );
        aggregateFunctions.add( "sum" );
        aggregateFunctions.add( "avg" );
    }


    private final List<Projection> projections = new ArrayList<>();
    private final List<Aggregation> aggregations = new ArrayList<>();
    private final List<Grouping> groupings = new ArrayList<>();
    private final Map<Long, Integer> projectedColumnOrdinalities = new HashMap<>();


    public static String getAggregationFunction( ColumnIndex columnIndex, Map<String, Modifier> modifiers ) {

        boolean[] booleans = new boolean[]{
                modifiers.containsKey( aggregateFunctions.get( 0 ) ),
                modifiers.containsKey( aggregateFunctions.get( 1 ) ),
                modifiers.containsKey( aggregateFunctions.get( 2 ) ),
                modifiers.containsKey( aggregateFunctions.get( 3 ) ),
                modifiers.containsKey( aggregateFunctions.get( 4 ) )
        };

        int index = ExclusiveComparisons.GetExclusivelyTrue( false, booleans );

        if ( index != -1 ) {
            index--;
            return aggregateFunctions.get( index );
        } else {
            return null;
        }
    }


    public boolean exists() {
        return projections.size() != 0;
    }


    /**
     * Creates and adds a {@link Projection}.
     * Also classifies it into {@link Aggregation} or {@link Grouping}.
     */
    public void add( ColumnIndex columnIndex, Map<String, Modifier> modifiers ) {
        Projection projection;
        String aggregationFunction = getAggregationFunction( columnIndex, modifiers );

        if ( aggregationFunction == null ) {
            if ( log.isDebugEnabled() ) {
                log.debug( "Adding grouping for ColumnIndex '{}'.", columnIndex.fullyQualifiedName );
            }
            Grouping grouping = new Grouping( columnIndex, modifiers );
            groupings.add( grouping );
            projection = grouping;
        } else {
            Aggregation aggregation = new Aggregation( columnIndex, modifiers, aggregationFunction );
            if ( log.isDebugEnabled() ) {
                log.debug( "Adding aggregation for ColumnIndex '{}'.", aggregation.getProjectionName() );
            }
            aggregations.add( aggregation );
            projection = aggregation;
        }

        projections.add( projection );
    }


    /**
     * Creates projection. Used when there are no Filters.
     *
     * @param tableScanOrdinalities Ordinalities of the columns after table scan.
     * @param relBuilder {@link RelBuilder}.
     * @param rexBuilder {@link RexBuilder}.
     * @return {@link RelBuilder}.
     */
    public RelBuilder convert2RelForSingleProjection( Map<Long, Integer> tableScanOrdinalities, RelBuilder relBuilder, RexBuilder rexBuilder ) {
        setColumnOrdinalities();

        List<RexNode> inputRefs = new ArrayList<>();
        List<String> aliases = new ArrayList<>();
        RelNode baseNode = relBuilder.peek();

        for ( Projection projection : projections ) {
            int ordinality = tableScanOrdinalities.get( projection.getColumnId() );
            RexNode inputRef = projection.createRexNode( rexBuilder, baseNode, ordinality );
            inputRefs.add( inputRef );
            aliases.add( projection.getProjectionName() );
        }

        log.debug( "Generating projection." );
        relBuilder = relBuilder.project( inputRefs, aliases, true );

        return relBuilder;
    }


    /**
     * Makes the call to {@link RelBuilder#aggregate(GroupKey, AggCall...)}
     * if there are any aggregations.
     * Makes the final projection.
     *
     * @param tableScanOrdinalities Ordinalities of the columns after table scan /
     * initial projection.
     * @param relBuilder {@link RelBuilder}.
     * @param rexBuilder {@link RexBuilder}.
     * @return {@link RelBuilder}.
     */
    public RelBuilder convert2Rel( Map<Long, Integer> tableScanOrdinalities, RelBuilder relBuilder, RexBuilder rexBuilder ) {
        RelNode baseNode = relBuilder.peek();

        if ( !aggregations.isEmpty() ) {
            log.debug( "Found aggregations. Creating AggregateCalls." );
            List<AggregateCall> aggregateCalls = new ArrayList<>();

            for ( Aggregation aggregation : aggregations ) {
                AggregateCall aggregateCall = aggregation.getAggregateCall(
                        baseNode,
                        tableScanOrdinalities.get( aggregation.getColumnId() ),
                        groupings.size()
                );
                aggregateCalls.add( aggregateCall );
            }

            if ( groupings.isEmpty() ) {
                log.debug( "No groupings for current query." );
            } else {
                log.debug( "Found groupings. Creating groupings ordinal list." );
            }
            List<Integer> groupByOrdinals = new ArrayList<>();
            for ( Grouping grouping : groupings ) {
                groupByOrdinals.add(
                        tableScanOrdinalities.get( grouping.getColumnId() )
                );
            }

            GroupKey groupKey = relBuilder.groupKey( ImmutableBitSet.of( groupByOrdinals ) );
            log.debug( "Generating aggregates." );
            relBuilder = relBuilder.aggregate( groupKey, aggregateCalls );
        }

        setColumnOrdinalities();

        List<RexNode> inputRefs = new ArrayList<>();
        List<String> aliases = new ArrayList<>();
        baseNode = relBuilder.peek();

        for ( Projection projection : projections ) {
            int ordinality = projectedColumnOrdinalities.get( projection.getColumnId() );
            RexNode inputRef = projection.createRexNode( rexBuilder, baseNode, ordinality );
            inputRefs.add( inputRef );
            aliases.add( projection.getProjectionName() );
        }

        log.debug( "Generating final projection." );
        relBuilder = relBuilder.project( inputRefs, aliases, true );

        return relBuilder;
    }


    /**
     * Sets the column ordinalities to match the order in the aggregation
     * rel node i.e. {@link #groupings} followed by {@link #aggregations}.
     */
    private void setColumnOrdinalities() {
        assert projectedColumnOrdinalities.size() == 0;

        for ( Grouping grouping : groupings ) {
            projectedColumnOrdinalities.put( grouping.getColumnId(), projectedColumnOrdinalities.size() );
        }
        for ( Aggregation aggregation : aggregations ) {
            projectedColumnOrdinalities.put( aggregation.getColumnId(), projectedColumnOrdinalities.size() );
        }
    }


    public boolean hasAggregations() {
        return aggregations.size() >= 1;
    }


    public Map<Long, Integer> getProjectionColumnOrdinalities() {
        return projectedColumnOrdinalities;
    }


    public enum AggregationFunctions {
        COUNT( SqlStdOperatorTable.COUNT ),
        MAX( SqlStdOperatorTable.MAX ),
        MIN( SqlStdOperatorTable.MIN ),
        SUM( SqlStdOperatorTable.SUM ),
        AVG( SqlStdOperatorTable.AVG );


        private final SqlAggFunction sqlAggFunction;


        AggregationFunctions( SqlAggFunction sqlAggFunction ) {
            this.sqlAggFunction = sqlAggFunction;
        }


        static AggregationFunctions createFromString( String aggregateFunction ) {
            if ( aggregateFunction.equalsIgnoreCase( "count" ) ) {
                return COUNT;
            } else if ( aggregateFunction.equalsIgnoreCase( "max" ) ) {
                return MAX;
            } else if ( aggregateFunction.equalsIgnoreCase( "min" ) ) {
                return MIN;
            } else if ( aggregateFunction.equalsIgnoreCase( "sum" ) ) {
                return SUM;
            } else if ( aggregateFunction.equalsIgnoreCase( "avg" ) ) {
                return AVG;
            } else {
                return null;
            }
        }


        SqlAggFunction getSqlAggFunction() {
            return this.sqlAggFunction;
        }


        public String getAliasWithColumnName( String fullyQualifiedName ) {
            return sqlAggFunction.getName() + "( " + fullyQualifiedName + " )";
        }

    }


    public static abstract class Projection {

        protected final ColumnIndex columnIndex;
        protected final Map<String, Modifier> modifiers;


        public Projection( ColumnIndex columnIndex, Map<String, Modifier> modifiers ) {
            this.columnIndex = columnIndex;
            this.modifiers = modifiers;
        }


        public long getColumnId() {
            return columnIndex.catalogColumn.id;
        }


        public String getProjectionName() {
            return columnIndex.fullyQualifiedName;
        }


        public RexNode createRexNode( RexBuilder rexBuilder, RelNode baseNode, int ordinality ) {
            return rexBuilder.makeInputRef( baseNode, ordinality );
        }

    }


    public static class Aggregation extends Projection {

        private final AggregationFunctions aggregationFunction;


        public Aggregation( ColumnIndex columnIndex, Map<String, Modifier> modifiers, String aggregationFunction ) {
            super( columnIndex, modifiers );
            this.aggregationFunction = AggregationFunctions.createFromString( aggregationFunction );
        }


        @Override
        public String getProjectionName() {
            return aggregationFunction.getAliasWithColumnName( columnIndex.fullyQualifiedName );
        }


        public AggregateCall getAggregateCall( RelNode baseNode, int ordinality, int groupCount ) {
            if ( log.isDebugEnabled() ) {
                log.debug( "Creating AggregateCall for '{}'.", getProjectionName() );
            }
            List<Integer> inputFields = new ArrayList<>();
            inputFields.add( ordinality );
            return AggregateCall.create(
                    aggregationFunction.getSqlAggFunction(),
                    false,
                    false,
                    inputFields,
                    -1,
                    RelCollations.EMPTY,
                    groupCount,
                    baseNode,
                    null,
                    aggregationFunction.getAliasWithColumnName( columnIndex.fullyQualifiedName )
            );
        }

    }


    private static class Grouping extends Projection {

        public Grouping( ColumnIndex columnIndex, Map<String, Modifier> modifiers ) {
            super( columnIndex, modifiers );
        }

    }

}
