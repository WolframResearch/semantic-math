package thmp.search;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import thmp.search.SearchCombined.ThmHypPair;
import thmp.utils.DBUtils;
import thmp.utils.FileUtils;

/**
 * Class used for testing various DB functions.
 * @author yihed
 *
 */
public class TestDB {
	
	public static void main(String[] args) throws SQLException {
		boolean f = false;
		if(f) {
			f();
			g();
			searchAuthor();
		}
		DBSearch.AuthorRelation rel;// = new DBSearch.AuthorRelation("F1 L1 and (F2 L2 or F3 L3)");
		
		//rel = new DBSearch.AuthorRelation("F1 L1 and ( F2 L2 or (F3 L3))"); 
		//rel = new DBSearch.AuthorRelation("F1 L1 and (and ( F2)"); //<--make into test case
		//rel = new DBSearch.AuthorRelation("tao and ((A1 d1  j or A2) and A3)"); //("F1 L1 and (and ( F2)"); 
		//rel = new DBSearch.AuthorRelation("(A1 or A3)"); //("F1 L1 and (and ( F2)"); 
		//rel = new DBSearch.AuthorRelation("F1 L1 and ( F2 L2 or (F3 L3))");
		//rel = new DBSearch.AuthorRelation("( F2 L2)");
		//rel = new DBSearch.AuthorRelation("F1 L1 and ( F2 L2 or (F3 L3)");
		rel = new DBSearch.AuthorRelation("tao ");
		
		//rel = new DBSearch.AuthorRelation("(A1) or G");
		
		Connection conn = thmp.test.TestSqlQueryParse.getLocalConnection();
		//System.out.println("queryStr: " +rel.sqlQueryStr());
		boolean abbreviateName = false;
		boolean abbreviateLastName = false;
		System.out.println("stm: " +rel.getPreparedStm(conn, abbreviateName, abbreviateLastName));
		
	}
	
	private static void searchAuthor() {
		//SELECT thmId FROM authorTb3 WHERE author='W. N. Kang';
		DataSource ds = com.wolfram.puremath.dbapp.DBUtils.getLocalDataSource(DBUtils.DEFAULT_DB_NAME, DBUtils.DEFAULT_USER, "wolfram", 
				DBUtils.DEFAULT_SERVER, DBUtils.DEFAULT_PORT);
		try {
			Connection conn = ds.getConnection();
			PreparedStatement stm = conn.prepareStatement("SELECT thmId FROM authorTb3 WHERE (author=?OR author='S. Ivanov');");
			stm.setString(1, "k'%");
			System.out.println("stm.toString() " +stm.toString());
			
			ResultSet rs = stm.executeQuery();
			System.out.println("rs: "+rs);
			while(rs.next()) {
				System.out.println(rs.getString("thmId"));				
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
	}
	
	private static void g() {
		String pathStr = "src/thmp/data/vocab3Words.txt";
		List<String> fileNameList = new ArrayList<String>();
		fileNameList.add(pathStr);
		List<String> lines = FileUtils.readLinesFromFiles(fileNameList, Charset.forName("UTF-8"));
		for(String s : lines) {
			System.out.println(s);
		}
	}
	
	private static void f() {
		String pathStr = "src/thmp/data/bigWordFreqPrunedMap.dat";
		@SuppressWarnings("unchecked")
		List<Map<String, Integer>> m = (List<Map<String, Integer>>)FileUtils.deserializeListFromFile(pathStr);
		System.out.println(m.get(0).size());
		/*for(String key : m.get(0).keySet()) {
			if("isomorphisme canonique".equals(key)) {
				System.out.println("isomorphisme canonique "+key);
			}			
		}*/		
		//FileUtils.writeToFile(m.get(0), "src/thmp/data/bigWordFreqPrunedMap1.txt");
	}
	
	private static void createDatabase() {
		MysqlDataSource ds = new MysqlDataSource();
		ds.setUser("root");
		//ds.setPassword("Lzft+utkk5q2");
		ds.setPassword("wolfram");
		ds.setServerName("localhost");
		ds.setPortNumber(3306);
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
	
	private static void fillTable() {
		final String peFileStr = "/Users/yihed/Documents/workspace/SemanticMath/src/thmp/data/pe/combinedParsedExpressionList1";
		//create database 
		@SuppressWarnings("unchecked")
		List<ThmHypPair> peList = (List<ThmHypPair>)FileUtils.deserializeListFromFile(peFileStr);
		//need to build metadata table!
		
		
		
	}
}
