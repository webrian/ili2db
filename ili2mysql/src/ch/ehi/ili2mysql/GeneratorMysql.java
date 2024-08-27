/* This file is part of the uml2db project.
 * For more information, please see <http://www.umleditor.org>.
 *
 * Copyright (c) 2006 Eisenhut Informatik AG
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included 
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL 
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */
package ch.ehi.ili2mysql;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.settings.Settings;
import ch.ehi.sqlgen.DbUtility;
import ch.ehi.sqlgen.generator.SqlConfiguration;
import ch.ehi.sqlgen.generator_impl.jdbc.GeneratorJdbc;
import ch.ehi.sqlgen.generator_impl.jdbc.GeneratorJdbc.Stmt;
import ch.ehi.sqlgen.repository.*;

/**
 * @author ce
 * @version $Revision: 1.1 $ $Date: 2007/01/27 09:26:12 $
 */
public class GeneratorMysql extends GeneratorJdbc {
	/*
	The standard names for indexes in PostgreSQL are:

	{tablename}_{columnname(s)}_{suffix}

	where the suffix is one of the following:

	    pkey for a Primary Key constraint
	    key for a Unique constraint
	    excl for an Exclusion constraint
	    idx for any other kind of index
	    fkey for a Foreign key
	    check for a Check constraint

	Standard suffix for sequences is

	    seq for all sequences
	*/

	private boolean createGeomIdx=false;
	private ArrayList<DbColumn> indexColumns=null;
	
	@Override
	public void visitSchemaBegin(Settings config, DbSchema schema)
			throws IOException {
		super.visitSchemaBegin(config, schema);
		if(Config.TRUE.equals(config.getValue(SqlConfiguration.CREATE_GEOM_INDEX))){
			createGeomIdx=true;
		}
	}

	@Override
	public void visitColumn(DbTable dbTab,DbColumn column) throws IOException {
		String type="";
		String size="";
		String notSupported=null;
		boolean createColNow=true;
		if(column instanceof DbColBoolean){
			type="boolean";
		}else if(column instanceof DbColDateTime){
			type="timestamp";
		}else if(column instanceof DbColDate){
			type="date";
		}else if(column instanceof DbColTime){
			type="time";
		}else if(column instanceof DbColDecimal){
			DbColDecimal col=(DbColDecimal)column;
			type="decimal("+Integer.toString(col.getSize())+","+Integer.toString(col.getPrecision())+")";
		}else if(column instanceof DbColGeometry){
		    DbColGeometry geo=(DbColGeometry)column;
		    if(!geo.getSrsAuth().equals("EPSG")) {
		        throw new IllegalArgumentException("unexpected SrsAuth <"+geo.getSrsAuth()+">");
		    }
            //type="geometry("+getPostgisType(geo.getType())+(geo.getDimension()==3?"Z":"")+","+geo.getSrsId()+")";
            type="geometry";
		}else if(column instanceof DbColId){
			type="bigint";
		}else if(column instanceof DbColUuid){
			type="char(36)";
		}else if(column instanceof DbColNumber){
			DbColNumber col=(DbColNumber)column;
			type="integer";
		}else if(column instanceof DbColVarchar){
			int colsize=((DbColVarchar)column).getSize();
			if(colsize==DbColVarchar.UNLIMITED){
                type="text";
			}else{
				type="varchar("+Integer.toString(colsize)+")";
			}
		}else if(column instanceof DbColBlob){
			type="longblob";
		}else if(column instanceof DbColXml){
			type="longtext";
        }else if(column instanceof DbColJson){
            type="json";
		}else{
			type="text";
		}
        String cmt=column.getComment();
        if(cmt!=null){
            cmt=" COMMENT '"+escapeString(cmt)+"'";
        }else {
            cmt="";
        }
        if(column.getArraySize()!=DbColumn.NOT_AN_ARRAY && !(column instanceof DbColJson)) {
            String isNull=column.isNotNull()?"NOT NULL":"NULL";
            type="TEXT";
            String name=column.getName();
            out.write(getIndent()+colSep+name+" "+type+" "+isNull+cmt+newline());
            colSep=",";
        }else {
            String isNull=column.isNotNull()?"NOT NULL":"NULL";
            if(column.isPrimaryKey()){
                isNull="PRIMARY KEY";
            }
            String sep=" ";
            String defaultValue="";
            if(column.getDefaultValue()!=null){
                defaultValue=sep+"DEFAULT "+column.getDefaultValue();
                sep=" ";
            }
            if(column.isIndex() || (createGeomIdx && column instanceof DbColGeometry)){
                // just collect it; process it later
                indexColumns.add(column);
            }
            if(createColNow){
                String name=column.getName();
                out.write(getIndent()+colSep+name+" "+type+" "+isNull+defaultValue+cmt+newline());
                colSep=",";
            }
        }
	}
	
	@Override
	public void visit1TableBegin(DbTable tab) throws IOException {
		super.visit1TableBegin(tab);
		indexColumns=new ArrayList<DbColumn>();
	}
	
	@Override
	public void visitTableEndColumn(DbTable tab) throws IOException {
		boolean tableExists=DbUtility.tableExists(conn,tab.getName());
	}

	@Override
	public void visitIndex(DbIndex idx) throws IOException {
		if(idx.isUnique()){
			java.io.StringWriter out = new java.io.StringWriter();
			DbTable tab=idx.getTable();
			String tableName=tab.getName().getQName();
			String constraintName=idx.getName();
			if(constraintName==null){
				String colNames[]=new String[idx.sizeAttr()];
				int i=0;
				for(Iterator attri=idx.iteratorAttr();attri.hasNext();){
					DbColumn attr=(DbColumn)attri.next();
					colNames[i++]=attr.getName();
				}
				constraintName=createConstraintName(tab,"key", colNames);
			}
			out.write(getIndent()+"CREATE UNIQUE INDEX "+constraintName+" ON "+tableName+" (");
			String sep="";
			for(Iterator attri=idx.iteratorAttr();attri.hasNext();){
				DbColumn attr=(DbColumn)attri.next();
				
				if (attr instanceof DbColGeometry) {
					if (((DbColGeometry) attr).getType() == DbColGeometry.POINT) {
						// only for points?
					}
					out.write(sep+"ST_AsBinary("+attr.getName()+")");
				} else {
					out.write(sep+attr.getName());
				}				
				sep=",";
			}
			out.write(")"+newline());
			String stmt=out.toString();
			addCreateLine(new Stmt(stmt));
			out=null;
			if(conn!=null) {
	            if(createdTables.contains(tab.getName())){
	                Statement dbstmt = null;
	                try{
	                    try{
	                        dbstmt = conn.createStatement();
	                        EhiLogger.traceBackendCmd(stmt);
	                        dbstmt.executeUpdate(stmt);
	                    }finally{
	                        dbstmt.close();
	                    }
	                }catch(SQLException ex){
	                    IOException iox=new IOException("failed to add UNIQUE to table "+tab.getName());
	                    iox.initCause(ex);
	                    throw iox;
	                }
	            }
			}
		}
	}

    @Override
    protected String getTableEndOptions(DbTable tab) {
        String cmt = tab.getComment();
        if (cmt != null) {
            return("COMMENT '" + escapeString(cmt) + "'");
        }
        return "";
    }
	@Override
	public void visit1TableEnd(DbTable tab) throws IOException {
		String sqlTabName=tab.getName().getName();
		if(tab.getName().getSchema()!=null){
			sqlTabName=tab.getName().getSchema()+"."+sqlTabName;
		}
		boolean tableExists=DbUtility.tableExists(conn,tab.getName());
		super.visit1TableEnd(tab);

		for(DbColumn idxcol:indexColumns){
			
			String idxstmt=null;
			String idxName=createConstraintName(tab,"idx",idxcol.getName().toLowerCase());
			if(idxcol instanceof DbColGeometry){
				idxstmt="CREATE SPATIAL INDEX "+idxName+" ON "+sqlTabName.toLowerCase()+" ( "+idxcol.getName().toLowerCase()+" )";
			}else{
				idxstmt="CREATE INDEX "+idxName+" ON "+sqlTabName.toLowerCase()+" ( "+idxcol.getName().toLowerCase()+" )";
			}
			addCreateLine(new Stmt(idxstmt));
			
            if(conn!=null) {
                if(!tableExists){
                    Statement dbstmt = null;
                    try{
                        try{
                            dbstmt = conn.createStatement();
                            EhiLogger.traceBackendCmd(idxstmt);
                            dbstmt.execute(idxstmt);
                        }finally{
                            dbstmt.close();
                        }
                    }catch(SQLException ex){
                        IOException iox=new IOException("failed to add index on column "+tab.getName()+"."+idxcol.getName());
                        iox.initCause(ex);
                        throw iox;
                    }
                }
            }
		}
		indexColumns=null;
	}

	static public String escapeString(String cmt)
	{
		StringBuilder ret=new StringBuilder((int)cmt.length());
		for(int i=0;i<cmt.length();i++){
			char c=cmt.charAt(i);
			ret.append(c);
			if(c=='\''){
				ret.append(c);
			}
		}
		return ret.toString();
	}
	static public String getPostgisType(int type)
	{
		 switch(type){
			case DbColGeometry.POINT:
				return "POINT";
			case DbColGeometry.LINESTRING:
				return "LINESTRING";
			case DbColGeometry.POLYGON:
				return "POLYGON";
			case DbColGeometry.MULTIPOINT:
				return "MULTIPOINT";
			case DbColGeometry.MULTILINESTRING:
				return "MULTILINESTRING";
			case DbColGeometry.MULTIPOLYGON:
				return "MULTIPOLYGON";
			case DbColGeometry.GEOMETRYCOLLECTION:
				return "GEOMETRYCOLLECTION";
			case DbColGeometry.CIRCULARSTRING:
				return "CIRCULARSTRING";
			case DbColGeometry.COMPOUNDCURVE:
				return "COMPOUNDCURVE";
			case DbColGeometry.CURVEPOLYGON:
				return "CURVEPOLYGON";
			case DbColGeometry.MULTICURVE:
				return "MULTICURVE";
			case DbColGeometry.MULTISURFACE:
				return "MULTISURFACE";
			case DbColGeometry.POLYHEDRALSURFACE:
				return "POLYHEDRALSURFACE";
			case DbColGeometry.TIN:
				return "TIN";
			case DbColGeometry.TRIANGLE:
				return "TRIANGLE";
			default:
				throw new IllegalArgumentException();
		 }
	}

	@Override
	public void visitTableBeginConstraint(DbTable dbTab) throws IOException {
		super.visitTableBeginConstraint(dbTab);
		
		String sqlTabName=dbTab.getName().getQName();
		for(Iterator dbColi=dbTab.iteratorColumn();dbColi.hasNext();){
			DbColumn dbCol=(DbColumn) dbColi.next();
			if(dbCol.getReferencedTable()!=null){
	            if(dbCol.getArraySize()==DbColumn.NOT_AN_ARRAY) {
	                String createstmt=null;
	                String action="";
	                if(dbCol.getOnUpdateAction()!=null){
	                    action=action+" ON UPDATE "+dbCol.getOnUpdateAction();
	                }
	                if(dbCol.getOnDeleteAction()!=null){
	                    action=action+" ON DELETE "+dbCol.getOnDeleteAction();
	                }
	                String constraintName=createConstraintName(dbTab,"fkey",dbCol.getName());
	                //  ALTER TABLE ce.classb1 ADD CONSTRAINT classb1_t_id_fkey FOREIGN KEY ( t_id ) REFERENCES ce.classa1;
	                // MySQL checks foreign key constraints immediately; the check is not deferred to transaction commit. According to the SQL standard, the default behavior should be deferred checking.
	                createstmt="ALTER TABLE "+sqlTabName+" ADD CONSTRAINT "+constraintName+" FOREIGN KEY ( "+dbCol.getName()+" ) REFERENCES "+dbCol.getReferencedTable().getQName()
	                        +" ( "+getPrimaryKeyColumn(dbTab.getSchema(),dbCol.getReferencedTable()).getName()+" )";
	                
	                //  ALTER TABLE ce.classb1 DROP CONSTRAINT classb1_t_id_fkey;
	                String dropstmt=null;
	                dropstmt="ALTER TABLE "+sqlTabName+" DROP CONSTRAINT "+constraintName;

	                addConstraint(dbTab, constraintName,createstmt, dropstmt);
	            }
			}
			if(dbCol instanceof DbColNumber && (((DbColNumber)dbCol).getMinValue()!=null || ((DbColNumber)dbCol).getMaxValue()!=null)){
	            if(dbCol.getArraySize()==DbColumn.NOT_AN_ARRAY) {
	                DbColNumber dbColNum=(DbColNumber)dbCol;
	                String createstmt=null;
	                String action="";
	                if(dbColNum.getMinValue()!=null || dbColNum.getMaxValue()!=null){
	                    if(dbColNum.getMaxValue()==null){
	                        action=">="+dbColNum.getMinValue();
	                    }else if(dbColNum.getMinValue()==null){
	                        action="<="+dbColNum.getMaxValue();
	                    }else{
	                        action="BETWEEN "+dbColNum.getMinValue()+" AND "+dbColNum.getMaxValue();
	                    }
	                }
	                String constraintName=createConstraintName(dbTab,"check",dbCol.getName());
	                //  ALTER TABLE ce.classb1 ADD CONSTRAINT classb1_attr_check CHECK (attr BETWEEN 0 AND 50);
	                createstmt="ALTER TABLE "+sqlTabName+" ADD CONSTRAINT "+constraintName+" CHECK( "+dbCol.getName()+" "+action+")";
	                
	                //  ALTER TABLE ce.classb1 DROP CONSTRAINT classb1_t_id_fkey;
	                String dropstmt=null;
	                dropstmt="ALTER TABLE "+sqlTabName+" DROP CONSTRAINT "+constraintName;

	                addConstraint(dbTab, constraintName,createstmt, dropstmt);
	            }
			}else if(dbCol instanceof DbColDecimal && (((DbColDecimal)dbCol).getMinValue()!=null || ((DbColDecimal)dbCol).getMaxValue()!=null)){
	            if(dbCol.getArraySize()==DbColumn.NOT_AN_ARRAY) {
	                DbColDecimal dbColNum=(DbColDecimal)dbCol;
	                String createstmt=null;
	                String action="";
	                if(dbColNum.getMinValue()!=null || dbColNum.getMaxValue()!=null){
	                    if(dbColNum.getMaxValue()==null){
	                        action=">="+dbColNum.getMinValue();
	                    }else if(dbColNum.getMinValue()==null){
	                        action="<="+dbColNum.getMaxValue();
	                    }else{
	                        action="BETWEEN "+dbColNum.getMinValue()+" AND "+dbColNum.getMaxValue();
	                    }
	                }
	                String constraintName=createConstraintName(dbTab,"check",dbCol.getName());
	                //  ALTER TABLE ce.classb1 ADD CONSTRAINT classb1_attr_check CHECK (attr BETWEEN 0 AND 50);
	                createstmt="ALTER TABLE "+sqlTabName+" ADD CONSTRAINT "+constraintName+" CHECK( "+dbCol.getName()+" "+action+")";
	                
	                //  ALTER TABLE ce.classb1 DROP CONSTRAINT classb1_t_id_fkey;
	                String dropstmt=null;
	                dropstmt="ALTER TABLE "+sqlTabName+" DROP CONSTRAINT "+constraintName;

	                addConstraint(dbTab, constraintName,createstmt, dropstmt);
	            }
			}
            if(dbCol instanceof DbColVarchar && ((DbColVarchar)dbCol).getValueRestriction()!=null){
                if(dbCol.getArraySize()==DbColumn.NOT_AN_ARRAY) {
                    DbColVarchar dbColTxt=(DbColVarchar)dbCol;
                    String createstmt=null;
                    StringBuffer action=new StringBuffer("IN (");
                    String sep="";
                    for(String restrictedValue:dbColTxt.getValueRestriction()){
                        action.append(sep);
                        action.append("'");
                        action.append(escapeString(restrictedValue));
                        action.append("'");
                        sep=",";
                    }
                    action.append(")");
                    String constraintName=createConstraintName(dbTab,"check",dbCol.getName());
                    //  ALTER TABLE ce.classb1 ADD CONSTRAINT classb1_attr_check CHECK (attr IN ('a','b'));
                    createstmt="ALTER TABLE "+sqlTabName+" ADD CONSTRAINT "+constraintName+" CHECK( "+dbCol.getName()+" "+action.toString()+")";
                    
                    //  ALTER TABLE ce.classb1 DROP CONSTRAINT classb1_t_id_fkey;
                    String dropstmt=null;
                    dropstmt="ALTER TABLE "+sqlTabName+" DROP CONSTRAINT "+constraintName;

                    addConstraint(dbTab, constraintName,createstmt, dropstmt);
                }
            }
		}
	}

    private DbColumn getPrimaryKeyColumn(DbSchema schema,DbTableName referencedTableName) {
        DbTable referencedTable=schema.findTable(referencedTableName);
        for(Iterator<DbColumn> dbColIt=referencedTable.iteratorColumn();dbColIt.hasNext();) {
            DbColumn dbCol=dbColIt.next();
            if(dbCol.isPrimaryKey()) {
                return dbCol;
            }
        }
        return null;
    }

}
