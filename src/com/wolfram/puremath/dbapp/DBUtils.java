package com.wolfram.puremath.dbapp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;


/**
 * Utility class used in DB application code, e.g. DB deployment.
 * @author yihed
 */
public class DBUtils {

	public static final String DEFAULT_USER = "root";
	public static final String DEFAULT_PW = "Wolframwolfram0*";
	public static final String DEFAULT_SERVER = "localhost";
	public static final int DEFAULT_PORT = 3306;	
	public static final String DEFAULT_DB_NAME = "thmDB";
	public static final String AUTHOR_TB_NAME = "authorTb";
	//public static final String AUTHOR_TB_PRIMARY_KEY = "PRIMARY";
	public static final String AUTHOR_TB_THMID_COL = "thmId";
	//names of columns to create index on 
	public static final String AUTHOR_TB_FIRSTNAME_COL = "firstName";
	public static final String AUTHOR_TB_MIDDLENAME_COL = "middleName";
	public static final String AUTHOR_TB_LASTNAME_COL = "lastName";
	
	/**relative (to ~/thm) path to serialization file containing thm index, and string of list of 
	 * indices of similar thms */
	public static final String similarThmIndexStrPath = "src/thmp/data/similarThmIndexStr.dat";
	
	private static final Logger logger = LogManager.getLogger(DBUtils.class);
	private static final int STM_EXECUTATION_FAILURE = -1;
	
	/**
	 * Get list of indexes for authorTb. 
	 * @return
	 */
	public static final List<String> getAuthorTbIndexes(){
		
		List<String> indexes = new ArrayList<String>();
		//AUTHOR_TB_FIRSTNAME_Index;
		indexes.add(AUTHOR_TB_FIRSTNAME_COL);
		indexes.add(AUTHOR_TB_MIDDLENAME_COL);
		indexes.add(AUTHOR_TB_LASTNAME_COL);
		return indexes;
	}
	
	/**
	 * Execute a mySql statement, for queries that should return a ResultSet,
	 * e.g. SELECT.
	 * 
	 * @param stm
	 * @param ds
	 * @return ResultSet of executing the query.
	 */
	public static ResultSet executeSqlStatement(String stmStr, Connection conn) {
		//Connection conn = null;
		try {
			//conn = ds.getConnection();
			//e.g. CREATE TABLE authorTb (thmId INT(20), author VARCHAR(20), content VARCHAR(200))
			//or INSERT INTO authorTb (thmId, author, content) VALUES (1, 's', 'content')
			PreparedStatement stm = conn.prepareStatement(stmStr);
			ResultSet rs = stm.executeQuery();
			//System.out.println("restultSet "+rs);
			return rs;
			//stm = conn.prepareStatement("INSERT INTO authorTb (thmId, author, content)"
			//		+ "VALUES (1, 's', 'content')");
			
		} catch (SQLException e) {
			
			logger.error("SQLException while executing " + stmStr + " cause " + e);
		}		
		return null;
	}
	
	/**
	 * Execute a mySql statement, for queries that should return a ResultSet,
	 * e.g. SELECT.
	 * 
	 * @param stm
	 * @param ds
	 * @return ResultSet of executing the query.
	 */
	public static ResultSet executeSqlStatement(PreparedStatement pstm, Connection conn) {
		//Connection conn = null;
		try {
			//conn = ds.getConnection();
			//e.g. CREATE TABLE authorTb (thmId INT(20), author VARCHAR(20), content VARCHAR(200))
			//or INSERT INTO authorTb (thmId, author, content) VALUES (1, 's', 'content')
			
			ResultSet rs = pstm.executeQuery();
			//System.out.println("restultSet "+rs);
			return rs;
			//stm = conn.prepareStatement("INSERT INTO authorTb (thmId, author, content)"
			//		+ "VALUES (1, 's', 'content')");
			
		} catch (SQLException e) {			
			logger.error("SQLException while executing " + pstm + " cause " + e);
		}		
		return null;
	}
	
	/**
	 * Execute a mySql data manipulation statement, e.g. INSERT, CREATE, DELETE, etc.
	 * @param stm
	 * @param ds
	 * @return number of rows changed.
			 * either (1) the row count for SQL Data Manipulation Language (DML) statements or 
			 * (2) 0 for SQL statements that return nothing, or -1 on failure.
	 */
	public static int manipulateData(String stmStr, Connection conn) {
		//Connection conn = null;
		try {
			//conn = ds.getConnection();
			//e.g. CREATE TABLE authorTb (thmId INT(20), author VARCHAR(20), content VARCHAR(200))
			//or INSERT INTO authorTb (thmId, author, content) VALUES (1, 's', 'content')
			PreparedStatement stm = conn.prepareStatement(stmStr);
			int rs = stm.executeUpdate();
			//System.out.println("restultSet "+rs);
			return rs;
			//stm = conn.prepareStatement("INSERT INTO authorTb (thmId, author, content)"
			//		+ "VALUES (1, 's', 'content')");
			
		} catch (SQLException e) {
			
			logger.error("SQLException while execute " + stmStr + " cause " + e);
		}		
		return STM_EXECUTATION_FAILURE;
	}
	
	/**
	 * Get connection to local (test-machine) databse. 
	 * @return
	 */
	public static Connection getLocalConnection() {
		DataSource ds = getLocalDataSource(DBUtils.DEFAULT_DB_NAME, DBUtils.DEFAULT_USER, DEFAULT_PW, 
				DBUtils.DEFAULT_SERVER, DBUtils.DEFAULT_PORT);
		Connection conn = null;
		try {
			 conn = ds.getConnection();
		} catch (SQLException e) {
			throw new IllegalStateException("SQLException when acquiring a DataSource!");
		}
		return conn;
	}
	
	/**
	 * Obtain datasource with prescribed params, create DB if none exists.
	 * Default values are as follows:
	 * @param user "root"
	 * @param pw "wolfram"
	 * @param serverName "localhost"
	 * @param portNum "3306"
	 * @return datasource that one can obtain Connections from.
	 * 
	 */
	public static DataSource getLocalDataSource(String dbName, String user, String pw, String serverName, 
			int portNum) {
		MysqlDataSource ds = new MysqlDataSource();
		ds.setUser(user);
		//ds.setPassword("Lzft+utkk5q2");
		ds.setPassword(pw);
		ds.setServerName(serverName);
		ds.setPortNumber(portNum);
		ds.setCreateDatabaseIfNotExist(true);
		//System.out.println("ds.getCreateDatabaseIfNotExist() "+ds.getCreateDatabaseIfNotExist());
		//should actually use import org.apache.tomcat.jdbc.pool.DataSource;
		//and create this on servlet code
		ds.setDatabaseName(dbName);		
		return ds;
	}
}
