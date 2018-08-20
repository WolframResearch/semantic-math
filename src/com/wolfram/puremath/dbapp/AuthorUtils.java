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
import com.wolfram.puremath.dbapp.DBUtils.LiteralSearchTb;
import com.wolfram.puremath.dbapp.DBUtils.ThmHypTb;

import thmp.search.LiteralSearch;


/**
 * Utilities class for deploying and querying author table.
 * Used for author search.
 * 
 * @author yihed
 *
 */
public class AuthorUtils {
	
	private static final Logger logger = LogManager.getLogger(AuthorUtils.class);
	
	/**
	 * Truncates the table , populates it with data from supplied csv file.
	 * Note this assumes primary key exists. Call createAuthorTb() beforehand if unsure.
	 * @param dataRootDirPath Path to ancestor director to CSV file containing data to 
	 * populate table with, e.g. "metaDataNameDB.csv".
	 * Path should be to directory containing src/thmp/data/metaDataNameDB.csv.
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
		
		//the indexes are also column names.
		List<String> indexList = DBUtils.getAuthorTbIndexes();
		/*for(String index : indexList) {
			//including quotes result in syntax error. Do not setting string.
			pstm = conn.prepareStatement("DROP INDEX " + index + " ON " + AuthorTb.TB_NAME + ";");		
			pstm.executeUpdate();
		}*/		
				
		/*LOAD DATA INFILE  command
		 * mysql> LOAD DATA INFILE "/usr/share/tomcat/webapps/theoremSearchTest/src/thmp/data/metaDataNameDB.csv" 
		 * INTO TABLE authorTb COLUMNS TERMINATED BY "," OPTIONALLY ENCLOSED BY "'" ESCAPED BY "\\";
		 * Each line in csv file has form 2,'1209.1247','hirokazu','','nishimura'
			3,'1209.1247','hirokazu','','nishimura'.
		 */		
		pstm = conn.prepareStatement("LOAD DATA INFILE ? INTO TABLE " + AuthorTb.TB_NAME 
				+ " COLUMNS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\\'' ESCAPED BY '\\\\';");
		
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
			//ON tbl_name (index_col_name,...), the indexes are also column names.
			pstm = conn.prepareStatement("CREATE INDEX " + index + " ON " + AuthorTb.TB_NAME 
					+ " (" + index + ");");
			pstm.executeUpdate();
		}		
	}
	
	/**
	 * Create the ThmHypTb table.
	 * @param conn
	 * @param tableName tableName required since name could be different.
	 * @throws SQLException
	 */
	public static void createAuthorTb(Connection conn) throws SQLException {
		createAuthorTb(conn, DBUtils.AuthorTb.TB_NAME);
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
		DBUtils.dropTableIfExists(conn, tableName);
		
		//don't create if table already exists:
		/*sb.append("SELECT 1 FROM ").append(AuthorTb.TB_NAME).append(" LIMIT 1;");
		pstm = conn.prepareStatement(sb.toString());
		
		ResultSet rs = null;
		try {
			rs = pstm.executeQuery();
		}catch(SQLException e) {
			System.out.println("!!e "+e);
			//pass, E.g. is table does not already exist.			
		}finally {
			
			if(rs != null && rs.next()) {
				return;
			}	
		}*/
		
		//CREATE TABLE pet (name VARCHAR(20), owner VARCHAR(20),
		//	    -> species VARCHAR(20), sex CHAR(1), birth DATE, death DATE);
		//CREATE TABLE literalSearchTb (word VARCHAR(15), thmIndices VARBINARY(789), wordIndices VARBINARY(600))
		//each line in csv has form 2,'1209.1247','hirokazu','','nishimura'.
		sb.setLength(0);
		sb.append("CREATE TABLE ").append(AuthorTb.TB_NAME)
		.append(" (").append(AuthorTb.THMID_COL).append(" INT(10), ")
		.append(AuthorTb.PAPER_ID_COL).append(" VARCHAR(15),")
		
		.append(AuthorTb.FIRSTNAME_COL).append(" VARCHAR(" + AuthorTb.maxNameLen + "),")
		//temporary due to a bug in data given 
		.append(AuthorTb.MIDDLENAME_COL).append(" VARCHAR(" + 101 + "),")
		.append(AuthorTb.LASTNAME_COL).append(" VARCHAR(" + AuthorTb.maxNameLen + "));");
		
		/*.append(AuthorTb.FIRSTNAME_COL).append(" VARCHAR(" + ThmHypTb.maxThmColLen + "),")
		.append(AuthorTb.MIDDLENAME_COL).append(" VARCHAR(" + ThmHypTb.maxHypColLen + "),")
		.append(AuthorTb.LASTNAME_COL   ThmHypTb.HYP_COL).append(" VARCHAR(" + ThmHypTb.maxHypColLen + "),")
		.append(ThmHypTb.FILE_NAME_COL).append(" VARCHAR(" + ThmHypTb.maxFileNameLen + "));");*/
		
		/*
		 * .append(AuthorTb.THMID_COL).append(",")
		.append(AuthorTb.FIRSTNAME_COL).append(",")
		.append(AuthorTb.MIDDLENAME_COL).append(",")
		.append(AuthorTb.LASTNAME_COL).append(");");
		pstm = conn.prepareStatement(keySb.toString());
		
		pstm = conn.prepareStatement("CREATE TABLE " + LiteralSearchTb.TB_NAME 
				+ " (" + LiteralSearchTb.WORD_COL + " VARCHAR(" + LiteralSearch.LITERAL_WORD_LEN_MAX + "),"
				+ LiteralSearchTb.THM_INDICES_COL + " VARBINARY(" + varbinaryLen + "),"
				+ LiteralSearchTb.WORD_INDICES_COL + " VARBINARY(" + wordArVarBinaryLen 
				+ ")) COLLATE utf8_bin;");
		 */
		
		pstm = conn.prepareStatement(sb.toString());
		pstm.executeUpdate();
		
		sb = new StringBuilder(50);
		
		sb.append("ALTER TABLE ").append(AuthorTb.TB_NAME)
		.append(" ADD PRIMARY KEY (")
		.append(AuthorTb.THMID_COL).append(");");
		
		pstm = conn.prepareStatement(sb.toString());
		pstm.executeUpdate();		
	}
		
}
