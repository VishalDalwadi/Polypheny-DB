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

package org.polypheny.db.rel.core;


import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import javax.annotation.Nonnull;
import org.bson.BsonValue;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.Catalog.TableType;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.plan.Contexts;
import org.polypheny.db.plan.RelOptCluster;
import org.polypheny.db.plan.RelOptTable;
import org.polypheny.db.plan.ViewExpanders;
import org.polypheny.db.prepare.RelOptTableImpl;
import org.polypheny.db.rel.RelCollation;
import org.polypheny.db.rel.RelDistribution;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.core.ConditionalExecute.Condition;
import org.polypheny.db.rel.logical.LogicalAggregate;
import org.polypheny.db.rel.logical.LogicalConditionalExecute;
import org.polypheny.db.rel.logical.LogicalCorrelate;
import org.polypheny.db.rel.logical.LogicalDocuments;
import org.polypheny.db.rel.logical.LogicalExchange;
import org.polypheny.db.rel.logical.LogicalFilter;
import org.polypheny.db.rel.logical.LogicalIntersect;
import org.polypheny.db.rel.logical.LogicalJoin;
import org.polypheny.db.rel.logical.LogicalMatch;
import org.polypheny.db.rel.logical.LogicalMinus;
import org.polypheny.db.rel.logical.LogicalProject;
import org.polypheny.db.rel.logical.LogicalSort;
import org.polypheny.db.rel.logical.LogicalSortExchange;
import org.polypheny.db.rel.logical.LogicalTableScan;
import org.polypheny.db.rel.logical.LogicalUnion;
import org.polypheny.db.rel.logical.LogicalValues;
import org.polypheny.db.rel.logical.LogicalViewTableScan;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.schema.LogicalTable;
import org.polypheny.db.schema.TranslatableTable;
import org.polypheny.db.sql.SemiJoinType;
import org.polypheny.db.sql.SqlKind;
import org.polypheny.db.tools.RelBuilder;
import org.polypheny.db.tools.RelBuilderFactory;
import org.polypheny.db.util.ImmutableBitSet;


/**
 * Contains factory interface and default implementation for creating various rel nodes.
 */
public class RelFactories {

    public static final ProjectFactory DEFAULT_PROJECT_FACTORY = new ProjectFactoryImpl();

    public static final FilterFactory DEFAULT_FILTER_FACTORY = new FilterFactoryImpl();

    public static final JoinFactory DEFAULT_JOIN_FACTORY = new JoinFactoryImpl();

    public static final CorrelateFactory DEFAULT_CORRELATE_FACTORY = new CorrelateFactoryImpl();

    public static final SemiJoinFactory DEFAULT_SEMI_JOIN_FACTORY = new SemiJoinFactoryImpl();

    public static final SortFactory DEFAULT_SORT_FACTORY = new SortFactoryImpl();

    public static final ExchangeFactory DEFAULT_EXCHANGE_FACTORY = new ExchangeFactoryImpl();

    public static final SortExchangeFactory DEFAULT_SORT_EXCHANGE_FACTORY = new SortExchangeFactoryImpl();

    public static final AggregateFactory DEFAULT_AGGREGATE_FACTORY = new AggregateFactoryImpl();

    public static final MatchFactory DEFAULT_MATCH_FACTORY = new MatchFactoryImpl();

    public static final SetOpFactory DEFAULT_SET_OP_FACTORY = new SetOpFactoryImpl();

    public static final ValuesFactory DEFAULT_VALUES_FACTORY = new ValuesFactoryImpl();

    public static final TableScanFactory DEFAULT_TABLE_SCAN_FACTORY = new TableScanFactoryImpl();

    public static final DocumentsFactory DEFAULT_DOCUMENTS_FACTORY = new DocumentsFactoryImpl();

    public static final ConditionalExecuteFactory DEFAULT_CONDITIONAL_EXECUTE_FACTORY = new ConditionalExecuteFactoryImpl();

    /**
     * A {@link RelBuilderFactory} that creates a {@link RelBuilder} that will create logical relational expressions for everything.
     */
    public static final RelBuilderFactory LOGICAL_BUILDER =
            RelBuilder.proto(
                    Contexts.of(
                            DEFAULT_PROJECT_FACTORY,
                            DEFAULT_FILTER_FACTORY,
                            DEFAULT_JOIN_FACTORY,
                            DEFAULT_SEMI_JOIN_FACTORY,
                            DEFAULT_SORT_FACTORY,
                            DEFAULT_EXCHANGE_FACTORY,
                            DEFAULT_SORT_EXCHANGE_FACTORY,
                            DEFAULT_AGGREGATE_FACTORY,
                            DEFAULT_MATCH_FACTORY,
                            DEFAULT_SET_OP_FACTORY,
                            DEFAULT_VALUES_FACTORY,
                            DEFAULT_TABLE_SCAN_FACTORY,
                            DEFAULT_CONDITIONAL_EXECUTE_FACTORY ) );


    private RelFactories() {
    }


    /**
     * Can create a {@link org.polypheny.db.rel.logical.LogicalProject} of the appropriate type for this rule's calling convention.
     */
    public interface ProjectFactory {

        /**
         * Creates a project.
         */
        RelNode createProject( RelNode input, List<? extends RexNode> childExprs, List<String> fieldNames );

    }


    /**
     * Implementation of {@link ProjectFactory} that returns a vanilla
     * {@link org.polypheny.db.rel.logical.LogicalProject}.
     */
    private static class ProjectFactoryImpl implements ProjectFactory {

        @Override
        public RelNode createProject( RelNode input, List<? extends RexNode> childExprs, List<String> fieldNames ) {
            return LogicalProject.create( input, childExprs, fieldNames );
        }

    }


    /**
     * Can create a {@link Sort} of the appropriate type for this rule's calling convention.
     */
    public interface SortFactory {

        /**
         * Creates a sort.
         */
        RelNode createSort( RelNode input, RelCollation collation, RexNode offset, RexNode fetch );

    }


    /**
     * Implementation of {@link RelFactories.SortFactory} that returns a vanilla {@link Sort}.
     */
    private static class SortFactoryImpl implements SortFactory {

        @Override
        public RelNode createSort( RelNode input, RelCollation collation, RexNode offset, RexNode fetch ) {
            return LogicalSort.create( input, collation, offset, fetch );
        }

    }


    /**
     * Can create a {@link org.polypheny.db.rel.core.Exchange} of the appropriate type for a rule's calling convention.
     */
    public interface ExchangeFactory {

        /**
         * Creates a Exchange.
         */
        RelNode createExchange( RelNode input, RelDistribution distribution );

    }


    /**
     * Implementation of {@link RelFactories.ExchangeFactory} that returns a {@link Exchange}.
     */
    private static class ExchangeFactoryImpl implements ExchangeFactory {

        @Override
        public RelNode createExchange( RelNode input, RelDistribution distribution ) {
            return LogicalExchange.create( input, distribution );
        }

    }


    /**
     * Can create a {@link SortExchange} of the appropriate type for a rule's calling convention.
     */
    public interface SortExchangeFactory {

        /**
         * Creates a {@link SortExchange}.
         */
        RelNode createSortExchange( RelNode input, RelDistribution distribution, RelCollation collation );

    }


    /**
     * Implementation of {@link RelFactories.SortExchangeFactory} that returns a {@link SortExchange}.
     */
    private static class SortExchangeFactoryImpl implements SortExchangeFactory {

        @Override
        public RelNode createSortExchange( RelNode input, RelDistribution distribution, RelCollation collation ) {
            return LogicalSortExchange.create( input, distribution, collation );
        }

    }


    /**
     * Can create a {@link SetOp} for a particular kind of set operation (UNION, EXCEPT, INTERSECT) and of the appropriate type for this rule's calling convention.
     */
    public interface SetOpFactory {

        /**
         * Creates a set operation.
         */
        RelNode createSetOp( SqlKind kind, List<RelNode> inputs, boolean all );

    }


    /**
     * Implementation of {@link RelFactories.SetOpFactory} that returns a vanilla {@link SetOp} for the particular kind of set operation (UNION, EXCEPT, INTERSECT).
     */
    private static class SetOpFactoryImpl implements SetOpFactory {

        @Override
        public RelNode createSetOp( SqlKind kind, List<RelNode> inputs, boolean all ) {
            switch ( kind ) {
                case UNION:
                    return LogicalUnion.create( inputs, all );
                case EXCEPT:
                    return LogicalMinus.create( inputs, all );
                case INTERSECT:
                    return LogicalIntersect.create( inputs, all );
                default:
                    throw new AssertionError( "not a set op: " + kind );
            }
        }

    }


    /**
     * Can create a {@link LogicalAggregate} of the appropriate type for this rule's calling convention.
     */
    public interface AggregateFactory {

        /**
         * Creates an aggregate.
         */
        RelNode createAggregate( RelNode input, boolean indicator, ImmutableBitSet groupSet, ImmutableList<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls );

    }


    /**
     * Implementation of {@link RelFactories.AggregateFactory} that returns a vanilla {@link LogicalAggregate}.
     */
    private static class AggregateFactoryImpl implements AggregateFactory {

        @Override
        @SuppressWarnings("deprecation")
        public RelNode createAggregate( RelNode input, boolean indicator, ImmutableBitSet groupSet, ImmutableList<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls ) {
            return LogicalAggregate.create( input, indicator, groupSet, groupSets, aggCalls );
        }

    }


    /**
     * Can create a {@link LogicalFilter} of the appropriate type for this rule's calling convention.
     */
    public interface FilterFactory {

        /**
         * Creates a filter.
         */
        RelNode createFilter( RelNode input, RexNode condition );

    }


    /**
     * Implementation of {@link RelFactories.FilterFactory} that returns a vanilla {@link LogicalFilter}.
     */
    private static class FilterFactoryImpl implements FilterFactory {

        @Override
        public RelNode createFilter( RelNode input, RexNode condition ) {
            return LogicalFilter.create( input, condition );
        }

    }


    /**
     * Can create a join of the appropriate type for a rule's calling convention.
     *
     * The result is typically a {@link Join}.
     */
    public interface JoinFactory {

        /**
         * Creates a join.
         *
         * @param left Left input
         * @param right Right input
         * @param condition Join condition
         * @param variablesSet Set of variables that are set by the LHS and used by the RHS and are not available to nodes above this LogicalJoin in the tree
         * @param joinType Join type
         * @param semiJoinDone Whether this join has been translated to a semi-join
         */
        RelNode createJoin( RelNode left, RelNode right, RexNode condition, Set<CorrelationId> variablesSet, JoinRelType joinType, boolean semiJoinDone );

    }


    /**
     * Implementation of {@link JoinFactory} that returns a vanilla {@link org.polypheny.db.rel.logical.LogicalJoin}.
     */
    private static class JoinFactoryImpl implements JoinFactory {

        @Override
        public RelNode createJoin( RelNode left, RelNode right, RexNode condition, Set<CorrelationId> variablesSet, JoinRelType joinType, boolean semiJoinDone ) {
            return LogicalJoin.create( left, right, condition, variablesSet, joinType, semiJoinDone, ImmutableList.of() );
        }

    }


    /**
     * Can create a correlate of the appropriate type for a rule's calling convention.
     *
     * The result is typically a {@link Correlate}.
     */
    public interface CorrelateFactory {

        /**
         * Creates a correlate.
         *
         * @param left Left input
         * @param right Right input
         * @param correlationId Variable name for the row of left input
         * @param requiredColumns Required columns
         * @param joinType Join type
         */
        RelNode createCorrelate( RelNode left, RelNode right, CorrelationId correlationId, ImmutableBitSet requiredColumns, SemiJoinType joinType );

    }


    /**
     * Implementation of {@link CorrelateFactory} that returns a vanilla {@link org.polypheny.db.rel.logical.LogicalCorrelate}.
     */
    private static class CorrelateFactoryImpl implements CorrelateFactory {

        @Override
        public RelNode createCorrelate( RelNode left, RelNode right, CorrelationId correlationId, ImmutableBitSet requiredColumns, SemiJoinType joinType ) {
            return LogicalCorrelate.create( left, right, correlationId, requiredColumns, joinType );
        }

    }


    /**
     * Can create a semi-join of the appropriate type for a rule's calling convention.
     */
    public interface SemiJoinFactory {

        /**
         * Creates a semi-join.
         *
         * @param left Left input
         * @param right Right input
         * @param condition Join condition
         */
        RelNode createSemiJoin( RelNode left, RelNode right, RexNode condition );

    }


    /**
     * Implementation of {@link SemiJoinFactory} that returns a vanilla {@link SemiJoin}.
     */
    private static class SemiJoinFactoryImpl implements SemiJoinFactory {

        @Override
        public RelNode createSemiJoin( RelNode left, RelNode right, RexNode condition ) {
            final JoinInfo joinInfo = JoinInfo.of( left, right, condition );
            return SemiJoin.create( left, right, condition, joinInfo.leftKeys, joinInfo.rightKeys );
        }

    }


    /**
     * Can create a {@link Values} of the appropriate type for a rule's calling convention.
     */
    public interface ValuesFactory {

        /**
         * Creates a Values.
         */
        RelNode createValues( RelOptCluster cluster, RelDataType rowType, List<ImmutableList<RexLiteral>> tuples );

    }


    /**
     * Implementation of {@link ValuesFactory} that returns a {@link LogicalValues}.
     */
    private static class ValuesFactoryImpl implements ValuesFactory {

        @Override
        public RelNode createValues( RelOptCluster cluster, RelDataType rowType, List<ImmutableList<RexLiteral>> tuples ) {
            return LogicalValues.create( cluster, rowType, ImmutableList.copyOf( tuples ) );
        }

    }


    public interface DocumentsFactory {

        RelNode createDocuments(
                RelOptCluster cluster,
                ImmutableList<BsonValue> tuples,
                RelDataType rowType,
                ImmutableList<ImmutableList<RexLiteral>> normalizedTuple );

    }


    private static class DocumentsFactoryImpl implements DocumentsFactory {

        @Override
        public RelNode createDocuments(
                RelOptCluster cluster,
                ImmutableList<BsonValue> tuples,
                RelDataType rowType,
                ImmutableList<ImmutableList<RexLiteral>> normalizedTuple ) {
            return LogicalDocuments.create( cluster, ImmutableList.copyOf( tuples ), rowType, ImmutableList.copyOf( normalizedTuple ) );
        }

    }


    /**
     * Can create a {@link TableScan} of the appropriate type for a rule's calling convention.
     */
    public interface TableScanFactory {

        /**
         * Creates a {@link TableScan}.
         */
        RelNode createScan( RelOptCluster cluster, RelOptTable table );

    }


    /**
     * Implementation of {@link TableScanFactory} that returns a {@link LogicalTableScan}.
     */
    private static class TableScanFactoryImpl implements TableScanFactory {

        @Override
        public RelNode createScan( RelOptCluster cluster, RelOptTable table ) {

            //check if RelOptTable contains a View, in this case a LogicalViewTableScan needs to be created
            if ( (((RelOptTableImpl) table).getTable()) instanceof LogicalTable ) {
                Catalog catalog = Catalog.getInstance();
                long idLogical = ((LogicalTable) ((RelOptTableImpl) table).getTable()).getTableId();
                CatalogTable catalogTable = catalog.getTable( idLogical );
                if ( catalogTable.tableType == TableType.VIEW ) {
                    return LogicalViewTableScan.create( cluster, table );
                } else {
                    return LogicalTableScan.create( cluster, table );
                }
            } else {
                return LogicalTableScan.create( cluster, table );
            }
        }

    }


    /**
     * Creates a {@link TableScanFactory} that can expand {@link TranslatableTable} instances, but explodes on views.
     *
     * @param tableScanFactory Factory for non-translatable tables
     * @return Table scan factory
     */
    @Nonnull
    public static TableScanFactory expandingScanFactory(
            @Nonnull TableScanFactory tableScanFactory ) {
        return expandingScanFactory(
                ( rowType, queryString, schemaPath, viewPath ) -> {
                    throw new UnsupportedOperationException( "cannot expand view" );
                },
                tableScanFactory );
    }


    /**
     * Creates a {@link TableScanFactory} that uses a {@link org.polypheny.db.plan.RelOptTable.ViewExpander} to handle {@link TranslatableTable} instances,
     * and falls back to a default factory for other tables.
     *
     * @param viewExpander View expander
     * @param tableScanFactory Factory for non-translatable tables
     * @return Table scan factory
     */
    @Nonnull
    public static TableScanFactory expandingScanFactory(
            @Nonnull RelOptTable.ViewExpander viewExpander,
            @Nonnull TableScanFactory tableScanFactory ) {
        return ( cluster, table ) -> {
            final TranslatableTable translatableTable = table.unwrap( TranslatableTable.class );
            if ( translatableTable != null ) {
                final RelOptTable.ToRelContext toRelContext = ViewExpanders.toRelContext( viewExpander, cluster );
                return translatableTable.toRel( toRelContext, table );
            }
            return tableScanFactory.createScan( cluster, table );
        };
    }


    /**
     * Can create a {@link Match} of the appropriate type for a rule's calling convention.
     */
    public interface MatchFactory {

        /**
         * Creates a {@link Match}.
         */
        RelNode createMatch( RelNode input, RexNode pattern, RelDataType rowType, boolean strictStart, boolean strictEnd, Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures,
                RexNode after, Map<String, ? extends SortedSet<String>> subsets, boolean allRows, List<RexNode> partitionKeys, RelCollation orderKeys, RexNode interval );

    }


    /**
     * Implementation of {@link MatchFactory} that returns a {@link LogicalMatch}.
     */
    private static class MatchFactoryImpl implements MatchFactory {

        @Override
        public RelNode createMatch( RelNode input, RexNode pattern, RelDataType rowType, boolean strictStart, boolean strictEnd, Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures,
                RexNode after, Map<String, ? extends SortedSet<String>> subsets, boolean allRows, List<RexNode> partitionKeys, RelCollation orderKeys, RexNode interval ) {
            return LogicalMatch.create( input, rowType, pattern, strictStart, strictEnd, patternDefinitions, measures, after, subsets, allRows, partitionKeys, orderKeys, interval );
        }

    }


    /**
     * Can create a {@link ConditionalExecute} of the appropriate type for a rule's calling convention.
     */
    public interface ConditionalExecuteFactory {

        /**
         * Creates a {@link ConditionalExecute}.
         */
        RelNode createConditionalExecute( RelNode left, RelNode right, Condition condition, Class<? extends Exception> exceptionClass, String exceptionMessage );

    }


    /**
     * Implementation of {@link MatchFactory} that returns a {@link LogicalMatch}.
     */
    private static class ConditionalExecuteFactoryImpl implements ConditionalExecuteFactory {

        @Override
        public RelNode createConditionalExecute( RelNode left, RelNode right, Condition condition, Class<? extends Exception> exceptionClass, String exceptionMessage ) {
            return LogicalConditionalExecute.create( left, right, condition, exceptionClass, exceptionMessage );
        }

    }

}
