/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.planner.operations;

import org.apache.flink.sql.parser.ddl.SqlAlterDatabase;
import org.apache.flink.sql.parser.ddl.SqlAlterFunction;
import org.apache.flink.sql.parser.ddl.SqlAlterTable;
import org.apache.flink.sql.parser.ddl.SqlAlterTableAddConstraint;
import org.apache.flink.sql.parser.ddl.SqlAlterTableDropConstraint;
import org.apache.flink.sql.parser.ddl.SqlAlterTableProperties;
import org.apache.flink.sql.parser.ddl.SqlAlterTableRename;
import org.apache.flink.sql.parser.ddl.SqlCreateCatalog;
import org.apache.flink.sql.parser.ddl.SqlCreateDatabase;
import org.apache.flink.sql.parser.ddl.SqlCreateFunction;
import org.apache.flink.sql.parser.ddl.SqlCreateTable;
import org.apache.flink.sql.parser.ddl.SqlCreateView;
import org.apache.flink.sql.parser.ddl.SqlDropDatabase;
import org.apache.flink.sql.parser.ddl.SqlDropFunction;
import org.apache.flink.sql.parser.ddl.SqlDropTable;
import org.apache.flink.sql.parser.ddl.SqlDropView;
import org.apache.flink.sql.parser.ddl.SqlTableOption;
import org.apache.flink.sql.parser.ddl.SqlUseCatalog;
import org.apache.flink.sql.parser.ddl.SqlUseDatabase;
import org.apache.flink.sql.parser.ddl.constraint.SqlTableConstraint;
import org.apache.flink.sql.parser.dml.RichSqlInsert;
import org.apache.flink.sql.parser.dql.SqlRichDescribeTable;
import org.apache.flink.sql.parser.dql.SqlShowCatalogs;
import org.apache.flink.sql.parser.dql.SqlShowDatabases;
import org.apache.flink.sql.parser.dql.SqlShowFunctions;
import org.apache.flink.sql.parser.dql.SqlShowTables;
import org.apache.flink.sql.parser.dql.SqlShowViews;
import org.apache.flink.table.api.TableException;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.api.ValidationException;
import org.apache.flink.table.catalog.Catalog;
import org.apache.flink.table.catalog.CatalogDatabase;
import org.apache.flink.table.catalog.CatalogDatabaseImpl;
import org.apache.flink.table.catalog.CatalogFunction;
import org.apache.flink.table.catalog.CatalogFunctionImpl;
import org.apache.flink.table.catalog.CatalogManager;
import org.apache.flink.table.catalog.CatalogTable;
import org.apache.flink.table.catalog.CatalogTableImpl;
import org.apache.flink.table.catalog.CatalogView;
import org.apache.flink.table.catalog.CatalogViewImpl;
import org.apache.flink.table.catalog.FunctionLanguage;
import org.apache.flink.table.catalog.ObjectIdentifier;
import org.apache.flink.table.catalog.UnresolvedIdentifier;
import org.apache.flink.table.catalog.exceptions.DatabaseNotExistException;
import org.apache.flink.table.factories.CatalogFactory;
import org.apache.flink.table.factories.TableFactoryService;
import org.apache.flink.table.operations.CatalogSinkModifyOperation;
import org.apache.flink.table.operations.DescribeTableOperation;
import org.apache.flink.table.operations.ExplainOperation;
import org.apache.flink.table.operations.Operation;
import org.apache.flink.table.operations.ShowCatalogsOperation;
import org.apache.flink.table.operations.ShowDatabasesOperation;
import org.apache.flink.table.operations.ShowFunctionsOperation;
import org.apache.flink.table.operations.ShowTablesOperation;
import org.apache.flink.table.operations.ShowViewsOperation;
import org.apache.flink.table.operations.UseCatalogOperation;
import org.apache.flink.table.operations.UseDatabaseOperation;
import org.apache.flink.table.operations.ddl.AlterCatalogFunctionOperation;
import org.apache.flink.table.operations.ddl.AlterDatabaseOperation;
import org.apache.flink.table.operations.ddl.AlterTableAddConstraintOperation;
import org.apache.flink.table.operations.ddl.AlterTableDropConstraintOperation;
import org.apache.flink.table.operations.ddl.AlterTablePropertiesOperation;
import org.apache.flink.table.operations.ddl.AlterTableRenameOperation;
import org.apache.flink.table.operations.ddl.CreateCatalogFunctionOperation;
import org.apache.flink.table.operations.ddl.CreateCatalogOperation;
import org.apache.flink.table.operations.ddl.CreateDatabaseOperation;
import org.apache.flink.table.operations.ddl.CreateTempSystemFunctionOperation;
import org.apache.flink.table.operations.ddl.CreateViewOperation;
import org.apache.flink.table.operations.ddl.DropCatalogFunctionOperation;
import org.apache.flink.table.operations.ddl.DropDatabaseOperation;
import org.apache.flink.table.operations.ddl.DropTableOperation;
import org.apache.flink.table.operations.ddl.DropTempSystemFunctionOperation;
import org.apache.flink.table.operations.ddl.DropViewOperation;
import org.apache.flink.table.planner.calcite.FlinkPlannerImpl;
import org.apache.flink.table.planner.hint.FlinkHints;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.utils.TableSchemaUtils;
import org.apache.flink.util.StringUtils;

import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.hint.HintStrategyTable;
import org.apache.calcite.rel.hint.RelHint;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlExplain;
import org.apache.calcite.sql.SqlExplainFormat;
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlUtil;
import org.apache.calcite.sql.dialect.CalciteSqlDialect;
import org.apache.calcite.sql.parser.SqlParser;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mix-in tool class for {@code SqlNode} that allows DDL commands to be
 * converted to {@link Operation}.
 *
 * <p>For every kind of {@link SqlNode}, there needs to have a corresponding
 * #convert(type) method, the 'type' argument should be the subclass
 * of the supported {@link SqlNode}.
 *
 * <p>Every #convert() should return a {@link Operation} which can be used in
 * {@link org.apache.flink.table.delegation.Planner}.
 */
public class SqlToOperationConverter {
	private final FlinkPlannerImpl flinkPlanner;
	private final CatalogManager catalogManager;
	private final SqlCreateTableConverter createTableConverter;

	//~ Constructors -----------------------------------------------------------

	private SqlToOperationConverter(
			FlinkPlannerImpl flinkPlanner,
			CatalogManager catalogManager) {
		this.flinkPlanner = flinkPlanner;
		this.catalogManager = catalogManager;
		this.createTableConverter = new SqlCreateTableConverter(
			flinkPlanner.getOrCreateSqlValidator(),
			catalogManager,
			this::getQuotedSqlString,
			this::validateTableConstraint);
	}

	/**
	 * This is the main entrance for executing all kinds of DDL/DML {@code SqlNode}s, different
	 * SqlNode will have it's implementation in the #convert(type) method whose 'type' argument
	 * is subclass of {@code SqlNode}.
	 *
	 * @param flinkPlanner FlinkPlannerImpl to convertCreateTable sql node to rel node
	 * @param catalogManager CatalogManager to resolve full path for operations
	 * @param sqlNode SqlNode to execute on
	 */
	public static Optional<Operation> convert(
			FlinkPlannerImpl flinkPlanner,
			CatalogManager catalogManager,
			SqlNode sqlNode) {
		// validate the query
		final SqlNode validated = flinkPlanner.validate(sqlNode);
		SqlToOperationConverter converter = new SqlToOperationConverter(flinkPlanner, catalogManager);
		if (validated instanceof SqlCreateTable) {
			return Optional.of(converter.createTableConverter.convertCreateTable((SqlCreateTable) validated));
		} else if (validated instanceof SqlDropTable) {
			return Optional.of(converter.convertDropTable((SqlDropTable) validated));
		} else if (validated instanceof SqlAlterTable) {
			return Optional.of(converter.convertAlterTable((SqlAlterTable) validated));
		} else if (validated instanceof SqlCreateFunction) {
			return Optional.of(converter.convertCreateFunction((SqlCreateFunction) validated));
		} else if (validated instanceof SqlAlterFunction) {
			return Optional.of(converter.convertAlterFunction((SqlAlterFunction) validated));
		} else if (validated instanceof SqlDropFunction) {
			return Optional.of(converter.convertDropFunction((SqlDropFunction) validated));
		} else if (validated instanceof RichSqlInsert) {
			return Optional.of(converter.convertSqlInsert((RichSqlInsert) validated));
		} else if (validated instanceof SqlUseCatalog) {
			return Optional.of(converter.convertUseCatalog((SqlUseCatalog) validated));
		} else if (validated instanceof SqlUseDatabase) {
			return Optional.of(converter.convertUseDatabase((SqlUseDatabase) validated));
		} else if (validated instanceof SqlCreateDatabase) {
			return Optional.of(converter.convertCreateDatabase((SqlCreateDatabase) validated));
		} else if (validated instanceof SqlDropDatabase) {
			return Optional.of(converter.convertDropDatabase((SqlDropDatabase) validated));
		} else if (validated instanceof SqlAlterDatabase) {
			return Optional.of(converter.convertAlterDatabase((SqlAlterDatabase) validated));
		} else if (validated instanceof SqlCreateCatalog) {
			return Optional.of(converter.convertCreateCatalog((SqlCreateCatalog) validated));
		} else if (validated instanceof SqlShowCatalogs) {
			return Optional.of(converter.convertShowCatalogs((SqlShowCatalogs) validated));
		} else if (validated instanceof SqlShowDatabases) {
			return Optional.of(converter.convertShowDatabases((SqlShowDatabases) validated));
		} else if (validated instanceof SqlShowTables) {
			return Optional.of(converter.convertShowTables((SqlShowTables) validated));
		} else if (validated instanceof SqlShowFunctions) {
			return Optional.of(converter.convertShowFunctions((SqlShowFunctions) validated));
		} else if (validated instanceof SqlCreateView) {
			return Optional.of(converter.convertCreateView((SqlCreateView) validated));
		} else if (validated instanceof SqlDropView) {
			return Optional.of(converter.convertDropView((SqlDropView) validated));
		} else if (validated instanceof SqlShowViews) {
			return Optional.of(converter.convertShowViews((SqlShowViews) validated));
		} else if (validated instanceof SqlExplain) {
			return Optional.of(converter.convertExplain((SqlExplain) validated));
		} else if (validated instanceof SqlRichDescribeTable) {
			return Optional.of(converter.convertDescribeTable((SqlRichDescribeTable) validated));
		} else if (validated.getKind().belongsTo(SqlKind.QUERY)) {
			return Optional.of(converter.convertSqlQuery(validated));
		} else {
			return Optional.empty();
		}
	}

	//~ Tools ------------------------------------------------------------------


	/** Convert DROP TABLE statement. */
	private Operation convertDropTable(SqlDropTable sqlDropTable) {
		UnresolvedIdentifier unresolvedIdentifier = UnresolvedIdentifier.of(sqlDropTable.fullTableName());
		ObjectIdentifier identifier = catalogManager.qualifyIdentifier(unresolvedIdentifier);

		return new DropTableOperation(identifier, sqlDropTable.getIfExists(), sqlDropTable.isTemporary());
	}

	/** convert ALTER TABLE statement. */
	private Operation convertAlterTable(SqlAlterTable sqlAlterTable) {
		UnresolvedIdentifier unresolvedIdentifier = UnresolvedIdentifier.of(sqlAlterTable.fullTableName());
		ObjectIdentifier tableIdentifier = catalogManager.qualifyIdentifier(unresolvedIdentifier);
		if (sqlAlterTable instanceof SqlAlterTableRename) {
			UnresolvedIdentifier newUnresolvedIdentifier =
				UnresolvedIdentifier.of(((SqlAlterTableRename) sqlAlterTable).fullNewTableName());
			ObjectIdentifier newTableIdentifier = catalogManager.qualifyIdentifier(newUnresolvedIdentifier);
			return new AlterTableRenameOperation(tableIdentifier, newTableIdentifier);
		} else if (sqlAlterTable instanceof SqlAlterTableProperties) {
			Optional<CatalogManager.TableLookupResult> optionalCatalogTable = catalogManager.getTable(tableIdentifier);
			if (optionalCatalogTable.isPresent() && !optionalCatalogTable.get().isTemporary()) {
				CatalogTable originalCatalogTable = (CatalogTable) optionalCatalogTable.get().getTable();
				Map<String, String> properties = new HashMap<>(originalCatalogTable.getOptions());
				((SqlAlterTableProperties) sqlAlterTable).getPropertyList().getList().forEach(p ->
					properties.put(((SqlTableOption) p).getKeyString(), ((SqlTableOption) p).getValueString()));
				CatalogTable catalogTable = new CatalogTableImpl(
					originalCatalogTable.getSchema(),
					originalCatalogTable.getPartitionKeys(),
					properties,
					originalCatalogTable.getComment());
				return new AlterTablePropertiesOperation(tableIdentifier, catalogTable);
			} else {
				throw new ValidationException(String.format("Table %s doesn't exist or is a temporary table.",
					tableIdentifier.toString()));
			}
		} else if (sqlAlterTable instanceof SqlAlterTableAddConstraint) {
			Optional<CatalogManager.TableLookupResult> optionalCatalogTable =
					catalogManager.getTable(tableIdentifier);
			if (optionalCatalogTable.isPresent() && !optionalCatalogTable.get().isTemporary()) {
				SqlTableConstraint constraint = ((SqlAlterTableAddConstraint) sqlAlterTable)
						.getConstraint();
				validateTableConstraint(constraint);
				TableSchema oriSchema = optionalCatalogTable.get().getTable().getSchema();
				// Sanity check for constraint.
				TableSchema.Builder builder = TableSchemaUtils.builderWithGivenSchema(oriSchema);
				if (constraint.getConstraintName().isPresent()) {
					builder.primaryKey(
							constraint.getConstraintName().get(),
							constraint.getColumnNames());
				} else {
					builder.primaryKey(constraint.getColumnNames());
				}
				builder.build();
				return new AlterTableAddConstraintOperation(
						tableIdentifier,
						constraint.getConstraintName().orElse(null),
						constraint.getColumnNames());
			} else {
				throw new ValidationException(String.format("Table %s doesn't exist or is a temporary table.",
						tableIdentifier.toString()));
			}
		} else if (sqlAlterTable instanceof SqlAlterTableDropConstraint) {
			Optional<CatalogManager.TableLookupResult> optionalCatalogTable =
					catalogManager.getTable(tableIdentifier);
			if (optionalCatalogTable.isPresent() && !optionalCatalogTable.get().isTemporary()) {
				SqlAlterTableDropConstraint dropConstraint = ((SqlAlterTableDropConstraint) sqlAlterTable);
				String constraintName = dropConstraint.getConstraintName().getSimple();
				CatalogTable oriCatalogTable = (CatalogTable) optionalCatalogTable.get().getTable();
				TableSchema oriSchema = oriCatalogTable.getSchema();
				if (!oriSchema.getPrimaryKey()
						.filter(pk -> pk.getName().equals(constraintName))
						.isPresent()) {
					throw new ValidationException(
							String.format("CONSTRAINT [%s] does not exist", constraintName));
				}
				return new AlterTableDropConstraintOperation(
						tableIdentifier,
						constraintName);
			} else {
				throw new ValidationException(String.format("Table %s doesn't exist or is a temporary table.",
						tableIdentifier.toString()));
			}
		} else {
			throw new ValidationException(
					String.format("[%s] needs to implement",
							sqlAlterTable.toSqlString(CalciteSqlDialect.DEFAULT)));
		}
	}

	/** Convert CREATE FUNCTION statement. */
	private Operation convertCreateFunction(SqlCreateFunction sqlCreateFunction) {
		UnresolvedIdentifier unresolvedIdentifier =
			UnresolvedIdentifier.of(sqlCreateFunction.getFunctionIdentifier());

		if (sqlCreateFunction.isSystemFunction()) {
			return new CreateTempSystemFunctionOperation(
				unresolvedIdentifier.getObjectName(),
				sqlCreateFunction.getFunctionClassName().getValueAs(String.class),
				sqlCreateFunction.isIfNotExists(),
				parseLanguage(sqlCreateFunction.getFunctionLanguage())
			);
		} else {
			FunctionLanguage language = parseLanguage(sqlCreateFunction.getFunctionLanguage());
			CatalogFunction catalogFunction = new CatalogFunctionImpl(
				sqlCreateFunction.getFunctionClassName().getValueAs(String.class),
				language);
			ObjectIdentifier identifier = catalogManager.qualifyIdentifier(unresolvedIdentifier);

			return new CreateCatalogFunctionOperation(
				identifier,
				catalogFunction,
				sqlCreateFunction.isIfNotExists(),
				sqlCreateFunction.isTemporary()
			);
		}
	}

	/** Convert ALTER FUNCTION statement. */
	private Operation convertAlterFunction(SqlAlterFunction sqlAlterFunction) {
		if (sqlAlterFunction.isSystemFunction()) {
			throw new ValidationException("Alter temporary system function is not supported");
		}

		FunctionLanguage language = parseLanguage(sqlAlterFunction.getFunctionLanguage());
		CatalogFunction catalogFunction = new CatalogFunctionImpl(
			sqlAlterFunction.getFunctionClassName().getValueAs(String.class),
			language);

		UnresolvedIdentifier unresolvedIdentifier = UnresolvedIdentifier.of(sqlAlterFunction.getFunctionIdentifier());
		ObjectIdentifier identifier = catalogManager.qualifyIdentifier(unresolvedIdentifier);
		return new AlterCatalogFunctionOperation(
			identifier,
			catalogFunction,
			sqlAlterFunction.isIfExists(),
			sqlAlterFunction.isTemporary()
		);
	}

	/** Convert DROP FUNCTION statement. */
	private Operation convertDropFunction(SqlDropFunction sqlDropFunction) {
		UnresolvedIdentifier unresolvedIdentifier = UnresolvedIdentifier.of(sqlDropFunction.getFunctionIdentifier());
		if (sqlDropFunction.isSystemFunction()) {
			return new DropTempSystemFunctionOperation(
				unresolvedIdentifier.getObjectName(),
				sqlDropFunction.getIfExists()
			);
		} else {
			ObjectIdentifier identifier = catalogManager.qualifyIdentifier(unresolvedIdentifier);

			return new DropCatalogFunctionOperation(
				identifier,
				sqlDropFunction.getIfExists(),
				sqlDropFunction.isTemporary()
			);
		}
	}

	/**
	 * Converts language string to the FunctionLanguage.
	 *
	 * @param languageString  the language string from SQL parser
	 * @return supported FunctionLanguage otherwise raise UnsupportedOperationException.
	 * @throws UnsupportedOperationException if the languageString is not parsable or language is not supported
	 */
	private FunctionLanguage parseLanguage(String languageString) {
		if (StringUtils.isNullOrWhitespaceOnly(languageString)) {
			return FunctionLanguage.JAVA;
		}

		FunctionLanguage language;
		try {
			language = FunctionLanguage.valueOf(languageString);
		} catch (IllegalArgumentException e) {
			throw new UnsupportedOperationException(
				String.format("Unrecognized function language string %s", languageString), e);
		}

		return language;
	}

	/** Convert insert into statement. */
	private Operation convertSqlInsert(RichSqlInsert insert) {
		// Get sink table name.
		List<String> targetTablePath = ((SqlIdentifier) insert.getTargetTableID()).names;
		// Get sink table hints.
		HintStrategyTable hintStrategyTable = flinkPlanner.config()
				.getSqlToRelConverterConfig()
				.getHintStrategyTable();
		List<RelHint> tableHints = SqlUtil.getRelHint(hintStrategyTable, insert.getTableHints());
		Map<String, String> dynamicOptions = FlinkHints.getHintedOptions(tableHints);

		UnresolvedIdentifier unresolvedIdentifier = UnresolvedIdentifier.of(targetTablePath);
		ObjectIdentifier identifier = catalogManager.qualifyIdentifier(unresolvedIdentifier);

		PlannerQueryOperation query = (PlannerQueryOperation) SqlToOperationConverter.convert(
			flinkPlanner,
			catalogManager,
			insert.getSource())
			.orElseThrow(() -> new TableException(
				"Unsupported node type " + insert.getSource().getClass().getSimpleName()));

		return new CatalogSinkModifyOperation(
			identifier,
			query,
			insert.getStaticPartitionKVs(),
			insert.isOverwrite(),
			dynamicOptions);
	}

	/** Convert use catalog statement. */
	private Operation convertUseCatalog(SqlUseCatalog useCatalog) {
		return new UseCatalogOperation(useCatalog.getCatalogName());
	}

	/** Convert CREATE CATALOG statement. */
	private Operation convertCreateCatalog(SqlCreateCatalog sqlCreateCatalog) {
		String catalogName = sqlCreateCatalog.catalogName();

		// set with properties
		Map<String, String> properties = new HashMap<>();
		sqlCreateCatalog.getPropertyList().getList().forEach(p ->
			properties.put(((SqlTableOption) p).getKeyString(), ((SqlTableOption) p).getValueString()));

		final CatalogFactory factory =
			TableFactoryService.find(CatalogFactory.class, properties, this.getClass().getClassLoader());

		Catalog catalog = factory.createCatalog(catalogName, properties);
		return new CreateCatalogOperation(catalogName, catalog);
	}

	/** Convert use database statement. */
	private Operation convertUseDatabase(SqlUseDatabase useDatabase) {
		String[] fullDatabaseName = useDatabase.fullDatabaseName();
		if (fullDatabaseName.length > 2) {
			throw new SqlConversionException("use database identifier format error");
		}
		String catalogName = fullDatabaseName.length == 2 ? fullDatabaseName[0] : catalogManager.getCurrentCatalog();
		String databaseName = fullDatabaseName.length == 2 ? fullDatabaseName[1] : fullDatabaseName[0];
		return new UseDatabaseOperation(catalogName, databaseName);
	}

	/** Convert CREATE DATABASE statement. */
	private Operation convertCreateDatabase(SqlCreateDatabase sqlCreateDatabase) {
		String[] fullDatabaseName = sqlCreateDatabase.fullDatabaseName();
		if (fullDatabaseName.length > 2) {
			throw new SqlConversionException("create database identifier format error");
		}
		String catalogName = (fullDatabaseName.length == 1) ? catalogManager.getCurrentCatalog() : fullDatabaseName[0];
		String databaseName = (fullDatabaseName.length == 1) ? fullDatabaseName[0] : fullDatabaseName[1];
		boolean ignoreIfExists = sqlCreateDatabase.isIfNotExists();
		String databaseComment = sqlCreateDatabase.getComment()
			.map(comment -> comment.getNlsString().getValue()).orElse(null);
		// set with properties
		Map<String, String> properties = new HashMap<>();
		sqlCreateDatabase.getPropertyList().getList().forEach(p ->
			properties.put(((SqlTableOption) p).getKeyString(), ((SqlTableOption) p).getValueString()));
		CatalogDatabase catalogDatabase = new CatalogDatabaseImpl(properties, databaseComment);
		return new CreateDatabaseOperation(catalogName, databaseName, catalogDatabase, ignoreIfExists);
	}

	/** Convert DROP DATABASE statement. */
	private Operation convertDropDatabase(SqlDropDatabase sqlDropDatabase) {
		String[] fullDatabaseName = sqlDropDatabase.fullDatabaseName();
		if (fullDatabaseName.length > 2) {
			throw new SqlConversionException("drop database identifier format error");
		}
		String catalogName = (fullDatabaseName.length == 1) ? catalogManager.getCurrentCatalog() : fullDatabaseName[0];
		String databaseName = (fullDatabaseName.length == 1) ? fullDatabaseName[0] : fullDatabaseName[1];
		return new DropDatabaseOperation(
			catalogName,
			databaseName,
			sqlDropDatabase.getIfExists(),
			sqlDropDatabase.isCascade());
	}

	/** Convert ALTER DATABASE statement. */
	private Operation convertAlterDatabase(SqlAlterDatabase sqlAlterDatabase) {
		String[] fullDatabaseName = sqlAlterDatabase.fullDatabaseName();
		if (fullDatabaseName.length > 2) {
			throw new SqlConversionException("alter database identifier format error");
		}
		String catalogName = (fullDatabaseName.length == 1) ? catalogManager.getCurrentCatalog() : fullDatabaseName[0];
		String databaseName = (fullDatabaseName.length == 1) ? fullDatabaseName[0] : fullDatabaseName[1];
		final Map<String, String> properties;
		CatalogDatabase originCatalogDatabase;
		Optional<Catalog> catalog = catalogManager.getCatalog(catalogName);
		if (catalog.isPresent()) {
			try {
				originCatalogDatabase = catalog.get().getDatabase(databaseName);
				properties = new HashMap<>(originCatalogDatabase.getProperties());
			} catch (DatabaseNotExistException e) {
				throw new SqlConversionException(String.format("Database %s not exists", databaseName), e);
			}
		} else {
			throw new SqlConversionException(String.format("Catalog %s not exists", catalogName));
		}
		// set with properties
		sqlAlterDatabase.getPropertyList().getList().forEach(p ->
			properties.put(((SqlTableOption) p).getKeyString(), ((SqlTableOption) p).getValueString()));
		CatalogDatabase catalogDatabase = new CatalogDatabaseImpl(properties, originCatalogDatabase.getComment());
		return new AlterDatabaseOperation(catalogName, databaseName, catalogDatabase);
	}

	/** Convert SHOW CATALOGS statement. */
	private Operation convertShowCatalogs(SqlShowCatalogs sqlShowCatalogs) {
		return new ShowCatalogsOperation();
	}

	/** Convert SHOW DATABASES statement. */
	private Operation convertShowDatabases(SqlShowDatabases sqlShowDatabases) {
		return new ShowDatabasesOperation();
	}

	/** Convert SHOW TABLES statement. */
	private Operation convertShowTables(SqlShowTables sqlShowTables) {
		return new ShowTablesOperation();
	}

	/** Convert SHOW FUNCTIONS statement. */
	private Operation convertShowFunctions(SqlShowFunctions sqlShowFunctions) {
		return new ShowFunctionsOperation();
	}

	/** Convert CREATE VIEW statement. */
	private Operation convertCreateView(SqlCreateView sqlCreateView) {
		final SqlNode query = sqlCreateView.getQuery();
		final SqlNodeList fieldList = sqlCreateView.getFieldList();

		SqlNode validateQuery = flinkPlanner.validate(query);
		PlannerQueryOperation operation = toQueryOperation(flinkPlanner, validateQuery);
		TableSchema schema = operation.getTableSchema();

		// the view column list in CREATE VIEW is optional, if it's not empty, we should update
		// the column name with the names in view column list.
		if (!fieldList.getList().isEmpty()) {
			// alias column names:
			String[] inputFieldNames = schema.getFieldNames();
			String[] aliasFieldNames = fieldList.getList().stream()
					.map(SqlNode::toString)
					.toArray(String[]::new);

			if (inputFieldNames.length != aliasFieldNames.length) {
				throw new SqlConversionException(String.format(
						"VIEW definition and input fields not match:\n\tDef fields: %s.\n\tInput fields: %s.",
						Arrays.toString(aliasFieldNames), Arrays.toString(inputFieldNames)));
			}

			DataType[] inputFieldTypes = schema.getFieldDataTypes();
			schema = TableSchema.builder().fields(aliasFieldNames, inputFieldTypes).build();
		}

		String originalQuery = getQuotedSqlString(query);
		String expandedQuery = getQuotedSqlString(validateQuery);
		String comment = sqlCreateView.getComment().map(c -> c.getNlsString().getValue()).orElse(null);
		CatalogView catalogView = new CatalogViewImpl(originalQuery,
				expandedQuery,
				schema,
				Collections.emptyMap(),
				comment);

		UnresolvedIdentifier unresolvedIdentifier = UnresolvedIdentifier.of(sqlCreateView.fullViewName());
		ObjectIdentifier identifier = catalogManager.qualifyIdentifier(unresolvedIdentifier);

		return new CreateViewOperation(
				identifier,
				catalogView,
				sqlCreateView.isIfNotExists(),
				sqlCreateView.isTemporary());
	}

	/** Convert DROP VIEW statement. */
	private Operation convertDropView(SqlDropView sqlDropView) {
		UnresolvedIdentifier unresolvedIdentifier = UnresolvedIdentifier.of(sqlDropView.fullViewName());
		ObjectIdentifier identifier = catalogManager.qualifyIdentifier(unresolvedIdentifier);

		return new DropViewOperation(identifier, sqlDropView.getIfExists(), sqlDropView.isTemporary());
	}

	/** Convert SHOW VIEWS statement. */
	private Operation convertShowViews(SqlShowViews sqlShowViews) {
		return new ShowViewsOperation();
	}

	/** Convert EXPLAIN statement. */
	private Operation convertExplain(SqlExplain sqlExplain) {
		Operation operation = convertSqlQuery(sqlExplain.getExplicandum());

		if (sqlExplain.getDetailLevel() != SqlExplainLevel.EXPPLAN_ATTRIBUTES ||
				sqlExplain.getDepth() != SqlExplain.Depth.PHYSICAL ||
				sqlExplain.getFormat() != SqlExplainFormat.TEXT) {
			throw new TableException("Only default behavior is supported now, EXPLAIN PLAN FOR xx");
		}

		return new ExplainOperation(operation);
	}

	/** Convert DESCRIBE [EXTENDED] [[catalogName.] dataBasesName].sqlIdentifier. */
	private Operation convertDescribeTable(SqlRichDescribeTable sqlRichDescribeTable) {
		UnresolvedIdentifier unresolvedIdentifier = UnresolvedIdentifier.of(sqlRichDescribeTable.fullTableName());
		ObjectIdentifier identifier = catalogManager.qualifyIdentifier(unresolvedIdentifier);

		return new DescribeTableOperation(identifier, sqlRichDescribeTable.isExtended());
	}

	/** Fallback method for sql query. */
	private Operation convertSqlQuery(SqlNode node) {
		return toQueryOperation(flinkPlanner, node);
	}

	private void validateTableConstraint(SqlTableConstraint constraint) {
		if (constraint.isUnique()) {
			throw new UnsupportedOperationException("UNIQUE constraint is not supported yet");
		}
		if (constraint.isEnforced()) {
			throw new ValidationException("Flink doesn't support ENFORCED mode for "
					+ "PRIMARY KEY constaint. ENFORCED/NOT ENFORCED  controls if the constraint "
					+ "checks are performed on the incoming/outgoing data. "
					+ "Flink does not own the data therefore the only supported mode "
					+ "is the NOT ENFORCED mode");
		}
	}

	private String getQuotedSqlString(SqlNode sqlNode) {
		SqlParser.Config parserConfig = flinkPlanner.config().getParserConfig();
		SqlDialect dialect = new CalciteSqlDialect(SqlDialect.EMPTY_CONTEXT
			.withQuotedCasing(parserConfig.unquotedCasing())
			.withConformance(parserConfig.conformance())
			.withUnquotedCasing(parserConfig.unquotedCasing())
			.withIdentifierQuoteString(parserConfig.quoting().string));
		return sqlNode.toSqlString(dialect).getSql();
	}

	private PlannerQueryOperation toQueryOperation(FlinkPlannerImpl planner, SqlNode validated) {
		// transform to a relational tree
		RelRoot relational = planner.rel(validated);
		return new PlannerQueryOperation(relational.project());
	}
}
