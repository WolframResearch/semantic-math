package thmp.test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;

import javax.sql.DataSource;

import org.junit.Test;

import thmp.search.DBSearch;
import com.wolfram.puremath.dbapp.DBUtils;

/**
 * Test SQL query parsing.
 * 
 * @author yihed
 */
public class TestSqlQueryParse {
	
	private static final Connection DEFAULT_CONN = getLocalConnection();
	
	/**
	 * 
	 * @param inputStr
	 * @param pstmStr PreparedStatement string. 
	 * @param targetStr Target string with '?' populated.
	 * @return
	 */
	private static boolean checkParse(String inputStr, String pstmStr, String targetStr) {
		DBSearch.AuthorRelation rel = new DBSearch.AuthorRelation(inputStr); 
		String queryStr = rel.sqlQueryStr();
		boolean pass = targetStr.equals(queryStr);
		
		PreparedStatement pstm = null;
		boolean abbreviateFirstNameBool = false;
		boolean abbreviateLastNameBool = false;
		try {
			pstm = rel.getPreparedStm(DEFAULT_CONN, abbreviateFirstNameBool, abbreviateLastNameBool);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		pass &= pstmStr.equals(pstm.toString());		
		return pass;
	}
	
	/**
	 * Get connection to local (test-machine) databse. 
	 * @return
	 */
	public static Connection getLocalConnection() {
		DataSource ds = com.wolfram.puremath.dbapp.DBUtils.getLocalDataSource(DBUtils.DEFAULT_DB_NAME, 
				DBUtils.DEFAULT_USER, "wolfram", DBUtils.DEFAULT_SERVER, DBUtils.DEFAULT_PORT);
		Connection conn = null;
		try {
			 conn = ds.getConnection();
		} catch (SQLException e) {
			throw new IllegalStateException("SQLException when acquiring a DataSource!");
		}
		return conn;
	}
	
	@Test
	public void test() {
		
		DBSearch.AuthorRelation rel;// = new DBSearch.AuthorRelation("F1 L1 and (F2 L2 or F3 L3)");
		//rel = new DBSearch.AuthorRelation("F1 L1 and ( F2 L2 or (F3 L3)");
				//rel = new DBSearch.AuthorRelation("tao and (A1 d1  j or A2)");
				//rel = new DBSearch.AuthorRelation("F1 L1 and ( F2 L2 or (F3 L3))"); //<--F2 dropped???
				//rel = new DBSearch.AuthorRelation("F1 L1 and (and ( F2)"); //<--make into test case
		rel = new DBSearch.AuthorRelation("F1 L1 and (and ( F2)"); 
		String queryStr = rel.sqlQueryStr();
		
		String targetStr = "SELECT t0.thmId FROM (authorTb AS t0, authorTb AS t1) WHERE t0.thmId=t1.thmId "
				+ "AND ( t0.firstName LIKE 'F%'  AND t0.lastName='L1' AND  t1.lastName='F2' );";
		assert(targetStr.equals(queryStr));		
	}
	
	@Test
	public void test1() {
		String inputStr = "F1 L1 and (and ( F2)";
		String targetStr = "SELECT t0.thmId FROM (authorTb AS t0, authorTb AS t1) WHERE t0.thmId=t1.thmId "
				+ "AND ( t0.firstName LIKE 'F%'  AND t0.lastName='L1' AND  t1.lastName='F2' );";
		String pstmStr = "SELECT t0.thmId FROM (authorTb AS t0, authorTb AS t1) WHERE t0.thmId=t1.thmId "
				+ "AND ( t0.firstName LIKE ?  AND t0.lastName=? AND  t1.lastName=? );";
		
		assert(checkParse(inputStr, pstmStr, targetStr));
	}
	
	//test fault tolerance: "(A1) or A3)"
	// SELECT t0.thmId FROM (authorTb AS t0) WHERE (( t0.lastName= 'A1' ) OR  t0.lastName= 'A3' );
	//and nested constructs "((A1) or A3)"
	//"tao and (A1 d1  j or A2) or G"
	
	@Test
	public void test2() {
		String inputStr = "(A1) or A3)";
		String targetStr = "SELECT t0.thmId FROM (authorTb AS t0) WHERE (( t0.lastName= 'A1' ) OR  t0.lastName= 'A3' );";
		String pstmStr = "SELECT t0.thmId FROM (authorTb AS t0) WHERE (( t0.lastName=? ) OR  t0.lastName=? );";
		
		assert(checkParse(inputStr, pstmStr, targetStr));
	}
}
