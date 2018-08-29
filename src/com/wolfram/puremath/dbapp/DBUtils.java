package com.wolfram.puremath.dbapp;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;


/**
 * Utility class used in DB application code, e.g. DB deployment.
 * @author yihed
 */
public class DBUtils {

	//Read user data from configuration file. This path is relative to base directory.
	private static final String dbConfigPath = "dbConfig.properties";
	//public static final String DEFAULT_USER;
	public static final String DEFAULT_PW;
	
	//account data for query user, with only select privilege, but none for modifying table.
	public static final String QUERY_USER;
	public static final String QUERY_PW;
	
	public static final String DEFAULT_SERVER = "localhost";
	public static final int DEFAULT_PORT = 3306;	
	public static final String DEFAULT_DB_NAME = "thmDB";
	//string to append to 
	public static String newNameSuffix = "new";
	
	//default directory path
	public static final String defaultDirPath;
	public static final int NUM_BITS_PER_BYTE = 8;
	
	private static final Logger logger = LogManager.getLogger(DBUtils.class);
	private static final int STM_EXECUTATION_FAILURE = -1;
	
	static {
		Properties dbppt = parseConfig(dbConfigPath);
		DEFAULT_USER = dbppt.getProperty("username");
		DEFAULT_PW = dbppt.getProperty("password");
		defaultDirPath = dbppt.getProperty("defaultDirPath");
		
		QUERY_USER = dbppt.getProperty("query_username");
		QUERY_PW = dbppt.getProperty("query_password");
	}
	
	public static class AuthorTb{
		
		public static final String TB_NAME = "authorTb";
		
		public static final String THMID_COL = "thmId";		
		public static final String PAPER_ID_COL = "paperId";
		
		//names of columns to create index on 
		public static final String FIRSTNAME_COL = "firstName";
		public static final String MIDDLENAME_COL = "middleName";
		public static final String LASTNAME_COL = "lastName";
		
		public static final int maxNameLen = 30;
		//public static final String AUTHOR_TB_CSV_PATH = "src/thmp/data/metaDataNameDB.csv";
		///home/usr0/yihed/thm
		/**relative (to ~/thm) path to serialization file containing thm index, and string of list of 
		 * indices of similar thms */
		public static final String CSV_REL_PATH = "src/thmp/data/metaDataNameDB.csv";
			
	}
	
	public static class SimilarThmsTb{
		/**relative (to ~/thm) path to serialization file containing thm index, and string of list of 
		 * indices of similar thms */
		public static final String similarThmIndexByteArrayPath = "src/thmp/data/similarThmIndexByteArray.dat";
		//to be used as root path for serialized maps. change the capitalizatio 
		public static final String similarThmIndexByteArrayPathNoDat = "src/thmp/data/similarThms/similarThmIndexByteArray";
		public static final String similarThmCombinedIndexByteArrayPath = "src/thmp/data/similarThms/similarThmCombinedIndexByteArray.dat";
		public static final String similarThmIndexByteArrayDirPath = "src/thmp/data/similarThms/";
		public static final String TB_NAME = "similarThmsTb";
		/**thm index*/
		public static final String INDEX_COL = "thmIndex";
		public static final String SIMILAR_THMS_COL = "similarThms";
	}
	
	/**
	 * Table containing concepts.
	 */
	public static class ThmConceptsTb{
		/**relative (to ~/thm) path to serialization file containing thm index, and string of list of 
		 * indices of similar thms */
		public static final String thmConceptsByteArrayPath = "src/thmp/data/thmConceptsByteArray.dat";
		//to be used as root path for serialized maps. change the capitalizatio 
		//public static final String similarThmIndexByteArrayPathNoDat = "src/thmp/data/similarThms/similarThmIndexByteArray";
		//public static final String similarThmCombinedIndexByteArrayPath = "src/thmp/data/similarThms/similarThmCombinedIndexByteArray.dat";
		//public static final String similarThmIndexByteArrayDirPath = "src/thmp/data/similarThms/";
		public static final String TB_NAME = "thmConceptsTb";
		/**thm index*/
		public static final String INDEX_COL = "thmIndex";
		public static final String CONCEPTS_COL = "thmConcepts";
	}
	
	/**
	 * Table containing literal search indices, i.e. words (normalized form)
	 * and thm indices that contain these words.
	 */
	public static class LiteralSearchTb{
		/**relative (to ~/thm) path to serialization file containing words, and string of list of 
		 * indices of thm indices */
		public static final String literalSearchByteArrayPath = "src/thmp/data/literalSearchByteArray.dat";
		//multimap of words and the LiteralSearchIndex's for the list of thms containing that word.
		//June 13, 2018, don't use literal search to save on memory.
		public static final String literalSearchIndexMMapPath = "src/thmp/data/literalSearchIndexMap.dat";
		public static final String TB_NAME = "literalSearchTb";
		/**thm index*/
		public static final String WORD_COL = "word";
		public static final String THM_INDICES_COL = "thmIndices";
		public static final String WORD_INDICES_COL = "wordIndices";
	}
	
	/**
	 * Table containing literal search indices, i.e. words (normalized form)
	 * and thm indices that contain these words.
	 */
	public static class ThmHypTb{
		/**relative (to ~/thm) path to serialization file containing words, and string of list of 
		 * indices of thm indices */
		//public static final String literalSearchByteArrayPath = "src/thmp/data/literalSearchByteArray.dat";
		//multimap of words and the LiteralSearchIndex's for the list of thms containing that word.
		public static final String thmHypPairsDirRelPath = "src/thmp/data/pe/";
		
		//these purposefully don't have file extension
		public static final String thmHypPairsNameRoot = "combinedParsedExpressionList";
		
		public static final String TB_NAME = "thmHypTb";
		/**thm index*/		
		public static final String THM_INDEX_COL = "thmIndex";
		public static final String THM_COL = "thm";
		public static final String HYP_COL = "hyp";
		public static final String FILE_NAME_COL = "fileName";
		//type of thm, e.g. Conjecture, Definition, etc.
		public static final String THM_TYPE_COL = "thmType";
		
		//32767 is 55535/2. A thm or hyp of this length has not been observed, not to mention 
		//both having this length.
		public static final int maxThmColLen = 32700; //32767;
		public static final int maxHypColLen = 32700; //32767;
		//max file name length, arXiv file, as characters.
		public static final int maxFileNameLen = 50;
		public static final int maxThmTypeLen = 15;
	}
	
	/**
	 * Get list of indexes for authorTb. The index names are also 
	 * column names, so can be used to add that index.
	 * @return
	 */
	public static final List<String> getAuthorTbIndexes(){
		
		List<String> indexes = new ArrayList<String>();
		//AUTHOR_TB_FIRSTNAME_Index;
		indexes.add(AuthorTb.FIRSTNAME_COL);
		indexes.add(AuthorTb.MIDDLENAME_COL);
		indexes.add(AuthorTb.LASTNAME_COL);
		return indexes;
	}
	
	/**
	 * Execute a mySql statement, for queries that should return a ResultSet,
	 * e.g. SELECT.
	 * *Note* caller is responsible for closing resultset and statement!
	 * @param stm
	 * @param ds
	 * @return ResultSet of executing the query.
	 */
	public static ResultSet executeSqlStatement(String stmStr, Connection conn) {
		
		try {
			PreparedStatement stm = null;
			ResultSet rs = null;
		
				//conn = ds.getConnection();
				//e.g. CREATE TABLE authorTb (thmId INT(20), author VARCHAR(20), content VARCHAR(200))
				//or INSERT INTO authorTb (thmId, author, content) VALUES (1, 's', 'content')
				stm = conn.prepareStatement(stmStr);
				rs = stm.executeQuery();
				//System.out.println("restultSet "+rs);
				return rs;
				
		} catch (SQLException e) {
			
			logger.error("SQLException while executing " + stmStr + " cause " + e);
		}
		return null;
	}
	
	/**
	 * Execute a mySql statement, for queries that should return a ResultSet,
	 * e.g. SELECT.
	 * *Note* caller is responsible for closing resultset and statement!
	 * @param stm
	 * @param ds
	 * @return ResultSet of executing the query.
	 */
	public static ResultSet executeSqlStatement(PreparedStatement pstm, Connection conn) {
		
		try {			
			//e.g. CREATE TABLE authorTb (thmId INT(20), author VARCHAR(20), content VARCHAR(200))
			//or INSERT INTO authorTb (thmId, author, content) VALUES (1, 's', 'content')			
			ResultSet rs = pstm.executeQuery();
			return rs;
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
			PreparedStatement stm = null;			
			try {
				//conn = ds.getConnection();
				//e.g. CREATE TABLE authorTb (thmId INT(20), author VARCHAR(20), content VARCHAR(200))
				//or INSERT INTO authorTb (thmId, author, content) VALUES (1, 's', 'content')
				stm = conn.prepareStatement(stmStr);
				int rs = stm.executeUpdate();
				//System.out.println("restultSet "+rs);
				return rs;
				//stm = conn.prepareStatement("INSERT INTO authorTb (thmId, author, content)"
				//		+ "VALUES (1, 's', 'content')");
			}finally {
				stm.close();
			}
		} catch (SQLException e) {			
			logger.error("SQLException while execute " + stmStr + " cause " + e);
		}		
		return STM_EXECUTATION_FAILURE;
	}
	
	/**
	 * Get connection to local (e.g. test-machine) database. 
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
	
	/**
	 * Deserialize objects from file supplied by serialFileStr.
	 * **Don't forget to ensure that
	 * path must include servlet path! I.e. real path on machine.
	 * 
	 * @param serialFileStr
	 * @return *List* of objects from the file.
	 */
	public static Object deserializeListFromFile(String serialFileStr) {
		FileInputStream fileInputStream = null;
		try {
			fileInputStream = new FileInputStream(serialFileStr);
		} catch (FileNotFoundException e) {
			String msg = "Serialization data file not found! " + serialFileStr;
			logger.error(msg);
			throw new IllegalStateException(msg);
		}

		ObjectInputStream objectInputStream = null;
		try {
			objectInputStream = new ObjectInputStream(fileInputStream);
			return objectInputStream.readObject();
		} catch (ClassNotFoundException | IOException e) {
			String msg = "Exception while opening ObjectOutputStream " + e;
			logger.error(msg);
			throw new IllegalStateException(msg);
		} finally {
			silentClose(fileInputStream);
		}
	}
	
	/**
	 * Closing resource, loggin possible IOException, without clobbering existing
	 * Exceptions if any has been thrown.
	 * 
	 * @param fileInputStream
	 */
	private static void silentClose(Closeable resource) {
		if (null == resource)
			return;
		try {
			resource.close();
		} catch (IOException e) {
			e.printStackTrace();
			logger.error("IOException while closing resource: " + resource);
		}
	}
	
	public static void renameTable(Connection conn, String fromName, String toName) throws SQLException {
		//RENAME TABLE `group` TO `member`;
		PreparedStatement pstm;
		
		pstm = conn.prepareStatement("RENAME TABLE `?` TO `?`;");
		pstm.setString(1, fromName);
		pstm.setString(2, toName);
		pstm.executeUpdate();	
	}
	
	/**
	 * Rename two tables atomically. Useful e.g. updating a PRD table.
	 * @param conn
	 * @param fromName1
	 * @param toName1
	 * @param fromName2
	 * @param toName2
	 * @throws SQLException
	 */
	public static void renameTwoTableAtomic(Connection conn, String fromName1, String toName1,
			String fromName2, String toName2) throws SQLException {
		//RENAME TABLE `group` TO `member`;
		PreparedStatement pstm;
		
		pstm = conn.prepareStatement("RENAME TABLE `?` TO `?`, `?` TO `?`;");
		pstm.setString(1, fromName1);
		pstm.setString(2, toName1);
		pstm.setString(3, fromName2);
		pstm.setString(4, toName2);
		
		pstm.executeUpdate();	
	}
	
	/** Removes the named table if exists.
	 * @param conn
	 * @param tableName table to drop.
	 * @throws SQLException
	 */
	public static void dropTableIfExists(Connection conn, String tableName) throws SQLException {
		
		//DROP [TEMPORARY] TABLE [IF EXISTS] tbl_name
		PreparedStatement pstm;
		
		pstm = conn.prepareStatement("DROP TABLE IF EXISTS " + tableName + ";");		
		pstm.executeUpdate();		
	}
	
	/**
	 * Close connection without throwing SQLException.
	 * @param conn
	 */
	public static void silentCloseConn(Connection conn) {
		try {
			conn.close();
		}catch(SQLException e) {
			String msg = "SQLException while closing connection " + e;
			logger.error(msg);
		}
	}
	
	/**
	 * Set global mysql mode to be nonstrict, e.g. so rows are truncated if they exceed maximal length.
	 * @param conn
	 */
	public static void setNonStrictMode(Connection conn) {
		String s = "SET @@global.sql_mode = 'NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION'";
		manipulateData(s, conn);
	}
	
	/**
	 * Read in configuration settings from file, and parse.
	 * @param path
	 * @throws IllegalStateException when file not found or other IOException,
	 * since these properties are crucial.
	 */
	public static Properties parseConfig(String configPath) {
		Properties ppt = new Properties();
		FileInputStream inStrm = null;
		try {
			inStrm = new FileInputStream(configPath);
			ppt.load(inStrm);
			return ppt;
		} catch (FileNotFoundException e) {
			throw new IllegalStateException("Config file not found for path " + configPath);
		} catch (IOException e) {
			throw new IllegalStateException("IOException while loading config file on path " + configPath);
		}finally {
			silentClose(inStrm);
		}
	}
	
}
