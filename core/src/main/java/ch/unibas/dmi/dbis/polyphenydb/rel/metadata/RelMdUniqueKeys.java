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

package ch.unibas.dmi.dbis.polyphenydb.rel.metadata;


import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Aggregate;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Correlate;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Filter;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Join;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.JoinInfo;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Project;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.SemiJoin;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.SetOp;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Sort;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexInputRef;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.util.BuiltInMethod;
import ch.unibas.dmi.dbis.polyphenydb.util.ImmutableBitSet;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * RelMdUniqueKeys supplies a default implementation of {@link RelMetadataQuery#getUniqueKeys} for the standard logical algebra.
 */
public class RelMdUniqueKeys implements MetadataHandler<BuiltInMetadata.UniqueKeys> {

    public static final RelMetadataProvider SOURCE = ReflectiveRelMetadataProvider.reflectiveSource( BuiltInMethod.UNIQUE_KEYS.method, new RelMdUniqueKeys() );


    private RelMdUniqueKeys() {
    }


    public MetadataDef<BuiltInMetadata.UniqueKeys> getDef() {
        return BuiltInMetadata.UniqueKeys.DEF;
    }


    public Set<ImmutableBitSet> getUniqueKeys( Filter rel, RelMetadataQuery mq, boolean ignoreNulls ) {
        return mq.getUniqueKeys( rel.getInput(), ignoreNulls );
    }


    public Set<ImmutableBitSet> getUniqueKeys( Sort rel, RelMetadataQuery mq, boolean ignoreNulls ) {
        return mq.getUniqueKeys( rel.getInput(), ignoreNulls );
    }


    public Set<ImmutableBitSet> getUniqueKeys( Correlate rel, RelMetadataQuery mq, boolean ignoreNulls ) {
        return mq.getUniqueKeys( rel.getLeft(), ignoreNulls );
    }


    public Set<ImmutableBitSet> getUniqueKeys( Project rel, RelMetadataQuery mq, boolean ignoreNulls ) {
        // LogicalProject maps a set of rows to a different set;
        // Without knowledge of the mapping function(whether it preserves uniqueness), it is only safe to derive uniqueness info from the child of a project when the mapping is f(a) => a.
        //
        // Further more, the unique bitset coming from the child needs to be mapped to match the output of the project.
        final Map<Integer, Integer> mapInToOutPos = new HashMap<>();
        final List<RexNode> projExprs = rel.getProjects();
        final Set<ImmutableBitSet> projUniqueKeySet = new HashSet<>();

        // Build an input to output position map.
        for ( int i = 0; i < projExprs.size(); i++ ) {
            RexNode projExpr = projExprs.get( i );
            if ( projExpr instanceof RexInputRef ) {
                mapInToOutPos.put( ((RexInputRef) projExpr).getIndex(), i );
            }
        }

        if ( mapInToOutPos.isEmpty() ) {
            // if there's no RexInputRef in the projected expressions return empty set.
            return projUniqueKeySet;
        }

        Set<ImmutableBitSet> childUniqueKeySet = mq.getUniqueKeys( rel.getInput(), ignoreNulls );

        if ( childUniqueKeySet != null ) {
            // Now add to the projUniqueKeySet the child keys that are fully projected.
            for ( ImmutableBitSet colMask : childUniqueKeySet ) {
                ImmutableBitSet.Builder tmpMask = ImmutableBitSet.builder();
                boolean completeKeyProjected = true;
                for ( int bit : colMask ) {
                    if ( mapInToOutPos.containsKey( bit ) ) {
                        tmpMask.set( mapInToOutPos.get( bit ) );
                    } else {
                        // Skip the child unique key if part of it is not projected.
                        completeKeyProjected = false;
                        break;
                    }
                }
                if ( completeKeyProjected ) {
                    projUniqueKeySet.add( tmpMask.build() );
                }
            }
        }

        return projUniqueKeySet;
    }


    public Set<ImmutableBitSet> getUniqueKeys( Join rel, RelMetadataQuery mq, boolean ignoreNulls ) {
        final RelNode left = rel.getLeft();
        final RelNode right = rel.getRight();

        // first add the different combinations of concatenated unique keys from the left and the right, adjusting the right hand side keys to
        // reflect the addition of the left hand side
        //
        // NOTE zfong 12/18/06 - If the number of tables in a join is large, the number of combinations of unique key sets will explode.
        // If that is undesirable, use RelMetadataQuery.areColumnsUnique() as an alternative way of getting unique key information.

        final Set<ImmutableBitSet> retSet = new HashSet<>();
        final Set<ImmutableBitSet> leftSet = mq.getUniqueKeys( left, ignoreNulls );
        Set<ImmutableBitSet> rightSet = null;

        final Set<ImmutableBitSet> tmpRightSet = mq.getUniqueKeys( right, ignoreNulls );
        int nFieldsOnLeft = left.getRowType().getFieldCount();

        if ( tmpRightSet != null ) {
            rightSet = new HashSet<>();
            for ( ImmutableBitSet colMask : tmpRightSet ) {
                ImmutableBitSet.Builder tmpMask = ImmutableBitSet.builder();
                for ( int bit : colMask ) {
                    tmpMask.set( bit + nFieldsOnLeft );
                }
                rightSet.add( tmpMask.build() );
            }

            if ( leftSet != null ) {
                for ( ImmutableBitSet colMaskRight : rightSet ) {
                    for ( ImmutableBitSet colMaskLeft : leftSet ) {
                        retSet.add( colMaskLeft.union( colMaskRight ) );
                    }
                }
            }
        }

        // locate the columns that participate in equijoins
        final JoinInfo joinInfo = rel.analyzeCondition();

        // determine if either or both the LHS and RHS are unique on the equijoin columns
        final Boolean leftUnique = mq.areColumnsUnique( left, joinInfo.leftSet(), ignoreNulls );
        final Boolean rightUnique = mq.areColumnsUnique( right, joinInfo.rightSet(), ignoreNulls );

        // if the right hand side is unique on its equijoin columns, then we can add the unique keys from left if the left hand side is not null generating
        if ( (rightUnique != null) && rightUnique && (leftSet != null) && !(rel.getJoinType().generatesNullsOnLeft()) ) {
            retSet.addAll( leftSet );
        }

        // same as above except left and right are reversed
        if ( (leftUnique != null) && leftUnique && (rightSet != null) && !(rel.getJoinType().generatesNullsOnRight()) ) {
            retSet.addAll( rightSet );
        }

        return retSet;
    }


    public Set<ImmutableBitSet> getUniqueKeys( SemiJoin rel, RelMetadataQuery mq, boolean ignoreNulls ) {
        // only return the unique keys from the LHS since a semijoin only returns the LHS
        return mq.getUniqueKeys( rel.getLeft(), ignoreNulls );
    }


    public Set<ImmutableBitSet> getUniqueKeys( Aggregate rel, RelMetadataQuery mq, boolean ignoreNulls ) {
        // group by keys form a unique key
        return ImmutableSet.of( rel.getGroupSet() );
    }


    public Set<ImmutableBitSet> getUniqueKeys( SetOp rel, RelMetadataQuery mq, boolean ignoreNulls ) {
        if ( !rel.all ) {
            return ImmutableSet.of( ImmutableBitSet.range( rel.getRowType().getFieldCount() ) );
        }
        return ImmutableSet.of();
    }


    // Catch-all rule when none of the others apply.
    public Set<ImmutableBitSet> getUniqueKeys( RelNode rel, RelMetadataQuery mq, boolean ignoreNulls ) {
        // no information available
        return null;
    }
}