package ch.ehi.ili2pg;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.ili2db.base.AbstractJdbcMapping;
import ch.ehi.ili2db.fromili.CustomMapping;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.sqlgen.repository.DbColumn;
import ch.ehi.sqlgen.repository.DbTable;
import ch.ehi.sqlgen.repository.DbTableName;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.Viewable;

public class PgCustomStrategy extends AbstractJdbcMapping {

	/**
	 * Command line options take precedence, then the libpq environment variables
	 * PGUSER and PGPASSWORD (not read by the JDBC driver itself).
	 * If still no user/password is set, the PostgreSQL JDBC driver falls back to
	 * the OS user resp. to the password file (.pgpass, PGPASSFILE).
	 */
	@Override
	public Connection connect(String url, String dbusr, String dbpwd,
			Config config) throws SQLException {
		java.util.Properties params=new java.util.Properties();
		if(config.getDbProperties()!=null){
			params.putAll(config.getDbProperties());
		}
		// precedence: --dbusr/--dbpwd, then --dbparams file, then environment
		setProperty(params, "user", dbusr, "PGUSER");
		setProperty(params, "password", dbpwd, "PGPASSWORD");
		return DriverManager.getConnection(url, params);
	}
	private static void setProperty(java.util.Properties params, String key, String cmdlineValue, String envVar) {
		if(cmdlineValue!=null && !cmdlineValue.isEmpty()){
			params.setProperty(key, cmdlineValue);
		}else if(!params.containsKey(key)){
			String envValue=System.getenv(envVar);
			if(envValue!=null && !envValue.isEmpty()){
				params.setProperty(key, envValue);
			}
		}
	}

	@Override
	public void fromIliInit(Config config) {
	}

	@Override
	public void fromIliEnd(Config config) {
	}

	@Override
	public void fixupViewable(DbTable sqlTableDef, Viewable iliClassDef) {
	}

	@Override
	public void fixupAttribute(DbTable sqlTableDef, DbColumn sqlColDef,
			AttributeDef iliAttrDef) {
	}

	@Override
	public void fixupEmbeddedLink(DbTable dbTable, DbColumn dbColId,
			AssociationDef roleOwner, RoleDef role, DbTableName targetTable,
			String targetPk) {
	}

	private final static String[] pgExtensions = new String[]{
			"postgis", "\"uuid-ossp\""
	};

	@Override
	public void postConnect(Connection conn, Config config) {
		// setupPgExt
		if(config.isSetupPgExt()){
			java.sql.Statement stmt=null;
			try {
				stmt=conn.createStatement();
				String sql=null;
				for(String pgExtension : pgExtensions){
					try {
						sql="CREATE EXTENSION IF NOT EXISTS "+pgExtension+";";
						EhiLogger.traceBackendCmd(sql);
						stmt.execute(sql);
					} catch(SQLException e){
						throw new IllegalStateException("failed to create extension "+pgExtension, e);
					}
				}
			} catch (SQLException e) {
				EhiLogger.logError(e);
			}finally{
				if(stmt!=null){
					try {
						stmt.close();
						stmt=null;
					} catch (SQLException e) {
						EhiLogger.logError(e);
					}
				}
			}
		}
	}
}
