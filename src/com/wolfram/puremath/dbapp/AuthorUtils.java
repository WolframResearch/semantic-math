package com.wolfram.puremath.dbapp;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.puremath.dbapp.DBUtils.AuthorTb;
import com.wolfram.puremath.dbapp.DBUtils.ThmHypTb;


/**
 * Utilities class for deploying and querying author table.
 * 
 * @author yihed
 *
 */
public class AuthorUtils {
	
	private static final Logger logger = LogManager.getLogger(AuthorUtils.class);
	
	/**
	 * Truncates the table , populates it with data from supplied csv file.
	 * @param csvFilePath Path to CSV file containing data to populate table with, resort
	 * to default file if none supplied. e.g. "metaDataNameDB.csv"
	 * @throws SQLException 
	 */
	public static void populateAuthorTb(Connection conn, String dataRootDirPath) throws SQLException {
		
		PreparedStatement pstm;
		//don't like using .toString(), but only option!
		String authorTbCsvAbsPath = Paths.get(dataRootDirPath, AuthorTb.CSV_REL_PATH).toString();
		
		pstm = conn.prepareStatement("TRUNCATE " + AuthorTb.TB_NAME + ";");		
		pstm.executeUpdate();
		
		//ALTER TABLE user_customer_permission DROP PRIMARY KEY;
		pstm = conn.prepareStatement("ALTER TABLE " + AuthorTb.TB_NAME + " DROP PRIMARY KEY;");
		pstm.executeUpdate();
		
		List<String> indexList = DBUtils.getAuthorTbIndexes();
		for(String index : indexList) {
			//including quotes result in syntax error. Do not setting string.
			pstm = conn.prepareStatement("DROP INDEX " + index + " ON " + AuthorTb.TB_NAME + ";");		
			pstm.executeUpdate();
		}		
				
		/*LOAD DATA INFILE  command
		 * mysql> LOAD DATA INFILE "/usr/share/tomcat/webapps/theoremSearchTest/src/thmp/data/metaDataNameDB.csv" 
		 * INTO TABLE authorTb COLUMNS TERMINATED BY "," OPTIONALLY ENCLOSED BY "'" ESCAPED BY "\\";
		 */		
		pstm = conn.prepareStatement("LOAD DATA INFILE ? INTO TABLE " + AuthorTb.TB_NAME 
				+ " COLUMNS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\'' ESCAPED BY '\\';");
		
		pstm.setString(1, authorTbCsvAbsPath);
		
		int rowsCount = pstm.executeUpdate();
		logger.info("ResultSet from loading csv data: "+rowsCount);
		
		//add keys back
		//CREATE INDEX authorIndex ON authorTb (author);
		StringBuilder keySb = new StringBuilder(200);
		keySb.append("ALTER TABLE ").append(DBUtils.AuthorTb.TB_NAME)
		.append(" ADD PRIMARY KEY (")
		.append(AuthorTb.THMID_COL).append(",")
		.append(AuthorTb.FIRSTNAME_COL).append(",")
		.append(AuthorTb.MIDDLENAME_COL).append(",")
		.append(AuthorTb.LASTNAME_COL).append(");");
		pstm = conn.prepareStatement(keySb.toString());
		
		pstm.executeUpdate();
		
		for(String index : indexList) {
			pstm = conn.prepareStatement("CREATE INDEX " + index + " ON " + DBUtils.AUTHOR_TB_NAME 
					+ " (" + DBUtils.AUTHOR_TB_NAME + ");");
			pstm.executeUpdate();
		}		
	}
	
	/**
	 * Create the ThmHypTb table.
	 * @param conn
	 * @param tableName tableName required since name could be different.
	 * @throws SQLException
	 */
	public static void createAuthorTb(Connection conn, String tableName) throws SQLException {
		
		PreparedStatement pstm;
		StringBuilder sb = new StringBuilder(50);
		
		//just drop table instead of checking.
		dropAuthorTb(conn, tableName);
		
		//don't create if table already exists:
		sb.append("SELECT 1 FROM ").append(AuthorTb.TB_NAME).append(" LIMIT 1;");
		pstm = conn.prepareStatement(sb.toString());
		
		ResultSet rs = null;
		try {
			rs = pstm.executeQuery();
		}catch(SQLException e) {
			System.out.println("!!e "+e);
			//pass, E.g. is table does not already exist.			
		}finally {
			System.out.println("!!rs "+rs);
			if(rs != null && rs.next()) {
				return;
			}	
		}
		//CREATE TABLE pet (name VARCHAR(20), owner VARCHAR(20),
		//	    -> species VARCHAR(20), sex CHAR(1), birth DATE, death DATE);
		//CREATE TABLE literalSearchTb (word VARCHAR(15), thmIndices VARBINARY(789), wordIndices VARBINARY(600))
		
		sb.setLength(0);
		sb.append("CREATE TABLE ").append(ThmHypTb.TB_NAME)
		.append(" (").append(ThmHypTb.THM_INDEX_COL).append(" INTEGER, ")
		.append(ThmHypTb.THM_COL).append(" VARCHAR(" + ThmHypTb.maxThmColLen + "),")
		.append(ThmHypTb.HYP_COL).append(" VARCHAR(" + ThmHypTb.maxHypColLen + "),")
		.append(ThmHypTb.FILE_NAME_COL).append(" VARCHAR(" + ThmHypTb.maxFileNameLen + "));");
		
		pstm = conn.prepareStatement(sb.toString());
		pstm.executeUpdate();
		
		sb = new StringBuilder(50);
		
		sb.append("ALTER TABLE ").append(ThmHypTb.TB_NAME)
		.append(" ADD PRIMARY KEY (")
		.append(ThmHypTb.THM_INDEX_COL).append(");");
		
		pstm = conn.prepareStatement(sb.toString());
		pstm.executeUpdate();
		
	}
	
	/** Deletes the ThmHypTb table.
	 * @param conn
	 * @param tableName table to drop.
	 * @throws SQLException
	 */
	public static void dropAuthorTb(Connection conn, String tableName) throws SQLException {
		
		//DROP [TEMPORARY] TABLE [IF EXISTS]
	    //tbl_name
		PreparedStatement pstm;
		
		pstm = conn.prepareStatement("DROP TABLE IF EXISTS " + AuthorTb.TB_NAME + ";");		
		pstm.executeUpdate();		
	}
	
	
	
}
