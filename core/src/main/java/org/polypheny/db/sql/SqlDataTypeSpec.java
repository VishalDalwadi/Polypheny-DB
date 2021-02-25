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

package org.polypheny.db.sql;


import java.nio.charset.Charset;
import java.util.Objects;
import java.util.TimeZone;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeFactory;
import org.polypheny.db.sql.parser.SqlParserPos;
import org.polypheny.db.sql.util.SqlVisitor;
import org.polypheny.db.sql.validate.SqlMonotonicity;
import org.polypheny.db.sql.validate.SqlValidator;
import org.polypheny.db.sql.validate.SqlValidatorScope;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.PolyTypeUtil;
import org.polypheny.db.util.Litmus;
import org.polypheny.db.util.Static;
import org.polypheny.db.util.Util;


/**
 * Represents a SQL data type specification in a parse tree.
 *
 * A <code>SqlDataTypeSpec</code> is immutable; once created, you cannot change any of the fields.
 *
 * todo: This should really be a subtype of {@link SqlCall}.
 *
 * In its full glory, we will have to support complex type expressions like:
 *
 * <blockquote><code>ROW(<br>
 * NUMBER(5, 2) NOT NULL AS foo,<br>
 * ROW(BOOLEAN AS b, MyUDT NOT NULL AS i) AS rec)</code></blockquote>
 *
 * Currently it only supports simple datatypes like CHAR, VARCHAR and DOUBLE, with optional precision and scale.
 */
public class SqlDataTypeSpec extends SqlNode {

    private final SqlIdentifier collectionsTypeName;
    private final SqlIdentifier typeName;
    private final SqlIdentifier baseTypeName;
    private final int scale;
    private final int precision;
    private final int dimension;
    private final int cardinality;
    private final String charSetName;
    private final TimeZone timeZone;

    /**
     * Whether data type is allows nulls.
     *
     * Nullable is nullable! Null means "not specified". E.g. {@code CAST(x AS INTEGER)} preserves has the same nullability as {@code x}.
     */
    private Boolean nullable;


    /**
     * Creates a type specification representing a regular, non-collection type.
     */
    public SqlDataTypeSpec(
            final SqlIdentifier typeName,
            int precision,
            int scale,
            String charSetName,
            TimeZone timeZone,
            SqlParserPos pos ) {
        this( null, typeName, precision, scale, -1, -1, charSetName, timeZone, null, pos );
    }


    /**
     * Creates a type specification representing a collection type.
     */
    public SqlDataTypeSpec(
            SqlIdentifier collectionsTypeName,
            SqlIdentifier typeName,
            int precision,
            int scale,
            int dimension,
            int cardinality,
            String charSetName,
            SqlParserPos pos ) {
        this( collectionsTypeName, typeName, precision, scale, dimension, cardinality, charSetName, null, null, pos );
    }


    /**
     * Creates a type specification that has no base type.
     */
    public SqlDataTypeSpec(
            SqlIdentifier collectionsTypeName,
            SqlIdentifier typeName,
            int precision,
            int scale,
            int dimension,
            int cardinality,
            String charSetName,
            TimeZone timeZone,
            Boolean nullable,
            SqlParserPos pos ) {
        this( collectionsTypeName, typeName, typeName, precision, scale, dimension, cardinality, charSetName, timeZone, nullable, pos );
    }


    /**
     * Creates a type specification.
     */
    public SqlDataTypeSpec(
            SqlIdentifier collectionsTypeName,
            SqlIdentifier typeName,
            SqlIdentifier baseTypeName,
            int precision,
            int scale,
            int dimension,
            int cardinality,
            String charSetName,
            TimeZone timeZone,
            Boolean nullable,
            SqlParserPos pos ) {
        super( pos );
        this.collectionsTypeName = collectionsTypeName;
        this.typeName = typeName;
        this.baseTypeName = baseTypeName;
        this.precision = precision;
        this.scale = scale;
        this.dimension = dimension;
        this.cardinality = cardinality;
        this.charSetName = charSetName;
        this.timeZone = timeZone;
        this.nullable = nullable;
    }


    @Override
    public SqlNode clone( SqlParserPos pos ) {
        return (collectionsTypeName != null)
                ? new SqlDataTypeSpec( collectionsTypeName, typeName, precision, scale, dimension, cardinality, charSetName, pos )
                : new SqlDataTypeSpec( typeName, precision, scale, charSetName, timeZone, pos );
    }


    @Override
    public SqlMonotonicity getMonotonicity( SqlValidatorScope scope ) {
        return SqlMonotonicity.CONSTANT;
    }


    public SqlIdentifier getCollectionsTypeName() {
        return collectionsTypeName;
    }


    /**
     * Parses the collection type to a PolyType; can be null
     *
     * @return the parsed collection
     */
    public PolyType getCollectionsType() {
        return collectionsTypeName == null ? null : PolyType.get( collectionsTypeName.getSimple() );
    }


    public SqlIdentifier getTypeName() {
        return typeName;
    }


    /**
     * Parses the type to a PolyType; can be null
     *
     * @return the parsed type
     */
    public PolyType getType() {
        return typeName == null ? null : PolyType.get( typeName.getSimple() );
    }


    public int getScale() {
        return scale;
    }


    public int getPrecision() {
        return precision;
    }


    public int getDimension() {
        return dimension;
    }


    public int getCardinality() {
        return cardinality;
    }


    public String getCharSetName() {
        return charSetName;
    }


    public TimeZone getTimeZone() {
        return timeZone;
    }


    public Boolean getNullable() {
        return nullable;
    }


    /**
     * Returns a copy of this data type specification with a given nullability.
     */
    public SqlDataTypeSpec withNullable( Boolean nullable ) {
        if ( Objects.equals( nullable, this.nullable ) ) {
            return this;
        }
        return new SqlDataTypeSpec( collectionsTypeName, typeName, precision, scale, dimension, cardinality, charSetName, timeZone, nullable, getParserPosition() );
    }


    /**
     * Returns a new SqlDataTypeSpec corresponding to the component type if the type spec is a collections type spec.<br>
     * Collection types are <code>ARRAY</code> and <code>MULTISET</code>.
     */
    public SqlDataTypeSpec getComponentTypeSpec() {
        assert getCollectionsTypeName() != null;
        return new SqlDataTypeSpec(
                typeName,
                precision,
                scale,
                charSetName,
                timeZone,
                getParserPosition() );
    }


    @Override
    public void unparse( SqlWriter writer, int leftPrec, int rightPrec ) {
        String name = typeName.getSimple();
        if ( PolyType.get( name ) != null ) {
            PolyType polyType = PolyType.get( name );

            //e.g. for CAST call, for stores that don't support ARRAYs. This is a fix for the WebUI filtering (see webui.Crud.filterTable)
            if ( !writer.getDialect().supportsNestedArrays() && polyType == PolyType.ARRAY ) {
                polyType = PolyType.VARCHAR;
                name = polyType.getName();
                if ( precision < 0 ) {
                    name = name + "(8000)";
                }
            }

            // we have a built-in data type
            writer.keyword( name );

            if ( polyType.allowsPrec() && (precision >= 0) ) {
                final SqlWriter.Frame frame = writer.startList( SqlWriter.FrameTypeEnum.FUN_CALL, "(", ")" );
                writer.print( precision );
                if ( polyType.allowsScale() && (scale >= 0) ) {
                    writer.sep( ",", true );
                    writer.print( scale );
                }
                writer.endList( frame );
            }

            if ( charSetName != null ) {
                writer.keyword( "CHARACTER SET" );
                writer.identifier( charSetName );
            }

            if ( collectionsTypeName != null ) {
                writer.keyword( collectionsTypeName.getSimple() );
            }
        } else if ( name.startsWith( "_" ) ) {
            // We're generating a type for an alien system. For example, UNSIGNED is a built-in type in MySQL.
            // (Need a more elegant way than '_' of flagging this.)
            writer.keyword( name.substring( 1 ) );
        } else {
            // else we have a user defined type
            typeName.unparse( writer, leftPrec, rightPrec );
        }
    }


    @Override
    public void validate( SqlValidator validator, SqlValidatorScope scope ) {
        validator.validateDataType( this );
    }


    @Override
    public <R> R accept( SqlVisitor<R> visitor ) {
        return visitor.visit( this );
    }


    @Override
    public boolean equalsDeep( SqlNode node, Litmus litmus ) {
        if ( !(node instanceof SqlDataTypeSpec) ) {
            return litmus.fail( "{} != {}", this, node );
        }
        SqlDataTypeSpec that = (SqlDataTypeSpec) node;
        if ( !SqlNode.equalDeep( this.collectionsTypeName, that.collectionsTypeName, litmus ) ) {
            return litmus.fail( null );
        }
        if ( !this.typeName.equalsDeep( that.typeName, litmus ) ) {
            return litmus.fail( null );
        }
        if ( this.precision != that.precision ) {
            return litmus.fail( "{} != {}", this, node );
        }
        if ( this.scale != that.scale ) {
            return litmus.fail( "{} != {}", this, node );
        }
        if ( !Objects.equals( this.timeZone, that.timeZone ) ) {
            return litmus.fail( "{} != {}", this, node );
        }
        if ( !Objects.equals( this.charSetName, that.charSetName ) ) {
            return litmus.fail( "{} != {}", this, node );
        }
        return litmus.succeed();
    }


    /**
     * Throws an error if the type is not found.
     */
    public RelDataType deriveType( SqlValidator validator ) {
        RelDataType type = null;
        if ( typeName.isSimple() ) {
            if ( null != collectionsTypeName ) {
                final String collectionName = collectionsTypeName.getSimple();
                if ( PolyType.get( collectionName ) == null ) {
                    throw validator.newValidationError( this, Static.RESOURCE.unknownDatatypeName( collectionName ) );
                }
            }

            RelDataTypeFactory typeFactory = validator.getTypeFactory();
            type = deriveType( typeFactory );
        }
        if ( type == null ) {
            type = validator.getValidatedNodeType( typeName );
        }
        return type;
    }


    /**
     * Does not throw an error if the type is not built-in.
     */
    public RelDataType deriveType( RelDataTypeFactory typeFactory ) {
        return deriveType( typeFactory, false );
    }


    /**
     * Converts this type specification to a {@link RelDataType}.
     *
     * Does not throw an error if the type is not built-in.
     *
     * @param nullable Whether the type is nullable if the type specification does not explicitly state
     */
    public RelDataType deriveType( RelDataTypeFactory typeFactory, boolean nullable ) {
        if ( !typeName.isSimple() ) {
            return null;
        }
        final String name = typeName.getSimple();
        final PolyType polyType = PolyType.get( name );
        if ( polyType == null ) {
            return null;
        }

        // NOTE jvs 15-Jan-2009:  earlier validation is supposed to have caught these, which is why it's OK for them to be assertions rather than user-level exceptions.
        RelDataType type;
        if ( (precision >= 0) && (scale >= 0) ) {
            assert polyType.allowsPrecScale( true, true );
            type = typeFactory.createPolyType( polyType, precision, scale );
        } else if ( precision >= 0 ) {
            assert polyType.allowsPrecNoScale();
            type = typeFactory.createPolyType( polyType, precision );
        } else {
            assert polyType.allowsNoPrecNoScale();
            type = typeFactory.createPolyType( polyType );
        }

        if ( PolyTypeUtil.inCharFamily( type ) ) {
            // Applying Syntax rule 10 from SQL:99 spec section 6.22 "If TD is a fixed-length, variable-length or large object character string,
            // then the collating sequence of the result of the <cast specification> is the default collating sequence for the
            // character repertoire of TD and the result of the <cast specification> has the Coercible coercibility characteristic."
            SqlCollation collation = SqlCollation.COERCIBLE;

            Charset charset;
            if ( null == charSetName ) {
                charset = typeFactory.getDefaultCharset();
            } else {
                String javaCharSetName = Objects.requireNonNull( SqlUtil.translateCharacterSetName( charSetName ), charSetName );
                charset = Charset.forName( javaCharSetName );
            }
            type = typeFactory.createTypeWithCharsetAndCollation( type, charset, collation );
        }

        if ( null != collectionsTypeName ) {
            final String collectionName = collectionsTypeName.getSimple();
            final PolyType collectionsPolyType = Objects.requireNonNull( PolyType.get( collectionName ), collectionName );

            switch ( collectionsPolyType ) {
                case MULTISET:
                    type = typeFactory.createMultisetType( type, cardinality );
                    break;
                case ARRAY:
                    type = typeFactory.createArrayType( type, cardinality, dimension );
                    break;

                default:
                    throw Util.unexpected( collectionsPolyType );
            }
        }

        if ( this.nullable != null ) {
            nullable = this.nullable;
        }
        type = typeFactory.createTypeWithNullability( type, nullable );

        return type;
    }
}
