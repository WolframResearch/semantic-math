package thmp.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import thmp.search.DBSearch;
import thmp.search.Searcher.SearchMetaData;

/**
 * Utility methods for database manipulations, for MySql database.
 * @author yihed
 */
public class DBUtils {

	//public static final String DEFAULT_DB_NAME = "thmDB";
	
	private static final DataSource DEFAULT_DATASOURCE;
	private static final String[] globalDBSettings;
	
	private static final Logger logger = LogManager.getLogger(DBUtils.class);
	
	static {
		DataSource pooledDS = null;
		//Connection defaultConn = null;
		
		//try {
			/*pooledDS = getDataSource(DBUtils.DEFAULT_DB_NAME, DBUtils.DEFAULT_USER, DBUtils.DEFAULT_PW, 
					DBUtils.DEFAULT_SERVER, DBUtils.DEFAULT_PORT);*/
			pooledDS = FileUtils.getPooledDataSource();
			
			//defaultConn = pooledDS.getConnection();
		/*}catch(SQLException e) {
			String msg = "SQLException when trying to establish default connection " + e.getMessage();
			logger.error(msg);
		}*/
		DEFAULT_DATASOURCE = pooledDS;
		//need to set both global and session timeout. Recall session trumps global.
		globalDBSettings = new String[] {"SET GLOBAL MAX_EXECUTION_TIME=1000;", "SET wait_timeout=31536000;",
				"SET GLOBAL wait_timeout=31536000;"};
		setGlobalDBSettings();
	}
	
	/**
	 * Conjunction or disjunction type.
	 */
	public enum ConjDisjType{
		CONJ("and"),
		DISJ("or");
		
		String typeStr;
		
		private ConjDisjType(String s) {
			this.typeStr = s;
		}
		
		/**
		 * Gets the type based on the colloquial name.
		 * @param called the colloquial name, defaults to "or" if string not reognized
		 * @return
		 */
		public static ConjDisjType getType(String called) {
			return CONJ.typeStr.equals(called) ? CONJ : DISJ;
		}
		
		/**
		 * @return "AND" or "OR" for CONJ or DISJ, resp.
		 */
		public String getDbName() {
			return typeStr.toUpperCase();
		}
	}
	
	public static class AuthorName{
		private String firstName;
		private String middleName;
		private String lastName;	
		private static final Pattern NAME_SPLIT_PATT = Pattern.compile("[.\\s]+");
		
		public AuthorName(String firstName_, String middleName_, String lastName_) {
			this.firstName = firstName_;
			this.middleName = middleName_;
			this.lastName = lastName_;
		}
		
		public AuthorName(String name) {
			String[] authorNameAr = NAME_SPLIT_PATT.split(name.trim());
			
			this.firstName = "";
			this.middleName = "";
			this.lastName = "";
			switch(authorNameAr.length) {
			case 1:
				this.lastName = authorNameAr[0];
				break;
			case 2:
				this.firstName = authorNameAr[0];
				this.lastName = authorNameAr[1];
				break;
			default:
				this.firstName = authorNameAr[0];
				//take care of case when more middle name is provided!
				this.middleName = authorNameAr[1];
				this.lastName = authorNameAr[authorNameAr.length - 1];
			}
			
		}
		
		@Override
		public String toString() {
			return this.firstName + " " + this.middleName + " " + this.lastName;
		}
		/**
		 * Create new AuthorName object where:
		 * Abbreviate first name to the first initial, remove middle name,
		 * keep last name intact. 
		 * Note first name can be empty string.
		 * @return
		 */
		public AuthorName abbreviateName() {			
			//String firstName = this.firstInitial();			
			return new AuthorName(this.firstInitial(), "", this.lastName);
		}
		
		/**
		 * First initial. Empty string if no first name provided.
		 * @return
		 */
		public String firstInitial() {
			if(this.firstName.length() == 0) {
				return "";
			}
			return this.firstName.substring(0, 1);
		}
		
		public String middleInitial() {
			if(this.middleName.length() == 0) {
				return "";
			}
			return this.middleName.substring(0, 1);
		}
		
		public String lastInitial() {
			if(this.lastName.length() == 0) {
				return "";
			}
			return this.lastName.substring(0, 1);
		}
		
		public String firstName() {
			return this.firstName;
		}		
		public String middleName() {
			return this.middleName;
		}
		public String lastName() {
			return this.lastName;
		}
	}
	
	public static void setGlobalDBSettings() {
		
		Connection conn = getPooledConnection();
		for(String setting : globalDBSettings) {
			executeSqlStatement(setting, conn);				
		}
		closePooledConnection(conn);
	}
	
	/**
	 * Repopulates data in author table, with updated data
	 * on thm indices. 
	 * Will delete existing author table and everything on it!
	 * @deprecated July 2018. Use 
	 */
	public static void reloadAuthorTable(Connection conn) {
		
		//delete existing table
		manipulateData("DROP TABLE " + DBSearch.AUTHOR_TABLE_NAME, conn);
		
		//CREATE TABLE authorTb (thmId INT(20), author VARCHAR(20), content VARCHAR(200))
		String stm = "CREATE TABLE "
				+ DBSearch.AUTHOR_TABLE_NAME + "("
				+ DBSearch.THM_ID_COL + " INT(10), "
				//e.g. math3243235, or math-ph35399623
				+ DBSearch.PAPER_ID_COL + " VARCHAR(15), "
				+ DBSearch.FIRST_NAME_COL + " VARCHAR(20), " 
				+ DBSearch.MIDDLE_NAME_COL + " VARCHAR(10), "
				+ DBSearch.LAST_NAME_COL + " VARCHAR(22)"
				+ ");";
		
		manipulateData(stm, conn);
		
		ResultSet rs = executeSqlStatement("SELECT @@GLOBAL.sql_mode;", conn);
		try {
		while(rs.next()) {
			logger.info ("sql_mode results: " + rs.getString(1));
			
		}
		} catch (SQLException e) {
			logger.error("SQLException when getting GLOABL.sql_mode "+e);
		}
		/*
		 * LOAD DATA INFILE "/sqldata/csv1.csv" INTO TABLE csv1 COLUMNS TERMINATED BY ',' ENCLOSED BY "'" ESCAPED BY "\\";
		 * 
		 * LOAD DATA INFILE "/usr/share/tomcat/webapps/theoremSearchTest/src/thmp/data/metaDataNameDB.csv" INTO TABLE authorTb 
		 * COLUMNS TERMINATED BY "," OPTIONALLY ENCLOSED BY "'" ESCAPED BY "\\";
		 */
		String csvPath = FileUtils.getPathIfOnServlet(SearchMetaData.nameCSVDataPath());
		stm = "LOAD DATA INFILE \"" + csvPath +"\" INTO TABLE "
				+ DBSearch.AUTHOR_TABLE_NAME + " COLUMNS TERMINATED BY \",\" "
				+ "OPTIONALLY ENCLOSED BY \"'\" ESCAPED BY \"\\\\\" ;";
		manipulateData(stm, conn);		
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
		return com.wolfram.puremath.dbapp.DBUtils.executeSqlStatement(stmStr, conn);
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
		return com.wolfram.puremath.dbapp.DBUtils.manipulateData(stmStr, conn);
	}
	/*public static int manipulateData(String stmStr, Connection conn) {
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
	}*/	
	
	/**
	 * Obtain handle to datasource connection from as provided by web container 
	 * Connections pool.
	 * *MUST* call thmp.utils.DBUtils.closePooledConnection to return connection 
	 * to pool when done!
	 * Currently (Jan 2018) should be only called from servlet methods to accompany 
	 * one connection per HTTP request.
	 * @return @Nullable connection from the pool. Null if cannot acquire connection.
	 * 
	 */
	public static Connection getPooledConnection() {
		
		DataSource ds = FileUtils.getPooledDataSource();
		Connection connHandle = null;
		try {
			connHandle = ds.getConnection();
			/**Interesting fact: conn here has class
			 * class com.sun.proxy.$Proxy91, generated at runtime,
			 * that implements both a PooledConnection and a Connection.
			 * I.e. a java.sql.Connection/javax.sql.PooledConnection reflection proxy.
			 * One can get the actual connection by 
			 * ((javax.sql.PooledConnection)connHandle).getConnection();
			 */
			
		} catch (SQLException e) {
			logger.error("SQLException when getting pooled connection! " + e);
		}
		return connHandle;
	}
	
	/**
	 * Closes handle to PooledConnection, returns underlying connection back
	 * to datasource Connections pool.
	 * @param conn
	 */
	public static void closePooledConnection(Connection conn) {
		if(null == conn) return;
		try {
			conn.close();
		} catch (SQLException e) {			
			logger.error("SQLException when closing pooled connection! " + e);
		}
	}
	
	/**
	 * Gets the default DataSource connection.
	 * @return
	 * @throws SQLException 
	 * delete in weeks - Dec 2017.
	 */
	/*public static Connection getNewDefaultDSConnection1() throws SQLException {
		return getPooledConnection();
		
	}*/
	
	/**
	 * Gets the default DataSource connection .
	 * @return
	 * @throws SQLException 
	 */
	/*public static Connection getDefaultDSConnection() {
		//handle if default conn times out!!
		return DEFAULT_CONNECTION;
	}*/
	
	/**
	 * Gets the default DataSource.
	 * @return
	 * @throws SQLException 
	 */
	public static DataSource getDefaultDS1() {
		//handle if default conn times out!!
		return DEFAULT_DATASOURCE;
	}
	
	private static void createDatabase() {
		MysqlDataSource ds = new MysqlDataSource();
		ds.setUser(com.wolfram.puremath.dbapp.DBUtils.DEFAULT_USER);
		ds.setPassword(com.wolfram.puremath.dbapp.DBUtils.DEFAULT_PW);
		ds.setServerName(com.wolfram.puremath.dbapp.DBUtils.DEFAULT_SERVER);
		ds.setPortNumber(com.wolfram.puremath.dbapp.DBUtils.DEFAULT_PORT);
		ds.setCreateDatabaseIfNotExist(true);
		System.out.println("ds.getCreateDatabaseIfNotExist() "+ds.getCreateDatabaseIfNotExist());
		
		ds.setDatabaseName("thmDB");
		Connection conn = null;
		try {
			conn = ds.getConnection();
			PreparedStatement stm = conn.prepareStatement("CREATE TABLE authorTb (thmId INT(20),"
					+ "author VARCHAR(20), content VARCHAR(200))");
			int rs = stm.executeUpdate();
			System.out.println("restultSet "+rs);
			stm = conn.prepareStatement("INSERT INTO authorTb (thmId, author, content)"
					+ "VALUES (1, 's', 'content')");
			rs = stm.executeUpdate();
			System.out.println("restultSet "+rs);
		} catch (SQLException e) {
			
			e.printStackTrace();
		}
		
	}
	
}
